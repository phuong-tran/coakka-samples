#!/usr/bin/env bash
set -euo pipefail

inspect_bin="/opt/coakka-runtime-inspect/bin/coakka-runtime-inspect"

print_usage() {
  cat <<'EOF'
coakka-runtime-inspect Docker sample

Usage:
  docker run --rm coakka-runtime-inspect-sample:1.3.1-local smoke
  docker run --rm -p 18080:18080 coakka-runtime-inspect-sample:1.3.1-local serve
  docker run --rm coakka-runtime-inspect-sample:1.3.1-local inspect version --output json

Commands:
  smoke    Run version, doctor, help serve, and snapshot checks.
  serve    Start the browser UI on 0.0.0.0:18080.
  inspect  Pass remaining arguments directly to coakka-runtime-inspect.
EOF
}

run_smoke() {
  local snapshot_json
  snapshot_json="$(mktemp)"

  "${inspect_bin}" version >/dev/null
  "${inspect_bin}" doctor >/dev/null
  "${inspect_bin}" help serve | grep -F "GET /api/snapshot" >/dev/null
  "${inspect_bin}" snapshot \
    --output json \
    --local-route inspect.echo=127.0.0.1:19001 >"${snapshot_json}"

  grep -E '"snapshot_source"[[:space:]]*:[[:space:]]*"local-linked-runtime"' "${snapshot_json}" >/dev/null
  grep -E '"target"[[:space:]]*:[[:space:]]*"inspect.echo"' "${snapshot_json}" >/dev/null
  rm -f "${snapshot_json}"
}

command_name="${1:-serve}"
case "${command_name}" in
  smoke)
    run_smoke
    echo "coakka-runtime-inspect docker image smoke ok"
    ;;
  serve)
    shift || true
    exec "${inspect_bin}" serve \
      --host 0.0.0.0 \
      --port 18080 \
      --local-route inspect.echo=127.0.0.1:19001 \
      "$@"
    ;;
  inspect)
    shift || true
    exec "${inspect_bin}" "$@"
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    echo "[coakka-runtime-inspect-sample] unknown command: ${command_name}" >&2
    exit 1
    ;;
esac
