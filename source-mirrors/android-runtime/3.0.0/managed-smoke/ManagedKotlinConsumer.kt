import coakka.v2.android.AndroidRuntimeConfig
import coakka.v2.android.AndroidRuntimeMonitorOptions
import coakka.v2.android.AndroidRuntimeRoute
import coakka.v2.android.AndroidStreamConsumerDecision
import coakka.v2.android.AndroidStreamSourceResult
import coakka.v2.android.RuntimeControlEncoder
import coakka.v2.android.RuntimeNetworkConfig
import coakka.v2.android.RuntimeNetworkMode
import coakka.v2.control.ControlEnvelope
import coakka.v2.control.RouteSnapshotPayload

/** Named/default arguments, data methods, nullability and sealed types stay idiomatic. */
fun main() {
    val absent = AndroidRuntimeConfig(systemName = "system", nodeId = "node")
    check(absent.monitor == null && absent.network == null)
    val zero = RuntimeNetworkConfig(mode = RuntimeNetworkMode.OUTBOUND_ONLY, bindPort = 0)
    check(zero.bindPort == 0 && zero.advertisePort == null)
    val explicit = absent.copy(network = zero, monitor = AndroidRuntimeMonitorOptions(enabled = false))
    val (system, node) = explicit
    check(system == "system" && node == "node" && explicit.monitor?.enabled == false)
    check(RuntimeNetworkMode.entries.contains(RuntimeNetworkMode.EMBEDDED))
    val source: AndroidStreamSourceResult = AndroidStreamSourceResult.WouldBlock
    check(when (source) {
        is AndroidStreamSourceResult.Frame -> false
        AndroidStreamSourceResult.WouldBlock -> true
        AndroidStreamSourceResult.End -> false
    })
    check(AndroidStreamConsumerDecision.entries.isNotEmpty())
    val encoded = RuntimeControlEncoder.encodeSnapshot(
        generation = 7,
        routes = listOf(AndroidRuntimeRoute.local(target = "svc.echo", nodeId = "android-node")),
    )
    val envelope = ControlEnvelope.parseFrom(encoded)
    val snapshot = RouteSnapshotPayload.parseFrom(envelope.payload)
    check(envelope.seq == 7L && envelope.getMetadataOrThrow("source_connector") == "coakka-runtime-android")
    check(snapshot.generation == 7L && snapshot.routesList.single().target == "svc.echo")
    val endpoint = snapshot.routesList.single().endpointsList.single()
    check(endpoint.host == "android-node" && endpoint.port == 0)
    println("PASS_PACKAGED_KOTLIN_API_AND_WIRE")
}
