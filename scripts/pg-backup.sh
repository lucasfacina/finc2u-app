#!/usr/bin/env bash
# Gera um backup (pg_dump, formato custom) do PostgreSQL local em backups/ (ignorada pelo Git).
#
# Uso: scripts/pg-backup.sh [--label nome]
#   --label   prefixo do arquivo (padrão: finc2u) -> backups/<label>-AAAAMMDD-HHMMSS.dump
#
# Atua apenas no contêiner local do docker-compose.yml.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

label="finc2u"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --label) label="${2:?--label exige um valor}"; shift 2 ;;
    *) echo "Argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

if ! docker compose ps 2>/dev/null | grep -q "running"; then
  echo "O PostgreSQL local não está rodando. Suba com: nx run server:pgStart" >&2
  exit 1
fi

mkdir -p backups
file="backups/${label}-$(date +%Y%m%d-%H%M%S).dump"
tmp="$file.partial"
trap 'rm -f -- "$tmp"' EXIT

docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$tmp"
[[ -s "$tmp" ]] || { echo "Backup vazio: algo deu errado no pg_dump." >&2; exit 1; }

mv -- "$tmp" "$file"
echo "Backup criado: $file ($(du -h "$file" | cut -f1))"
