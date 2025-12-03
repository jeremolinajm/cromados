package com.cromados.barberia.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pagos")
@Getter @Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "turno_id", nullable = false, unique = true)
    private Turno turno;

    private BigDecimal monto;
    private String moneda;          // "ARS"
    private String mpPreferenceId;
    private String mpPaymentId;
    private String status;          // pending, approved, rejected
    private String initPoint;
    private Instant creadoEn = Instant.now();
    private Boolean senia;
}
