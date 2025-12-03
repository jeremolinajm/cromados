package com.cromados.barberia.controller;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.repository.BarberoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@RestController
@RequestMapping
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private final BarberoRepository barberoRepository;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir; // Debe ser ABSOLUTA: /home/jota/dev/cromados/backend/uploads

    public ImageController(BarberoRepository barberoRepository) {
        this.barberoRepository = barberoRepository;
    }

    @GetMapping(value = "/barberos/{id}/foto", produces = {
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE
    })
    public ResponseEntity<byte[]> getBarberoFoto(@PathVariable Long id) {
        var opt = barberoRepository.findById(id);
        if (opt.isEmpty()) {
            log.warn("[ImageController] Barbero {} no encontrado", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var b = opt.get();

        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path barberosDir = root.resolve("barberos");

        log.info("[ImageController] GET /barberos/{}/foto - uploadDir={}, root={}, fotoUrl={}",
                id, uploadDir, root, b.getFotoUrl());

        // 1) Resolver con fotoUrl si existe
        Path candidate = null;
        if (b.getFotoUrl() != null && !b.getFotoUrl().isBlank()) {
            String raw = b.getFotoUrl().trim();
            String rel = raw;
            if (raw.startsWith("/uploads/")) rel = raw.substring("/uploads/".length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            if (!rel.startsWith("barberos/")) rel = "barberos/" + rel;
            candidate = root.resolve(rel).normalize();
            log.info("[ImageController] Candidate path from fotoUrl: {}, exists={}", candidate, Files.exists(candidate));
        }

        // 2) Fallback por patrón barbero-{id}-*.jpg|jpeg|png
        try {
            if (candidate == null || !Files.exists(candidate)) {
                try (var files = Files.exists(barberosDir) ? Files.list(barberosDir) : Stream.<Path>empty()) {
                    candidate = files
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String n = p.getFileName().toString().toLowerCase();
                                return n.startsWith("barbero-" + id + "-") &&
                                        (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"));
                            })
                            .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                            .orElse(null);
                }
            }

            if (candidate == null || !Files.exists(candidate) || !Files.isReadable(candidate)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            byte[] data = Files.readAllBytes(candidate);

            MediaType mt = MediaType.IMAGE_JPEG;
            try {
                String probe = Files.probeContentType(candidate);
                if (probe != null) mt = MediaType.parseMediaType(probe);
            } catch (Exception ignored) {}

            return ResponseEntity.ok()
                    .contentType(mt)
                    .contentLength(data.length)
                    .cacheControl(CacheControl.noCache())
                    .body(data);

        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
