package com.cromados.barberia.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tipos_corte")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TipoCorte {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length = 120)
    private String nombre;

    @Column(nullable=false)
    private Integer precio;        // ARS

    @Column(name="duracion_min", nullable=false)
    private Integer duracionMin;   // minutos

    @Column(length = 500)
    private String descripcion;

    @Column(nullable=false)
    private Integer sesiones = 1;  // Cantidad de sesiones que "vale" el servicio (default 1)

    /**
     * Indica si este servicio es un adicional que se puede agregar a otro servicio principal
     * (ej: "Lavado" puede ser adicional a "Corte Clásico")
     */
    @Column(nullable = false)
    private Boolean adicional = false;

    /**
     * Indica si el servicio está activo y visible para los clientes.
     * Si es false, el servicio no aparecerá en la lista de servicios disponibles,
     * pero se mantiene en la base de datos para preservar el historial de turnos.
     */
    @Column(nullable = false)
    private Boolean activo = true;

    /**
     * Barberos que están habilitados para ofrecer este servicio.
     * Si está vacío, significa que todos los barberos pueden ofrecer el servicio.
     * Si tiene elementos, solo esos barberos específicos pueden ofrecerlo.
     */
    @ManyToMany
    @JoinTable(
        name = "servicio_barbero",
        joinColumns = @JoinColumn(name = "servicio_id"),
        inverseJoinColumns = @JoinColumn(name = "barbero_id")
    )
    private Set<Barbero> barberosHabilitados = new HashSet<>();
}
