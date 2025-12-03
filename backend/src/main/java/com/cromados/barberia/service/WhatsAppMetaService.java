// src/main/java/com/cromados/barberia/service/WhatsAppMetaService.java
package com.cromados.barberia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Servicio para envío de mensajes WhatsApp mediante Meta Cloud API.
 * Reemplaza TwilioService con integración directa a Meta.
 *
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-messages
 */
@Slf4j
@Service
public class WhatsAppMetaService {

    private final String accessToken;
    private final String phoneNumberId;
    private final String apiVersion;
    private final String confirmacionTemplate;
    private final String recordatorioTemplate;
    private final String sucursal1Phone;
    private final String sucursal2Phone;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile boolean configured = false;

    public WhatsAppMetaService(
            @Value("${whatsapp.meta.accessToken:}") String accessToken,
            @Value("${whatsapp.meta.phoneNumberId:}") String phoneNumberId,
            @Value("${whatsapp.meta.apiVersion:v18.0}") String apiVersion,
            @Value("${whatsapp.meta.template.confirmacion:turno_confirmado}") String confirmacionTemplate,
            @Value("${whatsapp.meta.template.recordatorio:recordatorio_turno}") String recordatorioTemplate,
            @Value("${whatsapp.meta.sucursal1.phone:}") String sucursal1Phone,
            @Value("${whatsapp.meta.sucursal2.phone:}") String sucursal2Phone,
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {
        this.accessToken = nz(accessToken);
        this.phoneNumberId = nz(phoneNumberId);
        this.apiVersion = nz(apiVersion);
        this.confirmacionTemplate = nz(confirmacionTemplate);
        this.recordatorioTemplate = nz(recordatorioTemplate);
        this.sucursal1Phone = nz(sucursal1Phone);
        this.sucursal2Phone = nz(sucursal2Phone);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    @PostConstruct
    public void init() {
        if (accessToken.isEmpty() || phoneNumberId.isEmpty()) {
            log.warn("[WhatsApp Meta] accessToken/phoneNumberId vacíos; servicio no configurado.");
            return;
        }
        configured = true;
        log.info("[WhatsApp Meta] Servicio configurado. Phone ID: {}, API: {}", phoneNumberId, apiVersion);
    }

    private void ensureReady() {
        if (!configured) {
            throw new IllegalStateException("WhatsApp Meta no configurado correctamente.");
        }
    }

    /**
     * Normaliza número de teléfono a formato E.164 sin prefijo "whatsapp:"
     * Ejemplo: +5493547509878
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String p = phone.trim();
        // Remover prefijo whatsapp: si existe
        if (p.startsWith("whatsapp:")) {
            p = p.substring("whatsapp:".length());
        }
        // Asegurar que empiece con +
        if (!p.startsWith("+")) {
            p = "+" + p;
        }
        return p;
    }

    /**
     * Envía una plantilla de WhatsApp mediante Meta Cloud API.
     *
     * @param to Número de teléfono destino en formato E.164 (+5493547509878)
     * @param templateName Nombre de la plantilla aprobada en Meta
     * @param parameters Lista de parámetros para {{1}}, {{2}}, etc.
     * @return Message ID si fue exitoso
     */
    private String sendTemplate(String to, String templateName, List<String> parameters) {
        ensureReady();

        String toPhone = normalizePhone(to);
        if (toPhone.isEmpty()) {
            throw new IllegalArgumentException("Número de teléfono destino vacío.");
        }

        // Construir URL de la API
        String url = String.format("https://graph.facebook.com/%s/%s/messages",
                apiVersion, phoneNumberId);

        // Construir headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        // Construir body del mensaje
        Map<String, Object> body = buildTemplateMessage(toPhone, templateName, parameters);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("[WhatsApp Meta] Enviando template '{}' a {} con {} parámetros",
                    templateName, toPhone, parameters.size());
            log.debug("[WhatsApp Meta] Body: {}", objectMapper.writeValueAsString(body));

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> messages = (List<Map<String, Object>>) responseBody.get("messages");

                if (messages != null && !messages.isEmpty()) {
                    String messageId = (String) messages.get(0).get("id");
                    log.info("[WhatsApp Meta] Mensaje enviado exitosamente. ID: {}", messageId);
                    return messageId;
                }
            }

            log.error("[WhatsApp Meta] Respuesta inesperada: {}", response.getBody());
            throw new RuntimeException("Respuesta inesperada de Meta API");

        } catch (HttpClientErrorException e) {
            log.error("[WhatsApp Meta] Error HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error enviando mensaje WhatsApp: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[WhatsApp Meta] Error inesperado: {}", e.getMessage(), e);
            throw new RuntimeException("Error enviando mensaje WhatsApp", e);
        }
    }

    /**
     * Construye el cuerpo del mensaje para enviar una plantilla.
     */
    private Map<String, Object> buildTemplateMessage(String to, String templateName, List<String> parameters) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", to);
        message.put("type", "template");

        // Configuración de la plantilla
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);

        // Idioma
        Map<String, String> language = new LinkedHashMap<>();
        language.put("code", "es");
        template.put("language", language);

        // Parámetros del cuerpo
        if (parameters != null && !parameters.isEmpty()) {
            List<Map<String, Object>> components = new ArrayList<>();

            Map<String, Object> bodyComponent = new LinkedHashMap<>();
            bodyComponent.put("type", "body");

            List<Map<String, String>> bodyParameters = new ArrayList<>();
            for (String param : parameters) {
                Map<String, String> parameter = new LinkedHashMap<>();
                parameter.put("type", "text");
                parameter.put("text", param);
                bodyParameters.add(parameter);
            }

            bodyComponent.put("parameters", bodyParameters);
            components.add(bodyComponent);

            template.put("components", components);
        }

        message.put("template", template);
        return message;
    }

    /**
     * Envía plantilla de confirmación de turno (SIMPLE - 1 servicio, 1 sesión).
     * DEPRECADO: Usar sendConfirmacionDetallada() para soportar múltiples servicios.
     *
     * @param to Número de teléfono del cliente
     * @param nombre Nombre del cliente
     * @param fecha Fecha del turno (formato: DD/MM/YYYY)
     * @param hora Hora del turno (formato: HH:MM)
     * @param barbero Nombre del barbero
     * @param sucursal Nombre de la sucursal
     * @return Message ID
     */
    @Deprecated
    public String sendConfirmacion(String to, String nombre, String fecha, String hora,
                                   String barbero, String sucursal) {
        // Generar detalle simple para compatibilidad
        String detalle = String.format("📅 %s a las %s", fecha, hora);
        return sendConfirmacionDetallada(to, nombre, detalle, barbero, sucursal);
    }

    /**
     * Envía plantilla de confirmación con detalle completo de servicios.
     * Soporta múltiples servicios y sesiones en un solo mensaje.
     *
     * @param to Número de teléfono del cliente
     * @param nombre Nombre del cliente
     * @param detalleServicios Texto con servicios y fechas/horas (generado por PagoService)
     *                         Ejemplo: "1x Fade (03/05/2026 → 16:00)\n1x Promo (07/12/2025 → 12:00)"
     * @param barbero Nombre del barbero
     * @param sucursal Nombre de la sucursal
     * @return Message ID
     */
    public String sendConfirmacionDetallada(String to, String nombre, String detalleServicios,
                                            String barbero, String sucursal) {
        List<String> params = Arrays.asList(
                nombre,
                detalleServicios,
                barbero,
                sucursal,
                sucursal1Phone,
                sucursal2Phone
        );

        log.info("[WhatsApp Meta] Enviando confirmación detallada a {}", to);
        log.debug("[WhatsApp Meta] Detalle: {}", detalleServicios.replaceAll("\n", " | "));

        return sendTemplate(to, confirmacionTemplate, params);
    }

    /**
     * Envía plantilla de recordatorio de turno (8 horas antes).
     *
     * @param to Número de teléfono del cliente
     * @param nombre Nombre del cliente
     * @param fecha Fecha del turno (formato: DD/MM/YYYY)
     * @param hora Hora del turno (formato: HH:MM)
     * @param barbero Nombre del barbero
     * @param sucursal Nombre de la sucursal
     * @return Message ID
     */
    public String sendRecordatorio(String to, String nombre, String fecha, String hora,
                                   String barbero, String sucursal) {
        List<String> params = Arrays.asList(
                nombre,
                fecha,
                hora,
                barbero,
                sucursal,
                sucursal1Phone,
                sucursal2Phone
        );

        log.info("[WhatsApp Meta] Enviando recordatorio a {} - {} {} {}",
                to, nombre, fecha, hora);

        return sendTemplate(to, recordatorioTemplate, params);
    }

    /**
     * Método para testing/debugging - envía mensaje a número de prueba.
     */
    public String sendTest(String to, String templateName) {
        List<String> testParams = Arrays.asList(
                "Juan Pérez",
                "29/10/2025",
                "15:30",
                "Carlos Gómez",
                "Sucursal 1",
                sucursal1Phone,
                sucursal2Phone
        );

        log.info("[WhatsApp Meta] Enviando mensaje de prueba");
        return sendTemplate(to, templateName, testParams);
    }
}
