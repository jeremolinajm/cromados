package com.cromados.barberia.service;

import com.cromados.barberia.dto.TipoCorteDTO;
import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.TipoCorte;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.repository.TipoCorteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TipoCorteService {

    private final TipoCorteRepository repo;
    private final BarberoRepository barberoRepo;

    public Page<TipoCorteDTO> listar(Pageable pageable) {
        return repo.findAll(pageable).map(this::toDTO);
    }

    /**
     * Lista solo los servicios activos (visible para clientes).
     */
    public Page<TipoCorteDTO> listarActivos(Pageable pageable) {
        List<TipoCorte> activos = repo.findByActivoTrue();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), activos.size());
        List<TipoCorteDTO> dtos = activos.subList(start, end).stream()
                .map(this::toDTO)
                .toList();
        return new PageImpl<>(dtos, pageable, activos.size());
    }

    public TipoCorteDTO obtener(Long id) {
        return repo.findById(id).map(this::toDTO).orElseThrow();
    }

    public TipoCorteDTO crear(TipoCorteDTO d) {
        var e = new TipoCorte();
        e.setNombre(d.getNombre());
        e.setPrecio(d.getPrecio());
        e.setDuracionMin(d.getDuracionMin());
        e.setDescripcion(d.getDescripcion());
        e.setSesiones(d.getSesiones() == null || d.getSesiones() < 1 ? 1 : d.getSesiones());
        e.setAdicional(d.getAdicional() != null ? d.getAdicional() : false);

        // Establecer barberos habilitados
        if (d.getBarberosHabilitadosIds() != null && !d.getBarberosHabilitadosIds().isEmpty()) {
            List<Barbero> barberos = barberoRepo.findAllById(d.getBarberosHabilitadosIds());
            e.setBarberosHabilitados(new HashSet<>(barberos));
        }

        return toDTO(repo.save(e));
    }

    public TipoCorteDTO actualizar(Long id, TipoCorteDTO d) {
        var e = repo.findById(id).orElseThrow();
        e.setNombre(d.getNombre());
        e.setPrecio(d.getPrecio());
        e.setDuracionMin(d.getDuracionMin());
        e.setDescripcion(d.getDescripcion());
        e.setSesiones(d.getSesiones() == null || d.getSesiones() < 1 ? 1 : d.getSesiones());
        e.setAdicional(d.getAdicional() != null ? d.getAdicional() : false);

        // Actualizar barberos habilitados
        if (d.getBarberosHabilitadosIds() != null) {
            if (d.getBarberosHabilitadosIds().isEmpty()) {
                // Vacío = todos los barberos pueden ofrecer el servicio
                e.setBarberosHabilitados(new HashSet<>());
            } else {
                List<Barbero> barberos = barberoRepo.findAllById(d.getBarberosHabilitadosIds());
                e.setBarberosHabilitados(new HashSet<>(barberos));
            }
        }

        return toDTO(repo.save(e));
    }

    public void eliminar(Long id) {
        repo.deleteById(id);
    }

    /**
     * Activa o desactiva un servicio (soft delete).
     */
    public TipoCorteDTO toggleActivo(Long id) {
        TipoCorte servicio = repo.findById(id).orElseThrow();
        servicio.setActivo(!servicio.getActivo());
        return toDTO(repo.save(servicio));
    }

    public TipoCorteDTO actualizarBarberosHabilitados(Long id, List<Long> barberoIds) {
        TipoCorte servicio = repo.findById(id).orElseThrow();

        if (barberoIds == null || barberoIds.isEmpty()) {
            // Vacío significa que todos los barberos pueden ofrecer el servicio
            servicio.setBarberosHabilitados(new HashSet<>());
        } else {
            // Buscar barberos y establecerlos
            List<Barbero> barberos = barberoRepo.findAllById(barberoIds);
            servicio.setBarberosHabilitados(new HashSet<>(barberos));
        }

        return toDTO(repo.save(servicio));
    }

    private TipoCorteDTO toDTO(TipoCorte e) {
        List<Long> barberoIds = null;
        if (e.getBarberosHabilitados() != null && !e.getBarberosHabilitados().isEmpty()) {
            barberoIds = e.getBarberosHabilitados().stream()
                    .map(Barbero::getId)
                    .collect(Collectors.toList());
        }

        return TipoCorteDTO.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .precio(e.getPrecio())
                .duracionMin(e.getDuracionMin())
                .descripcion(e.getDescripcion())
                .sesiones(e.getSesiones() == null || e.getSesiones() < 1 ? 1 : e.getSesiones())
                .adicional(e.getAdicional() != null ? e.getAdicional() : false)
                .activo(e.getActivo() != null ? e.getActivo() : true)
                .barberosHabilitadosIds(barberoIds)
                .build();
    }
}
