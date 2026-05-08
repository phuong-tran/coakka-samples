from __future__ import annotations

from coakka_logger import CoakkaLoggerLevel, Logger, LoggerSpec


def main() -> None:
    info = Logger.read_info()
    print(
        f"coakka_logger_info abi={info.abi_version} "
        f"version={info.runtime_version} git={info.git_commit}"
    )

    category = "samples.logger.python.basic"
    message = '{"event":"hello","language":"python"}'
    with Logger.start(spec=LoggerSpec(system_name="python-sample-logger", min_level=CoakkaLoggerLevel.INFO)) as logger:
        sequence = logger.info(category, message)
        if sequence is None:
            raise RuntimeError("expected INFO log to be accepted")
        record = logger.await_next(timeout_ms=1000)
        if record is None:
            raise RuntimeError("expected one drained logger record")
        if record.sequence != sequence:
            raise RuntimeError(f"record sequence={record.sequence} did not match emitted sequence={sequence}")
        if record.category != category or record.message != message:
            raise RuntimeError(f"unexpected record: {record!r}")

        print(
            f"coakka_logger_record sequence={record.sequence} "
            f"level={record.level_name} category={record.category} message={record.message}"
        )

        stats = logger.stats()
        print(
            f"coakka_logger_stats emitted={stats.emitted_count} "
            f"delivered={stats.delivered_count} dropped={stats.dropped_count}"
        )


if __name__ == "__main__":
    main()
