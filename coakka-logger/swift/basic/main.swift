import CoAkkaLogger

let info = try Logger.readInfo()
let logger = try Logger.start(
    spec: LoggerSpec(systemName: "swift-logger-basic", minLevel: .info)
)
defer {
    try? logger.close()
}

let sequence = try logger.info("samples.logger.swift.basic", #"{"event":"hello","language":"swift"}"#)
let record = try logger.awaitNext(timeoutMs: 1_000)
let stats = try logger.stats()

print("coakka_logger_info abi=\(info.abiVersion) version=\(info.runtimeVersion) git=\(info.gitCommit)")
print("coakka_logger_record sequence=\(sequence ?? 0) level=\(record?.levelName ?? "") category=\(record?.category ?? "") message=\(record?.message ?? "")")
print("coakka_logger_stats emitted=\(stats.emittedCount) delivered=\(stats.deliveredCount) dropped=\(stats.droppedCount)")
