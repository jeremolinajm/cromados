// src/main/java/com/cromados/barberia/service/HoldService.java
package com.cromados.barberia.service;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HoldService {
    public static record Key(Long barberoId, LocalDate fecha, LocalTime hora) {}
    private static class Hold { Instant expiresAt; String who; }

    private final Map<Key, Hold> holds = new ConcurrentHashMap<>();
    private static final int TTL_MINUTES = 10;

    public boolean tryHold(Long barberoId, LocalDate fecha, LocalTime hora, String who) {
        cleanup();
        Key k = new Key(barberoId, fecha, hora);
        Hold h = holds.get(k);
        Instant now = Instant.now();
        if (h != null && h.expiresAt.isAfter(now)) {
            return false; // ocupado
        }
        Hold nh = new Hold();
        nh.expiresAt = now.plusSeconds(TTL_MINUTES * 60L);
        nh.who = who;
        holds.put(k, nh);
        return true;
    }

    public void release(Long barberoId, LocalDate fecha, LocalTime hora) {
        holds.remove(new Key(barberoId, fecha, hora));
    }

    public boolean isHeld(Long barberoId, LocalDate fecha, LocalTime hora) {
        cleanup();
        Hold h = holds.get(new Key(barberoId, fecha, hora));
        return h != null && h.expiresAt.isAfter(Instant.now());
    }

    private void cleanup() {
        Instant now = Instant.now();
        holds.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }
}
