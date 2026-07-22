#!/usr/bin/env bash

COAKKA_PUBLISH_RAW_BASE_DEFAULT="https://raw.githubusercontent.com/phuong-tran/coakka-publish/main"

COAKKA_SAMPLE_ROWS=(
  "logger|jvm|basic|Run one bounded logger record on JVM"
  "logger|jvm|java-basic|Run one bounded logger record from Java"
  "logger|jvm|pressure|Show bounded queue pressure and dropped counters on JVM"
  "logger|jvm|java-pressure|Show bounded queue pressure and dropped counters from Java"
  "logger|python|basic|Run one bounded logger record from Python"
  "logger|python|pressure|Show bounded queue pressure and dropped counters from Python"
  "logger|node|basic|Run one bounded logger record from Node.js"
  "logger|node|pressure|Show bounded queue pressure and dropped counters from Node.js"
  "logger|go|basic|Run one bounded logger record from Go"
  "logger|go|pressure|Show bounded queue pressure and dropped counters from Go"
  "logger|csharp|basic|Run one bounded logger record from C#"
  "logger|csharp|pressure|Show bounded queue pressure and dropped counters from C#"
  "logger|rust|basic|Run one bounded logger record from Rust"
  "logger|rust|pressure|Show bounded queue pressure and dropped counters from Rust"
  "logger|zig|basic|Run one bounded logger record from Zig"
  "logger|mojo|basic|Run one bounded logger record from Mojo"
  "logger|native|basic|Run one bounded logger record from C and C++"
  "logger|native|pressure|Show bounded queue pressure and dropped counters from C"
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

COAKKA_ARTIFACT_ROWS=(
  "logger JVM jar|logger/jvm/releases/1.2.1+f50756ebff0d/coakka-jvm-native-logger-1.2.1-gf50756ebff0d.jar"
  "logger Python wheel|logger/python/releases/1.2.1+f50756ebff0d/coakka_logger-1.2.1-py3-none-any.whl"
  "logger Node package|logger/node/releases/1.2.1+f50756ebff0d/coakka-logger-node-1.2.1.tgz"
  "logger Go package|logger/go/releases/1.2.1+f50756ebff0d/coakka-logger-go-1.2.1.tar.gz"
  "logger C# package|logger/csharp/releases/1.2.1+f50756ebff0d/CoAkka.Logger.1.2.1.nupkg"
  "logger Rust package|logger/rust/releases/1.2.1+f50756ebff0d/coakka-logger-rs-1.2.1.tar.gz"
  "logger Mojo source package|logger/mojo/releases/1.2.1+f50756ebff0d-8264bba/coakka-logger-mojo-1.2.1-source.tar.gz"
  "logger Zig source package|logger/zig/releases/1.2.1+f50756ebff0d-8264bba/coakka-logger-zig-1.2.1-source.tar.gz"
  "logger Native package|logger/native/releases/1.2.1+f50756ebff0d/coakka-logger-native-1.2.1.tar.gz"
  "runtime Native package|runtime/native/releases/1.3.1+bda2ef5/coakka-runtime-native-v2-1.3.1.tar.gz"
  "runtime JVM jar|runtime/jvm/releases/1.3.1+bda2ef5-0a0aa76/coakka-jvm-native-runtime-v2-1.3.1-gbda2ef5-0a0aa76.jar"
  "runtime Python wheel|runtime/python/releases/1.3.1+bda2ef5-0a0aa76/coakka_v2_connector-1.3.1-py3-none-any.whl"
  "runtime Node package|runtime/node/releases/1.3.1+bda2ef5-0a0aa76/coakka-v2-connector-node-1.3.1.tgz"
  "runtime Bun package|runtime/bun/releases/1.3.1+bda2ef5-247df1b/coakka-v2-connector-bun-1.3.1.tgz"
  "runtime Electron package|runtime/electron/releases/1.3.1+bda2ef5-4e0cab0/coakka-v2-connector-electron-1.3.1.tgz"
  "runtime Go package|runtime/go/releases/1.3.1+bda2ef5-0a0aa76/coakka-v2-connector-go-1.3.1.tar.gz"
  "runtime C# package|runtime/csharp/releases/1.3.1+bda2ef5-0a0aa76/CoAkka.Runtime.1.3.1.nupkg"
  "runtime Rust package|runtime/rust/releases/1.3.1+bda2ef5-0a0aa76/coakka-runtime-rs-1.3.1-spike.tar.gz"
  "runtime Mojo source package|runtime/mojo/releases/1.3.1+bda2ef5-0a0aa76/coakka-runtime-mojo-1.3.1-source.tar.gz"
  "runtime Zig source package|runtime/zig/releases/1.3.1+bda2ef5-0a0aa76/coakka-runtime-zig-1.3.1-source.tar.gz"
  "runtime Tauri source package|runtime/tauri/releases/1.3.1+bda2ef5-247df1b/coakka-runtime-tauri-intents-1.3.1-source.tar.gz"
  "Spring Boot starter Maven jar|maven/coakka/spring/coakka-spring-boot-starter/1.3.1-g0a0aa76/coakka-spring-boot-starter-1.3.1-g0a0aa76.jar"
  "Quarkus extension Maven jar|maven/coakka/quarkus/coakka-quarkus-extension/1.3.1-g0a0aa76/coakka-quarkus-extension-1.3.1-g0a0aa76.jar"
  "coakka-client linux-x86_64|cli/releases/1.3.1+2215b0f/coakka-client-v2-1.3.1-linux-x86_64.tar.gz"
  "coakka-client linux-aarch64|cli/releases/1.3.1+2215b0f/coakka-client-v2-1.3.1-linux-aarch64.tar.gz"
  "coakka-client macos-aarch64|cli/releases/1.3.1+2215b0f/coakka-client-v2-1.3.1-macos-aarch64.tar.gz"
  "coakka-client windows-x86_64|cli/releases/1.3.1+2215b0f/coakka-client-v2-1.3.1-windows-x86_64.tar.gz"
  "coakka-client windows-aarch64|cli/releases/1.3.1+2215b0f/coakka-client-v2-1.3.1-windows-aarch64.tar.gz"
  "coakka-client docker-demo linux-x86_64|demo/coakka-client/releases/1.3.1+2215b0f/coakka-client-docker-demo-v2-1.3.1-linux-x86_64.tar.gz"
  "coakka-client docker-demo linux-aarch64|demo/coakka-client/releases/1.3.1+2215b0f/coakka-client-docker-demo-v2-1.3.1-linux-aarch64.tar.gz"
  "coakka-runtime-inspect linux-aarch64|runtime-inspect/native/releases/1.3.1+4ce41f19/coakka-runtime-inspect-v2-1.3.1-linux-aarch64.tar.gz"
  "coakka-runtime-inspect linux-x86_64|runtime-inspect/native/releases/1.3.1+4ce41f19/coakka-runtime-inspect-v2-1.3.1-linux-x86_64.tar.gz"
  "coakka-runtime-inspect macos-aarch64|runtime-inspect/native/releases/1.3.1+d7ab7fa/coakka-runtime-inspect-v2-1.3.1-macos-aarch64.tar.gz"
  "coakka-runtime-inspect windows-x86_64|runtime-inspect/native/releases/1.3.1+d7ab7fa/coakka-runtime-inspect-v2-1.3.1-windows-x86_64.tar.gz"
  "coakka-runtime-inspect windows-aarch64|runtime-inspect/native/releases/1.3.1+d7ab7fa/coakka-runtime-inspect-v2-1.3.1-windows-aarch64.tar.gz"
)

coakka_default_publish_root() {
  local repo_root="$1"
  printf '%s\n' "${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
}

coakka_print_samples() {
  cat <<'EOF'
Recommended first runs:
  bash run.sh
  bash run.sh logger basic
  bash run.sh logger/python/basic

Available samples:
EOF

  local row lane language sample summary
  for row in "${COAKKA_SAMPLE_ROWS[@]}"; do
    IFS='|' read -r lane language sample summary <<<"${row}"
    printf '  %-31s %s\n' "${lane}/${language}/${sample}" "${summary}"
  done

  cat <<'EOF'

Runtime client sample:
  runtime-client/check            Verify the published CoAkka Runtime CLI client archive
  runtime-client/docker-bundle     Verify call/ask against the published Linux Docker bundle
  runtime-client/docker-walkthrough Run coakka-client against two native runtime services in Docker
  runtime-client/dockerhub-demo    Run the published Docker Hub runtime-client demo image

Runtime inspect sample:
  runtime-inspect/check           Verify inspect docs and published archive metadata when available
  runtime-inspect/published-smoke Smoke the published platform coakka-runtime-inspect archive
  runtime-inspect/local-smoke     Smoke a sibling native coakka-runtime-inspect build
  runtime-inspect/serve           Start the browser inspect UI from a local native build
  runtime-inspect/docker-smoke    Build a local Docker image from the published Linux inspect archive and smoke it
  runtime-inspect/docker-serve    Start the browser inspect UI from that local Docker image
  runtime-inspect/dockerhub-smoke Run the published Docker Hub inspect sample image

Scenario tracks:
EOF

  coakka_print_scenarios

  cat <<'EOF'

Container samples:
EOF

  coakka_print_containers
}

coakka_print_scenarios() {
  local row track topology summary
  for row in "${COAKKA_SCENARIO_ROWS[@]}"; do
    IFS='|' read -r track topology summary <<<"${row}"
    printf '  %-55s %s\n' "runtime/scenarios/${track}/${topology}" "${summary}"
  done

  cat <<'EOF'

Scenario commands:
  bash run.sh scenarios
  bash run.sh scenarios check
  bash run.sh scenario customer-crud spring-boot-single-process dev
EOF
}

coakka_print_containers() {
  local row sample summary
  for row in "${COAKKA_CONTAINER_ROWS[@]}"; do
    IFS='|' read -r sample summary <<<"${row}"
    printf '  %-31s %s\n' "containers/${sample}" "${summary}"
  done

  cat <<'EOF'

Container commands:
  bash run.sh containers node-python
  bash run.sh containers down
EOF
}
