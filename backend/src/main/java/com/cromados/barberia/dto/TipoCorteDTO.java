package com.cromados.barberia.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TipoCorteDTO {
    private Long id;

    @NotBlank
    private String nombre;

    @NotNull @Min(1)
    private Integer precio;

    @NotNull @Min(1)
    private Integer duracionMin;

    private String descripcion;

    @NotNull @Min(1)
    private Integer sesiones;

    @NotNull
    private Boolean adicional; // Indica si es un servicio adicional (ej: "Lavado")

    private Boolean activo; // Indica si el servicio está activo y visible para clientes

    /**
     * IDs de los barberos que pueden ofrecer este servicio.
     * Si es null o vacío, significa que todos los barberos pueden ofrecer el servicio.
     */
    private List<Long> barberosHabilitadosIds;
}
