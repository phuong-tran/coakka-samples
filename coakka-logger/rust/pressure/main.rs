use coakka_logger_rs::{Logger, LoggerSpec};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut spec = LoggerSpec::new("rust-logger-pressure");
    spec.queue_capacity = 2;
    let logger = Logger::start(spec)?;
    let attempts = 8;
    let mut accepted = 0;
    let mut rejected = 0;

    for i in 0..attempts {
        let message = format!("{{\"event\":\"pressure\",\"i\":{i}}}");
        if logger.try_info("samples.logger.rust.pressure", &message)?.is_some() {
            accepted += 1;
        } else {
            rejected += 1;
        }
    }

    for _ in 0..accepted {
        logger.await_next(1000)?.ok_or("expected accepted record")?;
    }

    let stats = logger.stats()?;
    if accepted != 2 || rejected != 6 || stats.dropped_count != 6 {
        return Err(format!("unexpected pressure result accepted={accepted} rejected={rejected} dropped={}", stats.dropped_count).into());
    }
    println!("coakka_logger_pressure attempts={attempts} accepted={accepted} rejected={rejected} capacity={} highWatermark={} language=rust", stats.queue_capacity, stats.queue_high_watermark);
    println!("coakka_logger_stats emitted={} delivered={} dropped={} language=rust", stats.emitted_count, stats.delivered_count, stats.dropped_count);
    Ok(())
}
