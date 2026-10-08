#!/usr/bin/env bash
# Stage public consumers and independently pinned archives; never Core sources.
set -euo pipefail
benchmark_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sample_root="$(cd "${benchmark_root}/.." && pwd)"
samples_repo="$(cd "${sample_root}/.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${samples_repo}/../coakka-publish}"
host="${COAKKA_RPI5_HOST:-pi5}"
remote_root="${COAKKA_RPI5_ROOT:?Set an absolute dedicated Pi benchmark workspace}"
tool_root="${COAKKA_BENCH_TOOL_ROOT:-${remote_root}/tools}"
gradle_cache="${COAKKA_BENCH_GRADLE_CACHE:-${remote_root}/build/gradle-cache}"
[[ "${host}" =~ ^[A-Za-z0-9._-]+$ && "${host}" != -* ]] || exit 1
[[ "${remote_root}" =~ ^/home/[A-Za-z0-9_-]+/coakka-http-runtime-benchmark-[A-Za-z0-9._-]+$ &&
   "${remote_root}" != *..* ]] || exit 1
for path in "${tool_root}" "${gradle_cache}"; do
  [[ "${path}" =~ ^/home/[A-Za-z0-9_-]+/[A-Za-z0-9._/-]+$ && "${path}" != *..* ]] || exit 1
done

# Verify all five archive digests locally before transferring anything. The Pi
# resolver independently verifies them again, including every extracted byte.
archive_paths="$(python3 - "${sample_root}/scripts/package-pins.json" "${publish_root}" <<'PY'
import hashlib
import json
from pathlib import Path
import re
import sys
pins = json.loads(Path(sys.argv[1]).read_text())
for lane in ('native', 'go', 'jvm', 'python', 'javascript'):
    pin = pins[lane]
    # A same-day corrected candidate gets a bounded revision suffix instead
    # of replacing prior evidence. Keep it a single safe path component.
    if not re.fullmatch(r'\d{4}-\d{2}-\d{2}(?:-r[1-9][0-9]{0,2})?', pin['date']) or pin['extension'] not in ('tar.gz', 'tgz'):
        raise ValueError('invalid package pin path')
    name = f"coakka-http-{lane}-1.0.0-candidate-linux-aarch64.{pin['extension']}"
    relative = Path('coakka-http-runtime') / lane / 'candidates' / pin['date'] / name
    archive = Path(sys.argv[2]) / relative
    if archive.is_symlink() or archive.stat().st_size > 64 * 1024 * 1024:
        raise ValueError('invalid archive')
    if hashlib.sha256(archive.read_bytes()).hexdigest() != pin['sha256']['linux-aarch64']:
        raise ValueError('archive identity mismatch: ' + str(relative))
    print(relative)
PY
)"
ssh_options=(-o BatchMode=yes -o StrictHostKeyChecking=yes)
rsync_shell='ssh -o BatchMode=yes -o StrictHostKeyChecking=yes'
# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "${host}" \
  "test ! -d '${remote_root}/sources' && test ! -d '${remote_root}/build/host-prefix' && mkdir -p '${remote_root}/package-tools' '${remote_root}/warehouse' '${remote_root}/gradle/wrapper'"
# Never delete an existing campaign, evidence, or another checkout during deploy.
rsync -a -e "${rsync_shell}" --exclude build --exclude evidence --exclude tools \
  --exclude .gradle --exclude node_modules --exclude __pycache__ --exclude .DS_Store \
  "${benchmark_root}/" "${host}:${remote_root}/"
rsync -a -e "${rsync_shell}" "${sample_root}/scripts/resolve-package.py" \
  "${sample_root}/scripts/package-pins.json" "${host}:${remote_root}/package-tools/"
rsync -a -e "${rsync_shell}" "${samples_repo}/gradlew" "${host}:${remote_root}/"
rsync -a -e "${rsync_shell}" "${samples_repo}/gradle/wrapper/" "${host}:${remote_root}/gradle/wrapper/"
while IFS= read -r relative; do
  (cd "${publish_root}" && rsync -aR -e "${rsync_shell}" "${relative}" "${host}:${remote_root}/warehouse/")
done <<<"${archive_paths}"
# Only allow validated, dedicated Pi paths to cross the remote shell boundary.
# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "${host}" \
  "cd '${remote_root}' && COAKKA_BENCH_TOOL_ROOT='${tool_root}' COAKKA_BENCH_GRADLE_CACHE='${gradle_cache}' bash scripts/prepare-rpi5.sh"
