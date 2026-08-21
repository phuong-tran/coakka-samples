#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
connector_root=$(cd -- "${script_dir}/.." && pwd)
# shellcheck source=load-release-identity.sh
source "${script_dir}/load-release-identity.sh"

worktree_v2_root=$(cd -- "${connector_root}/../.." && pwd)
default_repository_root=$(cd -- "${worktree_v2_root}/.." && pwd)
repository_root=$(cd -- "${COAKKA_CORE_REPO:-${default_repository_root}}" && pwd)

if ! git -C "${repository_root}" cat-file -e \
  "${COAKKA_ANDROID_CORE_COMMIT}^{commit}" 2>/dev/null; then
  printf 'Pinned Android Core commit is unavailable: %s\n' \
    "${COAKKA_ANDROID_CORE_COMMIT}" >&2
  exit 1
fi

declared_core_version=$(
  git -C "${repository_root}" show \
    "${COAKKA_ANDROID_CORE_COMMIT}:v2/CMakeLists.txt" |
    sed -n 's/^project(CoAkkaCoreV2 VERSION \([0-9][0-9.]*\) LANGUAGES.*$/\1/p'
)
if [[ "${declared_core_version}" != "${COAKKA_ANDROID_CORE_VERSION}" ]]; then
  printf 'Pinned Core version mismatch: identity=%s source=%s\n' \
    "${COAKKA_ANDROID_CORE_VERSION}" "${declared_core_version:-missing}" >&2
  exit 1
fi

require_text() {
  local file=$1
  local text=$2
  if ! grep -Fq -- "${text}" "${connector_root}/${file}"; then
    printf 'Android release identity gate is missing %s in %s\n' \
      "${text}" "${file}" >&2
    exit 1
  fi
}

require_text build.gradle.kts 'release-identity.properties'
require_text build.gradle.kts '"schema_version": 2'
require_text build.gradle.kts '"connector_source_tree_dirty"'
require_text build.gradle.kts '"core_source_git_commit"'
require_text build.gradle.kts '"core_source_tree_dirty"'
require_text maven-central.gradle.kts 'expectedAndroidCoreCommit'
require_text scripts/build-native-runtime.sh 'load-release-identity.sh'
require_text scripts/build-host-jni.sh 'load-release-identity.sh'
require_text scripts/run-device-smoke.sh 'load-release-identity.sh'
require_text src/main/cpp/CMakeLists.txt 'coakka_android_file_lane_jni.cpp'
require_text src/main/cpp/CMakeLists.txt 'coakka_android_stream_lane_jni.cpp'
require_text src/main/java/coakka/v2/android/FileLane.kt 'fun openOwned('
require_text src/main/java/coakka/v2/android/FileLane.kt 'fun prepareReceiveGrant('
require_text src/main/java/coakka/v2/android/StreamLane.kt 'fun openOwned('
require_text src/main/java/coakka/v2/android/StreamLane.kt 'fun preparePublishGrant('

printf '[coakka-android-identity] connector=%s native=%s abis=%s\n' \
  "${COAKKA_ANDROID_CONNECTOR_VERSION}" \
  "${COAKKA_ANDROID_NATIVE_PACKAGE_VERSION}" \
  "${COAKKA_ANDROID_ABIS_CSV}"
