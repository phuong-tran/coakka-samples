#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish-public}"
module_path="github.com/phuong-tran/coakka-runtime-go"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command go "Install Go 1.23 or newer, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/go/releases/0.1.0+22f571fd955c/coakka-v2-connector-go-0.1.0.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-v2-connector-go-0.1.0.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"
cp "${script_dir}/main.go" "${tmp_dir}/main.go"

cat > "${tmp_dir}/go.mod" <<EOF
module coakka-runtime-go-basic-sample

go 1.23.0

require ${module_path} v0.0.0

replace ${module_path} => ./package
EOF

(
  cd "${tmp_dir}"
  go mod tidy >/dev/null
  go run .
)
