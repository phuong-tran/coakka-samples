package coakka.samples.logger.jvm.javabasic;

import coakka.logger.jvm.JvmLogger;
import coakka.logger.jvm.LoggerInfoSnapshot;
import coakka.logger.jvm.LoggerRecordSnapshot;
import coakka.logger.jvm.LoggerStatsSnapshot;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        LoggerInfoSnapshot info = JvmLogger.readLoggerInfo();
        System.out.println(
            "coakka_logger_info abi=" + info.getAbiVersion() +
                " version=" + info.getRuntimeVersion() +
                " git=" + info.getGitCommit() +
                " language=java"
        );

        String category = "samples.logger.jvm.java.basic";
        String message = "{\"event\":\"hello\",\"language\":\"java\"}";
        try (JvmLogger logger = JvmLogger.start()) {
            Long sequence = logger.info(category, message);
            if (sequence == null) {
                throw new IllegalStateException("expected INFO log to be accepted");
            }

            LoggerRecordSnapshot record = logger.awaitNext(1_000);
            if (record == null) {
                throw new IllegalStateException("expected one drained logger record");
            }
            if (record.getSequence() != sequence) {
                throw new IllegalStateException(
                    "record sequence=" + record.getSequence() + " did not match emitted sequence=" + sequence
                );
            }
            if (!category.equals(record.getCategory()) || !message.equals(record.getMessage())) {
                throw new IllegalStateException("unexpected record: " + record);
            }

            System.out.println(
                "coakka_logger_record sequence=" + record.getSequence() +
                    " level=" + record.getLevelName() +
                    " category=" + record.getCategory() +
                    " message=" + record.getMessage()
            );

            LoggerStatsSnapshot stats = logger.stats();
            System.out.println(
                "coakka_logger_stats emitted=" + stats.getEmittedCount() +
                    " delivered=" + stats.getDeliveredCount() +
                    " dropped=" + stats.getDroppedCount() +
                    " language=java"
            );
        }
    }
}
