package com.cromados.barberia.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Representa un día excepcional de trabajo para un barbero.
 *
 * Casos de uso:
 * - Días festivos donde el barbero trabaja (ej: 24/12, 31/12)
 * - Días especiales fuera del horario regular
 * - Eventos especiales (bodas, ferias, etc.)
 *
 * Prioridad: Los días excepcionales SIEMPRE tienen prioridad sobre los horarios regulares (HorarioBarbero).
 * Si existe un DiaExcepcionalBarbero para una fecha específica, se usan esas franjas horarias
 * en lugar de las franjas del día de la semana regular.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
    name = "dia_excepcional_barbero",
    indexes = {
        @Index(name = "idx_dia_excepcional_barbero_fecha", columnList = "barbero_id,fecha"),
        @Index(name = "idx_dia_excepcional_fecha", columnList = "fecha")
    }
)
public class DiaExcepcionalBarbero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Barbero asociado al día excepcional
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "barbero_id", nullable = false)
    private Barbero barbero;

    /**
     * Fecha específica del día excepcional (ej: 2024-12-24)
     */
    @Column(nullable = false)
    private LocalDate fecha;

    /**
     * Hora de inicio de la franja (formato "HH:mm", ej: "09:00")
     */
    @Column(nullable = false)
    private String inicio;

    /**
     * Hora de fin de la franja (formato "HH:mm", ej: "18:00")
     *
     * IMPORTANTE: La hora de fin PERMITE reserva de turnos.
     * Es decir, si fin="12:00", el slot 12:00 está disponible para reservar.
     */
    @Column(nullable = false)
    private String fin;
}
