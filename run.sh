#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${repo_root}/scripts/sample-utils.sh"
source "${repo_root}/scripts/sample-metadata.sh"
quickstart() {
  bash "${repo_root}/scripts/doctor.sh"
  if { command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; } ||
     { command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; }; then
    local status=0
    bash "${repo_root}/coakka-runtime/run.sh" containers node-python smoke || status=$?
    bash "${repo_root}/coakka-runtime/run.sh" containers node-python down >/dev/null 2>&1 || true
    return "${status}"
  fi
  bash "${repo_root}/coakka-runtime/run.sh" node basic
}
case "${1:-quickstart}" in
  quickstart|start|try) quickstart ;;
  list) coakka_print_samples ;;
  doctor) exec bash "${repo_root}/scripts/doctor.sh" ;;
  all)
    bash "${repo_root}/coakka-logger/run.sh" all
    exec bash "${repo_root}/coakka-runtime/run.sh" all ;;
  coakka-runtime|runtime) shift; exec bash "${repo_root}/coakka-runtime/run.sh" "$@" ;;
  coakka-logger|logger) shift; exec bash "${repo_root}/coakka-logger/run.sh" "$@" ;;
  coakka-http-runtime) shift; exec bash "${repo_root}/coakka-http-runtime/run.sh" "$@" ;;
  coakka-runtime/*|runtime/*)
    selected="${1#*/}"; shift
    exec bash "${repo_root}/coakka-runtime/run.sh" "${selected}" "$@" ;;
  coakka-logger/*|logger/*)
    selected="${1#*/}"; shift
    exec bash "${repo_root}/coakka-logger/run.sh" "${selected}" "$@" ;;
  coakka-http-runtime/*)
    selected="${1#*/}"; shift
    exec bash "${repo_root}/coakka-http-runtime/run.sh" "${selected}" "$@" ;;
  runtime-test|runtime-client|runtime-inspect|runtime-addons|containers|scenarios|scenario|runtime-addons/*|runtime-client/*|runtime-inspect/*|containers/*)
    exec bash "${repo_root}/coakka-runtime/run.sh" "$@" ;;
  help|-h|--help)
    printf '%s\n' 'Usage: bash run.sh [list|doctor|quickstart|all]' \
      '  bash run.sh coakka-runtime <language> <sample>' \
      '  bash run.sh coakka-runtime [runtime-test|client|inspect|addons|containers|scenarios|scenario] ...' \
      '  bash run.sh coakka-logger <language> <sample>' \
      '  bash run.sh coakka-http-runtime [verify|all|<language>] ...' ;;
  *) coakka_die "Unknown product or command: $1" ;;
esac
