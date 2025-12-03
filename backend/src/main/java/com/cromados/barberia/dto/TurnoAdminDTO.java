package com.cromados.barberia.dto;
import lombok.*;
import java.math.BigDecimal;

@Getter@Setter
public class TurnoAdminDTO {
    private Long id;
    private String clienteNombre;
    private String clienteEmail;
    private String clienteTelefono;
    private Long sucursalId;
    private Long barberoId;
    private Long tipoCorteId;
    private String fecha; // yyyy-MM-dd
    private String hora;  // HH:mm
    private String estado;
    private Boolean pagoConfirmado;
    private BigDecimal montoPagado;
    private Boolean senia;
    private BigDecimal montoEfectivo;
    private String adicionales; // Servicios adicionales (comma-separated)
}