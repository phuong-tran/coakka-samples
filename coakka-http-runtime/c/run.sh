#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
prefix="$(prepare_host_prefix)"
require_file "${prefix}/include/coakka/http/host.h"
work="$(prepare_work_dir c)"
build="${work}/build"

cmake -S "${lane_root}" -B "${build}" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${prefix}" \
  -DCoAkkaHttpHost_DIR="${prefix}/lib/cmake/CoAkkaHttpHost"
cmake --build "${build}" --config Release --parallel

case "${command}" in
  check) ;;
  smoke)
    "${build}/coakka-http-c" --smoke "${sample_root}/assets"
    "${build}/coakka-http-c" --smoke "${sample_root}/assets" --io-uring
    ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    smoke_native_security_server "${build}/coakka-http-c-security" \
      "${fixtures}" c
    ;;
  run) "${build}/coakka-http-c" --serve "${sample_root}/assets" ;;
  *) printf 'usage: bash c/run.sh [check|smoke|security-smoke|run]\n' >&2; exit 2 ;;
esac
