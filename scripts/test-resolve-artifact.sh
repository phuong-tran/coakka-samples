#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/resolve-artifact.sh"

fail() {
  printf '[resolve-artifact-test] %s\n' "$*" >&2
  exit 1
}

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/coakka-resolve-artifact.XXXXXX")"
trap 'rm -rf "${tmp_dir}"' EXIT

publish_root="${tmp_dir}/publish"
artifact_rel="logger/native/releases/test/coakka-logger-native-test.tar.gz"
artifact_path="${publish_root}/${artifact_rel}"
mkdir -p "${publish_root}/artifacts" "$(dirname "${artifact_path}")" "${tmp_dir}/downloads"

printf 'clean artifact\n' >"${artifact_path}"
artifact_sha="$(coakka_artifact_sha256 "${artifact_path}")"
cat >"${publish_root}/artifacts/public-artifacts.tsv" <<EOF
# Public artifact manifest v1.
# Columns: status	label	relative_path	sha256
public	logger Native package	${artifact_rel}	${artifact_sha}
EOF

resolved_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/downloads/coakka-logger-native-test.tar.gz")"
[[ "${resolved_path}" == "${artifact_path}" ]] ||
  fail "expected local artifact path, got ${resolved_path}"

printf 'tampered artifact\n' >"${artifact_path}"
if coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/downloads/coakka-logger-native-test.tar.gz" \
  >"${tmp_dir}/stdout" 2>"${tmp_dir}/stderr"; then
  fail "expected checksum mismatch to fail"
fi
grep -Fq "artifact checksum mismatch" "${tmp_dir}/stderr" ||
  fail "checksum mismatch did not explain the failure"

unlisted_rel="runtime/private/releases/test/private-runtime.tar.gz"
unlisted_path="${publish_root}/${unlisted_rel}"
mkdir -p "$(dirname "${unlisted_path}")"
printf 'private local artifact\n' >"${unlisted_path}"
resolved_private="$(coakka_resolve_artifact "${publish_root}" "${unlisted_rel}" "${tmp_dir}/downloads/private-runtime.tar.gz")"
[[ "${resolved_private}" == "${unlisted_path}" ]] ||
  fail "expected unlisted local artifact path, got ${resolved_private}"

printf '[resolve-artifact-test] ok\n'
