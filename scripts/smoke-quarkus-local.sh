#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
scenario_dir="${repo_root}/runtime/scenarios/customer-crud/quarkus-local"

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
  if curl -fsS http://127.0.0.1:8083/api/customers/runtime >/tmp/coakka-quarkus-local-runtime.json 2>/dev/null; then
    break
  fi
  if ! kill -0 "${dev_pid}" 2>/dev/null; then
    wait "${dev_pid}" || true
    echo "[fail] Quarkus local dev process exited before becoming ready" >&2
    exit 1
  fi
  sleep 1
done

if ! curl -fsS http://127.0.0.1:8083/api/customers/runtime >/tmp/coakka-quarkus-local-runtime.json; then
  echo "[fail] Quarkus local runtime endpoint did not become ready" >&2
  exit 1
fi

python3 - <<'PY'
import json
from pathlib import Path

runtime = json.loads(Path("/tmp/coakka-quarkus-local-runtime.json").read_text())
demo_mode = runtime["connector"]["demoMode"]
route_count = runtime["runtimeConfig"]["routeCount"]
if demo_mode != "quarkus-local":
    raise SystemExit(f"[fail] expected quarkus-local demo mode, got {demo_mode!r}")
if route_count != 1:
    raise SystemExit(f"[fail] expected 1 local store route, got {route_count!r}")
print("[ok] Quarkus local runtime has 1 local store route")
PY

(
  cd "${scenario_dir}"
  bash run.sh smoke
)

cleanup
wait "${dev_pid}" 2>/dev/null || true
