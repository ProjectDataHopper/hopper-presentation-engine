#!/usr/bin/env bash
# Run hopper-presentation-rest with Google OAuth local config.
# Prerequisites:
#   source ~/.config/hopper/google-oauth.env   # GOOGLE_OAUTH_CLIENT_SECRET
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${HOME}/.config/hopper/google-oauth.env"
CONFIG_DIR="${ROOT}/hopper-presentation-rest/config-google-local"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — create it from your client_secret JSON (see docs/google-oauth-test.md)" >&2
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

export HOPPER_REST_CONFIG_PATH="$CONFIG_DIR"
echo "HOPPER_REST_CONFIG_PATH=$HOPPER_REST_CONFIG_PATH"
echo "GOOGLE_OAUTH_CLIENT_SECRET is set (length ${#GOOGLE_OAUTH_CLIENT_SECRET})"
echo "Open http://localhost:8080/hopper/api/render/main/ after Jetty starts"
# Jetty is only declared on hopper-presentation-rest. The parent aggregator has no jetty
# plugin, so the "jetty:" prefix fails from the root. Run the goal from the REST module
# after building dependencies with -am from the root first.
#
# Pass config as both env and -D system property (HRest reads both).
cd "$ROOT"
mvn -pl hopper-presentation-rest -am install -DskipTests -q
cd "$ROOT/hopper-presentation-rest"
exec mvn jetty:run -DHOPPER_REST_CONFIG_PATH="$HOPPER_REST_CONFIG_PATH"
