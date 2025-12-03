package com.cromados.barberia.service.telegram.handlers;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.HorarioBarbero;
import com.cromados.barberia.repository.*;
import com.cromados.barberia.service.HorarioService;
import com.cromados.barberia.service.telegram.SessionState;
import com.cromados.barberia.service.telegram.TelegramMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handler para el comando /disponibilidad.
 *
 * Permite consultar la disponibilidad de horarios para un mes específico.
 * Flujo:
 * 1. Solicitar mes (MM/YYYY o "actual")
 * 2. Mostrar disponibilidad día por día
 */
@Slf4j
@Component
public class DisponibilidadCommandHandler extends BaseCommandHandler {

    private static final String STEP_MONTH = "WAITING_MONTH_DISP";

    public DisponibilidadCommandHandler(
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
        return "disponibilidad";
    }

    @Override
    public boolean canHandle(String step) {
        return STEP_MONTH.equals(step);
    }

    @Override
    public String handleCommand(Long chatId, SessionState state) {
        state.reset();
        state.setStep(STEP_MONTH);
        return showMonthSelection(chatId);
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

        if ("MONTHD".equals(action)) {
            return handleMonthSelection(value, state);
        }

        return "❌ Acción no reconocida";
    }

    @Override
    public String handleTextInput(Long chatId, String text, SessionState state) {
        return "❌ Por favor usa los botones para navegar";
    }

    /**
     * Muestra botones para seleccionar mes.
     */
    private String showMonthSelection(Long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        YearMonth actual = YearMonth.now();

        // Mes actual
        rows.add(messageBuilder.createSingleButtonRow(
                "📅 " + formatYearMonth(actual) + " (Mes actual)",
                "MONTHD_ACTUAL"
        ));

        // Próximos 3 meses
        for (int i = 1; i <= 3; i++) {
            YearMonth mes = actual.plusMonths(i);
            rows.add(messageBuilder.createSingleButtonRow(
                    "📅 " + formatYearMonth(mes),
                    "MONTHD_" + mes.toString()
            ));
        }

        // Cancelar
        rows.add(messageBuilder.createCancelButton());

        InlineKeyboardMarkup keyboard = messageBuilder.buildInlineKeyboard(rows);
        return sendMessageWithButtons(chatId, "🗓️ Seleccioná el mes para consultar disponibilidad:", keyboard);
    }

    /**
     * Maneja la selección de mes.
     */
    private String handleMonthSelection(String value, SessionState state) {
        YearMonth mes;

        if ("ACTUAL".equals(value)) {
            mes = YearMonth.now();
        } else {
            try {
                mes = YearMonth.parse(value);
            } catch (DateTimeParseException e) {
                return "❌ Formato de mes inválido";
            }
        }

        state.reset();
        Barbero barbero = getBarbero(state);
        String resultado = mostrarDisponibilidadMes(barbero, mes);

        // Notificar al admin sobre la consulta
        String mesFormateado = formatYearMonth(mes);
        notificarConsulta("disponibilidad", barbero,
                String.format("Consultó disponibilidad para %s", mesFormateado));

        return resultado;
    }

    /**
     * Muestra la disponibilidad día por día para un mes completo.
     */
    private String mostrarDisponibilidadMes(Barbero barbero, YearMonth mes) {
        LocalDate inicio = mes.atDay(1);
        LocalDate fin = mes.atEndOfMonth();
        LocalDate hoy = LocalDate.now();

        StringBuilder sb = new StringBuilder(String.format("📅 Disponibilidad - %s\n\n",
                mes.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "AR")))));

        LocalDate fecha = inicio;
        int diasConHorarios = 0;

        while (!fecha.isAfter(fin) && diasConHorarios < 31) {
            // Omitir fechas pasadas
            if (fecha.isBefore(hoy)) {
                fecha = fecha.plusDays(1);
                continue;
            }

            int diaSemana = fecha.getDayOfWeek().getValue();
            List<HorarioBarbero> horarios = horarioRepo.findByBarbero_IdAndDiaSemana(barbero.getId(), diaSemana);

            if (!horarios.isEmpty()) {
                // ✅ USAR HorarioService como única fuente de verdad
                List<LocalTime> disponibles = horarioService.horariosDisponibles(barbero.getId(), fecha);

                String dia = fecha.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.SHORT,
                        new Locale("es", "AR")
                );

                sb.append(String.format("%s %s\n",
                        fecha.format(DATE_FMT),
                        dia));

                if (disponibles.isEmpty()) {
                    sb.append("🔴 Completo\n\n");
                } else {
                    // Mostrar horarios disponibles (separados por coma, salto de línea cada 6)
                    StringBuilder horariosStr = new StringBuilder();
                    for (int i = 0; i < disponibles.size(); i++) {
                        if (i > 0) horariosStr.append(", ");
                        // Salto de línea cada 6 horarios para mejor lectura
                        if (i > 0 && i % 6 == 0) horariosStr.append("\n");
                        horariosStr.append(disponibles.get(i).format(TIME_FMT));
                    }
                    sb.append(horariosStr.toString()).append("\n\n");
                }

                diasConHorarios++;
            }

            fecha = fecha.plusDays(1);
        }

        if (diasConHorarios == 0) {
            return String.format("❌ No tenés días laborales configurados en %s.",
                    mes.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "AR"))));
        }

        return sb.toString();
    }

    /**
     * Formatea un YearMonth para mostrar (ejemplo: "Diciembre 2024").
     */
    private String formatYearMonth(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
    }
}
