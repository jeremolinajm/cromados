package com.cromados.barberia.controller;

import com.cromados.barberia.dto.*;
import com.cromados.barberia.repository.HorarioBarberoRepository;
import com.cromados.barberia.service.BarberoService;
import com.cromados.barberia.service.SucursalService;
import com.cromados.barberia.service.TipoCorteService;
import com.cromados.barberia.service.TurnoAdminService;
import com.cromados.barberia.service.TurnoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PublicController {

    private final SucursalService sucursalService;
    private final BarberoService barberoService;
    private final TipoCorteService tipoCorteService;
    private final TurnoService turnoService;
    private final TurnoAdminService turnoAdminService;
    private final HorarioBarberoRepository horarioRepo;

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm");

    public PublicController(
            SucursalService sucursalService,
            BarberoService barberoService,
            TipoCorteService tipoCorteService,
            TurnoService turnoService,
            TurnoAdminService turnoAdminService, HorarioBarberoRepository horarioRepo
    ) {
        this.sucursalService = sucursalService;
        this.barberoService = barberoService;
        this.tipoCorteService = tipoCorteService;
        this.turnoService = turnoService;
        this.turnoAdminService = turnoAdminService;
        this.horarioRepo = horarioRepo;
    }

    // 1) Sucursales públicas (tomamos una página "grande")
    @GetMapping("/sucursales")
    public List<SucursalDTO> sucursales() {
        return sucursalService
                .listar(PageRequest.of(0, 500, Sort.by("nombre").ascending()))
                .getContent();
    }

    // 2) Barberos por sucursal
    @GetMapping("/barberos")
    public List<BarberoDTO> barberos(@RequestParam(required = false) Long sucursalId) {
        return barberoService
                .listar(sucursalId, PageRequest.of(0, 500, Sort.by("nombre").ascending()))
                .getContent();
    }

    // 3) Tipos de corte (alias /servicios por compatibilidad con el front)
    @GetMapping({"/tipos-corte", "/servicios"})
    public List<TipoCorteDTO> tiposCorte(@RequestParam(required = false) Long barberoId) {
        List<TipoCorteDTO> servicios = tipoCorteService
                .listarActivos(PageRequest.of(0, 500, Sort.by("id").ascending()))
                .getContent();

        // Si se especifica un barbero, filtrar servicios que él puede ofrecer
        if (barberoId != null) {
            servicios = servicios.stream()
                    .filter(s -> {
                        // Si no tiene barberos habilitados, todos pueden ofrecer el servicio
                        if (s.getBarberosHabilitadosIds() == null || s.getBarberosHabilitadosIds().isEmpty()) {
                            return true;
                        }
                        // Si tiene barberos habilitados, verificar que el barbero esté en la lista
                        return s.getBarberosHabilitadosIds().contains(barberoId);
                    })
                    .toList();
        }

        return servicios;
    }

    // 4) Horarios disponibles para un barbero en una fecha (HH:mm)
    @GetMapping("/disponibilidad")
    public List<String> disponibilidad(@RequestParam Long barberoId, @RequestParam String fecha) {
        return turnoService.horariosDisponibles(barberoId, LocalDate.parse(fecha))
                .stream().map(TF::format).toList();
    }

    // 5) Crear reserva (queda PENDIENTE_PAGO). Devolvemos el DTO admin por simplicidad.
    @PostMapping("/reservas")
    public ResponseEntity<TurnoAdminDTO> reservar(@Valid @RequestBody TurnoRequest req) {
        var creado = turnoService.crearTurno(req);
        var dto    = turnoAdminService.obtener(creado.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // 6) Consultar reserva por id
    @GetMapping("/reservas/{id}")
    public ResponseEntity<TurnoAdminDTO> reserva(@PathVariable Long id) {
        return ResponseEntity.ok(turnoAdminService.obtener(id));
    }

    @GetMapping("/barberos/{barberoId}/horarios-semana")
    public List<HorarioSemanaDTO> horariosSemana(@PathVariable Long barberoId) {
        var hs = horarioRepo.findByBarbero_Id(barberoId);
        return hs.stream()
                .map(h -> new HorarioSemanaDTO(h.getDiaSemana(), h.getInicio(), h.getFin()))
                .collect(Collectors.toList());
    }
}
