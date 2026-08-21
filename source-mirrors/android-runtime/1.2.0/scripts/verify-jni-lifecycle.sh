#!/usr/bin/env bash

set -euo pipefail

CONNECTOR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

verify_lane_cleanup() {
  local source_file="$1"
  local lane_name="$2"
  local helper_name="stop_and_destroy_${lane_name}_lane"
  local stop_call="coakka_v2_${lane_name}_lane_stop(lane);"
  local destroy_call="coakka_v2_${lane_name}_lane_destroy(lane);"

  if [[ "$(grep -Fxc "  (void)${stop_call}" "${source_file}")" -ne 1 ]] ||
     [[ "$(grep -Fxc "  ${destroy_call}" "${source_file}")" -ne 1 ]]; then
    printf '%s cleanup must centralize one stop-before-destroy sequence\n' "${lane_name}" >&2
    return 1
  fi

  local helper_uses
  helper_uses="$(grep -Fc "${helper_name}(lane);" "${source_file}")"
  if [[ "${helper_uses}" -ne 3 ]]; then
    printf '%s create cleanup must use %s in all three failure branches\n' \
      "${lane_name}" "${helper_name}" >&2
    return 1
  fi
}

verify_lane_cleanup \
  "${CONNECTOR_ROOT}/src/main/cpp/coakka_android_file_lane_jni.cpp" file
verify_lane_cleanup \
  "${CONNECTOR_ROOT}/src/main/cpp/coakka_android_stream_lane_jni.cpp" stream

printf '[coakka-android-jni] stop-before-destroy lifecycle verified\n'
