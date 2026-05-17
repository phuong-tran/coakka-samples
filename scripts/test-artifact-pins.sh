#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/sample-metadata.sh"

tmp_root="$(mktemp -d "${TMPDIR:-/tmp}/coakka-artifact-pins.XXXXXX")"
test_output="${tmp_root}/last-command.out"

cleanup() {
  rm -rf "${tmp_root}"
}
trap cleanup EXIT

fail() {
  printf '[artifact-pins-test] %s\n' "$*" >&2
  exit 1
}

expect_success() {
  local label="$1"
  shift
  if ! "$@" >"${test_output}" 2>&1; then
    echo "[artifact-pins-test] expected success: ${label}" >&2
    cat "${test_output}" >&2
    exit 1
  fi
}

expect_failure() {
  local label="$1"
  shift
  if "$@" >"${test_output}" 2>&1; then
    echo "[artifact-pins-test] expected failure: ${label}" >&2
    cat "${test_output}" >&2
    exit 1
  fi
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

make_publish_root() {
  local name="$1"
  local publish_root="${tmp_root}/${name}"
  local row label relative_path artifact_path artifact_sha
  rm -rf "${publish_root}"
  mkdir -p "${publish_root}/artifacts"

  cat >"${publish_root}/artifacts/public-artifacts.tsv" <<'EOF'
# Public artifact manifest v1.
# Columns: status	label	relative_path	sha256
EOF

  for row in "${COAKKA_ARTIFACT_ROWS[@]}"; do
    IFS='|' read -r label relative_path <<<"${row}"
    artifact_path="${publish_root}/${relative_path}"
    mkdir -p "$(dirname "${artifact_path}")"
    printf 'public artifact fixture: %s\n' "${relative_path}" >"${artifact_path}"
    artifact_sha="$(sha256_file "${artifact_path}")"
    printf 'public\t%s\t%s\t%s\n' "${label}" "${relative_path}" "${artifact_sha}" \
      >>"${publish_root}/artifacts/public-artifacts.tsv"
  done

  printf '%s\n' "${publish_root}"
}

run_pin_check() {
  local publish_root="$1"
  COAKKA_PUBLISH_ROOT="${publish_root}" bash "${repo_root}/scripts/check-artifact-pins.sh"
}

good_publish_root="$(make_publish_root good)"
expect_success "clean publish manifest" run_pin_check "${good_publish_root}"

bad_path_root="$(make_publish_root bad-path)"
cat >>"${bad_path_root}/artifacts/public-artifacts.tsv" <<'EOF'
public	outside fixture	../outside.tar.gz	0000000000000000000000000000000000000000000000000000000000000000
EOF
expect_failure "unsafe manifest path" run_pin_check "${bad_path_root}"
grep -Fq "unsafe path" "${test_output}" ||
  fail "missing unsafe path manifest error"

duplicate_path_root="$(make_publish_root duplicate-path)"
runtime_artifact="runtime/native/releases/0.1.0+e2dc43a/coakka-runtime-native-v2-0.1.0.tar.gz"
runtime_sha="$(sha256_file "${duplicate_path_root}/${runtime_artifact}")"
cat >>"${duplicate_path_root}/artifacts/public-artifacts.tsv" <<EOF
public	runtime Native package duplicate	${runtime_artifact}	${runtime_sha}
EOF
expect_failure "duplicate manifest path" run_pin_check "${duplicate_path_root}"
grep -Fq "duplicate artifact path" "${test_output}" ||
  fail "missing duplicate path manifest error"

duplicate_label_root="$(make_publish_root duplicate-label)"
logger_artifact="logger/native/releases/0.1.0+ba2a66d98eb5/coakka-logger-native-0.1.0-copy.tar.gz"
logger_path="${duplicate_label_root}/${logger_artifact}"
mkdir -p "$(dirname "${logger_path}")"
printf 'duplicate label fixture\n' >"${logger_path}"
logger_sha="$(sha256_file "${logger_path}")"
cat >>"${duplicate_label_root}/artifacts/public-artifacts.tsv" <<EOF
public	logger Native package	${logger_artifact}	${logger_sha}
EOF
expect_failure "duplicate manifest label" run_pin_check "${duplicate_label_root}"
grep -Fq "duplicate artifact label" "${test_output}" ||
  fail "missing duplicate label manifest error"

echo "[artifact-pins-test] ok"
