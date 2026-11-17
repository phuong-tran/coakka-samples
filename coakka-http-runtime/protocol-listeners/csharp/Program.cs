using System.Text;
using CoAkka.Http;

if (args.Length == 0 || args[0] is not ("http1" or "http2" or "http3" or "multi") ||
    (args[0] == "http1" ? args.Length != 1 : args.Length != 2))
{
    Console.Error.WriteLine("Usage: Server http1 | Server <http2|http3|multi> <certificate-directory>");
    return 2;
}

const string address = "127.0.0.1";
const ulong helloBinding = 1;
string mode = args[0];
ushort port = mode == "http1" ? (ushort)8080 : (ushort)8443;
TlsOptions? tls = mode == "http1" ? null : new TlsOptions(
    "sample", 1, Path.Combine(args[1], "server.pem"), Path.Combine(args[1], "server.key"));
var listener = mode switch
{
    "multi" => HttpListenerOptions.MultiProtocolListener(tls!, bindAddress: address, port: port),
    "http2" => HttpListenerOptions.Standalone(HttpProtocol.Http2, bindAddress: address, port: port, tls: tls),
    "http3" => HttpListenerOptions.Standalone(HttpProtocol.Http3, bindAddress: address, port: port, tls: tls),
    _ => HttpListenerOptions.Standalone(HttpProtocol.Http11, bindAddress: address, port: port),
};

using var service = new HttpService(listener, [new HttpRoute(1, helloBinding, "GET", "/hello")]);
service.Start();
Console.WriteLine($"Listening on {(tls is null ? "http" : "https")}://{address}:{service.Port}/hello");

int remaining = mode == "multi" ? 3 : 1;
byte[] body = Encoding.UTF8.GetBytes("hello");
HttpField[] headers = [new("content-type", "text/plain; charset=utf-8")];
while (remaining > 0)
{
    var request = service.Take(250);
    if (request is not { Kind: EventKind.Request, BindingId: helloBinding })
        continue;

    var status = service.Respond(request.Exchange, 200, body, headers);
    if (status == CoreStatus.StaleExchange)
        continue;
    if (status != CoreStatus.Ok)
        throw new InvalidOperationException($"Response refused: {status}");

    remaining--;
}

service.BeginDrain();
var drain = service.WaitDrained(5000);
service.Stop();
Console.WriteLine($"Drain: {drain.State} ({drain.Status})");
return drain.State == DrainState.Complete ? 0 : 1;
