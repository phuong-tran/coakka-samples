#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

app_build_task=":runtime:scenarios:customer-crud:spring-boot-single-process:customer-app:bootJar"
app_jar="${script_dir}/customer-app/build/libs/customer-app.jar"

print_usage() {
  cat <<'EOF'
Spring Boot single-process customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh dev
  bash run.sh app
  bash run.sh build
  bash run.sh smoke
  bash run.sh stop

Default command is 'check'. Use 'dev' to build and run the single Spring Boot app.
EOF
}

require_runtime_commands() {
  coakka_require_file "${repo_root}/gradlew" "Run from a full coakka-samples checkout."
  coakka_require_command java "Install JDK 17 or newer, then retry."
}

build_jar() {
  require_runtime_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${app_build_task}" --quiet
  coakka_note "check ok: built Spring Boot single-process customer app jar"
}

run_dev() {
  build_jar
  stop_ports
  coakka_note "dev running: open http://localhost:8081"
  exec java -jar "${app_jar}"
}

smoke() {
  coakka_require_command curl "Install curl, then retry."

  coakka_customer_smoke_request "read runtime diagnostics" \
    http://127.0.0.1:8081/api/customers/runtime

  coakka_customer_smoke_request "create customer through local runtime target" \
    -X POST http://127.0.0.1:8081/api/customers \
    -H 'Content-Type: application/json' \
    -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"smoke"}'

  coakka_customer_smoke_request "update customer through local runtime target" \
    -X PUT http://127.0.0.1:8081/api/customers/cust-001 \
    -H 'Content-Type: application/json' \
    -d '{"name":"Ada Lovelace","email":"ada@example.com","tier":"gold","notes":"updated"}'

  coakka_customer_smoke_request "list customers through local runtime target" \
    http://127.0.0.1:8081/api/customers

  coakka_customer_smoke_request "delete customer through local runtime target" \
    -X DELETE http://127.0.0.1:8081/api/customers/cust-001

  coakka_customer_smoke_request "trigger route-miss diagnostic" \
    -X POST http://127.0.0.1:8081/api/customers/route-miss
}

stop_ports() {
  coakka_stop_ports 8081 19141 19142
}

case "${1:-}" in
  ""|check)
    build_jar
    ;;
  app|dev)
    run_dev
    ;;
  build)
    build_jar
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
