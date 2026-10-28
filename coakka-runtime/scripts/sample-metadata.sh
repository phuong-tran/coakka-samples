#!/usr/bin/env bash
# Variables are the sourced manifest API consumed by repository/product callers.
# shellcheck disable=SC2034

COAKKA_RUNTIME_NATIVE_VERSION="2.5.1"
COAKKA_RUNTIME_NATIVE_RELEASE="2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be"
COAKKA_RUNTIME_NATIVE_ARTIFACT="runtime/native/releases/${COAKKA_RUNTIME_NATIVE_RELEASE}/coakka-runtime-native-v2-${COAKKA_RUNTIME_NATIVE_VERSION}.tar.gz"

COAKKA_RUNTIME_SAMPLE_ROWS=(
  "runtime|jvm|basic|Run one local request/reply echo on JVM"
  "runtime|jvm|java-basic|Run one local request/reply echo from Java"
  "runtime|jvm|deadletter|Verify a missing JVM route returns a matched deadletter"
  "runtime|jvm|java-deadletter|Observe a runtime deadletter from Java 8 listener API"
  "runtime|python|basic|Run one local request/reply echo from Python"
  "runtime|python|deadletter|Verify a missing Python route returns a matched deadletter"
  "runtime|python|hot-reload|Apply route snapshots and reject stale/invalid generations from Python"
  "runtime|node|basic|Run one local request/reply echo from Node.js"
  "runtime|node|deadletter|Verify a missing Node.js route returns a matched deadletter"
  "runtime|bun|basic|Run one local request/reply echo from Bun"
  "runtime|tauri|intent-command|Run one intent through the Tauri-shaped Rust command bridge"
  "runtime|tauri|desktop-intent|Run the Tauri desktop intent app host smoke"
  "runtime|electron|basic|Run one renderer intent through the Electron main-process runtime bridge"
  "runtime|go|basic|Run one local request/reply echo from Go"
  "runtime|go|deadletter|Verify a missing Go route returns a matched deadletter"
  "runtime|csharp|basic|Run one local request/reply echo from C#"
  "runtime|rust|basic|Run one local request/reply echo from Rust"
  "runtime|swift|basic|Run one local request/reply echo from Swift"
  "runtime|zig|basic|Run one native runtime lifecycle, raw request/reply, and route-miss check from Zig"
  "runtime|mojo|basic|Run one native runtime lifecycle, raw request/reply, and route-miss check from Mojo"
  "runtime|native|basic|Run one route snapshot and route-miss diagnostic from C and C++"
  "runtime|native|pressure|Show bounded runtime queue pressure and deadletter counters from C"
)

COAKKA_SCENARIO_ROWS=(
  "customer-crud|spring-boot-single-process|Spring Boot web UI plus local runtime store target"
  "customer-crud|spring-boot-starter-local|Spring Boot starter sample with local @CoAkkaHandler targets"
  "customer-crud|quarkus-local|Quarkus Kotlin web UI plus local runtime store target"
  "customer-crud|kotlin-desktop-local|Kotlin Swing desktop UI plus local runtime store target"
  "customer-crud|python-desktop-local|Python Tk desktop UI plus local runtime store target"
  "customer-crud|spring-boot-spring-boot|Spring Boot web UI plus Spring Boot customer store"
  "customer-crud|spring-boot-node|Spring Boot web UI plus Node.js customer store"
  "customer-crud|spring-boot-go|Spring Boot web UI plus Go customer store"
  "customer-crud|spring-boot-csharp|Spring Boot web UI plus C# customer store"
  "customer-crud|spring-boot-nodes|Spring Boot web UI plus Node.js store and audit services"
)

COAKKA_CONTAINER_ROWS=(
  "node-python|Node.js web container to Python store container"
)

COAKKA_ADDON_ROWS=(
  "https|HTTPS object"
  "s3|S3 or MinIO GetObject"
  "local-drop|Anchored POSIX local drop"
  "azure-blob|Version-pinned Azure Blob SAS object"
  "gcs|Generation-pinned GCS signed object"
  "webdav|Strong-ETag WebDAV resource"
  "oci-registry|Content-addressed OCI registry blob"
  "huggingface-hub|Commit-pinned Hugging Face Hub file"
  "github-release|Identity-pinned GitHub release asset"
  "google-drive|Retained Google Drive blob revision"
  "dropbox|Immutable Dropbox file revision"
  "sftp|Host-key-pinned SFTP file"
)

COAKKA_RUNTIME_ARTIFACT_ROWS=(
  "runtime Native package|${COAKKA_RUNTIME_NATIVE_ARTIFACT}"
  "runtime addon HTTPS native|runtime-addons/artifact-publisher-https/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-https-native-1.1.0.tar.gz"
  "runtime addon S3 native|runtime-addons/artifact-publisher-s3/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-s3-native-1.1.0.tar.gz"
  "runtime addon Local Drop native|runtime-addons/artifact-publisher-local-drop/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-local-drop-native-1.1.0.tar.gz"
  "runtime addon Azure Blob native|runtime-addons/artifact-publisher-azure-blob/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-azure-blob-native-1.1.0.tar.gz"
  "runtime addon GCS native|runtime-addons/artifact-publisher-gcs/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-gcs-native-1.1.0.tar.gz"
  "runtime addon WebDAV native|runtime-addons/artifact-publisher-webdav/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-webdav-native-1.1.0.tar.gz"
  "runtime addon OCI Distribution native|runtime-addons/artifact-publisher-oci-registry/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-oci-registry-native-1.1.0.tar.gz"
  "runtime addon Hugging Face Hub native|runtime-addons/artifact-publisher-huggingface-hub/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-huggingface-hub-native-1.1.0.tar.gz"
  "runtime addon GitHub Release native|runtime-addons/artifact-publisher-github-release/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-github-release-native-1.1.0.tar.gz"
  "runtime addon Google Drive native|runtime-addons/artifact-publisher-google-drive/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-google-drive-native-1.1.0.tar.gz"
  "runtime addon Dropbox native|runtime-addons/artifact-publisher-dropbox/native/releases/1.1.0+d1032f6d/coakka-runtime-addon-artifact-publisher-dropbox-native-1.1.0.tar.gz"
  "runtime addon SFTP native|runtime-addons/artifact-publisher-sftp/native/releases/1.2.0+88b9a047/coakka-runtime-addon-artifact-publisher-sftp-native-1.2.0.tar.gz"
  "runtime JVM jar|runtime/jvm/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-jvm-native-runtime-v2-2.5.3-g26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e.jar"
  "runtime Python wheel|runtime/python/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka_v2_connector-2.5.3-py3-none-any.whl"
  "runtime Node package|runtime/node/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-v2-connector-node-2.5.3.tgz"
  "runtime Bun package|runtime/bun/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-v2-connector-bun-2.5.3.tgz"
  "runtime Electron package|runtime/electron/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-v2-connector-electron-2.5.3.tgz"
  "runtime Go package|runtime/go/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-v2-connector-go-2.5.3.tar.gz"
  "runtime C# package|runtime/csharp/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/CoAkka.Runtime.2.5.3.nupkg"
  "runtime Rust package|runtime/rust/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-runtime-rs-2.5.3.tar.gz"
  "runtime Swift package|runtime/swift/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-runtime-swift-2.5.3.tar.gz"
  "runtime Mojo source package|runtime/mojo/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-runtime-mojo-2.5.3-source.tar.gz"
  "runtime Zig source package|runtime/zig/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-runtime-zig-2.5.3-source.tar.gz"
  "runtime Tauri source package|runtime/tauri/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/coakka-runtime-tauri-intents-2.5.3-source.tar.gz"
  "Spring Boot starter Maven jar|maven/io/github/phuong-tran/coakka/spring-boot-starter/2.5.3/spring-boot-starter-2.5.3.jar"
  "Quarkus extension Maven jar|maven/io/github/phuong-tran/coakka/quarkus-extension/2.5.3/quarkus-extension-2.5.3.jar"
  "coakka-client linux-x86_64 2.5.1|coakka-tools/coakka-client/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-v2-2.5.1-linux-x86_64.tar.gz"
  "coakka-client linux-aarch64 2.5.1|coakka-tools/coakka-client/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-v2-2.5.1-linux-aarch64.tar.gz"
  "coakka-client macos-aarch64 2.5.1|coakka-tools/coakka-client/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-v2-2.5.1-macos-aarch64.tar.gz"
  "coakka-client windows-x86_64 2.5.1|coakka-tools/coakka-client/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-v2-2.5.1-windows-x86_64.tar.gz"
  "coakka-client windows-aarch64 2.5.1|coakka-tools/coakka-client/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-v2-2.5.1-windows-aarch64.tar.gz"
  "coakka-client docker-demo linux-x86_64 2.5.1|coakka-tools/coakka-client/docker-demo/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-docker-demo-v2-2.5.1-linux-x86_64.tar.gz"
  "coakka-client docker-demo linux-aarch64 2.5.1|coakka-tools/coakka-client/docker-demo/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-client-docker-demo-v2-2.5.1-linux-aarch64.tar.gz"
  "coakka-runtime-inspect linux-aarch64 2.5.1|coakka-tools/coakka-runtime-inspect/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-inspect-v2-2.5.1-linux-aarch64.tar.gz"
  "coakka-runtime-inspect linux-x86_64 2.5.1|coakka-tools/coakka-runtime-inspect/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-inspect-v2-2.5.1-linux-x86_64.tar.gz"
  "coakka-runtime-inspect macos-aarch64 2.5.1|coakka-tools/coakka-runtime-inspect/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-inspect-v2-2.5.1-macos-aarch64.tar.gz"
  "coakka-runtime-inspect windows-x86_64 2.5.1|coakka-tools/coakka-runtime-inspect/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-inspect-v2-2.5.1-windows-x86_64.tar.gz"
  "coakka-runtime-inspect windows-aarch64 2.5.1|coakka-tools/coakka-runtime-inspect/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-inspect-v2-2.5.1-windows-aarch64.tar.gz"
)

coakka_runtime_native_package_fields() {
  if [[ "$#" -ne 1 ]]; then
    printf 'usage: coakka_runtime_native_package_fields <platform>\n' >&2
    return 2
  fi

  case "$1" in
    macos-aarch64|linux-aarch64|linux-x86_64)
      printf '%s|%s|\n' \
        "${COAKKA_RUNTIME_NATIVE_VERSION}" \
        "${COAKKA_RUNTIME_NATIVE_ARTIFACT}"
      ;;
    *)
      printf 'unsupported native runtime package platform: %s\n' "$1" >&2
      return 1
      ;;
  esac
}
