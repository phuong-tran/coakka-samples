using CoAkka.Logger;

using var logger = LoggerHost.Start(new LoggerStartSpec("csharp-logger-pressure", QueueCapacity: 2));
var attempts = 8;
var accepted = 0;
var rejected = 0;

for (var i = 0; i < attempts; i++)
{
    if (logger.TryInfo("samples.logger.csharp.pressure", $"{{\"event\":\"pressure\",\"i\":{i}}}", out _))
    {
        accepted++;
    }
    else
    {
        rejected++;
    }
}

for (var i = 0; i < accepted; i++)
{
    _ = logger.AwaitNext(1000) ?? throw new InvalidOperationException("expected accepted record");
}

var stats = logger.Stats();
if (accepted != 2 || rejected != 6 || stats.DroppedCount != 6)
{
    throw new InvalidOperationException($"unexpected pressure result accepted={accepted} rejected={rejected} dropped={stats.DroppedCount}");
}

Console.WriteLine($"coakka_logger_pressure attempts={attempts} accepted={accepted} rejected={rejected} capacity={stats.QueueCapacity} highWatermark={stats.QueueHighWatermark} language=csharp");
Console.WriteLine($"coakka_logger_stats emitted={stats.EmittedCount} delivered={stats.DeliveredCount} dropped={stats.DroppedCount} language=csharp");
