#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

release_id="${COAKKA_RUNTIME_CLIENT_RELEASE_ID:-1.3.1+0da8c2d9}"
release_version="${COAKKA_RUNTIME_CLIENT_VERSION:-1.3.1}"
context_root="${COAKKA_RUNTIME_CLIENT_DOCKERHUB_CONTEXT:-${repo_root}/build/runtime-client-dockerhub-demo/context}"

prepare_platform() {
  local coakka_platform="$1"
  local target_arch="$2"
  local archive_name archive_rel tmp_dir archive_path extracted_root

  archive_name="coakka-client-docker-demo-v2-${release_version}-${coakka_platform}.tar.gz"
  archive_rel="demo/coakka-client/releases/${release_id}/${archive_name}"
  tmp_dir="$(mktemp -d)"

  archive_path="$(coakka_resolve_artifact "${publish_root}" "${archive_rel}" "${tmp_dir}/${archive_name}")"
  tar -C "${tmp_dir}" -xzf "${archive_path}"
  extracted_root="${tmp_dir}/coakka-client-docker-demo-v2-${release_version}-${coakka_platform}"
  coakka_require_file "${extracted_root}/artifacts/cli/bin/coakka-client" \
    "The published Docker bundle is missing the CLI binary."
  coakka_require_file "${extracted_root}/artifacts/customer-service/bin/coakka-demo-customer-service" \
    "The published Docker bundle is missing the native demo service."

  mkdir -p "${context_root}/artifacts/${target_arch}"
  rm -rf "${context_root}/artifacts/${target_arch}/cli" \
    "${context_root}/artifacts/${target_arch}/customer-service"
  cp -R "${extracted_root}/artifacts/cli" "${context_root}/artifacts/${target_arch}/cli"
  cp -R "${extracted_root}/artifacts/customer-service" \
    "${context_root}/artifacts/${target_arch}/customer-service"
  rm -rf "${tmp_dir}"
}

rm -rf "${context_root}"
mkdir -p "${context_root}"
prepare_platform linux-x86_64 amd64
prepare_platform linux-aarch64 arm64
cp "${script_dir}/Dockerfile" "${context_root}/Dockerfile"
cp "${script_dir}/entrypoint.sh" "${context_root}/entrypoint.sh"
chmod +x "${context_root}/entrypoint.sh"

printf '%s\n' "${context_root}"
