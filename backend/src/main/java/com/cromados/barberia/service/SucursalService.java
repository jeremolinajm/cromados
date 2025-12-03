package com.cromados.barberia.service;

import com.cromados.barberia.dto.SucursalDTO;
import com.cromados.barberia.model.Sucursal;
import com.cromados.barberia.repository.SucursalRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
public class SucursalService {
    private final SucursalRepository repo;
    public SucursalService(SucursalRepository repo){ this.repo=repo; }

    public Page<SucursalDTO> listar(Pageable pageable){
        return repo.findAll(pageable).map(this::toDTO);
    }

    public SucursalDTO obtener(Long id){
        return toDTO(repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada")));
    }

    public SucursalDTO crear(SucursalDTO dto){
        Sucursal s = new Sucursal();
        s.setNombre(dto.getNombre());
        s.setDireccion(dto.getDireccion());
        return toDTO(repo.save(s));
    }

    public SucursalDTO actualizar(Long id, SucursalDTO dto){
        Sucursal s = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));
        s.setNombre(dto.getNombre());
        s.setDireccion(dto.getDireccion());
        return toDTO(repo.save(s));
    }

    public void eliminar(Long id){
        if (!repo.existsById(id)) throw new IllegalArgumentException("Sucursal no encontrada");
        repo.deleteById(id);
    }

    private SucursalDTO toDTO(Sucursal s){
        SucursalDTO dto = new SucursalDTO();
        dto.setId(s.getId());
        dto.setNombre(s.getNombre());
        dto.setDireccion(s.getDireccion());
        dto.setFotoUrl(s.getFotoUrl());
        return dto;
    }
}
