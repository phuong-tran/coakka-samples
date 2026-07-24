#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command bun "Install Bun, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"

(
  cd "${tmp_dir}"
  cat > package.json <<'JSON'
{
  "private": true,
  "type": "module"
}
JSON
  bun add coakka-logger-bun@1.2.4 >/dev/null
  bun main.mjs
)
