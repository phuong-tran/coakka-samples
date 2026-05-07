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
  "runtime|native|basic|Run one route snapshot and route-miss diagnostic from C and C++"
)

COAKKA_SCENARIO_ROWS=(
  "customer-crud|spring-boot-single-process|Spring Boot web UI plus local runtime store target"
  "customer-crud|kotlin-desktop-local|Kotlin Swing desktop UI plus local runtime store target"
  "customer-crud|python-desktop-local|Python Tk desktop UI plus local runtime store target"
  "customer-crud|spring-boot-spring-boot|Spring Boot web UI plus Spring Boot customer store"
  "customer-crud|spring-boot-node|Spring Boot web UI plus Node.js customer store"
  "customer-crud|spring-boot-go|Spring Boot web UI plus Go customer store"
  "customer-crud|spring-boot-nodes|Spring Boot web UI plus Node.js store and audit services"
)

COAKKA_ARTIFACT_ROWS=(
  "logger JVM jar|logger/jvm/releases/0.1.0+ba2a66d98eb5/coakka-jvm-native-logger-0.1.0.jar"
  "logger Python wheel|logger/python/releases/0.1.0+ba2a66d98eb5/coakka_logger-0.1.0-py3-none-any.whl"
  "logger Node package|logger/node/releases/0.1.0+ba2a66d98eb5/coakka-logger-node-0.1.0.tgz"
  "logger Go package|logger/go/releases/0.1.0+ba2a66d98eb5/coakka-logger-go-0.1.0.tar.gz"
  "logger Native package|logger/native/releases/0.1.0+ba2a66d98eb5/coakka-logger-native-0.1.0.tar.gz"
  "runtime JVM jar|runtime/jvm/releases/0.1.0+22f571fd955c/coakka-jvm-native-runtime-v2-0.1.0-g22f571fd955c.jar"
  "runtime Python wheel|runtime/python/releases/0.1.0+22f571fd955c/coakka_v2_connector-0.1.0-py3-none-any.whl"
  "runtime Node package|runtime/node/releases/0.1.0+22f571fd955c/coakka-v2-connector-node-0.1.0.tgz"
  "runtime Go package|runtime/go/releases/0.1.0+22f571fd955c/coakka-v2-connector-go-0.1.0.tar.gz"
  "runtime Native package|runtime/native/releases/0.1.0+22f571fd955c/coakka-runtime-native-v2-0.1.0.tar.gz"
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
  bash run.sh runtime basic

Available samples:
EOF

  local row lane language sample summary
  for row in "${COAKKA_SAMPLE_ROWS[@]}"; do
    IFS='|' read -r lane language sample summary <<<"${row}"
    printf '  %-31s %s\n' "${lane}/${language}/${sample}" "${summary}"
  done

  cat <<'EOF'

Scenario tracks:
EOF

  coakka_print_scenarios
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
  bash run.sh scenario customer-crud kotlin-desktop-local app
  bash run.sh scenario customer-crud python-desktop-local app
  bash run.sh scenario customer-crud spring-boot-nodes check
  bash run.sh runtime/scenarios/customer-crud/spring-boot-go check
EOF
}
