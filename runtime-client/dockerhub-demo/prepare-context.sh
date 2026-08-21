#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

release_id="${COAKKA_RUNTIME_CLIENT_RELEASE_ID:-}"
release_version="${COAKKA_RUNTIME_CLIENT_VERSION:-}"
context_root="${COAKKA_RUNTIME_CLIENT_DOCKERHUB_CONTEXT:-${repo_root}/build/runtime-client-dockerhub-demo/context}"
docker_base_name="docker.io/library/ubuntu:24.04"
docker_base_digest="sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90"
samples_source_commit="$(git -C "${repo_root}" rev-parse HEAD)"
samples_source_dirty=false
if [[ -n "$(git -C "${repo_root}" status --porcelain)" ]]; then
  samples_source_dirty=true
fi

[[ "${release_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
  coakka_die "Set COAKKA_RUNTIME_CLIENT_VERSION to the exact numeric tool version."
[[ "${release_id}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\+[0-9a-f]{40}$ ]] ||
  coakka_die "Set COAKKA_RUNTIME_CLIENT_RELEASE_ID to <version>+<full-core-commit>."
[[ "${release_id%%+*}" == "${release_version}" ]] ||
  coakka_die "Client Docker release id and version must match."

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    coakka_die "shasum or sha256sum is required"
  fi
}

require_legal_bundle() {
  local root="$1"
  local file
  for file in LICENSE NATIVE-LICENSE.md PACKAGE-LICENSE.md NOTICE; do
    coakka_require_file "${root}/${file}" \
      "The published Docker image subtree is missing its offline legal bundle."
  done
}

prepare_platform() {
  local coakka_platform="$1"
  local target_arch="$2"
  local archive_name archive_rel tmp_dir archive_path extracted_root

  archive_name="coakka-client-docker-demo-v2-${release_version}-${coakka_platform}.tar.gz"
  archive_rel="coakka-tools/coakka-client/docker-demo/releases/${release_id}/${archive_name}"
  tmp_dir="$(mktemp -d)"

  archive_path="$(coakka_resolve_artifact "${publish_root}" "${archive_rel}" "${tmp_dir}/${archive_name}")"
  tar -C "${tmp_dir}" -xzf "${archive_path}"
  extracted_root="${tmp_dir}/coakka-client-docker-demo-v2-${release_version}-${coakka_platform}"
  coakka_require_file "${extracted_root}/artifacts/cli/bin/coakka-client" \
    "The published Docker bundle is missing the CLI binary."
  coakka_require_file "${extracted_root}/artifacts/customer-service/bin/coakka-demo-customer-service" \
    "The published Docker bundle is missing the native demo service."
  require_legal_bundle "${extracted_root}/artifacts/cli"
  require_legal_bundle "${extracted_root}/artifacts/customer-service"

  mkdir -p "${context_root}/artifacts/${target_arch}"
  rm -rf "${context_root}/artifacts/${target_arch}/cli" \
    "${context_root}/artifacts/${target_arch}/customer-service"
  cp -R "${extracted_root}/artifacts/cli" "${context_root}/artifacts/${target_arch}/cli"
  cp -R "${extracted_root}/artifacts/customer-service" \
    "${context_root}/artifacts/${target_arch}/customer-service"
  printf 'input_archive_%s=%s@sha256:%s\n' \
    "${target_arch}" \
    "${archive_name}" \
    "$(sha256_file "${archive_path}")" >>"${context_root}/IMAGE-RELEASE.txt"
  rm -rf "${tmp_dir}"
}

rm -rf "${context_root}"
mkdir -p "${context_root}"
cat >"${context_root}/IMAGE-RELEASE.txt" <<EOF
CoAkka Runtime Client Docker Hub image inputs
version=${release_version}
runtime_generation=${release_id}
samples_source_commit=${samples_source_commit}
samples_source_dirty=${samples_source_dirty}
docker_base_name=${docker_base_name}
docker_base_digest=${docker_base_digest}
EOF
prepare_platform linux-x86_64 amd64
prepare_platform linux-aarch64 arm64
cp "${script_dir}/Dockerfile" "${context_root}/Dockerfile"
cp "${script_dir}/entrypoint.sh" "${context_root}/entrypoint.sh"
chmod +x "${context_root}/entrypoint.sh"
grep -Fx "FROM ubuntu:24.04@${docker_base_digest}" \
  "${context_root}/Dockerfile" >/dev/null || \
  coakka_die "Docker Hub client image base must be pinned by digest."
grep -F "org.opencontainers.image.base.name=\"${docker_base_name}\"" \
  "${context_root}/Dockerfile" >/dev/null || \
  coakka_die "Docker Hub client image must record its base name."
grep -F "io.coakka.base.digest=\"${docker_base_digest}\"" \
  "${context_root}/Dockerfile" >/dev/null || \
  coakka_die "Docker Hub client image must record its base digest."

printf '%s\n' "${context_root}"
