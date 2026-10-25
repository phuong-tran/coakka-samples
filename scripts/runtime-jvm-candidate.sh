#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$#" == 1 ]] || exit 64
case "$1" in basic|deadletter|java-basic|java-deadletter) ;; *) exit 64 ;; esac
for name in COAKKA_JVM_CANDIDATE_HOME COAKKA_SAMPLE_WORK_ROOT COAKKA_SAMPLE_JAVA_HOME GRADLE_USER_HOME; do
  value="${!name:-}"
  [[ "${value}" == /* && -d "${value}" && ! -L "${value}" ]] || { printf 'Set an existing absolute %s\n' "${name}" >&2; exit 2; }
done
: "${COAKKA_SAMPLE_JVM_NATIVE_DIR:?Set an absolute native cache path with an existing parent}"
[[ "${COAKKA_SAMPLE_JVM_NATIVE_DIR}" == /* ]] || exit 2
# Gradle owns its subprocesses and validated retained caches. Do not delete
# caller work or a potentially live consumer from a shell EXIT trap.
exec bash "${repo_root}/gradlew" -p "${repo_root}/runtime/jvm" ":$1:run" \
  --offline --no-daemon --max-workers=1 --console=plain \
  --project-cache-dir "${COAKKA_SAMPLE_WORK_ROOT}/gradle-project-cache"
