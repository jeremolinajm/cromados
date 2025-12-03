// src/main/java/com/cromados/barberia/controller/PagoController.java
package com.cromados.barberia.controller;

import com.cromados.barberia.dto.CheckoutRequest;
import com.cromados.barberia.service.PagoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /** Inicia el checkout: NO crea turno. Devuelve init_point para redirigir a MP. */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest req) {
        try {
            Map<String, Object> pref = pagoService.crearPreferencia(req);
            return ResponseEntity.ok(pref);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No se pudo iniciar el checkout",
                    "detail", e.getMessage()
            ));
        }
    }

    /** Webhook POST */
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(
            @RequestParam Map<String, String> queryParams,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId
    ) {
        try {
            // Validar firma si hay clave secreta configurada
            String dataId = queryParams.get("id");
            if (dataId != null && !pagoService.validateWebhookSignature(xSignature, xRequestId, dataId)) {
                System.err.println("Webhook rechazado: firma inválida");
                return ResponseEntity.status(401).build();
            }

            pagoService.procesarWebhook(queryParams);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Loguear SIEMPRE
            e.printStackTrace();
            // Igual devolvemos 200 para no provocar reintentos infinitos
            return ResponseEntity.ok().build();
        }
    }

    @GetMapping("/webhook")
    public ResponseEntity<?> webhookGet(
            @RequestParam Map<String, String> queryParams,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId
    ) {
        try {
            // Validar firma si hay clave secreta configurada
            String dataId = queryParams.get("id");
            if (dataId != null && !pagoService.validateWebhookSignature(xSignature, xRequestId, dataId)) {
                System.err.println("Webhook GET rechazado: firma inválida");
                return ResponseEntity.status(401).build();
            }

            pagoService.procesarWebhook(queryParams);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().build();
        }
    }

    // ---------------------------------------
    // Handler común para GET/POST
    // ---------------------------------------
    private ResponseEntity<?> handleWebhook(HttpServletRequest req) {
        try {
            // 1) Tomamos type / id / turnoId de query si existen
            String typeParam = req.getParameter("type");      // o "topic"
            if (typeParam == null) typeParam = req.getParameter("topic");
            String idParam   = req.getParameter("id");        // a veces viene "id" en query
            String turnoIdQs = req.getParameter("turnoId");   // si lo mandaste desde tu preferencia

            // 2) Si falta type o id, intentamos leer el body JSON (en POST)
            if (typeParam == null || idParam == null) {
                String body = readBody(req);
                if (!body.isBlank()) {
                    JsonNode json = objectMapper.readTree(body);
                    if (typeParam == null && json.hasNonNull("type")) {
                        typeParam = json.get("type").asText();
                    }
                    if (idParam == null && json.has("data") && json.get("data").hasNonNull("id")) {
                        idParam = json.get("data").get("id").asText();
                    }
                }
            }

            // 3) Si no es tipo "payment" o no hay id, respondemos 200 y salimos (no forzar reintentos)
            if (typeParam == null || !"payment".equalsIgnoreCase(typeParam) || idParam == null) {
                return ResponseEntity.ok().build();
            }

            // 4) Consultamos el pago en MP para poder leer metadata si hace falta
            Long paymentId = Long.valueOf(idParam);
            PaymentClient pclient = new PaymentClient();
            Payment payment = pclient.get(paymentId);

            // 5) Resolución de turnoId: query param o metadata.turnoId
            Long turnoId = null;
            if (turnoIdQs != null && !turnoIdQs.isBlank()) {
                turnoId = Long.valueOf(turnoIdQs);
            } else if (payment != null && payment.getMetadata() != null) {
                Object metaTurno = payment.getMetadata().get("turnoId");
                if (metaTurno instanceof Number n) turnoId = n.longValue();
                else if (metaTurno != null) turnoId = Long.valueOf(metaTurno.toString());
            }

            // 6) Si no logramos saber el turno, no rompemos el flujo (200 OK)
            if (turnoId == null) {
                // log.warn("Webhook payment {} sin turnoId.", paymentId);
                return ResponseEntity.ok().build();
            }

            // 7) Confirmar/crear el turno sólo si el pago fue aprobado
            String status = payment != null ? payment.getStatus() : null; // approved / pending / rejected...
            if ("approved".equalsIgnoreCase(status)) {
                // 👉 Opción A: si tu servicio crea el turno desde metadata internamente:
                //    (dejá tu método actual tal cual)
                Map<String, String> params = new HashMap<>();
                params.put("type", "payment");
                params.put("id", String.valueOf(paymentId));
                params.put("turnoId", String.valueOf(turnoId));
                pagoService.procesarWebhook(params); // tu método existente

                // 👉 Opción B (si preferís confirmar un turno existente):
                // pagoService.confirmarPagoDeTurno(turnoId, paymentId);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // 200 igual para que MP no reintente indefinidamente
            return ResponseEntity.ok().build();
        }
    }

    private String readBody(HttpServletRequest req) {
        try (BufferedReader reader = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
