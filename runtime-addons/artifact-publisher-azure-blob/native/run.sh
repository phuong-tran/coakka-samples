#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "${script_dir}/../../native-artifact-sample/run-addon.sh" "azure-blob" "${1:-published}"
