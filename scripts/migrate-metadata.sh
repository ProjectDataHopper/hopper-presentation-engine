#!/usr/bin/env bash
# Rewrite legacy Lean* component/connector plugin keys to H* in presentation/connector JSON.
# Usage: scripts/migrate-metadata.sh /path/to/metadata
set -euo pipefail
ROOT="${1:-}"
if [[ -z "$ROOT" || ! -d "$ROOT" ]]; then
  echo "Usage: $0 /path/to/metadata-folder" >&2
  exit 1
fi
count=0
while IFS= read -r -d '' f; do
  if grep -q 'Lean[A-Z]' "$f" 2>/dev/null; then
    sed -i \
      -e 's/"Lean\([A-Z][A-Za-z0-9_]*\)"/"H\1"/g' \
      "$f"
    count=$((count + 1))
    echo "migrated $f"
  fi
done < <(find "$ROOT" -type f -name '*.json' -print0)
echo "Done. Updated $count JSON file(s)."
