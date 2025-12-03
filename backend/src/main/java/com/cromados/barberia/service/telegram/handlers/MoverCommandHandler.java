package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.HorarioBarbero;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handler para el comando /mover.
 *
 * Permite mover un turno (web o presencial) a otra fecha/hora.
 * Flujo:
 * 1. Mostrar lista de próximos turnos con botones
 * 2. Seleccionar turno a mover
 * 3. Ingresar nueva fecha
 * 4. Seleccionar nueva hora (botones con horarios disponibles)
 * 5. Confirmar movimiento
 */
@Slf4j
@Component
public class MoverCommandHandler extends BaseCommandHandler {

    private static final String STEP_TURNO_SELECTION = "WAITING_TURNO_SELECTION_MOVER";
    private static final String STEP_MONTH_SELECTION = "WAITING_MONTH_SELECTION_MOVER";
    private static final String STEP_DAY_SELECTION = "WAITING_DAY_SELECTION_MOVER";
    private static final String STEP_CUSTOM_DATE = "WAITING_CUSTOM_DATE_MOVER";
    private static final String STEP_NEW_TIME = "WAITING_NEW_TIME_MOVER";
    private static final String STEP_CONFIRM = "WAITING_CONFIRM_MOVER";

    public MoverCommandHandler(
            TurnoRepository turnoRepo,
            BarberoRepository barberoRepo,
            TipoCorteRepository tipoCorteRepo,
            SucursalRepository sucursalRepo,
            HorarioBarberoRepository horarioRepo,
            TelegramMessageBuilder messageBuilder,
            TelegramLongPollingBot bot,
            com.cromados.barberia.service.HorarioService horarioService
    ) {
        super(turnoRepo, barberoRepo, tipoCorteRepo, sucursalRepo, horarioRepo, messageBuilder, bot, horarioService);
    }

    @Override
    public String getCommandName() {
        return "mover";
    }

    @Override
    public boolean canHandle(String step) {
        return step.startsWith("WAITING_") && step.endsWith("_MOVER");
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        state.setStep(STEP_TURNO_SELECTION);

        return showTurnoSelection(chatId, state);
    }

    @Override
    public String handleCallback(Long chatId, String callbackData, SessionState state) {
        if (callbackData.equals("CANCEL")) {
            state.reset();
            return "❌ Movimiento de turno cancelado";
        }

        String[] parts = callbackData.split("_", 2);
        String action = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        return switch (action) {
            case "MOVE" -> handleTurnoSelection(chatId, value, state);
            case "MONTHM" -> handleMonthSelection(chatId, value, state);
            case "DAYM" -> handleDaySelection(chatId, value, state);
            case "TIME" -> handleTimeSelection(chatId, value, state);
            case "CONFIRM" -> handleConfirmation(value, chatId, state);
            default -> "❌ Acción no reconocida";
        };
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        if (STEP_CUSTOM_DATE.equals(state.getStep())) {
            return handleCustomDateInput(chatId, text, state);
        }
        return "❌ Entrada no esperada. Usá los botones para navegar.";
    }

    /**
     * Muestra lista de turnos disponibles para mover.
     */
    private String showTurnoSelection(Long chatId, SessionState state) {
        Barbero barbero = getBarbero(state);
        LocalDate hoy = LocalDate.now();

        // Buscar turnos futuros
        LocalDate limitemax = hoy.plusMonths(2);
        List<Turno> turnos = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(
                barbero.getId(),
                hoy,
                limitemax
        ).stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado()) || "BLOQUEADO".equals(t.getEstado()))
                .limit(20)
                .toList();

        if (turnos.isEmpty()) {
            state.reset();
            return "📭 No tenés turnos futuros para mover";
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Turno t : turnos) {
            String tipo = "BLOQUEADO".equals(t.getEstado()) ? "🔒" : "✅";
            String label = String.format("%s %s %s - %s (%s)",
                    tipo,
                    t.getFecha().format(DATE_FMT),
                    t.getHora().format(TIME_FMT),
                    t.getClienteNombre(),
                    t.getTipoCorte().getNombre()
            );
            rows.add(messageBuilder.createSingleButtonRow(label, "MOVE_" + t.getId()));
        }

        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId, "📋 Seleccioná el turno que querés mover:", keyboard);
    }

    /**
     * Maneja la selección del turno a mover.
     */
    private String handleTurnoSelection(Long chatId, String value, SessionState state) {
        try {
            Long turnoId = Long.parseLong(value);
            Turno turno = turnoRepo.findById(turnoId).orElse(null);

            if (turno == null) {
                state.reset();
                return "❌ Turno no encontrado";
            }

            state.setTempTurnoIdToMove(turnoId);
            state.setStep(STEP_MONTH_SELECTION);

            return showMonthSelection(chatId, turno, state);
        } catch (NumberFormatException e) {
            return "❌ ID de turno inválido";
        }
    }

    /**
     * Muestra botones para seleccionar mes.
     */
    private String showMonthSelection(Long chatId, Turno turno, SessionState state) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        YearMonth actual = YearMonth.now();

        // Mes actual
        rows.add(messageBuilder.createSingleButtonRow(
                "📅 " + formatYearMonth(actual) + " (Mes actual)",
                "MONTHM_ACTUAL"
        ));

        // Próximos 3 meses
        for (int i = 1; i <= 3; i++) {
            YearMonth mes = actual.plusMonths(i);
            rows.add(messageBuilder.createSingleButtonRow(
                    "📅 " + formatYearMonth(mes),
                    "MONTHM_" + mes.toString()
            ));
        }

        // Fecha específica
        rows.add(messageBuilder.createSingleButtonRow(
                "🗓️ Fecha específica",
                "MONTHM_CUSTOM"
        ));

        // Cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = String.format("""
                📅 Turno seleccionado:
                %s %s - %s
                %s

                Seleccioná el mes para la nueva fecha:
                """,
                turno.getFecha().format(DATE_FMT),
                turno.getHora().format(TIME_FMT),
                turno.getClienteNombre(),
                turno.getTipoCorte().getNombre()
        );

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de mes.
     */
    private String handleMonthSelection(Long chatId, String value, SessionState state) {
        if ("ACTUAL".equals(value)) {
            state.setTempYearMonth(YearMonth.now());
        } else if ("CUSTOM".equals(value)) {
            state.setStep(STEP_CUSTOM_DATE);
            return "📅 Ingresá la fecha específica en formato DD/MM/YYYY o escribí 'hoy':";
        } else {
            try {
                state.setTempYearMonth(YearMonth.parse(value));
            } catch (DateTimeParseException e) {
                return "❌ Formato de mes inválido. Usá los botones.";
            }
        }

        state.setStep(STEP_DAY_SELECTION);
        return showDaySelection(chatId, state);
    }

    /**
     * Muestra días del mes seleccionado.
     */
    private String showDaySelection(Long chatId, SessionState state) {
        YearMonth yearMonth = state.getTempYearMonth();
        LocalDate inicio = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();
        LocalDate hoy = LocalDate.now();

        Barbero barbero = getBarbero(state);

        // Obtener días de la semana habilitados del barbero
        List<Integer> diasHabilitados = horarioRepo.findByBarbero_Id(barbero.getId())
                .stream()
                .map(com.cromados.barberia.model.HorarioBarbero::getDiaSemana)
                .distinct()
                .toList();

        if (diasHabilitados.isEmpty()) {
            state.reset();
            return "❌ No tenés horarios configurados. Configurá tus horarios en el panel web.";
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Mostrar solo días del mes que coincidan con días habilitados del barbero
        for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
            if (fecha.isBefore(hoy)) {
                continue; // Saltar días pasados
            }

            // Verificar si el día de la semana está habilitado para el barbero
            int diaSemana = fecha.getDayOfWeek().getValue();
            if (!diasHabilitados.contains(diaSemana)) {
                continue; // Saltar días no habilitados
            }

            String dayLabel = fecha.format(DATE_FMT) + " - " +
                fecha.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, new Locale("es", "AR"));
            rows.add(messageBuilder.createSingleButtonRow(dayLabel, "DAYM_" + fecha.toString()));
        }

        if (rows.isEmpty()) {
            state.setStep(STEP_MONTH_SELECTION);
            return "❌ No tenés días habilitados en " + formatYearMonth(yearMonth) + ". Seleccioná otro mes.";
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(
                chatId,
                "📅 Días de " + formatYearMonth(yearMonth) + "\n\nSeleccioná un día:",
                keyboard,
                state
        );
    }

    /**
     * Maneja la selección de día.
     */
    private String handleDaySelection(Long chatId, String value, SessionState state) {
        try {
            LocalDate fecha = LocalDate.parse(value);
            state.setTempFecha(fecha);
            state.setStep(STEP_NEW_TIME);
            return showAvailableTimes(chatId, state, fecha);
        } catch (DateTimeParseException e) {
            return "❌ Fecha inválida";
        }
    }

    /**
     * Maneja input de fecha personalizada.
     */
    private String handleCustomDateInput(Long chatId, String text, SessionState state) {
        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        LocalDate nuevaFecha;

        if (text.equalsIgnoreCase("hoy")) {
            nuevaFecha = LocalDate.now();
        } else {
            try {
                nuevaFecha = LocalDate.parse(text, DATE_FMT);
            } catch (DateTimeParseException e) {
                return "❌ Formato inválido. Usá DD/MM/YYYY (ejemplo: 25/12/2024) o escribí 'hoy'";
            }
        }

        if (nuevaFecha.isBefore(LocalDate.now())) {
            return "❌ No podés mover un turno a una fecha pasada. Ingresá otra fecha:";
        }

        state.setTempFecha(nuevaFecha);
        state.setStep(STEP_NEW_TIME);

        return showAvailableTimes(chatId, state, nuevaFecha);
    }

    /**
     * Muestra horarios disponibles para la nueva fecha.
     * ✅ USA TurnoService.horariosDisponibles() como única fuente de verdad.
     */
    private String showAvailableTimes(Long chatId, SessionState state, LocalDate fecha) {
        Barbero barbero = getBarbero(state);

        // ✅ USAR ÚNICA FUENTE DE VERDAD: HorarioService.horariosDisponibles()
        List<LocalTime> disponibles = horarioService.horariosDisponibles(barbero.getId(), fecha);

        if (disponibles.isEmpty()) {
            state.setStep(STEP_MONTH_SELECTION);
            return "❌ No hay horarios disponibles para el " + fecha.format(DATE_FMT) + ". Seleccioná otra fecha.";
        }

        // ✅ EXCLUIR el turno que estamos moviendo (ya que su slot debería estar disponible)
        List<Turno> turnosExistentes = turnoRepo.findByBarbero_IdAndFecha(barbero.getId(), fecha);
        List<LocalTime> ocupadosPorOtros = turnosExistentes.stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado()) || "BLOQUEADO".equals(t.getEstado()))
                .filter(t -> !t.getId().equals(state.getTempTurnoIdToMove()))
                .map(Turno::getHora)
                .toList();

        // Agregar de vuelta el slot del turno que estamos moviendo si no está en disponibles
        LocalTime horaTurnoAMover = turnoRepo.findById(state.getTempTurnoIdToMove())
                .map(Turno::getHora)
                .orElse(null);

        if (horaTurnoAMover != null && !horaTurnoAMover.equals(LocalTime.of(0, 0))) {
            if (!disponibles.contains(horaTurnoAMover) && !ocupadosPorOtros.contains(horaTurnoAMover)) {
                disponibles = new ArrayList<>(disponibles);
                disponibles.add(horaTurnoAMover);
                disponibles.sort(LocalTime::compareTo);
            }
        }

        state.setHorariosDisponibles(disponibles);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Botón "Fuera de horario"
        rows.add(messageBuilder.createSingleButtonRow("⏰ Fuera de horario (FH)", "TIME_FH"));

        // Horarios disponibles (2 por fila)
        for (int i = 0; i < disponibles.size(); i++) {
            if (i % 2 == 0) {
                rows.add(new ArrayList<>());
            }
            LocalTime hora = disponibles.get(i);
            rows.get(rows.size() - 1).add(messageBuilder.buildButton(
                    hora.format(TIME_FMT),
                    "TIME_" + i
            ));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(
                chatId,
                "⏰ Seleccioná el nuevo horario para el " + fecha.format(DATE_FMT) + ":",
                keyboard,
                state
        );
    }

    /**
     * Maneja la selección de hora.
     */
    private String handleTimeSelection(Long chatId, String value, SessionState state) {
        LocalTime nuevaHora;

        if ("FH".equals(value)) {
            nuevaHora = LocalTime.of(0, 0); // Marcador especial
        } else {
            try {
                int index = Integer.parseInt(value);
                nuevaHora = state.getHorariosDisponibles().get(index);
            } catch (Exception e) {
                return "❌ Horario inválido";
            }
        }

        state.setTempHora(nuevaHora);
        state.setStep(STEP_CONFIRM);

        return showConfirmation(chatId, state);
    }

    /**
     * Muestra confirmación del movimiento.
     */
    private String showConfirmation(Long chatId, SessionState state) {
        Turno turno = turnoRepo.findById(state.getTempTurnoIdToMove()).orElse(null);
        if (turno == null) {
            state.reset();
            return "❌ Error: turno no encontrado";
        }

        String horaStr = state.getTempHora().equals(LocalTime.of(0, 0))
                ? "Fuera de horario (FH)"
                : state.getTempHora().format(TIME_FMT);

        String mensaje = String.format("""
                ⚠️ Confirmá el movimiento:

                📅 Fecha anterior: %s %s
                📅 Nueva fecha: %s %s

                👤 %s
                💇 %s

                ¿Confirmar movimiento?
                """,
                turno.getFecha().format(DATE_FMT),
                turno.getHora().format(TIME_FMT),
                state.getTempFecha().format(DATE_FMT),
                horaStr,
                turno.getClienteNombre(),
                turno.getTipoCorte().getNombre()
        );

        List<List<InlineKeyboardButton>> rows = messageBuilder.createConfirmationButtons(
                "CONFIRM_YES",
                "CONFIRM_NO"
        );

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la confirmación final.
     */
    private String handleConfirmation(String value, Long chatId, SessionState state) {
        if ("NO".equals(value)) {
            state.reset();
            return "❌ Movimiento cancelado";
        }

        if (!"YES".equals(value)) {
            return "❌ Respuesta inválida";
        }

        return ejecutarMovimiento(chatId, state);
    }

    /**
     * Ejecuta el movimiento del turno.
     */
    private String ejecutarMovimiento(Long chatId, SessionState state) {
        Turno turno = turnoRepo.findById(state.getTempTurnoIdToMove()).orElse(null);

        if (turno == null) {
            state.reset();
            return "❌ Error: turno no encontrado";
        }

        LocalDate fechaAnterior = turno.getFecha();
        LocalTime horaAnterior = turno.getHora();

        // Actualizar turno
        turno.setFecha(state.getTempFecha());
        turno.setHora(state.getTempHora());

        try {
            turnoRepo.save(turno);

            String horaStr = state.getTempHora().equals(LocalTime.of(0, 0))
                    ? "FH"
                    : state.getTempHora().format(TIME_FMT);

            // Notificar al admin
            String tipoTurno = "BLOQUEADO".equals(turno.getEstado()) ? "Presencial" : "Web";
            String notificacion = String.format("""
                    🔄 TURNO MOVIDO (%s)

                    👤 %s
                    💇 %s
                    🏪 %s

                    📅 Antes: %s %s
                    📅 Ahora: %s %s

                    Movido por: %s
                    """,
                    tipoTurno,
                    turno.getClienteNombre(),
                    turno.getTipoCorte().getNombre(),
                    turno.getSucursal().getNombre(),
                    fechaAnterior.format(DATE_FMT),
                    horaAnterior.format(TIME_FMT),
                    state.getTempFecha().format(DATE_FMT),
                    horaStr,
                    state.getBarbero().getNombre()
            );

            notificarAdmin(notificacion);

            state.reset();

            return String.format("""
                    ✅ Turno movido exitosamente

                    📅 Nueva fecha: %s %s
                    👤 %s
                    💇 %s
                    """,
                    turno.getFecha().format(DATE_FMT),
                    horaStr,
                    turno.getClienteNombre(),
                    turno.getTipoCorte().getNombre()
            );

        } catch (Exception e) {
            log.error("[Telegram] Error moviendo turno: {}", e.getMessage(), e);
            state.reset();
            return "❌ Error guardando el turno. Intentá de nuevo.";
        }
    }

    /**
     * Formatea un YearMonth para mostrar (ejemplo: "Diciembre 2024").
     */
    private String formatYearMonth(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
    }
}
