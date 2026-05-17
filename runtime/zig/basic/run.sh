#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish-public}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command zig "Install Zig, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/zig/releases/0.2.0+c124a9e-10dc009/coakka-runtime-zig-0.2.0-source.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-zig-0.2.0-source.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"

(cd "${tmp_dir}/package" && bash scripts/smoke.sh)
