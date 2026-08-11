#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command zig "Install Zig, then retry."
coakka_require_command tar "Install tar, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/zig/releases/2.4.0+c2f53117-0afb5e9/coakka-runtime-zig-2.4.0-source.tar.gz"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-zig-2.4.0-source.tar.gz")"
mkdir -p "${tmp_dir}/package"
tar -C "${tmp_dir}/package" --strip-components 1 -xzf "${package_path}"

package_root="${tmp_dir}/package"
sample_src="${tmp_dir}/sample-src"
mkdir -p "${sample_src}"
cp "${package_root}/src/runtime.zig" "${sample_src}/runtime.zig"
cp "${package_root}/src/transport.zig" "${sample_src}/transport.zig"
cp "${package_root}/src/stream_lane.zig" "${sample_src}/stream_lane.zig"
cp "${script_dir}/main.zig" "${sample_src}/main.zig"

runtime_lib="${COAKKA_RUNTIME_LIB:-}"
if [[ -z "${runtime_lib}" ]]; then
  case "$(uname -s)" in
    Darwin) runtime_lib="${package_root}/native/macos-aarch64/libcoakka_runtime_v2.dylib" ;;
    Linux)
      case "$(uname -m)" in
        x86_64) runtime_lib="${package_root}/native/linux-x86_64/libcoakka_runtime_v2.so" ;;
        aarch64|arm64) runtime_lib="${package_root}/native/linux-aarch64/libcoakka_runtime_v2.so" ;;
        *) coakka_die "Unsupported runtime Zig sample arch: $(uname -m)" ;;
      esac
      ;;
    *) coakka_die "Unsupported runtime Zig sample OS: $(uname -s)" ;;
  esac
fi

binary="${tmp_dir}/coakka-runtime-zig-basic"
zig build-exe \
  "${sample_src}/main.zig" \
  "${package_root}/src/platform_bridge.c" \
  -I "${package_root}/src" \
  -lc \
  -femit-bin="${binary}" >/dev/null

case "$(uname -s)" in
  Darwin) COAKKA_RUNTIME_LIB="${runtime_lib}" DYLD_LIBRARY_PATH="$(dirname "${runtime_lib}")" "${binary}" ;;
  Linux) COAKKA_RUNTIME_LIB="${runtime_lib}" LD_LIBRARY_PATH="$(dirname "${runtime_lib}")" "${binary}" ;;
esac
