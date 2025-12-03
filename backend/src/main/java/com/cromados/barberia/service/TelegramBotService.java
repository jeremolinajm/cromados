// src/main/java/com/cromados/barberia/service/TelegramBotService.java
package com.cromados.barberia.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.service.telegram.CommandRegistry;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramSessionManager;
import com.cromados.barberia.service.telegram.handlers.CommandHandler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * TelegramBotService - Infraestructura del bot de Telegram.
 *
 * Responsabilidades:
 * - Recibir y procesar actualizaciones de Telegram
 * - Delegar comandos y callbacks a los handlers específicos
 * - Gestionar sesiones mediante TelegramSessionManager
 * - Enviar mensajes y notificaciones
 *
 * ⚠️ IMPORTANTE: Este servicio NO debe contener lógica de negocio de comandos.
 * Toda la lógica está en handlers individuales en el paquete telegram.handlers.
 */
@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    @Value("${telegram.admin.chatId:}")
    private String adminChatId;

    private final BarberoRepository barberoRepo;
    private final TelegramSessionManager sessionManager;
    private final CommandRegistry commandRegistry;

    public TelegramBotService(
            BarberoRepository barberoRepo,
            TelegramSessionManager sessionManager,
            @Lazy CommandRegistry commandRegistry
    ) {
        this.barberoRepo = barberoRepo;
        this.sessionManager = sessionManager;
        this.commandRegistry = commandRegistry;
    }

    @PostConstruct
    public void init() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("[Telegram] Token no configurado. Bot deshabilitado.");
            return;
        }
        log.info("[Telegram] Bot inicializado: {}", botUsername);
    }

    @Override
    public String getBotUsername() {
        return botUsername != null ? botUsername : "CromadosBot";
    }

    @Override
    public String getBotToken() {
        return botToken != null ? botToken : "";
    }

    /**
     * Procesa todas las actualizaciones recibidas de Telegram.
     */
    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Manejar callback queries (botones inline)
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
                return;
            }

            // Manejar mensajes de texto normales
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            handleTextMessage(update);

        } catch (Exception e) {
            log.error("[Telegram] Error procesando update: {}", e.getMessage(), e);
            Long chatId = extractChatId(update);
            if (chatId != null) {
                sendText(chatId, "❌ Error procesando mensaje. Intentá de nuevo.");
            }
        }
    }

    /**
     * Maneja callbacks de botones inline.
     */
    private void handleCallbackQuery(Update update) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackData = update.getCallbackQuery().getData();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        log.info("[Telegram] CallbackQuery de chatId={}: {}", chatId, callbackData);

        String response = processCallback(chatId, callbackData, messageId);

        // Si processCallback retorna null, significa que ya envió un mensaje con botones
        if (response == null) {
            // Editar el mensaje anterior para quitar los botones
            try {
                EditMessageText editMsg = new EditMessageText();
                editMsg.setChatId(chatId.toString());
                editMsg.setMessageId(messageId);
                editMsg.setText("✅ Selección confirmada");
                execute(editMsg);
            } catch (TelegramApiException e) {
                log.warn("[Telegram] No se pudo editar mensaje: {}", e.getMessage());
            }
            return;
        }

        // Si hay respuesta de texto, editar el mensaje anterior
        EditMessageText editMsg = new EditMessageText();
        editMsg.setChatId(chatId.toString());
        editMsg.setMessageId(messageId);
        editMsg.setText(response);

        try {
            execute(editMsg);
        } catch (TelegramApiException e) {
            // Si falla editar, enviar nuevo mensaje
            sendText(chatId, response);
        }
    }

    /**
     * Maneja mensajes de texto normales.
     */
    private void handleTextMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        log.info("[Telegram] Mensaje de chatId={}: {}", chatId, text);

        String response = processCommand(chatId, text);

        // Solo enviar si la respuesta no es null (cuando se envían botones inline, retornamos null)
        if (response != null && !response.isBlank()) {
            sendText(chatId, response);
        }
    }

    /**
     * Procesa comandos y texto del usuario.
     * Delega toda la lógica a los handlers correspondientes.
     */
    private String processCommand(Long chatId, String text) {
        // Comando /vincular es especial: no requiere barbero autorizado
        if (text.equalsIgnoreCase("/vincular")) {
            CommandHandler vincularHandler = commandRegistry.getHandler("vincular");
            if (vincularHandler != null) {
                SessionState state = sessionManager.getOrCreateSession(chatId);
                state.touch();
                return vincularHandler.handleCommand(chatId, state);
            }
            // Fallback si no hay handler
            return String.format("""
                    🔗 Tu Chat ID es: `%d`

                    📝 Proporcioná este número a un administrador para que te vincule.
                    """, chatId);
        }

        // Verificar autorización para el resto de comandos
        Barbero barbero = findBarberoByChat(chatId);
        if (barbero == null) {
            return """
                    👋 Hola! Para usar este bot necesitas ser un barbero autorizado.

                    📝 Usa el comando /vincular para obtener tu Chat ID y proporciónaselo a un administrador.

                    El administrador podrá vincularte desde el panel web en la sección de barberos.
                    """;
        }

        // Obtener o crear sesión
        SessionState state = sessionManager.getOrCreateSession(chatId);
        state.setBarbero(barbero);
        state.touch();

        log.info("[Telegram] Usuario autorizado: {} (chatId={})", barbero.getNombre(), chatId);

        // ===== SISTEMA DE HANDLERS =====

        // Si hay un step activo, verificar si hay un handler que puede manejarlo
        if (state.getStep() != null && !state.getStep().equals("IDLE")) {
            CommandHandler handlerForStep = commandRegistry.getHandlerForStep(state.getStep());
            if (handlerForStep != null) {
                log.info("[Telegram] Delegando input de texto al handler {} (step: {})",
                        handlerForStep.getClass().getSimpleName(), state.getStep());
                return handlerForStep.handleTextInput(chatId, text, state);
            }
        }

        // Si es un comando (empieza con /), buscar handler correspondiente
        if (text.startsWith("/")) {
            String commandName = text.substring(1).toLowerCase();
            CommandHandler handler = commandRegistry.getHandler(commandName);

            if (handler != null) {
                log.info("[Telegram] Delegando comando /{} al handler {}",
                        commandName, handler.getClass().getSimpleName());
                return handler.handleCommand(chatId, state);
            }

            // Comando no reconocido
            return "❌ Comando no reconocido. Usa /menu para ver los comandos disponibles.";
        }

        // Texto sin comando y sin step activo
        return "ℹ️ Usá /menu para ver los comandos disponibles.";
    }

    /**
     * Procesa callbacks de botones inline.
     * Delega toda la lógica a los handlers correspondientes.
     */
    private String processCallback(Long chatId, String callbackData, Integer messageId) {
        Barbero barbero = findBarberoByChat(chatId);
        if (barbero == null) {
            return "❌ No estás autorizado. Usa /vincular para obtener tu Chat ID.";
        }

        // Obtener o crear sesión
        SessionState state = sessionManager.getOrCreateSession(chatId);
        state.setBarbero(barbero);
        state.touch();
        state.setLastMessageId(messageId); // ✅ Guardar messageId para que handlers puedan editar

        log.info("[Telegram] Processing callback: {} in step: {}", callbackData, state.getStep());

        // ===== SISTEMA DE HANDLERS =====

        // Verificar si hay un handler que puede manejar el step actual
        if (state.getStep() != null && !state.getStep().equals("IDLE")) {
            CommandHandler handlerForStep = commandRegistry.getHandlerForStep(state.getStep());
            if (handlerForStep != null) {
                log.info("[Telegram] Delegando callback al handler {} (step: {})",
                        handlerForStep.getClass().getSimpleName(), state.getStep());
                String result = handlerForStep.handleCallback(chatId, callbackData, state);

                // Si el handler procesó el callback, retornar inmediatamente
                // Incluso si result es null (mensaje ya enviado por sendMessageWithButtons)
                // Esto evita que caiga al fallback y muestre "Acción desconocida"
                return result != null ? result : "";
            }
        }

        // Callback sin handler
        log.warn("[Telegram] Callback no manejado: {} (step: {})", callbackData, state.getStep());
        return "❌ Acción no reconocida. Usa /menu para empezar.";
    }

    /**
     * Busca un barbero por su chatId de Telegram.
     */
    private Barbero findBarberoByChat(Long chatId) {
        return barberoRepo.findByTelegramChatId(chatId).orElse(null);
    }

    /**
     * Envía un mensaje de texto simple.
     * PÚBLICO: usado por otros servicios (PagoService, AdminBarberoController).
     */
    public void sendText(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            log.error("[Telegram] Error enviando mensaje a chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Extrae el chatId de un update (ya sea mensaje o callback).
     */
    private Long extractChatId(Update update) {
        if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        return null;
    }

    // ========== MÉTODOS PÚBLICOS PARA NOTIFICACIONES ==========

    /**
     * Notifica al barbero sobre un nuevo turno creado desde la web.
     * Usado por: PagoService.
     */
    public void notificarNuevoTurno(com.cromados.barberia.model.Turno turno) {
        if (turno.getBarbero() == null || turno.getBarbero().getTelegramChatId() == null) {
            log.warn("[Telegram] No se puede notificar turno sin barbero o sin chatId");
            return;
        }

        Long chatId = turno.getBarbero().getTelegramChatId();
        String mensaje = formatearNotificacionTurno(turno);
        sendText(chatId, mensaje);

        // También notificar al admin si está configurado
        if (adminChatId != null && !adminChatId.isBlank()) {
            try {
                Long adminId = Long.parseLong(adminChatId);
                sendText(adminId, "🔔 Nuevo turno reservado:\n\n" + mensaje);
            } catch (NumberFormatException e) {
                log.warn("[Telegram] adminChatId inválido: {}", adminChatId);
            }
        }
    }

    /**
     * Notifica al barbero sobre múltiples turnos creados (reservas multi-sesión).
     * Usado por: PagoService.
     */
    public void notificarNuevoTurnoGrupo(java.util.List<com.cromados.barberia.model.Turno> turnos) {
        if (turnos == null || turnos.isEmpty()) {
            return;
        }

        com.cromados.barberia.model.Turno primerTurno = turnos.get(0);
        if (primerTurno.getBarbero() == null || primerTurno.getBarbero().getTelegramChatId() == null) {
            log.warn("[Telegram] No se puede notificar turnos sin barbero o sin chatId");
            return;
        }

        Long chatId = primerTurno.getBarbero().getTelegramChatId();
        String mensaje = formatearNotificacionTurnoGrupo(turnos);
        sendText(chatId, mensaje);

        // También notificar al admin
        if (adminChatId != null && !adminChatId.isBlank()) {
            try {
                Long adminId = Long.parseLong(adminChatId);
                sendText(adminId, "🔔 Nuevos turnos reservados:\n\n" + mensaje);
            } catch (NumberFormatException e) {
                log.warn("[Telegram] adminChatId inválido: {}", adminChatId);
            }
        }
    }

    /**
     * Formatea la notificación de un turno individual.
     */
    private String formatearNotificacionTurno(com.cromados.barberia.model.Turno turno) {
        java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append("🆕 *NUEVO TURNO WEB*\n\n");
        sb.append(String.format("📅 %s a las %s\n", turno.getFecha().format(dateFmt), turno.getHora().format(timeFmt)));
        sb.append(String.format("👤 %s\n", turno.getClienteNombre()));
        sb.append(String.format("💇 %s\n", turno.getTipoCorte().getNombre()));

        if (turno.getAdicionales() != null && !turno.getAdicionales().isEmpty()) {
            sb.append(String.format("➕ %s\n", turno.getAdicionales()));
        }

        if (turno.getMontoPagado() != null && turno.getMontoPagado().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("💰 Pagado: $%s\n", turno.getMontoPagado()));
        }

        if (Boolean.TRUE.equals(turno.getSenia())) {
            sb.append("💵 Seña 50% - Falta pagar en efectivo\n");
        }

        sb.append(String.format("🏪 %s", turno.getSucursal().getNombre()));

        return sb.toString();
    }

    /**
     * Formatea la notificación de múltiples turnos (multi-sesión).
     */
    private String formatearNotificacionTurnoGrupo(java.util.List<com.cromados.barberia.model.Turno> turnos) {
        java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        com.cromados.barberia.model.Turno primerTurno = turnos.get(0);

        StringBuilder sb = new StringBuilder();
        sb.append("🆕 *NUEVO PAQUETE DE TURNOS*\n\n");
        sb.append(String.format("👤 %s\n", primerTurno.getClienteNombre()));
        sb.append(String.format("💇 %s\n", primerTurno.getTipoCorte().getNombre()));
        sb.append(String.format("📊 %d sesiones:\n\n", turnos.size()));

        for (int i = 0; i < turnos.size(); i++) {
            com.cromados.barberia.model.Turno t = turnos.get(i);
            sb.append(String.format("  %d. %s a las %s\n",
                    i + 1,
                    t.getFecha().format(dateFmt),
                    t.getHora().format(timeFmt)));
        }

        if (primerTurno.getMontoPagado() != null && primerTurno.getMontoPagado().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("\n💰 Total pagado: $%s\n", primerTurno.getMontoPagado()));
        }

        if (Boolean.TRUE.equals(primerTurno.getSenia())) {
            sb.append("💵 Seña 50% - Falta pagar en efectivo\n");
        }

        sb.append(String.format("🏪 %s", primerTurno.getSucursal().getNombre()));

        return sb.toString();
    }
}
