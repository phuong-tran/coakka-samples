#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

COAKKA_RUNTIME_CLIENT_RELEASE_ID="1.3.1+0da8c2d9"
COAKKA_RUNTIME_CLIENT_DOCKER_BUNDLE_RELEASE_ID="1.3.1+0da8c2d9"
COAKKA_RUNTIME_CLIENT_VERSION="1.3.1"
COAKKA_RUNTIME_CLIENT_DEMO_IMAGE_DEFAULT="docker.io/gabrielgun1983/coakka-runtime-client-demo:1.3.1-0da8c2d9-remote"
coakka_runtime_client_tmp_dir=""
coakka_runtime_client_compose_dir=""
coakka_runtime_client_compose_override=""
coakka_runtime_client_compose_project=""

coakka_runtime_client_cleanup() {
  if [[ -n "${coakka_runtime_client_compose_dir}" &&
        -f "${coakka_runtime_client_compose_dir}/compose.yaml" &&
        -n "$(command -v docker 2>/dev/null || true)" ]]; then
    if [[ -n "${coakka_runtime_client_compose_override}" &&
          -f "${coakka_runtime_client_compose_override}" &&
          -n "${coakka_runtime_client_compose_project}" ]]; then
      docker compose \
        -p "${coakka_runtime_client_compose_project}" \
        -f "${coakka_runtime_client_compose_dir}/compose.yaml" \
        -f "${coakka_runtime_client_compose_override}" \
        down -v >/dev/null 2>&1 || true
    else
      docker compose -f "${coakka_runtime_client_compose_dir}/compose.yaml" down -v >/dev/null 2>&1 || true
    fi
    coakka_runtime_client_compose_dir=""
    coakka_runtime_client_compose_override=""
    coakka_runtime_client_compose_project=""
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
  bash run.sh runtime-client docker-bundle
  bash run.sh runtime-client docker-walkthrough
  bash run.sh runtime-client dockerhub-demo

Environment:
  COAKKA_PUBLISH_ROOT       local coakka-publish checkout
  COAKKA_PUBLISH_RAW_BASE   raw public fallback URL
  COAKKA_RUNTIME_CLIENT_DEMO_IMAGE Docker Hub demo image tag
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
    *) coakka_die "Unsupported Docker bundle host architecture: $(uname -m)" ;;
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

coakka_runtime_client_resolve_docker_bundle() {
  local platform="$1"
  local tmp_dir="$2"
  local artifact_name artifact_rel package_path bundle_root
  artifact_name="coakka-client-docker-demo-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}.tar.gz"
  artifact_rel="demo/coakka-client/releases/${COAKKA_RUNTIME_CLIENT_DOCKER_BUNDLE_RELEASE_ID}/${artifact_name}"

  package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/${artifact_name}")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${package_path}"

  bundle_root="${tmp_dir}/package/coakka-client-docker-demo-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}"
  coakka_require_file "${bundle_root}/compose.yaml" "The published Docker verification archive is incomplete."
  printf '%s\n' "${bundle_root}"
}

coakka_runtime_client_print_docker_walkthrough_plan() {
  cat <<'EOF'
coakka-runtime-client Docker walkthrough

client:
  service=cli
  command=coakka-client

native runtime services:
  service=customer-east port=19091 route=customer.east.create reply_prefix=east-created:
  service=customer-west port=19091 route=customer.west.create reply_prefix=west-created:

flow:
  1. build two tiny native runtime service images from the published bundle
  2. start both runtime services on the Docker network
  3. run coakka-client call/ask from the CLI container
  4. run one shell script that switches endpoint inside the same CLI session
EOF
}

coakka_runtime_client_write_walkthrough_override() {
  local override_path="$1"
  cat >"${override_path}" <<'EOF'
services:
  cli:
    depends_on:
      - customer-east
      - customer-west

  customer-east:
    build:
      context: .
      dockerfile: images/customer-service.Dockerfile
    environment:
      COAKKA_V2_PROBE_DOCKER_RESPONDER_PORT: "19091"
      COAKKA_V2_PROBE_DOCKER_RESPONDER_ROUTE_TARGET: "customer.east.create"
      COAKKA_V2_PROBE_DOCKER_RESPONDER_TEXT_REPLY_PREFIX: "east-created:"
    expose:
      - "19091"

  customer-west:
    build:
      context: .
      dockerfile: images/customer-service.Dockerfile
    environment:
      COAKKA_V2_PROBE_DOCKER_RESPONDER_PORT: "19091"
      COAKKA_V2_PROBE_DOCKER_RESPONDER_ROUTE_TARGET: "customer.west.create"
      COAKKA_V2_PROBE_DOCKER_RESPONDER_TEXT_REPLY_PREFIX: "west-created:"
    expose:
      - "19091"
EOF
}

coakka_runtime_client_write_walkthrough_shell_script() {
  local script_path="$1"
  cat >"${script_path}" <<'EOF'
connect customer-east:19091
route customer.east.create
payload customer#shell-east
call
connect customer-west:19091
route customer.west.create
payload customer#shell-west
ask
EOF
}

coakka_runtime_client_tail_compose_log() {
  local compose_log="$1"
  if [[ -f "${compose_log}" ]]; then
    echo "[docker] compose log tail:" >&2
    tail -n 80 "${compose_log}" >&2
  fi
}

coakka_runtime_client_docker_demo() {
  local platform artifact_name artifact_rel tmp_dir package_path bundle_root output
  platform="$(coakka_runtime_client_docker_platform)"
  artifact_name="coakka-client-docker-demo-v2-${COAKKA_RUNTIME_CLIENT_VERSION}-${platform}.tar.gz"
  artifact_rel="demo/coakka-client/releases/${COAKKA_RUNTIME_CLIENT_DOCKER_BUNDLE_RELEASE_ID}/${artifact_name}"

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
  coakka_require_file "${bundle_root}/compose.yaml" "The published Docker verification archive is incomplete."
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
    coakka_die "Docker bundle call did not return expected reply."
  printf '%s\n' "${output}"

  output="$(docker compose -f "${bundle_root}/compose.yaml" run --rm --no-deps -T cli \
    ask \
    --host customer-service \
    --port 19091 \
    --route customer.create \
    --payload "customer#ask")"
  [[ "${output}" == "created:customer#ask" ]] ||
    coakka_die "Docker bundle ask did not return expected reply."
  printf '%s\n' "${output}"

  output="$(docker compose -f "${bundle_root}/compose.yaml" run --rm --no-deps -T cli \
    shell \
    --host customer-service \
    --port 19091 \
    --script payloads/customer-create-shell.coakka)"
  grep -Fq 'created:customer#script' <<<"${output}" ||
    coakka_die "Docker bundle shell script did not return the expected call reply."
  grep -Fq 'created:customer#script-ask' <<<"${output}" ||
    coakka_die "Docker bundle shell script did not return the expected ask reply."
  grep -Fq '"payload_text": "created:{\"customer_id\":\"script\",\"tier\":\"violet\"}' <<<"${output}" ||
    coakka_die "Docker bundle shell script did not return the expected JSON reply."
  printf '%s\n' "${output}"

  coakka_runtime_client_cleanup
}

coakka_runtime_client_docker_walkthrough() {
  local platform tmp_dir bundle_root override_path project_name shell_script compose_log output
  platform="$(coakka_runtime_client_docker_platform)"

  coakka_require_command docker "Install Docker with the Compose plugin, then retry."
  coakka_require_command tar "Install tar, then retry."
  docker compose version >/dev/null 2>&1 ||
    coakka_die "Docker Compose plugin is required. Verify 'docker compose version' works."

  coakka_runtime_client_cleanup
  tmp_dir="$(mktemp -d)"
  coakka_runtime_client_tmp_dir="${tmp_dir}"
  bundle_root="$(coakka_runtime_client_resolve_docker_bundle "${platform}" "${tmp_dir}")"
  override_path="${bundle_root}/compose.walkthrough.yaml"
  shell_script="${bundle_root}/payloads/two-runtime-walkthrough.coakka"
  compose_log="${tmp_dir}/docker-compose-walkthrough.log"
  project_name="coakka-client-walkthrough-${platform//_/-}-$$"

  coakka_runtime_client_write_walkthrough_override "${override_path}"
  coakka_runtime_client_write_walkthrough_shell_script "${shell_script}"
  coakka_runtime_client_compose_dir="${bundle_root}"
  coakka_runtime_client_compose_override="${override_path}"
  coakka_runtime_client_compose_project="${project_name}"

  coakka_runtime_client_print_docker_walkthrough_plan
  echo
  echo "[docker] building cli, customer-east, customer-west"
  docker compose \
    -p "${project_name}" \
    -f "${bundle_root}/compose.yaml" \
    -f "${override_path}" \
    build cli customer-east customer-west >>"${compose_log}" 2>&1

  echo "[docker] starting native runtime services"
  docker compose \
    -p "${project_name}" \
    -f "${bundle_root}/compose.yaml" \
    -f "${override_path}" \
    up -d customer-east customer-west >>"${compose_log}" 2>&1

  echo
  echo "[coakka-client] call service=customer-east port=19091 route=customer.east.create"
  output="$(docker compose \
    -p "${project_name}" \
    -f "${bundle_root}/compose.yaml" \
    -f "${override_path}" \
    run --rm --no-deps -T cli \
      call \
      --host customer-east \
      --port 19091 \
      --route customer.east.create \
      --payload "customer#east" 2>>"${compose_log}")"
  if [[ "${output}" != "east-created:customer#east" ]]; then
    coakka_runtime_client_tail_compose_log "${compose_log}"
    coakka_die "Docker walkthrough east call did not return expected reply."
  fi
  printf '%s\n' "${output}"

  echo
  echo "[coakka-client] ask service=customer-west port=19091 route=customer.west.create"
  output="$(docker compose \
    -p "${project_name}" \
    -f "${bundle_root}/compose.yaml" \
    -f "${override_path}" \
    run --rm --no-deps -T cli \
      ask \
      --host customer-west \
      --port 19091 \
      --route customer.west.create \
      --payload "customer#west" 2>>"${compose_log}")"
  if [[ "${output}" != "west-created:customer#west" ]]; then
    coakka_runtime_client_tail_compose_log "${compose_log}"
    coakka_die "Docker walkthrough west ask did not return expected reply."
  fi
  printf '%s\n' "${output}"

  echo
  echo "[coakka-client] shell session switches customer-east -> customer-west"
  output="$(docker compose \
    -p "${project_name}" \
    -f "${bundle_root}/compose.yaml" \
    -f "${override_path}" \
    run --rm --no-deps -T cli \
      shell \
      --host customer-east \
      --port 19091 \
      --script payloads/two-runtime-walkthrough.coakka 2>>"${compose_log}")"
  if ! grep -Fq 'east-created:customer#shell-east' <<<"${output}"; then
    coakka_runtime_client_tail_compose_log "${compose_log}"
    coakka_die "Docker walkthrough shell did not return the expected east reply."
  fi
  if ! grep -Fq 'west-created:customer#shell-west' <<<"${output}"; then
    coakka_runtime_client_tail_compose_log "${compose_log}"
    coakka_die "Docker walkthrough shell did not return the expected west reply."
  fi
  printf '%s\n' "${output}"

  coakka_runtime_client_cleanup
}

coakka_runtime_client_dockerhub_demo() {
  local image="${COAKKA_RUNTIME_CLIENT_DEMO_IMAGE:-${COAKKA_RUNTIME_CLIENT_DEMO_IMAGE_DEFAULT}}"

  coakka_require_command docker "Install Docker, then retry."
  echo "[coakka-runtime-client] docker image=${image}"
  docker run --rm "${image}" "$@"
}

coakka_runtime_client_dockerhub_build_push() {
  COAKKA_RUNTIME_CLIENT_DEMO_IMAGE="${COAKKA_RUNTIME_CLIENT_DEMO_IMAGE:-${COAKKA_RUNTIME_CLIENT_DEMO_IMAGE_DEFAULT}}" \
    bash "${script_dir}/dockerhub-demo/build-and-push.sh"
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
  docker-bundle|docker-demo)
    coakka_runtime_client_docker_demo
    ;;
  docker-walkthrough|docker-cli)
    coakka_runtime_client_docker_walkthrough
    ;;
  dockerhub-demo|dockerhub)
    shift || true
    coakka_runtime_client_dockerhub_demo "$@"
    ;;
  dockerhub-build-push)
    coakka_runtime_client_dockerhub_build_push
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown runtime-client command: ${command_name}"
    ;;
esac
