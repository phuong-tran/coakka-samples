#!/usr/bin/env bash
set -euo pipefail

mapping_file=${1:-}
apk_file=${2:-}
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}

if [[ ! -f "${mapping_file}" ]]; then
  echo "R8 mapping is missing: ${mapping_file}" >&2
  exit 1
fi
if [[ ! -f "${apk_file}" ]]; then
  echo "minified release APK is missing: ${apk_file}" >&2
  exit 1
fi

apkanalyzer_bin=$(command -v apkanalyzer || true)
if [[ -z "${apkanalyzer_bin}" && -n "${sdk_root}" ]]; then
  candidate=${sdk_root}/cmdline-tools/latest/bin/apkanalyzer
  if [[ -x "${candidate}" ]]; then
    apkanalyzer_bin=${candidate}
  fi
fi
if [[ -z "${apkanalyzer_bin}" ]]; then
  echo "apkanalyzer not found; install Android SDK command-line tools" >&2
  exit 1
fi

jni_classes=(
  coakka.v2.android.NativeRuntimeBridge
  coakka.v2.android.NativeStreamCallbacks
  coakka.v2.android.AndroidStreamSource
  coakka.v2.android.AndroidStreamConsumer
)
for class_name in "${jni_classes[@]}"; do
  grep -Fxq "${class_name} -> ${class_name}:" "${mapping_file}" || {
    echo "R8 renamed or removed JNI class ${class_name}" >&2
    exit 1
  }
done

dex_inventory=$(mktemp "${TMPDIR:-/tmp}/coakka-android-dex.XXXXXX")
trap 'rm -f "${dex_inventory}"' EXIT
"${apkanalyzer_bin}" dex packages --defined-only "${apk_file}" >"${dex_inventory}"

required_dex_members=(
  'coakka.v2.android.NativeRuntimeBridge int nativeAbiVersion()'
  'coakka.v2.android.NativeRuntimeBridge int nativeReadRuntimeInfo(long[],java.lang.String[])'
  'coakka.v2.android.NativeRuntimeBridge long nativeCreate(java.lang.String,java.lang.String,int,boolean)'
  'coakka.v2.android.NativeRuntimeBridge int nativeApplyNetwork(long,int,java.lang.String,int,java.lang.String,int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeApplyInitialControl(long,byte[])'
  'coakka.v2.android.NativeRuntimeBridge int[] nativeOpenHostHandles(long,int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeStart(long)'
  'coakka.v2.android.NativeRuntimeBridge int nativeStop(long)'
  'coakka.v2.android.NativeRuntimeBridge long nativeConsumeMonitor(int)'
  'coakka.v2.android.NativeRuntimeBridge long[] nativeReadHealth(long)'
  'coakka.v2.android.NativeRuntimeBridge void nativeDestroy(long)'
  'coakka.v2.android.NativeRuntimeBridge long nativeFileLaneCreate(long[],java.lang.String[],boolean,int[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneStop(long)'
  'coakka.v2.android.NativeRuntimeBridge void nativeFileLaneDestroy(long)'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneBoundPort(long,int[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLanePrepareReceive(long,java.lang.String,java.lang.String,java.lang.String,long,byte[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLanePrepareReceiveGrant(long,java.lang.String,java.lang.String,java.lang.String,long,byte[],long[],java.lang.String[],byte[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneSubmitSend(long,java.lang.String,java.lang.String,java.lang.String,int,java.lang.String,long,byte[],int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneTransfer(long,java.lang.String,int,long,int,boolean,long[],java.lang.String[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneCancel(long,java.lang.String,int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneForget(long,java.lang.String,int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileLaneStats(long,long[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeFileSha256(java.lang.String,byte[],long[])'
  'coakka.v2.android.NativeRuntimeBridge long nativeStreamLaneCreate(long[],java.lang.String[],boolean,int[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneStop(long)'
  'coakka.v2.android.NativeRuntimeBridge void nativeStreamLaneDestroy(long)'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneBoundPort(long,int[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLanePreparePublish(long,java.lang.String,java.lang.String,long,int,coakka.v2.android.AndroidStreamSource,long[],java.lang.String[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneSubscribe(long,java.lang.String,java.lang.String,java.lang.String,int,long,int,int,int,coakka.v2.android.AndroidStreamConsumer)'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneSession(long,java.lang.String,int,long,int,boolean,long[],java.lang.String[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLanePressure(long,java.lang.String,int,long,int,boolean,long[])'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneCancel(long,java.lang.String,int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneForget(long,java.lang.String,int)'
  'coakka.v2.android.NativeRuntimeBridge int nativeStreamLaneStats(long,long[])'
  'coakka.v2.android.NativeStreamCallbacks int sourceNext(coakka.v2.android.AndroidStreamSource,java.nio.ByteBuffer,long[],long)'
  'coakka.v2.android.NativeStreamCallbacks int consume(coakka.v2.android.AndroidStreamConsumer,java.nio.ByteBuffer,long[],long)'
)
for member in "${required_dex_members[@]}"; do
  grep -Fq "${member}" "${dex_inventory}" || {
    echo "minified APK is missing exact JNI member ${member}" >&2
    exit 1
  }
done

echo "[coakka-android-r8] exact JNI classes and members verified in minified APK"
