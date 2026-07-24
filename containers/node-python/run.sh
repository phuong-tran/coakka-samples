#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

compose_file="${script_dir}/compose.yaml"
build_compose_file="${script_dir}/compose.build.yaml"
runtime_base_dockerfile="${repo_root}/containers/runtime-base/Dockerfile"
runtime_base_image="${COAKKA_RUNTIME_BASE_IMAGE:-coakka/runtime-base:1.3.1-0da8c2d9-local}"
artifact_manifest_sha256="${COAKKA_ARTIFACT_MANIFEST_SHA256:-a86a74b0e2299c5ad040454edd7fffe3b2e0e871959d6e01d659e9865361669f}"

print_usage() {
  cat <<'EOF'
Node.js client to Python service container sample

Usage:
  bash run.sh containers node-python
  bash run.sh containers node-python up
  bash run.sh containers node-python build
  bash run.sh containers node-python check
  bash run.sh containers node-python smoke
  bash run.sh containers node-python pull
  bash run.sh containers node-python down
  bash run.sh containers node-python config

Default command is 'up'. Open http://localhost:8080 and http://localhost:8081.
EOF
}

detect_engine() {
  if [[ -n "${COAKKA_CONTAINER_ENGINE:-}" ]]; then
    printf '%s\n' "${COAKKA_CONTAINER_ENGINE}"
    return 0
  fi
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    printf 'docker\n'
    return 0
  fi
  if command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
    printf 'podman\n'
    return 0
  fi
  if command -v docker >/dev/null 2>&1; then
    coakka_die "Docker CLI is installed, but no Docker daemon is reachable. Start Docker Desktop, set DOCKER_HOST, or start Podman with 'podman machine start'."
  fi
  coakka_die "Missing docker or podman. Install Docker Engine or Podman, then retry."
}

compose_cmd() {
  local engine="$1"
  case "${engine}" in
    docker)
      coakka_require_command docker "Install Docker Engine with the compose plugin, then retry."
      if docker compose version >/dev/null 2>&1; then
        printf '%s\n' "docker compose"
      elif command -v docker-compose >/dev/null 2>&1; then
        printf '%s\n' "docker-compose"
      else
        coakka_die "Docker is present, but neither 'docker compose' nor 'docker-compose' is available."
      fi
      ;;
    podman)
      coakka_require_command podman "Install Podman, then retry."
      if command -v podman-compose >/dev/null 2>&1; then
        printf '%s\n' "podman-compose"
      elif podman compose version >/dev/null 2>&1; then
        printf '%s\n' "podman compose"
      else
        coakka_die "Podman is present, but neither 'podman compose' nor 'podman-compose' is available."
      fi
      ;;
    *)
      coakka_die "Unsupported COAKKA_CONTAINER_ENGINE='${engine}'. Use docker or podman."
      ;;
  esac
}

detect_platform() {
  if [[ -n "${COAKKA_CONTAINER_PLATFORM:-}" ]]; then
    printf '%s\n' "${COAKKA_CONTAINER_PLATFORM}"
    return 0
  fi

  case "$(uname -m)" in
    arm64|aarch64)
      printf 'linux/arm64\n'
      ;;
    x86_64|amd64)
      printf 'linux/amd64\n'
      ;;
    *)
      coakka_die "Unsupported host architecture '$(uname -m)'. Set COAKKA_CONTAINER_PLATFORM explicitly."
      ;;
  esac
}

run_compose() {
  local compose_path="$1"
  shift
  local engine command_string
  engine="$(detect_engine)"
  command_string="$(compose_cmd "${engine}")"
  # shellcheck disable=SC2206
  local command_parts=(${command_string})
  "${command_parts[@]}" -f "${compose_path}" "$@"
}

run_build() {
  local engine platform
  engine="$(detect_engine)"
  platform="$(detect_platform)"
  coakka_note "building ${runtime_base_image} for ${platform}"
  "${engine}" build \
    --platform "${platform}" \
    -f "${runtime_base_dockerfile}" \
    --build-arg "COAKKA_PUBLISH_RAW_BASE=${COAKKA_PUBLISH_RAW_BASE:-https://raw.githubusercontent.com/phuong-tran/coakka-publish/main}" \
    --build-arg "COAKKA_ARTIFACT_MANIFEST_SHA256=${artifact_manifest_sha256}" \
    -t "${runtime_base_image}" \
    "${repo_root}"
  export COAKKA_RUNTIME_BASE_IMAGE="${runtime_base_image}"
  run_compose "${build_compose_file}" build "$@"
}

run_check() {
  run_compose "${compose_file}" config >/dev/null
  coakka_note "check ok: compose config is valid"
}

case "${1:-up}" in
  check)
    run_check
    ;;
  up)
    shift || true
    run_compose "${compose_file}" up "$@"
    ;;
  build)
    shift || true
    run_build "$@"
    ;;
  smoke)
    bash "${script_dir}/compose-smoke.sh"
    ;;
  pull)
    shift || true
    run_compose "${compose_file}" pull "$@"
    ;;
  down)
    shift || true
    run_compose "${compose_file}" down --remove-orphans "$@"
    ;;
  config)
    shift || true
    run_compose "${compose_file}" config "$@"
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown command: $1"
    ;;
esac
