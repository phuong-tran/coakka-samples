#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
scenario_dir="${repo_root}/runtime/scenarios/customer-crud/spring-boot-starter-local"

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
  if curl -fsS http://127.0.0.1:8082/api/customers/runtime >/tmp/coakka-starter-local-runtime.json 2>/dev/null; then
    break
  fi
  if ! kill -0 "${dev_pid}" 2>/dev/null; then
    wait "${dev_pid}" || true
    echo "[fail] Spring Boot starter local dev process exited before becoming ready" >&2
    exit 1
  fi
  sleep 1
done

if ! curl -fsS http://127.0.0.1:8082/api/customers/runtime >/tmp/coakka-starter-local-runtime.json; then
  echo "[fail] Spring Boot starter local runtime endpoint did not become ready" >&2
  exit 1
fi

python3 - <<'PY'
import json
from pathlib import Path

runtime = json.loads(Path("/tmp/coakka-starter-local-runtime.json").read_text())
demo_mode = runtime["connector"]["demoMode"]
route_count = runtime["runtimeConfig"]["routeCount"]
if demo_mode != "spring-boot-starter-local":
    raise SystemExit(f"[fail] expected starter-local demo mode, got {demo_mode!r}")
if route_count != 4:
    raise SystemExit(f"[fail] expected 4 local capability routes, got {route_count!r}")
print("[ok] Spring Boot starter local runtime has 4 local capability routes")
PY

(
  cd "${scenario_dir}"
  bash run.sh smoke
)

cleanup
wait "${dev_pid}" 2>/dev/null || true
