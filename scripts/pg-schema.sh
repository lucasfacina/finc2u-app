#!/usr/bin/env bash
# Gera o rascunho de uma migração Flyway (só o schema, sem dados) a partir do PostgreSQL local.
# Fluxo da Fase 14: suba a aplicação com Hibernate (ddl-auto) contra o Postgres, estabilize o schema e
# gere a V1 a partir dele. Revise o resultado à mão antes de commitar.
#
# Uso: scripts/pg-schema.sh [--output arquivo] [--force]
#   --output   destino (padrão: apps/server/src/main/resources/db/migration/V1__baseline.sql)
#   --force    sobrescreve o destino se já existir
#
# Atua apenas no contêiner local do docker-compose.yml.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

output="apps/server/src/main/resources/db/migration/V1__baseline.sql"
force=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) output="${2:?--output exige um valor}"; shift 2 ;;
    --force) force=true; shift ;;
    *) echo "Argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

if [[ -e "$output" && "$force" != true ]]; then
  echo "O destino já existe: $output (use --force para sobrescrever)." >&2
  exit 1
fi

if ! docker compose ps 2>/dev/null | grep -q "running"; then
  echo "O PostgreSQL local não está rodando. Suba com: nx run server:pgStart" >&2
  exit 1
fi

mkdir -p "$(dirname "$output")"
tmp="$output.partial"
trap 'rm -f -- "$tmp"' EXIT

# Sem donos/privilégios/comentários e sem a tabela de histórico do próprio Flyway. O pg_dump também emite
# linhas de sessão (SET, set_config) e, nas versões recentes, `\restrict`/`\unrestrict` (comandos do psql que
# o Flyway não entende): ficam de fora.
docker compose exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --schema-only --no-owner --no-privileges --no-comments --exclude-table=flyway_schema_history' \
  | grep -v -E '^(SET |SELECT pg_catalog\.set_config|\\restrict |\\unrestrict )' \
  | cat -s > "$tmp"

grep -q "CREATE TABLE" "$tmp" || { echo "Nenhuma tabela encontrada no banco: nada a gerar." >&2; exit 1; }

mv -- "$tmp" "$output"
echo "Rascunho gerado: $output"
echo "Revise antes de commitar (tipos, índices, nomes de constraints)."
