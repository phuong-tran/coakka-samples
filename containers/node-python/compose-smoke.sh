#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

node_port="${COAKKA_SAMPLE_WEB_PORT:-8080}"
store_port="${COAKKA_SAMPLE_STORE_WEB_PORT:-8081}"
customer_id="${COAKKA_SAMPLE_SMOKE_CUSTOMER_ID:-cus_container_smoke_001}"
expected_runtime_git_commit="${COAKKA_SAMPLE_EXPECT_RUNTIME_GIT_COMMIT:-}"
python_bin="$(coakka_python_bin)"

coakka_require_command "${python_bin}" "Install Python 3, or set COAKKA_PYTHON to a Python 3 executable for smoke validation."

bash "${script_dir}/run.sh" up -d

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

runtime_state="$(curl -fsS "http://127.0.0.1:${node_port}/api/runtime")"
create_response="$(
  curl -fsS -X POST "http://127.0.0.1:${node_port}/api/customers" \
    -H "content-type: application/json" \
    --data "{\"id\":\"${customer_id}\",\"name\":\"Container Ada\",\"email\":\"container@example.com\",\"tier\":\"gold\",\"notes\":\"created through compose smoke\"}"
)"
store_state="$(curl -fsS "http://127.0.0.1:${store_port}/api/state")"

"${python_bin}" - "${customer_id}" "${expected_runtime_git_commit}" "${runtime_state}" "${create_response}" "${store_state}" <<'PY'
import json
import sys

customer_id = sys.argv[1]
expected_runtime_git_commit = sys.argv[2]
runtime_state = json.loads(sys.argv[3])
create_response = json.loads(sys.argv[4])
store_state = json.loads(sys.argv[5])

runtime_info = runtime_state.get("runtime", {})
if runtime_info.get("southboundBackend") == "stub":
    raise SystemExit("runtime backend is still stub; publish a TCP-enabled runtime artifact before treating container smoke as passing")

remote_wire_profile = runtime_info.get("remoteWireProfile")
if remote_wire_profile is not None and remote_wire_profile in ("", "none"):
    raise SystemExit("runtime remote wire profile is not active")

if expected_runtime_git_commit and runtime_info.get("gitCommit") != expected_runtime_git_commit:
    raise SystemExit(
        f"runtime git commit mismatch: expected {expected_runtime_git_commit}, got {runtime_info.get('gitCommit')}"
    )

if create_response.get("response", {}).get("status") != "ACCEPTED":
    raise SystemExit("create response was not ACCEPTED")

customers = store_state.get("store", {}).get("customers", [])
if not any(customer.get("id") == customer_id for customer in customers):
    raise SystemExit("python store did not receive the customer update")

print("[containers/node-python] smoke ok: browser path updates Python store through CoAkka runtime")
PY
