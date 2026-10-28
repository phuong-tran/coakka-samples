#!/usr/bin/env bash
# Variables are the sourced manifest API consumed by repository/product callers.
# shellcheck disable=SC2034

COAKKA_LOGGER_SAMPLE_ROWS=(
  "logger|jvm|basic|Run one bounded logger record on JVM"
  "logger|jvm|java-basic|Run one bounded logger record from Java"
  "logger|jvm|pressure|Show bounded queue pressure and dropped counters on JVM"
  "logger|jvm|java-pressure|Show bounded queue pressure and dropped counters from Java"
  "logger|python|basic|Run one bounded logger record from Python"
  "logger|python|pressure|Show bounded queue pressure and dropped counters from Python"
  "logger|node|basic|Run one bounded logger record from Node.js"
  "logger|node|pressure|Show bounded queue pressure and dropped counters from Node.js"
  "logger|bun|basic|Run one bounded logger record from Bun"
  "logger|bun|pressure|Show bounded queue pressure and dropped counters from Bun"
  "logger|electron|basic|Run one renderer log intent through the Electron main-process logger bridge"
  "logger|tauri|basic|Run one log intent through the Tauri-shaped Rust logger command bridge"
  "logger|go|basic|Run one bounded logger record from Go"
  "logger|go|pressure|Show bounded queue pressure and dropped counters from Go"
  "logger|csharp|basic|Run one bounded logger record from C#"
  "logger|csharp|pressure|Show bounded queue pressure and dropped counters from C#"
  "logger|rust|basic|Run one bounded logger record from Rust"
  "logger|rust|pressure|Show bounded queue pressure and dropped counters from Rust"
  "logger|swift|basic|Run one bounded logger record from Swift"
  "logger|zig|basic|Run one bounded logger record from Zig"
  "logger|mojo|basic|Run one bounded logger record from Mojo"
  "logger|native|basic|Run one bounded logger record from C and C++"
  "logger|native|pressure|Show bounded queue pressure and dropped counters from C"
)

COAKKA_LOGGER_ARTIFACT_ROWS=(
  "logger JVM jar|logger/jvm/releases/1.2.1+f50756ebff0d/coakka-jvm-native-logger-1.2.1-gf50756ebff0d.jar"
  "logger Python wheel|logger/python/releases/1.2.1+f50756ebff0d/coakka_logger-1.2.2-py3-none-any.whl"
  "logger Node package|logger/node/releases/1.2.1+f50756ebff0d-7718ce6/coakka-logger-node-1.2.7.tgz"
  "logger Bun package|logger/bun/releases/1.2.1+f50756ebff0d-7718ce6/coakka-logger-bun-1.2.7.tgz"
  "logger Electron package|logger/electron/releases/1.2.1+f50756ebff0d-7718ce6/coakka-logger-electron-1.2.7.tgz"
  "logger Go package|logger/go/releases/1.2.1+f50756ebff0d/coakka-logger-go-1.2.1.tar.gz"
  "logger C# package|logger/csharp/releases/1.2.1+f50756ebff0d/CoAkka.Logger.1.2.1.nupkg"
  "logger Rust package|logger/rust/releases/1.2.1+f50756ebff0d/coakka-logger-rs-1.2.1.tar.gz"
  "logger Tauri source package|logger/tauri/releases/1.2.1+f50756ebff0d-7718ce6/coakka-logger-tauri-intents-1.2.2-source.tar.gz"
  "logger Mojo source package|logger/mojo/releases/1.2.1+f50756ebff0d-8264bba/coakka-logger-mojo-1.2.1-source.tar.gz"
  "logger Zig source package|logger/zig/releases/1.2.1+f50756ebff0d-8264bba/coakka-logger-zig-1.2.1-source.tar.gz"
  "logger Native package|logger/native/releases/1.2.1+f50756ebff0d/coakka-logger-native-1.2.1.tar.gz"
)
