#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
node_artifact_rel="runtime/node/releases/0.1.0+0cb644340467/coakka-v2-connector-node-0.1.0.tgz"
web_build_task=":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-web:bootJar"
web_jar="${repo_root}/runtime/scenarios/customer-crud/spring-boot-spring-boot/customer-web/build/libs/customer-web.jar"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

print_usage() {
  cat <<'EOF'
Spring Boot to multiple Node.js customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh audit
  bash run.sh store
  bash run.sh web
  bash run.sh smoke
  bash run.sh stop

Default command is 'check'. Run 'audit' and 'store' first, then 'web' in another terminal.
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

prepare_node_workspace() {
  local tmp_dir="$1"
  local package_path
  package_path="$(coakka_resolve_artifact "${publish_root}" "${node_artifact_rel}" "${tmp_dir}/artifacts/coakka-v2-connector-node-0.1.0.tgz")"
  cp "${script_dir}/store.mjs" "${tmp_dir}/store.mjs"
  cp "${script_dir}/audit.mjs" "${tmp_dir}/audit.mjs"
  cp "${script_dir}/store-index.html" "${tmp_dir}/store-index.html"
  cp "${script_dir}/audit-index.html" "${tmp_dir}/audit-index.html"

  (
    cd "${tmp_dir}"
    npm init -y >/dev/null
    npm install "${package_path}" >/dev/null
  )
}

run_web() {
  require_web_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  exec java -jar "${web_jar}" \
    --sample.connector.system-name=customer-web-nodes-topology \
    --sample.connector.node-id=customer-web-nodes-topology-node \
    --sample.connector.local-port=19131 \
    --sample.connector.peer-port=19132 \
    --sample.connector.store-http-base-url=http://127.0.0.1:8092
}

run_node_service() {
  require_node_commands

  local entrypoint="$1"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT
  prepare_node_workspace "${tmp_dir}"

  (
    cd "${tmp_dir}"
    exec node "${entrypoint}"
  )
}

check_nodes() {
  require_web_commands
  require_node_commands

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT
  prepare_node_workspace "${tmp_dir}"

  (
    cd "${tmp_dir}"
    node --check store.mjs
    node --check audit.mjs
  )
  bash "${repo_root}/gradlew" -p "${repo_root}" "${web_build_task}" --quiet
  coakka_note "check ok: built Spring Boot web jar and verified Node.js store/audit"
}

smoke() {
  coakka_require_command curl "Install curl, then retry."

  coakka_customer_smoke_request "create customer through Spring Boot web" \
    -X POST http://127.0.0.1:8081/api/customers \
    -H 'Content-Type: application/json' \
    -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"spring to nodes smoke"}'

  coakka_customer_smoke_request "list Node.js store customers" \
    http://127.0.0.1:8092/api/customers

  coakka_customer_smoke_request "list Node.js audit events" \
    http://127.0.0.1:8094/api/audit/events
}

stop_ports() {
  coakka_stop_ports 8081 8092 8094 19131 19132 19134
}

case "${1:-}" in
  ""|check)
    check_nodes
    ;;
  audit)
    run_node_service audit.mjs
    ;;
  store)
    run_node_service store.mjs
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
