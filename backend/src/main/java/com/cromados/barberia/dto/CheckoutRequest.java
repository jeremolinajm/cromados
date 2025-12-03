package com.cromados.barberia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {
    private Long turnoId;

    @NotNull
    private Long tipoCorteId;

    @NotNull
    private Long sucursalId;

    @NotNull
    private Long barberoId;

    // ========== OPCIÓN 1: Sesión única (campos legacy) ==========
    // Estos campos son opcionales ahora, ya que pueden venir en el array 'sesiones'
    private String fecha;
    private String hora;
    private List<Long> adicionalesIds; // Adicionales para sesión única (legacy)

    // ========== OPCIÓN 2: Múltiples sesiones (nuevo formato) ==========
    /**
     * Array de sesiones, cada una con su propia fecha, hora y adicionales.
     * Si este campo está presente, tiene prioridad sobre fecha/hora/adicionalesIds individuales.
     */
    private List<SesionRequest> sesiones;

    // ========== DEPRECATED: Legacy multi-sesión ==========
    /**
     * @deprecated Usar 'sesiones' en su lugar. Este campo se mantiene por compatibilidad.
     */
    @Deprecated
    private List<String> horarios;

    // ========== Datos del cliente ==========
    @NotBlank
    private String clienteNombre;

    @NotBlank
    private String clienteTelefono;

    @NotNull
    private Integer clienteEdad;

    @JsonProperty("montoTotal")
    private BigDecimal montoTotal;

    private Boolean senia;
}
