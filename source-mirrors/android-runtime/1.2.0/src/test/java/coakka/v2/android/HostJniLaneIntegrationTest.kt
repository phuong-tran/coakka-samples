package coakka.v2.android

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class HostJniLaneIntegrationTest {
    @Before
    fun requireExplicitHostJniRun() {
        assumeTrue(System.getProperty(HOST_JNI_PROPERTY) == "true")
    }

    @Test
    fun grantProjectionFailureCancelsForgetsAndAllowsSameIdentityRetry() {
        val directory = Files.createTempDirectory("coakka-android-host-jni").toFile()
        val destination = File(directory, "receive.bin")
        val digest = ByteArray(32) { it.toByte() }

        try {
            FileLane.openOwned(
                FileLaneConfig(flags = FileLaneFlags.RECEIVER),
                LaneOwnerConfig("file-owner-2", "127.0.0.1"),
            ).use { lane ->
                val spec = FileReceiveSpec("projection-file", "file-token", destination, 64, digest)
                HostJniTestBridge.nativeFailTextWriteAfter(0)
                val failure = assertThrows(FileLaneException::class.java) { lane.prepareReceiveGrant(spec) }
                assertEquals(NATIVE_NOMEM, failure.status)
                assertEquals(0, lane.stats().preparedReceives)
                assertEquals(0, lane.stats().retainedRecords)

                val retry = lane.prepareReceiveGrant(spec)
                assertEquals("file-owner-2", retry.owner.ownerInstanceId)
                lane.cancel(spec.transferId, FileTransferDirection.RECEIVE)
                lane.forget(spec.transferId, FileTransferDirection.RECEIVE)
                assertEquals(0, lane.stats().retainedRecords)
            }

            val source = AndroidStreamSource { AndroidStreamSourceResult.End }
            StreamLane.openOwned(
                StreamLaneConfig(
                    flags = StreamLaneFlags.PUBLISHER,
                    maxFrameBytes = 1024,
                    maxWindowBytes = 4096,
                ),
                LaneOwnerConfig("stream-owner-3", "127.0.0.1"),
            ).use { lane ->
                val spec = StreamPublishSpec("projection-stream", "stream-token", 0x102, 1024, source)
                HostJniTestBridge.nativeFailTextWriteAfter(0)
                val failure = assertThrows(StreamLaneException::class.java) { lane.preparePublishGrant(spec) }
                assertEquals(NATIVE_NOMEM, failure.status)
                assertEquals(0, lane.stats().preparedPublishers)
                assertEquals(0, lane.stats().retainedRecords)

                val retry = lane.preparePublishGrant(spec)
                assertEquals("stream-owner-3", retry.owner.ownerInstanceId)
                lane.cancel(spec.sessionId, StreamDirection.PUBLISH)
                lane.forget(spec.sessionId, StreamDirection.PUBLISH)
                assertEquals(0, lane.stats().retainedRecords)
            }
        } finally {
            HostJniTestBridge.nativeFailTextWriteAfter(-1)
            directory.deleteRecursively()
        }
    }

    @Test
    fun ownerPinnedStreamRunsCallbacksAndSameLaneCloseFailsFast() {
        val payload = "host-jni-frame".toByteArray()
        val produced = AtomicBoolean(false)
        val sourceCloseRejected = AtomicBoolean(false)
        val consumerCloseRejected = AtomicBoolean(false)
        val consumed = AtomicReference<ByteArray>()
        lateinit var publisher: StreamLane
        lateinit var subscriber: StreamLane

        val source = AndroidStreamSource { destination ->
            try {
                publisher.close()
                error("same-lane publisher close unexpectedly returned")
            } catch (expected: IllegalStateException) {
                sourceCloseRejected.set(expected.message?.contains("callback") == true)
            }
            if (produced.compareAndSet(false, true)) {
                destination.put(payload)
                AndroidStreamSourceResult.Frame(
                    size = payload.size,
                    capturedMonoNs = System.nanoTime(),
                    flags = StreamFrameFlags.KEYFRAME or StreamFrameFlags.END_OF_SEGMENT,
                )
            } else {
                AndroidStreamSourceResult.End
            }
        }
        val consumer = AndroidStreamConsumer { data, _ ->
            try {
                subscriber.close()
                error("same-lane subscriber close unexpectedly returned")
            } catch (expected: IllegalStateException) {
                consumerCloseRejected.set(expected.message?.contains("callback") == true)
            }
            ByteArray(data.remaining()).also { frame ->
                data.get(frame)
                consumed.set(frame)
            }
            AndroidStreamConsumerDecision.CONTINUE
        }

        publisher = StreamLane.openOwned(
            StreamLaneConfig(
                flags = StreamLaneFlags.PUBLISHER,
                maxFrameBytes = 1024,
                maxWindowBytes = 4096,
            ),
            LaneOwnerConfig("host-publisher-1", "127.0.0.1"),
        )
        publisher.use { livePublisher ->
            subscriber = StreamLane.open(
                StreamLaneConfig(
                    flags = StreamLaneFlags.SUBSCRIBER,
                    maxFrameBytes = 1024,
                    maxWindowBytes = 4096,
                ),
            )
            subscriber.use { liveSubscriber ->
                val sessionId = "host-callback-stream"
                val grant = livePublisher.preparePublishGrant(
                    StreamPublishSpec(sessionId, "host-stream-token", 0x103, 1024, source),
                )
                liveSubscriber.subscribe(grant.toSubscribeSpec(4096, consumer = consumer))

                val publish = awaitStreamTerminal(livePublisher, sessionId, StreamDirection.PUBLISH)
                val subscribe = awaitStreamTerminal(liveSubscriber, sessionId, StreamDirection.SUBSCRIBE)
                assertTrue("publisher did not end cleanly: $publish", publish.completed)
                assertTrue("subscriber did not end cleanly: $subscribe", subscribe.completed)
                assertTrue("source callback did not reject same-lane close", sourceCloseRejected.get())
                assertTrue("consumer callback did not reject same-lane close", consumerCloseRejected.get())
                assertArrayEquals(payload, consumed.get())
                livePublisher.forget(sessionId, StreamDirection.PUBLISH)
                liveSubscriber.forget(sessionId, StreamDirection.SUBSCRIBE)
            }
        }
    }

    @Test
    fun boundedFileLoopbackAndLaneReopenCyclesReturnToBaseline() {
        val directory = Files.createTempDirectory("coakka-android-host-file-stress").toFile()
        val source = File(directory, "source.bin")
        val payload = ByteArray(4096) { (it and 0xff).toByte() }
        source.writeBytes(payload)
        val digest = FileLane.sha256(source)

        try {
            FileLane.openOwned(
                FileLaneConfig(flags = FileLaneFlags.RECEIVER, queueCapacity = 8),
                LaneOwnerConfig("file-stress-owner", "127.0.0.1"),
            ).use { receiver ->
                FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER, queueCapacity = 8)).use { sender ->
                    repeat(STRESS_ITERATIONS) { iteration ->
                        val transferId = "host-file-stress-$iteration"
                        val destination = File(directory, "destination-$iteration.bin")
                        val grant = receiver.prepareReceiveGrant(
                            FileReceiveSpec(
                                transferId,
                                "host-file-token-$iteration",
                                destination,
                                digest.size,
                                digest.sha256,
                            ),
                        )
                        sender.submitSend(grant.toSendSpec(source, timeoutMs = 5_000))

                        val send = awaitFileTerminal(sender, transferId, FileTransferDirection.SEND)
                        val receive = awaitFileTerminal(receiver, transferId, FileTransferDirection.RECEIVE)
                        assertTrue("sender did not complete: $send", send.completed)
                        assertTrue("receiver did not complete: $receive", receive.completed)
                        assertArrayEquals(payload, destination.readBytes())

                        sender.forget(transferId, FileTransferDirection.SEND)
                        receiver.forget(transferId, FileTransferDirection.RECEIVE)
                        assertEquals(0, sender.stats().retainedRecords)
                        assertEquals(0, receiver.stats().retainedRecords)
                    }
                }
            }

            repeat(STRESS_ITERATIONS) {
                FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER)).close()
                StreamLane.open(StreamLaneConfig(flags = StreamLaneFlags.SUBSCRIBER)).close()
            }
            assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun streamPreparedActiveAndCloseCleanupReleaseCallbackRefs() {
        assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
        val publisherConfig = StreamLaneConfig(
            flags = StreamLaneFlags.PUBLISHER,
            capacity = 8,
            maxFrameBytes = 1024,
            maxWindowBytes = 4096,
            ioTimeoutMs = 2_000,
            sourceRetryMs = 1,
        )
        StreamLane.openOwned(
            publisherConfig,
            LaneOwnerConfig("stream-cleanup-owner", "127.0.0.1"),
        ).use { publisher ->
            repeat(STRESS_ITERATIONS) { iteration ->
                val sessionId = "host-prepared-cancel-$iteration"
                publisher.preparePublishGrant(
                    StreamPublishSpec(
                        sessionId,
                        "host-prepared-token-$iteration",
                        0x200L + iteration,
                        1024,
                    ) { AndroidStreamSourceResult.WouldBlock },
                )
                assertEquals(1, HostJniTestBridge.nativeRetainedStreamCallbacks())
                publisher.cancel(sessionId, StreamDirection.PUBLISH)
                assertEquals(StreamState.CANCELED, publisher.session(sessionId, StreamDirection.PUBLISH).state)
                publisher.forget(sessionId, StreamDirection.PUBLISH)
                assertEquals(0, publisher.stats().retainedRecords)
                assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
            }
        }

        val source = AndroidStreamSource { AndroidStreamSourceResult.WouldBlock }
        StreamLane.openOwned(
            publisherConfig,
            LaneOwnerConfig("stream-active-owner", "127.0.0.1"),
        ).use { publisher ->
            StreamLane.open(
                StreamLaneConfig(
                    flags = StreamLaneFlags.SUBSCRIBER,
                    capacity = 8,
                    maxFrameBytes = 1024,
                    maxWindowBytes = 4096,
                    ioTimeoutMs = 2_000,
                ),
            ).use { subscriber ->
                val sessionId = "host-active-cancel"
                val grant = publisher.preparePublishGrant(
                    StreamPublishSpec(sessionId, "host-active-token", 0x301, 1024, source),
                )
                subscriber.subscribe(
                    grant.toSubscribeSpec(
                        4096,
                        consumer = AndroidStreamConsumer { _, _ ->
                            AndroidStreamConsumerDecision.CONTINUE
                        },
                        timeoutMs = 5_000,
                    ),
                )
                awaitStreamState(publisher, sessionId, StreamDirection.PUBLISH, StreamState.ACTIVE)
                awaitStreamState(subscriber, sessionId, StreamDirection.SUBSCRIBE, StreamState.ACTIVE)
                assertEquals(2, HostJniTestBridge.nativeRetainedStreamCallbacks())

                publisher.cancel(sessionId, StreamDirection.PUBLISH)
                subscriber.cancel(sessionId, StreamDirection.SUBSCRIBE)
                assertTrue(awaitStreamTerminal(publisher, sessionId, StreamDirection.PUBLISH).terminal)
                assertTrue(awaitStreamTerminal(subscriber, sessionId, StreamDirection.SUBSCRIBE).terminal)
                awaitStreamForget(publisher, sessionId, StreamDirection.PUBLISH)
                awaitStreamForget(subscriber, sessionId, StreamDirection.SUBSCRIBE)
                assertEquals(0, publisher.stats().retainedRecords)
                assertEquals(0, subscriber.stats().retainedRecords)
                assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
            }
        }

        repeat(STRESS_ITERATIONS) { iteration ->
            StreamLane.openOwned(
                publisherConfig,
                LaneOwnerConfig("stream-close-owner-$iteration", "127.0.0.1"),
            ).use { publisher ->
                publisher.preparePublishGrant(
                    StreamPublishSpec(
                        "host-close-$iteration",
                        "host-close-token-$iteration",
                        0x400L + iteration,
                        1024,
                    ) { AndroidStreamSourceResult.WouldBlock },
                )
                assertEquals(1, HostJniTestBridge.nativeRetainedStreamCallbacks())
            }
            assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
        }
    }

    @Test
    fun streamCallbackExceptionsFailClosedAndReleaseCallbackRefs() {
        runCallbackFailureSession(
            sessionId = "host-source-failure",
            source = AndroidStreamSource { error("source failure") },
            consumer = AndroidStreamConsumer { _, _ -> AndroidStreamConsumerDecision.CONTINUE },
            expectedPublisherResult = StreamResult.SOURCE_ERROR,
        )

        val emitted = AtomicBoolean(false)
        runCallbackFailureSession(
            sessionId = "host-consumer-failure",
            source = AndroidStreamSource { destination ->
                if (emitted.compareAndSet(false, true)) {
                    destination.put(1)
                    AndroidStreamSourceResult.Frame(1)
                } else {
                    AndroidStreamSourceResult.WouldBlock
                }
            },
            consumer = AndroidStreamConsumer { _, _ -> error("consumer failure") },
            // The decoded consumer Terminal may win, or peer close may make
            // the locally requested cancel the first stable terminal result.
            expectedSubscriberResults = setOf(
                StreamResult.CONSUMER_ERROR,
                StreamResult.CANCELED_BY_HOST,
            ),
        )
        assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
    }

    private fun runCallbackFailureSession(
        sessionId: String,
        source: AndroidStreamSource,
        consumer: AndroidStreamConsumer,
        expectedPublisherResult: StreamResult? = null,
        expectedSubscriberResults: Set<StreamResult>? = null,
    ) {
        StreamLane.openOwned(
            StreamLaneConfig(
                flags = StreamLaneFlags.PUBLISHER,
                maxFrameBytes = 1024,
                maxWindowBytes = 4096,
                ioTimeoutMs = 2_000,
                sourceRetryMs = 1,
            ),
            LaneOwnerConfig("$sessionId-owner", "127.0.0.1"),
        ).use { publisher ->
            StreamLane.open(
                StreamLaneConfig(
                    flags = StreamLaneFlags.SUBSCRIBER,
                    maxFrameBytes = 1024,
                    maxWindowBytes = 4096,
                    ioTimeoutMs = 2_000,
                ),
            ).use { subscriber ->
                val grant = publisher.preparePublishGrant(
                    StreamPublishSpec(sessionId, "$sessionId-token", 0x501, 1024, source),
                )
                subscriber.subscribe(grant.toSubscribeSpec(4096, consumer, timeoutMs = 5_000))
                val publish = awaitStreamTerminal(publisher, sessionId, StreamDirection.PUBLISH)
                val subscribe = awaitStreamTerminal(subscriber, sessionId, StreamDirection.SUBSCRIBE)
                expectedPublisherResult?.let { assertEquals(it, publish.result) }
                expectedSubscriberResults?.let { expected ->
                    assertTrue(
                        "unexpected subscriber result ${subscribe.result}; expected one of $expected",
                        subscribe.result in expected,
                    )
                }
                awaitStreamForget(publisher, sessionId, StreamDirection.PUBLISH)
                awaitStreamForget(subscriber, sessionId, StreamDirection.SUBSCRIBE)
                assertEquals(0, publisher.stats().retainedRecords)
                assertEquals(0, subscriber.stats().retainedRecords)
                assertEquals(0, HostJniTestBridge.nativeRetainedStreamCallbacks())
            }
        }
    }

    private fun awaitFileTerminal(
        lane: FileLane,
        transferId: String,
        direction: FileTransferDirection,
    ): FileTransferSnapshot {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        var snapshot = lane.transfer(transferId, direction)
        while (!snapshot.terminal && System.nanoTime() < deadline) {
            Thread.sleep(10)
            snapshot = lane.transfer(transferId, direction)
        }
        return snapshot
    }

    private fun awaitStreamForget(
        lane: StreamLane,
        sessionId: String,
        direction: StreamDirection,
    ) {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (true) {
            try {
                lane.forget(sessionId, direction)
                return
            } catch (failure: StreamLaneException) {
                if (failure.status != NativeStatus.BAD_STATE || System.nanoTime() >= deadline) throw failure
                Thread.sleep(5)
            }
        }
    }

    private fun awaitStreamState(
        lane: StreamLane,
        sessionId: String,
        direction: StreamDirection,
        expected: StreamState,
    ): StreamSessionSnapshot {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        var snapshot = lane.session(sessionId, direction)
        while (snapshot.state != expected && !snapshot.terminal && System.nanoTime() < deadline) {
            Thread.sleep(10)
            snapshot = lane.session(sessionId, direction)
        }
        assertEquals("session did not reach $expected: $snapshot", expected, snapshot.state)
        return snapshot
    }

    private fun awaitStreamTerminal(
        lane: StreamLane,
        sessionId: String,
        direction: StreamDirection,
    ): StreamSessionSnapshot {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        var snapshot = lane.session(sessionId, direction)
        while (!snapshot.terminal && System.nanoTime() < deadline) {
            Thread.sleep(10)
            snapshot = lane.session(sessionId, direction)
        }
        return snapshot
    }

    private companion object {
        const val HOST_JNI_PROPERTY = "coakka.android.hostJni"
        const val NATIVE_NOMEM = -2
        const val STRESS_ITERATIONS = 8
        const val TIMEOUT_NANOS = 15_000_000_000L

        @JvmStatic
        @AfterClass
        fun verifyNoRecoverableNativeLeaks() {
            if (System.getProperty(HOST_LSAN_PROPERTY) == "true") {
                assertEquals(0, HostJniTestBridge.nativeRecoverableLeakCheck())
            }
        }

        const val HOST_LSAN_PROPERTY = "coakka.android.hostLsan"
    }
}
