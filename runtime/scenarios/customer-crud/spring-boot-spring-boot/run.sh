#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

web_build_task=":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-web:bootJar"
store_build_task=":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-store:bootJar"
web_jar="${script_dir}/customer-web/build/libs/customer-web.jar"
store_jar="${script_dir}/customer-store/build/libs/customer-store.jar"

print_usage() {
  cat <<'EOF'
Spring Boot to Spring Boot customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh store
  bash run.sh web
  bash run.sh build
  bash run.sh smoke
  bash run.sh stop

Default command is 'check'. Run 'store' first, then 'web' in another terminal.
EOF
}

require_runtime_commands() {
  coakka_require_file "${repo_root}/gradlew" "Run from a full coakka-samples checkout."
  coakka_require_command java "Install JDK 17 or newer, then retry."
}

build_jars() {
  require_runtime_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" "${store_build_task}" --quiet
  coakka_note "check ok: built Spring Boot web and store jars"
}

smoke() {
  coakka_require_command curl "Install curl, then retry."

  coakka_customer_smoke_request "create customer" \
    -X POST http://127.0.0.1:8081/api/customers \
    -H 'Content-Type: application/json' \
    -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"smoke"}'

  coakka_customer_smoke_request "update customer" \
    -X PUT http://127.0.0.1:8081/api/customers/cust-001 \
    -H 'Content-Type: application/json' \
    -d '{"name":"Ada Lovelace","email":"ada@example.com","tier":"gold","notes":"updated"}'

  coakka_customer_smoke_request "list customers" \
    http://127.0.0.1:8081/api/customers

  coakka_customer_smoke_request "delete customer" \
    -X DELETE http://127.0.0.1:8081/api/customers/cust-001
}

stop_ports() {
  coakka_stop_ports 8081 8082 19101 19102
}

case "${1:-}" in
  ""|check)
    build_jars
    ;;
  store)
    require_runtime_commands
    bash "${repo_root}/gradlew" -p "${repo_root}" "${store_build_task}" --quiet
    exec java -jar "${store_jar}"
    ;;
  web)
    require_runtime_commands
    bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
    exec java -jar "${web_jar}"
    ;;
  build)
    build_jars
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
