package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.*;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handler para el comando /agendar.
 *
 * Permite crear turnos presenciales con información completa:
 * - Fecha, hora, servicio, adicionales
 * - Medio de pago, datos del cliente
 *
 * Flujo complejo (10 pasos):
 * 1. Ingresar fecha
 * 2. Seleccionar hora (botones) - incluye opción "Fuera de Horario"
 * 3. Seleccionar servicio (botones)
 * 4. ¿Agregar adicionales? (Sí/No)
 * 5. Seleccionar adicionales uno por uno (si aplica)
 * 6. Seleccionar medio de pago (botones)
 * 7. Ingresar nombre del cliente
 * 8. Ingresar teléfono del cliente
 * 9. Ingresar edad del cliente
 * 10. Confirmar y crear turno
 */
@Slf4j
@Component
public class AgendarCommandHandler extends BaseCommandHandler {

    private static final String STEP_MONTH_SELECTION = "WAITING_MONTH_SELECTION_BLOCK";
    private static final String STEP_DAY_SELECTION = "WAITING_DAY_SELECTION_BLOCK";
    private static final String STEP_CUSTOM_DATE = "WAITING_CUSTOM_DATE_BLOCK";
    private static final String STEP_TIME = "WAITING_TIME_BLOCK";
    private static final String STEP_SERVICE = "WAITING_SERVICE_BLOCK";
    private static final String STEP_ADICIONALES_QUESTION = "WAITING_ADICIONALES_QUESTION";
    private static final String STEP_SELECTING_ADICIONALES = "SELECTING_ADICIONALES";
    private static final String STEP_MEDIO_PAGO = "WAITING_MEDIO_PAGO";
    private static final String STEP_CLIENT_NAME = "WAITING_CLIENT_NAME";
    private static final String STEP_CLIENT_PHONE_SELECTION = "WAITING_CLIENT_PHONE_SELECTION";
    private static final String STEP_CLIENT_PHONE = "WAITING_CLIENT_PHONE";
    private static final String STEP_CLIENT_AGE = "WAITING_CLIENT_AGE";
    private static final String STEP_CONFIRM = "CONFIRM_BLOCK";

    public AgendarCommandHandler(
            TurnoRepository turnoRepo,
            BarberoRepository barberoRepo,
            TipoCorteRepository tipoCorteRepo,
            SucursalRepository sucursalRepo,
            HorarioBarberoRepository horarioRepo,
            TelegramMessageBuilder messageBuilder,
            TelegramLongPollingBot bot,
            HorarioService horarioService
    ) {
        super(turnoRepo, barberoRepo, tipoCorteRepo, sucursalRepo, horarioRepo, messageBuilder, bot, horarioService);
    }

    @Override
    public String getCommandName() {
        return "agendar";
    }

    @Override
    public boolean canHandle(String step) {
        return STEP_MONTH_SELECTION.equals(step) ||
               STEP_DAY_SELECTION.equals(step) ||
               STEP_CUSTOM_DATE.equals(step) ||
               STEP_TIME.equals(step) ||
               STEP_SERVICE.equals(step) ||
               STEP_ADICIONALES_QUESTION.equals(step) ||
               STEP_SELECTING_ADICIONALES.equals(step) ||
               STEP_MEDIO_PAGO.equals(step) ||
               STEP_CLIENT_NAME.equals(step) ||
               STEP_CLIENT_PHONE_SELECTION.equals(step) ||
               STEP_CLIENT_PHONE.equals(step) ||
               STEP_CLIENT_AGE.equals(step) ||
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

        // Dividir sin límite para obtener todas las partes
        String[] parts = callbackData.split("_");
        String action = parts[0];

        return switch (action) {
            case "MONTHB" -> {
                String value = parts.length > 1 ? parts[1] : "";
                yield handleMonthSelection(chatId, value, state);
            }
            case "DAYB" -> {
                String value = parts.length > 1 ? parts[1] : "";
                yield handleDaySelection(chatId, value, state);
            }
            case "TIME" -> {
                String value = parts.length > 1 ? parts[1] : "";
                yield handleTimeCallback(chatId, value, state);
            }
            case "SERVICE" -> {
                String value = parts.length > 1 ? parts[1] : "";
                yield handleServiceCallback(chatId, value, state);
            }
            case "ADICIONALES" -> {
                String value = parts.length > 1 ? parts[1] : "";
                yield handleAdicionalesQuestionCallback(chatId, value, state);
            }
            case "ADICIONAL" -> {
                // Formato: ADICIONAL_TOGGLE_[id]
                if (parts.length >= 3 && "TOGGLE".equals(parts[1])) {
                    yield handleAdicionalToggle(chatId, parts[2], state);
                }
                yield "❌ Callback inválido";
            }
            case "PAYMENT" -> {
                String value = parts.length > 1 ? parts[1] : "";
                yield handleMedioPagoCallback(value, state);
            }
            case "PHONE" -> {
                // Formato: PHONE_ADD o PHONE_SELECT_[index]
                if (parts.length >= 2) {
                    if ("ADD".equals(parts[1])) {
                        yield handleAddNewPhone(state);
                    } else if ("SELECT".equals(parts[1]) && parts.length >= 3) {
                        yield handleSelectExistingPhone(chatId, parts[2], state);
                    }
                }
                yield "❌ Callback inválido";
            }
            case "CONFIRM" -> {
                // Formato: CONFIRM_BLOCK_[YES/NO]
                if (parts.length >= 3 && "BLOCK".equals(parts[1])) {
                    yield handleConfirmCallback("YES".equals(parts[2]), state);
                }
                yield "❌ Callback inválido";
            }
            default -> "❌ Acción no reconocida";
        };
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        return switch (state.getStep()) {
            case STEP_CUSTOM_DATE -> handleCustomDateInput(chatId, text, state);
            case STEP_CLIENT_NAME -> handleClientNameInput(text, state);
            case STEP_CLIENT_PHONE -> handleClientPhoneInput(text, state);
            case STEP_CLIENT_AGE -> handleClientAgeInput(chatId, text, state);
            default -> "❌ Por favor usa los botones para navegar";
        };
    }

    /**
     * Construye el teclado de botones para selección de mes.
     */
    private InlineKeyboardMarkup buildMonthSelectionKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        YearMonth actual = YearMonth.now();

        // Mes actual
        rows.add(messageBuilder.createSingleButtonRow(
                "📅 " + formatYearMonth(actual) + " (Mes actual)",
                "MONTHB_ACTUAL"
        ));

        // Próximos 3 meses
        for (int i = 1; i <= 3; i++) {
            YearMonth mes = actual.plusMonths(i);
            rows.add(messageBuilder.createSingleButtonRow(
                    "📅 " + formatYearMonth(mes),
                    "MONTHB_" + mes.toString()
            ));
        }

        // Fecha específica
        rows.add(messageBuilder.createSingleButtonRow(
                "🗓️ Fecha específica",
                "MONTHB_CUSTOM"
        ));

        // Cancelar
        rows.add(messageBuilder.createCancelButton());

        return messageBuilder.buildInlineKeyboard(rows);
    }

    /**
     * Muestra botones para seleccionar mes.
     */
    private String showMonthSelection(Long chatId) {
        InlineKeyboardMarkup keyboard = buildMonthSelectionKeyboard();
        return sendMessageWithButtons(chatId, "📅 Seleccioná el mes para agendar el turno:", keyboard);
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
                .map(HorarioBarbero::getDiaSemana)
                .distinct()
                .toList();

        if (diasHabilitados.isEmpty()) {
            state.reset();
            return "❌ No tenés horarios configurados. Configurá tus horarios en el panel web.";
        }

        // Primero recolectar todos los días válidos
        List<LocalDate> diasValidos = new ArrayList<>();
        for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
            if (fecha.isBefore(hoy)) {
                continue; // Saltar días pasados
            }

            // Verificar si el día de la semana está habilitado para el barbero
            int diaSemana = fecha.getDayOfWeek().getValue();
            if (!diasHabilitados.contains(diaSemana)) {
                continue; // Saltar días no habilitados
            }

            diasValidos.add(fecha);
        }

        if (diasValidos.isEmpty()) {
            state.setStep(STEP_MONTH_SELECTION);
            String errorMessage = "❌ No tenés días habilitados en " + formatYearMonth(yearMonth) + ".\n\n" +
                                 "📅 Seleccioná otro mes para agendar el turno:";
            InlineKeyboardMarkup keyboard = buildMonthSelectionKeyboard();
            return editMessageWithButtons(chatId, errorMessage, keyboard, state);
        }

        // Mostrar días en botones anchos (1 por fila)
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (LocalDate fecha : diasValidos) {
            String dayLabel = fecha.format(DATE_FMT) + " - " +
                fecha.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, new Locale("es", "AR"));
            rows.add(messageBuilder.createSingleButtonRow(dayLabel, "DAYB_" + fecha.toString()));
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
     */
    private String procesarFechaSeleccionada(Long chatId, LocalDate fecha, SessionState state) {
        Barbero barbero = getBarbero(state);

        int diaSemana = fecha.getDayOfWeek().getValue();
        List<HorarioBarbero> horarios = horarioRepo.findByBarbero_IdAndDiaSemana(barbero.getId(), diaSemana);

        if (horarios.isEmpty()) {
            String dia = fecha.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL,
                    new Locale("es", "AR")
            );
            return String.format("❌ No trabajás los %s.\n\nSeleccioná otra fecha.", dia);
        }

        state.setTempFecha(fecha);
        state.setStep(STEP_TIME);

        // ✅ USAR HorarioService como única fuente de verdad
        List<LocalTime> horariosDisponibles = horarioService.horariosDisponibles(barbero.getId(), fecha);
        state.setHorariosDisponibles(horariosDisponibles);

        return mostrarHorarios(chatId, state, fecha, horariosDisponibles);
    }

    /**
     * Muestra horarios disponibles con botones.
     */
    private String mostrarHorarios(Long chatId, SessionState state, LocalDate fecha, List<LocalTime> horariosDisponibles) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Opción "Fuera de horario" primero
        rows.add(messageBuilder.createSingleButtonRow("⏰ Fuera de horario (FH)", "TIME_FH"));

        // Agregar horarios disponibles (2 botones por fila)
        for (int i = 0; i < horariosDisponibles.size(); i++) {
            if (i % 2 == 0) {
                rows.add(new ArrayList<>());
            }
            LocalTime hora = horariosDisponibles.get(i);
            rows.get(rows.size() - 1).add(messageBuilder.buildButton(
                hora.format(TIME_FMT),
                "TIME_" + i
            ));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = String.format(
            "⏰ Horarios disponibles el %s:\n\nSeleccioná un horario o 'Fuera de horario' si fue atendido fuera del horario normal.",
            fecha.format(DATE_FMT)
        );

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de hora mediante callback.
     */
    private String handleTimeCallback(Long chatId, String value, SessionState state) {
        if (!STEP_TIME.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia. Usa /agendar para empezar.";
        }

        try {
            if ("FH".equals(value)) {
                // Fuera de horario
                state.setTempHora(LocalTime.of(0, 0));
            } else {
                // Horario de la lista
                int index = Integer.parseInt(value);
                if (index < 0 || index >= state.getHorariosDisponibles().size()) {
                    return "❌ Índice inválido.";
                }
                state.setTempHora(state.getHorariosDisponibles().get(index));
            }

            state.setStep(STEP_SERVICE);

            return mostrarServicios(chatId, state);

        } catch (Exception e) {
            return "❌ Error procesando horario: " + e.getMessage();
        }
    }

    /**
     * Muestra servicios disponibles.
     */
    private String mostrarServicios(Long chatId, SessionState state) {
        Barbero barbero = getBarbero(state);

        List<TipoCorte> servicios = tipoCorteRepo.findAllWithBarberos().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getAdicional()))
                .filter(s -> {
                    // Si el servicio no tiene barberos habilitados, todos pueden ofrecerlo
                    if (s.getBarberosHabilitados() == null || s.getBarberosHabilitados().isEmpty()) {
                        return true;
                    }
                    // Si tiene barberos habilitados, verificar que este barbero esté en la lista
                    return s.getBarberosHabilitados().stream()
                            .anyMatch(b -> b.getId().equals(barbero.getId()));
                })
                .toList();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Un botón por fila (vertical)
        for (TipoCorte s : servicios) {
            rows.add(messageBuilder.createSingleButtonRow(
                s.getNombre() + " - $" + s.getPrecio(),
                "SERVICE_" + s.getId()
            ));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = "💇 Elegí el servicio realizado:";
        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);

        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de servicio.
     */
    private String handleServiceCallback(Long chatId, String value, SessionState state) {
        if (!STEP_SERVICE.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            Long servicioId = Long.parseLong(value);
            TipoCorte servicio = tipoCorteRepo.findById(servicioId)
                    .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

            state.setTempServicioId(servicioId);
            state.setTempServicio(servicio);

            // Inicializar lista de adicionales
            state.setTempAdicionalesIds(new ArrayList<>());
            state.setStep(STEP_ADICIONALES_QUESTION);

            return preguntarAdicionales(chatId, state, servicio);

        } catch (Exception e) {
            return "❌ Error procesando servicio: " + e.getMessage();
        }
    }

    /**
     * Pregunta si desea agregar servicios adicionales.
     */
    private String preguntarAdicionales(Long chatId, SessionState state, TipoCorte servicio) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Botones sí/no
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(messageBuilder.buildButton("✅ Sí", "ADICIONALES_YES"));
        row1.add(messageBuilder.buildButton("❌ No", "ADICIONALES_NO"));
        rows.add(row1);

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = String.format("""
                ✂️ Servicio seleccionado: %s

                ➕ ¿Querés agregar servicios adicionales?
                (Lavado, Barba, etc.)
                """, servicio.getNombre());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la respuesta a la pregunta de adicionales.
     */
    private String handleAdicionalesQuestionCallback(Long chatId, String value, SessionState state) {
        if (!STEP_ADICIONALES_QUESTION.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if ("YES".equals(value)) {
            return mostrarAdicionalesDisponibles(chatId, state);
        } else if ("NO".equals(value)) {
            state.setStep(STEP_MEDIO_PAGO);
            return mostrarMediosPago(chatId, state);
        } else {
            return "❌ Opción inválida";
        }
    }

    /**
     * Muestra adicionales disponibles para seleccionar.
     */
    private String mostrarAdicionalesDisponibles(Long chatId, SessionState state) {
        // Obtener adicionales disponibles (filtrar los ya seleccionados)
        List<TipoCorte> adicionales = tipoCorteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getAdicional()))
                .filter(s -> !state.getTempAdicionalesIds().contains(s.getId()))
                .toList();

        if (adicionales.isEmpty()) {
            // No hay más adicionales disponibles
            sendText(chatId, "ℹ️ No hay más adicionales por agregar.");
            state.setStep(STEP_MEDIO_PAGO);
            return mostrarMediosPago(chatId, state);
        }

        state.setStep(STEP_SELECTING_ADICIONALES);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Botones para cada adicional disponible
        for (TipoCorte adicional : adicionales) {
            String buttonText = String.format("%s - $%d", adicional.getNombre(), adicional.getPrecio());
            rows.add(messageBuilder.createSingleButtonRow(buttonText, "ADICIONAL_TOGGLE_" + adicional.getId()));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = """
                ➕ Elegí un adicional:

                Seleccioná el servicio adicional que querés agregar:
                """;

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de un adicional.
     */
    private String handleAdicionalToggle(Long chatId, String adicionalIdStr, SessionState state) {
        if (!STEP_SELECTING_ADICIONALES.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            Long adicionalId = Long.parseLong(adicionalIdStr);

            // Agregar el adicional seleccionado
            state.getTempAdicionalesIds().add(adicionalId);

            // Obtener el nombre del adicional agregado
            TipoCorte adicionalAgregado = tipoCorteRepo.findById(adicionalId)
                    .orElseThrow(() -> new IllegalArgumentException("Adicional no encontrado"));

            // Preguntar si quiere agregar otro
            state.setStep(STEP_ADICIONALES_QUESTION);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            // Botones sí/no
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(messageBuilder.buildButton("✅ Sí", "ADICIONALES_YES"));
            row1.add(messageBuilder.buildButton("❌ No", "ADICIONALES_NO"));
            rows.add(row1);

            // Botón cancelar
            rows.add(messageBuilder.createCancelButton());

            String mensaje = String.format("""
                    ✅ Agregado: %s ($%d)

                    ➕ ¿Querés agregar otro adicional?
                    """, adicionalAgregado.getNombre(), adicionalAgregado.getPrecio());

            InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
            return editMessageWithButtons(chatId, mensaje, keyboard, state);

        } catch (NumberFormatException e) {
            return "❌ ID inválido.";
        }
    }

    /**
     * Muestra medios de pago.
     */
    private String mostrarMediosPago(Long chatId, SessionState state) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Botones de medio de pago
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(messageBuilder.buildButton("💵 Efectivo", "PAYMENT_EFECTIVO"));
        row1.add(messageBuilder.buildButton("🏦 Transferencia", "PAYMENT_TRANSFERENCIA"));
        rows.add(row1);

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        // Construir mensaje con resumen de selección
        StringBuilder mensaje = new StringBuilder("💳 Medio de pago\n\n");

        if (!state.getTempAdicionalesIds().isEmpty()) {
            mensaje.append("✅ Adicionales seleccionados:\n");
            for (Long id : state.getTempAdicionalesIds()) {
                tipoCorteRepo.findById(id).ifPresent(s ->
                        mensaje.append(String.format("  • %s ($%d)\n", s.getNombre(), s.getPrecio()))
                );
            }
            mensaje.append("\n");
        }

        mensaje.append("¿Cómo pagó el cliente?");

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje.toString(), keyboard, state);
    }

    /**
     * Maneja la selección de medio de pago.
     */
    private String handleMedioPagoCallback(String value, SessionState state) {
        if (!STEP_MEDIO_PAGO.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if ("EFECTIVO".equals(value)) {
            state.setTempMedioPago("EFECTIVO");
        } else if ("TRANSFERENCIA".equals(value)) {
            state.setTempMedioPago("TRANSFERENCIA");
        } else {
            return "❌ Medio de pago inválido.";
        }

        state.setStep(STEP_CLIENT_NAME);

        return """
                👤 Datos del cliente

                Ingresá el nombre del cliente:
                """;
    }

    /**
     * Maneja el input de nombre del cliente.
     * Busca números de teléfono asociados al nombre ingresado.
     */
    private String handleClientNameInput(String text, SessionState state) {
        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        if (text.trim().length() < 2) {
            return "❌ El nombre debe tener al menos 2 caracteres.\n\nIngresá el nombre:";
        }

        state.setTempClienteNombre(text.trim());

        // Buscar números de teléfono asociados a este nombre
        List<Object[]> clientesEncontrados = turnoRepo.findClientesByNombre(text.trim());

        if (clientesEncontrados.isEmpty()) {
            // No hay números asociados, solicitar teléfono normalmente
            state.setStep(STEP_CLIENT_PHONE);
            return """
                    📱 Ingresá el teléfono del cliente:

                    Ejemplo: +5491123456789 o 1123456789
                    """;
        } else {
            // Hay números asociados, mostrar opciones
            state.setStep(STEP_CLIENT_PHONE_SELECTION);
            // Guardar los clientes encontrados en la sesión para usarlos después
            state.setTempClientesEncontrados(clientesEncontrados);
            return mostrarOpcionesTelefono(state, clientesEncontrados);
        }
    }

    /**
     * Maneja el input de teléfono del cliente.
     */
    private String handleClientPhoneInput(String text, SessionState state) {
        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        String phone = text.trim().replaceAll("[^0-9+]", "");
        if (phone.length() < 8) {
            return "❌ Teléfono inválido.\n\nIngresá un teléfono válido:";
        }

        // Normalizar teléfono (+549 para Argentina)
        state.setTempClienteTelefono(normalizarTelefono(phone));
        state.setStep(STEP_CLIENT_AGE);

        return """
                🎂 Ingresá la edad del cliente:

                Ejemplo: 25
                """;
    }

    /**
     * Normaliza un teléfono agregando +549 si no tiene prefijo.
     */
    private String normalizarTelefono(String telefono) {
        String phone = telefono.trim().replaceAll("[^0-9+]", "");

        // Si ya tiene +, retornar tal cual
        if (phone.startsWith("+")) {
            return phone;
        }

        // Si no tiene +, agregar +549 para Argentina
        return "+549" + phone;
    }

    /**
     * Maneja el input de edad del cliente.
     */
    private String handleClientAgeInput(Long chatId, String text, SessionState state) {
        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        try {
            int edad = Integer.parseInt(text.trim());
            if (edad < 1 || edad > 120) {
                return "❌ Edad inválida.\n\nIngresá una edad entre 1 y 120:";
            }

            state.setTempClienteEdad(edad);
            state.setStep(STEP_CONFIRM);

            return mostrarConfirmacion(chatId, state);

        } catch (NumberFormatException e) {
            return "❌ Ingresá un número válido para la edad:";
        }
    }

    /**
     * Muestra confirmación del turno a crear.
     */
    private String mostrarConfirmacion(Long chatId, SessionState state) {
        boolean esFH = state.getTempHora().equals(LocalTime.of(0, 0));
        String horaDisplay = esFH ? "FH (Fuera de horario)" : state.getTempHora().format(TIME_FMT);
        String medioPagoDisplay = "EFECTIVO".equals(state.getTempMedioPago()) ? "Efectivo" : "Transferencia";

        // Calcular precio total (servicio + adicionales)
        BigDecimal precioTotal = BigDecimal.valueOf(state.getTempServicio().getPrecio());
        if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
            for (Long adicionalId : state.getTempAdicionalesIds()) {
                TipoCorte adicional = tipoCorteRepo.findById(adicionalId).orElse(null);
                if (adicional != null) {
                    precioTotal = precioTotal.add(BigDecimal.valueOf(adicional.getPrecio()));
                }
            }
        }

        // Construir lista de adicionales para mostrar
        StringBuilder adicionalesInfo = new StringBuilder();
        if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
            adicionalesInfo.append("\n➕ Adicionales:");
            for (Long adicionalId : state.getTempAdicionalesIds()) {
                tipoCorteRepo.findById(adicionalId).ifPresent(adicional ->
                        adicionalesInfo.append(String.format("\n   • %s ($%d)", adicional.getNombre(), adicional.getPrecio()))
                );
            }
        }

        String mensaje = String.format("""
                ✅ Confirmar bloqueo de turno

                📅 Fecha: %s
                ⏰ Hora: %s
                💇 Servicio: %s ($%d)%s
                💰 Total: $%s
                💳 Medio de pago: %s
                👤 Cliente: %s
                📱 Teléfono: %s
                🎂 Edad: %d años

                Este turno se registrará como turno presencial y aparecerá en el panel administrativo.
                """,
                state.getTempFecha().format(DATE_FMT),
                horaDisplay,
                state.getTempServicio().getNombre(),
                state.getTempServicio().getPrecio(),
                adicionalesInfo.toString(),
                precioTotal,
                medioPagoDisplay,
                state.getTempClienteNombre(),
                state.getTempClienteTelefono(),
                state.getTempClienteEdad()
        );

        // Enviar mensaje con botones
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Fila con botones de confirmar y cancelar
        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        confirmRow.add(messageBuilder.buildButton("✅ Sí, confirmar", "CONFIRM_BLOCK_YES"));
        confirmRow.add(messageBuilder.buildButton("❌ Cancelar", "CONFIRM_BLOCK_NO"));
        rows.add(confirmRow);

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId, mensaje, keyboard);
    }

    /**
     * Maneja la confirmación final.
     */
    private String handleConfirmCallback(boolean confirmar, SessionState state) {
        if (!confirmar) {
            state.reset();
            return "❌ Bloqueo cancelado";
        }
        return ejecutarBloqueoTurno(state);
    }

    /**
     * Ejecuta la creación del turno bloqueado.
     */
    private String ejecutarBloqueoTurno(SessionState state) {
        try {
            Barbero barbero = getBarbero(state);

            // Verificar si hay turno CONFIRMADO/PAGADO/BLOQUEADO
            boolean ocupado = turnoRepo.findByBarbero_IdAndFecha(barbero.getId(), state.getTempFecha())
                    .stream()
                    .filter(t -> t.getHora().equals(state.getTempHora()))
                    .anyMatch(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                            || "CONFIRMADO".equals(t.getEstado())
                            || "BLOQUEADO".equals(t.getEstado()));

            if (ocupado) {
                state.reset();
                return String.format("""
                    ❌ No se puede agendar

                    Ya existe un turno en:
                    📅 %s
                    ⏰ %s

                    El horario está ocupado.
                    """,
                        state.getTempFecha().format(DATE_FMT),
                        state.getTempHora().format(TIME_FMT));
            }

            // Recuperar TODAS las entidades frescas desde la BD
            Barbero barberoFresco = barberoRepo.findById(barbero.getId())
                    .orElseThrow(() -> new IllegalStateException("Barbero no encontrado"));

            TipoCorte servicio = tipoCorteRepo.findById(state.getTempServicioId())
                    .orElseThrow(() -> new IllegalStateException("Servicio no encontrado"));

            Sucursal sucursal = barberoFresco.getSucursal();
            if (sucursal == null) {
                throw new IllegalStateException("El barbero no tiene sucursal asignada");
            }

            // Crear turno BLOQUEADO
            Turno turno = new Turno();
            turno.setBarbero(barberoFresco);
            turno.setSucursal(sucursal);
            turno.setTipoCorte(servicio);
            turno.setFecha(state.getTempFecha());
            turno.setHora(state.getTempHora()); // Puede ser 00:00 para FH
            turno.setEstado("BLOQUEADO");
            turno.setPagoConfirmado(false);
            turno.setClienteNombre(state.getTempClienteNombre());
            turno.setClienteTelefono(state.getTempClienteTelefono());
            turno.setClienteEdad(state.getTempClienteEdad());
            turno.setSenia(false);

            // Agregar adicionales si fueron seleccionados
            String adicionalesStr = null;
            if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
                List<String> adicionalesNombres = new ArrayList<>();
                for (Long adicionalId : state.getTempAdicionalesIds()) {
                    tipoCorteRepo.findById(adicionalId).ifPresent(a -> adicionalesNombres.add(a.getNombre()));
                }
                if (!adicionalesNombres.isEmpty()) {
                    adicionalesStr = String.join(", ", adicionalesNombres);
                    turno.setAdicionales(adicionalesStr);
                }
            }

            // Calcular precio total (servicio + adicionales)
            BigDecimal precioTotal = BigDecimal.valueOf(servicio.getPrecio());

            // Sumar precio de adicionales
            if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
                for (Long adicionalId : state.getTempAdicionalesIds()) {
                    TipoCorte adicional = tipoCorteRepo.findById(adicionalId).orElse(null);
                    if (adicional != null) {
                        BigDecimal precioAdicional = BigDecimal.valueOf(adicional.getPrecio());
                        precioTotal = precioTotal.add(precioAdicional);
                    }
                }
            }

            // Guardar según medio de pago
            if ("EFECTIVO".equals(state.getTempMedioPago())) {
                turno.setMontoEfectivo(precioTotal);
                turno.setMontoPagado(BigDecimal.ZERO);
            } else { // TRANSFERENCIA
                turno.setMontoPagado(precioTotal);
                turno.setMontoEfectivo(BigDecimal.ZERO);
            }

            Turno saved = turnoRepo.save(turno);

            log.info("[Telegram] Turno bloqueado creado: id={} barbero={} fecha={} hora={}",
                    saved.getId(), barberoFresco.getNombre(), saved.getFecha(), saved.getHora());

            // Notificar al admin
            boolean esFH = saved.getHora().equals(LocalTime.of(0, 0));
            String horaNotif = esFH ? "FH" : saved.getHora().format(TIME_FMT);
            String medioPagoNotif = "EFECTIVO".equals(state.getTempMedioPago()) ? "💵 Efectivo" : "💳 Transferencia";

            String adicionalesNotif = "";
            if (adicionalesStr != null && !adicionalesStr.isEmpty()) {
                adicionalesNotif = "\n➕ Adicionales: " + adicionalesStr;
            }

            notificarAdmin(String.format("""
                    🔒 Turno bloqueado por %s

                    📅 Fecha: %s
                    ⏰ Hora: %s
                    👤 Cliente: %s
                    💇 Servicio: %s ($%d)%s
                    💰 Total: $%s - %s
                    """,
                    barberoFresco.getNombre(),
                    saved.getFecha().format(DATE_FMT),
                    horaNotif,
                    saved.getClienteNombre(),
                    servicio.getNombre(),
                    servicio.getPrecio(),
                    adicionalesNotif,
                    precioTotal,
                    medioPagoNotif
            ));

            state.reset();

            String horaDisplay = esFH ? "FH (Fuera de horario)" : saved.getHora().format(TIME_FMT);
            String medioPagoMsg = "EFECTIVO".equals(state.getTempMedioPago())
                ? String.format("💵 Cobrado en efectivo: $%s", precioTotal)
                : String.format("💳 Transferencia recibida: $%s", precioTotal);

            String adicionalesMsg = "";
            if (adicionalesStr != null && !adicionalesStr.isEmpty()) {
                adicionalesMsg = "\n➕ Adicionales: " + adicionalesStr;
            }

            return String.format("""
            ✅ Turno bloqueado exitosamente

            📅 %s a las %s
            👤 %s
            💇 %s%s
            💰 Total: $%s
            %s

            El turno ha sido registrado y aparece en el panel administrativo.

            Escribe /menu para ver el menú principal.
            """,
                    saved.getFecha().format(DATE_FMT),
                    horaDisplay,
                    saved.getClienteNombre(),
                    servicio.getNombre(),
                    adicionalesMsg,
                    precioTotal,
                    medioPagoMsg
            );

        } catch (Exception e) {
            log.error("[Telegram] Error creando turno bloqueado: {}", e.getMessage(), e);
            state.reset();
            return "❌ Error guardando el turno: " + e.getMessage();
        }
    }

    /**
     * Muestra opciones de teléfono: números existentes + opción de agregar nuevo.
     */
    private String mostrarOpcionesTelefono(SessionState state, List<Object[]> clientesEncontrados) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String mensaje;

        if (clientesEncontrados.size() == 1) {
            // Un solo número encontrado
            mensaje = String.format("""
                    📱 Encontré este número asociado a %s:

                    Podés seleccionarlo o agregar uno nuevo:
                    """, state.getTempClienteNombre());
        } else {
            // Múltiples números encontrados
            mensaje = String.format("""
                    📱 Encontré %d números asociados a %s:

                    Seleccioná uno o agregá uno nuevo:
                    """, clientesEncontrados.size(), state.getTempClienteNombre());
        }

        // Botones para cada número encontrado
        for (int i = 0; i < clientesEncontrados.size(); i++) {
            Object[] cliente = clientesEncontrados.get(i);
            String telefono = (String) cliente[0];
            Integer edad = (Integer) cliente[1];

            // Mostrar solo últimos 4 dígitos del teléfono para privacidad
            String telefonoDisplay = telefono.length() > 4
                ? "..." + telefono.substring(telefono.length() - 4)
                : telefono;

            rows.add(messageBuilder.createSingleButtonRow(
                String.format("📞 %s (%d años)", telefonoDisplay, edad),
                "PHONE_SELECT_" + i
            ));
        }

        // Botón para agregar nuevo número
        rows.add(messageBuilder.createSingleButtonRow(
            "➕ Agregar otro número",
            "PHONE_ADD"
        ));

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(state.getBarbero().getTelegramChatId(), mensaje, keyboard);
    }

    /**
     * Maneja la selección de un teléfono existente.
     */
    private String handleSelectExistingPhone(Long chatId, String indexStr, SessionState state) {
        if (!STEP_CLIENT_PHONE_SELECTION.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            int index = Integer.parseInt(indexStr);
            List<Object[]> clientes = state.getTempClientesEncontrados();

            if (index < 0 || index >= clientes.size()) {
                return "❌ Selección inválida.";
            }

            Object[] clienteSeleccionado = clientes.get(index);
            String telefono = (String) clienteSeleccionado[0];
            Integer edad = (Integer) clienteSeleccionado[1];

            // Guardar teléfono y edad
            state.setTempClienteTelefono(telefono);
            state.setTempClienteEdad(edad);

            // Ir directo a confirmación (saltar input de edad)
            state.setStep(STEP_CONFIRM);

            return mostrarConfirmacionEditando(chatId, state);

        } catch (NumberFormatException e) {
            return "❌ Índice inválido.";
        }
    }

    /**
     * Maneja cuando el usuario quiere agregar un nuevo número.
     */
    private String handleAddNewPhone(SessionState state) {
        if (!STEP_CLIENT_PHONE_SELECTION.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        // Cambiar a paso de ingreso de teléfono
        state.setStep(STEP_CLIENT_PHONE);

        // Editar el mensaje anterior en lugar de enviar uno nuevo
        String mensaje = """
                📱 Ingresá el nuevo teléfono del cliente:

                Ejemplo: +5491123456789 o 1123456789
                """;

        // Como no hay botones, simplemente editamos el mensaje para quitar los botones
        return editMessageText(state.getBarbero().getTelegramChatId(), mensaje, state);
    }

    /**
     * Edita un mensaje existente con nuevo texto (sin botones).
     */
    private String editMessageText(Long chatId, String text, SessionState state) {
        Integer messageId = state.getLastMessageId();

        if (messageId == null) {
            // Si no hay messageId, enviar nuevo mensaje
            sendText(chatId, text);
            return null;
        }

        try {
            org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editMsg =
                new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText();
            editMsg.setChatId(chatId.toString());
            editMsg.setMessageId(messageId);
            editMsg.setText(text);
            bot.execute(editMsg);
            return null;
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.warn("[Telegram] Error editando mensaje chatId={}, messageId={}: {}. Enviando nuevo mensaje.",
                     chatId, messageId, e.getMessage());
            sendText(chatId, text);
            return null;
        }
    }

    /**
     * Muestra confirmación editando el mensaje anterior (para cuando se selecciona un teléfono existente).
     */
    private String mostrarConfirmacionEditando(Long chatId, SessionState state) {
        boolean esFH = state.getTempHora().equals(LocalTime.of(0, 0));
        String horaDisplay = esFH ? "FH (Fuera de horario)" : state.getTempHora().format(TIME_FMT);
        String medioPagoDisplay = "EFECTIVO".equals(state.getTempMedioPago()) ? "Efectivo" : "Transferencia";

        // Calcular precio total (servicio + adicionales)
        BigDecimal precioTotal = BigDecimal.valueOf(state.getTempServicio().getPrecio());
        if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
            for (Long adicionalId : state.getTempAdicionalesIds()) {
                TipoCorte adicional = tipoCorteRepo.findById(adicionalId).orElse(null);
                if (adicional != null) {
                    precioTotal = precioTotal.add(BigDecimal.valueOf(adicional.getPrecio()));
                }
            }
        }

        // Construir lista de adicionales para mostrar
        StringBuilder adicionalesInfo = new StringBuilder();
        if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
            adicionalesInfo.append("\n➕ Adicionales:");
            for (Long adicionalId : state.getTempAdicionalesIds()) {
                tipoCorteRepo.findById(adicionalId).ifPresent(adicional ->
                        adicionalesInfo.append(String.format("\n   • %s ($%d)", adicional.getNombre(), adicional.getPrecio()))
                );
            }
        }

        String mensaje = String.format("""
                ✅ Confirmar bloqueo de turno

                📅 Fecha: %s
                ⏰ Hora: %s
                💇 Servicio: %s ($%d)%s
                💰 Total: $%s
                💳 Medio de pago: %s
                👤 Cliente: %s
                📱 Teléfono: %s
                🎂 Edad: %d años

                Este turno se registrará como turno presencial y aparecerá en el panel administrativo.
                """,
                state.getTempFecha().format(DATE_FMT),
                horaDisplay,
                state.getTempServicio().getNombre(),
                state.getTempServicio().getPrecio(),
                adicionalesInfo.toString(),
                precioTotal,
                medioPagoDisplay,
                state.getTempClienteNombre(),
                state.getTempClienteTelefono(),
                state.getTempClienteEdad()
        );

        // Editar mensaje con botones
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Fila con botones de confirmar y cancelar
        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        confirmRow.add(messageBuilder.buildButton("✅ Sí, confirmar", "CONFIRM_BLOCK_YES"));
        confirmRow.add(messageBuilder.buildButton("❌ Cancelar", "CONFIRM_BLOCK_NO"));
        rows.add(confirmRow);

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Formatea un YearMonth para mostrar (ejemplo: "Diciembre 2024").
     */
    private String formatYearMonth(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
    }
}
