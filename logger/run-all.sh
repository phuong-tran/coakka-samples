#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

echo "[logger/run-all] JVM basic"
bash "${script_dir}/jvm/basic/run.sh"

echo "[logger/run-all] JVM Java basic"
bash "${script_dir}/jvm/java-basic/run.sh"

echo "[logger/run-all] JVM pressure"
bash "${script_dir}/jvm/pressure/run.sh"

echo "[logger/run-all] JVM Java pressure"
bash "${script_dir}/jvm/java-pressure/run.sh"

echo "[logger/run-all] Python basic"
bash "${script_dir}/python/basic/run.sh"

echo "[logger/run-all] Python pressure"
bash "${script_dir}/python/pressure/run.sh"

echo "[logger/run-all] Node.js basic"
bash "${script_dir}/node/basic/run.sh"

echo "[logger/run-all] Node.js pressure"
bash "${script_dir}/node/pressure/run.sh"

echo "[logger/run-all] Go basic"
bash "${script_dir}/go/basic/run.sh"

echo "[logger/run-all] Go pressure"
bash "${script_dir}/go/pressure/run.sh"

echo "[logger/run-all] C# basic"
bash "${script_dir}/csharp/basic/run.sh"

echo "[logger/run-all] C# pressure"
bash "${script_dir}/csharp/pressure/run.sh"

echo "[logger/run-all] Rust basic"
bash "${script_dir}/rust/basic/run.sh"

echo "[logger/run-all] Rust pressure"
bash "${script_dir}/rust/pressure/run.sh"

echo "[logger/run-all] Native C/C++ basic"
bash "${script_dir}/native/basic/run.sh"

echo "[logger/run-all] Native C pressure"
bash "${script_dir}/native/pressure/run.sh"
