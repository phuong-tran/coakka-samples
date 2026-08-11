#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
artifact_rel="runtime/tauri/releases/2.4.0+c2f53117-0afb5e9/coakka-runtime-tauri-intents-2.4.0-source.tar.gz"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command cargo "Install Rust/Cargo, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-tauri-intents-2.4.0-source.tar.gz")"
package_root="${tmp_dir}/package"
app_root="${tmp_dir}/app"
mkdir -p "${package_root}" "${app_root}/src-tauri"
tar -C "${package_root}" --strip-components 1 -xzf "${package_path}"

cp -R "${script_dir}/dist" "${app_root}/dist"
cp "${script_dir}/src-tauri/Cargo.toml" "${app_root}/src-tauri/Cargo.toml"
cp "${script_dir}/src-tauri/build.rs" "${app_root}/src-tauri/build.rs"
cp "${script_dir}/src-tauri/tauri.conf.json" "${app_root}/src-tauri/tauri.conf.json"
cp -R "${script_dir}/src-tauri/capabilities" "${app_root}/src-tauri/capabilities"
cp -R "${script_dir}/src-tauri/icons" "${app_root}/src-tauri/icons"
cp -R "${script_dir}/src-tauri/src" "${app_root}/src-tauri/src"

perl -0pi -e "s#coakka-runtime-rs = \\{ path = \\\"[^\\\"]+\\\" \\}#coakka-runtime-rs = { path = \\\"${package_root}/coakka-runtime-rs\\\" }#" \
  "${app_root}/src-tauri/Cargo.toml"
perl -0pi -e "s#coakka-tauri-intents = \\{ path = \\\"[^\\\"]+\\\" \\}#coakka-tauri-intents = { path = \\\"${package_root}/coakka-tauri-intents\\\" }#" \
  "${app_root}/src-tauri/Cargo.toml"

cargo test --manifest-path "${app_root}/src-tauri/Cargo.toml"

if ! command -v cargo-tauri >/dev/null 2>&1 && [[ -x "${HOME}/.cargo/bin/cargo-tauri" ]]; then
  export PATH="${HOME}/.cargo/bin:${PATH}"
fi

if command -v cargo-tauri >/dev/null 2>&1; then
  (cd "${app_root}/src-tauri" && cargo tauri build --debug --no-bundle)
else
  coakka_note "cargo-tauri is not installed; skipped optional desktop binary build."
fi
