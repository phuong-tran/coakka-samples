#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
core_root="${COAKKA_CORE_ROOT:-${repo_root}/../coakkaCoreNativeDev}"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
addon_version="1.1.0"
addon_release="1.1.0+d1032f6d"

# shellcheck disable=SC1091
source "${repo_root}/scripts/resolve-artifact.sh"
# shellcheck disable=SC1091
source "${repo_root}/scripts/sample-metadata.sh"
# shellcheck disable=SC1091
source "${repo_root}/scripts/sample-utils.sh"

usage() {
  cat <<'EOF'
Usage: bash run-addon.sh <addon> [published|check]

Supported addons:
  https s3 local-drop azure-blob gcs webdav oci-registry
  huggingface-hub github-release google-drive dropbox
EOF
}

validate_addon() {
  case "$1" in
    https|s3|local-drop|azure-blob|gcs|webdav|oci-registry|huggingface-hub|github-release|google-drive|dropbox) ;;
    *) usage >&2; coakka_die "unsupported artifact addon sample: $1" ;;
  esac
}

definition_for_addon() {
  printf '%s\n' "$1" | tr '[:lower:]-' '[:upper:]_'
}

native_platform() {
  case "$(uname -s):$(uname -m)" in
    Darwin:arm64|Darwin:aarch64) printf '%s\n' macos-aarch64 ;;
    Linux:aarch64|Linux:arm64) printf '%s\n' linux-aarch64 ;;
    Linux:x86_64|Linux:amd64) printf '%s\n' linux-x86_64 ;;
    *) coakka_die "unsupported native addon sample host: $(uname -s)/$(uname -m)" ;;
  esac
}

run_with_native_libraries() {
  local runtime_native="$1"
  local addon_native="$2"
  shift 2
  case "$(uname -s)" in
    Darwin)
      DYLD_LIBRARY_PATH="${addon_native}:${runtime_native}${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}" "$@"
      ;;
    Linux)
      LD_LIBRARY_PATH="${addon_native}:${runtime_native}${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}" "$@"
      ;;
  esac
}

run_c_checks() {
  local addon="$1"
  local runtime_include="$2"
  local addon_include="$3"
  local definition
  definition="$(definition_for_addon "${addon}")"
  coakka_require_command cc "Install a C11 compiler, then retry."
  cc -std=c11 -Wall -Wextra -Wpedantic -Werror \
    -I"${runtime_include}" -I"${addon_include}" \
    -D"COAKKA_SAMPLE_${definition}" -fsyntax-only \
    "${script_dir}/sample_common.c" \
    "${script_dir}/service_a.c" \
    "${script_dir}/service_b.c"
  coakka_note "${addon} native sample C11 checks passed"
}

run_source_check() {
  local addon="$1"
  coakka_require_file "${core_root}/v2/include/coakka/v2/file_lane.h" \
    "Set COAKKA_CORE_ROOT to the coakkaCoreNativeDev checkout."
  coakka_require_file \
    "${core_root}/v2/addons/artifact-source-${addon}/include/coakka/addons/artifact_publisher_${addon//-/_}.h" \
    "The released addon header source is required."
  run_c_checks "${addon}" "${core_root}/v2/include" \
    "${core_root}/v2/addons/artifact-source-${addon}/include"
}

source_url_for_addon() {
  local addon="$1"
  local port="$2"
  local sha256="$3"
  local base="https://127.0.0.1:${port}"
  case "${addon}" in
    https) printf '%s\n' "${base}/artifact/model.bin" ;;
    s3) printf '%s\n' "${base}/" ;;
    azure-blob)
      printf '%s\n' "${base}/container/model.bin?sv=2022-11-02&sp=r&spr=https&sr=bv&se=2030-08-12T05%3A05%3A06Z&sig=sample-signature&versionid=2026-08-12T04%3A05%3A06.0000000Z"
      ;;
    gcs)
      printf '%s\n' "${base}/sample-bucket/model.bin?X-Goog-Algorithm=GOOG4-HMAC-SHA256&X-Goog-Credential=sample%2F20260815%2Fauto%2Fstorage%2Fgoog4_request&X-Goog-Date=20260815T000000Z&X-Goog-Expires=600&X-Goog-SignedHeaders=host&X-Goog-Signature=$(printf '0%.0s' {1..64})&generation=1700000000000001"
      ;;
    webdav) printf '%s\n' "${base}/dav/model.bin" ;;
    oci-registry) printf '%s\n' "${base}/v2/coakka/sample/blobs/sha256:${sha256}" ;;
    huggingface-hub)
      printf '%s\n' "${base}/coakka/sample/resolve/0123456789abcdef0123456789abcdef01234567/model.bin"
      ;;
    github-release) printf '%s\n' "${base}/repos/coakka/sample/releases/assets/1001" ;;
    google-drive)
      printf '%s\n' "${base}/drive/v3/files/sample-file/revisions/sample-revision?alt=media"
      ;;
    dropbox) printf '%s\n' "${base}/2/files/download" ;;
    *) return 1 ;;
  esac
}

run_published() {
  local addon="$1"
  local platform runtime_version runtime_artifact expected_sha runtime_archive
  local addon_artifact addon_archive workspace runtime_root addon_root build_dir
  local payload artifact_sha256 artifact_size staging_root drop_root destination
  local receiver_log ready_file runtime_native addon_native receiver_port
  local server_pid="" receiver_pid="" port_file fixture_port source_url=""
  local ca_key ca_cert server_key server_csr server_cert attempt

  coakka_require_command cmake "Install CMake 3.20 or newer, then retry."
  coakka_require_command openssl "Install OpenSSL command-line tools, then retry."
  coakka_require_command python3 "Install Python 3, then retry."
  coakka_require_command tar "Install tar, then retry."
  platform="$(native_platform)"
  if [[ "${addon}" == local-drop && "${platform}" == windows-* ]]; then
    coakka_die "Local Drop has no released Windows backend."
  fi

  workspace="$(mktemp -d "${TMPDIR:-/tmp}/coakka-${addon}-sample.XXXXXX")"
  cleanup_sample() {
    if [[ -n "${receiver_pid}" ]]; then
      kill "${receiver_pid}" 2>/dev/null || true
      wait "${receiver_pid}" 2>/dev/null || true
    fi
    if [[ -n "${server_pid}" ]]; then
      kill "${server_pid}" 2>/dev/null || true
      wait "${server_pid}" 2>/dev/null || true
    fi
    rm -rf "${workspace}"
  }
  trap cleanup_sample EXIT

  IFS='|' read -r runtime_version runtime_artifact expected_sha <<<"$(
    coakka_runtime_native_package_fields "${platform}"
  )"
  if [[ -n "${expected_sha}" ]]; then
    runtime_archive="$(coakka_resolve_pinned_artifact \
      "${publish_root}" "${runtime_artifact}" "${workspace}/runtime.tar.gz" \
      "${expected_sha}")"
  else
    runtime_archive="$(coakka_resolve_artifact \
      "${publish_root}" "${runtime_artifact}" "${workspace}/runtime.tar.gz")"
  fi
  mkdir -p "${workspace}/runtime-package"
  tar -C "${workspace}/runtime-package" -xzf "${runtime_archive}"
  runtime_root="${workspace}/runtime-package/coakka-runtime-native-v2-${runtime_version}"

  addon_artifact="runtime-addons/artifact-publisher-${addon}/native/releases/${addon_release}/coakka-runtime-addon-artifact-publisher-${addon}-native-${addon_version}.tar.gz"
  addon_archive="$(coakka_resolve_artifact \
    "${publish_root}" "${addon_artifact}" "${workspace}/addon.tar.gz")"
  mkdir -p "${workspace}/addon-package"
  tar -C "${workspace}/addon-package" -xzf "${addon_archive}"
  addon_root="${workspace}/addon-package/coakka-runtime-addon-artifact-publisher-${addon}-native-${addon_version}"

  run_c_checks "${addon}" "${runtime_root}/include" "${addon_root}/include"
  build_dir="${script_dir}/build/${addon}"
  rm -rf "${build_dir}"
  cmake -S "${script_dir}" -B "${build_dir}" \
    -DCOAKKA_SAMPLE_ADDON="${addon}" \
    -DCMAKE_PREFIX_PATH="${runtime_root};${addon_root}"
  cmake --build "${build_dir}"

  payload="${workspace}/model.bin"
  python3 - "${payload}" <<'PY'
import pathlib
import sys

block = bytes(((index * 31 + 17) & 0xff) for index in range(65536))
pathlib.Path(sys.argv[1]).write_bytes(block * 5 + b"coakka-native-addon-sample")
PY
  artifact_sha256="$(coakka_artifact_sha256 "${payload}")"
  artifact_size="$(wc -c <"${payload}" | tr -d '[:space:]')"
  staging_root="${workspace}/service-a-staging"
  drop_root="${workspace}/drop"
  destination="${workspace}/service-b/model.bin"
  receiver_log="${workspace}/service-b.log"
  ready_file="${workspace}/service-b.ready"
  mkdir -p "${staging_root}" "${drop_root}" "$(dirname "${destination}")"

  if [[ "${addon}" == local-drop ]]; then
    cp "${payload}" "${drop_root}/model.bin"
  else
    ca_key="${workspace}/ca.key"
    ca_cert="${workspace}/ca.pem"
    server_key="${workspace}/server.key"
    server_csr="${workspace}/server.csr"
    server_cert="${workspace}/server.pem"
    port_file="${workspace}/fixture.port"
    openssl req -x509 -newkey rsa:2048 -nodes -days 1 -sha256 \
      -subj '/CN=CoAkka native addon sample CA' \
      -keyout "${ca_key}" -out "${ca_cert}" >/dev/null 2>&1
    openssl req -newkey rsa:2048 -nodes -sha256 -subj '/CN=localhost' \
      -addext 'subjectAltName=IP:127.0.0.1,DNS:localhost' \
      -keyout "${server_key}" -out "${server_csr}" >/dev/null 2>&1
    printf 'subjectAltName=IP:127.0.0.1,DNS:localhost\nextendedKeyUsage=serverAuth\n' \
      >"${workspace}/server.ext"
    openssl x509 -req -days 1 -sha256 -in "${server_csr}" \
      -CA "${ca_cert}" -CAkey "${ca_key}" -CAcreateserial \
      -extfile "${workspace}/server.ext" -out "${server_cert}" >/dev/null 2>&1
    python3 "${script_dir}/fixture_server.py" \
      --addon "${addon}" --certificate "${server_cert}" --key "${server_key}" \
      --payload "${payload}" --port-file "${port_file}" \
      --sha256 "${artifact_sha256}" &
    server_pid="$!"
    for ((attempt = 0; attempt < 300; ++attempt)); do
      [[ -s "${port_file}" ]] && break
      kill -0 "${server_pid}" 2>/dev/null || coakka_die "TLS fixture exited early."
      sleep 0.05
    done
    [[ -s "${port_file}" ]] || coakka_die "TLS fixture did not become ready."
    fixture_port="$(tr -d '[:space:]' <"${port_file}")"
    [[ "${fixture_port}" =~ ^[0-9]+$ ]] || coakka_die "TLS fixture returned an invalid port."
    source_url="$(source_url_for_addon "${addon}" "${fixture_port}" "${artifact_sha256}")"
    export COAKKA_SAMPLE_CA_FILE="${ca_cert}"
    export COAKKA_SAMPLE_SOURCE_URL="${source_url}"
  fi

  export COAKKA_SAMPLE_JOB_ID="${addon}-sample-job"
  export COAKKA_SAMPLE_STAGING_ROOT="${staging_root}"
  export COAKKA_SAMPLE_STAGING_NAME="model.bin"
  export COAKKA_SAMPLE_DROP_ROOT="${drop_root}"
  export COAKKA_SAMPLE_SOURCE_NAME="model.bin"
  export COAKKA_SAMPLE_ARTIFACT_SIZE="${artifact_size}"
  export COAKKA_SAMPLE_ARTIFACT_SHA256="${artifact_sha256}"
  export COAKKA_SAMPLE_TRANSFER_ID="${addon}-sample-transfer"
  export COAKKA_SAMPLE_TRANSFER_TOKEN="sample-one-use-token"
  export COAKKA_SAMPLE_RECEIVER_HOST="127.0.0.1"
  export COAKKA_SAMPLE_RECEIVER_PORT="0"
  export COAKKA_SAMPLE_DESTINATION_PATH="${destination}"
  export COAKKA_SAMPLE_READY_FILE="${ready_file}"

  runtime_native="${runtime_root}/native/${platform}"
  addon_native="${addon_root}/native/${platform}"
  run_with_native_libraries "${runtime_native}" "${addon_native}" \
    "${build_dir}/coakka_artifact_sample_service_b" >"${receiver_log}" 2>&1 &
  receiver_pid="$!"
  for ((attempt = 0; attempt < 300; ++attempt)); do
    [[ -s "${ready_file}" ]] && break
    if ! kill -0 "${receiver_pid}" 2>/dev/null; then
      cat "${receiver_log}" >&2
      coakka_die "Service B exited before readiness."
    fi
    sleep 0.05
  done
  [[ -s "${ready_file}" ]] || coakka_die "Service B readiness timed out."
  receiver_port="$(tr -d '[:space:]' <"${ready_file}")"
  [[ "${receiver_port}" =~ ^[0-9]+$ ]] || coakka_die "Service B returned an invalid port."
  export COAKKA_SAMPLE_RECEIVER_PORT="${receiver_port}"

  run_with_native_libraries "${runtime_native}" "${addon_native}" \
    "${build_dir}/coakka_artifact_sample_service_a"
  if ! wait "${receiver_pid}"; then
    receiver_pid=""
    cat "${receiver_log}" >&2
    coakka_die "Service B rejected or failed the artifact."
  fi
  receiver_pid=""
  cat "${receiver_log}"
  cmp "${payload}" "${destination}"
  coakka_note "${addon} -> File Lane sample passed (${artifact_size} bytes, sha256=${artifact_sha256})"
  trap - EXIT
  cleanup_sample
}

if [[ "$#" -lt 1 ]]; then
  usage >&2
  exit 2
fi
addon="$1"
command="${2:-published}"
validate_addon "${addon}"
case "${command}" in
  published) run_published "${addon}" ;;
  check) run_source_check "${addon}" ;;
  help|-h|--help) usage ;;
  *) usage >&2; exit 2 ;;
esac
