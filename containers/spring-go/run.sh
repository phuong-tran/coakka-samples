#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

compose_file="${script_dir}/compose.yaml"

print_usage() {
  cat <<'EOF'
Spring Boot JVM web to Go store container sample

Usage:
  bash run.sh containers spring-go
  bash run.sh containers spring-go up
  bash run.sh containers spring-go check
  bash run.sh containers spring-go smoke
  bash run.sh containers spring-go pull
  bash run.sh containers spring-go down
  bash run.sh containers spring-go config

Default command is 'up'. Open http://localhost:8090 and http://localhost:8091.
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

run_compose() {
  local engine command_string
  engine="$(detect_engine)"
  command_string="$(compose_cmd "${engine}")"
  # shellcheck disable=SC2206
  local command_parts=(${command_string})
  "${command_parts[@]}" -f "${compose_file}" "$@"
}

run_check() {
  run_compose config >/dev/null
  coakka_note "check ok: compose config is valid"
}

case "${1:-up}" in
  check)
    run_check
    ;;
  up)
    shift || true
    run_compose up "$@"
    ;;
  smoke)
    bash "${script_dir}/compose-smoke.sh"
    ;;
  pull)
    shift || true
    run_compose pull "$@"
    ;;
  down)
    shift || true
    run_compose down --remove-orphans "$@"
    ;;
  config)
    shift || true
    run_compose config "$@"
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown command: $1"
    ;;
esac
