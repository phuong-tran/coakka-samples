#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module_path="github.com/phuong-tran/coakka-runtime-go"
module_version="${COAKKA_GO_MODULE_VERSION:-v1.8.2}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

cp "${script_dir}/main.go" "${tmp_dir}/main.go"
cat > "${tmp_dir}/go.mod" <<EOF
module coakka-replica-file-fanout-sample

go 1.22

require ${module_path} ${module_version}
EOF

if [[ -n "${COAKKA_GO_MODULE_REPLACE:-}" ]]; then
  printf '\nreplace %s => %s\n' "${module_path}" "${COAKKA_GO_MODULE_REPLACE}" >> "${tmp_dir}/go.mod"
fi

(
  cd "${tmp_dir}"
  go mod tidy >/dev/null
  go run .
)
