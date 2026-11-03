#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  c|cpp) target="coakka-http-$1-host-outbound" ;;
  *) printf 'usage: bash run.sh c|cpp\n' >&2; exit 2 ;;
esac
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
prefix="${COAKKA_HTTP_HOST_PREFIX:?set COAKKA_HTTP_HOST_PREFIX to the verified host SDK}"
work="${COAKKA_HTTP_SAMPLE_WORK_ROOT:?set COAKKA_HTTP_SAMPLE_WORK_ROOT to an external build directory}"
version="${COAKKA_HTTP_HOST_SAMPLE_SDK_VERSION:-1.1.2}"
# Use distinct build roots so concurrent C and C++ invocations can configure safely.
build="${work}/${target}"
cmake -S "${root}" -B "${build}" -DCMAKE_BUILD_TYPE=Release \
  -DCOAKKA_HTTP_HOST_PREFIX="${prefix}" -DCOAKKA_HTTP_HOST_SAMPLE_SDK_VERSION="${version}"
cmake --build "${build}" --config Release --target "${target}"
ctest --test-dir "${build}" -C Release -R "^${target}$" --output-on-failure
