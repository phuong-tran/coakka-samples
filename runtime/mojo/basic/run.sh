#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command mojo "Install Mojo, then retry."
coakka_require_command cc "Install a C compiler, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/mojo/releases/2.3.0+a83ab412-3a84c7b/coakka-runtime-mojo-2.3.0-source.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-mojo-2.3.0-source.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"

package_root="${tmp_dir}/package"

runtime_lib="${COAKKA_RUNTIME_LIB:-}"
if [[ -z "${runtime_lib}" ]]; then
  case "$(uname -s)" in
    Darwin) runtime_lib="${package_root}/native/macos-aarch64/libcoakka_runtime_v2.dylib" ;;
    Linux)
      case "$(uname -m)" in
        x86_64) runtime_lib="${package_root}/native/linux-x86_64/libcoakka_runtime_v2.so" ;;
        aarch64|arm64) runtime_lib="${package_root}/native/linux-aarch64/libcoakka_runtime_v2.so" ;;
        *) coakka_die "Unsupported runtime Mojo sample arch: $(uname -m)" ;;
      esac
      ;;
    *) coakka_die "Unsupported runtime Mojo sample OS: $(uname -s)" ;;
  esac
fi

case "$(uname -s)" in
  Darwin)
    shim="${tmp_dir}/libcoakka_mojo_runtime_basic.dylib"
    cc -dynamiclib \
      "${script_dir}/runtime_shim.c" \
      "${runtime_lib}" \
      -o "${shim}"
    DYLD_LIBRARY_PATH="$(dirname "${runtime_lib}"):${tmp_dir}" \
      mojo "${script_dir}/main.mojo" "${shim}"
    ;;
  Linux)
    shim="${tmp_dir}/libcoakka_mojo_runtime_basic.so"
    cc -shared -fPIC \
      "${script_dir}/runtime_shim.c" \
      "${runtime_lib}" \
      -o "${shim}"
    LD_LIBRARY_PATH="$(dirname "${runtime_lib}"):${tmp_dir}" \
      mojo "${script_dir}/main.mojo" "${shim}"
    ;;
esac
