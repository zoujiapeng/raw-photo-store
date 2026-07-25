#!/usr/bin/env bash
set -euo pipefail

REMOTE_URL="${1:-git@github.com:zoujiapeng/raw-photo-store.git}"
TAG="${2:-v0.3.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "请在 RAWJudge 仓库中运行。" >&2
  exit 1
fi

bash scripts/verify.sh

if [[ -n "$(git status --porcelain)" ]]; then
  echo "工作区存在未提交变更，拒绝发布。" >&2
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

echo "已推送 main 和 $TAG；GitHub Actions 将验证并创建 Beta APK Release。"
