#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
scenario_dir="${repo_root}/runtime/scenarios/customer-crud/spring-boot-spring-boot"

cleanup() {
  bash "${scenario_dir}/run.sh" stop >/dev/null 2>&1 || true
}

trap cleanup EXIT

cleanup

(
  cd "${scenario_dir}"
  bash run.sh dev
) &
dev_pid="$!"

for _ in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8081/api/customers/runtime >/tmp/coakka-customer-runtime.json 2>/dev/null; then
    break
  fi
  if ! kill -0 "${dev_pid}" 2>/dev/null; then
    wait "${dev_pid}" || true
    echo "[fail] customer runtime dev process exited before becoming ready" >&2
    exit 1
  fi
  sleep 1
done

if ! curl -fsS http://127.0.0.1:8081/api/customers/runtime >/tmp/coakka-customer-runtime.json; then
  echo "[fail] customer runtime endpoint did not become ready" >&2
  exit 1
fi

python3 - <<'PY'
import json
from pathlib import Path

runtime = json.loads(Path("/tmp/coakka-customer-runtime.json").read_text())
business_transport = runtime["connector"]["businessTransport"]
if business_transport != "runtime-only":
    raise SystemExit(f"[fail] expected runtime-only business transport, got {business_transport!r}")
print("[ok] customer runtime business transport is runtime-only")
PY

(
  cd "${scenario_dir}"
  bash run.sh smoke
)

cleanup
wait "${dev_pid}" 2>/dev/null || true
