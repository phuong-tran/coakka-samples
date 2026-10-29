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

# A changed connector can consume an already qualified exact Core artifact.
# Never fall back to a source rebuild after a requested prebuilt input refuses.
if [[ -n "${COAKKA_ANDROID_PREBUILT_CORE_AAR:-}" ||
      -n "${COAKKA_ANDROID_PREBUILT_CORE_SHA256:-}" ]]; then
  prebuilt_arguments=(python3 -B "${SCRIPT_DIR}/stage-prebuilt-core.py"
    --root "${CONNECTOR_ROOT}" --repository "${REPOSITORY_ROOT}"
    --aar "${COAKKA_ANDROID_PREBUILT_CORE_AAR:-}"
    --sha256 "${COAKKA_ANDROID_PREBUILT_CORE_SHA256:-}"
    --commit "${COAKKA_ANDROID_CORE_COMMIT}")
  [[ "${MODE}" != --verify-source ]] || prebuilt_arguments+=(--verify-only)
  "${prebuilt_arguments[@]}"
  exit
fi

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
ndk_prebuilt_candidates=("${NDK_ROOT}"/toolchains/llvm/prebuilt/*)
if [[ ${#ndk_prebuilt_candidates[@]} -ne 1 ||
      ! -x "${ndk_prebuilt_candidates[0]}/bin/llvm-objcopy" ||
      ! -x "${ndk_prebuilt_candidates[0]}/bin/llvm-strip" ]]; then
  printf 'Android NDK must expose qualified llvm-objcopy and llvm-strip tools.\n' >&2
  exit 1
fi
NDK_OBJCOPY="${ndk_prebuilt_candidates[0]}/bin/llvm-objcopy"
NDK_STRIP="${ndk_prebuilt_candidates[0]}/bin/llvm-strip"
PRIVATE_MESSAGE_ROOT=${COAKKA_ANDROID_PRIVATE_MESSAGE_ROOT:-}
if [[ -z "${PRIVATE_MESSAGE_ROOT}" || ! -d "${PRIVATE_MESSAGE_ROOT}" ]]; then
  printf 'Set COAKKA_ANDROID_PRIVATE_MESSAGE_ROOT to the sealed per-ABI provider root.\n' >&2
  exit 1
fi
PRIVATE_MESSAGE_ROOT=$(cd -- "${PRIVATE_MESSAGE_ROOT}" && pwd)

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

  build_dir="${CONNECTOR_ROOT}/.native-build/${NATIVE_GIT_COMMIT}/private-message-3/${abi}"
  output_dir="${CONNECTOR_ROOT}/src/main/jniLibs/${abi}"
  provider_root="${PRIVATE_MESSAGE_ROOT}/${abi}/provider-install"
  message_prefix="${PRIVATE_MESSAGE_ROOT}/${abi}/message-prefix"
  host_protoc="${message_prefix}/private-tools/runtime-message-codegen"
  if [[ ! -f "${provider_root}/lib/cmake/protobuf/protobuf-config.cmake" ||
        ! -f "${provider_root}/lib/cmake/absl/abslConfig.cmake" ||
        ! -x "${host_protoc}" ]]; then
    printf 'Incomplete sealed Android message provider for %s under %s.\n' \
      "${abi}" "${PRIVATE_MESSAGE_ROOT}" >&2
    exit 1
  fi
  python3 "${NATIVE_V2_ROOT}/scripts/prepare_private_message_profile.py" \
    verify-prefix --prefix "${message_prefix}" \
    --schema-root "${NATIVE_V2_ROOT}/proto" >/dev/null
  protoc_version=$("${host_protoc}" --version 2>/dev/null || true)
  if [[ "${protoc_version}" != "libprotoc 34.0-dev" ]]; then
    printf 'Private message code generator for %s has unexpected version %s.\n' \
      "${abi}" "${protoc_version:-unknown}" >&2
    exit 1
  fi
  native_path_flags="-g0 -ffile-prefix-map=${PINNED_SOURCE_ROOT}=coakka/runtime -fmacro-prefix-map=${PINNED_SOURCE_ROOT}=coakka/runtime -fdebug-prefix-map=${PINNED_SOURCE_ROOT}=coakka/runtime -ffile-prefix-map=${build_dir}=coakka/runtime-build -fmacro-prefix-map=${build_dir}=coakka/runtime-build -fdebug-prefix-map=${build_dir}=coakka/runtime-build"
  cmake -S "${NATIVE_V2_ROOT}" -B "${build_dir}" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${abi}" \
    -DANDROID_PLATFORM="${ANDROID_PLATFORM}" \
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" \
    -DCMAKE_PREFIX_PATH="${provider_root}" \
    -DProtobuf_DIR="${provider_root}/lib/cmake/protobuf" \
    -Dabsl_DIR="${provider_root}/lib/cmake/absl" \
    -Dutf8_range_DIR="${provider_root}/lib/cmake/utf8_range" \
    -DCMAKE_C_FLAGS="${native_path_flags}" \
    -DCMAKE_CXX_FLAGS="${native_path_flags}" \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,start-stop-visibility=hidden" \
    -DCOAKKA_V2_USE_BUNDLED_PROTOBUF=OFF \
    -DCOAKKA_V2_PRIVATE_MESSAGE_PREFIX="${message_prefix}" \
    -DCOAKKA_V2_PROTOC_EXECUTABLE="${host_protoc}" \
    -DCOAKKA_V2_GIT_COMMIT_OVERRIDE="${NATIVE_GIT_COMMIT}" \
    -DCOAKKA_V2_RUNTIME_TARGET_PROCESSOR="${processor}" \
    -DCOAKKA_V2_RUNTIME_EDITION=ENTERPRISE \
    -DCOAKKA_V2_RUNTIME_PROFILE=enterprise-standard \
    -DCOAKKA_V2_PUBLIC_ARTIFACT_SURFACE=ON \
    -DCOAKKA_V2_REQUIRE_SELF_CONTAINED_RUNTIME_DEPS=ON \
    -DCOAKKA_V2_ENABLE_INTERNAL_TEST_SEAMS=OFF \
    -DCOAKKA_V2_ENABLE_PUBLIC_TCP_TRANSPORT=ON \
    -DCOAKKA_V2_BUILD_TRANSPORT_PROTO_SHARED=OFF
  cmake --build "${build_dir}" --target coakka_runtime_v2 --parallel
  "${NDK_STRIP}" --strip-unneeded \
    "${build_dir}/libcoakka_runtime_v2.so"
  "${NDK_OBJCOPY}" --remove-section=.comment \
    "${build_dir}/libcoakka_runtime_v2.so"
  cmake -E make_directory "${output_dir}"
  cmake -E copy_if_different \
    "${build_dir}/libcoakka_runtime_v2.so" \
    "${output_dir}/libcoakka_runtime_v2.so"
done
