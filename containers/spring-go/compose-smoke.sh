#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

bash "${script_dir}/run.sh" up -d

for _ in $(seq 1 40); do
  if curl -fsS http://localhost:8090/api/runtime >/dev/null 2>&1 &&
    curl -fsS http://localhost:8091/api/state >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

coakka_customer_smoke_request "read Spring runtime diagnostics" \
  http://localhost:8090/api/runtime

coakka_customer_smoke_request "create customer through Spring to Go runtime path" \
  -X POST http://localhost:8090/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"gold","notes":"spring go container smoke"}'

coakka_customer_smoke_request "ping Spring through Go runtime path" \
  -X POST http://localhost:8091/api/ping-spring

coakka_customer_smoke_request "read Go store state" \
  http://localhost:8091/api/state

coakka_customer_expect_http_status "trigger intentional route-miss diagnostic" "200" \
  -X POST http://localhost:8090/api/route-miss

coakka_note "spring-go smoke ok"
