#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
core_root="${COAKKA_CORE_ROOT:-${repo_root}/../coakkaCoreNativeDev}"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"

# shellcheck disable=SC1091
source "${repo_root}/scripts/resolve-artifact.sh"
# shellcheck disable=SC1091
source "${repo_root}/scripts/sample-metadata.sh"
# shellcheck disable=SC1091
source "${repo_root}/scripts/sample-utils.sh"

usage() {
  cat <<'EOF'
CoAkka SFTP artifact publisher sample

Usage:
  bash run.sh check
  bash run.sh source-candidate

Commands:
  check             Compile-check the public C consumer sources.
  source-candidate  Build the addon from the sibling Core checkout, stage its
                    package contract, and run SFTP -> File Lane end to end.

The source-candidate command is intentionally separate from the root sample
lane until a versioned addon archive has passed release promotion.
EOF
}

native_platform() {
  local system machine
  system="$(uname -s)"
  machine="$(uname -m)"
  case "${system}:${machine}" in
    Darwin:arm64|Darwin:aarch64) printf '%s\n' macos-aarch64 ;;
    Linux:aarch64|Linux:arm64) printf '%s\n' linux-aarch64 ;;
    Linux:x86_64|Linux:amd64) printf '%s\n' linux-x86_64 ;;
    *) coakka_die "Unsupported SFTP addon sample host: ${system}/${machine}" ;;
  esac
}

reserve_loopback_port() {
  python3 - <<'PY'
import socket

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

run_check() {
  coakka_require_command cc "Install a C11 compiler, then retry."
  coakka_require_file \
    "${core_root}/v2/include/coakka/v2/file_lane.h" \
    "Set COAKKA_CORE_ROOT to the coakkaCoreNativeDev checkout."
  coakka_require_file \
    "${core_root}/v2/addons/artifact-source-sftp/include/coakka/addons/artifact_publisher_sftp.h" \
    "The source-candidate addon header is required."

  cc -std=c11 -Wall -Wextra -Wpedantic -Werror \
    -I"${core_root}/v2/include" \
    -I"${core_root}/v2/addons/artifact-source-sftp/include" \
    -fsyntax-only \
    "${script_dir}/sample_common.c" \
    "${script_dir}/service_a.c" \
    "${script_dir}/service_b.c"

  if command -v shellcheck >/dev/null 2>&1; then
    shellcheck "${script_dir}/run.sh"
  else
    coakka_note "shellcheck is not installed; C11 compile checks passed"
  fi
  coakka_note "SFTP addon sample source checks passed"
}

stage_addon_package() {
  local platform="$1"
  local build_dir="$2"
  local package_root="$3"
  local source_dir
  local -a libraries

  source_dir="${build_dir}/addons/artifact-source-sftp"
  mkdir -p \
    "${package_root}/cmake" \
    "${package_root}/include/coakka/addons" \
    "${package_root}/native/${platform}"
  cp \
    "${publish_root}/runtime-addons/artifact-publisher-sftp/native/package-template/cmake/CoAkkaRuntimeAddonArtifactPublisherSftpConfig.cmake" \
    "${package_root}/cmake/"
  cp \
    "${core_root}/v2/addons/artifact-source-sftp/include/coakka/addons/artifact_publisher_sftp.h" \
    "${package_root}/include/coakka/addons/"

  case "${platform}" in
    macos-aarch64)
      libraries=("${source_dir}"/libcoakka_addon_artifact_publisher_sftp*.dylib)
      ;;
    linux-aarch64|linux-x86_64)
      libraries=("${source_dir}"/libcoakka_addon_artifact_publisher_sftp.so*)
      ;;
  esac
  if [[ ! -e "${libraries[0]}" ]]; then
    coakka_die "Built SFTP addon library is missing under ${source_dir}."
  fi
  # Preserve the SONAME symlink chain: the unversioned consumer link and the
  # loader-facing versioned name are both part of the native package contract.
  cp -pPR "${libraries[@]}" "${package_root}/native/${platform}/"
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

run_source_candidate() {
  local platform runtime_version artifact_rel expected_sha runtime_archive
  local workspace runtime_package_root addon_package_root core_build consumer_build
  local runtime_native addon_native sshd_bin username sftp_port receiver_port
  local host_key client_key authorized_keys payload sshd_config sshd_log pid_file
  local host_sha256 artifact_sha256 artifact_size receiver_log ready_file
  local destination staging_root sshd_pid="" receiver_pid="" sshd_ready=0
  local attempt

  run_check
  coakka_require_command cmake "Install CMake 3.20 or newer, then retry."
  coakka_require_command c++ "Install a C++ compiler, then retry."
  coakka_require_command openssl "Install OpenSSL command-line tools, then retry."
  coakka_require_command python3 "Install Python 3, then retry."
  coakka_require_command ssh-keygen "Install OpenSSH client tools, then retry."
  coakka_require_command tar "Install tar, then retry."
  coakka_require_command xxd "Install xxd, then retry."
  coakka_require_file \
    "${publish_root}/runtime-addons/artifact-publisher-sftp/native/package-template/cmake/CoAkkaRuntimeAddonArtifactPublisherSftpConfig.cmake" \
    "Set COAKKA_PUBLISH_ROOT to the coakka-publish checkout."

  if [[ -x /usr/sbin/sshd ]]; then
    sshd_bin=/usr/sbin/sshd
  elif command -v sshd >/dev/null 2>&1; then
    sshd_bin="$(command -v sshd)"
  else
    coakka_die "OpenSSH sshd is required for the loopback SFTP fixture."
  fi

  platform="$(native_platform)"
  workspace="$(mktemp -d "${TMPDIR:-/tmp}/coakka-sftp-sample.XXXXXX")"
  core_build="${COAKKA_SFTP_CORE_BUILD:-${script_dir}/build/core}"
  consumer_build="${script_dir}/build/consumer"
  cleanup_sftp_sample() {
    if [[ -n "${receiver_pid}" ]]; then
      kill "${receiver_pid}" 2>/dev/null || true
      wait "${receiver_pid}" 2>/dev/null || true
    fi
    if [[ -n "${sshd_pid}" ]]; then
      kill "${sshd_pid}" 2>/dev/null || true
      wait "${sshd_pid}" 2>/dev/null || true
    fi
    rm -rf "${workspace}"
  }
  trap cleanup_sftp_sample EXIT

  IFS='|' read -r runtime_version artifact_rel expected_sha <<<"$(
    coakka_runtime_native_package_fields "${platform}"
  )"
  if [[ -n "${expected_sha}" ]]; then
    runtime_archive="$(coakka_resolve_pinned_artifact \
      "${publish_root}" "${artifact_rel}" \
      "${workspace}/runtime.tar.gz" "${expected_sha}")"
  else
    runtime_archive="$(coakka_resolve_artifact \
      "${publish_root}" "${artifact_rel}" "${workspace}/runtime.tar.gz")"
  fi
  mkdir -p "${workspace}/runtime-package"
  tar -C "${workspace}/runtime-package" -xzf "${runtime_archive}"
  runtime_package_root="${workspace}/runtime-package/coakka-runtime-native-v2-${runtime_version}"

  coakka_note "building the source-candidate SFTP addon"
  cmake -S "${core_root}/v2" -B "${core_build}" \
    -DCOAKKA_V2_BUILD_SFTP_ARTIFACT_PUBLISHER=ON
  cmake --build "${core_build}" \
    --target coakka_addon_artifact_publisher_sftp

  addon_package_root="${workspace}/coakka-runtime-addon-artifact-publisher-sftp-native-source"
  stage_addon_package "${platform}" "${core_build}" "${addon_package_root}"

  rm -rf "${consumer_build}"
  cmake -S "${script_dir}" -B "${consumer_build}" \
    -DCMAKE_PREFIX_PATH="${runtime_package_root};${addon_package_root}"
  cmake --build "${consumer_build}"

  username="$(id -un)"
  sftp_port="$(reserve_loopback_port)"
  receiver_port=0
  host_key="${workspace}/ssh_host_ecdsa_key"
  client_key="${workspace}/client_ecdsa_key"
  authorized_keys="${workspace}/authorized_keys"
  payload="${workspace}/model.bin"
  sshd_config="${workspace}/sshd_config"
  sshd_log="${workspace}/sshd.log"
  pid_file="${workspace}/sshd.pid"
  receiver_log="${workspace}/service-b.log"
  ready_file="${workspace}/service-b.ready"
  destination="${workspace}/service-b/model.bin"
  staging_root="${workspace}/service-a-staging"
  mkdir -p "$(dirname "${destination}")" "${staging_root}"

  ssh-keygen -q -t ecdsa -b 256 -N '' -f "${host_key}"
  ssh-keygen -q -t ecdsa -b 256 -N '' -f "${client_key}"
  cp "${client_key}.pub" "${authorized_keys}"
  chmod 600 "${host_key}" "${client_key}" "${authorized_keys}"
  python3 - "${payload}" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
block = bytes(((index * 31 + 17) & 0xff) for index in range(65536))
with path.open("wb") as output:
    for _ in range(33):
        output.write(block)
    output.write(b"coakka-sftp-sample")
PY

  {
    printf 'Port %s\n' "${sftp_port}"
    printf 'ListenAddress 127.0.0.1\n'
    printf 'PidFile %s\n' "${pid_file}"
    printf 'HostKey %s\n' "${host_key}"
    printf 'AuthorizedKeysFile %s\n' "${authorized_keys}"
    printf 'PubkeyAuthentication yes\n'
    printf 'PasswordAuthentication no\n'
    printf 'KbdInteractiveAuthentication no\n'
    printf 'AuthenticationMethods publickey\n'
    printf 'UsePAM no\n'
    printf 'StrictModes no\n'
    if [[ "${username}" == root ]]; then
      printf 'PermitRootLogin prohibit-password\n'
    else
      printf 'PermitRootLogin no\n'
    fi
    printf 'AllowUsers %s\n' "${username}"
    printf 'Subsystem sftp internal-sftp\n'
    printf 'LogLevel VERBOSE\n'
  } >"${sshd_config}"
  "${sshd_bin}" -t -f "${sshd_config}"
  "${sshd_bin}" -D -e -f "${sshd_config}" >"${sshd_log}" 2>&1 &
  sshd_pid="$!"
  for ((attempt = 0; attempt < 100; ++attempt)); do
    if python3 - "${sftp_port}" <<'PY'
import socket
import sys

try:
    with socket.create_connection(("127.0.0.1", int(sys.argv[1])), 0.1):
        pass
except OSError:
    raise SystemExit(1)
PY
    then
      sshd_ready=1
      break
    fi
    if ! kill -0 "${sshd_pid}" 2>/dev/null; then
      cat "${sshd_log}" >&2
      coakka_die "OpenSSH fixture exited before accepting connections."
    fi
    sleep 0.05
  done
  if [[ "${sshd_ready}" -ne 1 ]]; then
    cat "${sshd_log}" >&2
    coakka_die "OpenSSH fixture did not become ready."
  fi

  host_sha256="$(awk '{print $2}' "${host_key}.pub" | \
    openssl base64 -d -A | openssl dgst -sha256 -binary | xxd -p -c 256)"
  artifact_sha256="$(coakka_artifact_sha256 "${payload}")"
  artifact_size="$(wc -c <"${payload}" | tr -d '[:space:]')"
  runtime_native="${runtime_package_root}/native/${platform}"
  addon_native="${addon_package_root}/native/${platform}"

  export COAKKA_SAMPLE_JOB_ID="model-release-001"
  export COAKKA_SAMPLE_SFTP_LOGICAL_HOST="model-registry.internal"
  export COAKKA_SAMPLE_SFTP_CONNECT_ADDRESS="127.0.0.1"
  export COAKKA_SAMPLE_SFTP_PORT="${sftp_port}"
  export COAKKA_SAMPLE_SFTP_USERNAME="${username}"
  export COAKKA_SAMPLE_SFTP_PRIVATE_KEY="${client_key}"
  export COAKKA_SAMPLE_SFTP_HOST_SHA256="${host_sha256}"
  export COAKKA_SAMPLE_SFTP_REMOTE_PATH="${payload}"
  export COAKKA_SAMPLE_STAGING_ROOT="${staging_root}"
  export COAKKA_SAMPLE_STAGING_NAME="model.bin"
  export COAKKA_SAMPLE_ARTIFACT_SIZE="${artifact_size}"
  export COAKKA_SAMPLE_ARTIFACT_SHA256="${artifact_sha256}"
  export COAKKA_SAMPLE_TRANSFER_ID="model-release-001-service-b"
  export COAKKA_SAMPLE_TRANSFER_TOKEN="sample-one-use-token"
  export COAKKA_SAMPLE_RECEIVER_HOST="127.0.0.1"
  export COAKKA_SAMPLE_RECEIVER_PORT="${receiver_port}"
  export COAKKA_SAMPLE_DESTINATION_PATH="${destination}"
  export COAKKA_SAMPLE_READY_FILE="${ready_file}"

  run_with_native_libraries "${runtime_native}" "${addon_native}" \
    "${consumer_build}/coakka_sftp_sample_service_b" \
    >"${receiver_log}" 2>&1 &
  receiver_pid="$!"
  for ((attempt = 0; attempt < 100; ++attempt)); do
    [[ -s "${ready_file}" ]] && break
    if ! kill -0 "${receiver_pid}" 2>/dev/null; then
      cat "${receiver_log}" >&2
      coakka_die "Service B exited before publishing readiness."
    fi
    sleep 0.05
  done
  if [[ ! -s "${ready_file}" ]]; then
    cat "${receiver_log}" >&2
    coakka_die "Service B did not publish readiness."
  fi
  receiver_port="$(tr -d '[:space:]' <"${ready_file}")"
  if [[ ! "${receiver_port}" =~ ^[0-9]+$ ]] ||
    ((receiver_port < 1 || receiver_port > 65535)); then
    cat "${receiver_log}" >&2
    coakka_die "Service B published an invalid receiver port."
  fi
  export COAKKA_SAMPLE_RECEIVER_PORT="${receiver_port}"

  run_with_native_libraries "${runtime_native}" "${addon_native}" \
    "${consumer_build}/coakka_sftp_sample_service_a"
  if ! wait "${receiver_pid}"; then
    receiver_pid=""
    cat "${receiver_log}" >&2
    coakka_die "Service B rejected or failed the distributed artifact."
  fi
  receiver_pid=""
  cat "${receiver_log}"
  cmp "${payload}" "${destination}"
  coakka_note "SFTP -> File Lane sample passed (${artifact_size} bytes, sha256=${artifact_sha256})"
  trap - EXIT
  cleanup_sftp_sample
}

case "${1:-help}" in
  check)
    run_check
    ;;
  source-candidate)
    run_source_candidate
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
