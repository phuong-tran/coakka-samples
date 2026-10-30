import Foundation
import CoAkkaRuntime

enum SampleFailure: Error { case unexpectedReply }
struct OperationAndCloseFailure: Error {
    let operation: any Error
    let close: any Error
}

@main
enum CoAkkaFirstRun {
    static func main() throws {
        let target = "samples.echo"
        let host = try RuntimeHost.startLocal(systemName: "swift-first-run", target: target)
        var operationFailure: (any Error)?
        do {
            try host.registerPayloadHandler(target) { input in
                var result = Data("accepted-".utf8)
                result.append(input)
                return result
            }
            let reply = try host.askPayload(source: "first-run", target: target,
                payload: Data("hello".utf8), timeoutMs: 2000)
            guard reply == Data("accepted-hello".utf8) else { throw SampleFailure.unexpectedReply }
            print(String(decoding: reply, as: UTF8.self))
        } catch {
            operationFailure = error
        }
        do {
            try host.close()
        } catch {
            if let operationFailure {
                throw OperationAndCloseFailure(operation: operationFailure, close: error)
            }
            throw error
        }
        if let operationFailure { throw operationFailure }
    }
}
