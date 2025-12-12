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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler para el comando /adicional.
 *
 * Permite agregar servicios adicionales a turnos existentes.
 * Esto es útil cuando un cliente solicita adicionales durante el servicio.
 *
 * Flujo:
 * 1. Mostrar turnos próximos del barbero (hoy y futuros)
 * 2. Seleccionar un turno
 * 3. Seleccionar servicios adicionales para agregar
 * 4. Confirmar y actualizar el turno con el nuevo monto
 */
@Slf4j
@Component
public class AdicionalCommandHandler extends BaseCommandHandler {

    private static final String STEP_SELECT_TURNO = "WAITING_TURNO_ADICIONAL";
    private static final String STEP_SELECT_ADICIONALES = "SELECTING_ADICIONALES_ADD";
    private static final String STEP_CONFIRM = "CONFIRM_ADICIONALES";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public AdicionalCommandHandler(
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
        return "adicional";
    }

    @Override
    public boolean canHandle(String step) {
        return STEP_SELECT_TURNO.equals(step) ||
               STEP_SELECT_ADICIONALES.equals(step) ||
               STEP_CONFIRM.equals(step);
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        state.setStep(STEP_SELECT_TURNO);
        return showTurnosList(chatId, state);
    }

    @Override
    public String handleCallback(Long chatId, String callbackData, SessionState state) {
        log.info("[Telegram] AdicionalCommandHandler callback: {}", callbackData);

        if (callbackData.equals("CANCEL")) {
            state.reset();
            return "❌ Operación cancelada";
        }

        String[] parts = callbackData.split("_", 2);
        String action = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        return switch (action) {
            case "TURNO" -> handleTurnoSelection(chatId, value, state);
            case "ADDSRV" -> handleAdicionalSelection(chatId, value, state);
            case "DONE" -> handleDoneSelection(chatId, state);
            case "CONFIRM" -> handleConfirmation(chatId, value, state);
            default -> "❌ Acción no reconocida";
        };
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        return "❌ Entrada no esperada. Usá los botones para seleccionar.";
    }

    /**
     * Muestra la lista de turnos del barbero (últimos 30 días + futuros).
     * Permite agregar adicionales a turnos pasados para ajustar montos finales.
     */
    private String showTurnosList(Long chatId, SessionState state) {
        Barbero barbero = getBarbero(state);

        // Obtener turnos desde 30 días atrás hasta 1 mes adelante
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(30);  // Últimos 30 días
        LocalDate hasta = hoy.plusMonths(1);   // Próximo mes

        List<Turno> turnos = turnoRepo.findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(
                barbero.getId(), desde, hasta);

        // Filtrar solo turnos confirmados o bloqueados (no pendientes de pago)
        turnos = turnos.stream()
                .filter(t -> Boolean.TRUE.equals(t.getPagoConfirmado())
                          || "CONFIRMADO".equals(t.getEstado())
                          || "BLOQUEADO".equals(t.getEstado()))
                .toList();

        if (turnos.isEmpty()) {
            state.reset();
            return "ℹ️ No tenés turnos confirmados en los últimos 30 días ni próximos.";
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Turno t : turnos) {
            String label = String.format("%s %s - %s - %s",
                    t.getFecha().format(DATE_FMT),
                    t.getHora().format(TIME_FMT),
                    t.getClienteNombre(),
                    t.getTipoCorte().getNombre()
            );
            rows.add(messageBuilder.createSingleButtonRow(label, "TURNO_" + t.getId()));
        }

        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId,
                "📋 Seleccioná el turno al que querés agregar adicionales:", keyboard);
    }

    /**
     * Maneja la selección de un turno.
     */
    private String handleTurnoSelection(Long chatId, String turnoIdStr, SessionState state) {
        if (!STEP_SELECT_TURNO.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia. Usa /adicional para empezar.";
        }

        try {
            Long turnoId = Long.parseLong(turnoIdStr);
            Turno turno = turnoRepo.findById(turnoId)
                    .orElse(null);

            if (turno == null) {
                state.reset();
                return "❌ Turno no encontrado.";
            }

            // Guardar el turno seleccionado
            state.setTempTurnoIdToMove(turnoId);
            state.setStep(STEP_SELECT_ADICIONALES);

            // Inicializar lista de adicionales a agregar
            state.setTempAdicionalesIds(new ArrayList<>());

            return showAdicionalesSelection(chatId, state, turno);

        } catch (NumberFormatException e) {
            return "❌ ID de turno inválido.";
        }
    }

    /**
     * Muestra los servicios adicionales disponibles para seleccionar.
     */
    private String showAdicionalesSelection(Long chatId, SessionState state, Turno turno) {
        // Obtener adicionales disponibles
        List<TipoCorte> adicionales = tipoCorteRepo.findAll().stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getAdicional()))
                .filter(tc -> Boolean.TRUE.equals(tc.getActivo()))
                .toList();

        if (adicionales.isEmpty()) {
            state.reset();
            return "ℹ️ No hay servicios adicionales disponibles.";
        }

        // Obtener nombres de adicionales ya existentes en el turno
        List<String> adicionalesExistentes = new ArrayList<>();
        if (turno.getAdicionales() != null && !turno.getAdicionales().isEmpty()) {
            String[] partes = turno.getAdicionales().split(",");
            for (String parte : partes) {
                adicionalesExistentes.add(parte.trim().toLowerCase());
            }
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Mostrar información del turno
        StringBuilder message = new StringBuilder();
        message.append("➕ Agregar adicionales al turno:\n\n");
        message.append(String.format("👤 Cliente: %s\n", turno.getClienteNombre()));
        message.append(String.format("📅 Fecha: %s %s\n",
                turno.getFecha().format(DATE_FMT),
                turno.getHora().format(TIME_FMT)));
        message.append(String.format("✂️ Servicio: %s\n\n", turno.getTipoCorte().getNombre()));

        // Mostrar adicionales ya seleccionados
        if (turno.getAdicionales() != null && !turno.getAdicionales().isEmpty()) {
            message.append("📦 Adicionales actuales:\n");
            message.append(turno.getAdicionales()).append("\n\n");
        }

        // Mostrar adicionales en proceso de agregarse
        if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
            message.append("✅ Seleccionados para agregar:\n");
            for (Long adicionalId : state.getTempAdicionalesIds()) {
                tipoCorteRepo.findById(adicionalId).ifPresent(a ->
                    message.append(String.format("   • %s ($%d)\n", a.getNombre(), a.getPrecio()))
                );
            }
            message.append("\n");
        }

        message.append("Seleccioná los adicionales a agregar:");

        // Botones de adicionales (filtrar los ya seleccionados Y los que ya tiene el turno)
        for (TipoCorte adicional : adicionales) {
            // Verificar si ya está en la selección temporal
            boolean yaSeleccionado = state.getTempAdicionalesIds() != null
                && state.getTempAdicionalesIds().contains(adicional.getId());

            // Verificar si ya existe en el turno actual
            boolean yaExisteEnTurno = adicionalesExistentes.contains(adicional.getNombre().toLowerCase());

            if (!yaSeleccionado && !yaExisteEnTurno) {
                String label = String.format("%s - $%d", adicional.getNombre(), adicional.getPrecio());
                rows.add(messageBuilder.createSingleButtonRow(label, "ADDSRV_" + adicional.getId()));
            }
        }

        // Botón "Listo" si ya hay adicionales seleccionados
        if (state.getTempAdicionalesIds() != null && !state.getTempAdicionalesIds().isEmpty()) {
            rows.add(messageBuilder.createSingleButtonRow("✅ Listo", "DONE_ADD"));
        }

        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, message.toString(), keyboard, state);
    }

    /**
     * Maneja la selección de un adicional.
     */
    private String handleAdicionalSelection(Long chatId, String adicionalIdStr, SessionState state) {
        if (!STEP_SELECT_ADICIONALES.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        try {
            Long adicionalId = Long.parseLong(adicionalIdStr);

            // Agregar a la lista
            if (state.getTempAdicionalesIds() == null) {
                state.setTempAdicionalesIds(new ArrayList<>());
            }
            if (!state.getTempAdicionalesIds().contains(adicionalId)) {
                state.getTempAdicionalesIds().add(adicionalId);
            }

            // Recargar turno y mostrar nuevamente
            Turno turno = turnoRepo.findById(state.getTempTurnoIdToMove()).orElse(null);
            if (turno == null) {
                state.reset();
                return "❌ Turno no encontrado.";
            }

            return showAdicionalesSelection(chatId, state, turno);

        } catch (NumberFormatException e) {
            return "❌ ID de adicional inválido.";
        }
    }

    /**
     * Maneja el botón "Listo" cuando el usuario terminó de seleccionar adicionales.
     */
    private String handleDoneSelection(Long chatId, SessionState state) {
        if (!STEP_SELECT_ADICIONALES.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (state.getTempAdicionalesIds() == null || state.getTempAdicionalesIds().isEmpty()) {
            state.reset();
            return "❌ No seleccionaste ningún adicional. Usá /adicional para intentar de nuevo.";
        }

        state.setStep(STEP_CONFIRM);
        return showConfirmation(chatId, state);
    }

    /**
     * Muestra la confirmación final con el resumen de adicionales y nuevo monto.
     */
    private String showConfirmation(Long chatId, SessionState state) {
        Turno turno = turnoRepo.findById(state.getTempTurnoIdToMove()).orElse(null);
        if (turno == null) {
            state.reset();
            return "❌ Turno no encontrado.";
        }

        // Calcular monto actual (considerar ambos campos: montoPagado O montoEfectivo)
        BigDecimal montoActual;
        if (turno.getMontoEfectivo() != null && turno.getMontoEfectivo().compareTo(BigDecimal.ZERO) > 0) {
            montoActual = turno.getMontoEfectivo();
        } else {
            montoActual = turno.getMontoPagado() != null ? turno.getMontoPagado() : BigDecimal.ZERO;
        }

        BigDecimal montoAdicionales = BigDecimal.ZERO;

        List<String> adicionalesNombres = new ArrayList<>();
        for (Long adicionalId : state.getTempAdicionalesIds()) {
            TipoCorte adicional = tipoCorteRepo.findById(adicionalId).orElse(null);
            if (adicional != null) {
                adicionalesNombres.add(adicional.getNombre());
                montoAdicionales = montoAdicionales.add(BigDecimal.valueOf(adicional.getPrecio()));
            }
        }

        BigDecimal nuevoMonto = montoActual.add(montoAdicionales);

        String adicionalesStr = String.join(", ", adicionalesNombres);

        StringBuilder message = new StringBuilder();
        message.append("📋 Confirmar adicionales\n\n");
        message.append(String.format("👤 Cliente: %s\n", turno.getClienteNombre()));
        message.append(String.format("📅 Fecha: %s %s\n\n",
                turno.getFecha().format(DATE_FMT),
                turno.getHora().format(TIME_FMT)));

        if (turno.getAdicionales() != null && !turno.getAdicionales().isEmpty()) {
            message.append("📦 Adicionales actuales:\n");
            message.append(turno.getAdicionales()).append("\n\n");
        }

        message.append("➕ Adicionales a agregar:\n");
        message.append(adicionalesStr).append("\n\n");

        message.append(String.format("💰 Monto actual: $%d\n", montoActual.intValue()));
        message.append(String.format("➕ Adicionales: $%d\n", montoAdicionales.intValue()));
        message.append(String.format("💵 NUEVO MONTO TOTAL: $%d\n\n", nuevoMonto.intValue()));

        message.append("¿Confirmar la operación?");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(messageBuilder.createDoubleButtonRow("✅ Confirmar", "CONFIRM_YES", "❌ Cancelar", "CANCEL"));

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return editMessageWithButtons(chatId, message.toString(), keyboard, state);
    }

    /**
     * Maneja la confirmación final.
     */
    private String handleConfirmation(Long chatId, String value, SessionState state) {
        if (!STEP_CONFIRM.equals(state.getStep())) {
            return "❌ Comando fuera de secuencia.";
        }

        if (!"YES".equals(value)) {
            state.reset();
            return "❌ Operación cancelada.";
        }

        try {
            Turno turno = turnoRepo.findById(state.getTempTurnoIdToMove()).orElse(null);
            if (turno == null) {
                state.reset();
                return "❌ Turno no encontrado.";
            }

            // Construir la nueva lista de adicionales
            List<String> adicionalesNuevos = new ArrayList<>();
            BigDecimal montoAdicionales = BigDecimal.ZERO;

            for (Long adicionalId : state.getTempAdicionalesIds()) {
                TipoCorte adicional = tipoCorteRepo.findById(adicionalId).orElse(null);
                if (adicional != null) {
                    adicionalesNuevos.add(adicional.getNombre());
                    montoAdicionales = montoAdicionales.add(BigDecimal.valueOf(adicional.getPrecio()));
                }
            }

            // Combinar con adicionales existentes
            String adicionalesActuales = turno.getAdicionales();
            if (adicionalesActuales != null && !adicionalesActuales.isEmpty()) {
                adicionalesNuevos.add(0, adicionalesActuales);
            }

            String adicionalesFinales = String.join(", ", adicionalesNuevos);

            // Actualizar turno
            turno.setAdicionales(adicionalesFinales);

            // Actualizar monto según el medio de pago original
            // montoPagado y montoEfectivo son mutuamente excluyentes:
            // - Si pagó con app/transferencia: montoPagado tiene valor, montoEfectivo = 0
            // - Si pagó en efectivo: montoEfectivo tiene valor, montoPagado = 0
            BigDecimal nuevoMonto;
            if (turno.getMontoEfectivo() != null && turno.getMontoEfectivo().compareTo(BigDecimal.ZERO) > 0) {
                // Turno pagado en efectivo - actualizar solo montoEfectivo
                nuevoMonto = turno.getMontoEfectivo().add(montoAdicionales);
                turno.setMontoEfectivo(nuevoMonto);
            } else {
                // Turno pagado con app/transferencia - actualizar solo montoPagado
                BigDecimal montoActual = turno.getMontoPagado() != null ? turno.getMontoPagado() : BigDecimal.ZERO;
                nuevoMonto = montoActual.add(montoAdicionales);
                turno.setMontoPagado(nuevoMonto);
            }

            turnoRepo.save(turno);

            state.reset();

            return String.format("""
                    ✅ Adicionales agregados exitosamente

                    👤 Cliente: %s
                    📅 Fecha: %s %s

                    ➕ Adicionales agregados:
                    %s

                    💵 Nuevo monto total: $%d

                    El turno ha sido actualizado.
                    """,
                    turno.getClienteNombre(),
                    turno.getFecha().format(DATE_FMT),
                    turno.getHora().format(TIME_FMT),
                    String.join(", ", adicionalesNuevos),
                    nuevoMonto.intValue()
            );

        } catch (Exception e) {
            log.error("[AdicionalCommand] Error guardando adicionales: {}", e.getMessage(), e);
            state.reset();
            return "❌ Error al guardar los adicionales. Intentá nuevamente.";
        }
    }
}
