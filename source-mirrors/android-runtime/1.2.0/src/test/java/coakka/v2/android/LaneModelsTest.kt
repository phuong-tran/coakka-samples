package coakka.v2.android

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneModelsTest {
    @Test
    fun fileGrantPinsOwnerCopiesDigestAndRedactsToken() {
        val secret = "receiver-token"
        val inputDigest = ByteArray(32) { it.toByte() }
        val grant = FileReceiveGrant(
            owner = LaneOwnerEndpoint("receiver-2", "10.0.0.12", 7712),
            transferId = "model-42",
            authorizationToken = secret,
            expectedSize = 42,
            expectedSha256 = inputDigest,
        )

        inputDigest.fill(0)
        val returnedDigest = grant.expectedSha256
        returnedDigest.fill(1)
        val send = grant.toSendSpec(File("model.bin"), timeoutMs = 2500)

        assertEquals("10.0.0.12", send.remoteHost)
        assertEquals(7712, send.remotePort)
        assertEquals("model-42", send.transferId)
        assertEquals(secret, send.authorizationToken)
        assertEquals(31, grant.expectedSha256.last().toInt())
        assertFalse(grant.toString().contains(secret))
        assertFalse(send.toString().contains(secret))
    }

    @Test
    fun streamGrantPinsOwnerAndRedactsToken() {
        val secret = "publisher-token"
        val consumer = AndroidStreamConsumer { _, _ -> AndroidStreamConsumerDecision.CONTINUE }
        val grant = StreamPublishGrant(
            owner = LaneOwnerEndpoint("publisher-3", "10.0.0.23", 7723),
            sessionId = "camera-7",
            authorizationToken = secret,
            formatId = 0x102,
            maxFrameBytes = 4096,
        )

        val subscribe = grant.toSubscribeSpec(
            initialWindowBytes = 8192,
            timeoutMs = 900,
            consumer = consumer,
        )

        assertEquals("10.0.0.23", subscribe.remoteHost)
        assertEquals(7723, subscribe.remotePort)
        assertEquals("camera-7", subscribe.sessionId)
        assertEquals(secret, subscribe.authorizationToken)
        assertFalse(grant.toString().contains(secret))
        assertFalse(subscribe.toString().contains(secret))
    }

    @Test
    fun crossAbiBoundsAndOwnerTextAreValidatedBeforeJni() {
        assertThrows(IllegalArgumentException::class.java) {
            FileLaneConfig(queueCapacity = Int.MAX_VALUE.toLong() + 1).requireValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileReceiveSpec("empty", "token", File("empty.bin"), 0, ByteArray(32)).requireValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            LaneOwnerConfig("replica 1", "10.0.0.1").requireValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StreamLaneConfig(capacity = 65).requireValid()
        }
        val source = AndroidStreamSource { AndroidStreamSourceResult.End }
        val consumer = AndroidStreamConsumer { _, _ -> AndroidStreamConsumerDecision.CONTINUE }
        assertThrows(IllegalArgumentException::class.java) {
            StreamPublishSpec("negative-format", "token", -1, 1024, source).requireValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StreamSubscribeSpec(
                "negative-format",
                "token",
                "127.0.0.1",
                7710,
                -1,
                1024,
                2048,
                consumer = consumer,
            ).requireValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            StreamPublishGrant(
                LaneOwnerEndpoint("owner-1", "127.0.0.1", 7710),
                "negative-format",
                "token",
                -1,
                1024,
            )
        }
    }

    @Test
    fun streamSourceCallbackMapsBorrowedBufferAndOutcomes() {
        val callbackLaneHandle = 0x42L
        val destination = ByteBuffer.allocateDirect(8)
        val metadata = LongArray(4)
        val source = AndroidStreamSource { buffer ->
            assertThrows(IllegalStateException::class.java) {
                StreamCallbackGuard.requireCloseAllowed(callbackLaneHandle)
            }
            StreamCallbackGuard.requireCloseAllowed(callbackLaneHandle + 1)
            buffer.put(byteArrayOf(3, 4, 5))
            AndroidStreamSourceResult.Frame(
                size = 3,
                capturedMonoNs = 17,
                droppedBefore = 2,
                flags = StreamFrameFlags.KEYFRAME,
            )
        }

        assertEquals(
            NativeStatus.OK,
            NativeStreamCallbacks.sourceNext(source, destination, metadata, callbackLaneHandle),
        )
        StreamCallbackGuard.requireCloseAllowed(callbackLaneHandle)
        assertArrayEquals(longArrayOf(17, 2, StreamFrameFlags.KEYFRAME.toLong(), 3), metadata)
        destination.position(0)
        val bytes = ByteArray(3)
        destination.get(bytes)
        assertArrayEquals(byteArrayOf(3, 4, 5), bytes)
        assertEquals(
            NativeStatus.WOULD_BLOCK,
            NativeStreamCallbacks.sourceNext(
                AndroidStreamSource { AndroidStreamSourceResult.WouldBlock },
                ByteBuffer.allocateDirect(1),
                LongArray(4),
                callbackLaneHandle,
            ),
        )
        assertEquals(
            NativeStatus.CLOSED,
            NativeStreamCallbacks.sourceNext(
                AndroidStreamSource { AndroidStreamSourceResult.End },
                ByteBuffer.allocateDirect(1),
                LongArray(4),
                callbackLaneHandle,
            ),
        )
    }

    @Test
    fun streamConsumerCallbackCopiesMetadataAndHonorsStop() {
        val callbackLaneHandle = 0x43L
        var sawReadOnly = false
        var captured: StreamFrameMetadata? = null
        val consumer = AndroidStreamConsumer { data, metadata ->
            assertThrows(IllegalStateException::class.java) {
                StreamCallbackGuard.requireCloseAllowed(callbackLaneHandle)
            }
            StreamCallbackGuard.requireCloseAllowed(callbackLaneHandle + 1)
            sawReadOnly = data.isReadOnly
            captured = metadata
            AndroidStreamConsumerDecision.STOP
        }

        val status = NativeStreamCallbacks.consume(
            consumer,
            ByteBuffer.allocateDirect(4),
            longArrayOf(11, 12, 13, StreamFrameFlags.END_OF_SEGMENT.toLong()),
            callbackLaneHandle,
        )

        assertEquals(NativeStatus.CLOSED, status)
        StreamCallbackGuard.requireCloseAllowed(callbackLaneHandle)
        assertTrue(sawReadOnly)
        assertEquals(
            StreamFrameMetadata(11, 12, 13, StreamFrameFlags.END_OF_SEGMENT),
            captured,
        )
    }
}
