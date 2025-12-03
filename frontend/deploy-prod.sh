#!/bin/bash

# Script de despliegue para producción - Frontend
set -e

echo "🚀 Iniciando despliegue de Cromados Frontend a producción..."

# Variables
VPS_HOST="vps-cromados"
DEPLOY_DIR="/var/www/cromados/html"
BACKUP_DIR="${DEPLOY_DIR}.backup"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 1. Verificar que estamos en el directorio correcto
if [ ! -f "package.json" ]; then
    echo "❌ Error: No se encontró package.json. Asegúrate de estar en el directorio frontend/"
    exit 1
fi

# 2. Build del proyecto
echo "📦 Construyendo proyecto para producción..."
if ! pnpm run build; then
    echo "❌ Error al compilar el proyecto"
    exit 1
fi

# Verificar que el build se creó correctamente
if [ ! -d "dist" ] || [ -z "$(ls -A dist)" ]; then
    echo "❌ Error: El directorio dist está vacío o no existe"
    exit 1
fi

echo "✅ Build completado exitosamente"

# 3. Comprimir archivos
echo "📦 Comprimiendo archivos..."
cd dist
if ! tar -czf ../frontend-dist.tar.gz *; then
    echo "❌ Error al comprimir archivos"
    exit 1
fi
cd ..

# 4. Hacer backup del frontend actual en el servidor
echo "💾 Creando backup del frontend actual..."
ssh ${VPS_HOST} "[ -d ${DEPLOY_DIR} ] && [ ! -z \"\$(ls -A ${DEPLOY_DIR})\" ] && cp -r ${DEPLOY_DIR} ${BACKUP_DIR}_${TIMESTAMP} || true"

# 5. Copiar al servidor
echo "📤 Copiando archivos al servidor..."
if ! scp frontend-dist.tar.gz ${VPS_HOST}:/tmp/; then
    echo "❌ Error al copiar archivos al servidor"
    rm frontend-dist.tar.gz
    exit 1
fi

# 6. Descomprimir en el servidor
echo "📂 Desplegando archivos..."
if ! ssh ${VPS_HOST} "cd ${DEPLOY_DIR} && rm -rf * && tar -xzf /tmp/frontend-dist.tar.gz && rm /tmp/frontend-dist.tar.gz"; then
    echo "❌ Error al descomprimir archivos en el servidor"
    echo "🔄 Restaurando versión anterior..."
    ssh ${VPS_HOST} "[ -d ${BACKUP_DIR}_${TIMESTAMP} ] && rm -rf ${DEPLOY_DIR}/* && cp -r ${BACKUP_DIR}_${TIMESTAMP}/* ${DEPLOY_DIR}/"
    rm frontend-dist.tar.gz
    exit 1
fi

# 7. Verificar que los archivos se desplegaron correctamente
echo "✅ Verificando archivos desplegados..."
if ssh ${VPS_HOST} "[ -f ${DEPLOY_DIR}/index.html ]"; then
    echo "✅ Archivos desplegados correctamente!"
    echo "🗑️  Eliminando backup..."
    ssh ${VPS_HOST} "rm -rf ${BACKUP_DIR}_${TIMESTAMP}"
else
    echo "❌ Error: No se encontró index.html en el servidor"
    echo "🔄 Restaurando versión anterior..."
    ssh ${VPS_HOST} "[ -d ${BACKUP_DIR}_${TIMESTAMP} ] && rm -rf ${DEPLOY_DIR}/* && cp -r ${BACKUP_DIR}_${TIMESTAMP}/* ${DEPLOY_DIR}/"
    rm frontend-dist.tar.gz
    exit 1
fi

# 8. Limpiar archivos temporales locales
echo "🧹 Limpiando archivos temporales..."
rm frontend-dist.tar.gz

echo ""
echo "═══════════════════════════════════════════"
echo "✅ DESPLIEGUE COMPLETADO EXITOSAMENTE"
echo "═══════════════════════════════════════════"
echo "🌐 Frontend: https://cromados.uno"
echo "📊 Para verificar: curl -I https://cromados.uno"
echo ""
echo "⚠️  IMPORTANTE: Los archivos deben estar en /var/www/cromados/html/"
echo "   (NO en /var/www/cromados/ directamente)"
