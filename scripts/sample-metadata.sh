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
  "spring-go|Spring Boot JVM web container to Go store container"
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
  "runtime Native package|runtime/native/releases/1.2.1+abde383/coakka-runtime-native-v2-1.2.1.tar.gz"
  "runtime JVM jar|runtime/jvm/releases/1.2.1+abde383-fa29f94/coakka-jvm-native-runtime-v2-1.2.1-gabde383-fa29f94.jar"
  "runtime Python wheel|runtime/python/releases/1.2.1+abde383-fa29f94/coakka_v2_connector-1.2.1-py3-none-any.whl"
  "runtime Node package|runtime/node/releases/1.2.1+abde383-fa29f94/coakka-v2-connector-node-1.2.1.tgz"
  "runtime Go package|runtime/go/releases/1.2.1+abde383-fa29f94/coakka-v2-connector-go-1.2.1.tar.gz"
  "runtime C# package|runtime/csharp/releases/1.2.1+abde383-fa29f94/CoAkka.Runtime.1.2.1.nupkg"
  "runtime Rust package|runtime/rust/releases/1.2.1+abde383-fa29f94/coakka-runtime-rs-1.2.1-spike.tar.gz"
  "runtime Mojo source package|runtime/mojo/releases/1.2.1+abde383-fa29f94/coakka-runtime-mojo-1.2.1-source.tar.gz"
  "runtime Zig source package|runtime/zig/releases/1.2.1+abde383-fa29f94/coakka-runtime-zig-1.2.1-source.tar.gz"
  "Spring Boot starter Maven jar|maven/coakka/spring/coakka-spring-boot-starter/1.2.1-gfa29f94b59f9/coakka-spring-boot-starter-1.2.1-gfa29f94b59f9.jar"
  "Quarkus extension Maven jar|maven/coakka/quarkus/coakka-quarkus-extension/1.2.1-gfa29f94b59f9/coakka-quarkus-extension-1.2.1-gfa29f94b59f9.jar"
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
  runtime-client/docker-demo      Verify call/ask against the published Docker demo bundle

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
  bash run.sh containers spring-go
  bash run.sh containers down
EOF
}
