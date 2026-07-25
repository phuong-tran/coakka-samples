#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
module_path="github.com/phuong-tran/coakka-logger-go"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command go "Install Go 1.22 or newer, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
cp "${script_dir}/main.go" "${tmp_dir}/main.go"

cat > "${tmp_dir}/go.mod" <<EOF
module coakka-logger-go-pressure-sample

go 1.22

require ${module_path} v1.2.4
EOF

(
  cd "${tmp_dir}"
  go mod tidy >/dev/null
  go run .
)
