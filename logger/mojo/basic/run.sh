#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command mojo "Install Mojo 1.0 beta or newer, then retry."
coakka_require_command cc "Install a C compiler, then retry."
coakka_require_command tar "Install tar, then retry."

coakka_native_platform() {
  local system machine
  system="$(uname -s)"
  machine="$(uname -m)"
  case "${system}:${machine}" in
    Darwin:arm64|Darwin:aarch64) printf '%s\n' "macos-aarch64" ;;
    Linux:aarch64|Linux:arm64) printf '%s\n' "linux-aarch64" ;;
    Linux:x86_64|Linux:amd64) printf '%s\n' "linux-x86_64" ;;
    *) coakka_die "Unsupported native sample platform: ${system}/${machine}" ;;
  esac
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="logger/native/releases/0.1.0+ba2a66d98eb5/coakka-logger-native-0.1.0.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-logger-native-0.1.0.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" -xzf "${package_path}"

package_root="${tmp_dir}/package/coakka-logger-native-0.1.0"
platform="$(coakka_native_platform)"

case "$(uname -s)" in
  Darwin)
    shim="${tmp_dir}/libcoakka_mojo_logger_basic.dylib"
    cc -dynamiclib \
      -I"${package_root}/include" \
      "${script_dir}/logger_shim.c" \
      "${package_root}/native/${platform}/libcoakka_logger_core.dylib" \
      -o "${shim}"
    DYLD_LIBRARY_PATH="${package_root}/native/${platform}:${tmp_dir}" \
      mojo "${script_dir}/main.mojo" "${shim}"
    ;;
  Linux)
    shim="${tmp_dir}/libcoakka_mojo_logger_basic.so"
    cc -shared -fPIC \
      -I"${package_root}/include" \
      "${script_dir}/logger_shim.c" \
      "${package_root}/native/${platform}/libcoakka_logger_core.so" \
      -o "${shim}"
    LD_LIBRARY_PATH="${package_root}/native/${platform}:${tmp_dir}" \
      mojo "${script_dir}/main.mojo" "${shim}"
    ;;
esac
