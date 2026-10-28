import { CoakkaLoggerLevel, Logger, LoggerStatusError } from "coakka-logger-node";

const attempts = 8;
const category = "samples.logger.node.pressure";
const logger = Logger.start({
  systemName: "node-pressure-logger",
  queueCapacity: 2,
  minLevel: CoakkaLoggerLevel.INFO,
});

try {
  let accepted = 0;
  let rejected = 0;

  for (let index = 0; index < attempts; index += 1) {
    try {
      const sequence = logger.info(category, `{"event":"pressure","index":${index}}`);
      if (sequence != null) {
        accepted += 1;
      }
    } catch (error) {
      if (!(error instanceof LoggerStatusError)) {
        throw error;
      }
      rejected += 1;
    }
  }

  const beforeDrain = logger.stats();
  let drained = 0;
  while (logger.poll() != null) {
    drained += 1;
  }
  const afterDrain = logger.stats();

  if (accepted !== 2) {
    throw new Error(`expected accepted=2, got ${accepted}`);
  }
  if (rejected !== attempts - accepted) {
    throw new Error(`expected rejected=${attempts - accepted}, got ${rejected}`);
  }
  if (drained !== accepted) {
    throw new Error(`expected drained=${accepted}, got ${drained}`);
  }
  if (beforeDrain.queueHighWatermark !== 2) {
    throw new Error(`expected queueHighWatermark=2, got ${beforeDrain.queueHighWatermark}`);
  }
  if (afterDrain.droppedCount !== rejected) {
    throw new Error(`expected dropped=${rejected}, got ${afterDrain.droppedCount}`);
  }

  console.log(
    `coakka_logger_pressure attempts=${attempts} accepted=${accepted} rejected=${rejected} ` +
      `capacity=${afterDrain.queueCapacity} highWatermark=${afterDrain.queueHighWatermark}`,
  );
  console.log(
    `coakka_logger_stats emitted=${afterDrain.emittedCount} ` +
      `delivered=${afterDrain.deliveredCount} dropped=${afterDrain.droppedCount}`,
  );
} finally {
  logger.close();
}
