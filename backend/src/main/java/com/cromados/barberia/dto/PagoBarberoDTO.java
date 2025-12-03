package com.cromados.barberia.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PagoBarberoDTO {
    private Long barberoId;
    private String barberoNombre;
    private Integer cantidadTurnos;

    // MONTOS BRUTOS (informativos - cómo pagaron los clientes)
    private BigDecimal montoAppBruto;           // Total que entró por Mercado Pago
    private BigDecimal montoTransferenciaBruto; // Total que entró por transferencia al alias del negocio
    private BigDecimal montoEfectivoBruto;      // Total que entró en efectivo a caja
    private BigDecimal totalBruto;              // Suma de todos los montos brutos

    // CÁLCULO PARA EL BARBERO
    private BigDecimal comision50;              // 50% del total bruto (lo que le corresponde)
    private Integer cantidadBonos;              // Cantidad de bonos ganados (1 por cada 10 turnos/día)
    private BigDecimal montoBonus;              // Monto del bonus (cantidadBonos * 50% precio id=1)
    private BigDecimal totalAPagar;             // comision50 + montoBonus

    private List<DetalleServicioDTO> detalleServicios;
    private Map<String, Integer> bonosPorDia;   // Detalle de bonos por día (para mostrar en UI)

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DetalleServicioDTO {
        private String servicioNombre;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
    }
}
