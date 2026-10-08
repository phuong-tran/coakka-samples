#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir go)"
package="$(prepare_package go)"
prefix="${package}/native"
module="${package}/go"
require_file "${module}/go.mod"

app="${work}/app"
mkdir -p "${app}"
cp "${lane_root}/go.mod" "${lane_root}/main.go" "${lane_root}/security.go" "${lane_root}/control.go" "${lane_root}/adverse.go" "${lane_root}/protocol.go" "${app}/"
# Keep the public source tree free from machine-specific replace directives.
(cd "${app}" && go mod edit \
  -replace="github.com/phuong-tran/coakka-http-runtime-go=${module}")

export CGO_CFLAGS="-I${prefix}/include"
export GOCACHE="${work}/go-cache"
export GOMODCACHE="${work}/go-mod-cache"
export GOTMPDIR="${work}/go-tmp"
mkdir -p "${GOTMPDIR}"
# Go1.23 temporary executables also need an explicit loader path on macOS;
# system launchers can strip DYLD_* before go run starts its child. This path
# belongs to the sample build, never to the connector package or system loader.
export CGO_LDFLAGS="-L${prefix}/lib -Wl,-rpath,${prefix}/lib"
export DYLD_LIBRARY_PATH="${prefix}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export LD_LIBRARY_PATH="${prefix}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"

case "${command}" in
  check)
    unformatted="$(gofmt -d "${lane_root}/main.go" "${lane_root}/security.go" "${lane_root}/control.go" "${lane_root}/adverse.go" "${lane_root}/protocol.go")"
    [[ -z "${unformatted}" ]] || {
      printf '%s\n' "${unformatted}" >&2
      exit 1
    }
    (cd "${app}" && go vet ./...)
    ;;
  smoke)
    (cd "${app}" && go run . --smoke --assets "${sample_root}/assets")
    (cd "${app}" && go run . --smoke --io-uring --assets "${sample_root}/assets")
    # Execute the binary directly so the driver signals the actual service,
    # not go run's compiler/launcher parent. No shell process hides close errors.
    (cd "${app}" && go build -o "${work}/http-sample" .)
    python3 "${sample_root}/scripts/smoke-managed.py" \
      --log "${work}/wire-smoke.log" --ready-prefix go-sample \
      --go-adverse --gzip --assets "${sample_root}/assets" -- \
      "${work}/http-sample" --compression --assets "${sample_root}/assets"
    ;;
  adverse-smoke)
    (cd "${app}" && go run . --adverse-smoke)
    ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    (cd "${app}" && go run . --security-smoke "${fixtures}")
    ;;
  tuning-smoke)
    (cd "${app}" && go run . --smoke --tuning --compression --assets "${sample_root}/assets")
    if [[ "$(uname -s)" == Linux ]]; then
      (cd "${app}" && go run . --smoke --tuning --single-cpu --assets "${sample_root}/assets")
    fi
    ;;
  http2|http3)
    fixtures="$(prepare_test_certificates)"
    (cd "${app}" && go build -o "${work}/http-sample" .)
    exec "${work}/http-sample" --protocol "${command}" --protocol-fixtures "${fixtures}"
    ;;
  run)
    (cd "${app}" && go run . --assets "${sample_root}/assets")
    ;;
  *) printf 'usage: bash go/run.sh [check|smoke|tuning-smoke|adverse-smoke|security-smoke|http2|http3|run]\n' >&2; exit 2 ;;
esac
