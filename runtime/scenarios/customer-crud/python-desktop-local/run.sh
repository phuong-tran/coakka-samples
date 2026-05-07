#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

artifact_rel="runtime/python/releases/0.1.0+e91e6bb90bba/coakka_v2_connector-0.1.0-py3-none-any.whl"

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
  local python_bin="${COAKKA_PYTHON:-python3}"
  coakka_require_command "${python_bin}" "Install Python 3.11 or newer, then retry."
  "${python_bin}" -m venv --help >/dev/null 2>&1 ||
    coakka_die "Python venv support is required. Install the venv module for ${python_bin}, then retry."
}

with_python_env() {
  require_runtime_commands
  local python_bin tmp_dir venv_python wheel_path status
  python_bin="${COAKKA_PYTHON:-python3}"
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT INT TERM
  "${python_bin}" -m venv "${tmp_dir}/venv"
  venv_python="${tmp_dir}/venv/bin/python"
  wheel_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka_v2_connector-0.1.0-py3-none-any.whl")"
  PIP_DISABLE_PIP_VERSION_CHECK=1 "${venv_python}" -m pip install "${wheel_path}" >/dev/null
  set +e
  "${venv_python}" "${script_dir}/app.py" "$@"
  status="$?"
  set -e
  rm -rf "${tmp_dir}"
  trap - EXIT INT TERM
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
