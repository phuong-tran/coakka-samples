#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
artifact_rel="runtime/tauri/releases/1.3.1+bda2ef5-15d262e/coakka-runtime-tauri-intents-1.3.1-source.tar.gz"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command cargo "Install Rust/Cargo, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-tauri-intents-1.3.1-source.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"

cargo run --manifest-path "${tmp_dir}/package/examples/intent-command/Cargo.toml"
