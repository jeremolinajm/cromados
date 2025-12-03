package com.cromados.barberia.controller;

import com.cromados.barberia.model.BloqueoTurno;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.repository.BloqueoTurnoRepository;
import lombok.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;


@RestController
@RequestMapping("/dev/whatsapp")
@RequiredArgsConstructor
public class WhatsappDevController {

    private final BarberoRepository barberoRepo;
    private final BloqueoTurnoRepository bloqueoRepo;

    @PostMapping("/commands")
    public ResponseEntity<?> command(@RequestParam String from, @RequestParam String text) {
        // match barbero por teléfono
        var barbero = barberoRepo.findAll().stream()
                .filter(b -> b.getTelefono()!=null && b.getTelefono().replaceAll("\\D+","")
                        .equals(from.replaceAll("\\D+","")))
                .findFirst().orElse(null);
        if (barbero==null) return ResponseEntity.status(403).body(Map.of("error","Teléfono no asociado a barbero"));

        String t = text.trim().toLowerCase();
        // formatos aceptados: "bloquear 2025-09-26 16:00"  /  "desbloquear 2025-09-26 16:00"
        var m = java.util.regex.Pattern
                .compile("^(bloquear|desbloquear)\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})$")
                .matcher(t);
        if (!m.find()) return ResponseEntity.badRequest().body(Map.of("error","Comando inválido"));

        boolean bloquear = m.group(1).equals("bloquear");
        var fecha = java.time.LocalDate.parse(m.group(2));
        var hora  = java.time.LocalTime.parse(m.group(3));

        var existente = bloqueoRepo.findByBarbero_IdAndFechaAndHora(barbero.getId(), fecha, hora);
        if (bloquear) {
            if (existente.isPresent()) return ResponseEntity.ok(Map.of("status","ya_bloqueado"));
            bloqueoRepo.save(BloqueoTurno.builder().barbero(barbero).fecha(fecha).hora(hora).build());
            return ResponseEntity.ok(Map.of("status","bloqueado"));
        } else {
            existente.ifPresent(bloqueoRepo::delete);
            return ResponseEntity.ok(Map.of("status","desbloqueado"));
        }
    }
}
