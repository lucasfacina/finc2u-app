#!/usr/bin/env bash
# Executa o Flyway CLI (imagem oficial, mesma versão gerenciada pelo Spring Boot) contra o PostgreSQL
# local do docker-compose.yml, usando as migrações de apps/server/src/main/resources/db/migration.
#
# Uso: scripts/flyway.sh <comando> [opções do Flyway]
#   ex.: scripts/flyway.sh info | validate | migrate | baseline | repair | clean
#
# O `clean` APAGA todos os objetos dos schemas (tabelas e dados). O Flyway o traz desativado; aqui ele só é
# liberado depois de confirmação: pergunta [y/N] num terminal interativo, ou `--yes` (obrigatório sem terminal).
#
# Variáveis:
#   FLYWAY_VERSION   versão da imagem (padrão: 12.4.0, a do BOM do Spring Boot 4.1.1)
#   MIGRATIONS_DIR   pasta das migrações (padrão: apps/server/src/main/resources/db/migration)
#
# Credenciais: lidas do .env (DB_NAME, DB_USERNAME, DB_PASSWORD), como o docker-compose.yml.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

assume_yes=false
args=()
for arg in "$@"; do
  if [[ "$arg" == "--yes" ]]; then assume_yes=true; else args+=("$arg"); fi
done

if [[ ${#args[@]} -eq 0 ]]; then
  echo "Informe o comando do Flyway. Uso: scripts/flyway.sh <info|validate|migrate|baseline|repair|clean|...>" >&2
  exit 2
fi

clean_env=()
if [[ "${args[0]}" == "clean" ]]; then
  if [[ "$assume_yes" != true ]]; then
    if [[ ! -t 0 ]]; then
      echo "Sem terminal interativo: confirme o clean com --yes." >&2
      exit 1
    fi
    echo "O clean apaga TODOS os objetos (tabelas e dados) dos schemas do banco local."
    read -r -p "Continuar? [y/N] " answer
    [[ "$answer" =~ ^[yYsS]$ ]] || { echo "Cancelado."; exit 1; }
  fi
  clean_env=(-e FLYWAY_CLEAN_DISABLED=false)
fi

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi
: "${DB_PASSWORD:?Defina DB_PASSWORD no .env (copie o .env.example)}"

FLYWAY_VERSION="${FLYWAY_VERSION:-12.4.0}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$ROOT/apps/server/src/main/resources/db/migration}"

if ! docker compose ps 2>/dev/null | grep -q "running"; then
  echo "O PostgreSQL local não está rodando. Suba com: nx run server:pgStart" >&2
  exit 1
fi

# O Docker cria a pasta de montagem como root se ela não existir; garante-a com o usuário atual.
mkdir -p "$MIGRATIONS_DIR"

# A imagem roda na rede do compose e alcança o banco (porta interna 5432) pelo IP do contêiner: o Apple
# `container` não resolve o nome por DNS. Se o IP não vier (outro runtime), usa o nome do contêiner.
db_host="$(docker inspect -f '{{.NetworkSettings.Networks.finc2u_default.IPAddress}}' finc2u-postgres 2>/dev/null || true)"
db_host="${db_host:-finc2u-postgres}"

exec docker run --rm \
  --network finc2u_default \
  -e FLYWAY_URL="jdbc:postgresql://${db_host}:5432/${DB_NAME:-finc2u}" \
  -e FLYWAY_USER="${DB_USERNAME:-postgres}" \
  -e FLYWAY_PASSWORD="$DB_PASSWORD" \
  -e FLYWAY_LOCATIONS="filesystem:/flyway/sql" \
  -e FLYWAY_CONNECT_RETRIES=5 \
  ${clean_env[@]+"${clean_env[@]}"} \
  -v "$MIGRATIONS_DIR:/flyway/sql" \
  "flyway/flyway:$FLYWAY_VERSION" "${args[@]}"
