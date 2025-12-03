package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.format.DateTimeFormatter;

/**
 * Clase base para command handlers con utilidades comunes.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseCommandHandler implements CommandHandler {

    protected final TurnoRepository turnoRepo;
    protected final BarberoRepository barberoRepo;
    protected final TipoCorteRepository tipoCorteRepo;
    protected final SucursalRepository sucursalRepo;
    protected final HorarioBarberoRepository horarioRepo;
    protected final TelegramMessageBuilder messageBuilder;
    protected final TelegramLongPollingBot bot;
    protected final HorarioService horarioService;  // ✅ ÚNICA FUENTE DE VERDAD para disponibilidad

    @Value("${telegram.admin.chatId:}")
    protected String adminChatId;

    // Formatters comunes
    protected static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    protected static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    protected static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM/yyyy");

    /**
     * Envía un mensaje de texto simple.
     */
    protected void sendText(Long chatId, String text) {
        try {
            SendMessage message = messageBuilder.buildTextMessage(chatId, text);
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("[Telegram] Error enviando mensaje a chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Envía un mensaje con botones inline.
     * IMPORTANTE: Retorna null para indicar que el mensaje ya fue enviado.
     */
    protected String sendMessageWithButtons(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = messageBuilder.buildMessageWithButtons(chatId, text, keyboard);
            bot.execute(message);
            return null; // Señal de que ya se envió el mensaje
        } catch (TelegramApiException e) {
            log.error("[Telegram] Error enviando mensaje con botones a chatId={}: {}", chatId, e.getMessage());
            return "❌ Error enviando mensaje. Intentá de nuevo.";
        }
    }

    /**
     * Edita un mensaje existente con nuevo texto y botones.
     * Usa el messageId guardado en el estado de sesión.
     * Si el messageId no está disponible, envía un nuevo mensaje.
     * IMPORTANTE: Retorna null para indicar que el mensaje ya fue editado/enviado.
     */
    protected String editMessageWithButtons(Long chatId, String text, InlineKeyboardMarkup keyboard, SessionState state) {
        Integer messageId = state.getLastMessageId();

        if (messageId == null) {
            // Si no hay messageId, enviar nuevo mensaje
            log.debug("[Telegram] No hay messageId, enviando nuevo mensaje");
            return sendMessageWithButtons(chatId, text, keyboard);
        }

        try {
            org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editMsg =
                new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText();
            editMsg.setChatId(chatId.toString());
            editMsg.setMessageId(messageId);
            editMsg.setText(text);
            editMsg.setReplyMarkup(keyboard);
            bot.execute(editMsg);
            return null; // Señal de que ya se editó el mensaje
        } catch (TelegramApiException e) {
            log.warn("[Telegram] Error editando mensaje chatId={}, messageId={}: {}. Enviando nuevo mensaje.",
                     chatId, messageId, e.getMessage());
            // Si falla editar, enviar nuevo mensaje
            return sendMessageWithButtons(chatId, text, keyboard);
        }
    }

    /**
     * Notifica al admin con un mensaje personalizado.
     */
    protected void notificarAdmin(String mensaje) {
        if (adminChatId == null || adminChatId.isBlank()) {
            log.warn("[Telegram] adminChatId no configurado, no se puede notificar");
            return;
        }

        try {
            Long adminId = Long.parseLong(adminChatId);
            sendText(adminId, mensaje);
        } catch (NumberFormatException e) {
            log.error("[Telegram] adminChatId inválido: {}", adminChatId);
        }
    }

    /**
     * Notifica al admin sobre una acción realizada por un barbero.
     * Formato estructurado para facilitar seguimiento.
     */
    protected void notificarAccionBarbero(String accion, Barbero barbero, String detalles) {
        if (adminChatId == null || adminChatId.isBlank()) {
            return;
        }

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));

        String mensaje = String.format("""
                📋 *ACCIÓN DE BARBERO*

                ⏰ %s
                👤 %s
                🔧 %s

                %s
                """,
                timestamp,
                barbero.getNombre(),
                accion,
                detalles
        );

        notificarAdmin(mensaje);
    }

    /**
     * Notifica al admin sobre una consulta realizada por un barbero.
     */
    protected void notificarConsulta(String comando, Barbero barbero, String resultado) {
        if (adminChatId == null || adminChatId.isBlank()) {
            return;
        }

        // Usar zona horaria de Argentina
        String timestamp = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));

        String mensaje = String.format("""
                📊 CONSULTA

                ⏰ %s
                👤 %s
                📱 /%s

                %s
                """,
                timestamp,
                barbero.getNombre(),
                comando,
                resultado
        );

        notificarAdmin(mensaje);
    }

    /**
     * Valida que el barbero esté configurado en la sesión.
     */
    protected boolean validateBarbero(SessionState state) {
        return state.getBarbero() != null && state.getBarbero().getSucursal() != null;
    }

    /**
     * Obtiene el barbero de la sesión o lanza error.
     */
    protected Barbero getBarbero(SessionState state) {
        if (!validateBarbero(state)) {
            throw new IllegalStateException("Barbero no configurado en sesión");
        }
        return state.getBarbero();
    }

    @Override
    public String handleCallback(Long chatId, String callbackData, SessionState state) {
        // Por defecto, los callbacks se ignoran
        return "❌ Callback no soportado para este comando";
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        // Por defecto, no se maneja texto
        return "❌ Este comando no acepta entrada de texto en este paso";
    }
}
