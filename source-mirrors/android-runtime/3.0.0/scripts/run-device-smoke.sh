#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=load-release-identity.sh
source "${SCRIPT_DIR}/load-release-identity.sh"
AAR_PATH="${1:-${MODULE_DIR}/build/outputs/aar/coakka-runtime-android-release.aar}"
DEVICE_SERIAL="${2:-${ANDROID_SERIAL:-}}"
EXPECTED_DEVICE_ABI="${3:-${COAKKA_ANDROID_DEVICE_ABI:-arm64-v8a}}"
EXPECTED_CONNECTOR_COMMIT=${COAKKA_ANDROID_CONNECTOR_SOURCE_COMMIT:-$(git -C "${MODULE_DIR}" rev-parse HEAD)}
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ ! "${EXPECTED_CONNECTOR_COMMIT}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Android connector source commit must be an exact lowercase commit" >&2
  exit 1
fi

if [[ -z "${SDK_ROOT}" && -f "${MODULE_DIR}/local.properties" ]]; then
  SDK_ROOT="$(sed -n 's/^sdk\.dir=//p' "${MODULE_DIR}/local.properties" | head -n 1)"
fi

if [[ ! -f "${AAR_PATH}" ]]; then
  echo "missing candidate AAR: ${AAR_PATH}" >&2
  exit 1
fi
AAR_PATH="$(cd "$(dirname "${AAR_PATH}")" && pwd)/$(basename "${AAR_PATH}")"
if [[ -z "${DEVICE_SERIAL}" ]]; then
  echo "usage: $0 [candidate.aar] <device-serial> [arm64-v8a|armeabi-v7a|x86|x86_64]" >&2
  exit 1
fi
case "${EXPECTED_DEVICE_ABI}" in
  arm64-v8a|armeabi-v7a|x86|x86_64) ;;
  *)
    echo "unsupported device smoke ABI: ${EXPECTED_DEVICE_ABI}" >&2
    exit 1
    ;;
esac
if [[ -z "${SDK_ROOT}" || ! -d "${SDK_ROOT}" ]]; then
  echo "Android SDK not found; set ANDROID_SDK_ROOT or sdk.dir in ${MODULE_DIR}/local.properties" >&2
  exit 1
fi
export ANDROID_SDK_ROOT="${SDK_ROOT}"
export ANDROID_HOME="${SDK_ROOT}"

AAR_ENTRIES="$(unzip -Z1 "${AAR_PATH}")"
for entry in \
  assets/LICENSE \
  assets/NATIVE-LICENSE.md \
  assets/PACKAGE-LICENSE.md \
  assets/NOTICE \
  assets/coakka/runtime-package.json \
  jni/arm64-v8a/libcoakka_runtime_v2.so \
  jni/arm64-v8a/libcoakka_android_jni.so \
  jni/armeabi-v7a/libcoakka_runtime_v2.so \
  jni/armeabi-v7a/libcoakka_android_jni.so \
  jni/x86/libcoakka_runtime_v2.so \
  jni/x86/libcoakka_android_jni.so \
  jni/x86_64/libcoakka_runtime_v2.so \
  jni/x86_64/libcoakka_android_jni.so; do
  grep -Fxq "${entry}" <<<"${AAR_ENTRIES}" || {
    echo "candidate AAR is missing ${entry}" >&2
    exit 1
  }
done

METADATA="$(unzip -p "${AAR_PATH}" assets/coakka/runtime-package.json)"
grep -Fq "\"connector_version\": \"${COAKKA_ANDROID_CONNECTOR_VERSION}\"" <<<"${METADATA}"
grep -Fq "\"bundled_native_package_version\": \"${COAKKA_ANDROID_NATIVE_PACKAGE_VERSION}\"" <<<"${METADATA}"
grep -Fq "\"bundled_native_git_commit\": \"${COAKKA_ANDROID_CORE_COMMIT}\"" <<<"${METADATA}"
grep -Fq "\"connector_source_git_commit\": \"${EXPECTED_CONNECTOR_COMMIT}\"" <<<"${METADATA}"
grep -Fq "\"core_source_git_commit\": \"${COAKKA_ANDROID_CORE_COMMIT}\"" <<<"${METADATA}"
grep -Fq '"connector_source_tree_dirty": false' <<<"${METADATA}"
grep -Fq '"core_source_tree_dirty": false' <<<"${METADATA}"
grep -Fq '"source_tree_dirty": false' <<<"${METADATA}"
grep -Fq '"native_source_verified": true' <<<"${METADATA}"

adb -s "${DEVICE_SERIAL}" get-state >/dev/null
DEVICE_ABIS="$(adb -s "${DEVICE_SERIAL}" shell getprop ro.product.cpu.abilist | tr -d '\r')"
if [[ ",${DEVICE_ABIS}," != *",${EXPECTED_DEVICE_ABI},"* ]]; then
  echo "device ${DEVICE_SERIAL} does not advertise ${EXPECTED_DEVICE_ABI}; abilist=${DEVICE_ABIS}" >&2
  exit 1
fi

echo "[coakka-android-device-smoke] aar=$(shasum -a 256 "${AAR_PATH}" | awk '{print $1}')"
echo "[coakka-android-device-smoke] device=${DEVICE_SERIAL} target_abi=${EXPECTED_DEVICE_ABI} abilist=${DEVICE_ABIS}"
ANDROID_SERIAL="${DEVICE_SERIAL}" "${MODULE_DIR}/gradlew" \
  -p "${MODULE_DIR}/device-smoke" \
  -PcoakkaAndroidAar="${AAR_PATH}" \
  -PcoakkaDeviceSmokeAbi="${EXPECTED_DEVICE_ABI}" \
  -PcoakkaConnectorSourceCommit="${EXPECTED_CONNECTOR_COMMIT}" \
  verifyMinifiedJniNames connectedReleaseAndroidTest \
  --console=plain
