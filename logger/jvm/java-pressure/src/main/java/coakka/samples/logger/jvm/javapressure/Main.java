package coakka.samples.logger.jvm.javapressure;

import coakka.logger.jvm.CoakkaLoggerLevel;
import coakka.logger.jvm.JvmLogger;
import coakka.logger.jvm.JvmLoggerSpec;
import coakka.logger.jvm.LoggerStatsSnapshot;
import coakka.logger.jvm.LoggerStatusException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int attempts = 8;
        String category = "samples.logger.jvm.java.pressure";
        JvmLoggerSpec spec = new JvmLoggerSpec(
            "jvm-java-pressure-logger",
            2,
            128,
            1024,
            CoakkaLoggerLevel.INFO
        );

        try (JvmLogger logger = JvmLogger.start(null, spec)) {
            int accepted = 0;
            int rejected = 0;
            for (int index = 0; index < attempts; index += 1) {
                try {
                    Long sequence = logger.info(category, "{\"event\":\"pressure\",\"index\":" + index + "}");
                    if (sequence != null) {
                        accepted += 1;
                    }
                } catch (LoggerStatusException ignored) {
                    rejected += 1;
                }
            }

            LoggerStatsSnapshot beforeDrain = logger.stats();
            int drained = 0;
            while (logger.poll() != null) {
                drained += 1;
            }
            LoggerStatsSnapshot afterDrain = logger.stats();

            if (accepted != 2) {
                throw new IllegalStateException("expected accepted=2, got " + accepted);
            }
            if (rejected != attempts - accepted) {
                throw new IllegalStateException("expected rejected=" + (attempts - accepted) + ", got " + rejected);
            }
            if (drained != accepted) {
                throw new IllegalStateException("expected drained=" + accepted + ", got " + drained);
            }
            if (beforeDrain.getQueueHighWatermark() != 2) {
                throw new IllegalStateException(
                    "expected queueHighWatermark=2, got " + beforeDrain.getQueueHighWatermark()
                );
            }
            if (afterDrain.getDroppedCount() != rejected) {
                throw new IllegalStateException("expected dropped=" + rejected + ", got " + afterDrain.getDroppedCount());
            }

            System.out.println(
                "coakka_logger_pressure attempts=" + attempts +
                    " accepted=" + accepted +
                    " rejected=" + rejected +
                    " capacity=" + afterDrain.getQueueCapacity() +
                    " highWatermark=" + afterDrain.getQueueHighWatermark() +
                    " language=java"
            );
            System.out.println(
                "coakka_logger_stats emitted=" + afterDrain.getEmittedCount() +
                    " delivered=" + afterDrain.getDeliveredCount() +
                    " dropped=" + afterDrain.getDroppedCount() +
                    " language=java"
            );
        }
    }
}
