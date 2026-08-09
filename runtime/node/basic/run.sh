#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command node "Install Node.js 20 or newer, then retry."
coakka_require_command npm "Install npm, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"

(
  cd "${tmp_dir}"
  npm init -y >/dev/null
  npm install --prefer-online coakka-v2-connector-node@2.1.1 >/dev/null
  node main.mjs
)
