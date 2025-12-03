package com.cromados.barberia.service;

import com.cromados.barberia.dto.PagoBarberoDTO;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.model.TipoCorte;
import com.cromados.barberia.repository.TurnoRepository;
import com.cromados.barberia.repository.TipoCorteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculadoraPagosService {

    private final TurnoRepository turnoRepository;
    private final TipoCorteRepository tipoCorteRepository;

    /**
     * Calcula los pagos a realizar a cada barbero para un rango de fechas.
     * MODELO DEL NEGOCIO:
     * - Todo el dinero (APP/Transferencia/Efectivo) va al negocio, no al barbero
     * - El barbero cobra liquidación semanal: 50% comisión + bonos por volumen
     * - Bonos: cada 10 turnos/día → +50% de 1 servicio id=1
     *
     * @param desde Fecha de inicio (inclusive)
     * @param hasta Fecha de fin (inclusive)
     * @return Lista de pagos por barbero con detalles
     */
    public List<PagoBarberoDTO> calcularPagos(LocalDate desde, LocalDate hasta) {
        log.info("[CalculadoraPagos] Calculando pagos desde {} hasta {}", desde, hasta);

        // Obtener todos los turnos del rango de fechas
        List<Turno> turnos = turnoRepository.findByFechaBetween(desde, hasta);

        // Filtrar solo turnos confirmados
        List<Turno> turnosConfirmados = turnos.stream()
                .filter(this::esTurnoConfirmado)
                .collect(Collectors.toList());

        log.info("[CalculadoraPagos] Total turnos: {}, Confirmados: {}",
                turnos.size(), turnosConfirmados.size());

        // Agrupar por barbero
        Map<Long, List<Turno>> turnosPorBarbero = turnosConfirmados.stream()
                .collect(Collectors.groupingBy(t -> t.getBarbero().getId()));

        // Calcular pagos para cada barbero
        List<PagoBarberoDTO> pagos = new ArrayList<>();

        for (Map.Entry<Long, List<Turno>> entry : turnosPorBarbero.entrySet()) {
            Long barberoId = entry.getKey();
            List<Turno> turnosBarbero = entry.getValue();

            if (turnosBarbero.isEmpty()) continue;

            String barberoNombre = turnosBarbero.get(0).getBarbero().getNombre();

            // ═══════════════════════════════════════════════════════════
            // PASO 1: Calcular MONTOS BRUTOS (informativos)
            // ═══════════════════════════════════════════════════════════
            BigDecimal montoAppBruto = BigDecimal.ZERO;
            BigDecimal montoTransferenciaBruto = BigDecimal.ZERO;
            BigDecimal montoEfectivoBruto = BigDecimal.ZERO;

            for (Turno t : turnosBarbero) {
                BigDecimal montoPagado = t.getMontoPagado() != null ? t.getMontoPagado() : BigDecimal.ZERO;
                BigDecimal montoEfectivo = t.getMontoEfectivo() != null ? t.getMontoEfectivo() : BigDecimal.ZERO;

                if (Boolean.TRUE.equals(t.getPagoConfirmado())) {
                    // Turno pagado por APP (Mercado Pago)
                    montoAppBruto = montoAppBruto.add(montoPagado);
                    montoEfectivoBruto = montoEfectivoBruto.add(montoEfectivo); // Señas: efectivo pendiente
                } else if ("BLOQUEADO".equalsIgnoreCase(t.getEstado())) {
                    // Turno presencial (Telegram)
                    if (montoPagado.compareTo(BigDecimal.ZERO) > 0) {
                        montoTransferenciaBruto = montoTransferenciaBruto.add(montoPagado);
                    }
                    if (montoEfectivo.compareTo(BigDecimal.ZERO) > 0) {
                        montoEfectivoBruto = montoEfectivoBruto.add(montoEfectivo);
                    }
                }
            }

            BigDecimal totalBruto = montoAppBruto.add(montoTransferenciaBruto).add(montoEfectivoBruto);

            // ═══════════════════════════════════════════════════════════
            // PASO 2: Calcular COMISIÓN 50%
            // ═══════════════════════════════════════════════════════════
            BigDecimal comision50 = totalBruto.multiply(new BigDecimal("0.5"));

            // ═══════════════════════════════════════════════════════════
            // PASO 3: Calcular BONOS por volumen diario
            // ═══════════════════════════════════════════════════════════
            Map<String, Integer> bonosPorDia = calcularBonosPorDiaDetallado(turnosBarbero);
            int cantidadBonos = bonosPorDia.values().stream().mapToInt(Integer::intValue).sum();

            BigDecimal montoBonus = BigDecimal.ZERO;
            if (cantidadBonos > 0) {
                BigDecimal precioServicioId1 = obtenerPrecioServicioId1(turnosBarbero);
                if (precioServicioId1.compareTo(BigDecimal.ZERO) > 0) {
                    // Bonus = cantidadBonos * 50% del precio id=1
                    montoBonus = precioServicioId1.multiply(new BigDecimal("0.5"))
                            .multiply(BigDecimal.valueOf(cantidadBonos));
                }
            }

            // ═══════════════════════════════════════════════════════════
            // PASO 4: Total a pagar al barbero
            // ═══════════════════════════════════════════════════════════
            BigDecimal totalAPagar = comision50.add(montoBonus);

            // ═══════════════════════════════════════════════════════════
            // Detalle por servicio (para información adicional)
            // ═══════════════════════════════════════════════════════════
            Map<String, List<Turno>> turnosPorServicio = turnosBarbero.stream()
                    .collect(Collectors.groupingBy(t -> t.getTipoCorte().getNombre()));

            List<PagoBarberoDTO.DetalleServicioDTO> detalles = new ArrayList<>();

            // Agregar servicios principales
            for (Map.Entry<String, List<Turno>> servicioEntry : turnosPorServicio.entrySet()) {
                String servicioNombre = servicioEntry.getKey();
                List<Turno> turnosServicio = servicioEntry.getValue();

                Integer cantidad = turnosServicio.size();
                BigDecimal precioUnitario = BigDecimal.valueOf(
                        turnosServicio.get(0).getTipoCorte().getPrecio()
                );
                BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

                detalles.add(PagoBarberoDTO.DetalleServicioDTO.builder()
                        .servicioNombre(servicioNombre)
                        .cantidad(cantidad)
                        .precioUnitario(precioUnitario)
                        .subtotal(subtotal)
                        .build());
            }

            // Agregar servicios adicionales
            Map<String, Integer> conteoAdicionales = new HashMap<>();
            for (Turno t : turnosBarbero) {
                if (t.getAdicionales() != null && !t.getAdicionales().trim().isEmpty()) {
                    String[] adicionales = t.getAdicionales().split(",");
                    for (String adicional : adicionales) {
                        String nombreAdicional = adicional.trim();
                        if (!nombreAdicional.isEmpty()) {
                            conteoAdicionales.put(nombreAdicional,
                                conteoAdicionales.getOrDefault(nombreAdicional, 0) + 1);
                        }
                    }
                }
            }

            // Buscar precios de adicionales y agregarlos al detalle
            if (!conteoAdicionales.isEmpty()) {
                List<TipoCorte> todosLosServicios = tipoCorteRepository.findAll();
                Map<String, TipoCorte> serviciosPorNombre = todosLosServicios.stream()
                        .collect(Collectors.toMap(
                            TipoCorte::getNombre,
                            s -> s,
                            (existing, replacement) -> existing // En caso de duplicados, mantener el primero
                        ));

                for (Map.Entry<String, Integer> adicionalEntry : conteoAdicionales.entrySet()) {
                    String nombreAdicional = adicionalEntry.getKey();
                    Integer cantidad = adicionalEntry.getValue();

                    TipoCorte servicio = serviciosPorNombre.get(nombreAdicional);
                    if (servicio != null) {
                        BigDecimal precioUnitario = BigDecimal.valueOf(servicio.getPrecio());
                        BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));

                        detalles.add(PagoBarberoDTO.DetalleServicioDTO.builder()
                                .servicioNombre("➕ " + nombreAdicional) // Prefijo para distinguir adicionales
                                .cantidad(cantidad)
                                .precioUnitario(precioUnitario)
                                .subtotal(subtotal)
                                .build());
                    } else {
                        log.warn("[CalculadoraPagos] Servicio adicional '{}' no encontrado en catálogo", nombreAdicional);
                    }
                }
            }

            // Ordenar detalles por subtotal descendente
            detalles.sort((a, b) -> b.getSubtotal().compareTo(a.getSubtotal()));

            pagos.add(PagoBarberoDTO.builder()
                    .barberoId(barberoId)
                    .barberoNombre(barberoNombre)
                    .cantidadTurnos(turnosBarbero.size())
                    .montoAppBruto(montoAppBruto)
                    .montoTransferenciaBruto(montoTransferenciaBruto)
                    .montoEfectivoBruto(montoEfectivoBruto)
                    .totalBruto(totalBruto)
                    .comision50(comision50)
                    .cantidadBonos(cantidadBonos)
                    .montoBonus(montoBonus)
                    .totalAPagar(totalAPagar)
                    .detalleServicios(detalles)
                    .bonosPorDia(bonosPorDia)
                    .build());
        }

        // Ordenar por total a pagar descendente
        pagos.sort((a, b) -> b.getTotalAPagar().compareTo(a.getTotalAPagar()));

        log.info("[CalculadoraPagos] Calculados pagos para {} barberos", pagos.size());

        return pagos;
    }

    /**
     * Calcula los bonos ganados por volumen diario CON DETALLE.
     * Retorna un Map<String, Integer> donde:
     *   key = fecha en formato "dd/MM" (ej: "25/11")
     *   value = cantidad de bonos ese día
     *
     * Por cada 10 turnos en un día, gana 1 bono.
     * Ejemplo:
     *   - Lunes (25/11): 9 turnos = 0 bonos
     *   - Martes (26/11): 12 turnos = 1 bono
     *   - Miércoles (27/11): 21 turnos = 2 bonos
     */
    private Map<String, Integer> calcularBonosPorDiaDetallado(List<Turno> turnos) {
        // Agrupar turnos por fecha
        Map<LocalDate, Long> turnosPorDia = turnos.stream()
                .collect(Collectors.groupingBy(Turno::getFecha, Collectors.counting()));

        // Calcular bonos por día y formatear
        Map<String, Integer> bonosPorDia = new java.util.LinkedHashMap<>();
        for (Map.Entry<LocalDate, Long> entry : turnosPorDia.entrySet()) {
            LocalDate fecha = entry.getKey();
            Long turnosDia = entry.getValue();
            int bonosDia = (int) (turnosDia / 10); // Math.floor implícito con int division

            if (bonosDia > 0) {
                // Formato: "25/11" (día/mes)
                String fechaStr = String.format("%02d/%02d", fecha.getDayOfMonth(), fecha.getMonthValue());
                bonosPorDia.put(fechaStr, bonosDia);
            }
        }

        return bonosPorDia;
    }

    /**
     * Obtiene el precio del servicio con id=1 de los turnos del barbero.
     * Retorna 0 si no encuentra el servicio.
     */
    private BigDecimal obtenerPrecioServicioId1(List<Turno> turnos) {
        return turnos.stream()
                .filter(t -> t.getTipoCorte().getId() == 1L)
                .findFirst()
                .map(t -> BigDecimal.valueOf(t.getTipoCorte().getPrecio()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Verifica si un turno está confirmado y debe ser considerado para el pago.
     */
    private boolean esTurnoConfirmado(Turno turno) {
        return Boolean.TRUE.equals(turno.getPagoConfirmado())
                || "CONFIRMADO".equalsIgnoreCase(turno.getEstado())
                || "BLOQUEADO".equalsIgnoreCase(turno.getEstado());
    }
}
