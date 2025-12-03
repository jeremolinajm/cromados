package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

/**
 * Handler para el comando /vincular.
 *
 * Muestra el Chat ID del usuario para que pueda ser vinculado por un administrador.
 * Este comando funciona incluso para usuarios NO autorizados.
 */
@Slf4j
@Component
public class VincularCommandHandler extends BaseCommandHandler {

    public VincularCommandHandler(
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
        return "vincular";
    }

    @Override
    public boolean canHandle(String step) {
        // Este comando no tiene flujo con estado
        return false;
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        // Este comando NO requiere barbero autorizado

        // Notificar al admin sobre el intento de vinculación
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        notificarAdmin(String.format("""
                🔗 *SOLICITUD DE VINCULACIÓN*

                ⏰ %s
                💬 Chat ID: `%d`
                📱 Comando: /vincular

                Un usuario solicitó su Chat ID para ser vinculado.
                """, timestamp, chatId));

        return String.format("""
                🔗 Tu Chat ID es: `%d`

                📋 Este ID debe ser configurado por un administrador en el panel web.

                Proporcioná este número al administrador para que te vincule a tu perfil de barbero.
                """, chatId);
    }

    @Override
    public String handleCallback(Long chatId, String callbackData, SessionState state) {
        // Este comando no usa callbacks
        return "❌ Acción no soportada";
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        // Este comando no espera input de texto
        return "❌ Entrada no esperada";
    }

    /**
     * NOTA IMPORTANTE: Este handler NO valida si el barbero está autorizado.
     * Es un comando público que permite a cualquier usuario obtener su Chat ID.
     */
    @Override
    protected boolean validateBarbero(SessionState state) {
        // Sobrescribir validación - este comando es público
        return true;
    }
}
