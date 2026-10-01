#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir kotlin)"
prefix="$(prepare_host_prefix)"
# A shared, task-owned cache can be supplied for multi-lane qualification.
# Compiled connector and sample outputs still stay in this lane's work tree.
gradle_user_home="${COAKKA_HTTP_SAMPLE_GRADLE_USER_HOME:-${work}/gradle-home}"
build_dir="${work}/build"
connector_build="${work}/connector-build"
GRADLE_USER_HOME="${gradle_user_home}" "${connector_root}/gradlew" --no-daemon \
  -p "${connector_root}" \
  --project-cache-dir "${work}/connector-project-cache" \
  -PcoakkaHttpPrefix="${prefix}" \
  -PcoakkaHttpBuildDir="${connector_build}" \
  :connectors:jvm:runtime:jar :connectors:jvm:runtime:compileNativeBridge >/dev/null
jar="${connector_build}/libs/coakka-http-jvm-1.0.0.jar"
require_file "${jar}"
if [[ "$(uname -s)" == Darwin ]]; then
  host_library="${prefix}/lib/libcoakka_http_host.1.0.0.dylib"
  bridge_library="${connector_build}/native/libcoakka_http_jvm.dylib"
else
  host_library="${prefix}/lib/libcoakka_http_host.so.1.0.0"
  bridge_library="${connector_build}/native/libcoakka_http_jvm.so"
fi
require_file "${host_library}"
require_file "${bridge_library}"
args="--assets ${sample_root}/assets"
[[ "${command}" == "smoke" ]] && args="--smoke ${args}"
if [[ "${command}" == "security-smoke" ]]; then
  fixtures="$(prepare_test_certificates)"
  args="--security-smoke ${fixtures} ${args}"
fi

case "${command}" in
  check)
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      check
    ;;
  smoke)
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      run --args="${args}"
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      run --args="${args} --io-uring"
    ;;
  security-smoke|run)
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      run --args="${args}"
    ;;
  *) printf 'usage: bash kotlin/run.sh [check|smoke|security-smoke|run]\n' >&2; exit 2 ;;
esac
