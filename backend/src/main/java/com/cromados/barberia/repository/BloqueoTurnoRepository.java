package com.cromados.barberia.repository;

import com.cromados.barberia.model.BloqueoTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.*;
import java.util.*;

public interface BloqueoTurnoRepository extends JpaRepository<BloqueoTurno, Long> {
    List<BloqueoTurno> findByBarbero_IdAndFecha(Long barberoId, LocalDate fecha);
    List<BloqueoTurno> findByBarbero_IdAndFechaBetween(Long barberoId, LocalDate desde, LocalDate hasta);
    Optional<BloqueoTurno> findByBarbero_IdAndFechaAndHora(Long barberoId, LocalDate f, LocalTime h);
    long countByBarberoId(Long barberoId);
}

