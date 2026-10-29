#!/usr/bin/env bash
set -euo pipefail

mirror_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${mirror_root}"

if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c SOURCE-MANIFEST.sha256
else
    shasum -a 256 -c SOURCE-MANIFEST.sha256
fi
