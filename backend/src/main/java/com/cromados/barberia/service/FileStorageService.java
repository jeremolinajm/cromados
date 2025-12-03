package com.cromados.barberia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final ImageOptimizationService imageOptimizationService;

    private Path ensureDir(String subfolder) throws IOException {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path folder = root.resolve(subfolder).normalize();
        Files.createDirectories(folder);
        return folder;
    }

    /**
     * Guarda la foto de un barbero con optimización automática.
     * Genera múltiples versiones (thumbnail, medium, large, original) en formato WebP.
     *
     * @param file Archivo de imagen original
     * @param barberoId ID del barbero
     * @return URL relativa de la imagen optimizada (versión original)
     */
    public String saveBarberoPhoto(MultipartFile file, Long barberoId) throws IOException {
        Path folder = ensureDir("barberos");
        String baseName = "barbero-" + barberoId + "-" + System.currentTimeMillis();

        // Optimizar imagen y generar múltiples versiones
        Map<String, byte[]> versions = imageOptimizationService.optimizeImage(file);

        // Guardar todas las versiones
        for (Map.Entry<String, byte[]> entry : versions.entrySet()) {
            String version = entry.getKey();
            byte[] imageBytes = entry.getValue();
            String filename = imageOptimizationService.getVersionFilename(baseName + ".webp", version);
            Path target = folder.resolve(filename);
            Files.write(target, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("[FileStorage] Barbero {} {} -> {}", barberoId, version, target.getFileName());
        }

        // Retornar URL de la versión original optimizada
        String originalFilename = imageOptimizationService.getVersionFilename(baseName + ".webp", "original");
        String rel = "/uploads/barberos/" + originalFilename;
        log.info("[FileStorage] Barbero {} guardado con {} versiones -> {}",
                 barberoId, versions.size(), rel);
        return rel;
    }

    /**
     * Guarda la foto de una sucursal con optimización automática.
     * Genera múltiples versiones (thumbnail, medium, large, original) en formato WebP.
     *
     * @param file Archivo de imagen original
     * @param sucursalId ID de la sucursal
     * @return URL relativa de la imagen optimizada (versión original)
     */
    public String saveSucursalPhoto(MultipartFile file, Long sucursalId) throws IOException {
        Path folder = ensureDir("sucursales");
        String baseName = "sucursal-" + sucursalId + "-" + System.currentTimeMillis();

        // Optimizar imagen y generar múltiples versiones
        Map<String, byte[]> versions = imageOptimizationService.optimizeImage(file);

        // Guardar todas las versiones
        for (Map.Entry<String, byte[]> entry : versions.entrySet()) {
            String version = entry.getKey();
            byte[] imageBytes = entry.getValue();
            String filename = imageOptimizationService.getVersionFilename(baseName + ".webp", version);
            Path target = folder.resolve(filename);
            Files.write(target, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("[FileStorage] Sucursal {} {} -> {}", sucursalId, version, target.getFileName());
        }

        // Retornar URL de la versión original optimizada
        String originalFilename = imageOptimizationService.getVersionFilename(baseName + ".webp", "original");
        String rel = "/uploads/sucursales/" + originalFilename;
        log.info("[FileStorage] Sucursal {} guardada con {} versiones -> {}",
                 sucursalId, versions.size(), rel);
        return rel;
    }
}
