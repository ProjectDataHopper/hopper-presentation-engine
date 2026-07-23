#!/usr/bin/env bash
# Start the Hopper Presentation REST UI (Jetty).
# Must target the REST module — Jetty is not declared on the parent POM.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
exec mvn -pl hopper-presentation-rest -am jetty:run "$@"
