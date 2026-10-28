#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
: "${COAKKA_TAURI_ARCHIVE:?Select the exact private Tauri source aggregate}"
: "${COAKKA_SAMPLE_WORK_ROOT:?Select a fresh absolute external work directory}"
: "${COAKKA_RUST_CARGO:?Select the absolute Cargo executable path}"
: "${COAKKA_RUST_CARGO_HOME:?Select an existing absolute offline Cargo cache}"
: "${COAKKA_RUST_TARGET_CACHE:?Select an existing absolute build cache directory}"
: "${COAKKA_RUST_TARGET:?Select macos-aarch64, linux-aarch64 or windows-x86_64}"
python="${COAKKA_ARTIFACT_PYTHON:-python3}"
"${python}" -B "${repo_root}/coakka-runtime/scripts/runtime-tauri-desktop-candidate.py" \
  --archive "${COAKKA_TAURI_ARCHIVE}" --source "${script_dir}" \
  --work "${COAKKA_SAMPLE_WORK_ROOT}" --cargo "${COAKKA_RUST_CARGO}" \
  --cargo-home "${COAKKA_RUST_CARGO_HOME}" --target-cache "${COAKKA_RUST_TARGET_CACHE}" \
  --target "${COAKKA_RUST_TARGET}"
executable="${COAKKA_SAMPLE_WORK_ROOT}/installed/coakka-sample-tauri-desktop-intent"
if [[ "${COAKKA_RUST_TARGET}" == windows-x86_64 ]]; then
  executable+=".exe"
elif [[ "${COAKKA_RUST_TARGET}" == macos-aarch64 ]]; then
  executable="${COAKKA_SAMPLE_WORK_ROOT}/installed/CoAkka Tauri Intent.app/Contents/MacOS/coakka-sample-tauri-desktop-intent"
fi
arguments=()
if [[ -n "${COAKKA_TAURI_BROWSER_DATA_DIR:-}" ]]; then
  arguments=(--browser-data-dir "${COAKKA_TAURI_BROWSER_DATA_DIR}")
fi
exec "${executable}" "${arguments[@]}"
