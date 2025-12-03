package com.cromados.barberia.service.telegram;

import com.cromados.barberia.service.telegram.handlers.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro centralizado de command handlers.
 */
@Slf4j
@Component
public class CommandRegistry {

    private final Map<String, CommandHandler> handlers = new HashMap<>();

    /**
     * Constructor que recibe todos los handlers disponibles.
     */
    public CommandRegistry(List<CommandHandler> commandHandlers) {
        for (CommandHandler handler : commandHandlers) {
            String commandName = handler.getCommandName();
            handlers.put(commandName.toLowerCase(), handler);
            log.info("[Telegram] Registrado handler para comando: /{}", commandName);
        }
    }

    /**
     * Obtiene el handler para un comando específico.
     *
     * @param command Nombre del comando (sin la barra /)
     * @return Handler o null si no existe
     */
    public CommandHandler getHandler(String command) {
        return handlers.get(command.toLowerCase());
    }

    /**
     * Obtiene el handler que puede manejar un step específico.
     *
     * @param step Step actual de la sesión
     * @return Handler o null si ninguno puede manejar
     */
    public CommandHandler getHandlerForStep(String step) {
        return handlers.values().stream()
                .filter(h -> h.canHandle(step))
                .findFirst()
                .orElse(null);
    }

    /**
     * Verifica si existe un handler para un comando.
     */
    public boolean hasHandler(String command) {
        return handlers.containsKey(command.toLowerCase());
    }

    /**
     * Obtiene la lista de comandos registrados.
     */
    public List<String> getRegisteredCommands() {
        return handlers.keySet().stream().sorted().toList();
    }
}
