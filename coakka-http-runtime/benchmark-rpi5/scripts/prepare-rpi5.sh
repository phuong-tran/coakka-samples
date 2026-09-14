#!/bin/sh
set -eu

ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
ARCH=$(uname -m)
if [ "$ARCH" != "aarch64" ]; then
  echo "prepare-rpi5.sh requires Linux aarch64; found $ARCH" >&2
  exit 1
fi

DOWNLOADS="$ROOT/tools/downloads"
BUILD="$ROOT/build"
GO_APPLICATION_SOURCE="$ROOT/artifacts/packages/coakka-http-go-application-source.tar"
JVM_APPLICATION_SOURCE="$ROOT/artifacts/packages/coakka-http-jvm-application-source.tar"
PYTHON_APPLICATION_SOURCE="$ROOT/artifacts/packages/coakka-http-python-application-source.tar"
APPLICATION_CORE_SOURCE="$ROOT/artifacts/packages/coakka-http-language-native-core-source.tar"
CONNECTOR_SOURCE="$ROOT/artifacts/packages/coakka-http-runtime-connector-source.tar"
NATIVE_SOURCE="$ROOT/artifacts/packages/coakka-http-runtime-native-io-source.tar"
NATIVE_COMMONS_SOURCE="$ROOT/artifacts/packages/coakka-commons-native-io-source.tar"
BOOST_SOURCE=${COAKKA_HTTP_BOOST_SOURCE:-/home/pi5/coakka-http-deps-b122/coakka_http_boost_upstream-src}
GO=${GO:-/home/pi5/lab/go1.26.3/bin/go}
GRADLE=${GRADLE:-/home/pi5/lab/coakka-http-jvm-g1/source/gradlew}

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN=$(command -v java || true)
  if [ -z "$JAVA_BIN" ]; then
    echo "Java 17 is required" >&2
    exit 1
  fi
  JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")
  export JAVA_HOME
fi

require_version() {
  LABEL=$1
  ACTUAL=$2
  EXPECTED=$3
  if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "$LABEL version mismatch: expected $EXPECTED, found $ACTUAL" >&2
    exit 1
  fi
}

if [ ! -x "$GO" ]; then
  echo "Go 1.26.3 is required; set GO to its absolute path" >&2
  exit 1
fi
command -v node >/dev/null 2>&1 || {
  echo "Node.js 24.13.0 is required" >&2
  exit 1
}
command -v bun >/dev/null 2>&1 || {
  echo "Bun 1.3.14 is required" >&2
  exit 1
}

PYTHON_ABI=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
GO_VERSION=$($GO env GOVERSION)
JAVA_VERSION=$(
  "$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 |
    sed -n 's/^[[:space:]]*java.version = //p' |
    head -n 1
)
NODE_VERSION=$(node --version)
BUN_VERSION=$(bun --version)
require_version "Python" "$PYTHON_ABI" "3.11"
require_version "Go" "$GO_VERSION" "go1.26.3"
require_version "Java" "$JAVA_VERSION" "17.0.20.1"
require_version "Node.js" "$NODE_VERSION" "v24.13.0"
require_version "Bun" "$BUN_VERSION" "1.3.14"

python3 "$ROOT/scripts/fetch-tools.py"
python3 "$ROOT/scripts/verify-inputs.py"
python3 "$ROOT/scripts/source-manifest.py"
rm -rf \
  "$BUILD/go-application-package" \
  "$BUILD/jvm-application" \
  "$BUILD/jvm-application-source" \
  "$BUILD/python-application-package" \
  "$BUILD/application-core-source" \
  "$BUILD/application-core" \
  "$BUILD/application-native" \
  "$BUILD/python-venv" \
  "$BUILD/python-package"
mkdir -p \
  "$BUILD/bin" \
  "$BUILD/go-application-package" \
  "$BUILD/jvm-application" \
  "$BUILD/jvm-application-source" \
  "$BUILD/python-application-package/coakka_http" \
  "$BUILD/application-core-source" \
  "$BUILD/application-native"

if [ ! -f "$BOOST_SOURCE/boost/version.hpp" ]; then
  echo "the exact Boost 1.91.0 source tree is required; set COAKKA_HTTP_BOOST_SOURCE" >&2
  exit 1
fi
BOOST_TREE_SHA256=$(cd "$BOOST_SOURCE" && \
  find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | \
  awk '{print $1}')
if [ "$BOOST_TREE_SHA256" != \
  "4b07ec8fc17bcaab4b1b996869ce6ebdeb815806764fee960be97a373c0d9edd" ]; then
  echo "Boost source tree does not match the locked benchmark input" >&2
  exit 1
fi

COMMONS_SOURCE="$BUILD/coakka-commons-source"
cmake -E remove_directory "$COMMONS_SOURCE"
mkdir -p "$COMMONS_SOURCE"
tar -xf "$NATIVE_COMMONS_SOURCE" -C "$COMMONS_SOURCE"

tar -xf "$GO_APPLICATION_SOURCE" --strip-components=3 \
  -C "$BUILD/go-application-package"
tar -xf "$JVM_APPLICATION_SOURCE" -C "$BUILD/jvm-application-source"
patch -d "$BUILD/jvm-application-source" -p1 \
  < "$ROOT/patches/coakka-http-jvm-application-api.patch"
cp "$ROOT/src/jvm-application-api/ServiceFacade.kt" \
  "$BUILD/jvm-application-source/http/jvm/src/main/kotlin/coakka/http/connector/ServiceFacade.kt"
tar -xf "$PYTHON_APPLICATION_SOURCE" --strip-components=3 \
  -C "$BUILD/python-application-package/coakka_http"
patch --batch --forward --no-backup-if-mismatch \
  -d "$BUILD/python-application-package/coakka_http" -p1 \
  < "$ROOT/patches/coakka-http-python-application-api.patch"
tar -xf "$APPLICATION_CORE_SOURCE" -C "$BUILD/application-core-source"
patch -d "$BUILD/application-core-source" -p1 \
  < "$ROOT/patches/coakka-http-application-core-vocabulary.patch"

cmake -S "$BUILD/application-core-source" -B "$BUILD/application-core" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_TESTING=OFF \
  -DCOAKKA_HTTP_BUILD_PYTHON_HOST_INLINE=ON \
  -DCOAKKA_HTTP_BUILD_JVM_HOST_INLINE=ON \
  -DCOAKKA_HTTP_BUILD_JAVASCRIPT_CONNECTOR=ON \
  -DCOAKKA_HTTP_NODE_API_INCLUDE_DIR=/opt/node-v24.13.0-linux-arm64/include/node \
  -DFETCHCONTENT_SOURCE_DIR_COAKKA_HTTP_COMMONS="$COMMONS_SOURCE" \
  -DFETCHCONTENT_SOURCE_DIR_COAKKA_HTTP_BOOST_UPSTREAM="$BOOST_SOURCE"
cmake --build "$BUILD/application-core" --target \
  coakka_http_python_host_inline \
  coakka_http_jvm_host_inline \
  coakka_http_javascript_host_inline_addon
cp "$BUILD/application-core/libcoakka_http_python_host_inline.so" \
  "$BUILD/application-native/libcoakka_http_python_application.so"
cp "$BUILD/application-core/libcoakka_http_jvm_host_inline.so" \
  "$BUILD/application-native/libcoakka_http_jvm_application.so"
cp "$BUILD/application-core/coakka_http_javascript_host_inline.node" \
  "$BUILD/application-native/coakka_http_javascript_application.node"

"$BUILD/jvm-application-source/gradlew" \
  -p "$BUILD/jvm-application-source" --no-daemon :http:jvm:jar
JVM_APPLICATION_JAR=$(find \
  "$BUILD/jvm-application-source/http/jvm/build/libs" \
  -maxdepth 1 -type f -name '*.jar' \
  ! -name '*sources*' ! -name '*javadoc*' | head -n 1)
if [ -z "$JVM_APPLICATION_JAR" ] || [ ! -f "$JVM_APPLICATION_JAR" ]; then
  echo "the CoAkka HTTP JVM application jar was not produced" >&2
  exit 1
fi
cp "$JVM_APPLICATION_JAR" \
  "$BUILD/jvm-application/coakka-http-jvm-application.jar"

if [ -f "$NATIVE_SOURCE" ]; then
  RUNTIME_SOURCE="$BUILD/runtime-source"
  NATIVE_BUILD="$BUILD/native-io-uring"
  CONNECTOR_SOURCE_DIR="$BUILD/http-connector-source"
  CONNECTOR_BUILD="$BUILD/http-connector"
  CORE_SDK="$BUILD/http-core-sdk"
  cmake -E remove_directory "$RUNTIME_SOURCE"
  cmake -E remove_directory "$NATIVE_BUILD"
  cmake -E remove_directory "$CONNECTOR_SOURCE_DIR"
  cmake -E remove_directory "$CONNECTOR_BUILD"
  cmake -E remove_directory "$CORE_SDK"
  mkdir -p "$RUNTIME_SOURCE" "$NATIVE_BUILD/effective" \
    "$NATIVE_BUILD/startup" "$NATIVE_BUILD/tls" "$CONNECTOR_SOURCE_DIR"
  tar -xf "$NATIVE_SOURCE" -C "$RUNTIME_SOURCE"
  tar -xf "$CONNECTOR_SOURCE" -C "$CONNECTOR_SOURCE_DIR"
  patch -d "$RUNTIME_SOURCE" -p1 \
    < "$ROOT/patches/coakka-http-linux-poll-shim.patch"
  patch -d "$RUNTIME_SOURCE" -p1 \
    < "$ROOT/patches/coakka-http-public-io-uring-vocabulary.patch"
  cmake -S "$RUNTIME_SOURCE" -B "$NATIVE_BUILD" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_TESTING=ON \
    -DCOAKKA_HTTP_BUILD_BENCHMARKS=ON \
    -DCOAKKA_HTTP_ENABLE_HTTP2_FOUNDATION=ON \
    -DCOAKKA_HTTP_ENABLE_OPENSSL_PROVIDER=ON \
    -DCOAKKA_HTTP_ENABLE_IO_URING=ON \
    -DFETCHCONTENT_SOURCE_DIR_COAKKA_HTTP_COMMONS="$COMMONS_SOURCE" \
    -DFETCHCONTENT_SOURCE_DIR_COAKKA_HTTP_BOOST_UPSTREAM="$BOOST_SOURCE"
  cmake --build "$NATIVE_BUILD" --target \
    coakka_http_native_connector_server \
    coakka_http_native_poller_tests \
    coakka_http_public_runtime \
    coakka_http_runtime_http2_public_fixture
  "$NATIVE_BUILD/coakka_http_native_poller_tests"
  "$NATIVE_BUILD/coakka_http_runtime_http2_public_fixture"
  COAKKA_HTTP_TEST_SERVER_IO_BACKEND=io_uring \
    "$NATIVE_BUILD/coakka_http_runtime_http2_public_fixture"

  cmake --install "$NATIVE_BUILD" --prefix "$CORE_SDK"
  cmake -S "$CONNECTOR_SOURCE_DIR" -B "$CONNECTOR_BUILD" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_PREFIX_PATH="$CORE_SDK" \
    -DBUILD_TESTING=OFF \
    -DCOAKKA_HTTP_NODE_API_INCLUDE_DIR=/opt/node-v24.13.0-linux-arm64/include/node
  cmake --build "$CONNECTOR_BUILD" --target coakka_http_javascript_addon

  "$CONNECTOR_SOURCE_DIR/gradlew" -p "$CONNECTOR_SOURCE_DIR" --no-daemon \
    :connectors:jvm:runtime:jar \
    -PcoakkaHttpPrefix="$CORE_SDK"
  JVM_CONNECTOR_BUILD="$CONNECTOR_SOURCE_DIR/connectors/jvm/runtime/build"
  JVM_CONNECTOR_JAR="$JVM_CONNECTOR_BUILD/libs/coakka-http-jvm-1.0.0.jar"
  test -f "$JVM_CONNECTOR_JAR"

  JVM_HTTP2_NATIVE="$BUILD/jvm-http2-native"
  rm -rf "$JVM_HTTP2_NATIVE"
  mkdir -p "$JVM_HTTP2_NATIVE"
  cp "$NATIVE_BUILD/public/libcoakka_http_runtime.so.1.0.0" \
    "$JVM_HTTP2_NATIVE/libcoakka_http_runtime.so.1.0.0"
  cp "$JVM_CONNECTOR_BUILD/native/libcoakka_http_jvm.so" \
    "$JVM_HTTP2_NATIVE/libcoakka_http_jvm.so"
  ln -s libcoakka_http_runtime.so.1.0.0 \
    "$JVM_HTTP2_NATIVE/libcoakka_http_runtime.so.1"

  python3 "$ROOT/scripts/stage-javascript-http2-package.py" \
    "$CONNECTOR_SOURCE_DIR/connectors/javascript/package" \
    "$BUILD/javascript-http2-package" \
    "$CONNECTOR_BUILD/coakka_http_javascript.node" \
    "$NATIVE_BUILD/public/libcoakka_http_runtime.so.1.0.0"

  mkdir -p "$BUILD/python-package"
  python3 "$CONNECTOR_SOURCE_DIR/connectors/python/scripts/build-wheel.py" \
    --platform linux-aarch64 \
    --native "$NATIVE_BUILD/public/libcoakka_http_runtime.so.1.0.0" \
    --output "$BUILD/python-package"

  openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
    -subj '/CN=CoAkka RPi5 benchmark CA' \
    -keyout "$NATIVE_BUILD/tls/ca.key" \
    -out "$NATIVE_BUILD/tls/ca.pem" >/dev/null 2>&1
  openssl req -newkey rsa:2048 -nodes -sha256 \
    -subj '/CN=localhost' \
    -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
    -addext 'extendedKeyUsage=serverAuth' \
    -keyout "$NATIVE_BUILD/tls/server.key" \
    -out "$NATIVE_BUILD/tls/server.csr" >/dev/null 2>&1
  openssl x509 -req -sha256 -days 1 \
    -in "$NATIVE_BUILD/tls/server.csr" \
    -CA "$NATIVE_BUILD/tls/ca.pem" \
    -CAkey "$NATIVE_BUILD/tls/ca.key" \
    -CAcreateserial -copy_extensions copy \
    -out "$NATIVE_BUILD/tls/server.pem" >/dev/null 2>&1

  render_native_backend_config() {
    BACKEND=$1
    OUTPUT=$2
    sed \
      -e "s|@SERVER_IO_BACKEND@|$BACKEND|g" \
      -e "s|@CERTIFICATE_CHAIN_FILE@|$NATIVE_BUILD/tls/server.pem|g" \
      -e "s|@PRIVATE_KEY_FILE@|$NATIVE_BUILD/tls/server.key|g" \
      "$ROOT/config/native-http2-backend.textproto.in" >"$OUTPUT"
  }
  render_native_backend_config \
    COAKKA_HTTP_RUNTIME_SERVER_IO_BACKEND_PLATFORM_DEFAULT \
    "$NATIVE_BUILD/startup/platform-default.textproto"
  render_native_backend_config \
    COAKKA_HTTP_RUNTIME_SERVER_IO_BACKEND_IO_URING \
    "$NATIVE_BUILD/startup/io-uring.textproto"
  PROTOC="$NATIVE_BUILD/_deps/coakka_http_protobuf_upstream-build/protoc"
  export PROTOC
  "$RUNTIME_SOURCE/benchmarks/compile_startup_config.sh" \
    "$NATIVE_BUILD/startup/platform-default.textproto" \
    "$NATIVE_BUILD/startup/platform-default.pb"
  "$RUNTIME_SOURCE/benchmarks/compile_startup_config.sh" \
    "$NATIVE_BUILD/startup/io-uring.textproto" \
    "$NATIVE_BUILD/startup/io-uring.pb"
  {
    printf 'runtime_source_sha256=%s\n' \
      "$(sha256sum "$NATIVE_SOURCE" | awk '{print $1}')"
    printf 'commons_source_sha256=%s\n' \
      "$(sha256sum "$NATIVE_COMMONS_SOURCE" | awk '{print $1}')"
    printf 'boost_source_tree_sha256=%s\n' "$BOOST_TREE_SHA256"
  } >"$NATIVE_BUILD/dependency-sources.sha256"
fi

(cd "$ROOT/src/go" && CGO_ENABLED=1 "$GO" build -trimpath -ldflags='-s -w' \
  -o "$BUILD/bin/fixed-coakka-go" .)
(cd "$ROOT/src/core/go" && CGO_ENABLED=1 "$GO" build -trimpath -ldflags='-s -w' \
  -o "$BUILD/bin/fixed-coakka-go-core" .)
(cd "$ROOT/src/comparisons/go" && "$GO" mod download)
for GO_PROFILE in direct chi gin; do
  (cd "$ROOT/src/comparisons/go" && CGO_ENABLED=1 "$GO" build \
    -mod=readonly -trimpath -ldflags='-s -w' \
    -o "$BUILD/bin/fixed-go-$GO_PROFILE" "./cmd/$GO_PROFILE")
done

if [ ! -x "$GRADLE" ]; then
  echo "a Gradle wrapper with Java 17 support is required; set GRADLE" >&2
  exit 1
fi
(cd "$ROOT/src/jvm" && "$GRADLE" --no-daemon clean installDist)
rm -rf "$BUILD/jvm"
cp -R \
  "$ROOT/src/jvm/build/install/coakka-http-rpi5-jvm-comparisons" \
  "$BUILD/jvm"

(cd "$ROOT/src/javascript" && npm ci --ignore-scripts)
python3 -m venv "$BUILD/python-venv"
"$BUILD/python-venv/bin/python" -m pip install --no-index \
  --find-links "$DOWNLOADS/python-wheels" \
  -r "$ROOT/src/python/requirements.txt"
"$BUILD/python-venv/bin/python" -m pip install --no-deps \
  "$BUILD/python-package/coakka_http-1.0.0-py3-none-manylinux_2_28_aarch64.whl"

{
  uname -a
  python3 --version
  "$GO" version
  java -version 2>&1
  node --version
  bun --version
  npm --version
  NO_COLOR=true "$DOWNLOADS/oha-1.14.0-linux-arm64" --version
} > "$BUILD/toolchain-versions.txt"

echo "prepared the Go, JVM, Python, Node.js, Bun, and native backend pair suites"
