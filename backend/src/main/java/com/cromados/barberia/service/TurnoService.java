package com.cromados.barberia.service;

import com.cromados.barberia.dto.TurnoRequest;
import com.cromados.barberia.model.HorarioBarbero;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final BarberoRepository barberoRepository;
    private final SucursalRepository sucursalRepository;
    private final TipoCorteRepository tipoCorteRepository;
    private final HorarioBarberoRepository horarioBarberoRepository;  // Para validarFranjaAtencion
    private final BloqueoTurnoRepository bloqueoTurnoRepository;      // Para compatibilidad tabla legacy
    private final HorarioService horarioService;  // ✅ Usar servicio centralizado

    private static final DateTimeFormatter F_HORA = DateTimeFormatter.ofPattern("HH:mm");

    /* ===================== Disponibilidad ===================== */

    /**
     * Calcula horarios disponibles delegando a HorarioService (única fuente de verdad).
     */
    public List<LocalTime> horariosDisponibles(Long barberoId, LocalDate fecha) {
        return horarioService.horariosDisponibles(barberoId, fecha);
    }

    /* ===================== Crear turno ===================== */

    @Transactional
    public Turno crearTurno(TurnoRequest req) {
        Objects.requireNonNull(req, "TurnoRequest requerido");

        var sucursal = sucursalRepository.findById(req.getSucursalId())
                .orElseThrow(() -> new NoSuchElementException("Sucursal no encontrada"));
        var barbero = barberoRepository.findById(req.getBarberoId())
                .orElseThrow(() -> new NoSuchElementException("Barbero no encontrado"));
        var tipoCorte = tipoCorteRepository.findById(req.getTipoCorteId())
                .orElseThrow(() -> new NoSuchElementException("Servicio no encontrado"));

        LocalDate fecha = parseFecha(req.getFecha());
        LocalTime hora  = parseHora(req.getHora());
        if (fecha == null || hora == null) {
            throw new IllegalArgumentException("Fecha u hora inválidas (se esperan formatos YYYY-MM-DD y HH:mm)");
        }

        validarFranjaAtencion(barbero.getId(), fecha, hora);

        // 🔒 CRITICAL FIX: Use pessimistic locking to prevent race conditions
        // This query acquires a database-level write lock on conflicting bookings
        // preventing concurrent requests from double-booking the same slot
        Optional<Turno> conflicto = turnoRepository.findConflictingTurnoWithLock(
                barbero.getId(), fecha, hora);

        if (conflicto.isPresent()) {
            throw new IllegalArgumentException("El horario ya está reservado");
        }

        // Compatibilidad con tabla antigua (legacy bloqueo_turno table)
        boolean bloqueado = bloqueoTurnoRepository.findByBarbero_IdAndFecha(barbero.getId(), fecha)
                .stream().anyMatch(b -> b.getHora().equals(hora));
        if (bloqueado) throw new IllegalArgumentException("El horario está bloqueado");

        Turno t = new Turno();
        t.setSucursal(sucursal);
        t.setBarbero(barbero);
        t.setTipoCorte(tipoCorte);
        t.setFecha(fecha);
        t.setHora(hora);
        t.setEstado("PENDIENTE_PAGO");
        t.setPagoConfirmado(Boolean.FALSE);

        t.setClienteNombre(req.getClienteNombre());
        t.setClienteTelefono(req.getClienteTelefono());
        t.setClienteEdad(req.getClienteEdad());

        return turnoRepository.save(t);
    }

    /* ===================== Helpers ===================== */

    private void validarFranjaAtencion(Long barberoId, LocalDate fecha, LocalTime hora) {
        final int dow = fecha.getDayOfWeek().getValue();
        List<HorarioBarbero> franjas = horarioBarberoRepository
                .findByBarbero_IdAndDiaSemana(barberoId, dow);
        if (franjas == null || franjas.isEmpty()) {
            throw new IllegalArgumentException("El barbero no atiende ese día");
        }
        boolean dentro = franjas.stream().anyMatch(hb -> {
            LocalTime i = parseHora(hb.getInicio());
            LocalTime f = parseHora(hb.getFin());
            return i != null && f != null && !hora.isBefore(i) && !hora.isAfter(f);
        });
        if (!dentro) throw new IllegalArgumentException("Horario fuera del rango de atención");
    }

    private LocalTime parseHora(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            if (hhmm.length() == 5) return LocalTime.parse(hhmm, F_HORA);
            if (hhmm.length() >= 8) return LocalTime.parse(hhmm.substring(0,8));
            return LocalTime.parse(hhmm);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseFecha(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
        try {
            return LocalDate.parse(yyyymmdd);
        } catch (Exception e) {
            return null;
        }
    }
}