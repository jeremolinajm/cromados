package com.cromados.barberia.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TurnoRequest {
    @NotNull private Long sucursalId;
    @NotNull private Long barberoId;
    @NotNull private Long tipoCorteId;
    @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") @NotBlank private String fecha; // YYYY-MM-DD
    @Pattern(regexp="\\d{2}:\\d{2}") @NotBlank private String hora;         // HH:mm
    @NotBlank private String clienteNombre;
    @NotBlank private String clienteTelefono;
    @NotNull  private Integer clienteEdad;    // NUEVO


}


