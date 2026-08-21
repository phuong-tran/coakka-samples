#!/usr/bin/env bash
set -euo pipefail

client_bin="/opt/coakka/bin/coakka-client"
service_bin="/opt/coakka-demo/customer-service/bin/coakka-demo-customer-service"
runtime_pids=()

print_usage() {
  cat <<'EOF'
coakka-runtime-client Docker Hub demo

Usage:
  docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:<version>-<core-sha8>-remote
  docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:<version>-<core-sha8>-remote walkthrough
  docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:<version>-<core-sha8>-remote client --help
  docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:<version>-<core-sha8>-remote client version --output json

The default walkthrough starts two native runtime services inside the container
and drives both with coakka-client.
EOF
}

cleanup() {
  local pid
  for pid in "${runtime_pids[@]}"; do
    kill "${pid}" >/dev/null 2>&1 || true
  done
  wait >/dev/null 2>&1 || true
}

start_runtime_service() {
  local service_name="$1"
  local port="$2"
  local route="$3"
  local reply_prefix="$4"

  env \
    COAKKA_V2_PROBE_DOCKER_RESPONDER_PORT="${port}" \
    COAKKA_V2_PROBE_DOCKER_RESPONDER_ROUTE_TARGET="${route}" \
    COAKKA_V2_PROBE_DOCKER_RESPONDER_TEXT_REPLY_PREFIX="${reply_prefix}" \
    "${service_bin}" >"/tmp/${service_name}.log" 2>&1 &
  runtime_pids+=("$!")
}

wait_for_runtime() {
  local service_name="$1"
  local host="$2"
  local port="$3"
  local route="$4"
  local payload="$5"
  local expected="$6"
  local output

  for _ in $(seq 1 60); do
    if output="$("${client_bin}" call \
        --host "${host}" \
        --port "${port}" \
        --route "${route}" \
        --payload "${payload}" 2>/dev/null)"; then
      [[ "${output}" == "${expected}" ]] && return 0
    fi
    sleep 0.2
  done

  echo "[coakka-runtime-client-demo] ${service_name} did not become ready" >&2
  cat "/tmp/${service_name}.log" >&2 || true
  return 1
}

run_client() {
  local label="$1"
  shift
  echo
  echo "[coakka-client] ${label}"
  "${client_bin}" "$@"
}

run_walkthrough() {
  local runtime_host
  runtime_host="${COAKKA_RUNTIME_CLIENT_DEMO_HOST:-$(hostname -i | awk '{print $1}')}"
  if [[ -z "${runtime_host}" ]]; then
    runtime_host="127.0.0.1"
  fi

  trap cleanup EXIT INT TERM

  echo "coakka-runtime-client Docker Hub demo"
  echo
  echo "client:"
  echo "  command=coakka-client"
  echo
  echo "native runtime services:"
  echo "  service=customer-east host=${runtime_host} port=19091 route=customer.east.create reply_prefix=east-created:"
  echo "  service=customer-west host=${runtime_host} port=19092 route=customer.west.create reply_prefix=west-created:"
  echo
  echo "[runtime] starting native runtime services"

  start_runtime_service customer-east 19091 customer.east.create east-created:
  start_runtime_service customer-west 19092 customer.west.create west-created:
  wait_for_runtime customer-east "${runtime_host}" 19091 customer.east.create customer#ready-east east-created:customer#ready-east
  wait_for_runtime customer-west "${runtime_host}" 19092 customer.west.create customer#ready-west west-created:customer#ready-west

  run_client \
    "call service=customer-east port=19091 route=customer.east.create" \
    call \
    --host "${runtime_host}" \
    --port 19091 \
    --route customer.east.create \
    --payload customer#east

  run_client \
    "ask service=customer-west port=19092 route=customer.west.create" \
    ask \
    --host "${runtime_host}" \
    --port 19092 \
    --route customer.west.create \
    --payload customer#west

  local shell_script
  shell_script="$(mktemp)"
  cat >"${shell_script}" <<EOF
connect ${runtime_host}:19091
route customer.east.create
payload customer#shell-east
call
connect ${runtime_host}:19092
route customer.west.create
payload customer#shell-west
ask
EOF

  run_client \
    "shell session switches customer-east -> customer-west" \
    shell \
    --host "${runtime_host}" \
    --port 19091 \
    --script "${shell_script}"

  rm -f "${shell_script}"
}

case "${1:-walkthrough}" in
  walkthrough)
    shift || true
    if [[ "$#" -ne 0 ]]; then
      echo "[coakka-runtime-client-demo] walkthrough does not take arguments" >&2
      exit 64
    fi
    run_walkthrough
    ;;
  client)
    shift
    exec "${client_bin}" "$@"
    ;;
  service)
    shift
    exec "${service_bin}" "$@"
    ;;
  help|--help|-h)
    print_usage
    ;;
  *)
    exec "${client_bin}" "$@"
    ;;
esac
