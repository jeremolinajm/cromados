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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler para el comando /fijos.
 *
 * Permite crear turnos recurrentes (mismo cliente, mismo día y hora cada semana).
 *
 * Flujo complejo (9 pasos):
 * 1. Seleccionar servicio
 * 2. Seleccionar día de la semana
 * 3. Seleccionar hora
 * 4. Ingresar número de repeticiones (semanas)
 * 5. Ingresar nombre del cliente
 * 6. Ingresar teléfono del cliente
 * 7. Ingresar edad del cliente
 * 8. Seleccionar medio de pago
 * 9. Confirmar y crear turnos
 */
@Slf4j
@Component
public class FijosCommandHandler extends BaseCommandHandler {

    private static final String STEP_SERVICE = "WAITING_SERVICE_FIJOS";
    private static final String STEP_DAY = "WAITING_DAY_FIJOS";
    private static final String STEP_TIME = "WAITING_TIME_FIJOS";
    private static final String STEP_REPETITIONS = "WAITING_REPETITIONS_FIJOS";
    private static final String STEP_CONFLICT_RESOLUTION = "WAITING_CONFLICT_RESOLUTION_FIJOS";
    private static final String STEP_CLIENT_NAME = "WAITING_CLIENT_NAME_FIJOS";
    private static final String STEP_CLIENT_PHONE = "WAITING_CLIENT_PHONE_FIJOS";
    private static final String STEP_CLIENT_AGE = "WAITING_CLIENT_AGE_FIJOS";
    private static final String STEP_MEDIO_PAGO = "WAITING_MEDIO_PAGO_FIJOS";
    private static final String STEP_CONFIRM = "CONFIRM_FIJOS";

    public FijosCommandHandler(
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
        return "fijos";
    }

    @Override
    public boolean canHandle(String step) {
        return STEP_SERVICE.equals(step) ||
               STEP_DAY.equals(step) ||
               STEP_TIME.equals(step) ||
               STEP_REPETITIONS.equals(step) ||
               STEP_CONFLICT_RESOLUTION.equals(step) ||
               STEP_CLIENT_NAME.equals(step) ||
               STEP_CLIENT_PHONE.equals(step) ||
               STEP_CLIENT_AGE.equals(step) ||
               STEP_MEDIO_PAGO.equals(step) ||
               STEP_CONFIRM.equals(step);
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        state.setStep(STEP_SERVICE);
        return listarServicios(chatId, state);
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
            case "SFIJOS" -> handleServiceCallback(chatId, value, state);
            case "DFIJOS" -> handleDayCallback(chatId, value, state);
            case "TFIJOS" -> handleTimeCallback(value, state);
            case "CONFLICTRES" -> handleConflictResolution(chatId, value, state);
            case "PFIJOS" -> handleMedioPagoCallback(chatId, value, state);
            case "CFIJOS" -> handleConfirmCallback(value, state);
            default -> "❌ Acción no reconocida";
        };
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        return switch (state.getStep()) {
            case STEP_REPETITIONS -> handleRepetitionsInput(chatId, text, state);
            case STEP_CLIENT_NAME -> handleClientNameInput(text, state);
            case STEP_CLIENT_PHONE -> handleClientPhoneInput(text, state);
            case STEP_CLIENT_AGE -> handleClientAgeInput(chatId, text, state);
            default -> "❌ Entrada no esperada";
        };
    }

    /**
     * Muestra lista de servicios disponibles para turnos fijos.
     */
    private String listarServicios(Long chatId, SessionState state) {
        Barbero barbero = getBarbero(state);

        // Obtener solo servicios principales (no adicionales)
        List<TipoCorte> servicios = tipoCorteRepo.findAllWithBarberos().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getAdicional()))
                .filter(s -> {
                    // Si el servicio no tiene barberos habilitado, todos pueden ofrecerlo
                    if (s.getBarberosHabilitados() == null || s.getBarberosHabilitados().isEmpty()) {
                        return true;
                    }
                    // Si tiene barberos habilitados, verificar que este barbero esté en la lista
                    return s.getBarberosHabilitados().stream()
                            .anyMatch(b -> b.getId().equals(barbero.getId()));
                })
                .toList();

        if (servicios.isEmpty()) {
            return "❌ No hay servicios disponibles.";
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Botones para cada servicio
        for (TipoCorte servicio : servicios) {
            String buttonText = String.format("%s - $%d", servicio.getNombre(), servicio.getPrecio());
            rows.add(messageBuilder.createSingleButtonRow(buttonText, "SFIJOS_" + servicio.getId()));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = """
                🔁 Crear turnos recurrentes

                Esta opción te permite crear varios turnos para el mismo cliente en el mismo día y hora cada semana.

                Ejemplo: Todos los viernes a las 15:00 durante 5 semanas.

                Seleccioná el servicio:
                """;

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId, mensaje, keyboard);
    }

    /**
     * Maneja la selección de servicio.
     */
    private String handleServiceCallback(Long chatId, String servicioIdStr, SessionState state) {
        if (!STEP_SERVICE.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            Long servicioId = Long.parseLong(servicioIdStr);
            TipoCorte servicio = tipoCorteRepo.findById(servicioId)
                    .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

            state.setTempServicioId(servicioId);
            state.setTempServicio(servicio);
            state.setStep(STEP_DAY);

            return mostrarDiasSemana(chatId, state, servicio);

        } catch (NumberFormatException e) {
            return "❌ ID de servicio inválido.";
        }
    }

    /**
     * Muestra los días de la semana disponibles.
     */
    private String mostrarDiasSemana(Long chatId, SessionState state, TipoCorte servicio) {
        Barbero barbero = getBarbero(state);

        // Obtener días habilitados del barbero
        List<Integer> diasHabilitados = horarioRepo.findByBarbero_Id(barbero.getId())
                .stream()
                .map(HorarioBarbero::getDiaSemana)
                .distinct()
                .sorted()
                .toList();

        if (diasHabilitados.isEmpty()) {
            return "❌ No tenés días de trabajo configurados. Configurá tus horarios en el panel web primero.";
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Mapeo de día de semana a nombre y DayOfWeek
        Map<Integer, String[]> diasMap = Map.of(
            1, new String[]{"Lunes", "MONDAY"},
            2, new String[]{"Martes", "TUESDAY"},
            3, new String[]{"Miércoles", "WEDNESDAY"},
            4, new String[]{"Jueves", "THURSDAY"},
            5, new String[]{"Viernes", "FRIDAY"},
            6, new String[]{"Sábado", "SATURDAY"},
            7, new String[]{"Domingo", "SUNDAY"}
        );

        // Solo mostrar días habilitados
        for (Integer diaSemana : diasHabilitados) {
            String[] diaInfo = diasMap.get(diaSemana);
            if (diaInfo != null) {
                rows.add(messageBuilder.createSingleButtonRow(diaInfo[0], "DFIJOS_" + diaInfo[1]));
            }
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = String.format("""
                📅 Día de la semana

                Servicio: %s

                ¿Qué día de la semana se va a repetir?
                """, servicio.getNombre());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección del día de la semana.
     */
    private String handleDayCallback(Long chatId, String dayStr, SessionState state) {
        if (!STEP_DAY.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        DayOfWeek day = switch (dayStr) {
            case "MONDAY" -> DayOfWeek.MONDAY;
            case "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> null;
        };

        if (day == null) {
            return "❌ Día inválido.";
        }

        state.setTempDayOfWeek(day);

        // Calcular la próxima fecha de ese día
        LocalDate proximaFecha = LocalDate.now();
        while (proximaFecha.getDayOfWeek() != day) {
            proximaFecha = proximaFecha.plusDays(1);
        }

        Barbero barbero = getBarbero(state);

        // ✅ USAR HorarioService como única fuente de verdad
        List<LocalTime> horariosDisponibles = horarioService.horariosDisponibles(barbero.getId(), proximaFecha);

        if (horariosDisponibles.isEmpty()) {
            state.reset();
            return String.format("""
                    ❌ No hay horarios disponibles para el próximo %s (%s).

                    Todos los horarios están ocupados o bloqueados.

                    Usa /fijos para intentar con otro día.
                    """,
                    day.getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR")),
                    proximaFecha.format(DATE_FMT));
        }

        state.setHorariosDisponibles(horariosDisponibles);
        state.setStep(STEP_TIME);

        return mostrarHorarios(chatId, state, day);
    }

    /**
     * Muestra horarios disponibles.
     */
    private String mostrarHorarios(Long chatId, SessionState state, DayOfWeek day) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Mostrar máximo 20 horarios
        int count = 0;
        for (LocalTime hora : state.getHorariosDisponibles()) {
            if (count++ >= 20) break;
            rows.add(messageBuilder.createSingleButtonRow(
                    hora.format(TIME_FMT),
                    "TFIJOS_" + hora.toString()
            ));
        }

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String dayName = day.getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR"));
        String mensaje = String.format("""
                ⏰ Horario

                Servicio: %s
                Día: %s

                Seleccioná la hora:
                """, state.getTempServicio().getNombre(), dayName);

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje, keyboard, state);
    }

    /**
     * Maneja la selección de hora.
     */
    private String handleTimeCallback(String horaStr, SessionState state) {
        if (!STEP_TIME.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            LocalTime hora = LocalTime.parse(horaStr);
            state.setTempHora(hora);
            state.setStep(STEP_REPETITIONS);

            return """
                    🔢 Número de repeticiones

                    ¿Cuántas semanas querés que se repita este turno?

                    Ejemplo: 5 (para 5 semanas consecutivas)

                    Escribí un número entre 1 y 12:
                    """;

        } catch (Exception e) {
            return "❌ Hora inválida.";
        }
    }

    /**
     * Maneja el input de número de repeticiones.
     */
    private String handleRepetitionsInput(Long chatId, String text, SessionState state) {
        if (!STEP_REPETITIONS.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        try {
            int repetitions = Integer.parseInt(text.trim());

            if (repetitions < 1 || repetitions > 12) {
                return "❌ El número debe estar entre 1 y 12.\n\nIngresá la cantidad de semanas:";
            }

            state.setTempRepetitions(repetitions);

            // Calcular fechas
            LocalDate proximaFecha = LocalDate.now();
            while (proximaFecha.getDayOfWeek() != state.getTempDayOfWeek()) {
                proximaFecha = proximaFecha.plusDays(1);
            }

            List<LocalDate> fechas = new ArrayList<>();
            for (int i = 0; i < repetitions; i++) {
                fechas.add(proximaFecha.plusWeeks(i));
            }

            state.setTempFechasFijos(fechas);

            // Verificar conflictos
            List<LocalDate> conflictos = verificarConflictos(state, fechas);

            String dayName = state.getTempDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR"));
            StringBuilder mensaje = new StringBuilder();
            mensaje.append(String.format("""
                    📊 Resumen de turnos recurrentes

                    Servicio: %s
                    Día: %s
                    Hora: %s
                    Repeticiones: %d semanas

                    Fechas:
                    """,
                    state.getTempServicio().getNombre(),
                    dayName,
                    state.getTempHora().format(TIME_FMT),
                    repetitions));

            for (LocalDate fecha : fechas) {
                boolean esConflicto = conflictos.contains(fecha);
                String marca = esConflicto ? "❌" : "✅";
                mensaje.append(String.format("%s %s%s\n",
                        marca,
                        fecha.format(DATE_FMT),
                        esConflicto ? " (ocupado)" : ""));
            }

            if (!conflictos.isEmpty()) {
                // Guardar conflictos en el estado para usarlos después
                state.setTempConflictDates(conflictos);

                // Hay conflictos, mostrar botones para resolver
                mensaje.append(String.format("""

                        ⚠️ Advertencia: Hay %d fechas con conflictos.

                        ¿Qué querés hacer?
                        """, conflictos.size()));

                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                rows.add(messageBuilder.createSingleButtonRow(
                        "✅ Continuar con turnos disponibles",
                        "CONFLICTRES_CONTINUE"
                ));
                rows.add(messageBuilder.createSingleButtonRow(
                        "🔄 Cambiar horario",
                        "CONFLICTRES_CHANGE"
                ));
                rows.add(messageBuilder.createSingleButtonRow(
                        "📋 Ver turnos a mover",
                        "CONFLICTRES_MOVER"
                ));
                rows.add(messageBuilder.createCancelButton());

                InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
                state.setStep(STEP_CONFLICT_RESOLUTION);

                return sendMessageWithButtons(chatId, mensaje.toString(), keyboard);
            }

            // No hay conflictos, continuar normalmente
            mensaje.append("\nIngresá el nombre del cliente:\n");
            state.setStep(STEP_CLIENT_NAME);

            return mensaje.toString();

        } catch (NumberFormatException e) {
            return "❌ Número inválido.\n\nIngresá la cantidad de semanas (1-12):";
        }
    }

    /**
     * Verifica conflictos en las fechas.
     * Optimizado para evitar N+1 queries.
     */
    private List<LocalDate> verificarConflictos(SessionState state, List<LocalDate> fechas) {
        if (fechas.isEmpty()) {
            return new ArrayList<>();
        }

        Barbero barbero = getBarbero(state);
        List<LocalDate> conflictos = new ArrayList<>();

        // ✅ OPTIMIZACIÓN: Una sola consulta para todas las fechas
        LocalDate min = fechas.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = fechas.stream().max(LocalDate::compareTo).orElseThrow();

        List<Turno> todosTurnos = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(barbero.getId(), min, max);

        // Crear map para lookup rápido por fecha
        Map<LocalDate, List<Turno>> turnosPorFecha = todosTurnos.stream()
                .collect(Collectors.groupingBy(Turno::getFecha));

        // Verificar conflictos sin hacer queries adicionales
        for (LocalDate fecha : fechas) {
            List<Turno> turnosDia = turnosPorFecha.getOrDefault(fecha, List.of());
            boolean ocupado = turnosDia.stream()
                    .filter(t -> t.getHora().equals(state.getTempHora()))
                    .anyMatch(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                            || "CONFIRMADO".equals(t.getEstado())
                            || "BLOQUEADO".equals(t.getEstado()));

            if (ocupado) {
                conflictos.add(fecha);
            }
        }

        return conflictos;
    }

    /**
     * Maneja la resolución de conflictos en turnos fijos.
     */
    private String handleConflictResolution(Long chatId, String value, SessionState state) {
        if ("CONTINUE".equals(value)) {
            // Continuar con turnos disponibles
            state.setStep(STEP_CLIENT_NAME);
            return """
                    ✅ Se crearán los turnos disponibles (omitiendo los ocupados).

                    Ingresá el nombre del cliente:
                    """;
        } else if ("CHANGE".equals(value)) {
            // Cambiar horario - volver a mostrar horarios disponibles
            DayOfWeek day = state.getTempDayOfWeek();
            if (day == null) {
                state.reset();
                return "❌ Error: día no encontrado. Iniciá el comando nuevamente con /fijos";
            }

            // Recalcular horarios disponibles
            LocalDate proximaFecha = LocalDate.now();
            while (proximaFecha.getDayOfWeek() != day) {
                proximaFecha = proximaFecha.plusDays(1);
            }

            Barbero barbero = getBarbero(state);
            List<LocalTime> horariosDisponibles = horarioService.horariosDisponibles(barbero.getId(), proximaFecha);

            if (horariosDisponibles.isEmpty()) {
                state.reset();
                return String.format("""
                        ❌ No hay horarios disponibles para el próximo %s (%s).

                        Todos los horarios están ocupados o bloqueados.

                        Usa /fijos para intentar con otro día.
                        """,
                        day.getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR")),
                        proximaFecha.format(DATE_FMT));
            }

            state.setHorariosDisponibles(horariosDisponibles);
            state.setStep(STEP_TIME);

            return mostrarHorarios(chatId, state, day);
        } else if ("MOVER".equals(value)) {
            // Mostrar turnos que están ocupando los horarios conflictivos
            return mostrarTurnosAMover(state);
        }

        return "❌ Opción no reconocida";
    }

    /**
     * Muestra los turnos que están ocupando los horarios conflictivos.
     * Optimizado para evitar N+1 queries.
     */
    private String mostrarTurnosAMover(SessionState state) {
        Barbero barbero = getBarbero(state);
        List<LocalDate> conflictos = state.getTempConflictDates();

        if (conflictos == null || conflictos.isEmpty()) {
            state.reset();
            return "❌ No hay conflictos para mostrar.";
        }

        LocalTime hora = state.getTempHora();
        StringBuilder mensaje = new StringBuilder();
        mensaje.append(String.format("""
                📋 Turnos que ocupan el horario %s:

                Estos turnos necesitan ser movidos para liberar el horario.
                Usá /mover para cambiar cada uno de estos turnos:

                """, hora.format(TIME_FMT)));

        // ✅ OPTIMIZACIÓN: Una sola consulta para todas las fechas
        LocalDate min = conflictos.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = conflictos.stream().max(LocalDate::compareTo).orElseThrow();

        List<Turno> todosTurnos = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(barbero.getId(), min, max);

        // Filtrar y agrupar por fecha
        Map<LocalDate, List<Turno>> turnosPorFecha = todosTurnos.stream()
                .filter(t -> t.getHora().equals(hora))
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                        || "CONFIRMADO".equals(t.getEstado())
                        || "BLOQUEADO".equals(t.getEstado()))
                .collect(Collectors.groupingBy(Turno::getFecha));

        // Buscar los turnos que están en esos horarios
        int count = 0;
        for (LocalDate fecha : conflictos) {
            List<Turno> turnosEnConflicto = turnosPorFecha.getOrDefault(fecha, List.of());

            for (Turno t : turnosEnConflicto) {
                count++;
                String tipo = Boolean.TRUE.equals(t.getPagoConfirmado()) ? "WEB" : "PRESENCIAL";
                mensaje.append(String.format("""
                        %d. 📅 %s %s
                           👤 %s
                           💇 %s
                           🌐 Turno %s

                        """,
                        count,
                        fecha.format(DATE_FMT),
                        hora.format(TIME_FMT),
                        t.getClienteNombre(),
                        t.getTipoCorte() != null ? t.getTipoCorte().getNombre() : "-",
                        tipo
                ));
            }
        }

        if (count == 0) {
            mensaje.append("No se encontraron turnos en los horarios conflictivos.\n");
        } else {
            mensaje.append(String.format("""
                    ⚠️ Total: %d turno(s) a mover

                    📝 Instrucciones:
                    1. Usá /mover para mover cada turno
                    2. Luego volvé a usar /fijos

                    O podés elegir:
                    • Cambiar el horario del turno fijo
                    • Continuar omitiendo estas fechas
                    """, count));
        }

        state.reset();
        return mensaje.toString();
    }

    /**
     * Maneja el input de nombre del cliente.
     */
    private String handleClientNameInput(String text, SessionState state) {
        if (!STEP_CLIENT_NAME.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        if (text.trim().length() < 2) {
            return "❌ El nombre debe tener al menos 2 caracteres.\n\nIngresá el nombre:";
        }

        state.setTempClienteNombre(text.trim());
        state.setStep(STEP_CLIENT_PHONE);

        return """
                📱 Ingresá el teléfono del cliente:

                Ejemplo: +5491123456789 o 1123456789
                """;
    }

    /**
     * Maneja el input de teléfono del cliente.
     */
    private String handleClientPhoneInput(String text, SessionState state) {
        if (!STEP_CLIENT_PHONE.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

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

        return "🎂 Ingresá la edad del cliente:";
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
        if (!STEP_CLIENT_AGE.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (text.equalsIgnoreCase("cancelar")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        try {
            int edad = Integer.parseInt(text.trim());
            if (edad < 1 || edad > 120) {
                return "❌ Edad inválida.\n\nIngresá la edad:";
            }

            state.setTempClienteEdad(edad);
            state.setStep(STEP_MEDIO_PAGO);

            return mostrarMediosPago(chatId, state);

        } catch (NumberFormatException e) {
            return "❌ Edad inválida.\n\nIngresá un número:";
        }
    }

    /**
     * Muestra botones para seleccionar medio de pago.
     */
    private String mostrarMediosPago(Long chatId, SessionState state) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Botones de medio de pago
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(messageBuilder.buildButton("💵 Efectivo", "PFIJOS_EFECTIVO"));
        row1.add(messageBuilder.buildButton("🏦 Transferencia", "PFIJOS_TRANSFERENCIA"));
        rows.add(row1);

        // Botón cancelar
        rows.add(messageBuilder.createCancelButton());

        String mensaje = "💳 Medio de pago\n\n¿Cómo pagó el cliente?";
        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);

        return sendMessageWithButtons(chatId, mensaje, keyboard);
    }

    /**
     * Maneja la selección de medio de pago.
     */
    private String handleMedioPagoCallback(Long chatId, String value, SessionState state) {
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

        state.setStep(STEP_CONFIRM);

        return mostrarConfirmacion(chatId, state);
    }

    /**
     * Muestra resumen y confirmación final.
     */
    private String mostrarConfirmacion(Long chatId, SessionState state) {
        List<List<InlineKeyboardButton>> rows = messageBuilder.createConfirmationButtons(
                "CFIJOS_YES",
                "CFIJOS_NO"
        );

        String dayName = state.getTempDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR"));
        String medioPago = "EFECTIVO".equals(state.getTempMedioPago()) ? "💵 Efectivo" : "💳 Transferencia";

        StringBuilder mensaje = new StringBuilder();
        mensaje.append(String.format("""
                ✅ Confirmar turnos recurrentes

                👤 Cliente: %s
                📱 Teléfono: %s
                🎂 Edad: %d

                💇 Servicio: %s - $%d
                📅 Día: %s
                ⏰ Hora: %s
                💰 Pago: %s

                📊 Se crearán %d turnos:

                """,
                state.getTempClienteNombre(),
                state.getTempClienteTelefono(),
                state.getTempClienteEdad(),
                state.getTempServicio().getNombre(),
                state.getTempServicio().getPrecio(),
                dayName,
                state.getTempHora().format(TIME_FMT),
                medioPago,
                state.getTempFechasFijos().size()));

        // Verificar conflictos nuevamente
        Barbero barbero = getBarbero(state);

        // ✅ OPTIMIZACIÓN: Una sola consulta para todas las fechas
        LocalDate minFecha = state.getTempFechasFijos().stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate maxFecha = state.getTempFechasFijos().stream().max(LocalDate::compareTo).orElseThrow();

        List<Turno> todosTurnos = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(barbero.getId(), minFecha, maxFecha);
        Map<LocalDate, List<Turno>> turnosPorFecha = todosTurnos.stream()
                .collect(Collectors.groupingBy(Turno::getFecha));

        int disponibles = 0;
        for (LocalDate fecha : state.getTempFechasFijos()) {
            List<Turno> turnosDia = turnosPorFecha.getOrDefault(fecha, List.of());
            boolean ocupado = turnosDia.stream()
                    .filter(t -> t.getHora().equals(state.getTempHora()))
                    .anyMatch(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                            || "CONFIRMADO".equals(t.getEstado())
                            || "BLOQUEADO".equals(t.getEstado()));

            String marca = ocupado ? "❌" : "✅";
            if (!ocupado) disponibles++;
            mensaje.append(String.format("%s %s%s\n",
                    marca,
                    fecha.format(DATE_FMT),
                    ocupado ? " (saltado)" : ""));
        }

        if (disponibles == 0) {
            return "❌ Todos los horarios están ocupados. No se pueden crear turnos.";
        }

        mensaje.append(String.format("""

                ✅ Turnos disponibles: %d
                ❌ Turnos saltados: %d

                ¿Confirmar creación?
                """, disponibles, state.getTempFechasFijos().size() - disponibles));

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, mensaje.toString(), keyboard, state);
    }

    /**
     * Maneja la confirmación final y crea los turnos.
     */
    private String handleConfirmCallback(String value, SessionState state) {
        if (!STEP_CONFIRM.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (!"YES".equals(value)) {
            state.reset();
            return "❌ Operación cancelada";
        }

        try {
            // Recuperar entidades frescas
            Barbero barbero = barberoRepo.findById(state.getBarbero().getId())
                    .orElseThrow(() -> new IllegalStateException("Barbero no encontrado"));

            TipoCorte servicio = tipoCorteRepo.findById(state.getTempServicioId())
                    .orElseThrow(() -> new IllegalStateException("Servicio no encontrado"));

            Sucursal sucursal = barbero.getSucursal();
            if (sucursal == null) {
                throw new IllegalStateException("El barbero no tiene sucursal asignada");
            }

            BigDecimal precio = BigDecimal.valueOf(servicio.getPrecio());
            List<Turno> creados = new ArrayList<>();
            int saltados = 0;

            // ✅ OPTIMIZACIÓN: Una sola consulta para todas las fechas
            LocalDate minFecha = state.getTempFechasFijos().stream().min(LocalDate::compareTo).orElseThrow();
            LocalDate maxFecha = state.getTempFechasFijos().stream().max(LocalDate::compareTo).orElseThrow();

            List<Turno> todosTurnosExistentes = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(barbero.getId(), minFecha, maxFecha);
            Map<LocalDate, List<Turno>> turnosPorFecha = todosTurnosExistentes.stream()
                    .collect(Collectors.groupingBy(Turno::getFecha));

            // Crear turnos para cada fecha disponible
            for (LocalDate fecha : state.getTempFechasFijos()) {
                // Verificar si está ocupado
                List<Turno> turnosDia = turnosPorFecha.getOrDefault(fecha, List.of());
                boolean ocupado = turnosDia.stream()
                        .filter(t -> t.getHora().equals(state.getTempHora()))
                        .anyMatch(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                                || "CONFIRMADO".equals(t.getEstado())
                                || "BLOQUEADO".equals(t.getEstado()));

                if (ocupado) {
                    saltados++;
                    continue;
                }

                // Crear turno
                Turno turno = new Turno();
                turno.setBarbero(barbero);
                turno.setSucursal(sucursal);
                turno.setTipoCorte(servicio);
                turno.setFecha(fecha);
                turno.setHora(state.getTempHora());
                turno.setEstado("BLOQUEADO");
                turno.setPagoConfirmado(false);
                turno.setClienteNombre(state.getTempClienteNombre());
                turno.setClienteTelefono(state.getTempClienteTelefono());
                turno.setClienteEdad(state.getTempClienteEdad());
                turno.setSenia(false);

                if ("EFECTIVO".equals(state.getTempMedioPago())) {
                    turno.setMontoEfectivo(precio);
                    turno.setMontoPagado(BigDecimal.ZERO);
                } else {
                    turno.setMontoPagado(precio);
                    turno.setMontoEfectivo(BigDecimal.ZERO);
                }

                creados.add(turnoRepo.save(turno));
            }

            if (creados.isEmpty()) {
                state.reset();
                return "❌ No se pudo crear ningún turno. Todos los horarios están ocupados.";
            }

            // Notificar al admin
            String dayName = state.getTempDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR"));
            String medioPago = "EFECTIVO".equals(state.getTempMedioPago()) ? "💵 Efectivo" : "💳 Transferencia";

            notificarAdmin(String.format("""
                    🔁 Turnos recurrentes creados por %s

                    👤 Cliente: %s
                    💇 Servicio: %s
                    📅 Día: %s a las %s
                    💰 Pago: %s

                    ✅ Turnos creados: %d
                    ❌ Turnos saltados (ocupados): %d

                    Fechas confirmadas:
                    %s
                    """,
                    barbero.getNombre(),
                    state.getTempClienteNombre(),
                    servicio.getNombre(),
                    dayName,
                    state.getTempHora().format(TIME_FMT),
                    medioPago,
                    creados.size(),
                    saltados,
                    creados.stream()
                            .map(t -> "• " + t.getFecha().format(DATE_FMT))
                            .collect(java.util.stream.Collectors.joining("\n"))
            ));

            StringBuilder resultado = new StringBuilder();
            resultado.append(String.format("""
                    ✅ Turnos recurrentes creados exitosamente

                    👤 Cliente: %s
                    💇 %s - $%d
                    📅 %s a las %s

                    ✅ Creados: %d turnos
                    """,
                    state.getTempClienteNombre(),
                    servicio.getNombre(),
                    servicio.getPrecio(),
                    dayName,
                    state.getTempHora().format(TIME_FMT),
                    creados.size()));

            if (saltados > 0) {
                resultado.append(String.format("⚠️ %d turnos saltados (horarios ocupados)\n\n", saltados));
            }

            resultado.append("Escribe /menu para volver al menú principal.");

            state.reset();
            return resultado.toString();

        } catch (Exception e) {
            log.error("[Telegram] Error creando turnos fijos: {}", e.getMessage(), e);
            state.reset();
            return "❌ Error creando turnos: " + e.getMessage();
        }
    }
}
