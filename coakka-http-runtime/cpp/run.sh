#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
prefix="$(prepare_package native)"
require_file "${prefix}/include/coakka/http/http.h"
work="$(prepare_work_dir cpp)"
build="${work}/build"

cmake -S "${lane_root}" -B "${build}" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${prefix}" \
  -DCoAkkaHttp_DIR="${prefix}/lib/cmake/CoAkkaHttp"
cmake --build "${build}" --config Release --parallel

case "${command}" in
  check) ;;
  smoke)
    "${build}/coakka-http-cpp-outbound"
    python3 "${sample_root}/scripts/smoke-native.py" "${build}/coakka-http-cpp" "${sample_root}/assets" "${work}/smoke.log" --stream-upload --websocket
    python3 "${sample_root}/scripts/smoke-native.py" "${build}/coakka-http-cpp" "${sample_root}/assets" "${work}/tuning.log" --tuning
    python3 "${sample_root}/scripts/smoke-streaming.py" "${build}/coakka-http-cpp-streaming" "${work}/streaming.log"
    ;;
  streaming) "${build}/coakka-http-cpp-streaming" ;;
  outbound) "${build}/coakka-http-cpp-outbound" ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    smoke_native_security_server "${build}/coakka-http-cpp" \
      "${fixtures}" cpp
    ;;
  run) "${build}/coakka-http-cpp" --assets "${sample_root}/assets" ;;
  *) printf 'usage: bash cpp/run.sh [check|smoke|security-smoke|streaming|outbound|run]\n' >&2; exit 2 ;;
esac
