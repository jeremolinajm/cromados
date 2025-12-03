#!/bin/bash
# Script para optimizar imágenes existentes en la VPS
# Ejecutar desde la VPS: ssh vps-cromados '/var/www/cromados/backend/optimize-existing-images.sh'

set -e

echo "🖼️  Optimizando imágenes existentes en el servidor..."

# Directorio de imágenes
BACKEND_DIR="/var/www/cromados/backend"
UPLOADS_DIR="$BACKEND_DIR/uploads"

# Verificar que existe el JAR
if [ ! -f "$BACKEND_DIR/cromados-backend.jar" ]; then
    echo "❌ Error: No se encontró el JAR en $BACKEND_DIR"
    exit 1
fi

# Crear clase temporal de Java para optimizar imágenes
cat > /tmp/OptimizeImages.java << 'EOF'
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import java.awt.image.*;

public class OptimizeImages {
    public static void main(String[] args) throws Exception {
        String uploadsDir = args.length > 0 ? args[0] : "/var/www/cromados/backend/uploads";

        System.out.println("Buscando imágenes en: " + uploadsDir);

        int processed = 0;

        // Procesar barberos
        File barberosDir = new File(uploadsDir, "barberos");
        if (barberosDir.exists()) {
            processed += processDirectory(barberosDir);
        }

        // Procesar sucursales
        File sucursalesDir = new File(uploadsDir, "sucursales");
        if (sucursalesDir.exists()) {
            processed += processDirectory(sucursalesDir);
        }

        System.out.println("\n✅ Total de imágenes procesadas: " + processed);
    }

    static int processDirectory(File dir) throws Exception {
        int count = 0;
        File[] files = dir.listFiles((d, name) ->
            name.toLowerCase().matches(".*\\.(jpg|jpeg|png)$") &&
            !name.matches(".*-(400|800|1200)\\..*")
        );

        if (files == null) return 0;

        for (File file : files) {
            System.out.println("Procesando: " + file.getName());
            count++;
        }

        return count;
    }
}
EOF

echo ""
echo "⚠️  IMPORTANTE: Para optimizar las imágenes, necesitas:"
echo "1. Usar el endpoint HTTP con token de admin, O"
echo "2. Volver a subir las imágenes desde el panel admin"
echo ""
echo "Las nuevas imágenes que subas se optimizarán automáticamente."
echo ""

# Contar imágenes actuales
echo "📊 Imágenes actuales en el servidor:"
if [ -d "$UPLOADS_DIR/barberos" ]; then
    BARBEROS_COUNT=$(find "$UPLOADS_DIR/barberos" -type f \( -name "*.jpg" -o -name "*.jpeg" -o -name "*.png" \) ! -name "*-400.*" ! -name "*-800.*" ! -name "*-1200.*" 2>/dev/null | wc -l)
    echo "  Barberos: $BARBEROS_COUNT imágenes"
fi

if [ -d "$UPLOADS_DIR/sucursales" ]; then
    SUCURSALES_COUNT=$(find "$UPLOADS_DIR/sucursales" -type f \( -name "*.jpg" -o -name "*.jpeg" -o -name "*.png" \) ! -name "*-400.*" ! -name "*-800.*" ! -name "*-1200.*" 2>/dev/null | wc -l)
    echo "  Sucursales: $SUCURSALES_COUNT imágenes"
fi

rm -f /tmp/OptimizeImages.java
