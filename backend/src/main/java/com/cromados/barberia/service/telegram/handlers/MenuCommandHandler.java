package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

/**
 * Handler para el comando /menu.
 *
 * Muestra el menú principal con todos los comandos disponibles.
 */
@Slf4j
@Component
public class MenuCommandHandler extends BaseCommandHandler {

    public MenuCommandHandler(
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
        return "menu";
    }

    @Override
    public boolean canHandle(String step) {
        // Este comando no tiene flujo con estado
        return false;
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        Barbero barbero = getBarbero(state);
        return buildMenu(barbero);
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
     * Construye el menú principal con todos los comandos disponibles.
     */
    private String buildMenu(Barbero barbero) {
        return String.format("""
                🏠 Menú Principal - %s

                📋 Comandos disponibles:

                /turnos - Ver próximos turnos
                /disponibilidad - Consultar horarios libres
                /servicios - Ver servicios y precios
                /agendar - Agendar turno presencial
                /adicional - Agregar servicios adicionales a un turno
                /fijos - Crear turnos recurrentes
                /mover - Mover un turno a otra fecha/hora
                /descanso - Bloquear horarios (descanso/almuerzo)
                /desbloquear - Liberar turno bloqueado
                /menu - Mostrar este menú

                💡 Escribe el comando que necesites.
                """, barbero.getNombre());
    }
}
