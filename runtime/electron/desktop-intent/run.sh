#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
artifact_rel="${COAKKA_ELECTRON_ARTIFACT_REL:-runtime/electron/releases/1.3.1+bda2ef5-4e0cab0/coakka-v2-connector-electron-1.3.1.tgz}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command node "Install Node.js 20 or newer, then retry."
coakka_require_command npm "Install npm, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

resolve_electron_package() {
  local connector_root package_path
  connector_root="${COAKKA_CONNECTOR_ROOT:-}"
  if [[ -n "${connector_root}" && -d "${connector_root}/electron" ]]; then
    (
      cd "${connector_root}/electron"
      npm run pack:release >/dev/null
    )
    package_path="$(find "${connector_root}/electron" -maxdepth 1 -name 'coakka-v2-connector-electron-*.tgz' | head -n 1)"
    if [[ -n "${package_path}" ]]; then
      printf '%s\n' "${package_path}"
      return 0
    fi
  fi

  coakka_resolve_artifact \
    "${publish_root}" \
    "${artifact_rel}" \
    "${tmp_dir}/artifacts/coakka-v2-connector-electron-1.3.1.tgz"
}

package_path="$(resolve_electron_package)"

cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"
cp "${script_dir}/preload.cjs" "${tmp_dir}/preload.cjs"
cp "${script_dir}/index.html" "${tmp_dir}/index.html"

(
  cd "${tmp_dir}"
  npm init -y >/dev/null
  npm install "${package_path}" electron@^38.0.0 >/dev/null
  npx electron main.mjs
)
