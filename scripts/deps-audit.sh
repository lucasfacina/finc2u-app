#!/usr/bin/env bash
# Audita as dependências Maven do servidor (diretas e transitivas, todos os escopos) contra a base OSV.
# Complementa o alerta do IntelliJ (Mend.io): as duas bases divergem, confira as duas.
# Código de saída: 0 sem vulnerabilidades, 1 com vulnerabilidades, 2 falha na consulta.
#
# Uso: scripts/deps-audit.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp)"
trap 'rm -f -- "$tmp"' EXIT

(cd "$ROOT/apps/server" && ./mvnw -B -ntp -q dependency:list -DoutputFile="$tmp" -DappendOutput=false >/dev/null)

python3 -I "$ROOT/scripts/osv-audit.py" "$tmp"
