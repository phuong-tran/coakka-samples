#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir go)"
prefix="$(prepare_host_prefix)"
module="${connector_root}/connectors/go/coakkahttp"
require_file "${module}/go.mod"

app="${work}/app"
mkdir -p "${app}"
cp "${lane_root}/go.mod" "${lane_root}/main.go" "${lane_root}/security.go" "${app}/"
# Keep the public source tree free from machine-specific replace directives.
(cd "${app}" && go mod edit \
  -replace="github.com/phuong-tran/coakka-http-runtime-go=${module}")

export CGO_CFLAGS="-I${prefix}/include"
export CGO_LDFLAGS="-L${prefix}/lib"
export DYLD_LIBRARY_PATH="${prefix}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export LD_LIBRARY_PATH="${prefix}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"

case "${command}" in
  check)
    unformatted="$(gofmt -d "${lane_root}/main.go" "${lane_root}/security.go")"
    [[ -z "${unformatted}" ]] || {
      printf '%s\n' "${unformatted}" >&2
      exit 1
    }
    (cd "${app}" && go vet ./...)
    ;;
  smoke)
    (cd "${app}" && go run . --smoke --assets "${sample_root}/assets")
    (cd "${app}" && go run . --smoke --io-uring --assets "${sample_root}/assets")
    ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    (cd "${app}" && go run . --security-smoke "${fixtures}")
    ;;
  run)
    (cd "${app}" && go run . --assets "${sample_root}/assets")
    ;;
  *) printf 'usage: bash go/run.sh [check|smoke|security-smoke|run]\n' >&2; exit 2 ;;
esac
