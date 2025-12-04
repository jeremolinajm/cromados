// src/main/java/com/cromados/barberia/repository/TurnoRepository.java
package com.cromados.barberia.repository;

import com.cromados.barberia.model.Turno;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.time.*;
import java.util.*;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    // Próximos confirmados (para dashboard)
    Page<Turno> findByPagoConfirmadoTrueAndFechaGreaterThanEqualOrderByFechaAscHoraAsc(
            LocalDate fechaDesde, Pageable pageable);

    // Filtro por sucursal/barbero
    List<Turno> findByBarbero_IdAndFecha(Long barberoId, LocalDate fecha);

    Page<Turno> findByPagoConfirmadoTrueAndFechaBetweenOrderByFechaAscHoraAsc(
            LocalDate desde, LocalDate hasta, Pageable pageable);

    // 🆕 Para Telegram: turnos de un barbero en rango de fechas
    List<Turno> findByBarbero_IdAndFechaBetweenOrderByFechaAscHoraAsc(
            Long barberoId, LocalDate desde, LocalDate hasta);

    // 🆕 Para desbloquear: buscar turno bloqueado específico
    List<Turno> findByBarbero_IdAndFechaAndHoraAndEstado(
            Long barberoId, LocalDate fecha, LocalTime hora, String estado);

    // ✅ NUEVO: Buscar todos los turnos en un rango (incluye bloqueados)
    Page<Turno> findByFechaBetween(LocalDate desde, LocalDate hasta, Pageable pageable);

    // Para scheduler de recordatorios: buscar turnos en rango sin paginación
    List<Turno> findByFechaBetween(LocalDate desde, LocalDate hasta);

    // ✅ NUEVO: Contar turnos vigentes (fecha >= hoy, CONFIRMADO o BLOQUEADO)
    @Query("SELECT COUNT(t) FROM Turno t WHERE t.fecha >= :hoy AND (t.pagoConfirmado = true OR t.estado = 'BLOQUEADO')")
    long countVigentes(@Param("hoy") LocalDate hoy);

    // ✅ NUEVO: Buscar turnos válidos (BLOQUEADO o pagoConfirmado=true) en rango de fechas
    @Query("SELECT t FROM Turno t WHERE t.fecha BETWEEN :desde AND :hasta AND (t.pagoConfirmado = true OR t.estado = 'BLOQUEADO')")
    Page<Turno> findTurnosValidos(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, Pageable pageable);

    // 🔒 CRITICAL: Query with pessimistic write lock for race condition prevention
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT t FROM Turno t WHERE t.barbero.id = :barberoId AND t.fecha = :fecha AND t.hora = :hora " +
           "AND (t.pagoConfirmado = true OR t.estado = 'CONFIRMADO' OR t.estado = 'BLOQUEADO')")
    Optional<Turno> findConflictingTurnoWithLock(
        @Param("barberoId") Long barberoId,
        @Param("fecha") LocalDate fecha,
        @Param("hora") LocalTime hora
    );

    // 🧹 REAPER: Find bookings by estado and future dates
    List<Turno> findByEstadoAndFechaGreaterThanEqual(String estado, LocalDate fecha);

    // Count turnos for a barbero
    long countByBarberoId(Long barberoId);

    // 🆕 Para Telegram: buscar números de teléfono y edades asociadas a un nombre de cliente
    // Usa GROUP BY para obtener números únicos, ordenados por el turno más reciente
    @Query("SELECT t.clienteTelefono, t.clienteEdad, MAX(t.createdAt) as lastUsed FROM Turno t " +
           "WHERE LOWER(t.clienteNombre) LIKE LOWER(CONCAT('%', :nombre, '%')) " +
           "GROUP BY t.clienteTelefono, t.clienteEdad " +
           "ORDER BY lastUsed DESC")
    List<Object[]> findClientesByNombre(@Param("nombre") String nombre);
}