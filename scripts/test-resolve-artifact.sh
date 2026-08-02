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

unlisted_rel="runtime/unlisted/releases/test/unlisted-runtime.tar.gz"
unlisted_path="${publish_root}/${unlisted_rel}"
mkdir -p "$(dirname "${unlisted_path}")"
printf 'unlisted local artifact\n' >"${unlisted_path}"
if coakka_resolve_artifact "${publish_root}" "${unlisted_rel}" "${tmp_dir}/downloads/unlisted-runtime.tar.gz" \
  >"${tmp_dir}/stdout" 2>"${tmp_dir}/stderr"; then
  fail "expected unlisted local artifact to fail"
fi
grep -Fq "local public artifact manifest does not list" "${tmp_dir}/stderr" ||
  fail "unlisted local artifact did not explain the failure"

compat_rel="runtime/native/releases/compat/coakka-runtime-native-compat.tar.gz"
compat_path="${publish_root}/${compat_rel}"
mkdir -p "$(dirname "${compat_path}")"
printf 'compatibility artifact\n' >"${compat_path}"
compat_sha="$(coakka_artifact_sha256 "${compat_path}")"
resolved_path="$(coakka_resolve_pinned_artifact \
  "${publish_root}" \
  "${compat_rel}" \
  "${tmp_dir}/downloads/coakka-runtime-native-compat.tar.gz" \
  "${compat_sha}")"
[[ "${resolved_path}" == "${compat_path}" ]] ||
  fail "expected local compatibility artifact path, got ${resolved_path}"

if coakka_resolve_pinned_artifact \
  "${publish_root}" \
  "${compat_rel}" \
  "${tmp_dir}/downloads/coakka-runtime-native-compat.tar.gz" \
  "0000000000000000000000000000000000000000000000000000000000000000" \
  >"${tmp_dir}/stdout" 2>"${tmp_dir}/stderr"; then
  fail "expected compatibility checksum mismatch to fail"
fi
grep -Fq "artifact checksum mismatch" "${tmp_dir}/stderr" ||
  fail "compatibility checksum mismatch did not explain the failure"

printf '[resolve-artifact-test] ok\n'
