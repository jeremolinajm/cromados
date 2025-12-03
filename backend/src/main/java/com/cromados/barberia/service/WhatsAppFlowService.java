// src/main/java/com/cromados/barberia/service/WhatsAppFlowService.java
package com.cromados.barberia.service;

import com.cromados.barberia.model.*;
import com.cromados.barberia.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppFlowService {

    private final BloqueoTurnoRepository bloqueoRepo;
    private final TurnoRepository turnoRepo;
    private final HorarioBarberoRepository horarioRepo;

    // Cache en memoria (se pierde al reiniciar)
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Estado temporal de cada conversación
    static class SessionState {
        String estado = "IDLE";
        String accion;
        LocalDate fechaTemp;
        LocalTime horaTemp;
        Barbero barbero;
        Instant ultimaActividad = Instant.now();
    }

    /**
     * Procesa el mensaje según el estado actual
     */
    public String procesarMensaje(String telefono, String body, Barbero barbero) {
        SessionState state = sessions.computeIfAbsent(telefono, k -> {
            var s = new SessionState();
            s.barbero = barbero;
            return s;
        });

        state.ultimaActividad = Instant.now();

        return switch (state.estado) {
            case "IDLE" -> handleIdle(state, body);
            case "WAITING_DATE" -> handleWaitingDate(state, body);
            case "WAITING_TIME" -> handleWaitingTime(state, body);
            default -> {
                reset(state);
                yield "⚠️ Sesión reiniciada. Envía *hola* para el menú.";
            }
        };
    }

    private String handleIdle(SessionState s, String body) {
        String cmd = body.toLowerCase().trim();

        if (cmd.equals("bloquear") || cmd.contains("bloquear")) {
            s.estado = "WAITING_DATE";
            s.accion = "bloquear";

            StringBuilder sugerencias = generarFechasSugeridas(s.barbero);

            return String.format("""
                📅 *Bloquear turno*
                
                %s
                Envía la fecha en formato:
                DD/MM/YYYY
                
                Ejemplo: 25/10/2025
                """, sugerencias.length() > 0 ? sugerencias.toString() : "");
        }

        if (cmd.equals("desbloquear") || cmd.contains("desbloquear")) {
            s.estado = "WAITING_DATE";
            s.accion = "desbloquear";

            StringBuilder bloqueosActivos = listarBloqueos(s.barbero);

            return String.format("""
                🔓 *Desbloquear turno*
                
                %s
                Envía la fecha del turno bloqueado:
                DD/MM/YYYY
                
                Ejemplo: 25/10/2025
                """, bloqueosActivos.length() > 0 ? bloqueosActivos.toString() : "⚠️ No tenés bloqueos activos.\n\n");
        }

        if (cmd.equals("listar") || cmd.contains("listar") || cmd.contains("próximos")) {
            return listarProximosTurnos(s.barbero);
        }

        return "❌ No entendí tu mensaje.\nEnvía *hola* para ver el menú.";
    }

    private String handleWaitingDate(SessionState s, String body) {
        if (body.equalsIgnoreCase("cancelar")) {
            reset(s);
            return "❌ Operación cancelada.";
        }

        try {
            LocalDate fecha = LocalDate.parse(body.trim(), DATE_FMT);

            if (fecha.isBefore(LocalDate.now())) {
                return "❌ La fecha no puede ser pasada.\nIntenta de nuevo (DD/MM/YYYY):";
            }

            // Validar si el barbero trabaja ese día
            int diaSemana = fecha.getDayOfWeek().getValue(); // 1=Lun, 7=Dom
            List<HorarioBarbero> horariosDelDia = horarioRepo.findByBarbero_IdAndDiaSemana(
                    s.barbero.getId(),
                    diaSemana
            );

            if (horariosDelDia.isEmpty()) {
                String dia = fecha.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.FULL,
                        Locale.forLanguageTag("es-AR")
                );
                return String.format("""
                    ❌ No trabajás los %s
                    
                    Elegí otra fecha (DD/MM/YYYY):
                    """, dia);
            }

            s.fechaTemp = fecha;
            s.estado = "WAITING_TIME";

            String dia = fecha.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL,
                    Locale.forLanguageTag("es-AR")
            );

            // Mostrar horarios disponibles
            StringBuilder horarios = new StringBuilder();
            for (HorarioBarbero h : horariosDelDia) {
                horarios.append(String.format("  • %s a %s\n", h.getInicio(), h.getFin()));
            }

            return String.format("""
                ✅ Fecha: %s (%s)
                
                📋 Tus horarios ese día:
                %s
                ⏰ Envía la hora a %s:
                HH:MM (ej: 14:30)
                """, fecha.format(DATE_FMT), dia, horarios.toString(),
                    "bloquear".equals(s.accion) ? "bloquear" : "desbloquear");

        } catch (DateTimeParseException e) {
            return "❌ Formato incorrecto.\nUsa: DD/MM/YYYY\nEjemplo: 25/10/2025";
        }
    }

    private String handleWaitingTime(SessionState s, String body) {
        if (body.equalsIgnoreCase("cancelar")) {
            reset(s);
            return "❌ Operación cancelada.";
        }

        try {
            LocalTime hora = LocalTime.parse(body.trim(), TIME_FMT);

            // Validar si la hora está dentro del horario laboral
            int diaSemana = s.fechaTemp.getDayOfWeek().getValue();
            List<HorarioBarbero> horariosDelDia = horarioRepo.findByBarbero_IdAndDiaSemana(
                    s.barbero.getId(),
                    diaSemana
            );

            boolean dentroDeHorario = horariosDelDia.stream().anyMatch(h -> {
                try {
                    LocalTime inicio = LocalTime.parse(h.getInicio(), TIME_FMT);
                    LocalTime fin = LocalTime.parse(h.getFin(), TIME_FMT);
                    return !hora.isBefore(inicio) && !hora.isAfter(fin);
                } catch (Exception e) {
                    return false;
                }
            });

            if (!dentroDeHorario) {
                StringBuilder horarios = new StringBuilder();
                for (HorarioBarbero h : horariosDelDia) {
                    horarios.append(String.format("  • %s a %s\n", h.getInicio(), h.getFin()));
                }
                return String.format("""
                    ❌ Esa hora está fuera de tu horario laboral.
                    
                    📋 Tus horarios ese día:
                    %s
                    Envía una hora válida (HH:MM):
                    """, horarios.toString());
            }

            s.horaTemp = hora;

            String resultado = "bloquear".equals(s.accion)
                    ? ejecutarBloqueo(s)
                    : ejecutarDesbloqueo(s);

            reset(s);
            return resultado;

        } catch (DateTimeParseException e) {
            return "❌ Formato incorrecto.\nUsa: HH:MM\nEjemplo: 14:30";
        }
    }

    private String ejecutarBloqueo(SessionState s) {
        Barbero b = s.barbero;
        LocalDate fecha = s.fechaTemp;
        LocalTime hora = s.horaTemp;

        // PRIMERO: Verificar si hay turno CONFIRMADO/PAGADO
        boolean tieneTurnoConfirmado = turnoRepo.findByBarbero_IdAndFecha(b.getId(), fecha).stream()
                .anyMatch(t -> t.getHora().equals(hora) && Boolean.TRUE.equals(t.getPagoConfirmado()));

        if (tieneTurnoConfirmado) {
            return String.format("""
                ❌ *No se puede bloquear*
                
                Ya existe un turno PAGADO en:
                📅 %s
                ⏰ %s
                
                El cliente ya reservó y pagó este horario.
                """, fecha.format(DATE_FMT), hora.format(TIME_FMT));
        }

        // SEGUNDO: Verificar si YA está bloqueado manualmente
        var bloqueoExistente = bloqueoRepo.findByBarbero_IdAndFechaAndHora(b.getId(), fecha, hora);
        if (bloqueoExistente.isPresent()) {
            return String.format("""
                ⚠️ *Ya está bloqueado*
                
                Este horario ya fue bloqueado anteriormente:
                📅 %s
                ⏰ %s
                """, fecha.format(DATE_FMT), hora.format(TIME_FMT));
        }

        // TODO OK: Crear bloqueo
        BloqueoTurno bloqueo = BloqueoTurno.builder()
                .barbero(b)
                .fecha(fecha)
                .hora(hora)
                .build();

        bloqueoRepo.save(bloqueo);

        return String.format("""
            ✅ *Turno bloqueado*
            
            👤 %s
            📅 %s
            ⏰ %s
            
            Este horario ya no aparecerá en la web.
            """, b.getNombre(), fecha.format(DATE_FMT), hora.format(TIME_FMT));
    }

    private String ejecutarDesbloqueo(SessionState s) {
        Barbero b = s.barbero;
        LocalDate fecha = s.fechaTemp;
        LocalTime hora = s.horaTemp;

        // Verificar si existe un bloqueo MANUAL
        var bloqueo = bloqueoRepo.findByBarbero_IdAndFechaAndHora(b.getId(), fecha, hora);

        if (bloqueo.isEmpty()) {
            // Verificar si en realidad hay un turno pagado (no bloqueado manualmente)
            boolean tieneTurnoPagado = turnoRepo.findByBarbero_IdAndFecha(b.getId(), fecha).stream()
                    .anyMatch(t -> t.getHora().equals(hora) && Boolean.TRUE.equals(t.getPagoConfirmado()));

            if (tieneTurnoPagado) {
                return String.format("""
                    ⚠️ *No es un bloqueo manual*
                    
                    📅 %s - ⏰ %s
                    
                    Este horario está ocupado por un turno PAGADO,
                    no por un bloqueo manual.
                    
                    No se puede desbloquear.
                    """, fecha.format(DATE_FMT), hora.format(TIME_FMT));
            }

            return String.format("""
                ⚠️ *No hay bloqueo*
                
                El horario está libre:
                📅 %s
                ⏰ %s
                """, fecha.format(DATE_FMT), hora.format(TIME_FMT));
        }

        // Eliminar bloqueo manual
        bloqueoRepo.delete(bloqueo.get());

        return String.format("""
            ✅ *Turno desbloqueado*
            
            📅 %s
            ⏰ %s
            
            Este horario vuelve a estar disponible en la web.
            """, fecha.format(DATE_FMT), hora.format(TIME_FMT));
    }

    private String listarProximosTurnos(Barbero barbero) {
        LocalDate hoy = LocalDate.now();

        var turnos = new ArrayList<Turno>();
        for (int i = 0; i <= 7; i++) {
            var dia = hoy.plusDays(i);
            turnos.addAll(turnoRepo.findByBarbero_IdAndFecha(barbero.getId(), dia));
        }

        var confirmados = turnos.stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado()))
                .sorted(Comparator.comparing(Turno::getFecha)
                        .thenComparing(Turno::getHora))
                .limit(10)
                .toList();

        if (confirmados.isEmpty()) {
            return "📭 No tenés turnos confirmados en los próximos 7 días.";
        }

        StringBuilder sb = new StringBuilder("📋 *Próximos turnos*\n\n");
        for (Turno t : confirmados) {
            sb.append(String.format("""
                📅 %s ⏰ %s
                👤 %s
                💇 %s
                
                """,
                    t.getFecha().format(DATE_FMT),
                    t.getHora().format(TIME_FMT),
                    t.getClienteNombre(),
                    t.getTipoCorte() != null ? t.getTipoCorte().getNombre() : "-"
            ));
        }

        return sb.toString();
    }

    private void reset(SessionState s) {
        s.estado = "IDLE";
        s.accion = null;
        s.fechaTemp = null;
        s.horaTemp = null;
    }

    /**
     * Genera lista de próximas 7 fechas en las que el barbero trabaja
     */
    private StringBuilder generarFechasSugeridas(Barbero barbero) {
        StringBuilder sb = new StringBuilder("🗓️ *Próximos días laborales:*\n");
        LocalDate hoy = LocalDate.now();
        int count = 0;

        for (int i = 0; i < 30 && count < 7; i++) {
            LocalDate fecha = hoy.plusDays(i);
            int diaSemana = fecha.getDayOfWeek().getValue();

            var horarios = horarioRepo.findByBarbero_IdAndDiaSemana(barbero.getId(), diaSemana);
            if (!horarios.isEmpty()) {
                String dia = fecha.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.SHORT,
                        Locale.forLanguageTag("es-AR")
                );
                sb.append(String.format("  • %s (%s)\n", fecha.format(DATE_FMT), dia));
                count++;
            }
        }
        sb.append("\n");
        return sb;
    }

    /**
     * Lista bloqueos activos del barbero (próximos 30 días)
     */
    private StringBuilder listarBloqueos(Barbero barbero) {
        LocalDate hoy = LocalDate.now();

        List<BloqueoTurno> bloqueos = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            LocalDate fecha = hoy.plusDays(i);
            bloqueos.addAll(bloqueoRepo.findByBarbero_IdAndFecha(barbero.getId(), fecha));
        }

        if (bloqueos.isEmpty()) {
            return new StringBuilder();
        }

        // Ordenar por fecha/hora
        bloqueos.sort(Comparator.comparing(BloqueoTurno::getFecha)
                .thenComparing(BloqueoTurno::getHora));

        StringBuilder sb = new StringBuilder("🚫 *Bloqueos activos:*\n");
        int count = 0;
        for (BloqueoTurno b : bloqueos) {
            if (count >= 10) break;
            String dia = b.getFecha().getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.SHORT,
                    Locale.forLanguageTag("es-AR")
            );
            sb.append(String.format("  • %s (%s) %s\n",
                    b.getFecha().format(DATE_FMT),
                    dia,
                    b.getHora().format(TIME_FMT)
            ));
            count++;
        }
        sb.append("\n");
        return sb;
    }

    /**
     * Limpiar sesiones inactivas cada hora
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldSessions() {
        Instant cutoff = Instant.now().minusSeconds(1800); // 30 min
        sessions.entrySet().removeIf(e -> e.getValue().ultimaActividad.isBefore(cutoff));
        log.debug("[WhatsApp] {} sesiones activas", sessions.size());
    }
}