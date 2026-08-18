#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runtime_include="${1:-${COAKKA_NATIVE_EVIDENCE_RUNTIME_INCLUDE:-}}"
clang_bin="${CLANG:-clang}"

if [[ -z "${runtime_include}" ]]; then
  printf 'usage: %s <runtime-include-directory>\n' "$0" >&2
  exit 64
fi
if [[ ! -f "${runtime_include}/coakka/v2/runtime.h" ]]; then
  printf 'runtime header not found under %s\n' "${runtime_include}" >&2
  exit 66
fi
if ! command -v "${clang_bin}" >/dev/null 2>&1; then
  printf 'clang analyzer not found: %s\n' "${clang_bin}" >&2
  exit 69
fi

sources=(
  concurrency_config.c
  concurrency_main.c
  concurrency_report.c
  concurrency_runtime.c
  evidence_config.c
  evidence_json.c
  evidence_platform.c
  evidence_runtime.c
  evidence_report.c
  main.c
)
if [[ -f "${runtime_include}/coakka/v2/runtime_transport_config.h" ]]; then
  sources+=(
    connection_strategy_contract.c
    connection_strategy_report.c
    connection_strategy_main.c
  )
fi
if [[ -f "${runtime_include}/coakka/v2/file_lane.h" ]]; then
  sources+=(file_lane_main.c)
fi
if [[ -f "${runtime_include}/coakka/v2/stream_lane.h" ]]; then
  sources+=(stream_lane_main.c)
fi

platform_definitions=()
case "$(uname -s)" in
  Darwin) platform_definitions=(-D_POSIX_C_SOURCE=200809L -D_DARWIN_C_SOURCE) ;;
  Linux) platform_definitions=(-D_POSIX_C_SOURCE=200809L) ;;
esac

for source in "${sources[@]}"; do
  "${clang_bin}" \
    --analyze \
    -std=c11 \
    -Wall \
    -Wextra \
    -Wpedantic \
    -Werror \
    -o /dev/null \
    "${platform_definitions[@]}" \
    -I"${runtime_include}" \
    "${script_dir}/${source}"
done

printf 'native evidence static analysis passed (%s translation units)\n' \
  "${#sources[@]}"
