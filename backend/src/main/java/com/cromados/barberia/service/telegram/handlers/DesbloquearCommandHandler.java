package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.BloqueoTurno;
import com.cromados.barberia.model.Turno;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler para el comando /desbloquear.
 *
 * Permite desbloquear:
 * - Turnos presenciales bloqueados por Telegram (tabla turno)
 * - Bloqueos de descanso (tabla bloqueo_turno)
 *
 * Flujo:
 * 1. Seleccionar mes
 * 2. Seleccionar día con bloqueos
 * 3. Seleccionar bloqueo específico a eliminar
 * 4. Eliminar y liberar el horario
 */
@Slf4j
@Component
public class DesbloquearCommandHandler extends BaseCommandHandler {

    private static final String STEP_MONTH_SELECTION = "WAITING_MONTH_SELECTION_DESBLOQUEAR";
    private static final String STEP_DAY_SELECTION = "WAITING_DAY_SELECTION_DESBLOQUEAR";
    private static final String STEP_SELECT_UNBLOCK = "WAITING_SELECT_UNBLOCK";
    private static final String STEP_CUSTOM_DATE = "WAITING_CUSTOM_DATE_DESBLOQUEAR";

    private final BloqueoTurnoRepository bloqueoRepo;

    public DesbloquearCommandHandler(
            TurnoRepository turnoRepo,
            BarberoRepository barberoRepo,
            TipoCorteRepository tipoCorteRepo,
            SucursalRepository sucursalRepo,
            HorarioBarberoRepository horarioRepo,
            TelegramMessageBuilder messageBuilder,
            TelegramLongPollingBot bot,
            HorarioService horarioService,
            BloqueoTurnoRepository bloqueoRepo
    ) {
        super(turnoRepo, barberoRepo, tipoCorteRepo, sucursalRepo, horarioRepo, messageBuilder, bot, horarioService);
        this.bloqueoRepo = bloqueoRepo;
    }

    @Override
    public String getCommandName() {
        return "desbloquear";
    }

    @Override
    public boolean canHandle(String step) {
        return step.startsWith("WAITING_") && step.endsWith("_DESBLOQUEAR") || STEP_SELECT_UNBLOCK.equals(step);
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        state.setStep(STEP_MONTH_SELECTION);
        return showMonthSelection(chatId);
    }

    @Override
    public String handleCallback(Long chatId, String callbackData, SessionState state) {
        log.info("[Telegram] DesbloquearCommandHandler callback: {}", callbackData);

        if (callbackData.equals("CANCEL")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        String[] parts = callbackData.split("_", 2);
        String action = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        return switch (action) {
            case "MONTH" -> handleMonthSelection(chatId, value, state);
            case "DAY" -> handleDaySelection(chatId, value, state);
            case "UNBLOCK" -> handleUnblockSelection(chatId, callbackData, state);
            case "BACK" -> handleBackNavigation(value, chatId, state);
            default -> "❌ Acción no reconocida: " + action;
        };
    }

    private String handleUnblockSelection(Long chatId, String callbackData, SessionState state) {
        String[] parts = callbackData.split("_");
        if (parts.length < 3) {
            log.warn("[Telegram] Callback inválido, parts.length={}", parts.length);
            return "❌ Callback inválido";
        }

        String tipo = parts[1];   // T (turno) o D (descanso)
        String id = parts[2];     // ID del registro
        Barbero barbero = getBarbero(state);
        log.info("[Telegram] Desbloqueando tipo={}, id={}", tipo, id);

        if ("T".equals(tipo)) {
            return handleUnblockTurno(barbero, id, state);
        } else if ("D".equals(tipo)) {
            return handleUnblockDescanso(barbero, id, state);
        } else {
            log.warn("[Telegram] Tipo de bloqueo desconocido: {}", tipo);
            return "❌ Tipo de bloqueo no reconocido: " + tipo;
        }
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

        // Mes actual
        YearMonth actual = YearMonth.now();
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
        return sendMessageWithButtons(chatId, "📅 Seleccioná el mes para ver bloqueos:", keyboard);
    }

    /**
     * Maneja la selección de mes.
     */
    private String handleMonthSelection(Long chatId, String value, SessionState state) {
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
        return showDaySelection(chatId, state);
    }

    /**
     * Maneja input de fecha personalizada.
     */
    private String handleCustomDateInput(Long chatId, String text, SessionState state) {
        try {
            LocalDate fecha = LocalDate.parse(text, DATE_FMT);
            state.setTempFecha(fecha);
            return mostrarBloqueosDelDia(chatId, state, fecha);
        } catch (DateTimeParseException e) {
            return "❌ Formato inválido. Usá DD/MM/YYYY (ejemplo: 25/12/2024)";
        }
    }

    /**
     * Muestra botones para seleccionar día del mes.
     */
    private String showDaySelection(Long chatId, SessionState state) {
        Barbero barbero = getBarbero(state);
        YearMonth yearMonth = state.getTempYearMonth();

        LocalDate inicio = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();

        // 1. Buscar turnos bloqueados por Telegram (sin pagoConfirmado)
        List<Turno> turnosBloqueados = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(
                        barbero.getId(), inicio, fin
                ).stream()
                .filter(t -> "BLOQUEADO".equals(t.getEstado()) && !Boolean.TRUE.equals(t.getPagoConfirmado()))
                .toList();

        // 2. Buscar bloqueos de descanso
        List<BloqueoTurno> bloqueosDescanso = bloqueoRepo.findByBarbero_IdAndFechaBetween(
                barbero.getId(), inicio, fin
        );

        // Agrupar por día
        Map<LocalDate, Integer> bloqueosPorDia = new HashMap<>();

        for (Turno t : turnosBloqueados) {
            bloqueosPorDia.merge(t.getFecha(), 1, Integer::sum);
        }

        for (BloqueoTurno b : bloqueosDescanso) {
            bloqueosPorDia.merge(b.getFecha(), 1, Integer::sum);
        }

        // Si no hay ningún bloqueo
        if (bloqueosPorDia.isEmpty()) {
            state.reset();
            return "📭 No tenés bloqueos en " + formatYearMonth(yearMonth);
        }

        // Crear botones por día
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<LocalDate> diasOrdenados = new ArrayList<>(bloqueosPorDia.keySet());
        diasOrdenados.sort(LocalDate::compareTo);

        for (LocalDate dia : diasOrdenados) {
            int cantidad = bloqueosPorDia.get(dia);
            String label = dia.format(DATE_FMT) + " (" + cantidad + " bloqueo" + (cantidad > 1 ? "s" : "") + ")";
            rows.add(messageBuilder.createSingleButtonRow(label, "DAY_" + dia.toString()));
        }

        // Botón volver
        rows.add(messageBuilder.createSingleButtonRow("⬅️ Volver a meses", "BACK_MONTH"));

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(
                chatId,
                "🔓 Bloqueos de " + formatYearMonth(yearMonth) + "\n\nSeleccioná un día:",
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
            return mostrarBloqueosDelDia(chatId, state, fecha);
        } catch (DateTimeParseException e) {
            return "❌ Fecha inválida";
        }
    }

    /**
     * Muestra los bloqueos de un día específico.
     */
    private String mostrarBloqueosDelDia(Long chatId, SessionState state, LocalDate fecha) {
        Barbero barbero = getBarbero(state);

        // 1. Buscar turnos bloqueados en esa fecha
        List<Turno> turnosBloqueados = turnoRepo.findByBarbero_IdAndFecha(barbero.getId(), fecha)
                .stream()
                .filter(t -> "BLOQUEADO".equals(t.getEstado()) && !Boolean.TRUE.equals(t.getPagoConfirmado()))
                .toList();

        // 2. Buscar bloqueos de descanso
        List<BloqueoTurno> bloqueosDescanso = bloqueoRepo.findByBarbero_IdAndFechaBetween(
                barbero.getId(), fecha, fecha
        );

        if (turnosBloqueados.isEmpty() && bloqueosDescanso.isEmpty()) {
            state.reset();
            return "📭 No hay bloqueos para el " + fecha.format(DATE_FMT);
        }

        // Crear lista combinada de items para ordenar
        record BloqueoItem(LocalDate fecha, LocalTime hora, String texto, String callback) {}

        List<BloqueoItem> items = new ArrayList<>();

        // Agregar turnos bloqueados
        for (Turno t : turnosBloqueados) {
            String texto = String.format("🔒 %s - %s",
                    t.getHora().format(TIME_FMT),
                    t.getClienteNombre()
            );
            items.add(new BloqueoItem(t.getFecha(), t.getHora(), texto, "UNBLOCK_T_" + t.getId()));
        }

        // Agregar descansos
        for (BloqueoTurno b : bloqueosDescanso) {
            String texto = String.format("😴 %s - Descanso",
                    b.getHora().format(TIME_FMT)
            );
            items.add(new BloqueoItem(b.getFecha(), b.getHora(), texto, "UNBLOCK_D_" + b.getId()));
        }

        // Ordenar por hora
        items.sort(Comparator.comparing(BloqueoItem::hora));

        // Crear botones
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (BloqueoItem item : items) {
            rows.add(messageBuilder.createSingleButtonRow(item.texto(), item.callback()));
        }

        // Botones de navegación
        rows.add(messageBuilder.createSingleButtonRow("⬅️ Volver a días", "BACK_DAY"));
        rows.add(messageBuilder.createCancelButton());

        String mensaje = String.format("""
                🔓 Bloqueos del %s

                🔒 = Turno presencial
                😴 = Descanso

                Seleccioná qué querés desbloquear:
                """, fecha.format(DATE_FMT));

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        state.setStep(STEP_SELECT_UNBLOCK);

        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la navegación hacia atrás.
     */
    private String handleBackNavigation(String value, Long chatId, SessionState state) {
        return switch (value) {
            case "MONTH" -> {
                state.setStep(STEP_MONTH_SELECTION);
                yield showMonthSelection(chatId);
            }
            case "DAY" -> {
                state.setStep(STEP_DAY_SELECTION);
                yield showDaySelection(chatId, state);
            }
            default -> "❌ Navegación no reconocida";
        };
    }

    /**
     * Formatea YearMonth a texto en español.
     */
    private String formatYearMonth(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
    }

    /**
     * Maneja el desbloqueo de un turno.
     */
    private String handleUnblockTurno(Barbero barbero, String turnoIdStr, SessionState state) {
        try {
            Long turnoId = Long.valueOf(turnoIdStr);
            Turno turno = turnoRepo.findById(turnoId)
                    .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));

            // Verificar que sea del barbero y esté bloqueado
            if (!turno.getBarbero().getId().equals(barbero.getId())) {
                return "❌ Este turno no es tuyo.";
            }

            if (!"BLOQUEADO".equals(turno.getEstado())) {
                return "❌ Este turno no está bloqueado.";
            }

            // Verificar que NO sea una compra web
            if (Boolean.TRUE.equals(turno.getPagoConfirmado())) {
                return "❌ No podés desbloquear turnos de la web.";
            }

            // Eliminar el turno
            turnoRepo.delete(turno);

            // Notificar al admin
            boolean esFH = turno.getHora().equals(LocalTime.of(0, 0));
            String horaNotif = esFH ? "FH" : turno.getHora().format(TIME_FMT);
            notificarAdmin(String.format("""
                    🔓 Turno desbloqueado por %s

                    📅 Fecha: %s
                    ⏰ Hora: %s
                    👤 Cliente: %s
                    💇 Servicio: %s

                    El horario vuelve a estar disponible en la web.
                    """,
                    barbero.getNombre(),
                    turno.getFecha().format(DATE_FMT),
                    horaNotif,
                    turno.getClienteNombre(),
                    turno.getTipoCorte() != null ? turno.getTipoCorte().getNombre() : "-"
            ));

            // IMPORTANTE: Resetear estado para permitir usar otros comandos
            state.reset();

            return String.format("""
                ✅ Turno desbloqueado exitosamente

                📅 %s a las %s
                👤 %s
                💇 %s

                Este horario vuelve a estar disponible en la web.
                """,
                    turno.getFecha().format(DATE_FMT),
                    horaNotif,
                    turno.getClienteNombre(),
                    turno.getTipoCorte() != null ? turno.getTipoCorte().getNombre() : "-"
            );

        } catch (NumberFormatException e) {
            return "❌ ID de turno inválido";
        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            log.error("[Telegram] Error desbloqueando turno: {}", e.getMessage(), e);
            return "❌ Error desbloqueando turno. Intentá de nuevo.";
        }
    }

    /**
     * Maneja el desbloqueo de un descanso.
     */
    private String handleUnblockDescanso(Barbero barbero, String bloqueoIdStr, SessionState state) {
        try {
            Long bloqueoId = Long.valueOf(bloqueoIdStr);
            BloqueoTurno bloqueo = bloqueoRepo.findById(bloqueoId)
                    .orElseThrow(() -> new IllegalArgumentException("Bloqueo no encontrado"));

            // Verificar que sea del barbero
            if (!bloqueo.getBarbero().getId().equals(barbero.getId())) {
                return "❌ Este bloqueo no es tuyo.";
            }

            // Eliminar el bloqueo
            bloqueoRepo.delete(bloqueo);

            // Notificar al admin
            notificarAdmin(String.format("""
                    😴 Descanso desbloqueado por %s

                    📅 Fecha: %s
                    ⏰ Hora: %s

                    El horario vuelve a estar disponible en la web.
                    """,
                    barbero.getNombre(),
                    bloqueo.getFecha().format(DATE_FMT),
                    bloqueo.getHora().format(TIME_FMT)
            ));

            // IMPORTANTE: Resetear estado para permitir usar otros comandos
            state.reset();

            return String.format("""
                ✅ Descanso desbloqueado exitosamente

                📅 %s a las %s

                Este horario vuelve a estar disponible en la web.
                """,
                    bloqueo.getFecha().format(DATE_FMT),
                    bloqueo.getHora().format(TIME_FMT)
            );

        } catch (NumberFormatException e) {
            return "❌ ID de bloqueo inválido";
        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            log.error("[Telegram] Error desbloqueando descanso: {}", e.getMessage(), e);
            return "❌ Error desbloqueando descanso. Intentá de nuevo.";
        }
    }
}
