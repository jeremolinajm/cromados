package com.cromados.barberia.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(indexes = @Index(name="idx_horario_barbero_dia", columnList="barbero_id,diaSemana"))
public class HorarioBarbero {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private Barbero barbero;
    @Column(nullable=false) private int diaSemana; // 1=Lunes ... 7=Domingo
    @Column(nullable=false) private String inicio; // "09:00"
    @Column(nullable=false) private String fin;    // "18:00"
}
