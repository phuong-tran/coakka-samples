import CoAkkaRuntime
import Foundation

enum SampleError: Error { case unexpected(String) }

@main struct RuntimeBasic {
    static func require(_ condition: Bool, _ message: String) throws {
        if !condition { throw SampleError.unexpected(message) }
    }

    static func main() {
        do { try run() }
        catch {
            // CLI exit occurs only after the throwing app scope attempted shutdown.
            FileHandle.standardError.write(Data("coakka_runtime_error \(error)\n".utf8))
            exit(1)
        }
    }

    static func run() throws {
        let system = "swift-runtime-basic", target = "svc.echo", missing = "svc.missing"
        let runtime = try RuntimeHost.startLocal(systemName: system, target: target)
        try SampleScope.run({
            try runtime.registerTextHandler(target) { "echo-\($0)" }
            let response = try runtime.askText(source: system, target: target,
                payload: "hello-runtime-swift", timeoutMs: 2_000)
            var routeMiss: RuntimeDeadletter?
            do {
                _ = try runtime.askText(source: system, target: missing, payload: "missing", timeoutMs: 2_000)
            } catch RuntimeError.deadletter(let value) { routeMiss = value }

            let info = try runtime.runtimeInfo(), config = try runtime.runtimeConfig()
            let health = try runtime.health(), startup = try runtime.startupConfig()
            let delivery = try runtime.deliveryConfig(), stats = try runtime.stats()
            let client = runtime.clientStats()
            try require(info.abiVersion == 1 && config.systemName == system && config.routeCount == 1, "Core identity/routes")
            try require(config.runtimeState == .started && health.runtimeState == .started, "Core started state")
            try require(startup.execution.requestedPolicy == 0 && startup.connection.mode == .multiplexing
                && !startup.monitorEnabled, "Core AUTO/MULTIPLEX/monitor facts")
            try require(delivery.requestMode == 2 && delivery.responseMode == 2, "Core delivery facts")
            try require(startup.queueCapacity > 0 && Int64(startup.queueCapacity) == Int64(config.queueCapacity)
                && Int64(startup.queueCapacity) == Int64(stats.ingressQueueCapacity), "Core queue snapshots")
            try require(response == "echo-hello-runtime-swift", "response payload")
            try require(routeMiss?.reasonCode == 2 && routeMiss?.originalTarget == missing, "Core typed route miss")
            try require(client.deliveredRequests == 1 && client.matchedResponses == 1
                && client.matchedDeadletters == 1, "observed request outcomes")
            print("coakka_runtime_info abi=\(info.abiVersion) version=\(info.runtimeVersion) git=\(info.gitCommit)")
            print("coakka_runtime_response payload=\(response)")
            print("coakka_runtime_startup auto=\(startup.execution.requestedPolicy) connection=\(startup.connection.mode.rawValue) monitor=\(startup.monitorEnabled) queueCapacity=\(startup.queueCapacity)")
            print("coakka_runtime_stats delivered=\(client.deliveredRequests) matchedResponses=\(client.matchedResponses) matchedDeadletters=\(client.matchedDeadletters)")
        }, close: runtime.close)
    }
}
