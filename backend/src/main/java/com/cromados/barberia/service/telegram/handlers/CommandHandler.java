package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.service.telegram.SessionState;

/**
 * Interfaz base para handlers de comandos de Telegram.
 */
public interface CommandHandler {

    /**
     * Obtiene el nombre del comando (sin la barra /).
     */
    String getCommandName();

    /**
     * Maneja el inicio del comando.
     *
     * @param chatId ID del chat
     * @param state Estado de sesión
     * @return Mensaje de respuesta o null si se envió un mensaje con botones
     */
    String handleCommand(Long chatId, SessionState state);

    /**
     * Maneja el input de texto en el flujo del comando.
     *
     * @param chatId ID del chat
     * @param text Texto ingresado por el usuario
     * @param state Estado de sesión
     * @return Mensaje de respuesta o null si se envió un mensaje con botones
     */
    String handleTextInput(Long chatId, String text, SessionState state);

    /**
     * Maneja callbacks de botones inline.
     *
     * @param chatId ID del chat
     * @param callbackData Datos del callback (formato: ACTION_VALUE)
     * @param state Estado de sesión
     * @return Mensaje de respuesta o null si se envió un mensaje con botones
     */
    String handleCallback(Long chatId, String callbackData, SessionState state);

    /**
     * Verifica si este handler puede manejar el estado actual.
     *
     * @param step Paso actual del estado
     * @return true si puede manejar este paso
     */
    boolean canHandle(String step);
}
