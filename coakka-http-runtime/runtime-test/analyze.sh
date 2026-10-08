#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'usage: %s <installed-include-directory>\n' "$0" >&2
  exit 64
fi

include_dir="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compiler="${CLANG:-clang}"

if [[ ! -f "${include_dir}/coakka/http/http.h" ]]; then
  printf 'installed CoAkka HTTP header is missing: %s\n' "${include_dir}" >&2
  exit 66
fi
sources=(native_runtime.c native_contract.c native_concurrency.c \
         native_pressure.c core_runtime.c native_execution.c \
         native_outbound.c native_protocol.c \
         test_threads_posix.c)
warnings=(-std=c11 -Wall -Wextra -Wpedantic -Wconversion -Wshadow \
          -Wsign-conversion -Wstrict-prototypes -Werror)

if command -v "${compiler}" >/dev/null 2>&1; then
  for source in "${sources[@]}"; do
    "${compiler}" --analyze "${warnings[@]}" -I"${include_dir}" \
      -o /dev/null "${script_dir}/${source}"
  done
  printf 'static_analyzer=clang\n'
  exit 0
fi

# Release containers intentionally contain only the target GCC toolchain. Its
# analyzer keeps this gate authoritative on the matching Linux architecture
# without weakening the warning set or installing an unpinned host package.
gcc_compiler="${CC:-cc}"
command -v "${gcc_compiler}" >/dev/null 2>&1 || {
  printf 'static analyzers are unavailable: %s and %s\n' \
    "${compiler}" "${gcc_compiler}" >&2
  exit 69
}

analysis_dir="$(mktemp -d)"
trap 'rm -rf "${analysis_dir}"' EXIT
for source in "${sources[@]}"; do
  "${gcc_compiler}" -fanalyzer "${warnings[@]}" -I"${include_dir}" \
    -c "${script_dir}/${source}" \
    -o "${analysis_dir}/${source%.c}.o"
done
printf 'static_analyzer=gcc-fanalyzer\n'
