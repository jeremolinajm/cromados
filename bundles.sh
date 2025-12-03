#!/bin/bash
# Genera backend_bundle.txt y frontend_bundle.txt desde la raíz del repo.
set -Eeuo pipefail

ROOT="$(pwd)"
BACK_OUT="$ROOT/backend_bundle.txt"
FRONT_OUT="$ROOT/frontend_bundle.txt"

# Verificaciones rápidas
[[ -d "$ROOT/backend" ]] || { echo "No existe $ROOT/backend"; exit 1; }
[[ -d "$ROOT/frontend" ]] || { echo "No existe $ROOT/frontend"; exit 1; }
[[ -d "$ROOT/frontend/src" ]] || { echo "No existe $ROOT/frontend/src"; exit 1; }

# Limpiar salidas previas
rm -f "$BACK_OUT" "$FRONT_OUT"

echo "Generando backend_bundle.txt ..."
(
  cd "$ROOT/backend/"
  # Excluir target/.git y manejar nombres con espacios
  find . -type f ! -path "*/target/maven-*" ! -path "*/target/generated-*" ! -path "*/target/*classes" ! -path "*/.idea/*" ! -path "*/.mvn/*" ! -path "*/.git/*" -print0 \
  | sort -z \
  | while IFS= read -r -d '' file; do
      {
        printf '========== FILE: %s ==========\n' "$file"
        cat "$file"
        printf '\n\n'
      } >> "$BACK_OUT"
    done
)

echo "Generando frontend_bundle.txt ..."
(
  cd "$ROOT/frontend/src"
  # Excluir node_modules por si existiera debajo de src (raro, pero seguro)
  find . -type f ! -path "*/node_modules/*" -print0 \
  | sort -z \
  | while IFS= read -r -d '' file; do
      {
        printf '========== FILE: %s ==========\n' "$file"
        cat "$file"
        printf '\n\n'
      } >> "$FRONT_OUT"
    done
)

echo "✅ Bundles generados:"
ls -lh "$BACK_OUT" "$FRONT_OUT"

