#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
: "${COAKKA_SAMPLE_WORK_ROOT:?set a fresh absolute work path with an existing parent}"
for name in COAKKA_RUNTIME_LIB COAKKA_FILE_LANE_RUNTIME_LIB COAKKA_STREAM_LANE_RUNTIME_LIB LD_PRELOAD LD_LIBRARY_PATH DYLD_LIBRARY_PATH DYLD_INSERT_LIBRARIES; do
  if declare -p "${name}" >/dev/null 2>&1; then
    echo "remove native override: ${name}" >&2
    exit 2
  fi
done
: "${COAKKA_ZIG_ARCHIVE:?select the exact private3.0.0-source archive}"
[[ "$(zig version)" == "0.16.0" ]] || { echo "use qualified Zig0.16.0" >&2; exit 2; }
: "${COAKKA_ZIG_GLOBAL_CACHE_ROOT:?select the reusable Zig cache}"
[[ "${COAKKA_ZIG_GLOBAL_CACHE_ROOT}" == /* && -d "${COAKKA_ZIG_GLOBAL_CACHE_ROOT}" && ! -L "${COAKKA_ZIG_GLOBAL_CACHE_ROOT}" ]] || exit 2
admission="$(python3 -I -B "${repo_root}/coakka-runtime/scripts/runtime-source-candidate.py" --language zig --archive "${COAKKA_ZIG_ARCHIVE}" --work "${COAKKA_SAMPLE_WORK_ROOT}")"
package_root="$(printf '%s' "${admission}" | python3 -I -B -c 'import json,sys;print(json.load(sys.stdin)["packageRoot"])')"
work_root="${COAKKA_SAMPLE_WORK_ROOT}"
cleanup() { rm -rf "${work_root}/build" "${work_root}/package"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir "${work_root}/build" "${work_root}/installed"
export TMPDIR="${work_root}/build"
case "$(uname -s):$(uname -m)" in
 Darwin:arm64) platform=macos-aarch64; name=libcoakka_runtime_v2.dylib ;;
 Linux:aarch64|Linux:arm64) platform=linux-aarch64; name=libcoakka_runtime_v2.so ;;
 Linux:x86_64) platform=linux-x86_64; name=libcoakka_runtime_v2.so ;;
 *) echo "unsupported sample host" >&2; exit 64 ;;
esac
mkdir "${work_root}/installed/native"
cp "${package_root}/native/${platform}/${name}" "${work_root}/installed/native/${name}"
runtime_lib="${work_root}/installed/native/${name}"
compiler=(zig build-exe)
if [[ "${platform}" == macos-aarch64 ]]; then
  compiler+=(-target aarch64-macos.13.0 -mcpu=baseline)
fi
cp "${package_root}/src/"*.zig "${work_root}/build/"
cp "${package_root}/src/platform_bridge.c" "${package_root}/src/platform_bridge.h" "${work_root}/build/"
cp "${script_dir}/main.zig" "${work_root}/build/main.zig"
"${compiler[@]}" "${work_root}/build/main.zig" -cflags -std=c11 -Wall -Wextra -Werror -- "${work_root}/build/platform_bridge.c" -I "${work_root}/build" -lc -O ReleaseSafe --cache-dir "${work_root}/build/zig-cache" --global-cache-dir "${COAKKA_ZIG_GLOBAL_CACHE_ROOT}" -femit-bin="${work_root}/installed/basic"
if [[ "${platform}" == macos-aarch64 ]]; then
  python3 -I -B "${repo_root}/coakka-runtime/scripts/runtime-macos-floor.py" "${work_root}/installed/basic" "${runtime_lib}"
fi
COAKKA_RUNTIME_LIB="${runtime_lib}" "${work_root}/installed/basic"
