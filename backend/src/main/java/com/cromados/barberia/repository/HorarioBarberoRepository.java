package com.cromados.barberia.repository;

import com.cromados.barberia.model.HorarioBarbero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface HorarioBarberoRepository extends JpaRepository<HorarioBarbero, Long> {
    List<HorarioBarbero> findByBarbero_IdAndDiaSemana(Long barberoId, int diaSemana);
    List<HorarioBarbero> findByBarbero_Id(Long barberoId);
    long countByBarberoId(Long barberoId);
}
