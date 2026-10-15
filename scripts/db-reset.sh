#!/usr/bin/env bash
# Apaga o banco H2 de desenvolvimento (arquivos finc2udb*). Na próxima subida, o Hibernate recria
# o schema (ddl-auto: update) e os seeders repopulam os dados.
#
# Uso: scripts/db-reset.sh [--yes] [--dry-run]
#   --yes       não pede confirmação (obrigatório fora de um terminal interativo)
#   --dry-run   apenas lista o que seria apagado
#
# Variáveis:
#   SERVER_PORT   porta da aplicação a checar antes de apagar (padrão: 8080)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_NAME="finc2udb"
PORT="${SERVER_PORT:-8080}"
# `./data/` é relativo ao diretório de execução: raiz (IDE e `nx run server:dev`) ou apps/server (`mvnw` direto).
DATA_DIRS=("$ROOT/data" "$ROOT/apps/server/data")

assume_yes=false
dry_run=false
for arg in "$@"; do
  case "$arg" in
    --yes) assume_yes=true ;;
    --dry-run) dry_run=true ;;
    *) echo "Argumento desconhecido: $arg" >&2; exit 2 ;;
  esac
done

files=()
for dir in "${DATA_DIRS[@]}"; do
  [[ -d "$dir" ]] || continue
  for file in "$dir/$DB_NAME".*; do
    [[ -e "$file" ]] && files+=("$file")
  done
done

if [[ ${#files[@]} -eq 0 ]]; then
  echo "Nenhum banco H2 encontrado. Nada a fazer."
  exit 0
fi

echo "Arquivos do banco H2:"
printf '  %s\n' "${files[@]#"$ROOT/"}"

if [[ "$dry_run" == true ]]; then
  echo "(dry-run) nada foi apagado."
  exit 0
fi

if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Há um processo escutando na porta $PORT (a aplicação parece estar rodando)." >&2
  echo "Pare-a antes de apagar o banco (ou ajuste SERVER_PORT)." >&2
  exit 1
fi

if [[ "$assume_yes" != true ]]; then
  if [[ ! -t 0 ]]; then
    echo "Sem terminal interativo: confirme com --yes." >&2
    exit 1
  fi
  read -r -p "Apagar o banco de desenvolvimento? [y/N] " answer
  [[ "$answer" =~ ^[yYsS]$ ]] || { echo "Cancelado."; exit 1; }
fi

rm -f -- "${files[@]}"
for dir in "${DATA_DIRS[@]}"; do
  rmdir "$dir" 2>/dev/null || true
done
echo "Banco H2 apagado. Suba a aplicação para recriá-lo e repopulá-lo."
