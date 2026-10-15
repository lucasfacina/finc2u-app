#!/usr/bin/env bash
# Recria o PostgreSQL local (contêiner do docker-compose.yml): remove o contêiner e o volume de dados
# (`down -v`) e sobe um banco vazio. Quem recria o schema e popula os dados é a aplicação (Flyway e
# seeders, conforme o perfil).
#
# Uso: scripts/pg-reset.sh [--yes]
#   --yes   não pede confirmação (obrigatório fora de um terminal interativo)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

assume_yes=false
for arg in "$@"; do
  case "$arg" in
    --yes) assume_yes=true ;;
    *) echo "Argumento desconhecido: $arg" >&2; exit 2 ;;
  esac
done

if [[ "$assume_yes" != true ]]; then
  if [[ ! -t 0 ]]; then
    echo "Sem terminal interativo: confirme com --yes." >&2
    exit 1
  fi
  echo "Isto remove o contêiner e o volume de dados do PostgreSQL local (projeto finc2u)."
  read -r -p "Apagar TODOS os dados do banco local? [y/N] " answer
  [[ "$answer" =~ ^[yYsS]$ ]] || { echo "Cancelado."; exit 1; }
fi

docker compose down -v
docker compose up -d
echo "PostgreSQL recriado (vazio). Acompanhe com: nx run server:db:pg:logs"
