#!/bin/bash
# Script para generar un bundle con los archivos de una carpeta (sin subcarpetas)
# Uso: ./bundle_front.sh <carpeta> <archivo_salida>

set -e

if [ $# -ne 2 ]; then
  echo "Uso: $0 <carpeta> <archivo_salida>"
  exit 1
fi

CARPETA="$1"
SALIDA="$2"

# Borramos salida anterior si existe
rm -f "$SALIDA"

# Listamos solo archivos regulares de primer nivel
find "$CARPETA" -maxdepth 1 -type f | sort | while read archivo; do
  echo "========== FILE: $(basename "$archivo") ==========" >> "$SALIDA"
  cat "$archivo" >> "$SALIDA"
  echo -e "\n" >> "$SALIDA"
done

echo "✅ Bundle generado en: $SALIDA"

