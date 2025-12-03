package com.cromados.barberia.repository;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarberoRepository extends JpaRepository<Barbero, Long> {
    List<Barbero> findBySucursal_Id(Long sucursalId);
    boolean existsBySucursalId(Long sucursalId);
    Optional<Barbero> findByTelegramChatId(Long chatId);
}
