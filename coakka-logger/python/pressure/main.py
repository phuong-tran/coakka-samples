from __future__ import annotations

from coakka_logger import CoakkaLoggerLevel, Logger, LoggerSpec, LoggerStatusError


def main() -> None:
    attempts = 8
    category = "samples.logger.python.pressure"

    with Logger.start(
        spec=LoggerSpec(
            system_name="python-pressure-logger",
            queue_capacity=2,
            min_level=CoakkaLoggerLevel.INFO,
        )
    ) as logger:
        accepted = 0
        rejected = 0

        for index in range(attempts):
            try:
                sequence = logger.info(category, f'{{"event":"pressure","index":{index}}}')
                if sequence is not None:
                    accepted += 1
            except LoggerStatusError:
                rejected += 1

        before_drain = logger.stats()
        drained = 0
        while logger.poll() is not None:
            drained += 1
        after_drain = logger.stats()

        if accepted != 2:
            raise RuntimeError(f"expected accepted=2, got {accepted}")
        if rejected != attempts - accepted:
            raise RuntimeError(f"expected rejected={attempts - accepted}, got {rejected}")
        if drained != accepted:
            raise RuntimeError(f"expected drained={accepted}, got {drained}")
        if before_drain.queue_high_watermark != 2:
            raise RuntimeError(f"expected queue_high_watermark=2, got {before_drain.queue_high_watermark}")
        if after_drain.dropped_count != rejected:
            raise RuntimeError(f"expected dropped={rejected}, got {after_drain.dropped_count}")

        print(
            f"coakka_logger_pressure attempts={attempts} accepted={accepted} rejected={rejected} "
            f"capacity={after_drain.queue_capacity} highWatermark={after_drain.queue_high_watermark}"
        )
        print(
            f"coakka_logger_stats emitted={after_drain.emitted_count} "
            f"delivered={after_drain.delivered_count} dropped={after_drain.dropped_count}"
        )


if __name__ == "__main__":
    main()
