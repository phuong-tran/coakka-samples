#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish-public}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command mojo "Install Mojo, then retry."
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
    *) coakka_die "Unsupported Mojo sample platform: ${system}/${machine}" ;;
  esac
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/native/releases/0.2.0+c124a9e/coakka-runtime-native-v2-0.2.0.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-native-v2-0.2.0.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" -xzf "${package_path}"

package_root="${tmp_dir}/package/coakka-runtime-native-v2-0.2.0"
platform="$(coakka_native_platform)"

case "$(uname -s)" in
  Darwin)
    shim="${tmp_dir}/libcoakka_mojo_basic.dylib"
    cc -dynamiclib \
      -I"${package_root}/include" \
      "${script_dir}/coakka_mojo_basic_shim.c" \
      -L"${package_root}/native/${platform}" \
      -lcoakka_runtime_v2 \
      -o "${shim}"
    DYLD_LIBRARY_PATH="${package_root}/native/${platform}:${tmp_dir}" mojo "${script_dir}/main.mojo" "${shim}"
    ;;
  Linux)
    shim="${tmp_dir}/libcoakka_mojo_basic.so"
    cc -shared -fPIC \
      -I"${package_root}/include" \
      "${script_dir}/coakka_mojo_basic_shim.c" \
      -L"${package_root}/native/${platform}" \
      -lcoakka_runtime_v2 \
      -o "${shim}"
    LD_LIBRARY_PATH="${package_root}/native/${platform}:${tmp_dir}" mojo "${script_dir}/main.mojo" "${shim}"
    ;;
esac

