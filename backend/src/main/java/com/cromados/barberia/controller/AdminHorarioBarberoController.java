package com.cromados.barberia.controller;

import com.cromados.barberia.model.HorarioBarbero;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.repository.HorarioBarberoRepository;
import com.cromados.barberia.repository.TurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/admin/horarios-barbero","/admin/horario","/admin/horario-barbero"})
@RequiredArgsConstructor
public class AdminHorarioBarberoController {

    private final HorarioBarberoRepository repo;
    private final BarberoRepository barberoRepo;
    @Autowired
    private TurnoRepository turnoRepo;

    @GetMapping("/{barberoId}")
    public List<HorarioBarbero> listar(@PathVariable Long barberoId) {
        return repo.findByBarbero_Id(barberoId);
    }

    @Transactional
    @PutMapping("/{barberoId}/{dia}")
    public HorarioBarbero upsert(@PathVariable Long barberoId,
                                 @PathVariable int dia,
                                 @RequestBody Map<String, String> body) {
        var b = barberoRepo.findById(barberoId).orElseThrow();

// Compatibilidad: si viene formato viejo, lo mapeamos a inicio1/fin1
        String inicio = body.get("inicio");
        String fin    = body.get("fin");

        String inicio1 = body.getOrDefault("inicio1", inicio);
        String fin1    = body.getOrDefault("fin1",    fin);
        String inicio2 = body.get("inicio2");
        String fin2    = body.get("fin2");

// Traemos existentes del día, ordenados por inicio (T1 primero, T2 después)
        List<HorarioBarbero> existentes = repo.findByBarbero_IdAndDiaSemana(barberoId, dia);
        existentes.sort(java.util.Comparator.comparing(HorarioBarbero::getInicio, java.util.Comparator.nullsLast(String::compareTo)));

        HorarioBarbero last = null;

// Upsert para T1
        if (inicio1 != null && fin1 != null) {
            if (existentes.size() >= 1) {
                HorarioBarbero hb1 = existentes.get(0);
                hb1.setInicio(inicio1);
                hb1.setFin(fin1);
                last = repo.save(hb1);
            } else {
                HorarioBarbero hb1 = new HorarioBarbero();
                hb1.setBarbero(b);
                hb1.setDiaSemana(dia);
                hb1.setInicio(inicio1);
                hb1.setFin(fin1);
                last = repo.save(hb1);
            }
        }

// Upsert para T2 (si viene)
        if (inicio2 != null && fin2 != null) {
            if (existentes.size() >= 2) {
                HorarioBarbero hb2 = existentes.get(1);
                hb2.setInicio(inicio2);
                hb2.setFin(fin2);
                last = repo.save(hb2);
            } else {
                HorarioBarbero hb2 = new HorarioBarbero();
                hb2.setBarbero(b);
                hb2.setDiaSemana(dia);
                hb2.setInicio(inicio2);
                hb2.setFin(fin2);
                last = repo.save(hb2);
            }
        }

// 🚫 Importante: NO borrar aquí si “sobran” franjas (evita 409 por FK)
// El borrado seguro seguí haciéndolo con el DELETE explícito.

        return last;
    }


    @DeleteMapping("/{barberoId}/{dia}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable Long barberoId, @PathVariable int dia) {
        var existentes = repo.findByBarbero_IdAndDiaSemana(barberoId, dia);
        if (!existentes.isEmpty()) {
            repo.deleteAll(existentes);
        }
    }
}
