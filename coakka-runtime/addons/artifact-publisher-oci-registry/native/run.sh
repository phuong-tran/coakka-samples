#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "${script_dir}/../../native-artifact-sample/run-addon.sh" "oci-registry" "${1:-published}"
