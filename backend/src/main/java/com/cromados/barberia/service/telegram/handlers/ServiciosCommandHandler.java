package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.TipoCorte;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.util.List;

/**
 * Handler para el comando /servicios.
 *
 * Muestra la lista de servicios disponibles (principales y adicionales).
 */
@Slf4j
@Component
public class ServiciosCommandHandler extends BaseCommandHandler {

    public ServiciosCommandHandler(
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
        return "servicios";
    }

    @Override
    public boolean canHandle(String step) {
        // Este comando no tiene flujo con estado
        return false;
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset(); // Limpiar cualquier flujo previo

        Barbero barbero = getBarbero(state);
        String resultado = listarServicios();

        // Notificar al admin sobre la consulta
        notificarConsulta("servicios", barbero, "Consultó lista de servicios disponibles");

        return resultado;
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
     * Lista todos los servicios disponibles (solo activos), separados en principales y adicionales.
     */
    private String listarServicios() {
        List<TipoCorte> servicios = tipoCorteRepo.findByActivoTrue();
        if (servicios.isEmpty()) {
            return "❌ No hay servicios configurados.";
        }

        // Separar servicios normales de adicionales
        List<TipoCorte> normales = servicios.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getAdicional()))
                .toList();
        List<TipoCorte> adicionales = servicios.stream()
                .filter(s -> Boolean.TRUE.equals(s.getAdicional()))
                .toList();

        StringBuilder sb = new StringBuilder();

        // Servicios normales
        if (!normales.isEmpty()) {
            sb.append("💼 Servicios Principales\n\n");
            for (TipoCorte s : normales) {
                sb.append(String.format("""
                        🆔 ID: %d
                        ✂️ %s
                        💵 $%d
                        ⏱ %d min
                        📝 %s

                        """,
                        s.getId(),
                        s.getNombre(),
                        s.getPrecio(),
                        s.getDuracionMin(),
                        s.getDescripcion() != null ? s.getDescripcion() : "Sin descripción"
                ));
            }
        }

        // Servicios adicionales
        if (!adicionales.isEmpty()) {
            sb.append("\n➕ Servicios Adicionales\n\n");
            for (TipoCorte s : adicionales) {
                sb.append(String.format("""
                        🆔 ID: %d
                        ✂️ %s
                        💵 $%d
                        ⏱ %d min
                        📝 %s

                        """,
                        s.getId(),
                        s.getNombre(),
                        s.getPrecio(),
                        s.getDuracionMin(),
                        s.getDescripcion() != null ? s.getDescripcion() : "Sin descripción"
                ));
            }
        }

        return sb.toString();
    }
}
