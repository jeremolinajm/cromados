// src/main/java/com/cromados/barberia/controller/WhatsappController.java
package com.cromados.barberia.controller;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.service.TwilioService;
import com.cromados.barberia.service.WhatsAppFlowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {

    private final TwilioService twilio;
    private final BarberoRepository barberoRepo;
    private final WhatsAppFlowService flowService;

    public WhatsappController(TwilioService twilio, BarberoRepository barberoRepo, WhatsAppFlowService flowService) {
        this.twilio = twilio;
        this.barberoRepo = barberoRepo;
        this.flowService = flowService;
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }
    private static String digits(String s)  { return orEmpty(s).replaceAll("\\D+",""); }
    private static String stripWhats(String s) { return orEmpty(s).replaceFirst("^whatsapp:", ""); }
    private static String ensurePlusE164(String e164) {
        if (e164 == null || e164.isBlank()) return "";
        return e164.startsWith("+") ? e164 : "+" + e164;
    }

    /** Webhook inbound de Twilio (From/Body form-urlencoded). */
    @PostMapping(value = "/twilio/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> inbound(HttpServletRequest req) {
        String from = stripWhats(req.getParameter("From")); // e.g. "+549..."
        String body = orEmpty(req.getParameter("Body")).trim();

        log.info("[WA inbound] from={} body={}", from, body);

        try {
            String fromDigits = digits(from);

            // ¿Es un barbero?
            Barbero barbero = barberoRepo.findAll().stream()
                    .filter(b -> b.getTelefono() != null && digits(b.getTelefono()).contains(fromDigits))
                    .findFirst()
                    .orElse(null);

            if (barbero != null) {
                // Si escribe "hola" → enviar template con botones
                if (body.toLowerCase().matches("hola|menu|inicio")) {
                    var vars = Map.of("barbero", orEmpty(barbero.getNombre()));
                    twilio.sendTplAutoReply(ensurePlusE164(from), vars);
                    log.info("[WA inbound] template menu enviado a {}", from);
                } else {
                    // Procesar según el flujo conversacional
                    String respuesta = flowService.procesarMensaje(ensurePlusE164(from), body, barbero);
                    twilio.sendWhatsApp(ensurePlusE164(from), respuesta);
                    log.info("[WA inbound] respuesta enviada a {}", from);
                }
            } else {
                log.info("[WA inbound] cliente detectado; sin auto-reply.");
            }

            // ✅ FIX: Responder con TwiML vacío (no texto plano)
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_XML)
                    .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");

        } catch (Exception e) {
            log.error("[WA inbound] error: {}", e.toString(), e);
            // En caso de error también responder con TwiML vacío
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_XML)
                    .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
        }
    }

    /** Enviar CONFIRMACIÓN manual (prueba con Postman).
     * Template: confirmacion
     * Espera: {"cliente":"...", "fecha":"...", "hora":"...", "barbero":"...", "sucursal":"..."}
     */
    @PostMapping(value = "/send/confirmacion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendConfirmacion(@RequestBody Map<String, Object> json) {
        String to       = ensurePlusE164(String.valueOf(json.get("to")));
        String cliente  = String.valueOf(json.getOrDefault("cliente",""));
        String fecha    = String.valueOf(json.getOrDefault("fecha",""));
        String hora     = String.valueOf(json.getOrDefault("hora",""));
        String barbero  = String.valueOf(json.getOrDefault("barbero",""));
        String sucursal = String.valueOf(json.getOrDefault("sucursal",""));

        var vars = new java.util.LinkedHashMap<String,Object>();
        vars.put("cliente",  cliente);
        vars.put("fecha",    fecha);
        vars.put("hora",     hora);
        vars.put("barbero",  barbero);
        vars.put("sucursal", sucursal);

        try {
            twilio.sendTplReserva(to, vars);
            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            log.error("[WA confirmacion] error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Enviar RECORDATORIO manual (prueba con Postman).
     * Template: recordatorio_8hrs
     * Espera: {"fecha":"...", "hora":"...", "barbero":"..."}
     */
    @PostMapping(value = "/send/recordatorio", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendRecordatorio(@RequestBody Map<String, Object> json) {
        String to      = ensurePlusE164(String.valueOf(json.get("to")));
        String fecha   = String.valueOf(json.getOrDefault("fecha",""));
        String hora    = String.valueOf(json.getOrDefault("hora",""));
        String barbero = String.valueOf(json.getOrDefault("barbero",""));

        var vars = new java.util.LinkedHashMap<String,Object>();
        vars.put("fecha",   fecha);
        vars.put("hora",    hora);
        vars.put("barbero", barbero);

        try {
            twilio.sendTplRecordatorio(to, vars);
            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            log.error("[WA recordatorio] error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}