#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

core_root="${COAKKA_CORE_ROOT:-${repo_root}/../coakkaCoreNativeDev}"
inspect_bin="${COAKKA_RUNTIME_INSPECT_BIN:-${core_root}/build-v2/coakka-runtime-inspect}"

print_usage() {
  cat <<'EOF'
coakka-runtime-inspect sample

Usage:
  bash run.sh runtime-inspect
  bash run.sh runtime-inspect check
  bash run.sh runtime-inspect local-smoke
  bash run.sh runtime-inspect serve

Environment:
  COAKKA_CORE_ROOT=/path/to/coakkaCoreNativeDev
  COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect

Notes:
  check verifies the public sample docs and lane wiring without requiring a
  published inspect archive.
  local-smoke requires a native coakka-runtime-inspect binary from the sibling
  core repository or COAKKA_RUNTIME_INSPECT_BIN.
  serve starts the browser UI from that local binary.
EOF
}

require_docs() {
  coakka_require_file "${script_dir}/README.md" "The runtime-inspect landing page must be present."
  coakka_require_file "${script_dir}/docs/README.md" "The runtime-inspect docs index must be present."
  coakka_require_file "${script_dir}/docs/introduction.md" "The runtime-inspect introduction must be present."
  coakka_require_file "${script_dir}/docs/usage.md" "The runtime-inspect usage guide must be present."
  coakka_require_file "${script_dir}/docs/technical-notes.md" "The runtime-inspect technical notes must be present."
}

run_check() {
  require_docs
  echo "coakka-runtime-inspect sample check"
  echo "docs=ok"
  echo "published_artifact=not-yet-published"
  if [[ -x "${inspect_bin}" ]]; then
    echo "local_binary=${inspect_bin}"
    echo "local_smoke_hint=bash run.sh runtime-inspect local-smoke"
  else
    echo "local_binary=missing"
    echo "build_hint=cmake --build ${core_root}/build-v2 --target coakka_v2_coakka_runtime_inspect"
  fi
}

run_local_smoke() {
  require_docs
  coakka_require_executable_file "${inspect_bin}" "Build coakkaCoreNativeDev v2 first, or set COAKKA_RUNTIME_INSPECT_BIN."

  local tmp_dir snapshot_json
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT INT TERM
  snapshot_json="${tmp_dir}/snapshot.json"

  "${inspect_bin}" version >/dev/null
  "${inspect_bin}" doctor >/dev/null
  "${inspect_bin}" help serve | grep -F "GET /api/snapshot" >/dev/null
  "${inspect_bin}" snapshot \
    --output json \
    --local-route inspect.echo=127.0.0.1:19001 >"${snapshot_json}"

  grep -E '"snapshot_source"[[:space:]]*:[[:space:]]*"local-linked-runtime"' "${snapshot_json}" >/dev/null
  grep -E '"target"[[:space:]]*:[[:space:]]*"inspect.echo"' "${snapshot_json}" >/dev/null

  rm -rf "${tmp_dir}"
  trap - EXIT INT TERM
  echo "coakka-runtime-inspect local smoke ok"
}

run_serve() {
  coakka_require_executable_file "${inspect_bin}" "Build coakkaCoreNativeDev v2 first, or set COAKKA_RUNTIME_INSPECT_BIN."
  exec "${inspect_bin}" serve \
    --host "${COAKKA_RUNTIME_INSPECT_HOST:-127.0.0.1}" \
    --port "${COAKKA_RUNTIME_INSPECT_PORT:-18080}" \
    --local-route inspect.echo=127.0.0.1:19001 \
    "$@"
}

command_name="${1:-check}"
case "${command_name}" in
  check)
    run_check
    ;;
  local-smoke)
    run_local_smoke
    ;;
  serve)
    shift || true
    run_serve "$@"
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown runtime-inspect command: ${command_name}"
    ;;
esac
