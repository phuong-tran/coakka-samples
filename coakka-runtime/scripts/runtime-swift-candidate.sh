#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec python3 -I -X utf8 -B "${repo_root}/coakka-runtime/scripts/runtime-swift-candidate.py" --repo-root "${repo_root}"
