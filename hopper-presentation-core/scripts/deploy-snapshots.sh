#!/usr/bin/env bash
# Deploy org.hopper snapshot modules to Nexus "hopper" repository.
# Requires ~/.m2/settings.xml server id "hopper" with valid credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOPPER_NEXUS_URL="${HOPPER_NEXUS_URL:-https://repository.data-hopper.com/repository/hopper/}"

echo "Deploying org.hopper snapshots to ${HOPPER_NEXUS_URL}"
echo "Maven server id expected in settings.xml: hopper"
echo

deploy_module() {
  local dir="$1"
  shift
  echo "======== $(basename "$dir") ========"
  (cd "$dir" && mvn -B clean deploy -Dhopper.nexus.url="${HOPPER_NEXUS_URL}" "$@")
  echo
}

# Monorepo modules (sibling hop plugins if present under the parent checkout)
deploy_module "${ROOT}/hopper-presentation-core"
if [[ -d "${ROOT}/../hopper-hop-plugins" ]]; then
  deploy_module "${ROOT}/../hopper-hop-plugins" -Dmaven.test.skip=false
fi
if [[ -d "${ROOT}/../hop-hopper-plugins" ]]; then
  deploy_module "${ROOT}/../hop-hopper-plugins" -Dmaven.test.skip=true
fi
deploy_module "${ROOT}/hopper-presentation-rest" -DskipTests

echo "Done. Browse: https://repository.data-hopper.com/#browse/browse:hopper"
