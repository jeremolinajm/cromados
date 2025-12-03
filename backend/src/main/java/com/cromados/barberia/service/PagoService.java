// src/main/java/com/cromados/barberia/service/PagoService.java
package com.cromados.barberia.service;

import com.cromados.barberia.dto.CheckoutRequest;
import com.cromados.barberia.model.*;
import com.cromados.barberia.service.TelegramBotService;
import com.cromados.barberia.repository.*;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import jakarta.transaction.Transactional;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PagoService {

    private final TurnoRepository turnoRepo;
    private final BarberoRepository barberoRepo;
    private final TipoCorteRepository servicioRepo;
    private final SucursalRepository sucursalRepo;
    private final NotificationService notificationService;

    private final String mpAccessToken;
    private final String mpWebhookSecret;
    private final String frontendBaseUrlRaw;
    private final TelegramBotService telegramBot;
    private final TwilioService twilioService;

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    @Value("${whatsapp.meta.sucursal1.phone:}")
    private String sucursal1Phone;

    @Value("${whatsapp.meta.sucursal2.phone:}")
    private String sucursal2Phone;

    public PagoService(
            TurnoRepository turnoRepo,
            BarberoRepository barberoRepo,
            TipoCorteRepository servicioRepo,
            SucursalRepository sucursalRepo,
            NotificationService notificationService,
            TelegramBotService telegramBot,
            TwilioService twilioService,
            @Value("${mp.access.token:}") String mpAccessToken,
            @Value("${mp.webhook.secret:}") String mpWebhookSecret,
            @Value("${app.frontend.baseUrl:}") String frontendBaseUrlRaw
    ) {
        this.turnoRepo = turnoRepo;
        this.barberoRepo = barberoRepo;
        this.servicioRepo = servicioRepo;
        this.sucursalRepo = sucursalRepo;
        this.notificationService = notificationService;
        this.mpAccessToken = mpAccessToken;
        this.mpWebhookSecret = mpWebhookSecret;
        this.frontendBaseUrlRaw = frontendBaseUrlRaw;
        this.telegramBot = telegramBot;
        this.twilioService = twilioService;
    }
    
    @PostConstruct
    public void validateConfiguration() {
        if (mpAccessToken == null || mpAccessToken.isBlank()) {
            throw new IllegalStateException(
                "CRITICAL: MP_ACCESS_TOKEN is not configured. " +
                "Set environment variable MP_ACCESS_TOKEN before starting the application."
            );
        }

        if (mpWebhookSecret == null || mpWebhookSecret.isBlank()) {
            throw new IllegalStateException(
                "CRITICAL: MP_WEBHOOK_SECRET is not configured. " +
                "Webhook signature validation will be disabled, exposing the system to fraudulent payments. " +
                "Set environment variable MP_WEBHOOK_SECRET before starting the application."
            );
        }

        log.info("[PagoService] ✅ MercadoPago credentials validated successfully");
    }

    private String sanitizePersonalData(String type, String value) {
        if (value == null || value.isBlank()) {
            return "[not-provided]";
        }

        return switch (type) {
            case "name" -> {
                // "Juan Perez" -> "J*** P***"
                String[] parts = value.trim().split("\\s+");
                StringBuilder sanitized = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sanitized.append(" ");
                    String part = parts[i];
                    if (part.length() > 0) {
                        sanitized.append(part.charAt(0)).append("***");
                    }
                }
                yield sanitized.toString();
            }
            case "phone" -> {
                // "+5493547509878" -> "*******9878"
                // "3547509878" -> "******9878"
                String cleaned = value.replaceAll("[^0-9+]", "");
                if (cleaned.length() <= 4) {
                    yield "***";
                }
                int visibleDigits = Math.min(4, cleaned.length());
                String masked = "*".repeat(cleaned.length() - visibleDigits);
                yield masked + cleaned.substring(cleaned.length() - visibleDigits);
            }
            case "email" -> {
                // "juan.perez@example.com" -> "j***@e***.com"
                int atIndex = value.indexOf('@');
                if (atIndex <= 0 || atIndex >= value.length() - 1) {
                    yield "***@***";
                }
                String local = value.substring(0, atIndex);
                String domain = value.substring(atIndex + 1);

                String sanitizedLocal = local.length() > 1
                    ? local.charAt(0) + "***"
                    : "***";

                int dotIndex = domain.indexOf('.');
                String sanitizedDomain = dotIndex > 0
                    ? domain.charAt(0) + "***" + domain.substring(dotIndex)
                    : domain.charAt(0) + "***";

                yield sanitizedLocal + "@" + sanitizedDomain;
            }
            default -> "***";
        };
    }

    @Value("${app.backend.baseUrl:}")
    private String backendBaseUrlRaw;

    private String pickBackendBase() {
        String raw = backendBaseUrlRaw == null ? "" : backendBaseUrlRaw.trim();
        if (raw.isEmpty()) return "";
        for (String part : raw.split(",")) {
            String u = part.trim();
            if (u.startsWith("https://")) return u;
        }
        for (String part : raw.split(",")) {
            String u = part.trim();
            if (!u.isEmpty()) return u;
        }
        return "";
    }

    private String pickFrontendBase() {
        String raw = frontendBaseUrlRaw == null ? "" : frontendBaseUrlRaw.trim();
        if (raw.isEmpty()) return "";
        for (String part : raw.split(",")) {
            String u = part.trim();
            if (u.startsWith("https://")) return u;
        }
        for (String part : raw.split(",")) {
            String u = part.trim();
            if (!u.isEmpty()) return u;
        }
        return "";
    }

    /**
     * Valida la firma del webhook de Mercado Pago (OBLIGATORIO)
     * @param xSignature Header x-signature enviado por MP
     * @param xRequestId Header x-request-id enviado por MP
     * @param dataId ID del recurso (payment ID)
     * @return true si la firma es válida
     */
    public boolean validateWebhookSignature(String xSignature, String xRequestId, String dataId) {
        // ❌ ELIMINADO: El bypass inseguro que permitía webhooks sin validación
        // ✅ NUEVO: Validación obligatoria siempre (mpWebhookSecret ya se validó en @PostConstruct)

        if (xSignature == null || xRequestId == null || dataId == null) {
            log.warn("[MP][Security] Webhook rejected: Missing required headers (x-signature, x-request-id, or dataId)");
            return false;
        }

        try {
            // Mercado Pago envía: x-signature con formato "ts=<timestamp>,v1=<hash>"
            String[] parts = xSignature.split(",");
            String ts = null;
            String hash = null;

            for (String part : parts) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    if ("ts".equals(kv[0])) ts = kv[1];
                    if ("v1".equals(kv[0])) hash = kv[1];
                }
            }

            if (ts == null || hash == null) {
                log.warn("[MP][Security] Webhook rejected: Invalid x-signature format");
                return false;
            }

            // Construir el manifest: id + request-id + ts
            String manifest = dataId + xRequestId + ts;

            // Calcular HMAC SHA256
            javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(mpWebhookSecret.getBytes(), "HmacSHA256");
            hmac.init(secretKey);
            byte[] hashBytes = hmac.doFinal(manifest.getBytes());

            // Convertir a hex
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String calculatedHash = hexString.toString();
            boolean isValid = calculatedHash.equals(hash);

            if (!isValid) {
                log.error("[MP][Security] ⚠️ WEBHOOK SIGNATURE MISMATCH - Possible fraudulent webhook attempt!");
                log.error("[MP][Security] Expected: {}, Received: {}", calculatedHash, hash);
            }

            return isValid;
        } catch (Exception e) {
            log.error("[MP][Security] Error validating webhook signature", e);
            return false;
        }
    }

    public Map<String, Object> crearPreferencia(CheckoutRequest req) throws Exception {
        validarDatosBasicos(req);

        if (mpAccessToken == null || mpAccessToken.isBlank()) {
            throw new IllegalArgumentException("Falta mp.access.token en configuración.");
        }

        MercadoPagoConfig.setAccessToken(mpAccessToken);

        Barbero barbero = barberoRepo.findById(req.getBarberoId())
                .orElseThrow(() -> new IllegalArgumentException("Barbero inexistente"));
        Sucursal sucursal = sucursalRepo.findById(req.getSucursalId())
                .orElseThrow(() -> new IllegalArgumentException("Sucursal inexistente"));

        // Calcular precio total del servicio principal + adicionales
        TipoCorte servicio = servicioRepo.findById(req.getTipoCorteId())
                .orElseThrow(() -> new IllegalArgumentException("Servicio inexistente: " + req.getTipoCorteId()));

        BigDecimal precioTotal = BigDecimal.valueOf(servicio.getPrecio() == null ? 0 : servicio.getPrecio());

        // 🆕 Calcular precio con el nuevo formato de sesiones
        int totalSesiones = 1;
        if (req.getSesiones() != null && !req.getSesiones().isEmpty()) {
            // Nuevo formato: array de sesiones con adicionales por sesión
            totalSesiones = req.getSesiones().size();
            for (var sesion : req.getSesiones()) {
                if (sesion.getAdicionalesIds() != null && !sesion.getAdicionalesIds().isEmpty()) {
                    for (Long adicionalId : sesion.getAdicionalesIds()) {
                        TipoCorte adicional = servicioRepo.findById(adicionalId)
                                .orElseThrow(() -> new IllegalArgumentException("Servicio adicional inexistente: " + adicionalId));
                        BigDecimal precioAdicional = BigDecimal.valueOf(adicional.getPrecio() == null ? 0 : adicional.getPrecio());
                        precioTotal = precioTotal.add(precioAdicional);
                    }
                }
            }
            log.info("[MP][checkout] Formato nuevo: {} sesiones con adicionales por sesión", totalSesiones);
        } else if (req.getAdicionalesIds() != null && !req.getAdicionalesIds().isEmpty()) {
            // Formato legacy: adicionales globales
            for (Long adicionalId : req.getAdicionalesIds()) {
                TipoCorte adicional = servicioRepo.findById(adicionalId)
                        .orElseThrow(() -> new IllegalArgumentException("Servicio adicional inexistente: " + adicionalId));
                BigDecimal precioAdicional = BigDecimal.valueOf(adicional.getPrecio() == null ? 0 : adicional.getPrecio());
                precioTotal = precioTotal.add(precioAdicional);
            }
        }

        if (req.getHorarios() != null && !req.getHorarios().isEmpty()) {
            totalSesiones = req.getHorarios().size();
        }

        log.info("[MP][checkout] Servicio: {}, {} sesiones, precio total: {}",
                servicio.getNombre(), totalSesiones, precioTotal);

        BigDecimal montoBase = (req.getMontoTotal() != null && req.getMontoTotal().compareTo(BigDecimal.ZERO) > 0)
                ? req.getMontoTotal()
                : precioTotal;

        // ✅ Calcular unitPrice según si es seña o no
        BigDecimal unitPrice = Boolean.TRUE.equals(req.getSenia())
                ? montoBase.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                : montoBase;

        if (unitPrice.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("El monto a cobrar debe ser mayor a 0.");
        }

        log.info("[MP][checkout] montoTotalReq={} precioTotal={} senia={} -> unitPrice={}",
                req.getMontoTotal(), precioTotal, req.getSenia(), unitPrice);

        String title = "Reserva Cromados – " + barbero.getNombre() + " (" + sucursal.getNombre() + ")";

        com.mercadopago.client.preference.PreferenceItemRequest item =
                com.mercadopago.client.preference.PreferenceItemRequest.builder()
                        .title(title)
                        .quantity(1)
                        .unitPrice(unitPrice)
                        .currencyId("ARS")
                        .build();

        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("turnoId", req.getTurnoId());
        metadata.put("sucursalId", req.getSucursalId());
        metadata.put("barberoId", req.getBarberoId());

        metadata.put("clienteNombre", req.getClienteNombre());
        log.info("[MP][checkout] Cliente: {} | Teléfono: {} | Edad: {}",
            sanitizePersonalData("name", req.getClienteNombre()),
            sanitizePersonalData("phone", req.getClienteTelefono()),
            req.getClienteEdad()
        );
        metadata.put("clienteTelefono", req.getClienteTelefono());
        metadata.put("clienteEdad", req.getClienteEdad());
        metadata.put("senia", Boolean.TRUE.equals(req.getSenia()));
        metadata.put("montoTotal", montoBase);
        metadata.put("unitPriceUsado", unitPrice);

        // Guardar servicio principal
        metadata.put("tipoCorteId", req.getTipoCorteId());

        // 🆕 Nuevo formato: Serializar sesiones con adicionales por sesión
        if (req.getSesiones() != null && !req.getSesiones().isEmpty()) {
            StringBuilder sesionesJson = new StringBuilder("[");
            for (int i = 0; i < req.getSesiones().size(); i++) {
                if (i > 0) sesionesJson.append(",");
                var sesion = req.getSesiones().get(i);
                sesionesJson.append("{");
                sesionesJson.append("\"fecha\":\"").append(sesion.getFecha()).append("\",");
                sesionesJson.append("\"hora\":\"").append(sesion.getHora()).append("\"");

                // Incluir adicionales si existen para esta sesión
                if (sesion.getAdicionalesIds() != null && !sesion.getAdicionalesIds().isEmpty()) {
                    sesionesJson.append(",\"adicionalesIds\":[");
                    for (int j = 0; j < sesion.getAdicionalesIds().size(); j++) {
                        if (j > 0) sesionesJson.append(",");
                        sesionesJson.append(sesion.getAdicionalesIds().get(j));
                    }
                    sesionesJson.append("]");
                }
                sesionesJson.append("}");
            }
            sesionesJson.append("]");
            metadata.put("sesiones", sesionesJson.toString());
            log.info("[MP][checkout] Sesiones (nuevo formato): {}", sesionesJson.toString());
        }
        // Legacy: Serializar adicionales si existen (sesión única)
        else if (req.getAdicionalesIds() != null && !req.getAdicionalesIds().isEmpty()) {
            StringBuilder adicionalesStr = new StringBuilder();
            for (int i = 0; i < req.getAdicionalesIds().size(); i++) {
                if (i > 0) adicionalesStr.append(",");
                adicionalesStr.append(req.getAdicionalesIds().get(i));
            }
            metadata.put("adicionalesIds", adicionalesStr.toString());
            log.info("[MP][checkout] Adicionales (legacy): {}", adicionalesStr.toString());
        }

        // Legacy: Serializar horarios múltiples si existen (para multi-sesión sin adicionales)
        if (req.getHorarios() != null && !req.getHorarios().isEmpty()) {
            StringBuilder horariosJson = new StringBuilder("[");
            for (int i = 0; i < req.getHorarios().size(); i++) {
                if (i > 0) horariosJson.append(",");
                String horario = req.getHorarios().get(i);
                horariosJson.append(horario);
            }
            horariosJson.append("]");
            metadata.put("horarios", horariosJson.toString());
            log.info("[MP][checkout] Horarios múltiples (legacy): {}", horariosJson.toString());
        } else if (req.getSesiones() == null || req.getSesiones().isEmpty()) {
            // Sesión única (solo si no hay sesiones en nuevo formato)
            metadata.put("fecha", req.getFecha());
            metadata.put("hora", req.getHora());
            log.info("[MP][checkout] Sesión única: {} {}", req.getFecha(), req.getHora());
        }

        String front = pickFrontendBase();
        String back = pickBackendBase();

        log.info("[MP][checkout] frontendBaseUrlRaw={}", frontendBaseUrlRaw);
        log.info("[MP][checkout] front={}, back={}", front, back);

        com.mercadopago.client.preference.PreferenceRequest.PreferenceRequestBuilder prefBuilder =
                com.mercadopago.client.preference.PreferenceRequest.builder()
                        .items(java.util.Collections.singletonList(item))
                        .metadata(metadata);

        if (!front.isEmpty()) {
            String successUrl = front + "/pago/success";
            String pendingUrl = front + "/pago/pending";
            String failureUrl = front + "/pago/failure";

            log.info("[MP][checkout] Configurando backUrls: success={}, pending={}, failure={}",
                    successUrl, pendingUrl, failureUrl);

            com.mercadopago.client.preference.PreferenceBackUrlsRequest backUrls =
                    com.mercadopago.client.preference.PreferenceBackUrlsRequest.builder()
                            .success(successUrl)
                            .pending(pendingUrl)
                            .failure(failureUrl)
                            .build();
            prefBuilder.backUrls(backUrls);

            // Solo configurar autoReturn si es HTTPS (no en localhost)
            if (front.startsWith("https://")) {
                log.info("[MP][checkout] Configurando autoReturn=approved");
                prefBuilder.autoReturn("approved");
            } else {
                log.info("[MP][checkout] NO configurando autoReturn (localhost no soportado)");
            }
        } else {
            log.warn("[MP][checkout] front está vacío, NO se configuran backUrls ni autoReturn");
        }

        if (!back.isEmpty()) {
            log.info("[MP][checkout] Configurando notificationUrl={}", back + "/pagos/webhook");
            prefBuilder.notificationUrl(back + "/pagos/webhook");
        }

        com.mercadopago.client.preference.PreferenceRequest prefReq = prefBuilder.build();

        try {
            com.mercadopago.client.preference.PreferenceClient client = new com.mercadopago.client.preference.PreferenceClient();
            com.mercadopago.resources.preference.Preference pref = client.create(prefReq);

            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("id", pref.getId());
            String initPoint = pref.getInitPoint() != null ? pref.getInitPoint() : pref.getSandboxInitPoint();
            resp.put("init_point", initPoint);
            resp.put("initPoint", initPoint); // Soporte para camelCase en frontend
            return resp;

        } catch (com.mercadopago.exceptions.MPApiException apiEx) {
            var apiResp = apiEx.getApiResponse();
            String content = (apiResp != null && apiResp.getContent() != null) ? apiResp.getContent() : apiEx.getMessage();
            throw new IllegalArgumentException("MercadoPago API error: " + content, apiEx);

        } catch (com.mercadopago.exceptions.MPException ex) {
            throw new IllegalArgumentException("MercadoPago SDK error: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public synchronized void procesarWebhook(Map<String, String> query) throws Exception {
        try {
            log.info("[MP] Webhook query={}", query);
        } catch (Exception ignore) {}

        String type = null;
        String dataId = null;
        try {
            type = query.getOrDefault("type", query.get("topic"));
            dataId = query.get("data.id");
            if (dataId == null) dataId = query.get("id");
        } catch (Exception ignore) {}

        if (!"payment".equalsIgnoreCase(type) || dataId == null) {
            log.warn("[MP] Webhook ignorado: type={} id={}", type, dataId);
            return;
        }

        MercadoPagoConfig.setAccessToken(mpAccessToken);
        PaymentClient paymentClient = new PaymentClient();
        Payment payment = paymentClient.get(Long.parseLong(dataId));

        if (payment == null) {
            log.warn("[MP] Payment {} no encontrado en API", dataId);
            return;
        }

        String status = payment.getStatus();
        log.info("[MP] paymentId={} status={} metadata={}", payment.getId(), status, payment.getMetadata());

        if (!"approved".equalsIgnoreCase(status)) {
            log.info("[MP] Payment {} no aprobado ({}). No se crea turno.", payment.getId(), status);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payment.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            log.error("[MP] Payment {} aprobado pero SIN metadata. No se puede crear turno.", payment.getId());
            return;
        }

        Long sucursalId = getMetaLong(metadata, "sucursalId", "sucursal_id");
        Long barberoId = getMetaLong(metadata, "barberoId", "barbero_id");
        Long servicioId = getMetaLong(metadata, "tipoCorteId", "tipo_corte_id");

        String clienteNombre = getMetaStr(metadata, "clienteNombre", "cliente_nombre");
        String clienteTelefono = getMetaStr(metadata, "clienteTelefono", "cliente_telefono");
        Integer clienteEdad = getMetaInt(metadata, "clienteEdad", "cliente_edad");

        // 🆕 Extraer datos de pago
        Boolean esSenia = getBooleanMeta(metadata, "senia", "senia");
        BigDecimal montoTotal = getBigDecimalMeta(metadata, "montoTotal", "monto_total");
        BigDecimal unitPriceUsado = getBigDecimalMeta(metadata, "unitPriceUsado", "unit_price_usado");

        log.info("[MP][DEBUG] Metadata extraída: esSenia={}, montoTotal={}, unitPriceUsado={}",
                esSenia, montoTotal, unitPriceUsado);

        Objects.requireNonNull(sucursalId, "sucursalId requerido");
        Objects.requireNonNull(barberoId, "barberoId requerido");
        Objects.requireNonNull(servicioId, "tipoCorteId requerido");

        // 🆕 Detectar formato de sesiones
        String sesionesJson = getMetaStr(metadata, "sesiones", "sesiones");
        String horariosJson = getMetaStr(metadata, "horarios", "horarios");

        if (sesionesJson != null && !sesionesJson.trim().isEmpty()) {
            // 🆕 NUEVO FORMATO: Sesiones con adicionales por sesión
            log.info("[MP][Webhook] Procesando nuevo formato de sesiones");
            crearTurnosDesdeNuevoFormato(payment, metadata, sucursalId, barberoId, servicioId,
                    clienteNombre, clienteTelefono, clienteEdad,
                    esSenia, montoTotal, unitPriceUsado, sesionesJson);
        } else if (horariosJson != null && !horariosJson.trim().isEmpty()) {
            // MULTI-SESIÓN LEGACY (sin adicionales por sesión)
            log.info("[MP][Webhook] Procesando formato legacy de multi-sesión");
            crearTurnosMultiSesion(payment, metadata, sucursalId, barberoId, servicioId,
                    clienteNombre, clienteTelefono, clienteEdad,
                    esSenia, montoTotal, unitPriceUsado, horariosJson);
        } else {
            // SESIÓN ÚNICA
            log.info("[MP][Webhook] Procesando sesión única");
            String adicionalesIdsStr = getMetaStr(metadata, "adicionalesIds", "adicionales_ids");
            String fechaStr = getMetaStr(metadata, "fecha", "fecha");
            String horaStr = getMetaStr(metadata, "hora", "hora");
            LocalDate fecha = LocalDate.parse(fechaStr);
            LocalTime hora = LocalTime.parse(horaStr);

            crearTurnoUnico(payment, sucursalId, barberoId, servicioId, fecha, hora,
                    clienteNombre, clienteTelefono, clienteEdad,
                    esSenia, montoTotal, unitPriceUsado, adicionalesIdsStr);
        }
    }

    private void crearTurnoUnico(Payment payment, Long sucursalId, Long barberoId, Long servicioId,
                                  LocalDate fecha, LocalTime hora, String clienteNombre,
                                  String clienteTelefono, Integer clienteEdad, Boolean esSenia,
                                  BigDecimal montoTotal, BigDecimal unitPriceUsado, String adicionalesIdsStr) {
        // Verificar si ya existe un turno para este pago (idempotencia)
        boolean yaExiste = turnoRepo.findByBarbero_IdAndFecha(barberoId, fecha).stream()
                .filter(t -> "CONFIRMADO".equals(t.getEstado()) && Boolean.TRUE.equals(t.getPagoConfirmado()))
                .filter(t -> t.getHora().equals(hora))
                .anyMatch(t -> clienteNombre.equalsIgnoreCase(t.getClienteNombre()));

        if (yaExiste) {
            log.warn("[MP] Ya existe un turno CONFIRMADO para {} el {} a las {} (barbero {}). Payment {} ignorado (idempotencia).",
                    clienteNombre, fecha, hora, barberoId, payment.getId());
            return;
        }

        // Verificar si el horario está ocupado por otro cliente
        boolean ocupado = turnoRepo.findByBarbero_IdAndFecha(barberoId, fecha).stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                        || "CONFIRMADO".equalsIgnoreCase(String.valueOf(t.getEstado())))
                .anyMatch(t -> t.getHora().equals(hora));

        if (ocupado) {
            log.warn("[MP] El horario {} {} para barbero {} ya está ocupado al confirmar pago {}.",
                    fecha, hora, barberoId, payment.getId());
            return;
        }

        Barbero barbero = barberoRepo.findById(barberoId)
                .orElseThrow(() -> new IllegalArgumentException("Barbero inexistente"));
        TipoCorte servicio = servicioRepo.findById(servicioId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio inexistente"));
        Sucursal sucursal = sucursalRepo.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal inexistente"));

        Turno t = new Turno();
        t.setBarbero(barbero);
        t.setSucursal(sucursal);
        t.setTipoCorte(servicio);
        t.setFecha(fecha);
        t.setHora(hora);
        t.setEstado("CONFIRMADO");
        t.setPagoConfirmado(true);

        t.setClienteNombre(clienteNombre);
        t.setClienteTelefono(clienteTelefono);
        t.setClienteEdad(clienteEdad != null ? clienteEdad : 0);
        t.setClienteDni(null);
        t.setClienteEmail(null);

        // 🆕 Procesar servicios adicionales
        if (adicionalesIdsStr != null && !adicionalesIdsStr.trim().isEmpty()) {
            String[] ids = adicionalesIdsStr.split(",");
            List<String> nombres = new ArrayList<>();
            for (String idStr : ids) {
                try {
                    Long id = Long.valueOf(idStr.trim());
                    TipoCorte adicional = servicioRepo.findById(id).orElse(null);
                    if (adicional != null) {
                        nombres.add(adicional.getNombre());
                    }
                } catch (NumberFormatException e) {
                    log.warn("[MP] ID de adicional inválido: {}", idStr);
                }
            }
            if (!nombres.isEmpty()) {
                t.setAdicionales(String.join(", ", nombres));
                log.info("[MP] Adicionales guardados: {}", t.getAdicionales());
            }
        }

        // Calcular y guardar campos de pago
        log.info("[MP][DEBUG] servicio.getPrecio()={}", servicio.getPrecio());

        BigDecimal montoPagadoCalculado;
        if (unitPriceUsado != null) {
            montoPagadoCalculado = unitPriceUsado;
            log.info("[MP][DEBUG] Usando unitPriceUsado: {}", unitPriceUsado);
        } else if (montoTotal != null) {
            montoPagadoCalculado = esSenia != null && esSenia
                    ? montoTotal.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                    : montoTotal;
            log.info("[MP][DEBUG] Usando montoTotal: {} -> montoPagado: {}", montoTotal, montoPagadoCalculado);
        } else {
            Integer precio = servicio.getPrecio();
            if (precio == null) {
                log.error("[MP][ERROR] El servicio {} no tiene precio configurado!", servicio.getId());
                montoPagadoCalculado = BigDecimal.ZERO;
            } else {
                montoPagadoCalculado = BigDecimal.valueOf(precio);
            }
            log.info("[MP][DEBUG] Usando precio del servicio: {}", montoPagadoCalculado);
        }

        t.setMontoPagado(montoPagadoCalculado);
        t.setSenia(esSenia);

        log.info("[MP][DEBUG] ANTES DE GUARDAR: montoPagado={}, senia={}",
                t.getMontoPagado(), t.getSenia());

        if (Boolean.TRUE.equals(esSenia) && montoTotal != null) {
            BigDecimal montoEfectivo = montoTotal.subtract(t.getMontoPagado());
            t.setMontoEfectivo(montoEfectivo);
            log.info("[MP][DEBUG] montoEfectivo calculado: {}", montoEfectivo);
        }

        Turno saved = turnoRepo.save(t);

        log.info("[MP][DEBUG] DESPUÉS DE GUARDAR: id={}, montoPagado={}, senia={}, montoEfectivo={}",
                saved.getId(), saved.getMontoPagado(), saved.getSenia(), saved.getMontoEfectivo());

        // ✅ NOTIFICAR AL BARBERO POR TELEGRAM
        try {
            telegramBot.notificarNuevoTurno(saved);
            log.info("[Telegram] Notificación enviada al barbero {}", barbero.getNombre());
        } catch (Exception e) {
            log.error("[Telegram] Error enviando notificación: {}", e.getMessage());
        }

        // ✅ ENVIAR CONFIRMACIÓN AL CLIENTE POR TWILIO WHATSAPP
        try {
            DateTimeFormatter fechaFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("HH:mm");

            String fechaFormateada = saved.getFecha().format(fechaFormatter);
            String horaFormateada = saved.getHora().format(horaFormatter);

            // Determinar teléfono de la sucursal (basado en ID: 1 o 2)
            String telefonoSucursal = saved.getSucursal().getId() == 1 ? sucursal1Phone : sucursal2Phone;

            twilioService.sendConfirmacion(
                    saved.getClienteTelefono(),
                    saved.getClienteNombre(),
                    fechaFormateada,
                    horaFormateada,
                    saved.getBarbero().getNombre(),
                    saved.getSucursal().getNombre(),
                    telefonoSucursal
            );
            log.info("[Twilio] Confirmación enviada al cliente {} ({})",
                    saved.getClienteNombre(), saved.getClienteTelefono());
        } catch (Exception e) {
            log.error("[Twilio] Error enviando confirmación: {}", e.getMessage(), e);
            // No fallar el webhook si falla Twilio
        }

        if (Boolean.TRUE.equals(esSenia)) {
            notificationService.notifyPagoParcial(saved);
        }

        log.info("[MP] Turno creado OK id={} fecha={} hora={} barbero={} pago={} senia={}",
                saved.getId(), saved.getFecha(), saved.getHora(), barberoId, payment.getId(), esSenia);
    }

    private void crearTurnosMultiSesion(Payment payment, Map<String, Object> metadata,
                                        Long sucursalId, Long barberoId, Long servicioId,
                                        String clienteNombre, String clienteTelefono, Integer clienteEdad,
                                        Boolean esSenia, BigDecimal montoTotal, BigDecimal unitPriceUsado,
                                        String horariosJson) {
        // Parse JSON: [{"fecha":"2025-11-01","hora":"10:00","servicioId":5},...]
        java.util.List<HorarioData> horarios = new java.util.ArrayList<>();
        try {
            log.info("[MP][MultiSesion] Parseando horarios: {}", horariosJson);

            horariosJson = horariosJson.trim();

            // Remover corchetes externos
            if (horariosJson.startsWith("[")) horariosJson = horariosJson.substring(1);
            if (horariosJson.endsWith("]")) horariosJson = horariosJson.substring(0, horariosJson.length() - 1);

            // Split por "},{"
            String[] items = horariosJson.split("\\},\\{");

            for (String item : items) {
                // Remover llaves y comillas
                item = item.replace("{", "").replace("}", "").replace("\"", "");

                // Split por comas para obtener pares clave:valor
                String[] parts = item.split(",");
                String fecha = null, hora = null;
                Long itemServicioId = null;

                for (String part : parts) {
                    // Split por : para obtener clave y valor
                    int colonIndex = part.indexOf(":");
                    if (colonIndex > 0) {
                        String key = part.substring(0, colonIndex).trim();
                        String value = part.substring(colonIndex + 1).trim();

                        if ("fecha".equals(key)) fecha = value;
                        if ("hora".equals(key)) hora = value;
                        if ("servicioId".equals(key)) {
                            try {
                                itemServicioId = Long.parseLong(value);
                            } catch (NumberFormatException e) {
                                log.warn("[MP][MultiSesion] servicioId inválido: {}", value);
                            }
                        }
                    }
                }

                if (fecha != null && hora != null) {
                    // 🆕 Si no tiene servicioId específico, usar el del metadata (fallback)
                    Long finalServicioId = itemServicioId != null ? itemServicioId : servicioId;
                    horarios.add(new HorarioData(LocalDate.parse(fecha), LocalTime.parse(hora), finalServicioId));
                } else {
                    log.warn("[MP][MultiSesion] Horario incompleto ignorado: fecha={}, hora={}", fecha, hora);
                }
            }
        } catch (Exception e) {
            log.error("[MP][MultiSesion] Error parseando JSON: {}", e.getMessage(), e);
            return;
        }

        if (horarios.isEmpty()) {
            log.warn("[MP][MultiSesion] No se pudieron parsear horarios del JSON: {}", horariosJson);
            return;
        }

        log.info("[MP][MultiSesion] ✅ {} sesiones detectadas", horarios.size());

        // Generar UUID para agrupar todos los turnos
        String grupoId = java.util.UUID.randomUUID().toString();

        // Cargar entidades comunes una sola vez
        Barbero barbero = barberoRepo.findById(barberoId)
                .orElseThrow(() -> new IllegalArgumentException("Barbero inexistente"));
        Sucursal sucursal = sucursalRepo.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal inexistente"));

        // Calcular monto pagado (igual para todos)
        BigDecimal montoPagadoCalculado;
        if (unitPriceUsado != null) {
            montoPagadoCalculado = unitPriceUsado;
        } else if (montoTotal != null) {
            montoPagadoCalculado = esSenia != null && esSenia
                    ? montoTotal.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                    : montoTotal;
        } else {
            // Fallback: usar precio del primer servicio
            TipoCorte primerServicio = servicioRepo.findById(horarios.get(0).servicioId)
                    .orElseThrow(() -> new IllegalArgumentException("Servicio inexistente"));
            Integer precio = primerServicio.getPrecio();
            montoPagadoCalculado = precio != null ? BigDecimal.valueOf(precio) : BigDecimal.ZERO;
        }

        java.util.List<Turno> turnosCreados = new java.util.ArrayList<>();

        // Crear cada turno con su servicio específico
        for (int i = 0; i < horarios.size(); i++) {
            HorarioData horario = horarios.get(i);
            boolean esPrimerTurno = (i == 0);

            // 🆕 Cargar servicio específico para esta sesión
            TipoCorte servicio = servicioRepo.findById(horario.servicioId)
                    .orElseThrow(() -> new IllegalArgumentException("Servicio " + horario.servicioId + " inexistente"));

            // Verificar si ya existe (idempotencia)
            boolean yaExiste = turnoRepo.findByBarbero_IdAndFecha(barberoId, horario.fecha).stream()
                    .filter(t -> "CONFIRMADO".equals(t.getEstado()) && Boolean.TRUE.equals(t.getPagoConfirmado()))
                    .filter(t -> t.getHora().equals(horario.hora))
                    .anyMatch(t -> clienteNombre.equalsIgnoreCase(t.getClienteNombre()));

            if (yaExiste) {
                log.warn("[MP][MultiSesion] Ya existe turno para {} el {} a las {}, saltando",
                        clienteNombre, horario.fecha, horario.hora);
                continue;
            }

            // Verificar si está ocupado
            boolean ocupado = turnoRepo.findByBarbero_IdAndFecha(barberoId, horario.fecha).stream()
                    .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                            || "CONFIRMADO".equalsIgnoreCase(String.valueOf(t.getEstado())))
                    .anyMatch(t -> t.getHora().equals(horario.hora));

            if (ocupado) {
                log.warn("[MP][MultiSesion] Horario {} {} ocupado, saltando", horario.fecha, horario.hora);
                continue;
            }

            Turno t = new Turno();
            t.setBarbero(barbero);
            t.setSucursal(sucursal);
            t.setTipoCorte(servicio);
            t.setFecha(horario.fecha);
            t.setHora(horario.hora);
            t.setEstado("CONFIRMADO");
            t.setPagoConfirmado(true);
            t.setClienteNombre(clienteNombre);
            t.setClienteTelefono(clienteTelefono);
            t.setClienteEdad(clienteEdad != null ? clienteEdad : 0);
            t.setClienteDni(null);
            t.setClienteEmail(null);
            t.setGrupoId(grupoId); // 🆕 Asignar grupo

            // 💰 IMPORTANTE: Solo el primer turno del grupo tiene el monto pagado
            // Los demás turnos tienen monto 0 para evitar duplicación en cálculos totales
            if (esPrimerTurno) {
                t.setMontoPagado(montoPagadoCalculado);
                t.setSenia(esSenia);

                if (Boolean.TRUE.equals(esSenia) && montoTotal != null) {
                    BigDecimal montoEfectivo = montoTotal.subtract(montoPagadoCalculado);
                    t.setMontoEfectivo(montoEfectivo);
                }

                log.info("[MP][MultiSesion] 💰 Primer turno - asignando monto: ${} (seña: {})",
                        montoPagadoCalculado, esSenia);
            } else {
                t.setMontoPagado(BigDecimal.ZERO);
                t.setSenia(null);
                t.setMontoEfectivo(null);
                log.info("[MP][MultiSesion] ⚪ Turno adicional - sin monto (parte del grupo {})",
                        grupoId.substring(0, 8));
            }

            Turno saved = turnoRepo.save(t);
            turnosCreados.add(saved);
            log.info("[MP][MultiSesion] Turno {}/{} creado: {} {} - {} (grupo: {})",
                    turnosCreados.size(), horarios.size(), horario.fecha, horario.hora,
                    servicio.getNombre(), grupoId.substring(0, 8));
        }

        if (turnosCreados.isEmpty()) {
            log.warn("[MP][MultiSesion] No se creó ningún turno (todos ocupados o duplicados)");
            return;
        }

        // ✅ NOTIFICAR AL BARBERO POR TELEGRAM (todos los turnos juntos)
        try {
            telegramBot.notificarNuevoTurnoGrupo(turnosCreados);
            log.info("[Telegram][MultiSesion] Notificación enviada al barbero {} con {} turnos",
                    barbero.getNombre(), turnosCreados.size());
        } catch (Exception e) {
            log.error("[Telegram][MultiSesion] Error enviando notificación: {}", e.getMessage());
        }

        // ✅ ENVIAR CONFIRMACIÓN AL CLIENTE POR TWILIO WHATSAPP
        try {
            DateTimeFormatter fechaFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("HH:mm");

            Turno primerTurno = turnosCreados.get(0);

            // 🆕 Generar detalle completo de todos los servicios
            String detalleServicios = generarDetalleServicios(turnosCreados, fechaFormatter, horaFormatter);

            // Determinar teléfono de la sucursal (basado en ID: 1 o 2)
            String telefonoSucursal = primerTurno.getSucursal().getId() == 1 ? sucursal1Phone : sucursal2Phone;

            // Enviar confirmación con primera fecha/hora (multisesión tiene detalle en variable 2)
            twilioService.sendConfirmacion(
                    primerTurno.getClienteTelefono(),
                    primerTurno.getClienteNombre(),
                    primerTurno.getFecha().format(fechaFormatter),
                    primerTurno.getHora().format(horaFormatter),
                    primerTurno.getBarbero().getNombre(),
                    primerTurno.getSucursal().getNombre(),
                    telefonoSucursal
            );

            // Log detallado de todas las sesiones
            log.info("[Twilio][MultiSesion] ✅ Confirmación enviada a {} ({})",
                    clienteNombre, primerTurno.getClienteTelefono());
            log.info("[Twilio][MultiSesion] 📋 {} servicios/sesiones enviados",
                    turnosCreados.size());
            log.info("[Twilio][MultiSesion] 📅 Detalle: {}", detalleServicios.replaceAll("\n", " | "));

            log.info("[Twilio][MultiSesion] ⏰ IMPORTANTE: Cliente recibirá {} recordatorios individuales",
                    turnosCreados.size());
            log.info("[Twilio][MultiSesion] ⏰ Cada recordatorio se enviará 8hrs antes de cada sesión");

        } catch (Exception e) {
            log.error("[Twilio][MultiSesion] ❌ Error enviando confirmación: {}", e.getMessage(), e);
        }

        if (Boolean.TRUE.equals(esSenia)) {
            notificationService.notifyPagoParcial(turnosCreados.get(0));
        }

        log.info("[MP][MultiSesion] Grupo {} creado con {} turnos. Payment {}",
                grupoId, turnosCreados.size(), payment.getId());
    }

    /**
     * Procesa el nuevo formato de sesiones que incluye adicionales por sesión.
     */
    private void crearTurnosDesdeNuevoFormato(Payment payment, Map<String, Object> metadata,
                                              Long sucursalId, Long barberoId, Long servicioId,
                                              String clienteNombre, String clienteTelefono, Integer clienteEdad,
                                              Boolean esSenia, BigDecimal montoTotal, BigDecimal unitPriceUsado,
                                              String sesionesJson) {
        java.util.List<SesionData> sesiones = new java.util.ArrayList<>();

        try {
            log.info("[MP][NuevoFormato] Parseando sesiones: {}", sesionesJson);
            sesionesJson = sesionesJson.trim();

            // Remover corchetes externos
            if (sesionesJson.startsWith("[")) sesionesJson = sesionesJson.substring(1);
            if (sesionesJson.endsWith("]")) sesionesJson = sesionesJson.substring(0, sesionesJson.length() - 1);

            // Split por "},{"
            String[] items = sesionesJson.split("\\},\\{");

            for (String item : items) {
                // Remover llaves y comillas
                item = item.replace("{", "").replace("}", "").replace("\"", "");

                String fecha = null, hora = null;
                java.util.List<Long> adicionalesIds = new java.util.ArrayList<>();

                // Split por comas que no estén dentro de corchetes
                String[] parts = item.split(",(?![^\\[]*\\])");

                for (String part : parts) {
                    int colonIndex = part.indexOf(":");
                    if (colonIndex > 0) {
                        String key = part.substring(0, colonIndex).trim();
                        String value = part.substring(colonIndex + 1).trim();

                        if ("fecha".equals(key)) fecha = value;
                        else if ("hora".equals(key)) hora = value;
                        else if ("adicionalesIds".equals(key)) {
                            // Parse array de adicionales: [1,2,3]
                            value = value.replace("[", "").replace("]", "");
                            if (!value.isEmpty()) {
                                String[] ids = value.split(",");
                                for (String id : ids) {
                                    try {
                                        adicionalesIds.add(Long.parseLong(id.trim()));
                                    } catch (NumberFormatException e) {
                                        log.warn("[MP][NuevoFormato] ID adicional inválido: {}", id);
                                    }
                                }
                            }
                        }
                    }
                }

                if (fecha != null && hora != null) {
                    sesiones.add(new SesionData(LocalDate.parse(fecha), LocalTime.parse(hora), adicionalesIds));
                } else {
                    log.warn("[MP][NuevoFormato] Sesión incompleta: fecha={}, hora={}", fecha, hora);
                }
            }
        } catch (Exception e) {
            log.error("[MP][NuevoFormato] Error parseando JSON: {}", e.getMessage(), e);
            return;
        }

        if (sesiones.isEmpty()) {
            log.warn("[MP][NuevoFormato] No se pudieron parsear sesiones del JSON");
            return;
        }

        log.info("[MP][NuevoFormato] ✅ {} sesiones parseadas", sesiones.size());

        // Generar UUID para agrupar
        String grupoId = java.util.UUID.randomUUID().toString();

        // Cargar entidades comunes
        Barbero barbero = barberoRepo.findById(barberoId)
                .orElseThrow(() -> new IllegalArgumentException("Barbero inexistente"));
        Sucursal sucursal = sucursalRepo.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal inexistente"));
        TipoCorte servicioPrincipal = servicioRepo.findById(servicioId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio inexistente"));

        // Calcular monto pagado
        BigDecimal montoPagadoCalculado;
        if (unitPriceUsado != null) {
            montoPagadoCalculado = unitPriceUsado;
        } else if (montoTotal != null) {
            montoPagadoCalculado = esSenia != null && esSenia
                    ? montoTotal.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                    : montoTotal;
        } else {
            Integer precio = servicioPrincipal.getPrecio();
            montoPagadoCalculado = precio != null ? BigDecimal.valueOf(precio) : BigDecimal.ZERO;
        }

        java.util.List<Turno> turnosCreados = new java.util.ArrayList<>();

        // Crear turnos
        for (int i = 0; i < sesiones.size(); i++) {
            SesionData sesion = sesiones.get(i);
            boolean esPrimerTurno = (i == 0);

            // Verificar si ya existe
            boolean yaExiste = turnoRepo.findByBarbero_IdAndFecha(barberoId, sesion.fecha).stream()
                    .filter(t -> "CONFIRMADO".equals(t.getEstado()) && Boolean.TRUE.equals(t.getPagoConfirmado()))
                    .filter(t -> t.getHora().equals(sesion.hora))
                    .anyMatch(t -> clienteNombre.equalsIgnoreCase(t.getClienteNombre()));

            if (yaExiste) {
                log.warn("[MP][NuevoFormato] Ya existe turno para {} el {} a las {}, saltando",
                        clienteNombre, sesion.fecha, sesion.hora);
                continue;
            }

            // Verificar si está ocupado
            boolean ocupado = turnoRepo.findByBarbero_IdAndFecha(barberoId, sesion.fecha).stream()
                    .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                            || "CONFIRMADO".equalsIgnoreCase(String.valueOf(t.getEstado())))
                    .anyMatch(t -> t.getHora().equals(sesion.hora));

            if (ocupado) {
                log.warn("[MP][NuevoFormato] Horario {} {} ocupado, saltando", sesion.fecha, sesion.hora);
                continue;
            }

            Turno t = new Turno();
            t.setBarbero(barbero);
            t.setSucursal(sucursal);
            t.setTipoCorte(servicioPrincipal);
            t.setFecha(sesion.fecha);
            t.setHora(sesion.hora);
            t.setEstado("CONFIRMADO");
            t.setPagoConfirmado(true);
            t.setClienteNombre(clienteNombre);
            t.setClienteTelefono(clienteTelefono);
            t.setClienteEdad(clienteEdad != null ? clienteEdad : 0);
            t.setGrupoId(grupoId);

            // Procesar adicionales de esta sesión
            if (!sesion.adicionalesIds.isEmpty()) {
                java.util.List<String> nombres = new java.util.ArrayList<>();
                for (Long adicionalId : sesion.adicionalesIds) {
                    TipoCorte adicional = servicioRepo.findById(adicionalId).orElse(null);
                    if (adicional != null) {
                        nombres.add(adicional.getNombre());
                    }
                }
                if (!nombres.isEmpty()) {
                    t.setAdicionales(String.join(", ", nombres));
                    log.info("[MP][NuevoFormato] Sesión {} con adicionales: {}", i + 1, t.getAdicionales());
                }
            }

            // Solo el primer turno tiene el monto
            if (esPrimerTurno) {
                t.setMontoPagado(montoPagadoCalculado);
                t.setSenia(esSenia);
                if (Boolean.TRUE.equals(esSenia) && montoTotal != null) {
                    BigDecimal montoEfectivo = montoTotal.subtract(montoPagadoCalculado);
                    t.setMontoEfectivo(montoEfectivo);
                }
                log.info("[MP][NuevoFormato] 💰 Primer turno - monto: ${}", montoPagadoCalculado);
            } else {
                t.setMontoPagado(BigDecimal.ZERO);
                t.setSenia(null);
                t.setMontoEfectivo(null);
                log.info("[MP][NuevoFormato] ⚪ Turno adicional - sin monto");
            }

            Turno saved = turnoRepo.save(t);
            turnosCreados.add(saved);
            log.info("[MP][NuevoFormato] Turno {}/{} creado: {} {}", i + 1, sesiones.size(), sesion.fecha, sesion.hora);
        }

        if (turnosCreados.isEmpty()) {
            log.warn("[MP][NuevoFormato] No se creó ningún turno");
            return;
        }

        // Notificar
        try {
            telegramBot.notificarNuevoTurnoGrupo(turnosCreados);
            log.info("[Telegram][NuevoFormato] Notificación enviada");
        } catch (Exception e) {
            log.error("[Telegram][NuevoFormato] Error: {}", e.getMessage());
        }

        // WhatsApp
        try {
            DateTimeFormatter fechaFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("HH:mm");
            Turno primerTurno = turnosCreados.get(0);

            // Determinar teléfono de la sucursal (basado en ID: 1 o 2)
            String telefonoSucursal = primerTurno.getSucursal().getId() == 1 ? sucursal1Phone : sucursal2Phone;

            twilioService.sendConfirmacion(
                    primerTurno.getClienteTelefono(),
                    primerTurno.getClienteNombre(),
                    primerTurno.getFecha().format(fechaFormatter),
                    primerTurno.getHora().format(horaFormatter),
                    primerTurno.getBarbero().getNombre(),
                    primerTurno.getSucursal().getNombre(),
                    telefonoSucursal
            );

            log.info("[Twilio][NuevoFormato] ✅ Confirmación enviada a {}", clienteNombre);
        } catch (Exception e) {
            log.error("[Twilio][NuevoFormato] Error: {}", e.getMessage(), e);
        }

        if (Boolean.TRUE.equals(esSenia)) {
            notificationService.notifyPagoParcial(turnosCreados.get(0));
        }

        log.info("[MP][NuevoFormato] Grupo {} creado con {} turnos", grupoId, turnosCreados.size());
    }

    // Clase auxiliar para parsear sesiones (nuevo formato)
    private static class SesionData {
        LocalDate fecha;
        LocalTime hora;
        java.util.List<Long> adicionalesIds;

        SesionData(LocalDate fecha, LocalTime hora, java.util.List<Long> adicionalesIds) {
            this.fecha = fecha;
            this.hora = hora;
            this.adicionalesIds = adicionalesIds;
        }
    }

    // Clase auxiliar para parsear horarios (legacy)
    private static class HorarioData {
        LocalDate fecha;
        LocalTime hora;
        Long servicioId;

        HorarioData(LocalDate fecha, LocalTime hora, Long servicioId) {
            this.fecha = fecha;
            this.hora = hora;
            this.servicioId = servicioId;
        }
    }

    private void validarDatosBasicos(CheckoutRequest r) {
        if (r.getSucursalId() == null || r.getBarberoId() == null || r.getTipoCorteId() == null)
            throw new IllegalArgumentException("Ids requeridos");

        // Validar que haya horarios (sesiones, fecha/hora, o array de horarios legacy)
        boolean tieneSesiones = r.getSesiones() != null && !r.getSesiones().isEmpty();
        boolean tieneHorarioUnico = r.getFecha() != null && !r.getFecha().isBlank()
                && r.getHora() != null && !r.getHora().isBlank();
        boolean tieneHorariosMultiples = r.getHorarios() != null && !r.getHorarios().isEmpty();

        if (!tieneSesiones && !tieneHorarioUnico && !tieneHorariosMultiples) {
            throw new IllegalArgumentException("Debe especificar sesiones, fecha/hora, o array de horarios");
        }

        if (r.getClienteNombre() == null || r.getClienteNombre().isBlank())
            throw new IllegalArgumentException("Nombre requerido");
        if (r.getClienteTelefono() == null || r.getClienteTelefono().isBlank())
            throw new IllegalArgumentException("Teléfono requerido");
        if (r.getClienteEdad() == null)
            throw new IllegalArgumentException("Edad requerida");

        // Validar formato si son sesiones (nuevo formato)
        if (tieneSesiones) {
            for (var sesion : r.getSesiones()) {
                if (sesion.getFecha() == null || sesion.getFecha().isBlank())
                    throw new IllegalArgumentException("Fecha requerida en sesión");
                if (sesion.getHora() == null || sesion.getHora().isBlank())
                    throw new IllegalArgumentException("Hora requerida en sesión");
                // Validar formato
                LocalDate.parse(sesion.getFecha());
                LocalTime.parse(sesion.getHora());
            }
        }
        // Validar formato si es sesión única
        else if (tieneHorarioUnico) {
            LocalDate.parse(r.getFecha());
            LocalTime.parse(r.getHora());
        }
        // Validar formato si son múltiples sesiones (legacy)
        else if (tieneHorariosMultiples) {
            for (String horario : r.getHorarios()) {
                if (horario == null || horario.isBlank()) {
                    throw new IllegalArgumentException("Todos los horarios deben ser válidos");
                }
            }
        }
    }

    private Long getMetaLong(Map<String, Object> meta, String camel, String snake) {
        Object v = meta.get(camel);
        if (v == null) v = meta.get(snake);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private Integer getMetaInt(Map<String, Object> meta, String camel, String snake) {
        Object v = meta.get(camel);
        if (v == null) v = meta.get(snake);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private String getMetaStr(Map<String, Object> meta, String camel, String snake) {
        Object v = meta.get(camel);
        if (v == null) v = meta.get(snake);
        return v == null ? null : String.valueOf(v);
    }

    private Boolean getBooleanMeta(Map<String, Object> meta, String camel, String snake) {
        Object v = meta.get(camel);
        if (v == null) v = meta.get(snake);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private BigDecimal getBigDecimalMeta(Map<String, Object> meta, String camel, String snake) {
        Object v = meta.get(camel);
        if (v == null) v = meta.get(snake);
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Genera el detalle de servicios para el mensaje de WhatsApp.
     * Agrupa turnos por servicio y formatea con fechas/horas.
     *
     * Ejemplo output:
     * 1x Fade (03/05/2026 → 16:00)
     * 1x Promo 2x1 Fade (07/12/2025 → 12:00 | 06/07/2026 → 09:00)
     */
    private String generarDetalleServicios(java.util.List<Turno> turnos,
                                           DateTimeFormatter fechaFormatter,
                                           DateTimeFormatter horaFormatter) {
        // Agrupar turnos por servicio
        java.util.Map<String, java.util.List<Turno>> porServicio = new java.util.LinkedHashMap<>();
        for (Turno t : turnos) {
            String nombreServicio = t.getTipoCorte().getNombre();
            porServicio.computeIfAbsent(nombreServicio, k -> new java.util.ArrayList<>()).add(t);
        }

        // Construir texto
        StringBuilder sb = new StringBuilder();
        for (var entry : porServicio.entrySet()) {
            String nombreServicio = entry.getKey();
            java.util.List<Turno> turnosServicio = entry.getValue();
            int cantidad = turnosServicio.size();

            sb.append(String.format("%dx %s (", cantidad, nombreServicio));

            // Listar fechas/horas de cada sesión
            for (int i = 0; i < turnosServicio.size(); i++) {
                Turno t = turnosServicio.get(i);
                if (i > 0) sb.append(" | ");
                sb.append(t.getFecha().format(fechaFormatter))
                  .append(" → ")
                  .append(t.getHora().format(horaFormatter));
            }

            sb.append(")\n");
        }

        return sb.toString().trim();
    }

    /**
     * Genera el detalle de servicios incluyendo adicionales por sesión.
     *
     * Ejemplo output:
     * 📅 10/11/2025 a las 10:00
     * ✂️ Fade
     * ➕ Barba, Cejas
     *
     * 📅 15/11/2025 a las 14:00
     * ✂️ Fade
     */
    private String generarDetalleServiciosConAdicionales(java.util.List<Turno> turnos,
                                                          DateTimeFormatter fechaFormatter,
                                                          DateTimeFormatter horaFormatter) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < turnos.size(); i++) {
            Turno t = turnos.get(i);
            if (i > 0) sb.append("\n");

            // Fecha y hora
            sb.append("📅 ").append(t.getFecha().format(fechaFormatter))
              .append(" a las ").append(t.getHora().format(horaFormatter))
              .append("\n");

            // Servicio principal
            sb.append("✂️ ").append(t.getTipoCorte().getNombre());

            // Adicionales si existen
            if (t.getAdicionales() != null && !t.getAdicionales().isEmpty()) {
                sb.append("\n➕ ").append(t.getAdicionales());
            }
        }

        return sb.toString().trim();
    }
}