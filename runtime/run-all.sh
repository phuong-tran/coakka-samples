#!/usr/bin/env bash
set -euo pipefail

if [[ "${COAKKA_ALLOW_PAUSED_RUNTIME:-0}" != "1" ]]; then
  cat >&2 <<'EOF'
[runtime/run-all] runtime artifact-backed samples are paused.

The current public publish surface only exposes logger packages and the
sanitized direct runtime C ABI. Runtime JVM, language connector, Spring Boot,
and Quarkus packages must be republished before this lane is treated as a
public sample lane.

Set COAKKA_ALLOW_PAUSED_RUNTIME=1 only when testing a local private artifact
set that provides the paused runtime packages.
EOF
  exit 64
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[runtime/run-all] JVM basic"
bash "${script_dir}/jvm/basic/run.sh"

echo "[runtime/run-all] JVM Java basic"
bash "${script_dir}/jvm/java-basic/run.sh"

echo "[runtime/run-all] JVM deadletter"
bash "${script_dir}/jvm/deadletter/run.sh"

echo "[runtime/run-all] JVM Java deadletter observer"
bash "${script_dir}/jvm/java-deadletter/run.sh"

echo "[runtime/run-all] Python basic"
bash "${script_dir}/python/basic/run.sh"

echo "[runtime/run-all] Python deadletter"
bash "${script_dir}/python/deadletter/run.sh"

echo "[runtime/run-all] Python hot reload"
bash "${script_dir}/python/hot-reload/run.sh"

echo "[runtime/run-all] Node basic"
bash "${script_dir}/node/basic/run.sh"

echo "[runtime/run-all] Node deadletter"
bash "${script_dir}/node/deadletter/run.sh"

echo "[runtime/run-all] Go basic"
bash "${script_dir}/go/basic/run.sh"

echo "[runtime/run-all] Go deadletter"
bash "${script_dir}/go/deadletter/run.sh"

echo "[runtime/run-all] C# basic"
bash "${script_dir}/csharp/basic/run.sh"

echo "[runtime/run-all] Native C/C++ basic"
bash "${script_dir}/native/basic/run.sh"

echo "[runtime/run-all] Native C pressure"
bash "${script_dir}/native/pressure/run.sh"
