package com.cromados.barberia.service;

import com.cromados.barberia.dto.BarberoDTO;
import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.Sucursal;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.repository.SucursalRepository;
import com.cromados.barberia.repository.TurnoRepository;
import com.cromados.barberia.repository.HorarioBarberoRepository;
import com.cromados.barberia.repository.BloqueoTurnoRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class BarberoService {
    private final BarberoRepository repo;
    private final SucursalRepository sucRepo;
    private final TurnoRepository turnoRepo;
    private final HorarioBarberoRepository horarioRepo;
    private final BloqueoTurnoRepository bloqueoRepo;

    public BarberoService(BarberoRepository repo, SucursalRepository sucRepo,
                         TurnoRepository turnoRepo, HorarioBarberoRepository horarioRepo,
                         BloqueoTurnoRepository bloqueoRepo){
        this.repo=repo; this.sucRepo=sucRepo; this.turnoRepo=turnoRepo;
        this.horarioRepo=horarioRepo; this.bloqueoRepo=bloqueoRepo;
    }

    public Page<BarberoDTO> listar(Long sucursalId, Pageable pageable){
        Page<Barbero> page = (sucursalId==null)
                ? repo.findAll(pageable)
                : new PageImpl<>(repo.findBySucursal_Id(sucursalId), pageable, repo.findBySucursal_Id(sucursalId).size());
        return page.map(this::toDTO);
    }

    public BarberoDTO obtener(Long id){
        return toDTO(repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Barbero no encontrado")));
    }

    public BarberoDTO crear(BarberoDTO dto){
        Sucursal suc = sucRepo.findById(dto.getSucursalId())
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));
        Barbero b = new Barbero();
        b.setNombre(dto.getNombre());
        b.setSucursal(suc);
        b.setInstagram(dto.getInstagram());
        b.setFacebook(dto.getFacebook());
        b.setTelefono(dto.getTelefono());
        b.setTelegramChatId(dto.getTelegramChatId());
        return toDTO(repo.save(b));
    }

    public BarberoDTO actualizar(Long id, BarberoDTO dto){
        Barbero b = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Barbero no encontrado"));
        b.setNombre(dto.getNombre());
        if (!b.getSucursal().getId().equals(dto.getSucursalId())) {
            b.setSucursal(sucRepo.findById(dto.getSucursalId())
                    .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada")));
        }
        b.setInstagram(dto.getInstagram());
        b.setFacebook(dto.getFacebook());
        b.setTelefono(dto.getTelefono());
        b.setTelegramChatId(dto.getTelegramChatId());
        return toDTO(repo.save(b));
    }

    public void eliminar(Long id){
        if(!repo.existsById(id)) throw new IllegalArgumentException("Barbero no encontrado");

        // Verificar si hay turnos relacionados
        long turnosCount = turnoRepo.countByBarberoId(id);
        if (turnosCount > 0) {
            throw new DataIntegrityViolationException(
                "La operación no se pudo realizar porque existen registros relacionados. " +
                "Verificá barberos/turnos vinculados antes de eliminar."
            );
        }

        // Verificar si hay horarios relacionados
        long horariosCount = horarioRepo.countByBarberoId(id);
        if (horariosCount > 0) {
            throw new DataIntegrityViolationException(
                "La operación no se pudo realizar porque existen registros relacionados. " +
                "Verificá barberos/turnos vinculados antes de eliminar."
            );
        }

        // Verificar si hay bloqueos relacionados
        long bloqueosCount = bloqueoRepo.countByBarberoId(id);
        if (bloqueosCount > 0) {
            throw new DataIntegrityViolationException(
                "La operación no se pudo realizar porque existen registros relacionados. " +
                "Verificá barberos/turnos vinculados antes de eliminar."
            );
        }

        repo.deleteById(id);
    }

    private BarberoDTO toDTO(Barbero b){
        BarberoDTO dto = new BarberoDTO();
        dto.setId(b.getId());
        dto.setNombre(b.getNombre());
        dto.setSucursalId(b.getSucursal().getId());
        dto.setFotoUrl(b.getFotoUrl() != null
                ? b.getFotoUrl()
                : "/barberos/" + b.getId() + "/foto");
        dto.setInstagram(b.getInstagram());
        dto.setFacebook(b.getFacebook());
        dto.setTelefono(b.getTelefono());
        dto.setTelegramChatId(b.getTelegramChatId());
        return dto;
    }
}
