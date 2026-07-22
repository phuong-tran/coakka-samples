#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command node "Install Node.js 20 or newer, then retry."
coakka_require_command npm "Install npm, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="logger/electron/releases/1.2.1+f50756ebff0d-3e8a6ae/coakka-logger-electron-1.2.1.tgz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-logger-electron-1.2.1.tgz")"
cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"
cp "${script_dir}/preload.cjs" "${tmp_dir}/preload.cjs"
cp "${script_dir}/index.html" "${tmp_dir}/index.html"

(
  cd "${tmp_dir}"
  npm init -y >/dev/null
  npm install "${package_path}" electron@^38.0.0 >/dev/null
  npx electron main.mjs
)
