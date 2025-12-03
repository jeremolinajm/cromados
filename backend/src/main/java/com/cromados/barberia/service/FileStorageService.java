package com.cromados.barberia.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static String cleanExt(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        if (ext == null) return "jpg";
        ext = ext.toLowerCase();
        return switch (ext) {
            case "jpeg", "jpg" -> "jpg";
            case "png" -> "png";
            default -> "jpg";
        };
    }

    private Path ensureDir(String subfolder) throws IOException {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path folder = root.resolve(subfolder).normalize();
        Files.createDirectories(folder);
        return folder;
    }

    public String saveBarberoPhoto(MultipartFile file, Long barberoId) throws IOException {
        String ext = cleanExt(file.getOriginalFilename());
        Path folder = ensureDir("barberos");
        String name = "barbero-" + barberoId + "-" + System.currentTimeMillis() + "." + ext;
        Path target = folder.resolve(name);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String rel = "/uploads/barberos/" + name;
        log.info("[FileStorage] Barbero {} -> {}", barberoId, target);
        return rel;
    }

    public String saveSucursalPhoto(MultipartFile file, Long sucursalId) throws IOException {
        String ext = cleanExt(file.getOriginalFilename());
        Path folder = ensureDir("sucursales");
        String name = "sucursal-" + sucursalId + "-" + System.currentTimeMillis() + "." + ext;
        Path target = folder.resolve(name);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String rel = "/uploads/sucursales/" + name;
        log.info("[FileStorage] Sucursal {} -> {}", sucursalId, target);
        return rel;
    }
}
