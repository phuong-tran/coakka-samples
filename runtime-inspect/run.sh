#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

COAKKA_RUNTIME_INSPECT_VERSION="1.3.1"

core_root="${COAKKA_CORE_ROOT:-${repo_root}/../coakkaCoreNativeDev}"
inspect_bin="${COAKKA_RUNTIME_INSPECT_BIN:-${core_root}/build-v2/coakka-runtime-inspect}"
tmp_dir=""

cleanup() {
  if [[ -n "${tmp_dir}" ]]; then
    rm -rf "${tmp_dir}"
    tmp_dir=""
  fi
}

trap cleanup EXIT INT TERM

print_usage() {
  cat <<'EOF'
coakka-runtime-inspect sample

Usage:
  bash run.sh runtime-inspect
  bash run.sh runtime-inspect check
  bash run.sh runtime-inspect published-smoke
  bash run.sh runtime-inspect local-smoke
  bash run.sh runtime-inspect serve

Environment:
  COAKKA_PUBLISH_ROOT       local coakka-publish checkout
  COAKKA_PUBLISH_RAW_BASE   raw public fallback URL
  COAKKA_CORE_ROOT=/path/to/coakkaCoreNativeDev
  COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect

Notes:
  check verifies docs and, on macOS ARM64, Linux, or Windows x86_64, the
  published inspect archive checksum through the public artifact manifest.
  published-smoke extracts the published platform archive and runs command
  plus snapshot smoke from that prefix.
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

coakka_runtime_inspect_platform() {
  local system machine
  system="$(uname -s)"
  machine="$(uname -m)"
  case "${system}:${machine}" in
    Darwin:arm64|Darwin:aarch64) printf '%s\n' "macos-aarch64" ;;
    Linux:aarch64|Linux:arm64) printf '%s\n' "linux-aarch64" ;;
    Linux:x86_64|Linux:amd64) printf '%s\n' "linux-x86_64" ;;
    MINGW*:x86_64|MSYS*:x86_64|CYGWIN*:x86_64) printf '%s\n' "windows-x86_64" ;;
    *)
      return 1
      ;;
  esac
}

coakka_runtime_inspect_release_id_for_platform() {
  local platform="$1"
  case "${platform}" in
    windows-x86_64) printf '%s\n' "1.3.1+6c63864" ;;
    macos-aarch64|linux-aarch64|linux-x86_64) printf '%s\n' "1.3.1+e664986" ;;
    *) return 1 ;;
  esac
}

resolve_published_archive() {
  local platform="$1"
  local artifact_name artifact_rel release_id
  release_id="$(coakka_runtime_inspect_release_id_for_platform "${platform}")"
  artifact_name="coakka-runtime-inspect-v2-${COAKKA_RUNTIME_INSPECT_VERSION}-${platform}.tar.gz"
  artifact_rel="runtime-inspect/native/releases/${release_id}/${artifact_name}"

  mkdir -p "${tmp_dir}/artifacts"
  coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/${artifact_name}"
}

run_check() {
  require_docs
  echo "coakka-runtime-inspect sample check"
  echo "docs=ok"
  if platform="$(coakka_runtime_inspect_platform)"; then
    tmp_dir="$(mktemp -d)"
    archive_path="$(resolve_published_archive "${platform}")"
    echo "published_artifact=${platform}"
    echo "published_release_id=$(coakka_runtime_inspect_release_id_for_platform "${platform}")"
    echo "published_archive=${archive_path}"
    cleanup
  else
    echo "published_artifact=not-available-for-this-platform"
    echo "published_platforms=macos-aarch64,linux-aarch64,linux-x86_64,windows-x86_64"
  fi
  if [[ -x "${inspect_bin}" ]]; then
    echo "local_binary=${inspect_bin}"
    echo "local_smoke_hint=bash run.sh runtime-inspect local-smoke"
  else
    echo "local_binary=missing"
    echo "build_hint=cmake --build ${core_root}/build-v2 --target coakka_v2_coakka_runtime_inspect"
  fi
}

smoke_inspect_binary() {
  local binary_path="$1"
  require_docs
  coakka_require_executable_file "${binary_path}" "The inspect binary is missing or not executable."

  local smoke_tmp snapshot_json
  smoke_tmp="$(mktemp -d)"
  snapshot_json="${smoke_tmp}/snapshot.json"

  "${binary_path}" version >/dev/null
  "${binary_path}" doctor >/dev/null
  "${binary_path}" help serve | grep -F "GET /api/snapshot" >/dev/null
  "${binary_path}" snapshot \
    --output json \
    --local-route inspect.echo=127.0.0.1:19001 >"${snapshot_json}"

  grep -E '"snapshot_source"[[:space:]]*:[[:space:]]*"local-linked-runtime"' "${snapshot_json}" >/dev/null
  grep -E '"target"[[:space:]]*:[[:space:]]*"inspect.echo"' "${snapshot_json}" >/dev/null

  rm -rf "${smoke_tmp}"
}

run_published_smoke() {
  local platform archive_path package_root published_bin
  platform="$(coakka_runtime_inspect_platform)" ||
    coakka_die "Published runtime-inspect archive is currently available for macOS ARM64, Linux x86_64/ARM64, and Windows x86_64 only."

  coakka_require_command tar "Install tar, then retry."
  tmp_dir="$(mktemp -d)"
  archive_path="$(resolve_published_archive "${platform}")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${archive_path}"
  package_root="${tmp_dir}/package/coakka-runtime-inspect-v2-${COAKKA_RUNTIME_INSPECT_VERSION}-${platform}"
  case "${platform}" in
    windows-*) published_bin="${package_root}/bin/coakka-runtime-inspect.exe" ;;
    *) published_bin="${package_root}/bin/coakka-runtime-inspect" ;;
  esac

  case "$(uname -s)" in
    Darwin)
      DYLD_LIBRARY_PATH="${package_root}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}" \
        smoke_inspect_binary "${published_bin}"
      ;;
    Linux)
      LD_LIBRARY_PATH="${package_root}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}" \
        smoke_inspect_binary "${published_bin}"
      ;;
    MINGW*|MSYS*|CYGWIN*)
      smoke_inspect_binary "${published_bin}"
      ;;
    *)
      coakka_die "Unsupported runtime-inspect published smoke host."
      ;;
  esac

  cleanup
  echo "coakka-runtime-inspect published smoke ok"
}

run_local_smoke() {
  coakka_require_executable_file "${inspect_bin}" "Build coakkaCoreNativeDev v2 first, or set COAKKA_RUNTIME_INSPECT_BIN."
  smoke_inspect_binary "${inspect_bin}"
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
  published-smoke)
    run_published_smoke
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
