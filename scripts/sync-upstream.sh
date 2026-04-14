#!/usr/bin/env bash
#
# Sync this fork with upstream AsamK/signal-cli.
#
# Fast-forwards 'main' to match upstream, then merges upstream/main into
# 'agentchannels'. Any merge conflicts must be resolved manually before
# the final push.
#
# Usage:
#   ./scripts/sync-upstream.sh             # interactive, pushes on success
#   ./scripts/sync-upstream.sh --no-push   # resolve locally, push manually

set -euo pipefail

PUSH=1
for arg in "$@"; do
  case "$arg" in
    --no-push) PUSH=0 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

# Ensure we're inside the fork and that the upstream remote exists.
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "error: not inside a git repo" >&2
  exit 1
fi
if ! git remote get-url upstream >/dev/null 2>&1; then
  echo "error: 'upstream' remote is not configured" >&2
  echo "hint: git remote add upstream https://github.com/AsamK/signal-cli.git" >&2
  exit 1
fi

# Refuse to run with a dirty working tree — conflicts would be ambiguous.
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "error: working tree has uncommitted changes" >&2
  git status --short >&2
  exit 1
fi

echo "==> fetching upstream"
git fetch upstream

# Fast-forward main to upstream/main.
echo "==> updating main"
git checkout main
git merge --ff-only upstream/main

# Merge upstream into our long-lived patch branch.
echo "==> merging upstream/main into agentchannels"
git checkout agentchannels
if ! git merge --no-ff upstream/main -m "Merge upstream/main into agentchannels"; then
  echo
  echo "merge conflict — resolve, then run:"
  echo "  git add <files> && git commit"
  echo "  git push origin main agentchannels"
  exit 2
fi

if [[ "$PUSH" -eq 1 ]]; then
  echo "==> pushing main + agentchannels"
  git push origin main agentchannels
else
  echo "==> skipping push (--no-push)"
fi

echo "==> done"
