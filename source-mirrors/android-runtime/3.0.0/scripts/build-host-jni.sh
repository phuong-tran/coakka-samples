#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR

set -euo pipefail

CONNECTOR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=load-release-identity.sh
source "${CONNECTOR_ROOT}/scripts/load-release-identity.sh"
PROFILE="${1:-strict}"
NATIVE_GIT_COMMIT="${COAKKA_V2_NATIVE_GIT_COMMIT:-${COAKKA_ANDROID_CORE_COMMIT}}"
if [[ "${NATIVE_GIT_COMMIT}" != "${COAKKA_ANDROID_CORE_COMMIT}" ]]; then
  printf 'Host JNI build requires pinned Core commit %s; got %s.\n' \
    "${COAKKA_ANDROID_CORE_COMMIT}" "${NATIVE_GIT_COMMIT}" >&2
  exit 1
fi
NATIVE_SOURCE_ROOT="${CONNECTOR_ROOT}/.native-source/${NATIVE_GIT_COMMIT}"
NATIVE_V2_ROOT="${NATIVE_SOURCE_ROOT}/v2"
BUILD_BASE_ROOT="${COAKKA_ANDROID_HOST_BUILD_ROOT:-${CONNECTOR_ROOT}/build/host-jni}"
BUILD_ROOT="${BUILD_BASE_ROOT}/${PROFILE}"
CC_VALUE="${CC:-cc}"
CXX_VALUE="${CXX:-c++}"

case "${PROFILE}" in
  strict) SANITIZER=none ;;
  asan) SANITIZER=address ;;
  ubsan) SANITIZER=undefined ;;
  tsan) SANITIZER=thread ;;
  *)
    printf 'usage: %s <strict|asan|ubsan|tsan>\n' "$0" >&2
    exit 64
    ;;
esac

if [[ -f "${BUILD_ROOT}/CMakeCache.txt" ]]; then
  EXPECTED_CC=$(command -v "${CC_VALUE}")
  EXPECTED_CXX=$(command -v "${CXX_VALUE}")
  CACHED_CC=$(sed -n 's/^CMAKE_C_COMPILER:FILEPATH=//p' "${BUILD_ROOT}/CMakeCache.txt")
  CACHED_CXX=$(sed -n 's/^CMAKE_CXX_COMPILER:FILEPATH=//p' "${BUILD_ROOT}/CMakeCache.txt")
  if [[ "${CACHED_CC}" != "${EXPECTED_CC}" || "${CACHED_CXX}" != "${EXPECTED_CXX}" ]]; then
    printf '[coakka-android-host-jni] compiler changed; replacing generated profile=%s\n' \
      "${PROFILE}"
    cmake -E remove_directory "${BUILD_ROOT}"
  fi
fi

COAKKA_V2_NATIVE_GIT_COMMIT="${NATIVE_GIT_COMMIT}" \
COAKKA_CORE_REPO="${COAKKA_CORE_REPO:-${CONNECTOR_ROOT}/../../..}" \
  bash "${CONNECTOR_ROOT}/scripts/build-native-runtime.sh" --verify-source

case "$(uname -s)" in
  Darwin)
    PROTOBUF_PREFIX=$(bash "${NATIVE_V2_ROOT}/scripts/prepare_public_protobuf_cache_macos.sh")
    ;;
  Linux)
    PROTOBUF_PREFIX=$(bash "${NATIVE_V2_ROOT}/scripts/prepare_public_protobuf_cache_linux.sh")
    ;;
  *)
    printf 'Host JNI tests support macOS and Linux only\n' >&2
    exit 69
    ;;
esac

HOST_PROTOC="${COAKKA_V2_PROTOC_EXECUTABLE:-${PROTOBUF_PREFIX}/bin/protoc}"
if [[ ! -x "${HOST_PROTOC}" ]]; then
  printf 'Host protoc not executable: %s\n' "${HOST_PROTOC}" >&2
  exit 1
fi

JAVA_HOME_VALUE="${JAVA_HOME:-}"
if [[ -z "${JAVA_HOME_VALUE}" ]]; then
  if [[ "$(uname -s)" == Darwin ]]; then
    JAVA_HOME_VALUE=$(/usr/libexec/java_home)
  else
    JAVA_BIN=$(command -v java)
    JAVA_HOME_VALUE=$(cd "$(dirname "$(readlink -f "${JAVA_BIN}")")/.." && pwd)
  fi
fi

cmake -S "${CONNECTOR_ROOT}/host-test" -B "${BUILD_ROOT}" -G Ninja \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=13.0 \
  -DCOAKKA_RUNTIME_ROOT="${NATIVE_V2_ROOT}" \
  -DCOAKKA_ANDROID_HOST_SANITIZER="${SANITIZER}" \
  -DCOAKKA_V2_USE_BUNDLED_PROTOBUF=ON \
  -DCOAKKA_V2_REQUIRE_SELF_CONTAINED_RUNTIME_DEPS=ON \
  -DCOAKKA_V2_PROTOC_EXECUTABLE="${HOST_PROTOC}" \
  -DCOAKKA_V2_GIT_COMMIT_OVERRIDE="${NATIVE_GIT_COMMIT}" \
  -DCOAKKA_V2_RUNTIME_EDITION=COMMUNITY \
  -DCOAKKA_V2_RUNTIME_PROFILE=community-default \
  -DCOAKKA_V2_PUBLIC_ARTIFACT_SURFACE=ON \
  -DCOAKKA_V2_ENABLE_INTERNAL_TEST_SEAMS=OFF \
  -DCOAKKA_V2_ENABLE_PUBLIC_TCP_TRANSPORT=ON \
  -DCOAKKA_V2_BUILD_TRANSPORT_PROTO_SHARED=OFF \
  -DJAVA_HOME="${JAVA_HOME_VALUE}"
cmake --build "${BUILD_ROOT}" --target coakka_android_jni --parallel

printf '[coakka-android-host-jni] built profile=%s output=%s/lib\n' \
  "${PROFILE}" "${BUILD_ROOT}"
