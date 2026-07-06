#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command zig "Install Zig 0.16 or newer, then retry."
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
artifact_rel="logger/native/releases/1.2.1+f50756ebff0d/coakka-logger-native-1.2.1.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-logger-native-1.2.1.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" -xzf "${package_path}"

package_root="${tmp_dir}/package/coakka-logger-native-1.2.1"
platform="$(coakka_native_platform)"
binary="${tmp_dir}/coakka-logger-zig-basic"

zig build-exe "${script_dir}/main.zig" \
  -I"${package_root}/include" \
  -L"${package_root}/native/${platform}" \
  -lcoakka_logger_core \
  -lc \
  -femit-bin="${binary}" >/dev/null

case "$(uname -s)" in
  Darwin) DYLD_LIBRARY_PATH="${package_root}/native/${platform}" "${binary}" ;;
  Linux) LD_LIBRARY_PATH="${package_root}/native/${platform}" "${binary}" ;;
esac
