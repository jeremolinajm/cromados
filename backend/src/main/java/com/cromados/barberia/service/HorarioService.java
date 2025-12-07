package com.cromados.barberia.service;

import com.cromados.barberia.model.DiaExcepcionalBarbero;
import com.cromados.barberia.model.HorarioBarbero;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.BloqueoTurnoRepository;
import com.cromados.barberia.repository.DiaExcepcionalBarberoRepository;
import com.cromados.barberia.repository.HorarioBarberoRepository;
import com.cromados.barberia.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio centralizado para el cálculo de horarios disponibles.
 *
 * ✅ ÚNICA FUENTE DE VERDAD para disponibilidad de horarios.
 * Usado por:
 * - Web (TurnoService)
 * - Bot de Telegram (todos los handlers)
 * - Admin (si necesita consultar disponibilidad)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HorarioService {

    private final HorarioBarberoRepository horarioBarberoRepository;
    private final DiaExcepcionalBarberoRepository diaExcepcionalBarberoRepository;
    private final TurnoRepository turnoRepository;
    private final BloqueoTurnoRepository bloqueoTurnoRepository;

    private static final int SLOT_MINUTES = 30;
    private static final DateTimeFormatter F_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Calcula los horarios disponibles para un barbero en una fecha específica.
     *
     * Considera:
     * - Días excepcionales (DiaExcepcionalBarbero) - PRIORIDAD 1
     * - Horarios laborales regulares (HorarioBarbero) - PRIORIDAD 2
     * - Turnos confirmados (pagoConfirmado=true)
     * - Turnos bloqueados (estado=BLOQUEADO)
     * - Turnos confirmados pero no pagados (estado=CONFIRMADO)
     * - Bloqueos manuales (BloqueoTurno - tabla legacy)
     * - Horarios pasados (si es hoy)
     *
     * PRIORIDAD: Si existe un día excepcional para la fecha, se usan SOLO esas franjas
     * (ignora completamente el horario regular del día de la semana).
     *
     * @param barberoId ID del barbero
     * @param fecha Fecha a consultar
     * @return Lista de horarios disponibles (LocalTime) ordenados
     */
    public List<LocalTime> horariosDisponibles(Long barberoId, LocalDate fecha) {
        // 1. Verificar si existe un día excepcional para esta fecha (PRIORIDAD MÁXIMA)
        List<DiaExcepcionalBarbero> franjasExcepcionales = diaExcepcionalBarberoRepository
                .findByBarbero_IdAndFecha(barberoId, fecha);

        List<FranjaHoraria> franjas = new ArrayList<>();

        if (franjasExcepcionales != null && !franjasExcepcionales.isEmpty()) {
            // Usar franjas excepcionales (días festivos, eventos especiales, etc.)
            log.debug("[HorarioService] Barbero {} trabaja día excepcional en {}: {} franjas",
                      barberoId, fecha, franjasExcepcionales.size());

            for (DiaExcepcionalBarbero deb : franjasExcepcionales) {
                franjas.add(new FranjaHoraria(deb.getInicio(), deb.getFin()));
            }
        } else {
            // Usar horarios regulares según día de la semana
            final int dow = fecha.getDayOfWeek().getValue(); // 1=Lunes, 7=Domingo
            List<HorarioBarbero> franjasRegulares = horarioBarberoRepository
                    .findByBarbero_IdAndDiaSemana(barberoId, dow);

            if (franjasRegulares == null || franjasRegulares.isEmpty()) {
                log.debug("[HorarioService] Barbero {} no trabaja el día {} (dow={})", barberoId, fecha, dow);
                return List.of();
            }

            for (HorarioBarbero hb : franjasRegulares) {
                franjas.add(new FranjaHoraria(hb.getInicio(), hb.getFin()));
            }
        }

        if (franjas.isEmpty()) {
            log.debug("[HorarioService] Barbero {} no tiene franjas disponibles para {}", barberoId, fecha);
            return List.of();
        }

        // 2. Obtener turnos ocupados
        Set<LocalTime> ocupados = turnoRepository.findByBarbero_IdAndFecha(barberoId, fecha)
                .stream()
                .filter(this::esTurnoOcupado)
                .map(Turno::getHora)
                .collect(Collectors.toSet());

        // 3. Obtener bloqueos manuales (tabla legacy - mantener compatibilidad)
        Set<LocalTime> bloqueados = bloqueoTurnoRepository.findByBarbero_IdAndFecha(barberoId, fecha)
                .stream()
                .map(b -> b.getHora())
                .collect(Collectors.toSet());

        // 4. Generar slots de 30 minutos dentro de las franjas
        SortedSet<LocalTime> libres = new TreeSet<>();
        for (FranjaHoraria franja : franjas) {
            LocalTime inicio = parseHora(franja.inicio);
            LocalTime fin = parseHora(franja.fin);

            if (inicio == null || fin == null || fin.isBefore(inicio)) {
                log.warn("[HorarioService] Franja inválida para barbero {}: {} - {}",
                         barberoId, franja.inicio, franja.fin);
                continue;
            }

            // Generar slots cada 30 minutos dentro de la franja
            LocalTime slot = inicio;
            while (slot.compareTo(fin) <= 0) {
                if (!ocupados.contains(slot) && !bloqueados.contains(slot)) {
                    libres.add(slot);
                }

                // Avanzar al siguiente slot
                LocalTime nextSlot = slot.plusMinutes(SLOT_MINUTES);
                // Si el nextSlot es menor que el slot actual, significa que pasó medianoche
                // Esto previene loops infinitos cuando fin es cercano a medianoche (ej: 23:30)
                if (nextSlot.isBefore(slot)) {
                    break;
                }
                slot = nextSlot;
            }
        }

        // 5. Filtrar horarios pasados si es hoy (usar zona horaria de Argentina)
        LocalDate hoy = LocalDate.now(ZONA_ARGENTINA);
        if (fecha.isEqual(hoy)) {
            LocalTime now = LocalTime.now(ZONA_ARGENTINA);
            // Truncar a minutos (ignorar segundos) para que el slot actual siga disponible
            // Ej: Si son las 21:29:58, el slot 21:30 debe estar disponible
            // Si son las 21:30:59, el slot 21:30 todavía debe estar disponible
            // Si son las 21:31:00, el slot 21:30 ya NO debe estar disponible
            LocalTime nowTruncated = LocalTime.of(now.getHour(), now.getMinute());

            log.debug("[HorarioService] Filtrando horarios pasados. Hora Argentina: {}, Truncada: {}", now, nowTruncated);

            libres = libres.stream()
                    .filter(t -> !t.isBefore(nowTruncated))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        log.debug("[HorarioService] Barbero {} en {}: {} slots libres de {} franjas, {} ocupados, {} bloqueados",
                  barberoId, fecha, libres.size(), franjas.size(), ocupados.size(), bloqueados.size());

        return new ArrayList<>(libres);
    }

    /**
     * Determina si un turno ocupa un slot de horario.
     *
     * Criterios:
     * - pagoConfirmado = true (reservas web confirmadas)
     * - estado = "CONFIRMADO" (reservas confirmadas manualmente)
     * - estado = "BLOQUEADO" (turnos presenciales desde Telegram)
     */
    private boolean esTurnoOcupado(Turno t) {
        return Boolean.TRUE.equals(t.getPagoConfirmado())
                || "CONFIRMADO".equalsIgnoreCase(String.valueOf(t.getEstado()))
                || "BLOQUEADO".equalsIgnoreCase(String.valueOf(t.getEstado()));
    }

    /**
     * Parsea una hora en formato String a LocalTime.
     * Soporta:
     * - "HH:mm" (ej: "09:30")
     * - "HH:mm:ss" (ej: "09:30:00")
     * - Otros formatos ISO
     */
    private LocalTime parseHora(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            if (hhmm.length() == 5) return LocalTime.parse(hhmm, F_HORA);
            if (hhmm.length() >= 8) return LocalTime.parse(hhmm.substring(0, 8));
            return LocalTime.parse(hhmm);
        } catch (Exception e) {
            log.warn("[HorarioService] Error parseando hora: {}", hhmm, e);
            return null;
        }
    }

    /**
     * Clase auxiliar interna para representar una franja horaria genérica.
     * Permite tratar uniformemente franjas regulares (HorarioBarbero) y excepcionales (DiaExcepcionalBarbero).
     */
    private static class FranjaHoraria {
        final String inicio;
        final String fin;

        FranjaHoraria(String inicio, String fin) {
            this.inicio = inicio;
            this.fin = fin;
        }
    }
}
