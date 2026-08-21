#!/usr/bin/env bash
set -euo pipefail

inspect_bin="/opt/coakka-runtime-inspect/bin/coakka-runtime-inspect"
runtime_lib="/opt/coakka-runtime-inspect/lib/libcoakka_runtime_v2.so"

print_usage() {
  cat <<'EOF'
coakka-runtime-inspect Docker sample

Usage:
  docker run --rm coakka-runtime-inspect-sample:<version>-local smoke
  docker run --rm -p 18080:18080 coakka-runtime-inspect-sample:<version>-local serve
  docker run --rm coakka-runtime-inspect-sample:<version>-local inspect version --output json

Commands:
  smoke    Run version, doctor, help serve, and snapshot checks.
  serve    Start the browser UI on 0.0.0.0:18080.
  inspect  Pass remaining arguments directly to coakka-runtime-inspect.
EOF
}

assert_self_contained_native_deps() {
  if ! command -v ldd >/dev/null 2>&1; then
    echo "[coakka-runtime-inspect-sample] cannot verify Linux native dependencies: ldd is unavailable" >&2
    exit 1
  fi

  local report
  report="$(
    LD_LIBRARY_PATH=/opt/coakka-runtime-inspect/lib \
      ldd "${inspect_bin}" "${runtime_lib}" 2>&1 || true
  )"

  if printf '%s\n' "${report}" | grep -Eiq 'not found'; then
    echo "[coakka-runtime-inspect-sample] native bundle is not self-contained" >&2
    exit 1
  fi

  while IFS= read -r dep; do
    [[ -n "${dep}" ]] || continue
    case "${dep}" in
      linux-vdso.so.1|libcoakka_runtime_v2.so|libm.so.6|libc.so.6|ld-linux-x86-64.so.2|ld-linux-aarch64.so.1)
        ;;
      *)
        echo "[coakka-runtime-inspect-sample] native bundle declares a non-allowed dynamic dependency" >&2
        exit 1
        ;;
    esac
  done < <(
    printf '%s\n' "${report}" |
      awk '
        /^[[:space:]]*$/ { next }
        /:$/ { next }
        $1 == "statically" { next }
        index($1, "/") == 1 {
          n = split($1, parts, "/")
          print parts[n]
          next
        }
        { print $1 }
      '
  )
}

run_smoke() {
  local snapshot_json
  snapshot_json="$(mktemp)"

  assert_self_contained_native_deps
  "${inspect_bin}" version >/dev/null
  "${inspect_bin}" doctor >/dev/null
  "${inspect_bin}" help serve | \
    grep -F "Serve a local read-first inspect UI" >/dev/null
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
