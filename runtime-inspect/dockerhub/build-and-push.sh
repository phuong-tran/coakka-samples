#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

image="${COAKKA_RUNTIME_INSPECT_DOCKERHUB_IMAGE:-docker.io/gabrielgun1983/coakka-runtime-inspect-sample:1.3.1-4ce41f19-remote}"
context_root="$("${script_dir}/prepare-context.sh")"

coakka_require_command docker "Install Docker with buildx, then retry."
docker buildx version >/dev/null 2>&1 ||
  coakka_die "Docker buildx is required. Verify 'docker buildx version' works."

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "${image}" \
  --push \
  "${context_root}"

echo "[coakka-runtime-inspect-sample] pushed ${image}"
