package coakka.samples.logger.jvm.pressure

import coakka.logger.jvm.CoakkaLoggerLevel
import coakka.logger.jvm.JvmLogger
import coakka.logger.jvm.JvmLoggerSpec
import coakka.logger.jvm.LoggerStatusException

fun main() {
    val attempts = 8
    val category = "samples.logger.jvm.pressure"

    JvmLogger.start(
        spec = JvmLoggerSpec(
            systemName = "jvm-pressure-logger",
            queueCapacity = 2,
            minLevel = CoakkaLoggerLevel.INFO,
        )
    ).use { logger ->
        var accepted = 0
        var rejected = 0

        repeat(attempts) { index ->
            try {
                val sequence = logger.info(category, """{"event":"pressure","index":$index}""")
                if (sequence != null) {
                    accepted += 1
                }
            } catch (_: LoggerStatusException) {
                rejected += 1
            }
        }

        val beforeDrain = logger.stats()
        var drained = 0
        while (logger.poll() != null) {
            drained += 1
        }
        val afterDrain = logger.stats()

        check(accepted == 2) { "expected accepted=2, got $accepted" }
        check(rejected == attempts - accepted) { "expected rejected=${attempts - accepted}, got $rejected" }
        check(drained == accepted) { "expected drained=$accepted, got $drained" }
        check(beforeDrain.queueHighWatermark == 2) {
            "expected queueHighWatermark=2, got ${beforeDrain.queueHighWatermark}"
        }
        check(afterDrain.droppedCount == rejected.toLong()) {
            "expected dropped=$rejected, got ${afterDrain.droppedCount}"
        }

        println(
            "coakka_logger_pressure attempts=$attempts accepted=$accepted rejected=$rejected " +
                "capacity=${afterDrain.queueCapacity} highWatermark=${afterDrain.queueHighWatermark}"
        )
        println(
            "coakka_logger_stats emitted=${afterDrain.emittedCount} " +
                "delivered=${afterDrain.deliveredCount} dropped=${afterDrain.droppedCount}"
        )
    }
}
