#!/usr/bin/env bash
# Build benchmark applications against exact public packages, never the runtime.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"
[[ "$(uname -s)" == Linux && "$(uname -m)" == aarch64 ]] || exit 1
grep -a -q 'Raspberry Pi 5' /proc/device-tree/model
grep -Eq '^VERSION_CODENAME="?trixie"?$' /etc/os-release
[[ ! -d sources && ! -d build/host-prefix ]] || {
  printf 'refusing mixed source-built and installed-package workspace\n' >&2; exit 1;
}
tool_root="${COAKKA_BENCH_TOOL_ROOT:-${root}/tools}"
gradle_cache="${COAKKA_BENCH_GRADLE_CACHE:-${root}/build/gradle-cache}"
# Tool provisioning is separate from a measurement: never update the OS or
# replace a toolchain automatically during preparation or a campaign.
[[ "$("${tool_root}/go/bin/go" version)" == 'go version go1.27.1 linux/arm64' ]]
[[ "$("${tool_root}/node/bin/node" --version)" == v22.23.3 ]]
[[ "$("${tool_root}/bun/bin/bun" --version)" == 1.4.2 ]]
for command in cmake ninja python3 javac h2load sha256sum; do command -v "${command}" >/dev/null; done
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
[[ -x "${JAVA_HOME}/bin/java" ]]
export PATH="${tool_root}/go/bin:${tool_root}/node/bin:${JAVA_HOME}/bin:${PATH}"
export PYTHONDONTWRITEBYTECODE=1
mkdir -p tools build evidence/locks
for tool in go node bun; do
  if [[ ! -e "tools/${tool}" ]]; then ln -s "${tool_root}/${tool}" "tools/${tool}"; fi
  [[ "$(readlink -f "tools/${tool}")" == "$(readlink -f "${tool_root}/${tool}")" ]]
done
resolve() {
  python3 package-tools/resolve-package.py --publish "${root}/warehouse" \
    --work "${root}/packages" "$1" linux-aarch64
}
native="$(resolve native)"
go_package="$(resolve go)"
jvm="$(resolve jvm)"
python="$(resolve python)"
javascript="$(resolve javascript)"
cmake -S src/native -B build/native-cmake -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DCoAkkaHttp_DIR="${native}/lib/cmake/CoAkkaHttp" \
  -DCMAKE_PREFIX_PATH="${native}" -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="${root}/build/native"
cmake --build build/native-cmake --parallel 2

mkdir -p build/go/app build/go-tmp
export GOCACHE="${root}/build/go-cache" GOMODCACHE="${root}/build/go-mod-cache" GOTMPDIR="${root}/build/go-tmp"
cp -a src/go/. build/go/app/
(
  cd build/go/app
  go mod edit -replace="github.com/phuong-tran/coakka-http-runtime-go=${go_package}/go"
  go mod tidy
  CGO_ENABLED=1 CGO_CFLAGS="-I${go_package}/native/include" \
    CGO_LDFLAGS="-L${go_package}/native/lib -Wl,-rpath,${go_package}/native/lib" \
    go build -trimpath -ldflags='-s -w' -o ../fixed-server .
  go build -trimpath -ldflags='-s -w' -o ../chi-server ./chi
  go build -trimpath -ldflags='-s -w' -o ../gin-server ./gin
)
cp build/go/app/go.sum evidence/locks/go.sum
[[ -x build/python/venv/bin/python ]] || python3 -m venv build/python/venv
PIP_CACHE_DIR="${root}/build/pip-cache" build/python/venv/bin/python -m pip install \
  --disable-pip-version-check -r src/python/requirements.txt
build/python/venv/bin/python -m pip freeze --all >evidence/locks/python-freeze.txt
mkdir -p build/javascript
cp src/typescript/package.json src/typescript/server.mjs build/javascript/
npm --cache "${root}/build/npm-cache" install --prefix build/javascript \
  --ignore-scripts --install-links --no-audit --no-fund "${javascript}"
cp build/javascript/package-lock.json evidence/locks/javascript-package-lock.json
GRADLE_USER_HOME="${gradle_cache}" bash gradlew --no-daemon -p src/kotlin \
  --project-cache-dir "${root}/build/kotlin-project-cache" \
  -PcoakkaHttpJar="${jvm}/lib/coakka-http-jvm-1.0.0.jar" \
  -PsampleBuildDir="${root}/build/kotlin-gradle" installDist
[[ ! -e build/kotlin ]] || [[ "$(readlink -f build/kotlin)" == "${root}/build/kotlin-gradle/install/coakka-http-rpi5-kotlin" ]]
ln -sfn "${root}/build/kotlin-gradle/install/coakka-http-rpi5-kotlin" build/kotlin
GRADLE_USER_HOME="${gradle_cache}" bash gradlew --no-daemon -p src/jvm-frameworks \
  --project-cache-dir "${root}/build/framework-project-cache" \
  -PsampleBuildDir="${root}/build/frameworks" stage

# Receipts distinguish immutable package identity from compiled consumer bytes.
python3 - "${native}" "${go_package}" "${jvm}" "${python}" "${javascript}" <<'PY'
import json
from pathlib import Path
import sys
Path('evidence/locks/package-paths.json').write_text(json.dumps(dict(zip(
    ('native', 'go', 'jvm', 'python', 'javascript'), sys.argv[1:])), indent=2) + '\n')
PY
find config scripts src package-tools gradle -type f ! -name '*.pyc' ! -name .DS_Store \
  -print0 | LC_ALL=C sort -z | xargs -0 sha256sum >evidence/locks/source-files.sha256
sha256sum evidence/locks/source-files.sha256 >evidence/locks/source-manifest.sha256
find warehouse packages build/native build/go/fixed-server build/go/chi-server build/go/gin-server build/javascript build/kotlin-gradle/install build/frameworks \
  -type f ! -name '*.pyc' ! -name .DS_Store -print0 | LC_ALL=C sort -z | \
  xargs -0 sha256sum >evidence/locks/built-artifacts.sha256
cp package-tools/package-pins.json evidence/locks/package-pins.json
printf 'installed-package consumers; no Core/connector source build\n' >evidence/locks/source-identities.txt
{ go version; java -version 2>&1; python3 --version; node --version;
  "${tool_root}/bun/bin/bun" --version; h2load --version; } >evidence/locks/tool-versions.txt
sha256sum tools/go/bin/go tools/node/bin/node tools/bun/bin/bun \
  "${JAVA_HOME}/bin/java" "${JAVA_HOME}/lib/server/libjvm.so" \
  "$(command -v python3)" "$(command -v h2load)" >evidence/locks/tool-artifacts.sha256
printf 'rpi5-prepare=pass\n'
