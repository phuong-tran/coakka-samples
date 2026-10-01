#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

[[ "$(uname -m)" == "aarch64" ]] || {
  printf 'benchmark preparation requires Linux AArch64\n' >&2
  exit 1
}
grep -a -q 'Raspberry Pi 5' /proc/device-tree/model || {
  printf 'benchmark preparation requires a physical Raspberry Pi 5\n' >&2
  exit 1
}
os_codename="$(awk -F= '$1 == "VERSION_CODENAME" {
  gsub(/^"|"$/, "", $2); print $2; exit
}' /etc/os-release)"
[[ "${os_codename}" == "trixie" ]] || {
  printf 'benchmark preparation requires a clean Raspberry Pi OS Trixie install; found %s\n' \
    "${os_codename:-unknown}" >&2
  exit 1
}

for file in \
  sources/runtime/CMakeLists.txt \
  sources/commons/CMakeLists.txt \
  sources/connector/CMakeLists.txt \
  sources/connector/gradlew \
  sources/connector/connectors/go/coakkahttp/go.mod \
  sources/connector/connectors/jvm/runtime/build.gradle.kts \
  sources/connector/connectors/javascript/package/package.json \
  src/kotlin/build.gradle.kts; do
  [[ -f "${file}" ]] || {
    printf 'missing source input: %s\n' "${file}" >&2
    exit 1
  }
done

sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  build-essential ca-certificates cmake curl git libcpp-httplib-dev \
  libmicrohttpd-dev nghttp2-client ninja-build \
  openjdk-21-jdk-headless perl pkg-config python3 python3-venv unzip

for command in cmake curl java javac python3 sha256sum tar; do
  command -v "${command}" >/dev/null || {
    printf 'required benchmark tool is unavailable: %s\n' "${command}" >&2
    exit 1
  }
done

mkdir -p build tools evidence/locks

# Trixie's Node 20 is below the connector's declared Node >=22 floor. Pin one
# official ARM64 toolchain for both builds and measured Node framework lanes.
node_version=22.23.3
node_archive="tools/node-v${node_version}-linux-arm64.tar.xz"
node_sha256=a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f
node_binary="tools/node/bin/node"
if [[ -x "${node_binary}" ]] &&
  [[ "$("${node_binary}" --version)" != "v${node_version}" ]]; then
  printf 'existing benchmark Node has an unexpected version\n' >&2
  exit 1
fi
if [[ ! -x "${node_binary}" ]]; then
  curl --fail --location --retry 3 \
    "https://nodejs.org/download/release/v${node_version}/node-v${node_version}-linux-arm64.tar.xz" \
    -o "${node_archive}"
  printf '%s  %s\n' "${node_sha256}" "${node_archive}" |
    sha256sum --check --strict
  mkdir -p tools/node
  tar -xJf "${node_archive}" -C tools/node --strip-components=1
fi
rm -f "${node_archive}"
[[ "$("${node_binary}" --version)" == "v${node_version}" &&
   -x tools/node/bin/npm ]] || {
  printf 'benchmark Node toolchain check failed\n' >&2
  exit 1
}
export PATH="${root}/tools/node/bin:${PATH}"

javac_path="$(readlink -f "$(command -v javac)")"
export JAVA_HOME
JAVA_HOME="$(dirname "$(dirname "${javac_path}")")"

reset_build_dir() {
  local directory="$1"
  case "${directory}" in
    "${root}/build/"*) ;;
    *)
      printf 'refusing to reset non-build directory: %s\n' "${directory}" >&2
      exit 1
      ;;
  esac
  if [[ -d "${directory}" ]]; then
    find "${directory}" -depth -delete
  fi
}

# This manifest, rather than a package version, identifies every benchmark,
# runtime and connector source byte used for this candidate.
find config scripts src sources -type f \
  ! -path '*/.gradle/*' ! -path '*/build/*' ! -name .DS_Store \
  -print0 | LC_ALL=C sort -z | xargs -0 sha256sum \
  >evidence/locks/source-files.sha256
sha256sum evidence/locks/source-files.sha256 \
  >evidence/locks/source-manifest.sha256

go_version=1.27.1
go_archive="tools/go${go_version}.linux-arm64.tar.gz"
go_sha256=3450b45a3f9ee8568792736a5c5e70a1f2e9b36c35a8f74958c03e51d7d92bec
if [[ -x tools/go/bin/go ]] &&
  [[ "$(tools/go/bin/go version)" != "go version go${go_version} linux/arm64" ]]; then
  find tools/go -depth -delete
fi
if [[ ! -x tools/go/bin/go ]]; then
  if [[ -d tools/go ]]; then
    find tools/go -depth -delete
  fi
  curl --fail --location --retry 3 \
    "https://go.dev/dl/go${go_version}.linux-arm64.tar.gz" -o "${go_archive}"
  printf '%s  %s\n' "${go_sha256}" "${go_archive}" | sha256sum --check --strict
  tar -xzf "${go_archive}" -C tools
fi
rm -f "${go_archive}"
export PATH="${root}/tools/go/bin:${PATH}"

# Use a reviewed, digest-checked ARM64 binary rather than depending on a
# pre-existing workstation or board installation for the Bun lanes.
bun_version=1.4.2
bun_archive="tools/bun-v${bun_version}-linux-aarch64.zip"
bun_sha256=54328bbc2d9c8e0c9f892c544d66c57a83b84139e34909e5ee81758f1ac8fda7
bun_binary="tools/bun/bin/bun"
if [[ -x "${bun_binary}" ]] &&
  [[ "$("${bun_binary}" --version)" != "${bun_version}" ]]; then
  printf 'existing benchmark Bun has an unexpected version\n' >&2
  exit 1
fi
if [[ ! -x "${bun_binary}" ]]; then
  curl --fail --location --retry 3 \
    "https://github.com/oven-sh/bun/releases/download/bun-v${bun_version}/bun-linux-aarch64.zip" \
    -o "${bun_archive}"
  printf '%s  %s\n' "${bun_sha256}" "${bun_archive}" |
    sha256sum --check --strict
  mkdir -p tools/bun/bin
  unzip -p "${bun_archive}" bun-linux-aarch64/bun >"${bun_binary}"
  chmod 0755 "${bun_binary}"
fi
rm -f "${bun_archive}"
[[ "$("${bun_binary}" --version)" == "${bun_version}" ]] || {
  printf 'benchmark Bun version check failed\n' >&2
  exit 1
}

reset_build_dir "${root}/build/host-prefix"
qualified_build_dir="${COAKKA_HTTP_QUALIFIED_BUILD_DIR:-}"
if [[ -n "${qualified_build_dir}" ]]; then
  qualified_revision="${COAKKA_HTTP_QUALIFIED_SOURCE_REVISION:-}"
  qualified_binary_sha256="${COAKKA_HTTP_QUALIFIED_BINARY_SHA256:-}"
  [[ "${qualified_build_dir}" == /* &&
     -f "${qualified_build_dir}/CMakeCache.txt" &&
     "${qualified_revision}" =~ ^[0-9a-f]{40}$ &&
     "${qualified_binary_sha256}" =~ ^[0-9a-f]{64}$ ]] || {
    printf 'qualified native build identity is invalid\n' >&2
    exit 1
  }
  printf '%s  %s\n' "${qualified_binary_sha256}" \
    "${qualified_build_dir}/libcoakka_http_host.so.1.0.0" |
    sha256sum --check --strict
  grep -Fxq "COAKKA_HTTP_SOURCE_REVISION:STRING=${qualified_revision}" \
    "${qualified_build_dir}/CMakeCache.txt"
  grep -Fxq 'CMAKE_BUILD_TYPE:STRING=Release' \
    "${qualified_build_dir}/CMakeCache.txt"
  grep -Fxq "runtime_head=${qualified_revision}" \
    evidence/locks/source-identities.txt
  grep -Fxq 'runtime_dirty=0' evidence/locks/source-identities.txt
  qualified_source="$(sed -n \
    's/^CMAKE_HOME_DIRECTORY:INTERNAL=//p' \
    "${qualified_build_dir}/CMakeCache.txt")"
  [[ "${qualified_source}" == /* &&
     -f "${qualified_source}/CMakeLists.txt" ]] || {
    printf 'qualified native source tree is unavailable\n' >&2
    exit 1
  }
  diff -qr sources/runtime "${qualified_source}" >/dev/null || {
    printf 'benchmark runtime source differs from qualified native source\n' >&2
    exit 1
  }
  cmake --build "${qualified_build_dir}" \
    --target coakka_http_host --parallel 3
  cmake --install "${qualified_build_dir}" \
    --prefix "${root}/build/host-prefix" --component coakka_http_host
else
  reset_build_dir "${root}/build/runtime"
  cmake -S sources/runtime -B build/runtime -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="${root}/build/host-prefix" \
    -DFETCHCONTENT_SOURCE_DIR_COAKKA_HTTP_COMMONS="${root}/sources/commons" \
    -DBUILD_TESTING=OFF \
    -DCOAKKA_HTTP_BUILD_DEPENDENCY_PROBE=OFF \
    -DCOAKKA_HTTP_ENABLE_OPENSSL_PROVIDER=ON \
    -DCOAKKA_HTTP_ENABLE_ZLIB_PROVIDER=ON \
    -DCOAKKA_HTTP_ENABLE_HTTP2_FOUNDATION=ON \
    -DCOAKKA_HTTP_ENABLE_HTTP3_DEPENDENCY_FOUNDATION=ON \
    -DCOAKKA_HTTP_ENABLE_PINNED_CURL_CLIENT_FOUNDATION=ON \
    -DCOAKKA_HTTP_BUILD_CURL_HTTP1_PROVIDER=ON
  cmake --build build/runtime --target coakka_http_host --parallel 3
  cmake --install build/runtime --component coakka_http_host
fi
host_library="${root}/build/host-prefix/lib/libcoakka_http_host.so.1.0.0"
[[ -f "${host_library}" ]] || {
  printf 'host library was not installed: %s\n' "${host_library}" >&2
  exit 1
}
if [[ -n "${qualified_build_dir}" ]]; then
  printf '%s  %s\n' "${qualified_binary_sha256}" "${host_library}" |
    sha256sum --check --strict
fi

reset_build_dir "${root}/build/go"
mkdir -p build/go/app
cp src/go/go.mod src/go/main.go build/go/app/
(
  cd build/go/app
  go mod edit \
    -replace="github.com/phuong-tran/coakka-http-runtime-go=${root}/sources/connector/connectors/go/coakkahttp"
  go mod tidy
  CGO_ENABLED=1 \
    CGO_CFLAGS="-I${root}/build/host-prefix/include" \
    CGO_LDFLAGS="-L${root}/build/host-prefix/lib -Wl,-rpath,${root}/build/host-prefix/lib" \
    go build -trimpath -ldflags='-s -w' -o ../fixed-server .
)
cp build/go/app/go.sum evidence/locks/go.sum

reset_build_dir "${root}/build/python"
python3 -m venv build/python/venv
build/python/venv/bin/python -m pip install --disable-pip-version-check --upgrade pip
build/python/venv/bin/python -m pip install --disable-pip-version-check \
  -r src/python/requirements.txt
build/python/venv/bin/python -m pip freeze --all >evidence/locks/python-freeze.txt

reset_build_dir "${root}/build/javascript-connector"
cmake -S sources/connector -B build/javascript-connector -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_TESTING=OFF \
  -DCMAKE_PREFIX_PATH="${root}/build/host-prefix"
cmake --build build/javascript-connector \
  --target coakka_http_javascript_addon --parallel 3
javascript_addon="$(find build/javascript-connector -type f \
  -name coakka_http_javascript.node -print -quit)"
[[ -n "${javascript_addon}" ]] || {
  printf 'JavaScript connector addon was not built\n' >&2
  exit 1
}
javascript_addon="${root}/${javascript_addon}"

reset_build_dir "${root}/build/javascript"
mkdir -p build/javascript
cp src/typescript/package.json src/typescript/server.mjs build/javascript/
npm install --prefix build/javascript --install-links --no-audit --no-fund \
  "${root}/sources/connector/connectors/javascript/package"
cp build/javascript/package-lock.json evidence/locks/javascript-package-lock.json

reset_build_dir "${root}/build/jvm-connector"
mkdir -p build/jvm-connector-cache
# Both Gradle projects use the same retained dependency cache; only their
# compiled outputs are reset when preparation is repeated.
gradle_cache="${root}/build/jvm-connector-cache"
GRADLE_USER_HOME="${gradle_cache}" \
  sources/connector/gradlew --no-daemon -p sources/connector \
  -PcoakkaHttpPrefix="${root}/build/host-prefix" \
  -PcoakkaHttpBuildDir="${root}/build/jvm-connector" \
  :connectors:jvm:runtime:jar :connectors:jvm:runtime:compileNativeBridge
jvm_jar="$(find build/jvm-connector/libs -maxdepth 1 -type f \
  -name 'coakka-http-jvm-*.jar' -print -quit)"
jvm_bridge="$(find build/jvm-connector/native -maxdepth 1 -type f \
  -name 'libcoakka_http_jvm.so' -print -quit)"
[[ -n "${jvm_jar}" && -n "${jvm_bridge}" ]] || {
  printf 'JVM connector outputs are incomplete\n' >&2
  exit 1
}
jvm_jar="${root}/${jvm_jar}"
jvm_bridge="${root}/${jvm_bridge}"

reset_build_dir "${root}/build/kotlin"
reset_build_dir "${root}/build/kotlin-gradle"
GRADLE_USER_HOME="${gradle_cache}" \
  sources/connector/gradlew --no-daemon -p src/kotlin \
  -PcoakkaHttpJar="${jvm_jar}" \
  -PsampleBuildDir="${root}/build/kotlin-gradle" \
  installDist
cp -a build/kotlin-gradle/install/coakka-http-rpi5-kotlin build/kotlin
GRADLE_USER_HOME="${gradle_cache}" \
  sources/connector/gradlew --no-daemon -p src/kotlin \
  -PcoakkaHttpJar="${jvm_jar}" \
  -PsampleBuildDir="${root}/build/kotlin-gradle" \
  dependencies >evidence/locks/kotlin-dependencies.txt

reset_build_dir "${root}/build/native"
reset_build_dir "${root}/build/native-cmake"
cmake -S src/native -B build/native-cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${root}/build/host-prefix" \
  -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="${root}/build/native"
cmake --build build/native-cmake --parallel 3

capture_tool_versions() {
  {
    tools/go/bin/go version
    java -version 2>&1 | head -n 1
    python3 --version
    node --version
    npm --version
    "${bun_binary}" --version
    "${bun_binary}" --revision
    h2load --version
    cmake --version | head -n 1
    gcc --version | head -n 1
    dpkg-query -W -f='${Package}=${Version}\n' \
      libmicrohttpd-dev libcpp-httplib-dev nghttp2-client
  } >evidence/locks/tool-versions.txt
}
capture_tool_versions

sha256sum \
  "${host_library}" \
  build/go/fixed-server \
  "${javascript_addon}" \
  "${jvm_jar}" \
  "${jvm_bridge}" \
  build/native/coakka-c \
  build/native/coakka-cpp \
  build/native/microhttpd \
  build/native/cpp-httplib \
  >evidence/locks/built-artifacts.sha256

printf '%s\n' \
  "host_library=${host_library}" \
  "javascript_addon=${javascript_addon}" \
  "jvm_jar=${jvm_jar}" \
  "jvm_bridge=${jvm_bridge}" \
  >evidence/locks/runtime-paths.env
printf 'rpi5-prepare=pass\n'
