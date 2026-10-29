#!/usr/bin/env bash

set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  printf 'The ad-hoc host JNI Java launcher is macOS-only\n' >&2
  exit 69
fi

DESTINATION_ROOT="${1:?destination root is required}"
JAVA_HOME_VALUE="${2:-${JAVA_HOME:-}}"
if [[ -z "${JAVA_HOME_VALUE}" ]]; then
  JAVA_HOME_VALUE=$(/usr/libexec/java_home)
fi
if [[ ! -x "${JAVA_HOME_VALUE}/bin/java" ]]; then
  printf 'Java launcher not executable: %s/bin/java\n' "${JAVA_HOME_VALUE}" >&2
  exit 1
fi

mkdir -p "${DESTINATION_ROOT}/bin"
cp "${JAVA_HOME_VALUE}/bin/java" "${DESTINATION_ROOT}/bin/java"
rm -f "${DESTINATION_ROOT}/lib" "${DESTINATION_ROOT}/conf"
ln -s "${JAVA_HOME_VALUE}/lib" "${DESTINATION_ROOT}/lib"
ln -s "${JAVA_HOME_VALUE}/conf" "${DESTINATION_ROOT}/conf"
cp "${JAVA_HOME_VALUE}/release" "${DESTINATION_ROOT}/release"

# A vendor-signed hardened launcher rejects compiler-rt sanitizer injection.
# This generated launcher keeps the same JDK modules but deliberately drops the
# hardened-runtime signature for local diagnostics only.
codesign --force --sign - "${DESTINATION_ROOT}/bin/java"
printf '[coakka-android-host-jni] prepared diagnostic Java launcher=%s\n' \
  "${DESTINATION_ROOT}/bin/java"
