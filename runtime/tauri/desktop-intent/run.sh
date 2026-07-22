#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command cargo "Install Rust/Cargo, then retry."

cargo test --manifest-path "${script_dir}/src-tauri/Cargo.toml"

if ! command -v cargo-tauri >/dev/null 2>&1 && [[ -x "${HOME}/.cargo/bin/cargo-tauri" ]]; then
  export PATH="${HOME}/.cargo/bin:${PATH}"
fi

if command -v cargo-tauri >/dev/null 2>&1; then
  (cd "${script_dir}/src-tauri" && cargo tauri build --debug --no-bundle)
else
  coakka_note "cargo-tauri is not installed; skipped optional desktop binary build."
fi
