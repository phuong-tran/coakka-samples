import CoAkkaRuntime

let runtime = try RuntimeHost.start(
    ConnectorStartSpec(
        systemName: "swift-runtime-basic",
        nodeID: "swift-runtime-basic-node",
        queueCapacity: 64,
        routes: [.local("svc.echo", port: 19193)]
    )
)
defer {
    runtime.close()
}

try runtime.registerTextHandler("svc.echo") { request in
    "echo-\(request)"
}

let response = try runtime.askText(
    source: "swift-runtime-basic",
    target: "svc.echo",
    payload: "hello-runtime-swift",
    timeoutMs: 2_000,
    deliveryHint: .requireLocal
)

let info = try runtime.runtimeInfo()
let stats = runtime.clientStats()
print("coakka_runtime_info abi=\(info.abiVersion) version=\(info.runtimeVersion) git=\(info.gitCommit)")
print("coakka_runtime_response payload=\(response)")
print("coakka_runtime_stats delivered=\(stats.deliveredRequests) matchedResponses=\(stats.matchedResponses)")
