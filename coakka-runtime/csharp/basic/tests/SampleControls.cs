namespace CoAkka.Samples.Runtime.CSharp.Basic;

internal static class SampleControls
{
    public static async Task Main()
    {
        foreach (var bodyFails in new[] { false, true })
        foreach (var closeFails in new[] { false, true })
        {
            var body = new InvalidOperationException("body-control");
            var close = new IOException("close-control");
            var closes = 0;
            Exception? observed = null;
            try
            {
                await Program.WithCleanup(() => bodyFails ? Task.FromException(body) : Task.CompletedTask, async () =>
                {
                    // Exercise exceptions after asynchronous cleanup has yielded.
                    await Task.Yield();
                    closes++;
                    if (closeFails) throw close;
                });
            }
            catch (Exception error) { observed = error; }
            if (closes != 1) throw new InvalidOperationException("cleanup must execute exactly once");
            if (bodyFails && closeFails)
            {
                if (observed is not AggregateException aggregate || aggregate.InnerExceptions.Count != 2
                    || !ReferenceEquals(aggregate.InnerExceptions[0], body) || !ReferenceEquals(aggregate.InnerExceptions[1], close))
                    throw new InvalidOperationException("both causal exceptions must survive");
            }
            else if (!ReferenceEquals(observed, bodyFails ? body : closeFails ? close : null))
                throw new InvalidOperationException("original operation/cleanup failure must survive");
        }
        Console.WriteLine("csharp_sample_cleanup_controls cases=4");
    }
}
