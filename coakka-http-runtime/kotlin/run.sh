#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir kotlin)"
package="$(prepare_package jvm)"
# A shared, task-owned cache can be supplied for multi-lane qualification.
# Compiled connector and sample outputs still stay in this lane's work tree.
gradle_user_home="${COAKKA_HTTP_SAMPLE_GRADLE_USER_HOME:-${work}/gradle-home}"
build_dir="${work}/build"
jar="${package}/lib/coakka-http-jvm-1.0.0.jar"
require_file "${jar}"
if [[ "$(uname -s)" == Darwin ]]; then
  host_library="${package}/native/libcoakka_http_host.dylib"
  bridge_library="${package}/native/libcoakka_http_jvm.dylib"
else
  host_library="${package}/native/libcoakka_http_host.so"
  bridge_library="${package}/native/libcoakka_http_jvm.so"
fi
require_file "${host_library}"
require_file "${bridge_library}"
args="--assets ${sample_root}/assets"
[[ "${command}" == "smoke" ]] && args="--smoke --compression ${args}"
[[ "${command}" == "adverse-smoke" ]] && args="--adverse-smoke"
[[ "${command}" == "tuning-smoke" ]] && args="--smoke --tuning --compression ${args}"
if [[ "${command}" == "security-smoke" ]]; then
  fixtures="$(prepare_test_certificates)"
  args="--security-smoke ${fixtures} ${args}"
fi

case "${command}" in
  check)
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      --project-cache-dir "${work}/sample-project-cache" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      check
    ;;
  smoke)
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      --project-cache-dir "${work}/sample-project-cache" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      run --args="${args}"
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      --project-cache-dir "${work}/sample-project-cache" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      run --args="${args} --io-uring"
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" --project-cache-dir "${work}/sample-project-cache" \
      -PcoakkaHttpJar="${jar}" -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" -PsampleBuildDir="${build_dir}" installDist
    # Run the application launcher, not Gradle's child supervisor, so SIGTERM
    # reaches the JVM whose hook waits for completed Core shutdown.
    python3 "${sample_root}/scripts/smoke-managed.py" \
      --log "${work}/wire-smoke.log" --ready-prefix kotlin-sample \
      --termination-exit-code 143 --shutdown-marker kotlin-shutdown=complete \
      --check-upload-trailer --upload-abort-prefix kotlin \
      --assets "${sample_root}/assets" -- \
      "${build_dir}/install/coakka-http-runtime-kotlin-sample/bin/coakka-http-runtime-kotlin-sample" \
      --compression --assets "${sample_root}/assets"
    ;;
  http2|http3)
    fixtures="$(prepare_test_certificates)"
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" --project-cache-dir "${work}/sample-project-cache" \
      -PcoakkaHttpJar="${jar}" -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" -PsampleBuildDir="${build_dir}" installDist
    exec "${build_dir}/install/coakka-http-runtime-kotlin-sample/bin/coakka-http-runtime-kotlin-sample" \
      --protocol "${command}" --protocol-fixtures "${fixtures}"
    ;;
  security-smoke|adverse-smoke|tuning-smoke|run)
    GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
      -p "${lane_root}" \
      --project-cache-dir "${work}/sample-project-cache" \
      -PcoakkaHttpJar="${jar}" \
      -PcoakkaHttpHost="${host_library}" \
      -PcoakkaHttpBridge="${bridge_library}" \
      -PsampleBuildDir="${build_dir}" \
      run --args="${args}"
    if [[ "${command}" == tuning-smoke && "$(uname -s)" == Linux ]]; then
      GRADLE_USER_HOME="${gradle_user_home}" "${repo_root}/gradlew" --no-daemon \
        -p "${lane_root}" --project-cache-dir "${work}/sample-project-cache" \
        -PcoakkaHttpJar="${jar}" -PcoakkaHttpHost="${host_library}" \
        -PcoakkaHttpBridge="${bridge_library}" -PsampleBuildDir="${build_dir}" \
        run --args="${args} --single-cpu"
    fi
    ;;
  *) printf 'usage: bash kotlin/run.sh [check|smoke|tuning-smoke|adverse-smoke|security-smoke|http2|http3|run]\n' >&2; exit 2 ;;
esac
