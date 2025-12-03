package com.cromados.barberia.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Barbero {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre;

    @ManyToOne(optional = false)
    private Sucursal sucursal;

    @JsonIgnore
    private String fotoUrl;

    private String instagram;
    private String facebook;

    private String telefono; // E.164 o local

    @Column(unique = true)
    private Long telegramChatId;

}
