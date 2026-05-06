#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

web_build_task=":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-web:bootJar"
web_jar="${repo_root}/runtime/scenarios/customer-crud/spring-boot-spring-boot/customer-web/build/libs/customer-web.jar"
node_artifact_rel="runtime/node/releases/0.1.0+5e25dda67597/coakka-v2-connector-node-0.1.0.tgz"

print_usage() {
  cat <<'EOF'
Spring Boot to Node.js customer CRUD

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

require_node_commands() {
  coakka_require_command node "Install Node.js 20 or newer, then retry."
  coakka_require_command npm "Install npm, then retry."
}

run_web() {
  require_web_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  exec java -jar "${web_jar}" \
    --sample.connector.system-name=customer-web-node-topology \
    --sample.connector.node-id=customer-web-node-topology-node \
    --sample.connector.local-port=19111 \
    --sample.connector.peer-port=19112 \
    --sample.connector.store-http-base-url=http://127.0.0.1:8092
}

run_store() {
  require_node_commands

  local tmp_dir package_path
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT
  package_path="$(coakka_resolve_artifact "${publish_root}" "${node_artifact_rel}" "${tmp_dir}/artifacts/coakka-v2-connector-node-0.1.0.tgz")"
  cp "${script_dir}/store.mjs" "${tmp_dir}/store.mjs"
  cp "${script_dir}/store-index.html" "${tmp_dir}/store-index.html"

  (
    cd "${tmp_dir}"
    npm init -y >/dev/null
    npm install "${package_path}" >/dev/null
    exec node store.mjs
  )
}

check_scenario() {
  require_web_commands
  require_node_commands

  local tmp_dir package_path
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT
  package_path="$(coakka_resolve_artifact "${publish_root}" "${node_artifact_rel}" "${tmp_dir}/artifacts/coakka-v2-connector-node-0.1.0.tgz")"
  cp "${script_dir}/store.mjs" "${tmp_dir}/store.mjs"
  cp "${script_dir}/store-index.html" "${tmp_dir}/store-index.html"

  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  (
    cd "${tmp_dir}"
    npm init -y >/dev/null
    npm install "${package_path}" >/dev/null
    node --check store.mjs
  )
  coakka_note "check ok: built Spring Boot web jar and verified Node.js store"
}

smoke() {
  coakka_require_command curl "Install curl, then retry."

  coakka_customer_smoke_request "create customer through Spring Boot web" \
    -X POST http://127.0.0.1:8081/api/customers \
    -H 'Content-Type: application/json' \
    -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"spring to node smoke"}'

  coakka_customer_smoke_request "list Node.js store customers" \
    http://127.0.0.1:8092/api/customers
}

stop_ports() {
  coakka_stop_ports 8081 8092 19111 19112
}

case "${1:-}" in
  ""|check)
    check_scenario
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
