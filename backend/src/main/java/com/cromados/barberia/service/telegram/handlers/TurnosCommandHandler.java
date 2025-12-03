package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler mejorado para el comando /turnos.
 *
 * Flujo:
 * 1. Seleccionar mes mediante botones
 * 2. Seleccionar día mediante botones
 * 3. Mostrar turnos del día seleccionado con detalles
 */
@Slf4j
@Component
public class TurnosCommandHandler extends BaseCommandHandler {

    private static final String STEP_MONTH_SELECTION = "WAITING_MONTH_SELECTION_TURNOS";
    private static final String STEP_DAY_SELECTION = "WAITING_DAY_SELECTION_TURNOS";
    private static final String STEP_CUSTOM_DATE = "WAITING_CUSTOM_DATE_TURNOS";

    public TurnosCommandHandler(
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
        return "turnos";
    }

    @Override
    public boolean canHandle(String step) {
        return step.startsWith("WAITING_") && step.endsWith("_TURNOS");
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
            return "❌ Consulta de turnos cancelada";
        }

        String[] parts = callbackData.split("_", 2);
        String action = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        return switch (action) {
            case "MONTH" -> handleMonthSelection(chatId, value, state);
            case "DAY" -> handleDaySelection(chatId, value, state);
            case "BACK" -> handleBackNavigation(value, chatId, state);
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
     * Muestra botones para seleccionar mes.
     */
    private String showMonthSelection(Long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        YearMonth actual = YearMonth.now();

        // Mes actual
        rows.add(messageBuilder.createSingleButtonRow(
                "📅 " + formatYearMonth(actual) + " (Mes actual)",
                "MONTH_ACTUAL"
        ));

        // Próximos 3 meses
        for (int i = 1; i <= 3; i++) {
            YearMonth mes = actual.plusMonths(i);
            rows.add(messageBuilder.createSingleButtonRow(
                    "📅 " + formatYearMonth(mes),
                    "MONTH_" + mes.toString()
            ));
        }

        // Fecha específica
        rows.add(messageBuilder.createSingleButtonRow(
                "🗓️ Fecha específica",
                "MONTH_CUSTOM"
        ));

        // Cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId, "📅 Seleccioná el mes para ver tus turnos:", keyboard);
    }

    /**
     * Maneja la selección de mes.
     */
    private String handleMonthSelection(Long chatId, String value, SessionState state) {
        log.info("[TurnosCommand] handleMonthSelection: chatId={}, value={}", chatId, value);

        if ("ACTUAL".equals(value)) {
            state.setTempYearMonth(YearMonth.now());
        } else if ("CUSTOM".equals(value)) {
            state.setStep(STEP_CUSTOM_DATE);
            return "📅 Ingresá la fecha específica en formato DD/MM/YYYY:";
        } else {
            try {
                state.setTempYearMonth(YearMonth.parse(value));
            } catch (DateTimeParseException e) {
                return "❌ Formato de mes inválido. Usá los botones.";
            }
        }

        state.setStep(STEP_DAY_SELECTION);
        log.info("[TurnosCommand] Calling showDaySelection for yearMonth={}", state.getTempYearMonth());
        return showDaySelection(chatId, state);
    }

    /**
     * Maneja input de fecha personalizada.
     */
    private String handleCustomDateInput(Long chatId, String text, SessionState state) {
        try {
            LocalDate fecha = LocalDate.parse(text, DATE_FMT);
            state.setTempFecha(fecha);
            return showTurnosForDay(chatId, state, fecha);
        } catch (DateTimeParseException e) {
            return "❌ Formato inválido. Usá DD/MM/YYYY (ejemplo: 25/12/2024)";
        }
    }

    /**
     * Muestra botones para seleccionar día del mes.
     */
    private String showDaySelection(Long chatId, SessionState state) {
        log.info("[TurnosCommand] showDaySelection: chatId={}", chatId);

        Barbero barbero = getBarbero(state);
        YearMonth yearMonth = state.getTempYearMonth();

        log.info("[TurnosCommand] barberoId={}, yearMonth={}", barbero != null ? barbero.getId() : null, yearMonth);

        LocalDate inicio = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();

        List<Turno> turnos = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(
                barbero.getId(),
                inicio,
                fin
        );

        log.info("[TurnosCommand] Found {} turnos for period {} to {}", turnos.size(), inicio, fin);

        // Filtrar y agrupar por día
        Map<LocalDate, List<Turno>> turnosPorDia = turnos.stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado()) || "BLOQUEADO".equals(t.getEstado()))
                .collect(Collectors.groupingBy(Turno::getFecha));

        log.info("[TurnosCommand] turnosPorDia size: {}", turnosPorDia.size());

        if (turnosPorDia.isEmpty()) {
            log.info("[TurnosCommand] No turnos, returning message");
            state.reset();
            return "📭 No tenés turnos confirmados en " + formatYearMonth(yearMonth);
        }

        // Crear botones por día
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<LocalDate> diasOrdenados = new ArrayList<>(turnosPorDia.keySet());
        diasOrdenados.sort(LocalDate::compareTo);

        for (LocalDate dia : diasOrdenados) {
            int cantidad = turnosPorDia.get(dia).size();
            String label = dia.format(DATE_FMT) + " (" + cantidad + " turno" + (cantidad > 1 ? "s" : "") + ")";
            rows.add(messageBuilder.createSingleButtonRow(label, "DAY_" + dia.toString()));
        }

        // Botón volver
        rows.add(messageBuilder.createSingleButtonRow("⬅️ Volver a meses", "BACK_MONTH"));

        String mensaje = "📋 Turnos de " + formatYearMonth(yearMonth) + "\n\nSeleccioná un día:";
        log.info("[TurnosCommand] About to edit message with text: '{}', rows count: {}", mensaje, rows.size());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de día.
     */
    private String handleDaySelection(Long chatId, String value, SessionState state) {
        try {
            LocalDate fecha = LocalDate.parse(value);
            state.setTempFecha(fecha);
            return showTurnosForDay(chatId, state, fecha);
        } catch (DateTimeParseException e) {
            return "❌ Fecha inválida";
        }
    }

    /**
     * Muestra los turnos de un día específico.
     */
    private String showTurnosForDay(Long chatId, SessionState state, LocalDate fecha) {
        Barbero barbero = getBarbero(state);

        List<Turno> turnos = turnoRepo.findByBarbero_IdAndFecha(barbero.getId(), fecha);

        List<Turno> confirmados = turnos.stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado()) || "BLOQUEADO".equals(t.getEstado()))
                .sorted(Comparator.comparing(Turno::getHora))
                .toList();

        if (confirmados.isEmpty()) {
            state.reset();
            return "📭 No hay turnos confirmados para el " + fecha.format(DATE_FMT);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Turnos del ").append(fecha.format(DATE_FMT)).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        for (Turno t : confirmados) {
            String tipo = "BLOQUEADO".equals(t.getEstado()) ? "🔒 Presencial" : "✅ Web";
            String medioPago = getPaymentMethod(t);
            BigDecimal montoTotal = calculateTotalAmount(t);

            sb.append(String.format("⏰ %s - %s\n", t.getHora().format(TIME_FMT), tipo));
            sb.append(String.format("👤 %s\n", t.getClienteNombre()));
            sb.append(String.format("💇 %s\n", t.getTipoCorte().getNombre()));

            // Mostrar adicionales si existen
            if (t.getAdicionales() != null && !t.getAdicionales().isEmpty()) {
                sb.append(String.format("➕ %s\n", t.getAdicionales()));
            }

            sb.append(String.format("%s\n", medioPago));
            sb.append(String.format("💵 Total: $%s\n", montoTotal));

            // Detalles de pago para reservas web
            if (!"BLOQUEADO".equals(t.getEstado())) {
                if (Boolean.TRUE.equals(t.getSenia())) {
                    sb.append(String.format("   💰 Pagado: $%s | Falta: $%s (Seña 50%%)\n",
                            t.getMontoPagado(), t.getMontoEfectivo()));
                }
            }

            sb.append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("📊 Total: %d turno%s\n", confirmados.size(), confirmados.size() > 1 ? "s" : ""));

        // Notificar al admin sobre la consulta
        notificarConsulta("turnos", barbero,
                String.format("Consultó turnos del %s (%d turno%s confirmado%s)",
                        fecha.format(DATE_FMT),
                        confirmados.size(),
                        confirmados.size() > 1 ? "s" : "",
                        confirmados.size() > 1 ? "s" : ""));

        state.reset();
        return sb.toString();
    }

    /**
     * Maneja navegación hacia atrás.
     */
    private String handleBackNavigation(String target, Long chatId, SessionState state) {
        if ("MONTH".equals(target)) {
            state.setStep(STEP_MONTH_SELECTION);
            return showMonthSelectionEdit(chatId, state);
        }
        return "❌ Navegación inválida";
    }

    /**
     * Muestra botones para seleccionar mes (versión para editar mensaje).
     */
    private String showMonthSelectionEdit(Long chatId, SessionState state) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        YearMonth actual = YearMonth.now();

        // Mes actual
        rows.add(messageBuilder.createSingleButtonRow(
                "📅 " + formatYearMonth(actual) + " (Mes actual)",
                "MONTH_ACTUAL"
        ));

        // Próximos 3 meses
        for (int i = 1; i <= 3; i++) {
            YearMonth mes = actual.plusMonths(i);
            rows.add(messageBuilder.createSingleButtonRow(
                    "📅 " + formatYearMonth(mes),
                    "MONTH_" + mes.toString()
            ));
        }

        // Fecha específica
        rows.add(messageBuilder.createSingleButtonRow(
                "🗓️ Fecha específica",
                "MONTH_CUSTOM"
        ));

        // Cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, "📅 Seleccioná el mes para ver tus turnos:", keyboard, state);
    }

    /**
     * Obtiene el método de pago de un turno.
     */
    private String getPaymentMethod(Turno t) {
        if ("BLOQUEADO".equals(t.getEstado())) {
            // Turno presencial de Telegram
            if (t.getMontoEfectivo() != null && t.getMontoEfectivo().compareTo(BigDecimal.ZERO) > 0) {
                return "💵 Efectivo";
            } else if (t.getMontoPagado() != null && t.getMontoPagado().compareTo(BigDecimal.ZERO) > 0) {
                return "💳 Transferencia";
            }
            return "❓ No especificado";
        } else {
            // Reserva web
            return "💰 Mercado Pago";
        }
    }

    /**
     * Calcula el monto total de un turno (incluyendo adicionales).
     */
    private BigDecimal calculateTotalAmount(Turno t) {
        BigDecimal montoTotal = BigDecimal.ZERO;

        if (t.getMontoPagado() != null) {
            montoTotal = montoTotal.add(t.getMontoPagado());
        }
        if (t.getMontoEfectivo() != null) {
            montoTotal = montoTotal.add(t.getMontoEfectivo());
        }

        return montoTotal;
    }

    /**
     * Formatea un YearMonth para mostrar (ejemplo: "Diciembre 2024").
     */
    private String formatYearMonth(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
    }
}
