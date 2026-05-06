#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

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

echo "[runtime/run-all] Node basic"
bash "${script_dir}/node/basic/run.sh"

echo "[runtime/run-all] Node deadletter"
bash "${script_dir}/node/deadletter/run.sh"

echo "[runtime/run-all] Go basic"
bash "${script_dir}/go/basic/run.sh"

echo "[runtime/run-all] Go deadletter"
bash "${script_dir}/go/deadletter/run.sh"

echo "[runtime/run-all] Native C/C++ basic"
bash "${script_dir}/native/basic/run.sh"
