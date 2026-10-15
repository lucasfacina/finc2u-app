#!/usr/bin/env bash
# Abre o `psql` no contêiner do PostgreSQL local, já autenticado com o usuário e o banco do .env.
# Os argumentos são repassados ao `psql` (ex.: -c "select 1", -f arquivo.sql).
#
# Uso: scripts/pg-psql.sh [argumentos do psql]
#
# Com terminal interativo abre a sessão normal; sem terminal (pipe, CI, Nx sem TTY) desliga a
# alocação de TTY (-T), que senão faz o `exec` falhar.

set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

tty_flags=()
[[ -t 0 ]] || tty_flags+=(-T)

exec docker compose exec ${tty_flags[@]+"${tty_flags[@]}"} postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh "$@"
