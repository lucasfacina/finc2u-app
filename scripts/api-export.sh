#!/usr/bin/env bash
# Exporta a especificação OpenAPI da aplicação em execução (springdoc, /v3/api-docs) para um arquivo JSON
# formatado, base para gerar o cliente do frontend (apps/client) e para acompanhar mudanças no contrato da API
# pelo diff do Git.
#
# Uso: scripts/api-export.sh [--output arquivo] [--url endereço]
#   --output   destino (padrão: apps/server/openapi.json)
#   --url      endereço da especificação (padrão: http://localhost:${SERVER_PORT:-8080}/v3/api-docs)
#
# Requer a aplicação rodando (nx run server:dev).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

output="apps/server/openapi.json"
url="http://localhost:${SERVER_PORT:-8080}/v3/api-docs"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) output="${2:?--output exige um valor}"; shift 2 ;;
    --url) url="${2:?--url exige um valor}"; shift 2 ;;
    *) echo "Argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

tmp="$(mktemp)"
trap 'rm -f -- "$tmp"' EXIT

if ! curl -fsS --max-time 30 "$url" -o "$tmp" 2>/dev/null; then
  echo "Não foi possível obter $url. A aplicação está rodando? (nx run server:dev)" >&2
  exit 1
fi

mkdir -p "$(dirname "$output")"
python3 -I -c 'import json, sys; json.dump(json.load(sys.stdin), sys.stdout, indent=2, ensure_ascii=False); print()' \
  < "$tmp" > "$output"
echo "Especificação exportada: $output ($(python3 -I -c 'import json, sys; print(len(json.load(open(sys.argv[1]))["paths"]))' "$output") caminhos)"
