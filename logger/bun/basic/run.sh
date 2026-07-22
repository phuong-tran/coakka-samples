#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command bun "Install Bun, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="logger/bun/releases/1.2.1+f50756ebff0d-6fdcc69/coakka-logger-bun-1.2.1.tgz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-logger-bun-1.2.1.tgz")"
cp "${script_dir}/main.mjs" "${tmp_dir}/main.mjs"

(
  cd "${tmp_dir}"
  cat > package.json <<'JSON'
{
  "private": true,
  "type": "module"
}
JSON
  bun add "${package_path}" >/dev/null
  bun main.mjs
)
