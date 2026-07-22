#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
connector_root="${COAKKA_CONNECTOR_ROOT:-${repo_root}/../coakkaJVMConnector}"
bun_package_root="${COAKKA_BUN_CONNECTOR_ROOT:-${connector_root}/bun}"
source "${repo_root}/scripts/sample-utils.sh"

if ! command -v bun >/dev/null 2>&1; then
  bun_home="${BUN_INSTALL:-${HOME}/.bun}"
  if [[ -x "${bun_home}/bin/bun" ]]; then
    export BUN_INSTALL="${bun_home}"
    export PATH="${BUN_INSTALL}/bin:${PATH}"
  fi
fi

coakka_require_command bun "Install Bun or set BUN_INSTALL to a user-local Bun install, then retry."
coakka_require_file "${bun_package_root}/package.json" \
  "Set COAKKA_BUN_CONNECTOR_ROOT to a local coakka-v2-connector-bun checkout."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

(
  cd "${bun_package_root}"
  bun run pack:release >/dev/null
)

package_path="$(find "${bun_package_root}" -maxdepth 1 -name 'coakka-v2-connector-bun-*.tgz' | head -n 1)"
coakka_require_file "${package_path}" "Run bun/scripts/smoke-packaged-package.sh in the connector workspace first."

cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"

(
  cd "${tmp_dir}"
  bun init -y >/dev/null
  bun add "${package_path}" >/dev/null
  bun main.mjs
)
