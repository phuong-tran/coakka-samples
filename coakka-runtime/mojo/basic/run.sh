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
: "${COAKKA_MOJO_ARCHIVE:?select the exact private3.0.0-source archive}"
[[ "$(mojo --version)" == "Mojo 1.0.0b2.dev2026052115 (cb8874f5)" ]] || { echo "use qualified Mojo1.0.0b2.dev2026052115" >&2; exit 2; }
admission="$(python3 -I -B "${repo_root}/coakka-runtime/scripts/runtime-source-candidate.py" --language mojo --archive "${COAKKA_MOJO_ARCHIVE}" --work "${COAKKA_SAMPLE_WORK_ROOT}")"
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
cp "${script_dir}/main.mojo" "${work_root}/installed/main.mojo"
case "$(uname -s)" in
 Darwin)
  shim="${work_root}/installed/libcoakka_mojo_basic.dylib"
  cc -std=c11 -Wall -Wextra -Werror -fvisibility=hidden -mmacosx-version-min=13.0 -dynamiclib "${package_root}/src/runtime_connector_shim.c" "${runtime_lib}" -o "${shim}"
  python3 -I -B "${repo_root}/coakka-runtime/scripts/runtime-macos-floor.py" "${shim}" "${runtime_lib}"
  DYLD_LIBRARY_PATH="${work_root}/installed/native" mojo "${work_root}/installed/main.mojo" "${shim}"
  ;;
 Linux)
  shim="${work_root}/installed/libcoakka_mojo_basic.so"
  # The loader expands ORIGIN relative to the installed shim.
  # shellcheck disable=SC2016
  cc -std=c11 -Wall -Wextra -Werror -fvisibility=hidden -shared -fPIC "${package_root}/src/runtime_connector_shim.c" "${runtime_lib}" -Wl,-rpath,'$ORIGIN/native' -o "${shim}"
  LD_LIBRARY_PATH="${work_root}/installed/native" mojo "${work_root}/installed/main.mojo" "${shim}"
  ;;
esac
