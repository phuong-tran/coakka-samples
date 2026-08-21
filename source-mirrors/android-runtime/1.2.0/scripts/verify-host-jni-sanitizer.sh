#!/usr/bin/env bash

set -euo pipefail

PROFILE="${1:?sanitizer profile is required}"
JAVA_EXECUTABLE="${2:?Java executable is required}"
CC_VALUE="${CC:-cc}"
TIMEOUT_SECONDS="${COAKKA_ANDROID_SANITIZER_STARTUP_TIMEOUT_SECONDS:-20}"
PROBE_OPTIMIZATION=-O1

case "${PROFILE}" in
  asan)
    SANITIZER=address
    RUNTIME_STEM=asan
    OPTIONS_NAME=ASAN_OPTIONS
    OPTIONS_VALUE=halt_on_error=1:abort_on_error=1:symbolize=1
    MARKER='ERROR: AddressSanitizer'
    PROBE_SOURCE=$'#include <stdlib.h>\n\
__attribute__((noinline)) static int read_value(int *value) { return *value; }\n\
int main(void) {\n\
  int *value = (int *)malloc(sizeof(*value));\n\
  if (value == NULL) return 2;\n\
  *value = 7;\n\
  free(value);\n\
  return read_value(value);\n\
}'
    ;;
  lsan)
    SANITIZER=address
    RUNTIME_STEM=asan
    OPTIONS_NAME=ASAN_OPTIONS
    OPTIONS_VALUE=detect_leaks=1:leak_check_at_exit=1:halt_on_error=1:exitcode=23
    JAVA_OPTIONS_VALUE=detect_leaks=0:halt_on_error=1
    MARKER='LeakSanitizer: detected memory leaks'
    PROBE_OPTIMIZATION=-O0
    PROBE_SOURCE=$'#include <stdlib.h>\n\
int main(void) { void *value = malloc(64); return value == NULL; }'
    ;;
  ubsan)
    SANITIZER=undefined
    RUNTIME_STEM=ubsan
    OPTIONS_NAME=UBSAN_OPTIONS
    OPTIONS_VALUE=halt_on_error=1:print_stacktrace=1
    MARKER='runtime error:'
    PROBE_SOURCE=$'#include <limits.h>\n\
__attribute__((noinline)) static int add_one(int value) { return value + 1; }\n\
int main(void) { volatile int value = INT_MAX; return add_one(value); }'
    ;;
  tsan)
    SANITIZER=thread
    RUNTIME_STEM=tsan
    OPTIONS_NAME=TSAN_OPTIONS
    OPTIONS_VALUE=halt_on_error=1:second_deadlock_stack=1
    MARKER='ThreadSanitizer: data race'
    PROBE_SOURCE=$'#include <pthread.h>\n\
static int shared_value;\n\
static void *write_shared(void *value) { shared_value = *(int *)value; return 0; }\n\
int main(void) {\n\
  pthread_t first; pthread_t second; int one = 1; int two = 2;\n\
  if (pthread_create(&first, 0, write_shared, &one) != 0) return 2;\n\
  if (pthread_create(&second, 0, write_shared, &two) != 0) return 2;\n\
  pthread_join(first, 0); pthread_join(second, 0);\n\
  return shared_value == 0;\n\
}'
    ;;
  *)
    printf 'usage: %s <asan|lsan|ubsan|tsan> <java-executable>\n' "$0" >&2
    exit 64
    ;;
esac
JAVA_OPTIONS_VALUE="${JAVA_OPTIONS_VALUE:-${OPTIONS_VALUE}}"

if [[ ! -x "${JAVA_EXECUTABLE}" ]]; then
  printf 'Java executable not found: %s\n' "${JAVA_EXECUTABLE}" >&2
  exit 1
fi
if ! command -v "${CC_VALUE}" >/dev/null 2>&1; then
  printf 'Host C compiler not found: %s\n' "${CC_VALUE}" >&2
  exit 1
fi
if ! command -v perl >/dev/null 2>&1; then
  printf 'Perl is required for bounded sanitizer JVM startup\n' >&2
  exit 1
fi

run_bounded() {
  perl -e '
    use POSIX qw(WNOHANG);
    my $timeout = shift @ARGV;
    my $pid = fork();
    die "fork failed: $!" unless defined $pid;
    if ($pid == 0) { exec @ARGV; exit 127; }
    my $deadline = time() + $timeout;
    while (time() < $deadline) {
      my $done = waitpid($pid, WNOHANG);
      if ($done == $pid) {
        exit(($? & 127) ? 128 + ($? & 127) : $? >> 8);
      }
      select undef, undef, undef, 0.05;
    }
    kill "KILL", $pid;
    waitpid($pid, 0);
    exit 124;
  ' "${TIMEOUT_SECONDS}" "$@"
}

case "$(uname -s)" in
  Darwin)
    RESOURCE_ROOT=$("${CC_VALUE}" -print-resource-dir)
    SANITIZER_RUNTIME="${RESOURCE_ROOT}/lib/darwin/libclang_rt.${RUNTIME_STEM}_osx_dynamic.dylib"
    PRELOAD_NAME=DYLD_INSERT_LIBRARIES
    ;;
  Linux)
    case "$(uname -m)" in
      aarch64|arm64) RUNTIME_ARCH=aarch64 ;;
      x86_64|amd64) RUNTIME_ARCH=x86_64 ;;
      *)
        printf 'Unsupported Linux sanitizer host architecture: %s\n' "$(uname -m)" >&2
        exit 69
        ;;
    esac
    if [[ "${PROFILE}" == ubsan ]]; then
      RUNTIME_STEM=ubsan_standalone
    fi
    SANITIZER_RUNTIME=$("${CC_VALUE}" \
      -print-file-name="libclang_rt.${RUNTIME_STEM}-${RUNTIME_ARCH}.so")
    PRELOAD_NAME=LD_PRELOAD
    ;;
  *)
    printf 'Host JNI sanitizers support macOS and Linux only\n' >&2
    exit 69
    ;;
esac
if [[ ! -f "${SANITIZER_RUNTIME}" ]]; then
  printf 'Sanitizer runtime not found: %s\n' "${SANITIZER_RUNTIME}" >&2
  exit 1
fi

PROBE_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/coakka-android-${PROFILE}.XXXXXX")
trap 'rm -rf "${PROBE_ROOT}"' EXIT
printf '%b\n' "${PROBE_SOURCE}" >"${PROBE_ROOT}/probe.c"
"${CC_VALUE}" -std=c17 "${PROBE_OPTIMIZATION}" -g -fno-omit-frame-pointer \
  -fno-sanitize-recover=all "-fsanitize=${SANITIZER}" \
  "${PROBE_ROOT}/probe.c" -pthread -o "${PROBE_ROOT}/probe"

set +e
run_bounded env "${OPTIONS_NAME}=${OPTIONS_VALUE}" "${PROBE_ROOT}/probe" \
  >"${PROBE_ROOT}/probe.out" 2>&1
PROBE_STATUS=$?
set -e
if [[ ${PROBE_STATUS} -eq 0 ]] || ! grep -Fq "${MARKER}" "${PROBE_ROOT}/probe.out"; then
  cat "${PROBE_ROOT}/probe.out" >&2
  printf '%s probe did not produce the expected diagnostic (status=%d)\n' \
    "${PROFILE}" "${PROBE_STATUS}" >&2
  exit 1
fi

set +e
run_bounded env "${PRELOAD_NAME}=${SANITIZER_RUNTIME}" \
  "${OPTIONS_NAME}=${JAVA_OPTIONS_VALUE}" "${JAVA_EXECUTABLE}" -version \
  >"${PROBE_ROOT}/java.out" 2>&1
JAVA_STATUS=$?
set -e
if [[ ${JAVA_STATUS} -ne 0 ]]; then
  cat "${PROBE_ROOT}/java.out" >&2
  printf '%s runtime cannot start the host JNI JVM (status=%d, timeout=%ss)\n' \
    "${PROFILE}" "${JAVA_STATUS}" "${TIMEOUT_SECONDS}" >&2
  exit 1
fi

printf '[coakka-android-host-jni] sanitizer verified profile=%s compiler=%s runtime=%s\n' \
  "${PROFILE}" "${CC_VALUE}" "${SANITIZER_RUNTIME}"
