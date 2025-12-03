#!/bin/bash

# Script de despliegue para producción - Backend
set -e

echo "🚀 Iniciando despliegue de Cromados Backend a producción..."

# Variables
VPS_HOST="vps-cromados"
VPS_USER="jere"
DEPLOY_DIR="/var/www/cromados/backend"
JAR_NAME="cromados-backend.jar"
BACKUP_JAR="${JAR_NAME}.backup"

# 1. Build del proyecto
echo "📦 Construyendo proyecto..."
if ! mvn clean package -DskipTests; then
    echo "❌ Error al compilar el proyecto"
    exit 1
fi

# Verificar que el JAR se creó correctamente
if [ ! -f "target/cromados-backend-0.0.1-SNAPSHOT.jar" ]; then
    echo "❌ Error: No se encontró el JAR compilado"
    exit 1
fi

echo "✅ Compilación exitosa"

# 2. Hacer backup del JAR actual en el servidor
echo "💾 Creando backup del JAR actual..."
ssh ${VPS_HOST} "[ -f ${DEPLOY_DIR}/${JAR_NAME} ] && cp ${DEPLOY_DIR}/${JAR_NAME} ${DEPLOY_DIR}/${BACKUP_JAR} || true"

# 3. Copiar JAR al servidor (incluye application.properties embebido)
echo "📤 Copiando JAR al servidor..."
if ! scp target/cromados-backend-0.0.1-SNAPSHOT.jar ${VPS_HOST}:${DEPLOY_DIR}/${JAR_NAME}; then
    echo "❌ Error al copiar el JAR al servidor"
    exit 1
fi

# 4. Reiniciar servicio
echo "🔄 Reiniciando servicio..."
if ! ssh ${VPS_HOST} "sudo systemctl restart cromados-backend"; then
    echo "❌ Error al reiniciar el servicio"
    exit 1
fi

# 5. Verificar que el servicio arrancó correctamente
echo "✅ Verificando que el servicio arrancó..."
sleep 5

if ssh ${VPS_HOST} "sudo systemctl is-active --quiet cromados-backend"; then
    echo "✅ Servicio iniciado correctamente!"
    echo "🗑️  Eliminando backup..."
    ssh ${VPS_HOST} "rm -f ${DEPLOY_DIR}/${BACKUP_JAR}"
    echo ""
    echo "═══════════════════════════════════════════"
    echo "✅ DESPLIEGUE COMPLETADO EXITOSAMENTE"
    echo "═══════════════════════════════════════════"
    echo "🌐 Backend: https://api.cromados.uno"
    echo "📊 Ver logs: ssh ${VPS_HOST} 'sudo journalctl -u cromados-backend -f'"
else
    echo ""
    echo "═══════════════════════════════════════════"
    echo "❌ ERROR: El servicio no pudo iniciarse"
    echo "═══════════════════════════════════════════"
    echo "🔄 Restaurando versión anterior..."
    ssh ${VPS_HOST} "[ -f ${DEPLOY_DIR}/${BACKUP_JAR} ] && cp ${DEPLOY_DIR}/${BACKUP_JAR} ${DEPLOY_DIR}/${JAR_NAME} && sudo systemctl restart cromados-backend"
    echo ""
    echo "📊 Últimos 50 logs del error:"
    echo "───────────────────────────────────────────"
    ssh ${VPS_HOST} "sudo journalctl -u cromados-backend -n 50 --no-pager"
    exit 1
fi
