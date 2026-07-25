#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
module_path="github.com/phuong-tran/coakka-runtime-go"
web_build_task=":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-web:bootJar"
web_jar="${repo_root}/runtime/scenarios/customer-crud/spring-boot-spring-boot/customer-web/build/libs/customer-web.jar"
source "${repo_root}/scripts/sample-utils.sh"

print_usage() {
  cat <<'EOF'
Spring Boot to Go customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh dev
  bash run.sh store
  bash run.sh web
  bash run.sh smoke
  bash run.sh stop

Default command is 'check'. Run 'store' first, then 'web' in another terminal.
Use 'dev' to build and run both processes from one shell.
EOF
}

require_web_commands() {
  coakka_require_file "${repo_root}/gradlew" "Run from a full coakka-samples checkout."
  coakka_require_command java "Install JDK 17 or newer, then retry."
}

require_go_commands() {
  coakka_require_command go "Install Go 1.23 or newer, then retry."
}

prepare_go_workspace() {
  local tmp_dir="$1"
  cp "${script_dir}/store.go" "${tmp_dir}/store.go"

  cat > "${tmp_dir}/go.mod" <<EOF
module coakka-runtime-spring-boot-go-store

go 1.23.0

require ${module_path} v1.3.6
EOF
}

run_dev() {
  require_web_commands
  require_go_commands

  local tmp_dir store_pid web_pid
  tmp_dir="$(mktemp -d)"
  prepare_go_workspace "${tmp_dir}"
  (
    cd "${tmp_dir}"
    go mod tidy >/dev/null
    go build -o customer-store-go .
  )
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  stop_ports

  (cd "${tmp_dir}" && ./customer-store-go) &
  store_pid="$!"
  sleep 2
  java -jar "${web_jar}" \
    --sample.connector.system-name=customer-web-go-topology \
    --sample.connector.node-id=customer-web-go-topology-node \
    --sample.connector.local-port=19121 \
    --sample.connector.peer-port=19122 &
  web_pid="$!"

  cleanup() {
    if [[ -n "${web_pid:-}" ]]; then
      kill "${web_pid}" 2>/dev/null || true
    fi
    if [[ -n "${store_pid:-}" ]]; then
      kill "${store_pid}" 2>/dev/null || true
    fi
    if [[ -n "${tmp_dir:-}" ]]; then
      rm -rf "${tmp_dir}"
    fi
  }
  trap cleanup EXIT INT TERM

  coakka_note "dev running: open http://localhost:8081"
  while kill -0 "${web_pid}" 2>/dev/null && kill -0 "${store_pid}" 2>/dev/null; do
    sleep 1
  done
  cleanup
  trap - EXIT INT TERM
  wait "${web_pid}" "${store_pid}" 2>/dev/null || true
}

run_web() {
  require_web_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  exec java -jar "${web_jar}" \
    --sample.connector.system-name=customer-web-go-topology \
    --sample.connector.node-id=customer-web-go-topology-node \
    --sample.connector.local-port=19121 \
    --sample.connector.peer-port=19122
}

run_store() {
  require_go_commands

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT
  prepare_go_workspace "${tmp_dir}"

  (
    cd "${tmp_dir}"
    go mod tidy >/dev/null
    exec go run .
  )
}

check_store() {
  require_web_commands
  require_go_commands

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT
  prepare_go_workspace "${tmp_dir}"

  (
    cd "${tmp_dir}"
    gofmt -w store.go
    go mod tidy >/dev/null
    go build .
  )
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  coakka_note "check ok: built Spring Boot web jar and compiled Go store"
}

smoke() {
  coakka_require_command curl "Install curl, then retry."

  coakka_customer_smoke_request "read runtime diagnostics" \
    http://127.0.0.1:8081/api/customers/runtime

  coakka_customer_smoke_request "trigger route-miss diagnostic" \
    -X POST http://127.0.0.1:8081/api/customers/route-miss

  coakka_customer_smoke_request "create customer through runtime message" \
    -X POST http://127.0.0.1:8081/api/customers \
    -H 'Content-Type: application/json' \
    -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"spring to go smoke"}'

  coakka_customer_smoke_request "update customer through runtime message" \
    -X PUT http://127.0.0.1:8081/api/customers/cust-001 \
    -H 'Content-Type: application/json' \
    -d '{"name":"Ada Lovelace","email":"ada@example.com","tier":"gold","notes":"updated"}'

  coakka_customer_smoke_request "list customers through runtime message" \
    http://127.0.0.1:8081/api/customers

  coakka_customer_smoke_request "delete customer through runtime message" \
    -X DELETE http://127.0.0.1:8081/api/customers/cust-001
}

stop_ports() {
  coakka_stop_ports 8081 19121 19122
}

case "${1:-}" in
  ""|check)
    check_store
    ;;
  store)
    run_store
    ;;
  dev)
    run_dev
    ;;
  web)
    run_web
    ;;
  smoke)
    smoke
    ;;
  stop)
    stop_ports
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown command: $1"
    ;;
esac
