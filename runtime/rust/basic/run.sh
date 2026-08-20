#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command cargo "Install Rust/Cargo, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/rust/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-3ae74f4/coakka-runtime-rs-2.5.2.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-rs-2.5.2.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"

cargo run --manifest-path "${tmp_dir}/package/Cargo.toml" --bin coakka-rust-smoke
