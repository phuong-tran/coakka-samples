#!/usr/bin/env bash
set -euo pipefail

sample_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'EOF'
Usage: bash coakka-http-runtime/run.sh <verify|all|c|cpp|go|kotlin|python|typescript> [check|smoke|security-smoke|run]

The default command is smoke. Builds and dependency trees are written under
COAKKA_HTTP_SAMPLE_WORK_ROOT. Set COAKKA_PUBLISH_ROOT to the candidate
warehouse checkout. No private source checkout or Core rebuild is used.
EOF
}

lane="${1:-}"
command="${2:-smoke}"
case "${lane}" in
  verify)
    shellcheck -x -P SCRIPTDIR "${sample_root}/run.sh" \
      "${sample_root}/scripts/"*.sh "${sample_root}/"*/run.sh
    env PYTHONDONTWRITEBYTECODE=1 python3 \
      "${sample_root}/scripts/test-package-resolution.py"
    for item in c cpp go kotlin python typescript; do
      bash "${sample_root}/${item}/run.sh" check
      bash "${sample_root}/${item}/run.sh" smoke
      bash "${sample_root}/${item}/run.sh" security-smoke
    done
    ;;
  all)
    for item in c cpp go kotlin python typescript; do
      bash "${sample_root}/${item}/run.sh" "${command}"
    done
    ;;
  c|cpp|go|kotlin|python|typescript)
    bash "${sample_root}/${lane}/run.sh" "${command}"
    ;;
  *) usage; exit 2 ;;
esac
