use coakka_logger_rs::{Logger, LoggerSpec};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let info = Logger::read_info()?;
    let logger = Logger::start(LoggerSpec::new("rust-logger-basic"))?;
    let category = "samples.logger.rust.basic";
    let message = "{\"event\":\"hello\",\"language\":\"rust\"}";
    let sequence = logger.try_info(category, message)?.ok_or("log rejected")?;
    let record = logger.await_next(1000)?.ok_or("record missing")?;
    if record.sequence != sequence || record.category != category || record.message != message {
        return Err("unexpected record".into());
    }
    let stats = logger.stats()?;
    println!("coakka_logger_info abi={} version={} git={}", info.abi_version, info.runtime_version, info.git_commit);
    println!("coakka_logger_record sequence={} level=info category={} message={}", record.sequence, record.category, record.message);
    println!("coakka_logger_stats emitted={} delivered={} dropped={}", stats.emitted_count, stats.delivered_count, stats.dropped_count);
    Ok(())
}
