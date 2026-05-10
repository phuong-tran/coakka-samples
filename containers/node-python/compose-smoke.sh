#!/usr/bin/env bash
set -euo pipefail

node_port="${COAKKA_SAMPLE_WEB_PORT:-8080}"
store_port="${COAKKA_SAMPLE_STORE_WEB_PORT:-8081}"
customer_id="${COAKKA_SAMPLE_SMOKE_CUSTOMER_ID:-cus_container_smoke_001}"

for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${node_port}/api/runtime" >/dev/null 2>&1 \
    && curl -fsS "http://127.0.0.1:${store_port}/api/state" >/dev/null 2>&1; then
    break
  fi
  if [[ "${i}" == "60" ]]; then
    echo "[containers/node-python] services did not become ready" >&2
    exit 1
  fi
  sleep 0.5
done

create_response="$(
  curl -fsS -X POST "http://127.0.0.1:${node_port}/api/customers" \
    -H "content-type: application/json" \
    --data "{\"id\":\"${customer_id}\",\"name\":\"Container Ada\",\"email\":\"container@example.com\",\"tier\":\"gold\",\"notes\":\"created through compose smoke\"}"
)"
store_state="$(curl -fsS "http://127.0.0.1:${store_port}/api/state")"

node -e '
const customerId = process.argv[1];
const createResponse = JSON.parse(process.argv[2]);
const storeState = JSON.parse(process.argv[3]);
if (createResponse.response?.status !== "ACCEPTED") {
  throw new Error("create response was not ACCEPTED");
}
if (!storeState.store?.customers?.some((customer) => customer.id === customerId)) {
  throw new Error("python store did not receive the customer update");
}
console.log("[containers/node-python] smoke ok: browser path updates Python store through CoAkka runtime");
' "${customer_id}" "${create_response}" "${store_state}"
