package coakka.v2.android

import coakka.v2.control.ControlEnvelope
import coakka.v2.control.RouteSnapshotPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeControlEncoderTest {
    @Test
    fun localRouteUsesInProcessPortZero() {
        val encoded = RuntimeControlEncoder.encodeSnapshot(
            generation = 7,
            routes = listOf(AndroidRuntimeRoute.local("svc.echo", "android-node")),
        )

        val envelope = ControlEnvelope.parseFrom(encoded)
        val snapshot = RouteSnapshotPayload.parseFrom(envelope.payload)
        assertEquals(7L, snapshot.generation)
        assertEquals("svc.echo", snapshot.routesList.single().target)
        assertEquals("android-node", snapshot.routesList.single().endpointsList.single().host)
        assertEquals(0, snapshot.routesList.single().endpointsList.single().port)
        assertEquals(
            RuntimeEndpointFlags.LOCAL,
            snapshot.routesList.single().endpointsList.single().flags,
        )
    }
}
