#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

print_usage() {
  cat <<'EOF'
Python Desktop local customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh dev
  bash run.sh app
  bash run.sh smoke

Default command is 'check'. Use 'app' or 'dev' to open the Tk desktop UI.
EOF
}

with_python_env() {
  coakka_with_python_package_env "coakka-v2-connector==1.3.2" "${script_dir}/app.py" "$@"
}

check_app() {
  with_python_env --smoke >/dev/null
  coakka_note "check ok: ran Python Desktop local runtime customer smoke"
}

case "${1:-}" in
  ""|check|build)
    check_app
    ;;
  app|dev)
    with_python_env
    ;;
  smoke)
    with_python_env --smoke
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown command: $1"
    ;;
esac
