package com.cromados.barberia.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.*;


@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloqueoTurno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private Barbero barbero;
    @NotNull
    private LocalDate fecha;
    @NotNull private LocalTime hora;
}
