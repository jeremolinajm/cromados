// src/main/java/com/cromados/barberia/service/TurnoAdminService.java
package com.cromados.barberia.service;

import com.cromados.barberia.dto.TurnoAdminDTO;
import com.cromados.barberia.model.Turno;
import com.cromados.barberia.repository.TurnoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class TurnoAdminService {
    private final TurnoRepository turnoRepo;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public TurnoAdminService(TurnoRepository turnoRepo) {
        this.turnoRepo = turnoRepo;
    }

    public Page<Turno> proximosConfirmados(int dias, int limit) {
        LocalDate desde = LocalDate.now();
        int size = Math.max(1, Math.min(limit, 50));
        return turnoRepo.findByPagoConfirmadoTrueAndFechaGreaterThanEqualOrderByFechaAscHoraAsc(
                desde, PageRequest.of(0, size));
    }

    public Page<Turno> buscarConfirmados(LocalDate desde, LocalDate hasta, int page, int size) {
        return turnoRepo.findByPagoConfirmadoTrueAndFechaBetweenOrderByFechaAscHoraAsc(
                desde, hasta, PageRequest.of(page, size));
    }

    // ✅ NUEVO: Método que incluye turnos BLOQUEADOS con ordenamiento dinámico
    public Page<Turno> buscarTurnosConBloqueados(LocalDate desde, LocalDate hasta, int page, int size, String sortParam) {
        // Parsear el parámetro de ordenamiento (ej: "fecha,desc" o "id,asc")
        Sort sort;
        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String field = parts[0].trim();
            Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            // Siempre añadir hora como segundo criterio si ordenamos por fecha
            if ("fecha".equals(field)) {
                sort = Sort.by(direction, "fecha").and(Sort.by(direction, "hora"));
            } else {
                sort = Sort.by(direction, field);
            }
        } else {
            // Default: ordenar por fecha desc, hora desc
            sort = Sort.by(Sort.Direction.DESC, "fecha", "hora");
        }

        PageRequest pageRequest = PageRequest.of(page, size, sort);
        // 🔧 Usar query que filtra directamente en SQL (pagoConfirmado=true OR estado='BLOQUEADO')
        return turnoRepo.findTurnosValidos(desde, hasta, pageRequest);
    }

    /** Devuelve el Turno en forma de TurnoAdminDTO (usado por PublicController). */
    public TurnoAdminDTO obtener(Long id) {
        Turno t = turnoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Turno inexistente: " + id));
        return toAdminDTO(t);
    }

    /**
     * Obtiene los últimos N turnos ordenados por ID descendente
     * (los más recientes primero, sin filtro de fecha)
     */
    public Page<Turno> ultimosTurnos(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        return turnoRepo.findAll(PageRequest.of(0, size, sort));
    }

    /**
     * Cuenta los turnos vigentes (fecha >= hoy, CONFIRMADO o BLOQUEADO)
     */
    public long contarVigentes() {
        LocalDate hoy = LocalDate.now();
        return turnoRepo.countVigentes(hoy);
    }

    /** Mapeo puntual Turno -> TurnoAdminDTO (1:1 con tu DTO actual). */
    private TurnoAdminDTO toAdminDTO(Turno t) {
        TurnoAdminDTO dto = new TurnoAdminDTO();
        dto.setId(t.getId());
        dto.setClienteNombre(t.getClienteNombre());
        dto.setClienteEmail(t.getClienteEmail());
        dto.setClienteTelefono(t.getClienteTelefono());

        // ids relacionados (evito NPE por las dudas)
        dto.setSucursalId(t.getSucursal() != null ? t.getSucursal().getId() : null);
        dto.setBarberoId(t.getBarbero() != null ? t.getBarbero().getId() : null);
        dto.setTipoCorteId(t.getTipoCorte() != null ? t.getTipoCorte().getId() : null);

        // formato igual al front: "yyyy-MM-dd" y "HH:mm"
        dto.setFecha(t.getFecha() != null ? t.getFecha().toString() : null);
        dto.setHora(t.getHora() != null ? t.getHora().format(HHMM) : null);

        dto.setEstado(t.getEstado());
        dto.setPagoConfirmado(t.getPagoConfirmado());
        dto.setMontoPagado(t.getMontoPagado());
        dto.setSenia(t.getSenia());
        dto.setMontoEfectivo(t.getMontoEfectivo());
        dto.setAdicionales(t.getAdicionales());

        return dto;
    }
}