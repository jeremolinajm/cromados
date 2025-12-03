// src/main/java/com/cromados/barberia/service/ZombieBookingReaperScheduler.java
package com.cromados.barberia.service;

import com.cromados.barberia.model.Pago;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.PagoRepository;
import com.cromados.barberia.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * THE REAPER - Zombie Booking Cleanup Service
 * Prevents abandoned carts from blocking booking slots forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZombieBookingReaperScheduler {

    private final TurnoRepository turnoRepository;
    private final PagoRepository pagoRepository;

    private static final int MAX_PENDING_MINUTES = 15;

    @Scheduled(cron = "0 0/5 * * * *")
    @Transactional
    public void reapZombieBookings() {
        log.info("[REAPER] Starting zombie booking cleanup...");

        try {
            Instant threshold = Instant.now().minus(MAX_PENDING_MINUTES, ChronoUnit.MINUTES);
            LocalDate today = LocalDate.now();

            // Find all PENDIENTE_PAGO bookings with old pending payments
            List<Turno> candidates = turnoRepository.findByEstadoAndFechaGreaterThanEqual("PENDIENTE_PAGO", today);

            int cancelled = 0;
            int kept = 0;
            int errors = 0;

            for (Turno turno : candidates) {
                try {
                    // Find associated payment
                    Pago pago = pagoRepository.findByTurnoId(turno.getId()).orElse(null);

                    if (pago == null) {
                        // No payment record - this shouldn't happen, but cancel to be safe
                        log.warn("[REAPER] Turno #{} has PENDIENTE_PAGO but no Pago record. Cancelling.", turno.getId());
                        cancelTurno(turno, "No payment record found");
                        cancelled++;
                        continue;
                    }

                    // Check if payment is old and still pending
                    if (pago.getCreadoEn() != null && pago.getCreadoEn().isBefore(threshold)) {
                        String status = pago.getStatus();
                        if ("pending".equalsIgnoreCase(status) || "in_process".equalsIgnoreCase(status)) {
                            // Zombie detected! Cancel it.
                            log.warn("[REAPER] Zombie detected - Turno #{} - Payment {} - Created {} - Status {}",
                                    turno.getId(), pago.getId(), pago.getCreadoEn(), status);
                            cancelTurno(turno, "Payment timeout - " + status);
                            cancelled++;
                        } else {
                            // Payment has a different status (approved, rejected, etc.)
                            // This shouldn't happen (should be handled by webhook), but don't cancel
                            log.warn("[REAPER] Turno #{} has PENDIENTE_PAGO but payment status is '{}'. Manual review needed.",
                                    turno.getId(), status);
                            kept++;
                        }
                    } else {
                        // Payment is recent, keep it
                        kept++;
                    }

                } catch (Exception e) {
                    errors++;
                    log.error("[REAPER] Error processing turno #{}: {}", turno.getId(), e.getMessage(), e);
                }
            }

            log.info("[REAPER] Cleanup complete - Cancelled: {}, Kept: {}, Errors: {}, Total processed: {}",
                    cancelled, kept, errors, candidates.size());

            // Alert if there are many zombies (might indicate a problem with webhooks)
            if (cancelled > 10) {
                log.error("[REAPER] HIGH ZOMBIE COUNT: {} bookings cancelled. Check MercadoPago webhook configuration!",
                        cancelled);
            }

        } catch (Exception e) {
            log.error("[REAPER] Fatal error in zombie cleanup: {}", e.getMessage(), e);
        }
    }

    private void cancelTurno(Turno turno, String reason) {
        turno.setEstado("CANCELADO");
        turnoRepository.save(turno);

        log.info("[REAPER] CANCELLED - Turno #{} - Barbero: {} - Fecha: {} {} - Reason: {}",
                turno.getId(),
                turno.getBarbero().getNombre(),
                turno.getFecha(),
                turno.getHora(),
                reason);
    }

    @Transactional
    public int reapNow() {
        log.warn("[REAPER] MANUAL TRIGGER - Running emergency cleanup");
        reapZombieBookings();
        return (int) turnoRepository.findByEstadoAndFechaGreaterThanEqual("PENDIENTE_PAGO", LocalDate.now()).size();
    }

    public ZombieStats getStats() {
        LocalDate today = LocalDate.now();
        List<Turno> pending = turnoRepository.findByEstadoAndFechaGreaterThanEqual("PENDIENTE_PAGO", today);

        Instant threshold = Instant.now().minus(MAX_PENDING_MINUTES, ChronoUnit.MINUTES);
        long zombies = pending.stream()
                .filter(t -> {
                    Pago pago = pagoRepository.findByTurnoId(t.getId()).orElse(null);
                    return pago != null &&
                           pago.getCreadoEn() != null &&
                           pago.getCreadoEn().isBefore(threshold);
                })
                .count();

        return new ZombieStats(pending.size(), (int) zombies);
    }

    public record ZombieStats(int totalPending, int zombies) {}
}
