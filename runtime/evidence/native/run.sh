#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

runtime_artifact_rel="runtime/native/releases/1.3.2+caff6d6d/coakka-runtime-native-v2-1.3.2.tar.gz"
evidence_release="1.3.2+caff6d6d"
evidence_version="1.3.2"
coakka_evidence_tmp_dir=""

coakka_cleanup_evidence_tmp_dir() {
  if [[ -n "${coakka_evidence_tmp_dir}" && -d "${coakka_evidence_tmp_dir}" ]]; then
    rm -rf -- "${coakka_evidence_tmp_dir}"
  fi
}

trap coakka_cleanup_evidence_tmp_dir EXIT

coakka_evidence_mode() {
  case "${1:-smoke}" in
    smoke|pressure|stress|soak) printf '%s\n' "$1" ;;
    -h|--help) printf '%s\n' "help" ;;
    *) printf '%s\n' "smoke" ;;
  esac
}

coakka_print_evidence_help() {
  cat <<'EOF'
{
  "schema": "coakka.runtime.native.evidence.help.v1",
  "usage": "bash run.sh runtime/evidence/native [smoke|pressure|stress|soak] [--payload 64K] [--requests 128] [--duration 10s] [--queue-capacity 1024] [--max-in-flight 64]",
  "payloadPresets": ["64K", "128K", "256K", "512K", "1M", "2M", "3M"],
  "pressurePayloadLimit": "16K",
  "requestLimitMax": 500000
}
EOF
}

coakka_native_platform() {
  local system machine
  system="$(uname -s)"
  machine="$(uname -m)"
  case "${system}:${machine}" in
    Darwin:arm64|Darwin:aarch64) printf '%s\n' "macos-aarch64" ;;
    Linux:aarch64|Linux:arm64) printf '%s\n' "linux-aarch64" ;;
    Linux:x86_64|Linux:amd64) printf '%s\n' "linux-x86_64" ;;
    *) coakka_die "Unsupported native evidence platform: ${system}/${machine}" ;;
  esac
}

coakka_can_build_from_source() {
  command -v cmake >/dev/null 2>&1 &&
    command -v cc >/dev/null 2>&1 &&
    command -v tar >/dev/null 2>&1
}

run_from_source() {
  local tmp_dir package_path package_root platform build_dir
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/coakka-native-evidence.XXXXXX")"
  coakka_evidence_tmp_dir="${tmp_dir}"

  coakka_note "preparing native runtime evidence runner from source"
  package_path="$(coakka_resolve_artifact "${publish_root}" "${runtime_artifact_rel}" "${tmp_dir}/artifacts/coakka-runtime-native-v2-1.3.2.tar.gz")"
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${package_path}"

  package_root="${tmp_dir}/package/coakka-runtime-native-v2-1.3.2"
  platform="$(coakka_native_platform)"
  build_dir="${tmp_dir}/build"
  cmake -S "${script_dir}" -B "${build_dir}" -DCMAKE_PREFIX_PATH="${package_root}" >/dev/null
  cmake --build "${build_dir}" --config Release >/dev/null
  coakka_note "starting native runtime evidence mode=$(coakka_evidence_mode "${1:-}") path=source platform=${platform}"

  case "$(uname -s)" in
    Darwin)
      COAKKA_EVIDENCE_EXECUTION_PATH=source \
        DYLD_LIBRARY_PATH="${package_root}/native/${platform}" \
        "${build_dir}/coakka_runtime_v2_native_evidence" "$@"
      ;;
    Linux)
      COAKKA_EVIDENCE_EXECUTION_PATH=source \
        LD_LIBRARY_PATH="${package_root}/native/${platform}" \
        "${build_dir}/coakka_runtime_v2_native_evidence" "$@"
      ;;
  esac
}

run_from_prebuilt() {
  local tmp_dir platform artifact_name artifact_rel archive_path package_root native_path
  coakka_require_command tar "Install tar, then retry."

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/coakka-native-evidence.XXXXXX")"
  coakka_evidence_tmp_dir="${tmp_dir}"

  platform="$(coakka_native_platform)"
  coakka_note "preparing published native runtime evidence runner platform=${platform}"
  artifact_name="coakka-runtime-native-evidence-v2-${evidence_version}-${platform}.tar.gz"
  artifact_rel="runtime/evidence/native/releases/${evidence_release}/${artifact_name}"
  archive_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/${artifact_name}")"
  mkdir -p "${tmp_dir}/evidence"
  tar -C "${tmp_dir}/evidence" -xzf "${archive_path}"

  package_root="${tmp_dir}/evidence/coakka-runtime-native-evidence-v2-${evidence_version}-${platform}"
  native_path="${package_root}/native/${platform}"
  coakka_note "starting native runtime evidence mode=$(coakka_evidence_mode "${1:-}") path=prebuilt platform=${platform}"
  case "$(uname -s)" in
    Darwin)
      COAKKA_EVIDENCE_EXECUTION_PATH=prebuilt \
        DYLD_LIBRARY_PATH="${native_path}" \
        "${package_root}/bin/coakka-runtime-native-evidence" "$@"
      ;;
    Linux)
      COAKKA_EVIDENCE_EXECUTION_PATH=prebuilt \
        LD_LIBRARY_PATH="${native_path}" \
        "${package_root}/bin/coakka-runtime-native-evidence" "$@"
      ;;
  esac
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  coakka_print_evidence_help
elif [[ "${COAKKA_NATIVE_EVIDENCE_USE_PREBUILT:-0}" == "1" ]]; then
  run_from_prebuilt "$@"
elif coakka_can_build_from_source; then
  run_from_source "$@"
else
  coakka_note "native toolchain not found; using published prebuilt evidence runner when available"
  run_from_prebuilt "$@"
fi
