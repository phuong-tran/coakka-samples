#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sample_dir="$(cd "${script_dir}/.." && pwd)"
repo_root="$(cd "${sample_dir}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

release_version="${COAKKA_RUNTIME_INSPECT_VERSION:-1.3.1}"
context_root="${COAKKA_RUNTIME_INSPECT_DOCKERHUB_CONTEXT:-${repo_root}/build/runtime-inspect-dockerhub/context}"

release_id_for_platform() {
  case "$1" in
    linux-aarch64|linux-x86_64) printf '%s\n' "1.3.1+d7ab7fa" ;;
    *) coakka_die "Unsupported Docker Hub inspect platform: $1" ;;
  esac
}

prepare_platform() {
  local coakka_platform="$1"
  local target_arch="$2"
  local release_id archive_name archive_rel tmp_dir archive_path extracted_root

  release_id="$(release_id_for_platform "${coakka_platform}")"
  archive_name="coakka-runtime-inspect-v2-${release_version}-${coakka_platform}.tar.gz"
  archive_rel="runtime-inspect/native/releases/${release_id}/${archive_name}"
  tmp_dir="$(mktemp -d)"

  archive_path="$(coakka_resolve_artifact "${publish_root}" "${archive_rel}" "${tmp_dir}/${archive_name}")"
  tar -C "${tmp_dir}" -xzf "${archive_path}"
  extracted_root="${tmp_dir}/coakka-runtime-inspect-v2-${release_version}-${coakka_platform}"
  coakka_require_file "${extracted_root}/bin/coakka-runtime-inspect" \
    "The published inspect archive is missing the inspect binary."
  coakka_require_file "${extracted_root}/lib/libcoakka_runtime_v2.so" \
    "The published inspect archive is missing the runtime library."

  mkdir -p "${context_root}/inspect/${target_arch}"
  rm -rf "${context_root}/inspect/${target_arch:?}/"*
  cp -R "${extracted_root}/." "${context_root}/inspect/${target_arch}/"
  rm -rf "${tmp_dir}"
}

rm -rf "${context_root}"
mkdir -p "${context_root}/inspect"
prepare_platform linux-x86_64 amd64
prepare_platform linux-aarch64 arm64
cp "${script_dir}/Dockerfile" "${context_root}/Dockerfile"
cp "${sample_dir}/docker/entrypoint.sh" "${context_root}/entrypoint.sh"
chmod +x "${context_root}/entrypoint.sh"

printf '%s\n' "${context_root}"
