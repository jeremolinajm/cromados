package com.cromados.barberia.controller;

import com.cromados.barberia.dto.BarberoDTO;
import com.cromados.barberia.service.BarberoService;
import com.cromados.barberia.service.FileStorageService;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.service.TelegramBotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/admin/barberos")
@RequiredArgsConstructor
public class AdminBarberoController {
    private static final Logger log = LoggerFactory.getLogger(AdminBarberoController.class);

    private final BarberoService service;
    private final FileStorageService fileStorageService;
    private final BarberoRepository barberoRepository;
    private final TelegramBotService telegramBot;

    @GetMapping
    public Page<BarberoDTO> listar(@RequestParam(required=false) Long sucursalId,
                                   @RequestParam(defaultValue="0") int page,
                                   @RequestParam(defaultValue="10") int size,
                                   @RequestParam(defaultValue="id,asc") String sort){
        String[] p=sort.split(",");
        Sort s=Sort.by(Sort.Direction.fromString(p.length>1?p[1]:"asc"), p[0]);
        return service.listar(sucursalId, PageRequest.of(page, size, s));
    }

    @GetMapping("/{id}") public BarberoDTO obtener(@PathVariable Long id){ return service.obtener(id); }

    @PostMapping public ResponseEntity<BarberoDTO> crear(@Valid @RequestBody BarberoDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(dto));
    }

    @PutMapping("/{id}") public BarberoDTO actualizar(@PathVariable Long id, @Valid @RequestBody BarberoDTO dto){
        return service.actualizar(id, dto);
    }

    @PatchMapping("/{id}/telegram")
    public ResponseEntity<?> vincularTelegram(
            @PathVariable Long id,
            @RequestParam Long chatId
    ) {
        var barbero = barberoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Barbero no encontrado"));

        // Verificar que el chatId no esté usado por otro barbero
        var existente = barberoRepository.findByTelegramChatId(chatId);
        if (existente.isPresent() && !existente.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Este Chat ID ya está vinculado a otro barbero"));
        }

        barbero.setTelegramChatId(chatId);
        barberoRepository.save(barbero);

        // Enviar mensaje de bienvenida al barbero
        try {
            telegramBot.sendText(chatId, String.format("""
            ✅ **Vinculación exitosa**
            
            Hola %s! Tu cuenta fue vinculada correctamente.
            
            Usa /menu para ver los comandos disponibles.
            """, barbero.getNombre()));
        } catch (Exception e) {
            log.warn("[Telegram] No se pudo enviar mensaje de bienvenida: {}", e.getMessage());
        }

        return ResponseEntity.ok(service.obtener(id));
    }

    // Foto EXCLUSIVA por este endpoint
    @PostMapping(path = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BarberoDTO> uploadFoto(@PathVariable Long id,
                                                 @RequestPart("file") MultipartFile file) {
        var opt = barberoRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            String url = fileStorageService.saveBarberoPhoto(file, id);
            var entity = opt.get();
            entity.setFotoUrl(url);
            barberoRepository.save(entity);
            // devolver DTO con fotoUrl actualizada
            return ResponseEntity.ok(service.obtener(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id){ service.eliminar(id); }
}
