#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

if ! command -v bun >/dev/null 2>&1; then
  bun_home="${BUN_INSTALL:-${HOME}/.bun}"
  if [[ -x "${bun_home}/bin/bun" ]]; then
    export BUN_INSTALL="${bun_home}"
    export PATH="${BUN_INSTALL}/bin:${PATH}"
  fi
fi

coakka_require_command bun "Install Bun or set BUN_INSTALL to a user-local Bun install, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"

(
  cd "${tmp_dir}"
  bun init -y >/dev/null
  bun add coakka-v2-connector-bun@1.3.2 >/dev/null
  bun main.mjs
)
