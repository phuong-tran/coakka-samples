#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"
electron_version="${COAKKA_ELECTRON_VERSION:-^42.0.0}"

coakka_require_command node "Install Node.js 22 or newer, then retry."
coakka_require_command npm "Install npm, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

resolve_electron_package() {
  local connector_root package_path package_version
  connector_root="${COAKKA_CONNECTOR_ROOT:-}"
  if [[ -n "${connector_root}" && -d "${connector_root}/electron" ]]; then
    connector_root="$(cd "${connector_root}" && pwd)"
    (
      cd "${connector_root}/electron"
      npm run pack:release >/dev/null
    )
    package_version="$(node -p "require('${connector_root}/electron/package.json').version")"
    package_path="${connector_root}/electron/coakka-v2-connector-electron-${package_version}.tgz"
    if [[ -f "${package_path}" ]]; then
      printf '%s\n' "${package_path}"
      return 0
    fi
  fi

  printf '%s\n' "coakka-v2-connector-electron@2.4.1"
}

resolve_node_package_for_local_connector() {
  local connector_root package_path package_version
  connector_root="${COAKKA_CONNECTOR_ROOT:-}"
  if [[ -z "${connector_root}" || ! -d "${connector_root}/node" ]]; then
    return 0
  fi
  connector_root="$(cd "${connector_root}" && pwd)"

  (
    cd "${connector_root}/node"
    npm run pack:release >/dev/null
  )
  package_version="$(node -p "require('${connector_root}/node/package.json').version")"
  package_path="${connector_root}/node/coakka-v2-connector-node-${package_version}.tgz"
  if [[ -f "${package_path}" ]]; then
    printf '%s\n' "${package_path}"
  fi
}

prepare_local_connector_package() {
  local electron_package="$1"
  local node_package="$2"
  local package_dir patched_package

  if [[ -z "${node_package}" ]]; then
    printf '%s\n' "${electron_package}"
    return 0
  fi

  package_dir="${tmp_dir}/electron-package"
  mkdir -p "${package_dir}"
  COPYFILE_DISABLE=1 tar -xzf "${electron_package}" -C "${package_dir}"
  python3 - "${package_dir}/package/package.json" "${node_package}" <<'PY'
import json
import sys

package_json = sys.argv[1]
node_package = sys.argv[2]
with open(package_json, "r", encoding="utf-8") as fh:
    package = json.load(fh)
package.setdefault("dependencies", {})["coakka-v2-connector-node"] = f"file:{node_package}"
with open(package_json, "w", encoding="utf-8") as fh:
    json.dump(package, fh, indent=2)
    fh.write("\n")
PY
  patched_package="${tmp_dir}/coakka-v2-connector-electron-local.tgz"
  COPYFILE_DISABLE=1 tar -C "${package_dir}" -czf "${patched_package}" package
  printf '%s\n' "${patched_package}"
}

package_path="$(resolve_electron_package)"
node_package_path="$(resolve_node_package_for_local_connector)"
package_path="$(prepare_local_connector_package "${package_path}" "${node_package_path}")"

cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"
cp "${script_dir}/preload.cjs" "${tmp_dir}/preload.cjs"
cp "${script_dir}/index.html" "${tmp_dir}/index.html"

(
  cd "${tmp_dir}"
  npm init -y >/dev/null
  npm install --prefer-online "${package_path}" "electron@${electron_version}" >/dev/null
  npx electron main.mjs
)
