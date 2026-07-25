#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON="${RAWJUDGE_PYTHON:-python3}"

(
  cd "$ROOT/backend"
  "$PYTHON" -m pytest -q
  "$PYTHON" -m compileall -q app tests
)

bash "$ROOT/android/gradlew" :app:testDebugUnitTest :app:assembleDebug --stacktrace

echo "RAWJudge verification completed."
echo "APK: $ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
