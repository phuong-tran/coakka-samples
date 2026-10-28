package coakka.samples.logger.jvm.basic

import coakka.logger.jvm.JvmLogger

fun main() {
    val info = JvmLogger.readLoggerInfo()
    println(
        "coakka_logger_info abi=${info.abiVersion} " +
            "version=${info.runtimeVersion} git=${info.gitCommit}"
    )

    JvmLogger.start().use { logger ->
        val category = "samples.logger.jvm.basic"
        val message = """{"event":"hello","language":"jvm"}"""
        val sequence = logger.info(category, message)
            ?: error("expected INFO log to be accepted")
        val record = logger.awaitNext(1_000)
            ?: error("expected one drained logger record")

        check(record.sequence == sequence) {
            "record sequence=${record.sequence} did not match emitted sequence=$sequence"
        }
        check(record.category == category && record.message == message) {
            "unexpected record: $record"
        }

        println(
            "coakka_logger_record sequence=${record.sequence} " +
                "level=${record.levelName} category=${record.category} message=${record.message}"
        )

        val stats = logger.stats()
        println(
            "coakka_logger_stats emitted=${stats.emittedCount} " +
                "delivered=${stats.deliveredCount} dropped=${stats.droppedCount}"
        )
    }
}
