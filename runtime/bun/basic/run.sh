#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
artifact_rel="runtime/bun/releases/1.3.1+bda2ef5-247df1b/coakka-v2-connector-bun-1.3.1.tgz"
source "${repo_root}/scripts/resolve-artifact.sh"
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

package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-v2-connector-bun-1.3.1.tgz")"

cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"

(
  cd "${tmp_dir}"
  bun init -y >/dev/null
  bun add "${package_path}" >/dev/null
  bun main.mjs
)
