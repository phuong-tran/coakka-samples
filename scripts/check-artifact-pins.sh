#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/sample-metadata.sh"

expected_logger_native="0.1.0+ba2a66d98eb5"

required_rows=(
  "logger JVM jar|logger/jvm/releases/${expected_logger_native}/coakka-jvm-native-logger-0.1.0.jar"
  "logger Python wheel|logger/python/releases/${expected_logger_native}/coakka_logger-0.1.0-py3-none-any.whl"
  "logger Node package|logger/node/releases/${expected_logger_native}/coakka-logger-node-0.1.0.tgz"
  "logger Go package|logger/go/releases/${expected_logger_native}/coakka-logger-go-0.1.0.tar.gz"
  "logger Native package|logger/native/releases/${expected_logger_native}/coakka-logger-native-0.1.0.tar.gz"
)

stale_patterns=(
  "0.1.0+5e25""dda67597"
  "0.1.0-g5e25""dda67597"
  "0.1.0-g5e25""dda67597-a21e1ad"
  "0.1.0+2eae""d9a043ca"
  "0.1.0-g0cb""644340467-e4a8""ed3"
  "0.1.0+0cb""644340467"
  "0.1.0-g0cb""644340467-cfb8ee4"
  "runtime/jvm/releases/0.1.0+0cb""644340467/coakka-jvm-native-runtime-v2-0.1.0-g0cb644""340467.jar"
)

fail() {
  printf '[fail] %s\n' "$*" >&2
  exit 1
}

artifact_row_exists() {
  local needle="$1"
  local row
  for row in "${COAKKA_ARTIFACT_ROWS[@]}"; do
    if [[ "${row}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

tracked_source_files() {
  git -C "${repo_root}" ls-files \
    ':!:*.lock' \
    ':!:.gradle/**' \
    ':!:build/**' \
    ':!:**/build/**' \
    ':!:.idea/**'
}

check_required_rows() {
  local row
  for row in "${required_rows[@]}"; do
    artifact_row_exists "${row}" || fail "sample-metadata.sh is missing pinned artifact row: ${row}"
  done
}

check_stale_patterns() {
  local pattern matches
  for pattern in "${stale_patterns[@]}"; do
    matches="$(
      tracked_source_files |
        xargs grep -n -F -- "${pattern}" 2>/dev/null || true
    )"
    if [[ -n "${matches}" ]]; then
      printf '%s\n' "${matches}" >&2
      fail "stale artifact pin found: ${pattern}"
    fi
  done
}

check_local_artifacts() {
  local publish_root row label relative_path
  publish_root="$(coakka_default_publish_root "${repo_root}")"
  if [[ ! -d "${publish_root}" ]]; then
    printf '[skip] local coakka-publish checkout not found at %s\n' "${publish_root}"
    return 0
  fi

  for row in "${required_rows[@]}"; do
    IFS='|' read -r label relative_path <<<"${row}"
    [[ -f "${publish_root}/${relative_path}" ]] ||
      fail "local coakka-publish is missing ${label}: ${relative_path}"
  done
}

check_public_artifacts() {
  local raw_base row label relative_path
  if [[ "${COAKKA_PIN_CHECK_NETWORK:-0}" != "1" ]]; then
    printf '[skip] public artifact URL checks disabled; set COAKKA_PIN_CHECK_NETWORK=1 to enable\n'
    return 0
  fi
  command -v curl >/dev/null 2>&1 || fail "curl is required for COAKKA_PIN_CHECK_NETWORK=1"

  raw_base="${COAKKA_PUBLISH_RAW_BASE:-${COAKKA_PUBLISH_RAW_BASE_DEFAULT}}"
  for row in "${required_rows[@]}"; do
    IFS='|' read -r label relative_path <<<"${row}"
    curl -fsI --max-time 10 "${raw_base%/}/${relative_path}" >/dev/null ||
      fail "public artifact URL missing ${label}: ${relative_path}"
  done
}

check_required_rows
check_stale_patterns
check_local_artifacts
check_public_artifacts

printf '[ok] artifact pins match the current public publish surface\n'
