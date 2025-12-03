package com.cromados.barberia.service.telegram;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.TipoCorte;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Estado de sesión para conversaciones de Telegram.
 * Cada chat tiene su propia instancia de SessionState.
 */
@Data
@FieldDefaults(level = AccessLevel.PUBLIC) // Campos públicos para compatibilidad con código legacy
public class SessionState {
    String step = "IDLE";

    // Datos temporales del turno
    LocalDate tempFecha;
    LocalTime tempHora;
    Long tempServicioId;
    TipoCorte tempServicio;
    String tempClienteNombre;
    String tempClienteTelefono;
    Integer tempClienteEdad;
    String tempMedioPago; // "EFECTIVO" o "TRANSFERENCIA"
    List<LocalTime> horariosDisponibles;

    // Para /descanso (bloqueo de rango horario)
    LocalTime tempHoraDesde;
    LocalTime tempHoraHasta;

    // Para adicionales en /bloquear
    List<Long> tempAdicionalesIds;

    // Para /fijos (turnos recurrentes)
    DayOfWeek tempDayOfWeek;
    Integer tempRepetitions;
    List<LocalDate> tempFechasFijos;
    List<LocalDate> tempConflictDates; // Fechas con conflictos en /fijos

    // Para /turnos (nuevo)
    YearMonth tempYearMonth;

    // Para /mover (nuevo)
    Long tempTurnoIdToMove;

    // Barbero vinculado
    Barbero barbero;

    // Control de tiempo
    Instant lastActivity = Instant.now();

    // ID del último mensaje con botones (para editar en lugar de enviar nuevo)
    Integer lastMessageId;

    /**
     * Resetea el estado a valores iniciales.
     */
    public void reset() {
        step = "IDLE";
        tempFecha = null;
        tempHora = null;
        tempServicioId = null;
        tempServicio = null;
        tempClienteNombre = null;
        tempClienteTelefono = null;
        tempClienteEdad = null;
        tempMedioPago = null;
        horariosDisponibles = null;
        tempHoraDesde = null;
        tempHoraHasta = null;
        tempAdicionalesIds = null;
        tempDayOfWeek = null;
        tempRepetitions = null;
        tempFechasFijos = null;
        tempConflictDates = null;
        tempYearMonth = null;
        tempTurnoIdToMove = null;
    }

    /**
     * Actualiza el timestamp de última actividad.
     */
    public void touch() {
        this.lastActivity = Instant.now();
    }
}
