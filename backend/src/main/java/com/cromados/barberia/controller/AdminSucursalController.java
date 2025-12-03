package com.cromados.barberia.controller;

import com.cromados.barberia.model.Sucursal;
import com.cromados.barberia.repository.SucursalRepository;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@RestController
@RequestMapping("/admin/sucursales")
@RequiredArgsConstructor
public class AdminSucursalController {

    private final SucursalRepository sucursalRepository;
    private final FileStorageService fileStorage;
    private final BarberoRepository barberoRepository; // <- IMPORTANTE

    @GetMapping
    public Page<Sucursal> listar(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(defaultValue = "id,asc") String sort) {
        String[] p = sort.split(",");
        Sort s = Sort.by(Sort.Direction.fromString(p.length > 1 ? p[1] : "asc"), p[0]);
        return sucursalRepository.findAll(PageRequest.of(page, size, s));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sucursal> obtener(@PathVariable Long id) {
        return sucursalRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Sucursal> crear(@Valid @RequestBody Sucursal body) {
        body.setId(null);
        Sucursal saved = sucursalRepository.save(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sucursal> actualizar(@PathVariable Long id, @Valid @RequestBody Sucursal body) {
        Optional<Sucursal> op = sucursalRepository.findById(id);
        if (op.isEmpty()) return ResponseEntity.notFound().build();
        Sucursal db = op.get();
        db.setNombre(body.getNombre());
        db.setDireccion(body.getDireccion());
        return ResponseEntity.ok(sucursalRepository.save(db));
    }

    @PostMapping(path = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Sucursal> uploadFoto(@PathVariable Long id,
                                               @RequestPart("file") MultipartFile file) {
        Optional<Sucursal> op = sucursalRepository.findById(id);
        if (op.isEmpty()) return ResponseEntity.notFound().build();
        try {
            String url = fileStorage.saveSucursalPhoto(file, id);
            Sucursal db = op.get();
            db.setFotoUrl(url);
            return ResponseEntity.ok(sucursalRepository.save(db));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        if (!sucursalRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (barberoRepository.existsBySucursalId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of(
                            "message",
                            "No se puede eliminar la sucursal porque tiene barberos asignados. " +
                                    "Reasigná o eliminá esos barberos antes de eliminar."
                    ));
        }
        sucursalRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
