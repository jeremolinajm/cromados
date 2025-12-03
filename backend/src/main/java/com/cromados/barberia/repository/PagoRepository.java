package com.cromados.barberia.repository;

import com.cromados.barberia.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByMpPreferenceId(String mpPreferenceId);
    Optional<Pago> findByTurnoId(Long turnoId);
}
