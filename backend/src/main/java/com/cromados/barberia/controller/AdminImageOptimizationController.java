package com.cromados.barberia.controller;

import com.cromados.barberia.model.Barbero;
import com.cromados.barberia.model.Sucursal;
import com.cromados.barberia.repository.BarberoRepository;
import com.cromados.barberia.repository.SucursalRepository;
import com.cromados.barberia.service.ImageOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Controlador administrativo para optimización de imágenes existentes.
 */
@RestController
@RequestMapping("/admin/optimize-images")
@RequiredArgsConstructor
@Slf4j
public class AdminImageOptimizationController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final ImageOptimizationService imageOptimizationService;
    private final BarberoRepository barberoRepository;
    private final SucursalRepository sucursalRepository;

    /**
     * Optimiza todas las imágenes existentes (barberos y sucursales).
     * Genera versiones optimizadas en WebP sin eliminar las originales.
     *
     * @return Resumen de la optimización
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> optimizeAllImages() {
        log.info("[ImageOptimization] Iniciando optimización de imágenes existentes...");

        Map<String, Object> result = new HashMap<>();
        int totalProcessed = 0;
        int totalErrors = 0;
        List<String> errors = new ArrayList<>();

        try {
            // Optimizar imágenes de barberos
            int barberosProcessed = optimizeBarberosImages();
            totalProcessed += barberosProcessed;
            result.put("barberosProcessed", barberosProcessed);

            // Optimizar imágenes de sucursales
            int sucursalesProcessed = optimizeSucursalesImages();
            totalProcessed += sucursalesProcessed;
            result.put("sucursalesProcessed", sucursalesProcessed);

            result.put("totalProcessed", totalProcessed);
            result.put("status", "success");
            result.put("message", String.format("Se optimizaron %d imágenes correctamente", totalProcessed));

            log.info("[ImageOptimization] Optimización completada: {} imágenes procesadas", totalProcessed);

        } catch (Exception e) {
            log.error("[ImageOptimization] Error durante la optimización", e);
            result.put("status", "error");
            result.put("message", "Error al optimizar imágenes: " + e.getMessage());
            result.put("totalProcessed", totalProcessed);
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Optimiza solo las imágenes de barberos.
     */
    @PostMapping("/barberos")
    public ResponseEntity<Map<String, Object>> optimizeBarberosOnly() {
        log.info("[ImageOptimization] Optimizando solo imágenes de barberos...");

        try {
            int processed = optimizeBarberosImages();
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("processed", processed);
            result.put("message", String.format("Se optimizaron %d imágenes de barberos", processed));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[ImageOptimization] Error optimizando barberos", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Optimiza solo las imágenes de sucursales.
     */
    @PostMapping("/sucursales")
    public ResponseEntity<Map<String, Object>> optimizeSucursalesOnly() {
        log.info("[ImageOptimization] Optimizando solo imágenes de sucursales...");

        try {
            int processed = optimizeSucursalesImages();
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("processed", processed);
            result.put("message", String.format("Se optimizaron %d imágenes de sucursales", processed));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[ImageOptimization] Error optimizando sucursales", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    private int optimizeBarberosImages() throws IOException {
        List<Barbero> barberos = barberoRepository.findAll();
        int processed = 0;

        for (Barbero barbero : barberos) {
            if (barbero.getFotoUrl() == null || barbero.getFotoUrl().isEmpty()) {
                continue;
            }

            try {
                optimizeExistingImage(barbero.getFotoUrl(), "barberos");
                processed++;
            } catch (Exception e) {
                log.error("[ImageOptimization] Error optimizando barbero {}: {}",
                         barbero.getId(), e.getMessage());
            }
        }

        return processed;
    }

    private int optimizeSucursalesImages() throws IOException {
        List<Sucursal> sucursales = sucursalRepository.findAll();
        int processed = 0;

        for (Sucursal sucursal : sucursales) {
            if (sucursal.getFotoUrl() == null || sucursal.getFotoUrl().isEmpty()) {
                continue;
            }

            try {
                optimizeExistingImage(sucursal.getFotoUrl(), "sucursales");
                processed++;
            } catch (Exception e) {
                log.error("[ImageOptimization] Error optimizando sucursal {}: {}",
                         sucursal.getId(), e.getMessage());
            }
        }

        return processed;
    }

    /**
     * Optimiza una imagen existente en el sistema de archivos.
     * Genera múltiples versiones WebP sin eliminar el original.
     */
    private void optimizeExistingImage(String imageUrl, String subfolder) throws IOException {
        // Convertir URL relativa a path físico
        // Ejemplo: "/uploads/barberos/barbero-1.jpg" -> "barberos/barbero-1.jpg"
        // El uploadDir ya contiene "uploads", así que lo quitamos de la URL
        String relativePath = imageUrl.replace("/uploads/", "");
        Path imagePath = Paths.get(uploadDir).resolve(relativePath).toAbsolutePath().normalize();

        if (!Files.exists(imagePath)) {
            log.warn("[ImageOptimization] Archivo no encontrado: {}", imagePath);
            return;
        }

        String filename = imagePath.getFileName().toString();

        // Si es una versión específica (-400, -800, -1200), saltar
        if (filename.matches(".*-(400|800|1200)\\.(jpg|jpeg|png)$")) {
            log.debug("[ImageOptimization] Es una versión específica: {}", filename);
            return;
        }

        log.info("[ImageOptimization] Optimizando: {}", imagePath);

        // Leer imagen original
        byte[] originalBytes = Files.readAllBytes(imagePath);

        // Crear un MultipartFile mock para reutilizar el servicio
        MockMultipartFile mockFile = new MockMultipartFile(originalBytes, filename);

        // Optimizar y generar versiones
        Map<String, byte[]> versions = imageOptimizationService.optimizeImage(mockFile);

        // Guardar versiones optimizadas
        Path folder = imagePath.getParent();
        String baseName = filename.substring(0, filename.lastIndexOf('.'));

        for (Map.Entry<String, byte[]> entry : versions.entrySet()) {
            String version = entry.getKey();
            byte[] imageBytes = entry.getValue();
            String versionFilename = imageOptimizationService.getVersionFilename(baseName + ".webp", version);
            Path target = folder.resolve(versionFilename);
            Files.write(target, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("[ImageOptimization] Guardado: {}", target.getFileName());
        }

        log.info("[ImageOptimization] Imagen optimizada con {} versiones: {}", versions.size(), filename);
    }

    /**
     * Clase auxiliar para simular MultipartFile desde bytes.
     */
    private static class MockMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final byte[] content;
        private final String name;

        public MockMultipartFile(byte[] content, String name) {
            this.content = content;
            this.name = name;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "image/jpeg"; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
