package com.cromados.barberia.controller;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.DiaExcepcionalBarbero;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.repository.DiaExcepcionalBarberoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controlador para gestionar días excepcionales de trabajo de los barberos.
 *
 * Endpoints:
 * - GET    /admin/horarios-barbero/{barberoId}/excepcionales - Listar días excepcionales
 * - POST   /admin/horarios-barbero/{barberoId}/excepcionales - Agregar día excepcional
 * - DELETE /admin/horarios-barbero/{barberoId}/excepcionales/{id} - Eliminar día excepcional
 */
@Slf4j
@RestController
@RequestMapping("/admin/horarios-barbero")
@RequiredArgsConstructor
public class AdminDiaExcepcionalController {

    private final DiaExcepcionalBarberoRepository diaExcepcionalRepo;
    private final BarberoRepository barberoRepo;

    /**
     * Lista todos los días excepcionales de un barbero, ordenados por fecha ascendente.
     *
     * GET /admin/horarios-barbero/{barberoId}/excepcionales
     */
    @GetMapping("/{barberoId}/excepcionales")
    public List<DiaExcepcionalBarbero> listar(@PathVariable Long barberoId) {
        log.debug("[AdminDiaExcepcional] Listando días excepcionales del barbero {}", barberoId);
        return diaExcepcionalRepo.findByBarbero_IdOrderByFechaAsc(barberoId);
    }

    /**
     * Agrega un nuevo día excepcional para un barbero.
     * Permite múltiples franjas para la misma fecha (T1, T2, etc.).
     *
     * POST /admin/horarios-barbero/{barberoId}/excepcionales
     *
     * Body:
     * {
     *   "fecha": "2024-12-24",
     *   "inicio": "09:00",
     *   "fin": "13:00"
     * }
     */
    @Transactional
    @PostMapping("/{barberoId}/excepcionales")
    @ResponseStatus(HttpStatus.CREATED)
    public DiaExcepcionalBarbero agregar(@PathVariable Long barberoId,
                                         @RequestBody Map<String, String> body) {
        Barbero barbero = barberoRepo.findById(barberoId)
                .orElseThrow(() -> new IllegalArgumentException("Barbero no encontrado: " + barberoId));

        String fechaStr = body.get("fecha");
        String inicio = body.get("inicio");
        String fin = body.get("fin");

        if (fechaStr == null || inicio == null || fin == null) {
            throw new IllegalArgumentException("Faltan campos requeridos: fecha, inicio, fin");
        }

        LocalDate fecha = LocalDate.parse(fechaStr); // Formato esperado: YYYY-MM-DD

        // Validar que la fecha no sea pasada
        if (fecha.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("No se puede agregar un día excepcional en el pasado: " + fecha);
        }

        DiaExcepcionalBarbero nuevo = DiaExcepcionalBarbero.builder()
                .barbero(barbero)
                .fecha(fecha)
                .inicio(inicio)
                .fin(fin)
                .build();

        DiaExcepcionalBarbero guardado = diaExcepcionalRepo.save(nuevo);
        log.info("[AdminDiaExcepcional] Día excepcional agregado: Barbero {}, Fecha {}, {}-{}",
                 barberoId, fecha, inicio, fin);

        return guardado;
    }

    /**
     * Elimina un día excepcional específico por su ID.
     *
     * DELETE /admin/horarios-barbero/{barberoId}/excepcionales/{id}
     */
    @DeleteMapping("/{barberoId}/excepcionales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long barberoId, @PathVariable Long id) {
        DiaExcepcionalBarbero diaExcepcional = diaExcepcionalRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Día excepcional no encontrado: " + id));

        // Validar que el día excepcional pertenece al barbero correcto (seguridad)
        if (!diaExcepcional.getBarbero().getId().equals(barberoId)) {
            throw new IllegalArgumentException("El día excepcional no pertenece al barbero especificado");
        }

        diaExcepcionalRepo.deleteById(id);
        log.info("[AdminDiaExcepcional] Día excepcional eliminado: ID {}, Barbero {}, Fecha {}",
                 id, barberoId, diaExcepcional.getFecha());
    }
}
