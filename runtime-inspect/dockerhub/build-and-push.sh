#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

context_root="$("${script_dir}/prepare-context.sh")"
release_version="$(sed -n 's/^version=//p' "${context_root}/IMAGE-RELEASE.txt")"
samples_source_commit="$(sed -n 's/^samples_source_commit=//p' "${context_root}/IMAGE-RELEASE.txt")"
samples_source_dirty="$(sed -n 's/^samples_source_dirty=//p' "${context_root}/IMAGE-RELEASE.txt")"
runtime_generation="$(sed -n 's/^runtime_generation=//p' "${context_root}/IMAGE-RELEASE.txt")"
runtime_commit="${runtime_generation#*+}"
immutable_tag="${release_version}-${runtime_commit:0:8}-remote"
image="${COAKKA_RUNTIME_INSPECT_DOCKERHUB_IMAGE:-docker.io/gabrielgun1983/coakka-runtime-inspect-sample:${immutable_tag}}"
[[ "${release_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || \
  coakka_die "Docker Hub Inspect context has an invalid release version."
[[ "${samples_source_commit}" =~ ^[0-9a-f]{40}$ ]] || \
  coakka_die "Docker Hub Inspect context has an invalid Samples source commit."
[[ "${samples_source_dirty}" == "false" ]] || \
  coakka_die "Docker Hub Inspect publication requires a clean Samples source commit."
[[ "${runtime_generation}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\+[0-9a-f]{40}$ ]] || \
  coakka_die "Docker Hub Inspect context has an invalid Runtime generation."
[[ "${runtime_generation%%+*}" == "${release_version}" ]] || \
  coakka_die "Docker Hub Inspect context version and Runtime generation differ."
[[ "${image##*:}" == "${immutable_tag}" && "${image}" != *@* ]] || \
  coakka_die "Docker Hub Inspect image must use immutable tag ${immutable_tag}."

coakka_require_command docker "Install Docker with buildx, then retry."
docker buildx version >/dev/null 2>&1 ||
  coakka_die "Docker buildx is required. Verify 'docker buildx version' works."

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --build-arg "RUNTIME_GENERATION=${runtime_generation}" \
  --build-arg "SAMPLES_SOURCE_COMMIT=${samples_source_commit}" \
  --tag "${image}" \
  --push \
  "${context_root}"

manifest_report="$(docker buildx imagetools inspect "${image}")"
image_digest="$(
  awk '$1 == "Digest:" { print $2; exit }' <<<"${manifest_report}"
)"
[[ "${image_digest}" =~ ^sha256:[0-9a-f]{64}$ ]] || \
  coakka_die "Docker Hub Inspect image returned an invalid manifest digest."
manifest_report="$(docker buildx imagetools inspect "${image}@${image_digest}")"
grep -Eq 'Platform:[[:space:]]+linux/amd64' <<<"${manifest_report}" || \
  coakka_die "Docker Hub Inspect image has no linux/amd64 manifest."
grep -Eq 'Platform:[[:space:]]+linux/arm64' <<<"${manifest_report}" || \
  coakka_die "Docker Hub Inspect image has no linux/arm64 manifest."
for platform in linux/amd64 linux/arm64; do
  docker run --rm --platform "${platform}" \
    --entrypoint sh "${image}@${image_digest}" -lc \
    "grep -Fx 'samples_source_commit=${samples_source_commit}' /opt/coakka-image/RELEASE.txt >/dev/null \
      && grep -Fx 'runtime_generation=${runtime_generation}' /opt/coakka-image/RELEASE.txt >/dev/null \
      && test -f /opt/coakka-runtime-inspect/LICENSE \
      && test -f /opt/coakka-runtime-inspect/NATIVE-LICENSE.md \
      && test -f /opt/coakka-runtime-inspect/PACKAGE-LICENSE.md \
      && test -f /opt/coakka-runtime-inspect/NOTICE"
  docker run --rm --platform "${platform}" \
    "${image}@${image_digest}" smoke
done
cat >"${context_root}/PUBLISHED-IMAGE.txt" <<EOF
image=${image}
digest=${image_digest}
version=${release_version}
runtime_generation=${runtime_generation}
samples_source_commit=${samples_source_commit}
verified_platforms=linux/amd64,linux/arm64
live_gate=inspect-smoke
EOF
echo "[coakka-runtime-inspect-sample] pushed ${image}@${image_digest}"
