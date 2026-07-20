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
    linux-aarch64|linux-x86_64) printf '%s\n' "1.3.1+4ce41f19" ;;
    *) coakka_die "Unsupported Docker Hub inspect platform: $1" ;;
  esac
}

require_self_contained_dockerfiles() {
  local matches
  matches="$(
    grep -EIn \
      'apt-get[[:space:]]+install|apk[[:space:]]+add|dnf[[:space:]]+install|yum[[:space:]]+install' \
      "${sample_dir}/docker/Dockerfile" \
      "${script_dir}/Dockerfile" 2>/dev/null || true
  )"

  if [[ -n "${matches}" ]]; then
    printf '%s\n' "${matches}" >&2
    coakka_die "runtime-inspect Docker Hub image must use self-contained native archives and must not install native runtime packages."
  fi
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
  require_linux_archive_self_contained "${coakka_platform}" "${extracted_root}"

  mkdir -p "${context_root}/inspect/${target_arch}"
  rm -rf "${context_root}/inspect/${target_arch:?}/"*
  cp -R "${extracted_root}/." "${context_root}/inspect/${target_arch}/"
  rm -rf "${tmp_dir}"
}

linux_native_dep_allowed() {
  case "$1" in
    libcoakka_runtime_v2.so|libm.so.6|libc.so.6|ld-linux-x86-64.so.2|ld-linux-aarch64.so.1)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

require_linux_archive_self_contained() {
  local platform="$1"
  local package_root="$2"
  local path dep

  case "${platform}" in
    linux-aarch64|linux-x86_64) ;;
    *) coakka_die "dependency gate only supports Linux inspect Docker archives: ${platform}" ;;
  esac

  coakka_require_command objdump "Install binutils, then retry."
  for path in \
      "${package_root}/bin/coakka-runtime-inspect" \
      "${package_root}/lib/libcoakka_runtime_v2.so"; do
    coakka_require_file "${path}" "The published Linux inspect archive is incomplete."
    while IFS= read -r dep; do
      [[ -n "${dep}" ]] || continue
      linux_native_dep_allowed "${dep}" ||
        coakka_die "published ${platform} inspect archive declares a non-allowed dynamic dependency: ${path}"
    done < <(objdump -p "${path}" 2>/dev/null | awk '$1 == "NEEDED" { print $2 }')
  done
}

require_self_contained_dockerfiles
rm -rf "${context_root}"
mkdir -p "${context_root}/inspect"
prepare_platform linux-x86_64 amd64
prepare_platform linux-aarch64 arm64
cp "${script_dir}/Dockerfile" "${context_root}/Dockerfile"
cp "${sample_dir}/docker/entrypoint.sh" "${context_root}/entrypoint.sh"
chmod +x "${context_root}/entrypoint.sh"

printf '%s\n' "${context_root}"
