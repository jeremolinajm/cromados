#!/bin/bash
# Script para generar un bundle con todos los archivos .java
# Uso: ./bundle_java.sh <carpeta> <salida.txt>

set -e

if [ $# -ne 2 ]; then
  echo "Uso: $0 <carpeta_con_java> <archivo_salida>"
  exit 1
fi

CARPETA="$1"
SALIDA="$2"

# Borramos salida anterior si existe
rm -f "$SALIDA"

# Buscamos todos los .java, ordenados alfabéticamente
find "$CARPETA" -type f -name "*.java" | sort | while read archivo; do
  echo "========== FILE: $archivo ==========" >> "$SALIDA"
  cat "$archivo" >> "$SALIDA"
  echo -e "\n" >> "$SALIDA"
done

echo "✅ Bundle generado en: $SALIDA"

