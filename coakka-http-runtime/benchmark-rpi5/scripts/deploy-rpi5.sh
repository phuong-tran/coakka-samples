#!/usr/bin/env bash
set -euo pipefail

benchmark_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sample_root="$(cd "${benchmark_root}/.." && pwd)"
samples_repo="$(cd "${sample_root}/.." && pwd)"
runtime_root="${COAKKA_HTTP_RUNTIME_ROOT:-${samples_repo}/../coakka-http-runtime}"
connector_root="${COAKKA_HTTP_CONNECTOR_ROOT:-${samples_repo}/../coakka-http-runtime-connector}"
commons_root="${COAKKA_COMMONS_ROOT:-${samples_repo}/../coakka-commons}"
host="${COAKKA_RPI5_HOST:-pi5}"
remote_root="${COAKKA_RPI5_ROOT:-/home/pi5/coakka-http-runtime-benchmark-20261001}"
qualified_build_dir="${COAKKA_HTTP_QUALIFIED_BUILD_DIR:-}"
runtime_ref="${COAKKA_HTTP_RUNTIME_REF:-}"
known_hosts="${COAKKA_RPI5_KNOWN_HOSTS:-}"

[[ "${host}" =~ ^[A-Za-z0-9._-]+$ ]] || {
  printf 'invalid RPi host\n' >&2
  exit 1
}
[[ "${remote_root}" =~ ^/home/pi5/coakka-http-runtime-benchmark-[A-Za-z0-9._-]+$ ]] || {
  printf 'remote root must be a dedicated Pi benchmark directory\n' >&2
  exit 1
}
ssh_options=()
rsync_shell=ssh
if [[ -n "${known_hosts}" ]]; then
  [[ "${known_hosts}" =~ ^/[A-Za-z0-9._/-]+$ && -f "${known_hosts}" ]] || {
    printf 'invalid dedicated Pi known-hosts file\n' >&2
    exit 1
  }
  ssh_options=(-o BatchMode=yes -o StrictHostKeyChecking=yes
    -o "UserKnownHostsFile=${known_hosts}")
  rsync_shell="ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=${known_hosts}"
fi
if [[ -n "${qualified_build_dir}" ]]; then
  [[ "${qualified_build_dir}" =~ ^/home/pi5/[A-Za-z0-9._/-]+/build$ &&
     "${qualified_build_dir}" != *..* &&
     "${runtime_ref}" =~ ^[0-9a-f]{40}$ ]] || {
    printf 'qualified build requires a dedicated Pi build path and exact runtime commit\n' >&2
    exit 1
  }
  git -C "${runtime_root}" cat-file -e "${runtime_ref}^{commit}"
  runtime_head="$(git -C "${runtime_root}" rev-parse "${runtime_ref}^{commit}")"
  runtime_dirty=0
else
  [[ -z "${runtime_ref}" ]] || {
    printf 'runtime ref requires a qualified build directory\n' >&2
    exit 1
  }
  runtime_head="$(git -C "${runtime_root}" rev-parse HEAD)"
  runtime_dirty="$([[ -n "$(git -C "${runtime_root}" status --porcelain)" ]] && printf 1 || printf 0)"
fi
for source_root in "${runtime_root}" "${connector_root}"; do
  [[ -f "${source_root}/CMakeLists.txt" ]] || {
    printf 'required source checkout is missing: %s\n' "${source_root}" >&2
    exit 1
  }
done
[[ -d "${commons_root}/.git" ]] || {
  printf 'required coakka-commons checkout is missing: %s\n' "${commons_root}" >&2
  exit 1
}

# The runtime lock is the authority for the exact commons tree. Exporting that
# commit avoids relying on private-network credentials or on the checkout's
# current branch while keeping the remote source manifest complete.
if [[ -n "${qualified_build_dir}" ]]; then
  lock_source="$(git -C "${runtime_root}" show "${runtime_head}:cmake/DependencyLock.cmake")"
else
  lock_source="$(<"${runtime_root}/cmake/DependencyLock.cmake")"
fi
commons_commit="$(sed -n \
  's/^set(COAKKA_HTTP_COMMONS_COMMIT "\([0-9a-f]\{40\}\)")$/\1/p' \
  <<<"${lock_source}")"
[[ "${commons_commit}" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'runtime commons lock is missing or malformed\n' >&2
  exit 1
}
git -C "${commons_root}" cat-file -e "${commons_commit}^{commit}"
commons_stage=""
cleanup() {
  if [[ -n "${commons_stage}" && -d "${commons_stage}" ]]; then
    find "${commons_stage}" -depth -delete
  fi
}
trap cleanup EXIT INT TERM
if [[ -z "${qualified_build_dir}" ]]; then
  commons_stage="$(mktemp -d "${TMPDIR:-/tmp}/coakka-rpi5-commons.XXXXXX")"
  mkdir -p "${commons_stage}/tree"
  git -C "${commons_root}" archive \
    --format=tar --output="${commons_stage}/commons.tar" "${commons_commit}"
  tar -xf "${commons_stage}/commons.tar" -C "${commons_stage}/tree"
fi

connector_head="$(git -C "${connector_root}" rev-parse HEAD)"
samples_head="$(git -C "${samples_repo}" rev-parse HEAD)"
connector_dirty="$([[ -n "$(git -C "${connector_root}" status --porcelain)" ]] && printf 1 || printf 0)"
samples_dirty="$([[ -n "$(git -C "${samples_repo}" status --porcelain)" ]] && printf 1 || printf 0)"

# The destination is validated above and is dedicated to this benchmark.
# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "${host}" "mkdir -p '${remote_root}/sources/runtime' '${remote_root}/sources/connector' '${remote_root}/sources/commons' '${remote_root}/evidence/locks'"
rsync -e "${rsync_shell}" -a --delete \
  --exclude build --exclude evidence --exclude tools --exclude sources \
  --exclude .DS_Store --exclude .gradle --exclude node_modules \
  "${benchmark_root}/" "${host}:${remote_root}/"
if [[ -n "${qualified_build_dir}" ]]; then
  # The same on-board source tree owns the already-qualified native build.
  # Reusing it avoids a second dependency graph and keeps benchmark bytes exact.
  qualified_root="${qualified_build_dir%/build}"
  # Both remote paths were constrained to dedicated Pi directories above.
  # shellcheck disable=SC2029
  ssh "${ssh_options[@]}" "${host}" \
    "test -f '${qualified_root}/source/CMakeLists.txt' && test -f '${qualified_root}/dependencies/commons/CMakeLists.txt' && rsync -a --delete '${qualified_root}/source/' '${remote_root}/sources/runtime/' && rsync -a --delete '${qualified_root}/dependencies/commons/' '${remote_root}/sources/commons/'"
else
  rsync -e "${rsync_shell}" -a --delete --delete-excluded \
    --exclude .git --exclude .DS_Store --exclude .gradle --exclude 'build*/' \
    --exclude 'cmake-build-*/' --exclude node_modules --exclude __pycache__ \
    "${runtime_root}/" "${host}:${remote_root}/sources/runtime/"
  rsync -e "${rsync_shell}" -a --delete \
    "${commons_stage}/tree/" "${host}:${remote_root}/sources/commons/"
fi
rsync -e "${rsync_shell}" -a --delete --delete-excluded \
  --exclude .git --exclude .DS_Store --exclude .gradle --exclude 'build*/' \
  --exclude 'cmake-build-*/' --exclude node_modules --exclude __pycache__ \
  "${connector_root}/" "${host}:${remote_root}/sources/connector/"
# Commit identities describe the base revisions. The remote source manifest
# generated by prepare-rpi5.sh identifies the complete dirty candidate bytes.
# All interpolated values are fixed-format Git hashes or one-byte flags.
# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "${host}" "printf '%s\n' \
  'runtime_head=${runtime_head}' 'runtime_dirty=${runtime_dirty}' \
  'connector_head=${connector_head}' 'connector_dirty=${connector_dirty}' \
  'commons_commit=${commons_commit}' \
  'samples_head=${samples_head}' 'samples_dirty=${samples_dirty}' \
  > '${remote_root}/evidence/locks/source-identities.txt'"

# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "${host}" "cd '${remote_root}' && chmod +x scripts/*.sh sources/connector/gradlew && COAKKA_HTTP_QUALIFIED_BUILD_DIR='${qualified_build_dir}' COAKKA_HTTP_QUALIFIED_SOURCE_REVISION='${runtime_head}' bash scripts/prepare-rpi5.sh"
printf 'remote benchmark prepared at %s:%s\n' "${host}" "${remote_root}"
