#!/usr/bin/env bash
set -euo pipefail
if [[ "$#" -ne 1 ]]; then
  printf 'usage: %s <installed-include-directory>\n' "$0" >&2
  exit 64
fi
include_dir="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compiler="${CLANG:-clang}"
if [[ ! -f "${include_dir}/coakka/http/host.h" ]]; then
  printf 'installed public header is missing: %s\n' "${include_dir}" >&2
  exit 66
fi
# Analyze only owned source. No production dependency graph is rebuilt here.
sources=(host_runtime.c host_contract.c host_ownership.c host_pressure.c
         host_outbound.c host_routes.c host_stream.c host_concurrency.c
         host_execution.c test_support.c test_socket.c test_wire.c
         test_threads_posix.c fixture_wire_test.c fixture_wire_fuzz.c fixture_threads_test.c)
warnings=(-std=c11 -Wall -Wextra -Wpedantic -Wconversion -Wshadow
          -Wsign-conversion -Wstrict-prototypes -Werror)
if command -v "${compiler}" >/dev/null 2>&1; then
  for source in "${sources[@]}"; do
    "${compiler}" --analyze "${warnings[@]}" -I"${include_dir}" \
      -o /dev/null "${script_dir}/${source}"
  done
  printf 'static_analyzer=clang\n'
else
  gcc_compiler="${CC:-cc}"
  command -v "${gcc_compiler}" >/dev/null 2>&1 || exit 69
  for source in "${sources[@]}"; do
    "${gcc_compiler}" -fanalyzer "${warnings[@]}" -I"${include_dir}" \
      -c -o /dev/null "${script_dir}/${source}"
  done
  printf 'static_analyzer=gcc-fanalyzer\n'
fi
