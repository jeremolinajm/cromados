// src/main/java/com/cromados/barberia/controller/AdminTurnoController.java
package com.cromados.barberia.controller;

import com.cromados.barberia.model.Turno;
import com.cromados.barberia.service.TurnoAdminService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/admin/turnos")
public class AdminTurnoController {

    private final TurnoAdminService adminService;

    public AdminTurnoController(TurnoAdminService adminService) {
        this.adminService = adminService;
    }

    // Mini listado para el panel
    @GetMapping("/proximos")
    public Map<String, Object> proximos(
            @RequestParam(defaultValue = "7") int dias,
            @RequestParam(defaultValue = "8") int limit
    ) {
        Page<Turno> page = adminService.proximosConfirmados(dias, limit);
        return Map.of(
                "items", page.getContent().stream().map(AdminTurnoController::toDTO).toList()
        );
    }

    // ✅ NUEVO: Últimos turnos reservados (ordenados por ID desc, sin filtro de fecha)
    @GetMapping("/ultimos")
    public Map<String, Object> ultimos(@RequestParam(defaultValue = "10") int limit) {
        Page<Turno> page = adminService.ultimosTurnos(limit);

        // Filtrar solo turnos válidos (BLOQUEADO o pagoConfirmado=true)
        var filtered = page.getContent().stream()
                .filter(t -> "BLOQUEADO".equals(t.getEstado()) || Boolean.TRUE.equals(t.getPagoConfirmado()))
                .map(AdminTurnoController::toDTO)
                .toList();

        return Map.of("items", filtered);
    }

    // ✅ NUEVO: Contador de turnos vigentes
    @GetMapping("/count-vigentes")
    public Map<String, Object> countVigentes() {
        long count = adminService.contarVigentes();
        return Map.of("count", count);
    }

    // ✅ LISTADO FILTRADO - Solo BLOQUEADO (efectivo) y CONFIRMADO (web)
    @GetMapping
    public Map<String, Object> list(
            @RequestParam String desde,
            @RequestParam String hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "fecha,desc") String sort
    ) {
        Page<Turno> p = adminService.buscarTurnosConBloqueados(
                LocalDate.parse(desde),
                LocalDate.parse(hasta),
                page,
                size,
                sort
        );

        // El filtrado ya se hace en SQL, solo mapeamos a DTO
        var items = p.getContent().stream()
                .map(AdminTurnoController::toDTO)
                .toList();

        return Map.of(
                "items", items,
                "page", p.getNumber(),
                "pages", p.getTotalPages(),
                "total", p.getTotalElements() // Total de la query SQL
        );
    }

    // ✅ MÉTODO SIMPLIFICADO - Lee DIRECTO de la BD sin lógica extra
    private static Map<String, Object> toDTO(Turno t) {
        // ✅ LEER DIRECTO DE LA BD - Sin override
        BigDecimal montoPagado = t.getMontoPagado() != null ? t.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal montoEfectivo = t.getMontoEfectivo() != null ? t.getMontoEfectivo() : BigDecimal.ZERO;
        Boolean seniaDB = t.getSenia(); // Leer tal cual viene

        // ✅ Usar HashMap para permitir valores null en grupoId
        var dto = new java.util.HashMap<String, Object>();
        dto.put("id", t.getId());
        dto.put("fecha", t.getFecha().toString());
        dto.put("hora", t.getHora().toString());
        dto.put("clienteNombre", t.getClienteNombre() != null ? t.getClienteNombre() : "-");
        dto.put("clienteTelefono", t.getClienteTelefono() != null ? t.getClienteTelefono() : "-");
        // ✅ IDs (requeridos por el frontend)
        dto.put("barberoId", t.getBarbero() != null ? t.getBarbero().getId() : null);
        dto.put("sucursalId", t.getSucursal() != null ? t.getSucursal().getId() : null);
        dto.put("tipoCorteId", t.getTipoCorte() != null ? t.getTipoCorte().getId() : null);
        // ✅ Nombres desde relaciones (con los nombres correctos que espera el frontend)
        dto.put("barberoNombre", t.getBarbero() != null ? t.getBarbero().getNombre() : null);
        dto.put("sucursalNombre", t.getSucursal() != null ? t.getSucursal().getNombre() : null);
        dto.put("servicioNombre", t.getTipoCorte() != null ? t.getTipoCorte().getNombre() : null);
        // ✅ Estado y pago DIRECTO de la BD
        dto.put("estado", t.getEstado() != null ? t.getEstado() : "DESCONOCIDO");
        dto.put("pagoConfirmado", Boolean.TRUE.equals(t.getPagoConfirmado()));
        // ✅ Montos DIRECTOS de la BD
        dto.put("montoPagado", montoPagado);
        dto.put("senia", Boolean.TRUE.equals(seniaDB));
        dto.put("montoEfectivo", montoEfectivo);
        // ✅ Grupo para servicios multi-sesión (puede ser null para turnos individuales)
        dto.put("grupoId", t.getGrupoId());
        // ✅ Adicionales (servicios adicionales)
        dto.put("adicionales", t.getAdicionales() != null ? t.getAdicionales() : "");

        return dto;
    }
}
