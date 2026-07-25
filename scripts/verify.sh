#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

(
  cd "$ROOT/backend"
  python -m pytest -q
  python -m compileall -q app tests
)

if command -v gradle >/dev/null 2>&1; then
  gradle -p "$ROOT/android" :app:testDebugUnitTest :app:assembleDebug --stacktrace
else
  echo "Gradle 未安装：已跳过完整 Android 构建；GitHub Actions 会执行该步骤。" >&2
fi
