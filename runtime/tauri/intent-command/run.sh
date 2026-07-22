#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command cargo "Install Rust/Cargo, then retry."

cargo run --manifest-path "${script_dir}/Cargo.toml"
