using CoAkka.Logger;

using var logger = LoggerHost.Start(new LoggerStartSpec("csharp-logger-basic", QueueCapacity: 8));
var info = LoggerHost.ReadInfo();

var category = "samples.logger.csharp.basic";
var message = "{\"event\":\"hello\",\"language\":\"csharp\"}";
if (!logger.TryInfo(category, message, out var sequence))
{
    throw new InvalidOperationException("expected INFO log to be accepted");
}

var record = logger.AwaitNext(1000) ?? throw new InvalidOperationException("expected one drained logger record");
if (record.Sequence != sequence || record.Category != category || record.Message != message)
{
    throw new InvalidOperationException($"unexpected record: {record}");
}

var stats = logger.Stats();
Console.WriteLine($"coakka_logger_info abi={info.AbiVersion} version={info.RuntimeVersion} git={info.GitCommit}");
Console.WriteLine($"coakka_logger_record sequence={record.Sequence} level=info category={record.Category} message={record.Message}");
Console.WriteLine($"coakka_logger_stats emitted={stats.EmittedCount} delivered={stats.DeliveredCount} dropped={stats.DroppedCount}");
