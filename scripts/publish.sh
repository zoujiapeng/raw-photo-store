#!/usr/bin/env bash
set -euo pipefail

REMOTE_URL="${1:-git@github.com:zoujiapeng/raw-photo-store.git}"
TAG="${2:-v0.2.0}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
./scripts/verify.sh

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to publish a dirty worktree." >&2
  exit 1
fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi

git push -u origin main
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  git tag -a "$TAG" -m "RAWJudge $TAG"
fi
git push origin "$TAG"

echo "Pushed main and $TAG. GitHub Actions will verify and publish the installable beta APK."
