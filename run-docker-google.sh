#!/usr/bin/env bash
# Run projectdatahopper/presentation-engine with Google OAuth (local Docker).
#
# Prerequisites:
#   - Image: projectdatahopper/presentation-engine:latest (or set IMAGE=...)
#   - Secret: ~/.config/hopper/google-oauth.env with GOOGLE_OAUTH_CLIENT_SECRET
#   - Config: hopper-presentation-rest/config-google-docker/hopper-presentation.properties
#   - Google Console redirect URI: http://localhost:8080/hopper/api/auth/callback
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${HOME}/.config/hopper/google-oauth.env"
CONFIG_DIR="${ROOT}/hopper-presentation-rest/config-google-docker"
METADATA_DIR="${ROOT}/hopper-presentation-rest/config/metadata"
IMAGE="${IMAGE:-projectdatahopper/presentation-engine:latest}"
NAME="${NAME:-hopper-presentation}"
HOST_PORT="${HOST_PORT:-8080}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — create it with GOOGLE_OAUTH_CLIENT_SECRET (see docs/google-oauth-test.md)" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$ENV_FILE"
if [[ -z "${GOOGLE_OAUTH_CLIENT_SECRET:-}" ]]; then
  echo "GOOGLE_OAUTH_CLIENT_SECRET is empty after sourcing $ENV_FILE" >&2
  exit 1
fi
if [[ ! -f "$CONFIG_DIR/hopper-presentation.properties" ]]; then
  echo "Missing $CONFIG_DIR/hopper-presentation.properties" >&2
  exit 1
fi
if [[ ! -d "$METADATA_DIR" ]]; then
  echo "Missing metadata dir $METADATA_DIR" >&2
  exit 1
fi

# Sanity: mounted config must enable oauth2
if ! grep -qE '^[[:space:]]*auth\.enabled[[:space:]]*=[[:space:]]*true' \
  "$CONFIG_DIR/hopper-presentation.properties"; then
  echo "WARNING: auth.enabled=true not found in $CONFIG_DIR/hopper-presentation.properties" >&2
fi
if ! grep -qE '^[[:space:]]*auth\.mode[[:space:]]*=[[:space:]]*oauth2' \
  "$CONFIG_DIR/hopper-presentation.properties"; then
  echo "WARNING: auth.mode=oauth2 not found in config" >&2
fi

docker rm -f "$NAME" 2>/dev/null || true

echo "Starting $IMAGE as $NAME on http://localhost:${HOST_PORT}/hopper/api/render/main/"
echo "  config  : $CONFIG_DIR -> /hopper-data/config"
echo "  metadata: $METADATA_DIR -> /hopper-data/metadata"
echo "  secret  : GOOGLE_OAUTH_CLIENT_SECRET (length ${#GOOGLE_OAUTH_CLIENT_SECRET})"

# Also set CATALINA_OPTS so older images that bake
#   -DHOPPER_REST_CONFIG_PATH=/config
# still pick up the mounted Google config (system property wins in those builds).
exec docker run --rm \
  -p "${HOST_PORT}:8080" \
  --name "$NAME" \
  -v "${CONFIG_DIR}:/hopper-data/config:ro" \
  -v "${METADATA_DIR}:/hopper-data/metadata" \
  -e HOPPER_REST_CONFIG_PATH=/hopper-data/config \
  -e HOPPER_METADATA_PATH=/hopper-data/metadata \
  -e CATALINA_OPTS="-DHOPPER_REST_CONFIG_PATH=/hopper-data/config" \
  -e GOOGLE_OAUTH_CLIENT_SECRET \
  "$IMAGE"
