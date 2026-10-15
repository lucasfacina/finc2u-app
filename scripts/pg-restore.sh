#!/usr/bin/env bash
# Restaura um backup (formato custom do pg_dump) no PostgreSQL local, SUBSTITUINDO o banco atual
# (ele é recriado). Antes de sobrescrever, gera um backup de segurança (backups/pre-restore-*.dump).
#
# Uso: scripts/pg-restore.sh <arquivo.dump | latest> [--yes]
#   latest   usa o backup mais recente de backups/
#   --yes    não pede confirmação (obrigatório fora de um terminal interativo)
#
# Atua apenas no contêiner local do docker-compose.yml.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

source_file=""
assume_yes=false
for arg in "$@"; do
  case "$arg" in
    --yes) assume_yes=true ;;
    -*) echo "Argumento desconhecido: $arg" >&2; exit 2 ;;
    *) source_file="$arg" ;;
  esac
done

if [[ -z "$source_file" ]]; then
  echo "Informe o arquivo de backup (ou 'latest'). Uso: scripts/pg-restore.sh <arquivo.dump | latest> [--yes]" >&2
  exit 2
fi

if [[ "$source_file" == "latest" ]]; then
  source_file="$(ls -t backups/*.dump 2>/dev/null | head -n 1 || true)"
  [[ -n "$source_file" ]] || { echo "Nenhum backup encontrado em backups/." >&2; exit 1; }
fi
[[ -f "$source_file" ]] || { echo "Arquivo não encontrado: $source_file" >&2; exit 1; }

if ! docker compose ps 2>/dev/null | grep -q "running"; then
  echo "O PostgreSQL local não está rodando. Suba com: nx run server:pgStart" >&2
  exit 1
fi

echo "Backup a restaurar: $source_file"
if [[ "$assume_yes" != true ]]; then
  if [[ ! -t 0 ]]; then
    echo "Sem terminal interativo: confirme com --yes." >&2
    exit 1
  fi
  read -r -p "Isto SUBSTITUI o conteúdo atual do banco local. Continuar? [y/N] " answer
  [[ "$answer" =~ ^[yYsS]$ ]] || { echo "Cancelado."; exit 1; }
fi

./scripts/pg-backup.sh --label pre-restore

# Recria o banco (derruba conexões abertas) para que o resultado seja exatamente o do backup: o
# `pg_restore --clean` só remove objetos presentes no dump e deixaria para trás o que foi criado depois.
docker compose exec -T postgres sh -c \
  'dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB" \
    && createdb -U "$POSTGRES_USER" "$POSTGRES_DB" \
    && pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges --exit-on-error' \
  < "$source_file"
echo "Backup restaurado: $source_file"
