#!/usr/bin/env bash
# Lista atualizações disponíveis das dependências Maven do servidor: parent (Spring Boot), dependências
# diretas e propriedades de versão. Só informa — não altera o pom.xml. Versões alpha/beta/RC/milestone/
# snapshot são ignoradas. As bibliotecas gerenciadas pelo BOM do Spring Boot sobem junto com o parent.
#
# Uso: scripts/deps-updates.sh
#
# Variáveis:
#   VERSIONS_PLUGIN   versão do versions-maven-plugin (padrão: 2.23.0)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/apps/server"

plugin="org.codehaus.mojo:versions-maven-plugin:${VERSIONS_PLUGIN:-2.23.0}"

./mvnw -B -ntp "$plugin:display-parent-updates" "$plugin:display-dependency-updates" \
  "$plugin:display-property-updates" -DprocessDependencyManagement=false \
  '-Dmaven.version.ignore=(?i).*(alpha|beta|rc|milestone|snapshot|[.-]m\d+).*'
