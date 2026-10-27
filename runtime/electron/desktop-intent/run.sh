#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
: "${COAKKA_SAMPLE_WORK_ROOT:?Set a fresh absolute output path on suitable storage}"
: "${COAKKA_ELECTRON_EXECUTABLE:?Set the installed Electron executable}"
node "${repo_root}/scripts/runtime-electron-candidate.mjs" "${COAKKA_SAMPLE_WORK_ROOT}"
exec "${COAKKA_ELECTRON_EXECUTABLE}" "${COAKKA_SAMPLE_WORK_ROOT}/installed/main.mjs" \
  --sample-data-dir "${COAKKA_SAMPLE_WORK_ROOT}/browser-data"
