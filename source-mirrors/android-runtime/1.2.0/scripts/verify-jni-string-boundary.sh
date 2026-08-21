#!/usr/bin/env bash

set -euo pipefail

CONNECTOR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUPPORT_HEADER="${CONNECTOR_ROOT}/src/main/cpp/coakka_android_jni_support.h"

required_patterns=(
  '#include <array>'
  'static constexpr jsize kCapacity = 8;'
  'expected_count < 0 || expected_count > kCapacity'
  'std::array<jstring, static_cast<std::size_t>(kCapacity)> strings_{};'
  'std::array<const char *, static_cast<std::size_t>(kCapacity)> chars_{};'
)

for pattern in "${required_patterns[@]}"; do
  if ! grep -Fq "${pattern}" "${SUPPORT_HEADER}"; then
    printf 'JNI string boundary is missing fixed-storage invariant: %s\n' \
      "${pattern}" >&2
    exit 1
  fi
done

if grep -Fq '#include <vector>' "${SUPPORT_HEADER}" ||
   grep -Eq 'strings_\.(reserve|push_back|at)|chars_\.(reserve|push_back|at)' \
     "${SUPPORT_HEADER}"; then
  printf 'JNI string boundary must not allocate or use throwing bounds access\n' >&2
  exit 1
fi

printf '[coakka-android-jni] fixed UTF string boundary verified\n'
