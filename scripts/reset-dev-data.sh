#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

rm -rf "$ROOT/backend/data" "$ROOT/backend/uploads"
mkdir -p "$ROOT/backend/data" "$ROOT/backend/uploads"

if command -v docker >/dev/null 2>&1; then
  docker compose -f "$ROOT/docker-compose.yml" down -v --remove-orphans >/dev/null 2>&1 || true
fi

echo "Development database, uploads, and Docker volumes were reset."
