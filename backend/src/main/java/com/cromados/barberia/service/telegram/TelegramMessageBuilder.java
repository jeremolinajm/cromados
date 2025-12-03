package com.cromados.barberia.service.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructor de mensajes y botones de Telegram.
 */
@Slf4j
@Component
public class TelegramMessageBuilder {

    /**
     * Crea un mensaje de texto simple.
     */
    public SendMessage buildTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        return message;
    }

    /**
     * Crea un mensaje con botones inline.
     */
    public SendMessage buildMessageWithButtons(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = buildTextMessage(chatId, text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Crea un mensaje de edición de texto.
     */
    public EditMessageText buildEditMessage(Long chatId, Integer messageId, String text) {
        EditMessageText editMsg = new EditMessageText();
        editMsg.setChatId(chatId.toString());
        editMsg.setMessageId(messageId);
        editMsg.setText(text);
        return editMsg;
    }

    /**
     * Crea un botón inline.
     */
    public InlineKeyboardButton buildButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Crea un teclado inline a partir de una lista de filas de botones.
     */
    public InlineKeyboardMarkup buildInlineKeyboard(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Crea una fila con un solo botón.
     */
    public List<InlineKeyboardButton> createSingleButtonRow(String text, String callbackData) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(buildButton(text, callbackData));
        return row;
    }

    /**
     * Crea una fila con dos botones.
     */
    public List<InlineKeyboardButton> createDoubleButtonRow(
            String text1, String callback1,
            String text2, String callback2
    ) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(buildButton(text1, callback1));
        row.add(buildButton(text2, callback2));
        return row;
    }

    /**
     * Crea botones de cancelar estándar.
     */
    public List<InlineKeyboardButton> createCancelButton() {
        return createSingleButtonRow("❌ Cancelar", "CANCEL");
    }

    /**
     * Crea botones de confirmación Sí/No.
     */
    public List<List<InlineKeyboardButton>> createConfirmationButtons(String confirmCallback, String cancelCallback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createDoubleButtonRow("✅ Sí", confirmCallback, "❌ No", cancelCallback));
        return rows;
    }
}
