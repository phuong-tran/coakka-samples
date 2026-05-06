#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
module_path="github.com/phuong-tran/coakka-runtime-go"
go_artifact_rel="runtime/go/releases/0.1.0+0cb644340467/coakka-v2-connector-go-0.1.0.tar.gz"
web_build_task=":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-web:bootJar"
web_jar="${repo_root}/runtime/scenarios/customer-crud/spring-boot-spring-boot/customer-web/build/libs/customer-web.jar"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

print_usage() {
  cat <<'EOF'
Spring Boot to Go customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh store
  bash run.sh web
  bash run.sh smoke
  bash run.sh stop

Default command is 'check'. Run 'store' first, then 'web' in another terminal.
EOF
}

require_web_commands() {
  coakka_require_file "${repo_root}/gradlew" "Run from a full coakka-samples checkout."
  coakka_require_command java "Install JDK 17 or newer, then retry."
}

require_go_commands() {
  coakka_require_command go "Install Go 1.23 or newer, then retry."
  coakka_require_command tar "Install tar, then retry."
}

prepare_go_workspace() {
  local tmp_dir="$1"
  local package_path
  package_path="$(coakka_resolve_artifact "${publish_root}" "${go_artifact_rel}" "${tmp_dir}/artifacts/coakka-v2-connector-go-0.1.0.tar.gz")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"
  cp "${script_dir}/store.go" "${tmp_dir}/store.go"
  cp "${script_dir}/store-index.html" "${tmp_dir}/store-index.html"

  cat > "${tmp_dir}/go.mod" <<EOF
module coakka-runtime-spring-boot-go-store

go 1.23.0

require ${module_path} v0.0.0

replace ${module_path} => ./package
EOF
}

run_web() {
  require_web_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  exec java -jar "${web_jar}" \
    --sample.connector.system-name=customer-web-go-topology \
    --sample.connector.node-id=customer-web-go-topology-node \
    --sample.connector.local-port=19121 \
    --sample.connector.peer-port=19122 \
    --sample.connector.store-http-base-url=http://127.0.0.1:8093
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

  coakka_customer_smoke_request "create customer through Spring Boot web" \
    -X POST http://127.0.0.1:8081/api/customers \
    -H 'Content-Type: application/json' \
    -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"spring to go smoke"}'

  coakka_customer_smoke_request "list Go store customers" \
    http://127.0.0.1:8093/api/customers
}

stop_ports() {
  coakka_stop_ports 8081 8093 19121 19122
}

case "${1:-}" in
  ""|check)
    check_store
    ;;
  store)
    run_store
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
