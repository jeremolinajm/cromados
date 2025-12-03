package com.cromados.barberia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * DTO que representa una sesión individual dentro de una reserva multi-sesión.
 */
@Data
public class SesionRequest {
    @NotBlank
    private String fecha;

    @NotBlank
    private String hora;

    /**
     * IDs de servicios adicionales para esta sesión específica.
     * Puede ser null o vacío si no hay adicionales para esta sesión.
     */
    private List<Long> adicionalesIds;
}
