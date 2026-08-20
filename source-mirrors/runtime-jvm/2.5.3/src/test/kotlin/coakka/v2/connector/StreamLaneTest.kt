package coakka.v2.connector

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamLaneTest {
    @Test
    fun nativeLayoutsMatchPublic64BitAbi() {
        assertEquals(48, NativeStreamFrame().size())
        assertEquals(56, NativeStreamLaneSecurityConfig().size())
        assertEquals(96, NativeStreamLaneConfig().size())
        assertEquals(56, NativeStreamPublishSpec().size())
        assertEquals(80, NativeStreamSubscribeSpec().size())
        assertEquals(280, NativeStreamSessionSnapshot().size())
        assertEquals(152, NativeStreamLaneStats().size())
        assertEquals(120, NativeStreamPressureSnapshot().size())
        assertEquals(24, NativeLaneOwnerConfig().size())
        assertEquals(400, NativeLaneOwnerEndpoint().size())
        assertEquals(128, NativeStreamLaneOwnedConfig().size())
        assertEquals(624, NativeStreamPublishGrant().size())
    }

    @Test
    fun publicConnectorStreamsFramesAndReportsPressure() {
        val runtime = System.getProperty("coakka.streamLane.runtime.lib")
            ?: System.getenv("COAKKA_STREAM_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current stream-lane runtime not configured")

        val sourceBytes = ByteArray(2 * 1024 * 1024 + 731) { index -> ((index * 31 + 17) and 0xff).toByte() }
        val offset = AtomicInteger()
        val received = ByteArrayOutputStream(sourceBytes.size)
        val maxFrameBytes = 64 * 1024
        val sessionId = "jvm-stream-lane-roundtrip"
        val token = "jvm-stream-lane-token"
        val formatId = 0x4a564d5354524541L

        StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER), runtime).use { publisher ->
            StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.SUBSCRIBER), runtime).use { subscriber ->
                publisher.preparePublish(
                    StreamPublishSpec(sessionId, token, formatId, maxFrameBytes) { destination ->
                        val start = offset.get()
                        if (start >= sourceBytes.size) {
                            StreamSourceResult.End
                        } else {
                            val count = min(destination.remaining(), sourceBytes.size - start)
                            destination.put(sourceBytes, start, count)
                            offset.addAndGet(count)
                            StreamSourceResult.Frame(count, droppedBefore = if (start == 0) 2 else 0)
                        }
                    },
                )
                subscriber.subscribe(
                    StreamSubscribeSpec(
                        sessionId, token, "127.0.0.1", publisher.boundPort, formatId,
                        maxFrameBytes, maxFrameBytes * 2,
                    ) { data, _ ->
                        val bytes = ByteArray(data.remaining())
                        data.get(bytes)
                        received.write(bytes)
                        StreamConsumerDecision.CONTINUE
                    },
                )

                val subscribed = waitTerminal(subscriber, sessionId, StreamDirection.SUBSCRIBE)
                val published = waitTerminal(publisher, sessionId, StreamDirection.PUBLISH)
                assertTrue(subscribed.completed, subscribed.detail)
                assertTrue(published.completed, published.detail)
                assertEquals(sourceBytes.size.toLong(), published.bytes)
                assertEquals(sourceBytes.size.toLong(), subscribed.bytes)
                assertEquals(2, published.droppedFrames)
                assertEquals(2, subscribed.droppedFrames)
                assertContentEquals(sourceBytes, received.toByteArray())
                assertEquals(StreamPressureState.INACTIVE, subscriber.pressure(sessionId, StreamDirection.SUBSCRIBE).state)
                assertEquals(published.frames, publisher.stats().publishedFrames)
                assertEquals(subscribed.frames, subscriber.stats().consumedFrames)

                subscriber.forget(sessionId, StreamDirection.SUBSCRIBE)
                publisher.forget(sessionId, StreamDirection.PUBLISH)
            }
        }
    }

    @Test
    fun ownerGrantPinsPublisherAndCompletesStream() {
        val runtime = System.getProperty("coakka.streamLane.runtime.lib")
            ?: System.getenv("COAKKA_STREAM_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current stream-lane runtime not configured")

        val sourceBytes = ByteArray(256 * 1024 + 23) { index -> ((index * 13 + 5) and 0xff).toByte() }
        val offset = AtomicInteger()
        val received = ByteArrayOutputStream(sourceBytes.size)
        val maxFrameBytes = 32 * 1024
        val token = "stream-owner-grant-secret"

        StreamLane.openOwned(
            StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER),
            LaneOwnerConfig("camera-3", "127.0.0.1"),
            runtime,
        ).use { publisher ->
            StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.SUBSCRIBER), runtime).use { subscriber ->
                val grant = publisher.preparePublishGrant(
                    StreamPublishSpec("stream-owner-grant", token, 0x53545245414dL, maxFrameBytes) { destination ->
                        val start = offset.get()
                        if (start >= sourceBytes.size) {
                            StreamSourceResult.End
                        } else {
                            val count = min(destination.remaining(), sourceBytes.size - start)
                            destination.put(sourceBytes, start, count)
                            offset.addAndGet(count)
                            StreamSourceResult.Frame(count)
                        }
                    },
                )
                assertEquals("camera-3", grant.owner.ownerInstanceId)
                assertEquals("127.0.0.1", grant.owner.advertisedHost)
                assertEquals(publisher.boundPort, grant.owner.port)
                assertFalse(grant.toString().contains(token))
                val receivedGrant = StreamPublishGrant(
                    grant.owner,
                    grant.sessionId,
                    grant.authorizationToken,
                    grant.formatId,
                    grant.maxFrameBytes,
                )
                subscriber.subscribe(receivedGrant.toSubscribeSpec(maxFrameBytes * 2) { data, _ ->
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    received.write(bytes)
                    StreamConsumerDecision.CONTINUE
                })
                assertTrue(waitTerminal(subscriber, grant.sessionId, StreamDirection.SUBSCRIBE).completed)
                assertTrue(waitTerminal(publisher, grant.sessionId, StreamDirection.PUBLISH).completed)
                assertContentEquals(sourceBytes, received.toByteArray())
            }
        }
    }

    private fun waitTerminal(lane: StreamLane, sessionId: String, direction: StreamDirection): StreamSessionSnapshot {
        var sequence = 0L
        repeat(128) {
            val snapshot = lane.waitSession(sessionId, direction, sequence, 30_000)
            if (snapshot.terminal) return snapshot
            sequence = snapshot.updateSequence
        }
        error("stream session did not reach a terminal state")
    }
}
