#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV="${RAWJUDGE_VENV:-$ROOT/.venv}"

if [[ ! -x "$VENV/bin/python" ]]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -e "$ROOT/backend[dev]"
fi

(
  cd "$ROOT/backend"
  "$VENV/bin/ruff" check .
  "$VENV/bin/ruff" format --check .
  "$VENV/bin/python" -m pytest --cov=app --cov-report=term-missing --cov-fail-under=70
  "$VENV/bin/python" -m compileall -q app tests
)

"$ROOT/android/gradlew" :app:testDebugUnitTest :app:assembleDebug --stacktrace

echo "RAWJudge verification completed. APK: android/app/build/outputs/apk/debug/app-debug.apk"
