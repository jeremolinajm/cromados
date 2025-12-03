// src/main/java/com/cromados/barberia/controller/WhatsAppWebhookController.java
package com.cromados.barberia.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook para Meta WhatsApp Cloud API.
 * Meta envía notificaciones de mensajes, entregas, errores, etc.
 *
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks
 */
@Slf4j
@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    @Value("${whatsapp.webhook.verify.token:cromados_webhook_2024}")
    private String verifyToken;

    /**
     * Verificación del webhook (GET).
     *
     * Meta envía:
     * - hub.mode=subscribe
     * - hub.verify_token=<tu_token>
     * - hub.challenge=<número_aleatorio>
     *
     * Debes responder con hub.challenge si el token coincide.
     *
     * Ejemplo URL de configuración en Meta:
     * https://api.cromados.uno/api/whatsapp/webhook
     */
    @GetMapping
    public ResponseEntity<?> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge
    ) {
        log.info("[WhatsApp Webhook] Verificación recibida: mode={}, token={}, challenge={}",
                mode, token != null ? "***" : null, challenge);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("[WhatsApp Webhook] ✅ Verificación exitosa, respondiendo challenge");
            return ResponseEntity.ok(challenge);
        }

        log.warn("[WhatsApp Webhook] ❌ Verificación fallida: mode={}, token válido={}",
                mode, verifyToken.equals(token));
        return ResponseEntity.status(403).body("Verification failed");
    }

    /**
     * Recepción de notificaciones (POST).
     *
     * Meta envía notificaciones de:
     * - Mensajes entrantes
     * - Entregas de mensajes
     * - Lecturas de mensajes
     * - Errores
     *
     * Ejemplo de payload:
     * {
     *   "object": "whatsapp_business_account",
     *   "entry": [{
     *     "id": "WHATSAPP_BUSINESS_ACCOUNT_ID",
     *     "changes": [{
     *       "value": {
     *         "messaging_product": "whatsapp",
     *         "metadata": {
     *           "display_phone_number": "15551234567",
     *           "phone_number_id": "PHONE_NUMBER_ID"
     *         },
     *         "messages": [{
     *           "from": "5493547640108",
     *           "id": "wamid.xxx",
     *           "timestamp": "1234567890",
     *           "text": { "body": "Hola" },
     *           "type": "text"
     *         }],
     *         "statuses": [{
     *           "id": "wamid.xxx",
     *           "status": "delivered",
     *           "timestamp": "1234567890",
     *           "recipient_id": "5493547640108"
     *         }]
     *       },
     *       "field": "messages"
     *     }]
     *   }]
     * }
     */
    @PostMapping
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[WhatsApp Webhook] Notificación recibida: {}", payload);

        try {
            String object = (String) payload.get("object");

            if ("whatsapp_business_account".equals(object)) {
                // Procesar entrada
                Object entryObj = payload.get("entry");
                if (entryObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Map<String, Object>> entries =
                        (java.util.List<Map<String, Object>>) entryObj;

                    for (Map<String, Object> entry : entries) {
                        processEntry(entry);
                    }
                }

                return ResponseEntity.ok(Map.of("status", "received"));
            }

            log.warn("[WhatsApp Webhook] Tipo de objeto desconocido: {}", object);
            return ResponseEntity.ok(Map.of("status", "ignored"));

        } catch (Exception e) {
            log.error("[WhatsApp Webhook] Error procesando notificación: {}", e.getMessage(), e);
            // IMPORTANTE: Siempre responder 200 para que Meta no reintente
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return ResponseEntity.ok(Map.of("status", "error", "message", errorMsg));
        }
    }

    private void processEntry(Map<String, Object> entry) {
        Object changesObj = entry.get("changes");
        if (!(changesObj instanceof java.util.List)) return;

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> changes =
            (java.util.List<Map<String, Object>>) changesObj;

        for (Map<String, Object> change : changes) {
            String field = (String) change.get("field");
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) change.get("value");

            if ("messages".equals(field)) {
                processMessages(value);
            }

            if (value != null && value.containsKey("statuses")) {
                processStatuses(value);
            }
        }
    }

    private void processMessages(Map<String, Object> value) {
        Object messagesObj = value.get("messages");
        if (!(messagesObj instanceof java.util.List)) return;

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> messages =
            (java.util.List<Map<String, Object>>) messagesObj;

        for (Map<String, Object> message : messages) {
            String from = (String) message.get("from");
            String messageId = (String) message.get("id");
            String type = (String) message.get("type");
            String timestamp = (String) message.get("timestamp");

            log.info("[WhatsApp Webhook] 📨 Mensaje entrante:");
            log.info("  - De: {}", from);
            log.info("  - ID: {}", messageId);
            log.info("  - Tipo: {}", type);
            log.info("  - Timestamp: {}", timestamp);

            if ("text".equals(type)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> text = (Map<String, Object>) message.get("text");
                if (text != null) {
                    String body = (String) text.get("body");
                    log.info("  - Texto: {}", body);
                }
            }

            // TODO: Implementar lógica de respuesta automática si es necesario
            // Por ejemplo: confirmar cancelación de turno, etc.
        }
    }

    private void processStatuses(Map<String, Object> value) {
        Object statusesObj = value.get("statuses");
        if (!(statusesObj instanceof java.util.List)) return;

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> statuses =
            (java.util.List<Map<String, Object>>) statusesObj;

        for (Map<String, Object> status : statuses) {
            String messageId = (String) status.get("id");
            String statusType = (String) status.get("status");
            String recipientId = (String) status.get("recipient_id");
            String timestamp = (String) status.get("timestamp");

            log.info("[WhatsApp Webhook] 📮 Estado de mensaje:");
            log.info("  - ID: {}", messageId);
            log.info("  - Estado: {}", statusType);
            log.info("  - Destinatario: {}", recipientId);
            log.info("  - Timestamp: {}", timestamp);

            // Estados posibles: sent, delivered, read, failed
            if ("failed".equals(statusType)) {
                Object errorsObj = status.get("errors");
                log.error("[WhatsApp Webhook] ❌ Error en envío: {}", errorsObj);
            }
        }
    }
}
