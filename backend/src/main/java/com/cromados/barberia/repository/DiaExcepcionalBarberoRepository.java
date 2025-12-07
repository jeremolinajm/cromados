package com.cromados.barberia.repository;

import com.cromados.barberia.model.DiaExcepcionalBarbero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio para gestionar días excepcionales de trabajo de los barberos.
 */
@Repository
public interface DiaExcepcionalBarberoRepository extends JpaRepository<DiaExcepcionalBarbero, Long> {

    /**
     * Busca todas las franjas excepcionales de un barbero para una fecha específica.
     *
     * @param barberoId ID del barbero
     * @param fecha Fecha específica a consultar
     * @return Lista de franjas excepcionales para esa fecha (puede haber múltiples: T1, T2, etc.)
     */
    List<DiaExcepcionalBarbero> findByBarbero_IdAndFecha(Long barberoId, LocalDate fecha);

    /**
     * Busca todos los días excepcionales configurados para un barbero.
     * Útil para el panel admin.
     *
     * @param barberoId ID del barbero
     * @return Lista de todos los días excepcionales del barbero, ordenados por fecha
     */
    List<DiaExcepcionalBarbero> findByBarbero_IdOrderByFechaAsc(Long barberoId);

    /**
     * Busca días excepcionales futuros para un barbero (a partir de hoy).
     * Útil para limpiar días pasados o mostrar solo futuros.
     *
     * @param barberoId ID del barbero
     * @param fechaDesde Fecha desde la cual buscar (normalmente hoy)
     * @return Lista de días excepcionales futuros
     */
    List<DiaExcepcionalBarbero> findByBarbero_IdAndFechaGreaterThanEqualOrderByFechaAsc(Long barberoId, LocalDate fechaDesde);

    /**
     * Cuenta cuántos días excepcionales tiene configurados un barbero.
     *
     * @param barberoId ID del barbero
     * @return Cantidad de días excepcionales
     */
    long countByBarbero_Id(Long barberoId);
}
