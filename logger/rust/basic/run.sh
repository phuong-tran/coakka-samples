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
artifact_rel="logger/rust/releases/1.2.1+f50756ebff0d/coakka-logger-rs-1.2.1.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-logger-rs-1.2.1.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"
mkdir -p "${tmp_dir}/consumer/src"
cp "${script_dir}/main.rs" "${tmp_dir}/consumer/src/main.rs"
cat > "${tmp_dir}/consumer/Cargo.toml" <<EOF
[package]
name = "coakka-logger-rust-basic-sample"
version = "1.2.1"
edition = "2021"

[dependencies]
coakka-logger-rs = { path = "../package" }
EOF

(cd "${tmp_dir}/consumer" && cargo run --quiet)
