#!/usr/bin/env bash
# Exporta o histórico de commits do repositório, um por linha (`AAAA-MM-DD | mensagem`), do mais antigo ao mais
# recente. Serve de evidência da evolução do projeto (um item do checklist = um commit).
#
# Uso: scripts/history-export.sh [--output arquivo]
#   --output   destino (padrão: docs/historico_commits.txt; a pasta docs/ não é versionada)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

output="docs/historico_commits.txt"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) output="${2:?--output exige um valor}"; shift 2 ;;
    *) echo "Argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "Isto não é um repositório Git." >&2
  exit 1
fi

mkdir -p "$(dirname "$output")"
tmp="$output.partial"
trap 'rm -f -- "$tmp"' EXIT

git log --reverse --date=short --pretty=tformat:'%ad | %s' > "$tmp"
[[ -s "$tmp" ]] || { echo "O repositório ainda não tem commits." >&2; exit 1; }

mv -- "$tmp" "$output"
echo "Histórico exportado: $output ($(wc -l < "$output" | tr -d ' ') commits)"
