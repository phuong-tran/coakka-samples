#!/usr/bin/env bash
set -euo pipefail

sample_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'EOF'
Usage: bash coakka-http-runtime/run.sh <verify|all|c|cpp|go|kotlin|python|typescript> [check|smoke|security-smoke|run]

The default command is smoke. Builds and dependency trees are written under
COAKKA_HTTP_SAMPLE_WORK_ROOT. Set COAKKA_HTTP_RUNTIME_ROOT or
COAKKA_HTTP_CONNECTOR_ROOT when the source repositories are not sibling
checkouts.
EOF
}

lane="${1:-}"
command="${2:-smoke}"
case "${lane}" in
  verify)
    shellcheck -x -P SCRIPTDIR "${sample_root}/run.sh" \
      "${sample_root}/scripts/"*.sh "${sample_root}/"*/run.sh \
      "${sample_root}/benchmark-rpi5/scripts/"*.sh
    bash -n "${sample_root}/benchmark-rpi5/scripts/"*.sh
    env PYTHONDONTWRITEBYTECODE=1 python3 \
      "${sample_root}/benchmark-rpi5/scripts/test_benchmark_tools.py"
    python3 -m json.tool \
      "${sample_root}/benchmark-rpi5/config/lanes.json" >/dev/null
    node --check "${sample_root}/benchmark-rpi5/src/typescript/server.mjs"
    unformatted_go="$(gofmt -l "${sample_root}/benchmark-rpi5/src/go/main.go")"
    if [[ -n "${unformatted_go}" ]]; then
      echo "benchmark Go source is not formatted: ${unformatted_go}" >&2
      exit 1
    fi
    # Build the shared native owner once. Child runners validate and reuse this
    # exact prefix instead of reconfiguring the same dependency graph.
    # shellcheck source=scripts/common.sh
    source "${sample_root}/scripts/common.sh"
    export COAKKA_HTTP_PREPARED_HOST_PREFIX
    COAKKA_HTTP_PREPARED_HOST_PREFIX="$(prepare_host_prefix)"
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
