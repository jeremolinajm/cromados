package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.BloqueoTurno;
import com.cromados.barberia.model.DiaExcepcionalBarbero;
import com.cromados.barberia.model.HorarioBarbero;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handler para el comando /descanso.
 *
 * Permite bloquear múltiples horarios para descansos (almuerzo, pausas, etc.)
 * creando registros en la tabla bloqueo_turno.
 *
 * Flujo:
 * 1. Seleccionar fecha
 * 2. Seleccionar hora de inicio (botones)
 * 3. Seleccionar hora de fin (botones)
 * 4. Confirmar bloqueo
 * 5. Crear BloqueoTurno para cada slot en el rango
 */
@Slf4j
@Component
public class DescansoCommandHandler extends BaseCommandHandler {

    private static final String STEP_MONTH_SELECTION = "WAITING_MONTH_SELECTION_REST";
    private static final String STEP_DAY_SELECTION = "WAITING_DAY_SELECTION_REST";
    private static final String STEP_CUSTOM_DATE = "WAITING_CUSTOM_DATE_REST";
    private static final String STEP_TIME_FROM = "WAITING_TIME_FROM_REST";
    private static final String STEP_TIME_TO = "WAITING_TIME_TO_REST";
    private static final String STEP_CONFIRM = "CONFIRM_REST";

    private final BloqueoTurnoRepository bloqueoRepo;
    private final DiaExcepcionalBarberoRepository diaExcepcionalRepo;

    public DescansoCommandHandler(
            TurnoRepository turnoRepo,
            BarberoRepository barberoRepo,
            TipoCorteRepository tipoCorteRepo,
            SucursalRepository sucursalRepo,
            HorarioBarberoRepository horarioRepo,
            TelegramMessageBuilder messageBuilder,
            TelegramLongPollingBot bot,
            HorarioService horarioService,
            BloqueoTurnoRepository bloqueoRepo,
            DiaExcepcionalBarberoRepository diaExcepcionalRepo
    ) {
        super(turnoRepo, barberoRepo, tipoCorteRepo, sucursalRepo, horarioRepo, messageBuilder, bot, horarioService);
        this.bloqueoRepo = bloqueoRepo;
        this.diaExcepcionalRepo = diaExcepcionalRepo;
    }

    @Override
    public String getCommandName() {
        return "descanso";
    }

    @Override
    public boolean canHandle(String step) {
        return STEP_MONTH_SELECTION.equals(step) ||
               STEP_DAY_SELECTION.equals(step) ||
               STEP_CUSTOM_DATE.equals(step) ||
               STEP_TIME_FROM.equals(step) ||
               STEP_TIME_TO.equals(step) ||
               STEP_CONFIRM.equals(step);
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        state.setStep(STEP_MONTH_SELECTION);
        return showMonthSelection(chatId);
    }

    @Override
    public String handleCallback(Long chatId, String callbackData, SessionState state) {
        if (callbackData.equals("CANCEL")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        String[] parts = callbackData.split("_", 2);
        String action = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        return switch (action) {
            case "MONTHR" -> handleMonthSelection(chatId, value, state);
            case "DAYR" -> handleDaySelection(chatId, value, state);
            case "RESTFROM" -> handleTimeFromCallback(chatId, value, state);
            case "RESTTO" -> handleTimeToCallback(chatId, value, state);
            case "CONFIRMREST" -> handleConfirmCallback(chatId, value, state);
            default -> "❌ Acción no reconocida";
        };
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        if (STEP_CUSTOM_DATE.equals(state.getStep())) {
            return handleCustomDateInput(chatId, text, state);
        }
        // Los otros steps usan botones
        return "⚠️ Por favor usa los botones para seleccionar.";
    }

    /**
     * Muestra botones para seleccionar mes.
     */
    private String showMonthSelection(Long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        YearMonth actual = YearMonth.now();

        // Mes actual
        rows.add(messageBuilder.createSingleButtonRow(
                "📅 " + formatYearMonth(actual) + " (Mes actual)",
                "MONTHR_ACTUAL"
        ));

        // Próximos 3 meses
        for (int i = 1; i <= 3; i++) {
            YearMonth mes = actual.plusMonths(i);
            rows.add(messageBuilder.createSingleButtonRow(
                    "📅 " + formatYearMonth(mes),
                    "MONTHR_" + mes.toString()
            ));
        }

        // Fecha específica
        rows.add(messageBuilder.createSingleButtonRow(
                "🗓️ Fecha específica",
                "MONTHR_CUSTOM"
        ));

        // Cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId, "😴 Seleccioná el mes para bloquear descanso:", keyboard);
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
     * Considera tanto horarios regulares como días excepcionales.
     */
    private String showDaySelection(Long chatId, SessionState state) {
        YearMonth yearMonth = state.getTempYearMonth();
        LocalDate inicio = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();
        LocalDate hoy = LocalDate.now();

        Barbero barbero = getBarbero(state);

        // Obtener días de la semana habilitados del barbero (horarios regulares)
        List<Integer> diasHabilitadosRegulares = horarioRepo.findByBarbero_Id(barbero.getId())
                .stream()
                .map(HorarioBarbero::getDiaSemana)
                .distinct()
                .toList();

        // Obtener fechas con días excepcionales en el rango del mes
        List<LocalDate> fechasExcepcionales = diaExcepcionalRepo
                .findByBarbero_IdAndFechaGreaterThanEqualOrderByFechaAsc(barbero.getId(), inicio)
                .stream()
                .map(DiaExcepcionalBarbero::getFecha)
                .filter(fecha -> !fecha.isAfter(fin))
                .distinct()
                .toList();

        if (diasHabilitadosRegulares.isEmpty() && fechasExcepcionales.isEmpty()) {
            state.reset();
            return "❌ No tenés horarios configurados. Configurá tus horarios en el panel web.";
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Mostrar solo días del mes que coincidan con días habilitados del barbero O días excepcionales
        for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
            if (fecha.isBefore(hoy)) {
                continue; // Saltar días pasados
            }

            // Verificar si el día de la semana está habilitado para el barbero O si es un día excepcional
            int diaSemana = fecha.getDayOfWeek().getValue();
            boolean esRegular = diasHabilitadosRegulares.contains(diaSemana);
            boolean esExcepcional = fechasExcepcionales.contains(fecha);

            if (!esRegular && !esExcepcional) {
                continue; // Saltar días no habilitados
            }

            String dayLabel = fecha.format(DATE_FMT) + " - " +
                fecha.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, new Locale("es", "AR"));
            rows.add(messageBuilder.createSingleButtonRow(dayLabel, "DAYR_" + fecha.toString()));
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
            return procesarFechaSeleccionada(chatId, fecha, state);
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

        try {
            LocalDate fecha;

            if (text.equalsIgnoreCase("hoy")) {
                fecha = LocalDate.now();
            } else {
                fecha = LocalDate.parse(text, DATE_FMT);

                if (fecha.isBefore(LocalDate.now())) {
                    return "❌ La fecha no puede ser pasada.\n\nIngresá otra fecha (DD/MM/YYYY) o escribí 'hoy':";
                }
            }

            return procesarFechaSeleccionada(chatId, fecha, state);

        } catch (DateTimeParseException e) {
            return "❌ Formato incorrecto.\n\nUsa: DD/MM/YYYY (ej: 25/01/2025)";
        }
    }

    /**
     * Procesa la fecha seleccionada y muestra horarios disponibles.
     * Considera tanto horarios regulares como días excepcionales.
     */
    private String procesarFechaSeleccionada(Long chatId, LocalDate fecha, SessionState state) {
        Barbero barbero = getBarbero(state);

        // Verificar si hay horarios regulares O días excepcionales para esta fecha
        int diaSemana = fecha.getDayOfWeek().getValue();
        List<HorarioBarbero> horariosRegulares = horarioRepo.findByBarbero_IdAndDiaSemana(barbero.getId(), diaSemana);
        List<DiaExcepcionalBarbero> horariosExcepcionales = diaExcepcionalRepo.findByBarbero_IdAndFecha(barbero.getId(), fecha);

        if (horariosRegulares.isEmpty() && horariosExcepcionales.isEmpty()) {
            String dia = fecha.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL,
                    new Locale("es", "AR")
            );
            return String.format("❌ No trabajás el %s.\n\nSeleccioná otra fecha.", fecha.format(DATE_FMT));
        }

        state.setTempFecha(fecha);
        state.setStep(STEP_TIME_FROM);

        // ✅ USAR HorarioService como única fuente de verdad
        // HorarioService ya maneja la prioridad entre horarios excepcionales y regulares
        List<LocalTime> horariosDisponibles = horarioService.horariosDisponibles(barbero.getId(), fecha);
        state.setHorariosDisponibles(horariosDisponibles);

        if (horariosDisponibles.isEmpty()) {
            return String.format("""
                ℹ️ No hay horarios disponibles en %s.
                Todos los horarios ya están bloqueados.

                Seleccioná otra fecha.
                """, fecha.format(DATE_FMT));
        }

        // Mostrar con botones
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Agregar horarios disponibles (2 botones por fila)
        for (int i = 0; i < horariosDisponibles.size(); i++) {
            if (i % 2 == 0) {
                rows.add(new ArrayList<>());
            }
            LocalTime hora = horariosDisponibles.get(i);
            rows.get(rows.size() - 1).add(messageBuilder.buildButton(
                hora.format(TIME_FMT),
                "RESTFROM_" + i
            ));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = String.format(
            "⏰ Hora de inicio del descanso (%s)\n\nSeleccioná desde qué hora querés bloquear:",
            fecha.format(DATE_FMT)
        );

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de hora de inicio mediante callback.
     */
    private String handleTimeFromCallback(Long chatId, String value, SessionState state) {
        if (!STEP_TIME_FROM.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            int index = Integer.parseInt(value);
            if (index < 0 || index >= state.getHorariosDisponibles().size()) {
                return "❌ Índice inválido.";
            }

            LocalTime horaDesde = state.getHorariosDisponibles().get(index);
            state.setTempHoraDesde(horaDesde);
            state.setStep(STEP_TIME_TO);

            // Filtrar horarios que sean posteriores a la hora seleccionada
            List<LocalTime> horariosPosteriores = state.getHorariosDisponibles().stream()
                    .filter(h -> h.isAfter(horaDesde))
                    .toList();

            if (horariosPosteriores.isEmpty()) {
                return "❌ No hay horarios posteriores disponibles. Empezá de nuevo con /descanso.";
            }

            // Mostrar con botones
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            // Agregar horarios posteriores (2 botones por fila)
            for (int i = 0; i < horariosPosteriores.size(); i++) {
                if (i % 2 == 0) {
                    rows.add(new ArrayList<>());
                }
                LocalTime hora = horariosPosteriores.get(i);
                // Guardar el índice original en horariosDisponibles
                int originalIndex = state.getHorariosDisponibles().indexOf(hora);
                rows.get(rows.size() - 1).add(messageBuilder.buildButton(
                    hora.format(TIME_FMT),
                    "RESTTO_" + originalIndex
                ));
            }

            // Botón cancelar
            rows.add(messageBuilder.createCancelButton());

            String mensaje = String.format(
                "⏰ Hora de fin del descanso\n\nDesde: %s\nHasta: ??\n\nSeleccioná hasta qué hora querés bloquear:",
                horaDesde.format(TIME_FMT)
            );

            InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
            return editMessageWithButtons(chatId, mensaje, keyboard, state);

        } catch (Exception e) {
            return "❌ Error procesando hora: " + e.getMessage();
        }
    }

    /**
     * Maneja la selección de hora de fin mediante callback.
     */
    private String handleTimeToCallback(Long chatId, String value, SessionState state) {
        if (!STEP_TIME_TO.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            int index = Integer.parseInt(value);
            if (index < 0 || index >= state.getHorariosDisponibles().size()) {
                return "❌ Índice inválido.";
            }

            LocalTime horaHasta = state.getHorariosDisponibles().get(index);
            state.setTempHoraHasta(horaHasta);
            state.setStep(STEP_CONFIRM);

            // Calcular cuántos slots se bloquearán
            long slotsABloquear = state.getHorariosDisponibles().stream()
                    .filter(h -> !h.isBefore(state.getTempHoraDesde()) && !h.isAfter(horaHasta))
                    .count();

            // Mostrar confirmación con botones
            List<List<InlineKeyboardButton>> rows = messageBuilder.createConfirmationButtons(
                    "CONFIRMREST_YES",
                    "CONFIRMREST_NO"
            );

            String mensaje = String.format("""
                    😴 Confirmar bloqueo de descanso

                    📅 Fecha: %s
                    ⏰ Desde: %s
                    ⏰ Hasta: %s
                    🔒 Slots a bloquear: %d

                    Estos horarios NO aparecerán disponibles en la web.
                    NO se crearán turnos en el panel admin.

                    ¿Confirmar?
                    """,
                    state.getTempFecha().format(DATE_FMT),
                    state.getTempHoraDesde().format(TIME_FMT),
                    horaHasta.format(TIME_FMT),
                    slotsABloquear
            );

            InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
            return editMessageWithButtons(chatId, mensaje, keyboard, state);

        } catch (Exception e) {
            return "❌ Error procesando hora: " + e.getMessage();
        }
    }

    /**
     * Maneja la confirmación final y crea los bloqueos.
     */
    private String handleConfirmCallback(Long chatId, String value, SessionState state) {
        if (!STEP_CONFIRM.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (!"YES".equals(value)) {
            state.reset();
            return "❌ Operación cancelada";
        }

        try {
            Barbero barbero = getBarbero(state);

            // Crear BloqueoTurno para cada slot en el rango
            List<BloqueoTurno> bloqueos = new ArrayList<>();
            for (LocalTime hora : state.getHorariosDisponibles()) {
                if (!hora.isBefore(state.getTempHoraDesde()) && !hora.isAfter(state.getTempHoraHasta())) {
                    BloqueoTurno bloqueo = new BloqueoTurno();
                    bloqueo.setBarbero(barbero);
                    bloqueo.setFecha(state.getTempFecha());
                    bloqueo.setHora(hora);
                    bloqueos.add(bloqueo);
                }
            }

            bloqueoRepo.saveAll(bloqueos);

            // Notificar al admin
            notificarAdmin(String.format("""
                    😴 Descanso bloqueado por %s

                    📅 Fecha: %s
                    ⏰ Desde: %s
                    ⏰ Hasta: %s
                    🔒 Slots bloqueados: %d

                    (No aparecerán en el panel admin)
                    """,
                    barbero.getNombre(),
                    state.getTempFecha().format(DATE_FMT),
                    state.getTempHoraDesde().format(TIME_FMT),
                    state.getTempHoraHasta().format(TIME_FMT),
                    bloqueos.size()
            ));

            String resultado = String.format("""
                    ✅ Descanso bloqueado exitosamente

                    📅 %s
                    ⏰ %s - %s
                    🔒 %d horarios bloqueados

                    Estos horarios ya no aparecerán disponibles en la web.

                    Escribe /menu para ver el menú principal.
                    """,
                    state.getTempFecha().format(DATE_FMT),
                    state.getTempHoraDesde().format(TIME_FMT),
                    state.getTempHoraHasta().format(TIME_FMT),
                    bloqueos.size()
            );

            state.reset();
            return resultado;

        } catch (Exception e) {
            log.error("[Telegram] Error creando bloqueos de descanso: {}", e.getMessage(), e);
            return "❌ Error guardando bloqueos: " + e.getMessage();
        }
    }

    /**
     * Formatea un YearMonth para mostrar (ejemplo: "Diciembre 2024").
     */
    private String formatYearMonth(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
    }
}
