import CoAkkaHttp
import Foundation

enum SampleError: Error {
    case usage(String)
    case responseRefused(CoreStatus)
    case drainFailed(DrainState, CoreStatus)
}

let args = Array(CommandLine.arguments.dropFirst())
guard let mode = args.first, ["http1", "http2", "http3", "multi"].contains(mode),
      args.count == (mode == "http1" ? 1 : 2) else {
    throw SampleError.usage("Usage: Server http1 | Server <http2|http3|multi> <certificate-directory>")
}

let address = "127.0.0.1"
let port: UInt16 = mode == "http1" ? 8080 : 8443
let listener: HttpListener
if mode == "http1" {
    listener = .standalone(protocol: .http11, address: address, port: port)
} else {
    let certificates = URL(fileURLWithPath: args[1], isDirectory: true)
    let tls = TlsOptions(
        credentialID: "sample", generation: 1,
        certificateChainFile: certificates.appendingPathComponent("server.pem").path,
        privateKeyFile: certificates.appendingPathComponent("server.key").path)
    switch mode {
    case "multi": listener = .multiProtocolListener(tls: tls, address: address, port: port)
    case "http2": listener = .standalone(protocol: .http2, address: address, port: port, tls: tls)
    default: listener = .standalone(protocol: .http3, address: address, port: port, tls: tls)
    }
}

let helloBinding: UInt64 = 1
let route = HttpRoute(routeID: 1, bindingID: helloBinding, method: "GET", pattern: "/hello")
let service = try HttpService(listener: listener, routes: [route])
defer { try? service.close() }
try service.start()
let scheme = mode == "http1" ? "http" : "https"
print("Listening on \(scheme)://\(address):\(try service.port)/hello")

var remaining = mode == "multi" ? 3 : 1
let body = Array("hello".utf8)
let headers = [HttpField("content-type", "text/plain; charset=utf-8")]
while remaining > 0 {
    guard let request = try service.take(timeoutMilliseconds: 250),
          request.kind == .request, request.bindingID == helloBinding else { continue }

    let status = try service.respond(to: request.exchange, status: 200, body: body, headers: headers)
    if status == .staleExchange { continue }
    guard status == .ok else { throw SampleError.responseRefused(status) }
    remaining -= 1
}

try service.beginDrain()
let drain = try service.waitDrained(timeoutMilliseconds: 5000)
try service.stop()
print("Drain: \(drain.state) (\(drain.status))")
guard drain.state == .complete else { throw SampleError.drainFailed(drain.state, drain.status) }
try service.close()
