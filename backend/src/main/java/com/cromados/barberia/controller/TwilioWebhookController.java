// src/main/java/com/cromados/barberia/controller/TwilioWebhookController.java
package com.cromados.barberia.controller;

import com.cromados.barberia.repository.BloqueoTurnoRepository;
import com.cromados.barberia.model.BloqueoTurno;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/twilio")
@RequiredArgsConstructor
public class TwilioWebhookController {

    private final BloqueoTurnoRepository bloqueoRepo;

    /**
     * Webhook principal para mensajes entrantes de Twilio WhatsApp Sandbox
     * URL: https://api.cromados.uno/twilio/webhook
     */
    @PostMapping("/webhook")
    public String webhook(@RequestParam("From") String from,
                          @RequestParam("Body") String body,
                          @RequestParam(value = "MessageSid", required = false) String messageSid) {
        // Log del mensaje entrante
        System.out.println("[Twilio Webhook] From: " + from + " | Body: " + body + " | MessageSid: " + messageSid);

        // Por ahora solo responder con confirmación
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>Mensaje recibido. Gracias por contactarnos!</Message></Response>";
    }

    /**
     * Legacy endpoint (mantener para compatibilidad)
     */
    @PostMapping("/wh/whatsapp")
    public String incoming(@RequestParam("From") String from,
                           @RequestParam("Body") String body) {
        // Comando: BLOQUEAR {barberoId} {yyyy-MM-dd} {HH:mm}
        String[] p = body.trim().split("\\s+");
        if (p.length == 4 && "BLOQUEAR".equalsIgnoreCase(p[0])) {
            Long barberoId = Long.parseLong(p[1]);
            LocalDate fecha = LocalDate.parse(p[2]);
            LocalTime hora = LocalTime.parse(p[3]);

            var existente = bloqueoRepo.findByBarbero_IdAndFechaAndHora(barberoId, fecha, hora);
            if (existente.isEmpty()) {
                BloqueoTurno b = new BloqueoTurno();
                b.setFecha(fecha);
                b.setHora(hora);
                // si tu entidad relaciona Barbero, setear b.setBarbero(...)
                // o b.setBarberoId si tenés el campo
                bloqueoRepo.save(b);
            }
            return "Turno bloqueado " + fecha + " " + hora;
        }
        return "Formato: BLOQUEAR {barberoId} {yyyy-MM-dd} {HH:mm}";
    }
}
