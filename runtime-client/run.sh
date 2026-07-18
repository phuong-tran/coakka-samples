#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

COAKKA_RUNTIME_CLIENT_RELEASE_ID="1.3.1+2215b0f"
COAKKA_RUNTIME_CLIENT_VERSION="1.3.1"
coakka_runtime_client_tmp_dir=""
coakka_runtime_client_compose_dir=""

coakka_runtime_client_cleanup() {
  if [[ -n "${coakka_runtime_client_compose_dir}" &&
        -f "${coakka_runtime_client_compose_dir}/compose.yaml" &&
        -n "$(command -v docker 2>/dev/null || true)" ]]; then
    docker compose -f "${coakka_runtime_client_compose_dir}/compose.yaml" down -v >/dev/null 2>&1 || true
    coakka_runtime_client_compose_dir=""
  fi
  if [[ -n "${coakka_runtime_client_tmp_dir}" ]]; then
    rm -rf "${coakka_runtime_client_tmp_dir}"
    coakka_runtime_client_tmp_dir=""
  fi
}

trap coakka_runtime_client_cleanup EXIT INT TERM

print_usage() {
  cat <<'EOF'
coakka-runtime-client sample

Usage:
  bash run.sh runtime-client
  bash run.sh runtime-client check
  bash run.sh runtime-client version
  bash run.sh runtime-client doctor
  bash run.sh runtime-client docker-demo

Environment:
  COAKKA_PUBLISH_ROOT       local coakka-publish checkout
  COAKKA_PUBLISH_RAW_BASE   raw public fallback URL
EOF
}

coakka_runtime_client_platform() {
  local system machine
  system="$(uname -s)"
  machine="$(uname -m)"
  case "${system}:${machine}" in
    Darwin:arm64|Darwin:aarch64) printf '%s\n' "macos-aarch64" ;;
    Linux:aarch64|Linux:arm64) printf '%s\n' "linux-aarch64" ;;
    Linux:x86_64|Linux:amd64) printf '%s\n' "linux-x86_64" ;;
    *)
      coakka_die "Unsupported runtime-client sample platform: ${system}/${machine}. Use the published Windows archive directly on Windows."
      ;;
  esac
}

coakka_runtime_client_docker_platform() {
  case "$(uname -m)" in
    arm64|aarch64) printf '%s\n' "linux-aarch64" ;;
    x86_64|amd64) printf '%s\n' "linux-x86_64" ;;
    *) coakka_die "Unsupported Docker demo host architecture: $(uname -m)" ;;
  esac
}

coakka_runtime_client_run() {
  if [[ "$#" -lt 1 ]]; then
    coakka_die "usage: coakka_runtime_client_run <command> [args...]"
  fi

  local platform artifact_name artifact_rel tmp_dir package_path package_root cli_path
  platform="$(coakka_runtime_client_platform)"
  artifact_name="coakka-client-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}.tar.gz"
  artifact_rel="cli/releases/${COAKKA_RUNTIME_CLIENT_RELEASE_ID}/${artifact_name}"

  coakka_require_command tar "Install tar, then retry."

  coakka_runtime_client_cleanup
  tmp_dir="$(mktemp -d)"
  coakka_runtime_client_tmp_dir="${tmp_dir}"

  package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/${artifact_name}")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${package_path}"

  package_root="${tmp_dir}/package/coakka-client-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}"
  cli_path="${package_root}/bin/coakka-client"
  coakka_require_executable_file "${cli_path}" "The published CLI archive is incomplete."

  case "$(uname -s)" in
    Darwin)
      DYLD_LIBRARY_PATH="${package_root}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}" "${cli_path}" "$@"
      ;;
    Linux)
      LD_LIBRARY_PATH="${package_root}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}" "${cli_path}" "$@"
      ;;
    *)
      coakka_die "Unsupported runtime-client sample platform."
      ;;
  esac

  coakka_runtime_client_cleanup
}

coakka_runtime_client_docker_demo() {
  local platform artifact_name artifact_rel tmp_dir package_path bundle_root output
  platform="$(coakka_runtime_client_docker_platform)"
  artifact_name="coakka-client-docker-demo-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}.tar.gz"
  artifact_rel="demo/coakka-client/releases/${COAKKA_RUNTIME_CLIENT_RELEASE_ID}/${artifact_name}"

  coakka_require_command docker "Install Docker with the Compose plugin, then retry."
  coakka_require_command tar "Install tar, then retry."
  docker compose version >/dev/null 2>&1 ||
    coakka_die "Docker Compose plugin is required. Verify 'docker compose version' works."

  coakka_runtime_client_cleanup
  tmp_dir="$(mktemp -d)"
  coakka_runtime_client_tmp_dir="${tmp_dir}"

  package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/${artifact_name}")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${package_path}"

  bundle_root="${tmp_dir}/package/coakka-client-docker-demo-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}"
  coakka_require_file "${bundle_root}/compose.yaml" "The published Docker demo archive is incomplete."
  coakka_runtime_client_compose_dir="${bundle_root}"

  docker compose -f "${bundle_root}/compose.yaml" build >/dev/null
  docker compose -f "${bundle_root}/compose.yaml" up -d customer-service >/dev/null

  output=""
  for _ in $(seq 1 60); do
    if output="$(docker compose -f "${bundle_root}/compose.yaml" run --rm --no-deps -T cli \
        call \
        --host customer-service \
        --port 19091 \
        --route customer.create \
        --payload "customer#42" 2>/dev/null)"; then
      [[ "${output}" == "created:customer#42" ]] && break
    fi
    sleep 0.5
  done
  [[ "${output}" == "created:customer#42" ]] ||
    coakka_die "Docker demo call did not return expected reply."
  printf '%s\n' "${output}"

  output="$(docker compose -f "${bundle_root}/compose.yaml" run --rm --no-deps -T cli \
    ask \
    --host customer-service \
    --port 19091 \
    --route customer.create \
    --payload "customer#ask")"
  [[ "${output}" == "created:customer#ask" ]] ||
    coakka_die "Docker demo ask did not return expected reply."
  printf '%s\n' "${output}"

  coakka_runtime_client_cleanup
}

command_name="${1:-check}"
case "${command_name}" in
  check|smoke)
    coakka_runtime_client_run version --output json
    coakka_runtime_client_run doctor --output json
    ;;
  version)
    coakka_runtime_client_run version --output json
    ;;
  doctor)
    coakka_runtime_client_run doctor --output json
    ;;
  docker-demo)
    coakka_runtime_client_docker_demo
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown runtime-client command: ${command_name}"
    ;;
esac
