#!/usr/bin/env bash

# This file is sourced by release/build scripts. Keep it side-effect free.
coakka_android_identity_script_dir=$(
  cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd
)
coakka_android_identity_file=${COAKKA_ANDROID_RELEASE_IDENTITY_FILE:-"${coakka_android_identity_script_dir}/../release-identity.properties"}

if [[ ! -f "${coakka_android_identity_file}" ]]; then
  printf 'Android release identity not found: %s\n' \
    "${coakka_android_identity_file}" >&2
  return 1
fi

coakka_android_read_identity_property() {
  local key=$1
  local value
  value=$(
    awk -F= -v expected_key="${key}" \
      '$1 == expected_key { sub(/^[^=]*=/, ""); print }' \
      "${coakka_android_identity_file}"
  )
  if [[ -z "${value}" || "${value}" == *$'\n'* ]]; then
    printf 'Android release identity requires exactly one non-empty %s row.\n' \
      "${key}" >&2
    return 1
  fi
  printf '%s' "${value}"
}

COAKKA_ANDROID_CONNECTOR_VERSION=$(
  coakka_android_read_identity_property connector.version
)
COAKKA_ANDROID_CORE_VERSION=$(
  coakka_android_read_identity_property core.version
)
COAKKA_ANDROID_CORE_COMMIT=$(
  coakka_android_read_identity_property core.commit
)
COAKKA_ANDROID_ABIS_CSV=$(
  coakka_android_read_identity_property android.abis
)

if [[ ! "${COAKKA_ANDROID_CONNECTOR_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
   [[ ! "${COAKKA_ANDROID_CORE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
   [[ ! "${COAKKA_ANDROID_CORE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] ||
   [[ "${COAKKA_ANDROID_ABIS_CSV}" != \
      "arm64-v8a,armeabi-v7a,x86,x86_64" ]]; then
  printf 'Android release identity is malformed or has an unsupported ABI set.\n' >&2
  return 1
fi

COAKKA_ANDROID_NATIVE_PACKAGE_VERSION="${COAKKA_ANDROID_CORE_VERSION}+${COAKKA_ANDROID_CORE_COMMIT}"
COAKKA_ANDROID_ABIS_DEFAULT=${COAKKA_ANDROID_ABIS_CSV//,/ }

export COAKKA_ANDROID_CONNECTOR_VERSION
export COAKKA_ANDROID_CORE_VERSION
export COAKKA_ANDROID_CORE_COMMIT
export COAKKA_ANDROID_NATIVE_PACKAGE_VERSION
export COAKKA_ANDROID_ABIS_CSV
export COAKKA_ANDROID_ABIS_DEFAULT

unset coakka_android_identity_script_dir
unset coakka_android_identity_file
