// src/main/java/com/cromados/barberia/model/Turno.java
package com.cromados.barberia.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Turno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String clienteNombre;

    private String clienteDni;

    @Email
    private String clienteEmail;

    @NotBlank
    private String clienteTelefono;

    @NotNull
    private Integer clienteEdad;

    @ManyToOne(optional = false) private Sucursal sucursal;
    @ManyToOne(optional = false) private Barbero barbero;
    @ManyToOne(optional = false) private TipoCorte tipoCorte;

    @NotNull private LocalDate fecha;
    @NotNull private LocalTime hora;

    @NotBlank
    private String estado; // PENDIENTE_PAGO, RESERVADO, CANCELADO, BLOQUEADO, etc.

    @Column
    private Boolean pagoConfirmado;

    // 🆕 Nuevos campos para manejar pagos y señas

    @Column(precision = 12, scale = 2)
    private BigDecimal montoPagado; // Monto que efectivamente pagó (puede ser 50% si es seña)

    @Column
    private Boolean senia; // true si pagó solo el 50%, false si pagó el 100%

    @Column(precision = 12, scale = 2)
    private BigDecimal montoEfectivo; // Lo que debe pagar en efectivo al barbero (si senia=true)

    @Column
    private Boolean recordatorioEnviado; // true si ya se envió el recordatorio por WhatsApp

    @Column
    private String grupoId; // UUID para agrupar turnos de servicios multi-sesión (null si es sesión única)

    @Column(columnDefinition = "TEXT")
    private String adicionales; // Nombres de servicios adicionales separados por comas (ej: "Lavado,Barba")

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // Timestamp de cuándo se creó el turno
}