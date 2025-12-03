package com.cromados.barberia.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio de optimización de imágenes.
 * Genera múltiples versiones de una imagen en formato JPG optimizado.
 */
@Service
@Slf4j
public class ImageOptimizationService {

    // Tamaños de imagen para srcset responsivo
    private static final int SIZE_THUMBNAIL = 400;  // Para previews y móviles
    private static final int SIZE_MEDIUM = 800;     // Para tablets y móviles grandes
    private static final int SIZE_LARGE = 1200;     // Para desktop

    // Calidad de compresión JPG (0.0 - 1.0)
    // 0.85 es el sweet spot: buena calidad visual con gran reducción de tamaño (50-70%)
    private static final double JPG_QUALITY = 0.85;

    /**
     * Optimiza una imagen y genera múltiples versiones.
     *
     * @param file Archivo MultipartFile original
     * @return Map con las versiones optimizadas (thumbnail, medium, large, original)
     * @throws IOException Si hay error al procesar la imagen
     */
    public Map<String, byte[]> optimizeImage(MultipartFile file) throws IOException {
        Map<String, byte[]> versions = new HashMap<>();

        log.info("[ImageOptimization] Procesando imagen: {} ({} bytes)",
                 file.getOriginalFilename(), file.getSize());

        // Leer imagen original
        byte[] originalBytes = file.getBytes();

        // Generar versión thumbnail (400px)
        byte[] thumbnail = resizeAndOptimize(originalBytes, SIZE_THUMBNAIL);
        versions.put("thumbnail", thumbnail);
        log.debug("[ImageOptimization] Thumbnail generado: {} bytes", thumbnail.length);

        // Generar versión medium (800px)
        byte[] medium = resizeAndOptimize(originalBytes, SIZE_MEDIUM);
        versions.put("medium", medium);
        log.debug("[ImageOptimization] Medium generado: {} bytes", medium.length);

        // Generar versión large (1200px)
        byte[] large = resizeAndOptimize(originalBytes, SIZE_LARGE);
        versions.put("large", large);
        log.debug("[ImageOptimization] Large generado: {} bytes", large.length);

        // Generar versión original optimizada (sin resize, solo compresión WebP)
        byte[] optimizedOriginal = optimizeWithoutResize(originalBytes);
        versions.put("original", optimizedOriginal);
        log.debug("[ImageOptimization] Original optimizado: {} bytes", optimizedOriginal.length);

        long totalOriginal = file.getSize();
        long totalOptimized = thumbnail.length + medium.length + large.length + optimizedOriginal.length;
        double reduction = ((totalOriginal - optimizedOriginal.length) / (double) totalOriginal) * 100;

        log.info("[ImageOptimization] Optimización completada. Original: {} KB, Optimizado: {} KB, Reducción: {:.1f}%",
                 totalOriginal / 1024, optimizedOriginal.length / 1024, reduction);

        return versions;
    }

    /**
     * Redimensiona y optimiza una imagen a un tamaño específico.
     * Mantiene el aspect ratio y comprime a JPG optimizado.
     */
    private byte[] resizeAndOptimize(byte[] imageBytes, int maxSize) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Thumbnails.of(input)
                .size(maxSize, maxSize)          // Tamaño máximo (mantiene aspect ratio)
                .outputFormat("jpg")             // Formato JPG
                .outputQuality(JPG_QUALITY)      // Calidad 85%
                .toOutputStream(output);

            return output.toByteArray();
        }
    }

    /**
     * Optimiza una imagen sin redimensionar, solo comprime a JPG optimizado.
     */
    private byte[] optimizeWithoutResize(byte[] imageBytes) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Thumbnails.of(input)
                .scale(1.0)                      // Sin redimensionar
                .outputFormat("jpg")             // Formato JPG
                .outputQuality(JPG_QUALITY)      // Calidad 85%
                .toOutputStream(output);

            return output.toByteArray();
        }
    }

    /**
     * Obtiene el nombre de archivo con el sufijo de tamaño.
     * Ejemplo: "barbero-1-123456.jpg" -> "barbero-1-123456-400.jpg"
     */
    public String getVersionFilename(String baseFilename, String version) {
        // Remover extensión original
        String nameWithoutExt = baseFilename.substring(0, baseFilename.lastIndexOf('.'));

        return switch (version) {
            case "thumbnail" -> nameWithoutExt + "-400.jpg";
            case "medium" -> nameWithoutExt + "-800.jpg";
            case "large" -> nameWithoutExt + "-1200.jpg";
            case "original" -> nameWithoutExt + ".jpg";
            default -> throw new IllegalArgumentException("Version desconocida: " + version);
        };
    }

    /**
     * Obtiene el tamaño en píxeles de una versión.
     */
    public int getSizeForVersion(String version) {
        return switch (version) {
            case "thumbnail" -> SIZE_THUMBNAIL;
            case "medium" -> SIZE_MEDIUM;
            case "large" -> SIZE_LARGE;
            default -> throw new IllegalArgumentException("Version desconocida: " + version);
        };
    }
}
