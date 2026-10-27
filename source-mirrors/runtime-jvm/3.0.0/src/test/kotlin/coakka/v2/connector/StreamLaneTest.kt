package coakka.v2.connector

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamLaneTest {
    @Test
    fun usesTheFrozenCoreAbiThroughThePrivateJniProjection() {
        assertEquals(1, CoakkaRuntimeAbi.VERSION)
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

    @Test
    fun callbackOwnershipStaysBoundedAcrossCoreReplacementAndPruning() {
        val runtime = System.getProperty("coakka.streamLane.runtime.lib")
            ?: System.getenv("COAKKA_STREAM_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current stream-lane runtime not configured")

        StreamLane.open(
            StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER, capacity = 1),
            runtime,
        ).use { publisher ->
            val reusable = "jvm-stream-callback-replacement"
            repeat(2) {
                publisher.preparePublish(
                    StreamPublishSpec(reusable, "replacement-token", 0x5245504c414345L, 1024) {
                        StreamSourceResult.End
                    },
                )
                publisher.cancel(reusable, StreamDirection.PUBLISH)
                assertTrue(waitTerminal(publisher, reusable, StreamDirection.PUBLISH).terminal)
            }

            repeat(32) { index ->
                val sessionId = "jvm-stream-callback-prune-$index"
                publisher.preparePublish(
                    StreamPublishSpec(sessionId, "prune-token", 0x5052554e45L, 1024) {
                        StreamSourceResult.End
                    },
                )
                publisher.cancel(sessionId, StreamDirection.PUBLISH)
                assertTrue(waitTerminal(publisher, sessionId, StreamDirection.PUBLISH).terminal)
            }
            assertEquals(1, publisher.stats().retainedRecords)
            publisher.forget("jvm-stream-callback-prune-31", StreamDirection.PUBLISH)
        }
    }

    @Test
    fun concurrentAcceptedPublishersKeepTheCurrentCallbackAliveAcrossGc() {
        val runtime = System.getProperty("coakka.streamLane.runtime.lib")
            ?: System.getenv("COAKKA_STREAM_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current stream-lane runtime not configured")
        val workers = Executors.newFixedThreadPool(2)
        try {
            StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER, capacity = 1), runtime).use { publisher ->
                StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.SUBSCRIBER, capacity = 1), runtime).use { subscriber ->
                    repeat(8) { round ->
                        val session = "jvm-concurrent-callback-$round"
                        val ready = CountDownLatch(2)
                        val start = CountDownLatch(1)
                        val tasks = (1..2).map { marker ->
                            workers.submit<List<WeakReference<StreamSource>>> {
                                ready.countDown()
                                check(start.await(5, TimeUnit.SECONDS))
                                (0 until 32).map { installProbe(publisher, session, marker) }
                            }
                        }
                        assertTrue(ready.await(5, TimeUnit.SECONDS))
                        start.countDown()
                        val references = tasks.flatMap { it.get(5, TimeUnit.SECONDS) }
                        // Only weak application roots remain. The accepted source
                        // must survive actual VM GC through JNI's global root.
                        repeat(3) { System.gc() }
                        assertTrue(references.any { it.get() != null })
                        val received = ByteArrayOutputStream()
                        subscriber.subscribe(StreamSubscribeSpec(
                            session, "callback-token", "127.0.0.1", publisher.boundPort,
                            1, 64, 128,
                        ) { data, _ ->
                            while (data.hasRemaining()) received.write(data.get().toInt())
                            StreamConsumerDecision.CONTINUE
                        })
                        assertTrue(waitTerminal(subscriber, session, StreamDirection.SUBSCRIBE).completed)
                        assertTrue(waitTerminal(publisher, session, StreamDirection.PUBLISH).completed)
                        val bytes = received.toByteArray()
                        assertEquals(1, bytes.size)
                        assertTrue(bytes[0].toInt() in 1..2)
                        subscriber.forget(session, StreamDirection.SUBSCRIBE)
                        publisher.forget(session, StreamDirection.PUBLISH)
                        assertEquals(0, publisher.stats().retainedRecords)
                    }
                }
            }
        } finally {
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun installProbe(lane: StreamLane, session: String, marker: Int): WeakReference<StreamSource> {
        val sent = AtomicInteger()
        val source = StreamSource { destination ->
            if (sent.getAndIncrement() == 0) {
                destination.put(marker.toByte())
                StreamSourceResult.Frame(1)
            } else {
                StreamSourceResult.End
            }
        }
        lane.preparePublish(StreamPublishSpec(session, "callback-token", 1, 64, source))
        return WeakReference(source)
    }

    @Test
    fun concurrentPreparationAndObservationFinishWhenTheLaneCloses() {
        val runtime = System.getProperty("coakka.streamLane.runtime.lib")
            ?: System.getenv("COAKKA_STREAM_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current stream-lane runtime not configured")
        val workers = Executors.newFixedThreadPool(2)
        val publisher = StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER), runtime)
        try {
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val tasks = (0..1).map { kind ->
                workers.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(64) {
                        try {
                            if (kind == 0) {
                                publisher.preparePublish(StreamPublishSpec("close-race", "close-token", 1, 64) {
                                    StreamSourceResult.End
                                })
                            } else {
                                publisher.stats()
                            }
                        } catch (closed: StreamLaneException) {
                            assertEquals(CoakkaStatus.ERR_CLOSED, closed.status)
                        } catch (closed: IllegalStateException) {
                            // The public facade may reject before native lookup;
                            // an already borrowed call instead observes Core stop.
                            assertEquals("stream lane is closed", closed.message)
                        }
                    }
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            publisher.close()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            publisher.close()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
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
