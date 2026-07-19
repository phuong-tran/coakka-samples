#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

COAKKA_RUNTIME_INSPECT_VERSION="1.3.1"
COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE_DEFAULT="coakka-runtime-inspect-sample:1.3.1-local"

core_root="${COAKKA_CORE_ROOT:-${repo_root}/../coakkaCoreNativeDev}"
inspect_bin="${COAKKA_RUNTIME_INSPECT_BIN:-${core_root}/build-v2/coakka-runtime-inspect}"
docker_context_root="${COAKKA_RUNTIME_INSPECT_DOCKER_CONTEXT:-${repo_root}/build/runtime-inspect-docker/context}"
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
  bash run.sh runtime-inspect docker-smoke
  bash run.sh runtime-inspect docker-serve

Environment:
  COAKKA_PUBLISH_ROOT       local coakka-publish checkout
  COAKKA_PUBLISH_RAW_BASE   raw public fallback URL
  COAKKA_CORE_ROOT=/path/to/coakkaCoreNativeDev
  COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect
  COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE=coakka-runtime-inspect-sample:1.3.1-local
  COAKKA_RUNTIME_INSPECT_DOCKER_PORT=18080

Notes:
  check verifies docs and, on macOS ARM64, Linux, or Windows, the
  published inspect archive checksum through the public artifact manifest.
  published-smoke extracts the published platform archive and runs command
  plus snapshot smoke from that prefix.
  local-smoke requires a native coakka-runtime-inspect binary from the sibling
  core repository or COAKKA_RUNTIME_INSPECT_BIN.
  serve starts the browser UI from that local binary.
  docker-smoke builds a local image from the published Linux archive and runs
  command smoke inside the container.
  docker-serve runs the same image and exposes the browser UI on the host.
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
    MINGW*:aarch64|MINGW*:arm64|MSYS*:aarch64|MSYS*:arm64|CYGWIN*:aarch64|CYGWIN*:arm64) printf '%s\n' "windows-aarch64" ;;
    MINGW*:x86_64|MSYS*:x86_64|CYGWIN*:x86_64) printf '%s\n' "windows-x86_64" ;;
    *)
      return 1
      ;;
  esac
}

coakka_runtime_inspect_release_id_for_platform() {
  local platform="$1"
  case "${platform}" in
    windows-aarch64) printf '%s\n' "1.3.1+5c70234" ;;
    windows-x86_64) printf '%s\n' "1.3.1+6c63864" ;;
    macos-aarch64|linux-aarch64|linux-x86_64) printf '%s\n' "1.3.1+e664986" ;;
    *) return 1 ;;
  esac
}

coakka_runtime_inspect_docker_platform() {
  case "$(uname -m)" in
    arm64|aarch64) printf '%s\n' "linux-aarch64" ;;
    x86_64|amd64) printf '%s\n' "linux-x86_64" ;;
    *) coakka_die "Unsupported runtime-inspect Docker host architecture: $(uname -m)" ;;
  esac
}

coakka_runtime_inspect_docker_platform_arg() {
  local platform="$1"
  case "${platform}" in
    linux-aarch64) printf '%s\n' "linux/arm64" ;;
    linux-x86_64) printf '%s\n' "linux/amd64" ;;
    *) coakka_die "Unsupported runtime-inspect Docker platform: ${platform}" ;;
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
    echo "published_platforms=macos-aarch64,linux-aarch64,linux-x86_64,windows-x86_64,windows-aarch64"
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

prepare_docker_context() {
  local platform="$1"
  local archive_path package_root

  coakka_require_command tar "Install tar, then retry."
  tmp_dir="$(mktemp -d)"
  archive_path="$(resolve_published_archive "${platform}")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${archive_path}"
  package_root="${tmp_dir}/package/coakka-runtime-inspect-v2-${COAKKA_RUNTIME_INSPECT_VERSION}-${platform}"
  coakka_require_executable_file "${package_root}/bin/coakka-runtime-inspect" \
    "The published Linux inspect archive is incomplete."

  rm -rf "${docker_context_root}"
  mkdir -p "${docker_context_root}/inspect"
  cp -R "${package_root}/." "${docker_context_root}/inspect/"
  cp "${script_dir}/docker/Dockerfile" "${docker_context_root}/Dockerfile"
  cp "${script_dir}/docker/entrypoint.sh" "${docker_context_root}/entrypoint.sh"
  chmod +x "${docker_context_root}/entrypoint.sh"
  cleanup

  printf '%s\n' "${docker_context_root}"
}

run_published_smoke() {
  local platform archive_path package_root published_bin
  platform="$(coakka_runtime_inspect_platform)" ||
    coakka_die "Published runtime-inspect archive is currently available for macOS ARM64, Linux x86_64/ARM64, and Windows x86_64/ARM64 only."

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

run_docker_smoke() {
  local platform docker_platform image context_root
  platform="$(coakka_runtime_inspect_docker_platform)"
  docker_platform="$(coakka_runtime_inspect_docker_platform_arg "${platform}")"
  image="${COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE:-${COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE_DEFAULT}}"

  coakka_require_command docker "Install Docker, then retry."
  context_root="$(prepare_docker_context "${platform}")"
  docker build --platform "${docker_platform}" --tag "${image}" "${context_root}" >/dev/null
  docker run --rm --platform "${docker_platform}" "${image}" smoke
  echo "coakka-runtime-inspect docker smoke ok"
}

run_docker_serve() {
  local platform docker_platform image context_root host_port
  platform="$(coakka_runtime_inspect_docker_platform)"
  docker_platform="$(coakka_runtime_inspect_docker_platform_arg "${platform}")"
  image="${COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE:-${COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE_DEFAULT}}"
  host_port="${COAKKA_RUNTIME_INSPECT_DOCKER_PORT:-18080}"

  coakka_require_command docker "Install Docker, then retry."
  context_root="$(prepare_docker_context "${platform}")"
  docker build --platform "${docker_platform}" --tag "${image}" "${context_root}" >/dev/null
  exec docker run --rm \
    --platform "${docker_platform}" \
    -p "${host_port}:18080" \
    "${image}" serve "$@"
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
  docker-smoke)
    run_docker_smoke
    ;;
  docker-serve)
    shift || true
    run_docker_serve "$@"
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown runtime-inspect command: ${command_name}"
    ;;
esac
