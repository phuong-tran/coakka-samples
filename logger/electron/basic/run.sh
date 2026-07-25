#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command node "Install Node.js 20 or newer, then retry."
coakka_require_command npm "Install npm, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

resolve_electron_logger_package() {
  local connector_root package_path
  connector_root="${COAKKA_CONNECTOR_ROOT:-}"
  if [[ -n "${connector_root}" && -d "${connector_root}/logger/electron" ]]; then
    (
      cd "${connector_root}/logger/electron"
      npm run pack:release >/dev/null
    )
    package_path="$(find "${connector_root}/logger/electron" -maxdepth 1 -name 'coakka-logger-electron-*.tgz' | head -n 1)"
    if [[ -n "${package_path}" ]]; then
      printf '%s\n' "${package_path}"
      return 0
    fi
  fi

  printf '%s\n' "coakka-logger-electron@1.2.6"
}

resolve_node_logger_package_for_local_connector() {
  local connector_root package_path
  connector_root="${COAKKA_CONNECTOR_ROOT:-}"
  if [[ -z "${connector_root}" || ! -d "${connector_root}/logger/node" ]]; then
    return 0
  fi

  (
    cd "${connector_root}/logger/node"
    npm run pack:release >/dev/null
  )
  package_path="$(find "${connector_root}/logger/node" -maxdepth 1 -name 'coakka-logger-node-*.tgz' | head -n 1)"
  if [[ -n "${package_path}" ]]; then
    printf '%s\n' "${package_path}"
  fi
}

prepare_local_logger_package() {
  local electron_package="$1"
  local node_package="$2"
  local package_dir patched_package

  if [[ -z "${node_package}" ]]; then
    printf '%s\n' "${electron_package}"
    return 0
  fi

  package_dir="${tmp_dir}/electron-logger-package"
  mkdir -p "${package_dir}"
  COPYFILE_DISABLE=1 tar -xzf "${electron_package}" -C "${package_dir}"
  python3 - "${package_dir}/package/package.json" "${node_package}" <<'PY'
import json
import sys

package_json = sys.argv[1]
node_package = sys.argv[2]
with open(package_json, "r", encoding="utf-8") as fh:
    package = json.load(fh)
package.setdefault("dependencies", {})["coakka-logger-node"] = f"file:{node_package}"
with open(package_json, "w", encoding="utf-8") as fh:
    json.dump(package, fh, indent=2)
    fh.write("\n")
PY
  patched_package="${tmp_dir}/coakka-logger-electron-local.tgz"
  COPYFILE_DISABLE=1 tar -C "${package_dir}" -czf "${patched_package}" package
  printf '%s\n' "${patched_package}"
}

package_path="$(resolve_electron_logger_package)"
node_package_path="$(resolve_node_logger_package_for_local_connector)"
package_path="$(prepare_local_logger_package "${package_path}" "${node_package_path}")"
cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"
cp "${script_dir}/preload.cjs" "${tmp_dir}/preload.cjs"
cp "${script_dir}/index.html" "${tmp_dir}/index.html"

(
  cd "${tmp_dir}"
  npm init -y >/dev/null
  npm install "${package_path}" electron@^38.0.0 >/dev/null
  npx electron main.mjs
)
