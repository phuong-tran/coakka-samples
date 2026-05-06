#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

artifact_rel="runtime/python/releases/0.1.0+65b36b8ad2ec/coakka_v2_connector-0.1.0-py3-none-any.whl"

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

require_runtime_commands() {
  coakka_require_command python3 "Install Python 3.11 or newer, then retry."
}

with_python_env() {
  require_runtime_commands
  local tmp_dir site_packages wheel_path status
  tmp_dir="$(mktemp -d)"
  site_packages="${tmp_dir}/site-packages"
  wheel_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka_v2_connector-0.1.0-py3-none-any.whl")"
  python3 -m pip install "${wheel_path}" --target "${site_packages}" >/dev/null
  set +e
  PYTHONPATH="${site_packages}" python3 "${script_dir}/app.py" "$@"
  status="$?"
  set -e
  rm -rf "${tmp_dir}"
  return "${status}"
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
