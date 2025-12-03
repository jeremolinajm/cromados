// src/main/java/com/cromados/barberia/controller/WhatsAppMetaController.java
package com.cromados.barberia.controller;

import com.cromados.barberia.service.WhatsAppMetaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller para envío de mensajes WhatsApp mediante Meta Cloud API.
 * Endpoints de prueba y envío de plantillas aprobadas.
 */
@Slf4j
@RestController
@RequestMapping("/api/whatsapp/meta")
@RequiredArgsConstructor
public class WhatsAppMetaController {

    private final WhatsAppMetaService whatsAppService;

    /**
     * Endpoint de prueba - envía template con datos de prueba.
     *
     * POST /api/whatsapp/meta/test
     * Body: {
     *   "to": "+5493547640108",
     *   "template": "turno_confirmado"
     * }
     */
    @PostMapping("/test")
    public ResponseEntity<?> sendTest(@RequestBody Map<String, String> request) {
        try {
            String to = request.get("to");
            String template = request.getOrDefault("template", "turno_confirmado");

            if (to == null || to.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Campo 'to' requerido"));
            }

            log.info("[WhatsApp Meta Test] Enviando template '{}' a {}", template, to);
            String messageId = whatsAppService.sendTest(to, template);

            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "messageId", messageId,
                "to", to,
                "template", template
            ));

        } catch (Exception e) {
            log.error("[WhatsApp Meta Test] Error: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Envía plantilla de confirmación de turno.
     *
     * POST /api/whatsapp/meta/confirmacion
     * Body: {
     *   "to": "+5493547640108",
     *   "nombre": "Juan Pérez",
     *   "fecha": "31/10/2025",
     *   "hora": "15:30",
     *   "barbero": "Carlos Gómez",
     *   "sucursal": "Sucursal Centro"
     * }
     */
    @PostMapping("/confirmacion")
    public ResponseEntity<?> sendConfirmacion(@RequestBody Map<String, String> request) {
        try {
            String to = request.get("to");
            String nombre = request.get("nombre");
            String fecha = request.get("fecha");
            String hora = request.get("hora");
            String barbero = request.get("barbero");
            String sucursal = request.get("sucursal");

            // Validaciones
            if (to == null || to.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Campo 'to' requerido"));
            }

            log.info("[WhatsApp Meta] Enviando confirmación a {} - {} {} {}",
                to, nombre, fecha, hora);

            String messageId = whatsAppService.sendConfirmacion(
                to, nombre, fecha, hora, barbero, sucursal
            );

            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "messageId", messageId,
                "to", to,
                "template", "turno_confirmado"
            ));

        } catch (Exception e) {
            log.error("[WhatsApp Meta] Error enviando confirmación: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Envía plantilla de recordatorio de turno.
     *
     * POST /api/whatsapp/meta/recordatorio
     * Body: {
     *   "to": "+5493547640108",
     *   "nombre": "Juan Pérez",
     *   "fecha": "31/10/2025",
     *   "hora": "15:30",
     *   "barbero": "Carlos Gómez",
     *   "sucursal": "Sucursal Centro"
     * }
     */
    @PostMapping("/recordatorio")
    public ResponseEntity<?> sendRecordatorio(@RequestBody Map<String, String> request) {
        try {
            String to = request.get("to");
            String nombre = request.get("nombre");
            String fecha = request.get("fecha");
            String hora = request.get("hora");
            String barbero = request.get("barbero");
            String sucursal = request.get("sucursal");

            if (to == null || to.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Campo 'to' requerido"));
            }

            log.info("[WhatsApp Meta] Enviando recordatorio a {} - {} {} {}",
                to, nombre, fecha, hora);

            String messageId = whatsAppService.sendRecordatorio(
                to, nombre, fecha, hora, barbero, sucursal
            );

            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "messageId", messageId,
                "to", to,
                "template", "recordatorio_turno"
            ));

        } catch (Exception e) {
            log.error("[WhatsApp Meta] Error enviando recordatorio: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
