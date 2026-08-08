#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"
source "${repo_root}/scripts/sample-metadata.sh"

evidence_release="1.3.4+dc6ec284"
evidence_version="1.3.4"
coakka_evidence_tmp_dir=""

coakka_cleanup_evidence_tmp_dir() {
  if [[ -n "${coakka_evidence_tmp_dir}" && -d "${coakka_evidence_tmp_dir}" ]]; then
    rm -rf -- "${coakka_evidence_tmp_dir}"
  fi
}

trap coakka_cleanup_evidence_tmp_dir EXIT

coakka_evidence_mode() {
  case "${1:-smoke}" in
    smoke|pressure|stress|soak|connection-strategies|race|hot-reload) printf '%s\n' "$1" ;;
    -h|--help) printf '%s\n' "help" ;;
    *) printf '%s\n' "smoke" ;;
  esac
}

coakka_print_evidence_help() {
  cat <<'EOF'
{
  "schema": "coakka.runtime.native.evidence.help.v1",
  "usage": "bash run.sh runtime-test [smoke|pressure|stress|soak|connection-strategies|race|hot-reload]",
  "payloadPresets": ["64K", "128K", "256K", "512K", "1M", "2M", "3M"],
  "pressurePayloadLimit": "16K",
  "requestLimitMax": 500000,
  "concurrencyUsage": "bash run.sh runtime-test [race|hot-reload] [--threads 4] [--requests 128] [--generations 16] [--lifecycle-iterations 8] [--queue-capacity 1024] [--timeout 30s]"
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
  local package_version artifact_rel expected_sha artifact_name mode executable
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/coakka-native-evidence.XXXXXX")"
  coakka_evidence_tmp_dir="${tmp_dir}"

  platform="$(coakka_native_platform)"
  IFS='|' read -r package_version artifact_rel expected_sha <<<"$(
    coakka_runtime_native_package_fields "${platform}"
  )"
  artifact_name="coakka-runtime-native-v2-${package_version}.tar.gz"
  coakka_note "preparing native runtime evidence runner from source"
  if [[ -n "${expected_sha}" ]]; then
    coakka_note "using compatibility runtime generation ${package_version} for ${platform}; connection-strategy evidence requires 1.4.1"
    package_path="$(coakka_resolve_pinned_artifact \
      "${publish_root}" \
      "${artifact_rel}" \
      "${tmp_dir}/artifacts/${artifact_name}" \
      "${expected_sha}")"
  else
    package_path="$(coakka_resolve_artifact \
      "${publish_root}" \
      "${artifact_rel}" \
      "${tmp_dir}/artifacts/${artifact_name}")"
  fi
  mkdir -p "${tmp_dir}/package"
  tar -C "${tmp_dir}/package" -xzf "${package_path}"

  package_root="${tmp_dir}/package/coakka-runtime-native-v2-${package_version}"
  build_dir="${tmp_dir}/build"
  if [[ "${COAKKA_NATIVE_EVIDENCE_STATIC_ANALYSIS:-0}" == "1" ]]; then
    CLANG="${CLANG:-clang}" bash "${script_dir}/analyze.sh" "${package_root}/include"
  fi

  cmake_args=(
    -S "${script_dir}"
    -B "${build_dir}"
    -DCMAKE_PREFIX_PATH="${package_root}"
  )
  if [[ "${COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS:-0}" == "1" ]]; then
    cmake_args+=( -DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=ON )
  fi
  if [[ "${COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN:-0}" == "1" ]]; then
    cmake_args+=( -DCOAKKA_NATIVE_EVIDENCE_ENABLE_TSAN=ON )
  fi
  mode="$(coakka_evidence_mode "${1:-}")"
  if [[ "${mode}" == "connection-strategies" ]]; then
    cmake_args+=( -DCOAKKA_NATIVE_EVIDENCE_REQUIRE_CONNECTION_STRATEGY=ON )
  fi
  cmake "${cmake_args[@]}" >/dev/null
  cmake --build "${build_dir}" --config Release >/dev/null
  if [[ "${mode}" == "connection-strategies" ]]; then
    executable="${build_dir}/coakka_runtime_v2_connection_strategy_evidence"
    set --
  elif [[ "${mode}" == "race" || "${mode}" == "hot-reload" ]]; then
    executable="${build_dir}/coakka_runtime_v2_concurrency_evidence"
  else
    executable="${build_dir}/coakka_runtime_v2_native_evidence"
  fi
  coakka_note "starting native runtime evidence mode=${mode} path=source platform=${platform} runtime=${package_version}"

  case "$(uname -s)" in
    Darwin)
      COAKKA_EVIDENCE_EXECUTION_PATH=source \
        DYLD_LIBRARY_PATH="${package_root}/native/${platform}" \
        "${executable}" "$@"
      ;;
    Linux)
      COAKKA_EVIDENCE_EXECUTION_PATH=source \
        LD_LIBRARY_PATH="${package_root}/native/${platform}" \
        "${executable}" "$@"
      ;;
  esac
}

run_from_prebuilt() {
  local tmp_dir platform artifact_name artifact_rel archive_path package_root native_path
  coakka_require_command tar "Install tar, then retry."

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/coakka-native-evidence.XXXXXX")"
  coakka_evidence_tmp_dir="${tmp_dir}"

  platform="$(coakka_native_platform)"
  if [[ "$(coakka_evidence_mode "${1:-}")" == "connection-strategies" ||
        "$(coakka_evidence_mode "${1:-}")" == "race" ||
        "$(coakka_evidence_mode "${1:-}")" == "hot-reload" ]]; then
    coakka_die "Connection-strategy and concurrency evidence require the current source-build harness."
  fi
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
