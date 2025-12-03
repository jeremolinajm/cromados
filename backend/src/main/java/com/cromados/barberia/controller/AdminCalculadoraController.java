package com.cromados.barberia.controller;

import com.cromados.barberia.dto.PagoBarberoDTO;
import com.cromados.barberia.service.CalculadoraPagosService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/calculadora")
@RequiredArgsConstructor
public class AdminCalculadoraController {

    private final CalculadoraPagosService calculadoraService;

    /**
     * Calcula los pagos a barberos para un rango de fechas.
     *
     * @param desde Fecha de inicio (formato: yyyy-MM-dd)
     * @param hasta Fecha de fin (formato: yyyy-MM-dd)
     * @return Lista de pagos por barbero
     */
    @GetMapping("/pagos")
    public List<PagoBarberoDTO> calcularPagos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return calculadoraService.calcularPagos(desde, hasta);
    }
}
