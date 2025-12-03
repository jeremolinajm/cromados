package com.cromados.barberia.repository;

import com.cromados.barberia.model.TipoCorte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoCorteRepository extends JpaRepository<TipoCorte, Long> {

    /**
     * Obtiene todos los tipos de corte con sus barberos habilitados cargados eagerly.
     * Esto evita LazyInitializationException cuando se accede a barberosHabilitados
     * fuera de la sesión de Hibernate.
     */
    @Query("SELECT DISTINCT t FROM TipoCorte t LEFT JOIN FETCH t.barberosHabilitados")
    List<TipoCorte> findAllWithBarberos();

    /**
     * Obtiene solo los tipos de corte activos (visibles para clientes).
     */
    List<TipoCorte> findByActivoTrue();

    /**
     * Obtiene todos los tipos de corte activos con sus barberos habilitados cargados eagerly.
     */
    @Query("SELECT DISTINCT t FROM TipoCorte t LEFT JOIN FETCH t.barberosHabilitados WHERE t.activo = true")
    List<TipoCorte> findAllActiveWithBarberos();
}
