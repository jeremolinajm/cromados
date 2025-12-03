// src/main/java/com/cromados/barberia/service/TwilioService.java
package com.cromados.barberia.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class TwilioService {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String confirmacionSid;
    private final String recordatorioSid;
    private final String menuBarberoSid;

    private volatile boolean sdkInitialized = false;

    public TwilioService(
            @Value("${twilio.accountSid:}") String accountSid,
            @Value("${twilio.authToken:}") String authToken,
            @Value("${twilio.whatsapp.from:${twilio.fromWhatsApp:}}") String fromNumber,
            @Value("${twilio.template.reserva:${twilio.content.confirmSid:}}") String confirmacionSid,
            @Value("${twilio.template.recordatorio:${twilio.content.reminderSid:}}") String recordatorioSid,
            @Value("${twilio.template.autoreply:${twilio.content.barberMenuSid:}}") String menuBarberoSid
    ) {
        this.accountSid = nz(accountSid);
        this.authToken = nz(authToken);
        this.fromNumber = nz(fromNumber);
        this.confirmacionSid = nz(confirmacionSid);
        this.recordatorioSid = nz(recordatorioSid);
        this.menuBarberoSid = nz(menuBarberoSid);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    @PostConstruct
    public void init() {
        if (accountSid.isEmpty() || authToken.isEmpty()) {
            log.warn("[Twilio] accountSid/authToken vacíos; SDK no inicializado.");
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            sdkInitialized = true;
            log.info("[Twilio] SDK inicializado OK.");
        } catch (Exception e) {
            sdkInitialized = false;
            log.error("[Twilio] No se pudo inicializar SDK: {}", e.getMessage());
        }
    }

    private void ensureReady() {
        if (!sdkInitialized) {
            throw new IllegalStateException("Twilio no inicializado.");
        }
        if (fromNumber.isEmpty()) {
            throw new IllegalStateException("Remitente WhatsApp no configurado.");
        }
    }

    private static String asWhats(String number) {
        if (number == null || number.isBlank()) return "";
        String n = number.trim();
        return n.startsWith("whatsapp:") ? n : "whatsapp:" + n;
    }

    private static String normalizeTo(String number) {
        if (number == null) return "";
        String n = number.trim();
        if (n.startsWith("whatsapp:")) n = n.substring("whatsapp:".length());
        return n;
    }

    // ========== FIX: Content Variables ==========
    /**
     * Convierte Map<String,?> en JSON válido para Twilio Content API.
     *
     * CRÍTICO: Twilio ContentVariables espera formato ESTRICTO:
     * - Si tu plantilla usa {{1}}, {{2}}, etc → keys numéricas como strings
     * - Si usa {{barbero}}, {{fecha}}, etc → keys de texto
     *
     * Estructura: {"1":"valor1","2":"valor2"} o {"barbero":"Juan","fecha":"2025-01-15"}
     */
    private static String toContentVariables(Map<String, ?> vars) {
        if (vars == null || vars.isEmpty()) {
            return "{}"; // ⚠️ NO envíes null, Twilio rechaza
        }

        // Ordenar para consistencia (opcional pero recomendado)
        Map<String, String> ordered = new LinkedHashMap<>();
        for (var e : vars.entrySet()) {
            String key = String.valueOf(e.getKey() == null ? "" : e.getKey()).trim();
            String val = String.valueOf(e.getValue() == null ? "" : e.getValue());
            if (!key.isEmpty()) {
                ordered.put(key, val);
            }
        }

        if (ordered.isEmpty()) {
            return "{}";
        }

        // Construir JSON manualmente con escape correcto
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (var e : ordered.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escapeJson(e.getKey())).append('"')
                    .append(':')
                    .append('"').append(escapeJson(e.getValue())).append('"');
        }
        json.append('}');

        return json.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    // ========== Envío de Templates ==========
    private Message sendTemplateInternal(String to, String contentSid, Map<String, ?> vars) {
        ensureReady();

        if (contentSid == null || contentSid.isBlank()) {
            throw new IllegalArgumentException("ContentSid vacío.");
        }

        String toWhats = asWhats(normalizeTo(to));
        String fromWhats = asWhats(fromNumber);
        String contentVars = toContentVariables(vars);

        log.info("[Twilio] Template {} → {} | vars={}", contentSid, toWhats, contentVars);

        try {
            return Message.creator(
                            new PhoneNumber(toWhats),
                            new PhoneNumber(fromWhats),
                            "" // body vacío cuando se usa ContentSid
                    )
                    .setContentSid(contentSid)
                    .setContentVariables(contentVars)
                    .create();

        } catch (ApiException apiEx) {
            log.error("[Twilio] API Error: code={} msg={} moreInfo={}",
                    apiEx.getCode(), apiEx.getMessage(), apiEx.getMoreInfo());
            throw apiEx;
        } catch (Exception ex) {
            log.error("[Twilio] Error inesperado: {}", ex.getMessage(), ex);
            throw new RuntimeException("Error enviando mensaje Twilio", ex);
        }
    }

    /**
     * Plantilla: cromados_confirmacion_turno
     * Variables esperadas: 1=nombre, 2=fecha, 3=hora, 4=barbero, 5=sucursal, 6=telefono
     */
    public String sendTplReserva(String to, Map<String, ?> vars) {
        Message m = sendTemplateInternal(to, confirmacionSid, vars);
        log.info("[Twilio] Confirmación enviada. SID={}", m.getSid());
        return m.getSid();
    }

    /**
     * Plantilla: cromados_recordatorio_turno
     * Variables esperadas: 1=nombre, 2=fecha, 3=hora, 4=barbero, 5=sucursal, 6=telefono
     */
    public String sendTplRecordatorio(String to, Map<String, ?> vars) {
        Message m = sendTemplateInternal(to, recordatorioSid, vars);
        log.info("[Twilio] Recordatorio enviado. SID={}", m.getSid());
        return m.getSid();
    }

    /**
     * Helper: Enviar confirmación de turno
     */
    public String sendConfirmacion(String to, String nombre, String fecha, String hora,
                                   String barbero, String sucursal, String telefono) {
        Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.put("1", nombre);
        vars.put("2", fecha);
        vars.put("3", hora);
        vars.put("4", barbero);
        vars.put("5", sucursal);
        vars.put("6", telefono);
        return sendTplReserva(to, vars);
    }

    /**
     * Helper: Enviar recordatorio de turno
     */
    public String sendRecordatorio(String to, String nombre, String fecha, String hora,
                                   String barbero, String sucursal, String telefono) {
        Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.put("1", nombre);
        vars.put("2", fecha);
        vars.put("3", hora);
        vars.put("4", barbero);
        vars.put("5", sucursal);
        vars.put("6", telefono);
        return sendTplRecordatorio(to, vars);
    }

    /** Plantilla: menu_barbero (barbero)
     * Template espera: {"barbero":"NombreDelBarbero"}
     */
    public String sendTplAutoReply(String to, Map<String, ?> vars) {
        Message m = sendTemplateInternal(to, menuBarberoSid, vars);
        log.info("[Twilio] Menú barbero enviado. SID={}", m.getSid());
        return m.getSid();
    }

    /** Envío de texto libre (sin template) */
    public String sendWhatsApp(String toE164, String body) {
        ensureReady();
        String to = asWhats(normalizeTo(toE164));
        String from = asWhats(fromNumber);

        try {
            Message msg = Message
                    .creator(new PhoneNumber(to), new PhoneNumber(from), body == null ? "" : body)
                    .create();
            log.info("[Twilio] Texto libre enviado a {} sid={}", to, msg.getSid());
            return msg.getSid();
        } catch (ApiException api) {
            log.error("[Twilio] Error API: {}", api.getMessage());
            throw api;
        }
    }
}