package com.cromados.barberia.controller;

import com.cromados.barberia.dto.TipoCorteDTO;
import com.cromados.barberia.service.TipoCorteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/admin/servicios", "/admin/tipo-corte"})
@RequiredArgsConstructor
public class AdminTipoCorteController {

    private final TipoCorteService service;

    @GetMapping
    public Page<TipoCorteDTO> listar(@RequestParam(defaultValue="0") int page,
                                     @RequestParam(defaultValue="20") int size,
                                     @RequestParam(defaultValue="id,asc") String sort) {
        String[] p=sort.split(",");
        Sort s=Sort.by(Sort.Direction.fromString(p.length>1?p[1]:"asc"), p[0]);
        return service.listar(PageRequest.of(page, size, s));
    }

    @GetMapping("/{id}")
    public TipoCorteDTO obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<TipoCorteDTO> crear(@Valid @RequestBody TipoCorteDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(dto));
    }

    @PutMapping("/{id}")
    public TipoCorteDTO actualizar(@PathVariable Long id, @Valid @RequestBody TipoCorteDTO dto) {
        return service.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }

    /**
     * Activa o desactiva un servicio (soft delete).
     */
    @PatchMapping("/{id}/toggle-activo")
    public TipoCorteDTO toggleActivo(@PathVariable Long id) {
        return service.toggleActivo(id);
    }

    /**
     * Actualiza los barberos habilitados para un servicio.
     * @param id ID del servicio
     * @param barberoIds Lista de IDs de barberos habilitados (vacía = todos)
     */
    @PutMapping("/{id}/barberos-habilitados")
    public TipoCorteDTO actualizarBarberosHabilitados(
            @PathVariable Long id,
            @RequestBody java.util.List<Long> barberoIds) {
        return service.actualizarBarberosHabilitados(id, barberoIds);
    }
}
