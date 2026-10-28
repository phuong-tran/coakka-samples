import { CoakkaLoggerLevel, Logger } from "coakka-logger-node";

const info = Logger.readInfo();
console.log(`coakka_logger_info abi=${info.abiVersion} version=${info.runtimeVersion} git=${info.gitCommit}`);

const logger = Logger.start({ systemName: "node-sample-logger", minLevel: CoakkaLoggerLevel.INFO });
try {
  const category = "samples.logger.node.basic";
  const message = '{"event":"hello","language":"node"}';
  const sequence = logger.info(category, message);
  if (sequence == null) {
    throw new Error("expected INFO log to be accepted");
  }

  const record = logger.awaitNext(1000);
  if (record == null) {
    throw new Error("expected one drained logger record");
  }
  if (record.sequence !== sequence) {
    throw new Error(`record sequence=${record.sequence} did not match emitted sequence=${sequence}`);
  }
  if (record.category !== category || record.message !== message) {
    throw new Error(`unexpected record=${JSON.stringify(record)}`);
  }

  console.log(
    `coakka_logger_record sequence=${record.sequence} ` +
      `level=${record.levelName} category=${record.category} message=${record.message}`,
  );

  const stats = logger.stats();
  console.log(
    `coakka_logger_stats emitted=${stats.emittedCount} ` +
      `delivered=${stats.deliveredCount} dropped=${stats.droppedCount}`,
  );
} finally {
  logger.close();
}
