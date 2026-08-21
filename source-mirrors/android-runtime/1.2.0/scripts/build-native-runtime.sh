#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
CONNECTOR_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
# shellcheck source=load-release-identity.sh
source "${SCRIPT_DIR}/load-release-identity.sh"
WORKTREE_V2_ROOT=$(cd -- "${CONNECTOR_ROOT}/../.." && pwd)
DEFAULT_REPOSITORY_ROOT=$(cd -- "${WORKTREE_V2_ROOT}/.." && pwd)
REPOSITORY_ROOT=$(cd -- "${COAKKA_CORE_REPO:-${DEFAULT_REPOSITORY_ROOT}}" && pwd)
MODE=${1:---build}

case "${MODE}" in
  --build | --verify-source) ;;
  *)
    printf 'usage: %s [--build|--verify-source]\n' "$0" >&2
    exit 2
    ;;
esac

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  ANDROID_SDK_ROOT=${ANDROID_HOME:-}
fi
if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
  case "$(uname -s)" in
    Darwin) ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk" ;;
    Linux) ANDROID_SDK_ROOT="${HOME}/Android/Sdk" ;;
  esac
fi
NDK_VERSION=29.0.14206865
NDK_ROOT=${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}}
ANDROID_PLATFORM=android-24
BUILD_TYPE=Release
ABIS=${COAKKA_ANDROID_ABIS:-${COAKKA_ANDROID_ABIS_DEFAULT}}
NATIVE_GIT_COMMIT=${COAKKA_V2_NATIVE_GIT_COMMIT:-${COAKKA_ANDROID_CORE_COMMIT}}

if [[ "${NATIVE_GIT_COMMIT}" != "${COAKKA_ANDROID_CORE_COMMIT}" ]]; then
  printf 'Android native build requires pinned Core commit %s; got %s.\n' \
    "${COAKKA_ANDROID_CORE_COMMIT}" "${NATIVE_GIT_COMMIT}" >&2
  exit 1
fi

if [[ ! "${NATIVE_GIT_COMMIT}" =~ ^[0-9a-f]{40}$ ]] ||
   ! git -C "${REPOSITORY_ROOT}" cat-file -e "${NATIVE_GIT_COMMIT}^{commit}" 2>/dev/null; then
  printf 'COAKKA_V2_NATIVE_GIT_COMMIT must name an exact 40-character commit.\n' >&2
  exit 1
fi

PINNED_SOURCE_ROOT="${CONNECTOR_ROOT}/.native-source/${NATIVE_GIT_COMMIT}"
PINNED_SOURCE_MARKER="${PINNED_SOURCE_ROOT}/.complete"
PROTOBUF_GIT_COMMIT=$(
  git -C "${REPOSITORY_ROOT}" ls-tree "${NATIVE_GIT_COMMIT}" -- external/protobuf |
    awk '$1 == "160000" { print $3 }'
)
if [[ ! "${PROTOBUF_GIT_COMMIT}" =~ ^[0-9a-f]{40}$ ]] ||
   ! git -C "${REPOSITORY_ROOT}/external/protobuf" \
     cat-file -e "${PROTOBUF_GIT_COMMIT}^{commit}" 2>/dev/null; then
  printf 'Pinned protobuf submodule commit is not available locally.\n' >&2
  exit 1
fi
if [[ ! -f "${PINNED_SOURCE_MARKER}" ||
      ! -f "${PINNED_SOURCE_ROOT}/external/protobuf/src/google/protobuf/runtime_version.h" ]]; then
  staging_root="${PINNED_SOURCE_ROOT}.tmp.$$"
  rm -rf "${staging_root}"
  mkdir -p "${staging_root}"
  git -C "${REPOSITORY_ROOT}" archive "${NATIVE_GIT_COMMIT}" -- v2 |
    tar -xf - -C "${staging_root}"
  mkdir -p "${staging_root}/external/protobuf"
  git -C "${REPOSITORY_ROOT}/external/protobuf" archive "${PROTOBUF_GIT_COMMIT}" |
    tar -xf - -C "${staging_root}/external/protobuf"
  touch "${staging_root}/.complete"
  rm -rf "${PINNED_SOURCE_ROOT}"
  mv "${staging_root}" "${PINNED_SOURCE_ROOT}"
fi
NATIVE_V2_ROOT="${PINNED_SOURCE_ROOT}/v2"
if ! grep -Fq \
  "project(CoAkkaCoreV2 VERSION ${COAKKA_ANDROID_CORE_VERSION} LANGUAGES" \
  "${NATIVE_V2_ROOT}/CMakeLists.txt"; then
  printf 'Pinned Android Core version does not match release identity %s.\n' \
    "${COAKKA_ANDROID_CORE_VERSION}" >&2
  exit 1
fi

VERIFY_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/coakka-android-native-verify.XXXXXX")
cleanup_verify_root() {
  case "${VERIFY_ROOT}" in
    "${TMPDIR:-/tmp}"/coakka-android-native-verify.*)
      rm -rf -- "${VERIFY_ROOT}"
      ;;
  esac
}
trap cleanup_verify_root EXIT
git -C "${REPOSITORY_ROOT}" archive "${NATIVE_GIT_COMMIT}" -- v2 |
  tar -xf - -C "${VERIFY_ROOT}"
mkdir -p "${VERIFY_ROOT}/external/protobuf"
git -C "${REPOSITORY_ROOT}/external/protobuf" archive "${PROTOBUF_GIT_COMMIT}" |
  tar -xf - -C "${VERIFY_ROOT}/external/protobuf"
if ! diff -qr -x build -x .complete "${VERIFY_ROOT}" "${PINNED_SOURCE_ROOT}"; then
  printf 'Pinned Android native source drifted from Core/protobuf git objects.\n' >&2
  exit 1
fi
cleanup_verify_root
trap - EXIT
printf '[coakka-android-native] source verified core=%s protobuf=%s\n' \
  "${NATIVE_GIT_COMMIT}" "${PROTOBUF_GIT_COMMIT}"
if [[ "${MODE}" == --verify-source ]]; then
  exit 0
fi

if [[ ! -f "${NDK_ROOT}/build/cmake/android.toolchain.cmake" ]]; then
  printf 'Android NDK toolchain not found: %s\n' "${NDK_ROOT}" >&2
  exit 1
fi
HOST_PROTOC=${COAKKA_V2_PROTOC_EXECUTABLE:-}
if [[ -z "${HOST_PROTOC}" ]]; then
  case "$(uname -s)" in
    Darwin)
      protobuf_prefix=$(bash "${NATIVE_V2_ROOT}/scripts/prepare_public_protobuf_cache_macos.sh")
      ;;
    Linux)
      protobuf_prefix=$(bash "${NATIVE_V2_ROOT}/scripts/prepare_public_protobuf_cache_linux.sh")
      ;;
    *)
      printf 'Set COAKKA_V2_PROTOC_EXECUTABLE on this host OS.\n' >&2
      exit 1
      ;;
  esac
  HOST_PROTOC="${protobuf_prefix}/bin/protoc"
fi
if [[ -z "${HOST_PROTOC}" || ! -x "${HOST_PROTOC}" ]]; then
  printf 'Set COAKKA_V2_PROTOC_EXECUTABLE to an executable host protoc.\n' >&2
  exit 1
fi
PROTOC_VERSION=$("${HOST_PROTOC}" --version 2>/dev/null || true)
case "${PROTOC_VERSION}" in
  "libprotoc 34.0-dev") ;;
  *)
    printf 'Host protoc (%s) does not match vendored protobuf 34.0-dev.\n' \
      "${PROTOC_VERSION:-unknown}" >&2
    exit 1
    ;;
esac

for abi in ${ABIS}; do
  case "${abi}" in
    arm64-v8a) processor=aarch64 ;;
    armeabi-v7a) processor=armv7 ;;
    x86) processor=i686 ;;
    x86_64) processor=x86_64 ;;
    *)
      printf 'Unsupported COAKKA_ANDROID_ABIS entry: %s\n' "${abi}" >&2
      exit 1
      ;;
  esac

  build_dir="${CONNECTOR_ROOT}/.native-build/${NATIVE_GIT_COMMIT}/${abi}"
  output_dir="${CONNECTOR_ROOT}/src/main/jniLibs/${abi}"
  cmake -S "${NATIVE_V2_ROOT}" -B "${build_dir}" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${abi}" \
    -DANDROID_PLATFORM="${ANDROID_PLATFORM}" \
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" \
    -DCOAKKA_V2_USE_BUNDLED_PROTOBUF=ON \
    -DCOAKKA_V2_PROTOC_EXECUTABLE="${HOST_PROTOC}" \
    -DCOAKKA_V2_GIT_COMMIT_OVERRIDE="${NATIVE_GIT_COMMIT}" \
    -DCOAKKA_V2_RUNTIME_TARGET_PROCESSOR="${processor}" \
    -DCOAKKA_V2_RUNTIME_EDITION=COMMUNITY \
    -DCOAKKA_V2_RUNTIME_PROFILE=community-default \
    -DCOAKKA_V2_PUBLIC_ARTIFACT_SURFACE=ON \
    -DCOAKKA_V2_ENABLE_INTERNAL_TEST_SEAMS=OFF \
    -DCOAKKA_V2_ENABLE_PUBLIC_TCP_TRANSPORT=ON \
    -DCOAKKA_V2_BUILD_TRANSPORT_PROTO_SHARED=OFF
  cmake --build "${build_dir}" --target coakka_runtime_v2 --parallel
  cmake -E make_directory "${output_dir}"
  cmake -E copy_if_different \
    "${build_dir}/libcoakka_runtime_v2.so" \
    "${output_dir}/libcoakka_runtime_v2.so"
done
