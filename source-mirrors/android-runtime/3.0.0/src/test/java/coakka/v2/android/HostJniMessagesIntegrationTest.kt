package coakka.v2.android

import java.lang.reflect.InvocationTargetException
import java.nio.charset.CharacterCodingException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class HostJniMessagesIntegrationTest {
    @Before
    fun requireHost() {
        assumeTrue(System.getProperty("coakka.android.hostJni") == "true")
    }

    private fun request(payload: ByteArray = byteArrayOf(0, -1, 7), oneWay: Boolean = false) =
        AndroidRuntimeMessages.request("id-😀", "client-😀", "svc.😀", if (oneWay) "" else "reply-😀", payload, 5000, oneWay)

    private fun noOwners() = assertArrayEquals(longArrayOf(0, 0), HostJniTestBridge.nativeMessageOwnerCounts())

    @Test
    fun publicMessagesPreserveUnicodeEmptyPayloadsAndIndependentCopies() {
        val input = byteArrayOf(0, -1, 7)
        val frame = request(input)
        input[0] = 99
        val values = AndroidRuntimeMessages.decodeRequest(frame)
        assertEquals("svc.😀", values.target)
        assertFalse(values.oneWay)
        assertArrayEquals(byteArrayOf(0, -1, 7), values.payload)
        values.payload.fill(55)
        assertArrayEquals(byteArrayOf(0, -1, 7), AndroidRuntimeMessages.decodeRequest(frame).payload)
        val replyInput = byteArrayOf(8, 0, -128)
        val reply = AndroidRuntimeMessages.reply(frame, "handler-😀", replyInput)
        replyInput.fill(99)
        val payload = AndroidRuntimeMessages.responsePayload(reply)
        assertArrayEquals(byteArrayOf(8, 0, -128), payload)
        payload.fill(1)
        assertArrayEquals(byteArrayOf(8, 0, -128), AndroidRuntimeMessages.responsePayload(reply))
        assertArrayEquals(byteArrayOf(), AndroidRuntimeMessages.decodeRequest(request(byteArrayOf())).payload)
        assertArrayEquals(byteArrayOf(), AndroidRuntimeMessages.responsePayload(AndroidRuntimeMessages.reply(frame, "svc", byteArrayOf())))
        val oneWay = request(oneWay = true)
        assertTrue(AndroidRuntimeMessages.decodeRequest(oneWay).oneWay)
        assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) {
            AndroidRuntimeMessages.reply(oneWay, "svc", byteArrayOf())
        }.status)
        noOwners()
    }

    @Test
    fun representationFailuresStayJavaExceptionsAndCoreRefusalsKeepStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidRuntimeMessages.request("id", "source", "target", "reply", byteArrayOf(), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidRuntimeMessages.request("id\u0000x", "source", "target", "reply", byteArrayOf(), 1)
        }
        for (bad in listOf("\ud800", "\udc00")) {
            assertThrows(CharacterCodingException::class.java) {
                AndroidRuntimeMessages.request(bad, "source", "target", "reply", byteArrayOf(), 1)
            }
        }
        val refusal = assertThrows(CoAkkaNativeException::class.java) {
            AndroidRuntimeMessages.request("", "source", "target", "reply", byteArrayOf(), 1)
        }
        assertEquals(-1, refusal.status)
        assertTrue(refusal.message!!.startsWith("message_request "))
        assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) {
            AndroidRuntimeMessages.request("id", "source", "target", "reply", byteArrayOf(), 1, true)
        }.status)
        noOwners()
    }

    @Test
    fun malformedFramesAndWrongKindsAreRejectedByCore() {
        for (bad in listOf(byteArrayOf(), byteArrayOf(-128), byteArrayOf(-1, -1))) {
            assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) { AndroidRuntimeMessages.decodeRequest(bad) }.status)
            assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) { AndroidRuntimeMessages.reply(bad, "svc", byteArrayOf()) }.status)
            assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) { AndroidRuntimeMessages.responsePayload(bad) }.status)
        }
        val frame = request()
        val response = AndroidRuntimeMessages.reply(frame, "svc", byteArrayOf())
        assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) { AndroidRuntimeMessages.responsePayload(frame) }.status)
        assertEquals(-1, assertThrows(CoAkkaNativeException::class.java) { AndroidRuntimeMessages.decodeRequest(response) }.status)
        noOwners()
    }

    @Test
    fun directJniMisuseCannotReadPastArraysOrCStrings() {
        val text = byteArrayOf(65, 0)
        for (bad in listOf(byteArrayOf(), byteArrayOf(65), byteArrayOf(65, 0, 66, 0))) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeRuntimeBridge.nativeBuildMessageRequest(bad, text, text, text, byteArrayOf(), 1, false, IntArray(1))
            }
            noOwners()
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRuntimeBridge.nativeBuildMessageRequest(text, text, text, text, byteArrayOf(), -1, false, IntArray(1))
        }
        assertThrows(IllegalArgumentException::class.java) { NativeRuntimeBridge.nativeReadMessageRequest(request(), IntArray(1)) }
        val method = NativeRuntimeBridge::class.java.getDeclaredMethod("nativeMessageResponsePayload", ByteArray::class.java, IntArray::class.java)
        assertTrue(assertThrows(InvocationTargetException::class.java) { method.invoke(NativeRuntimeBridge, null, IntArray(1)) }.cause is IllegalArgumentException)
        assertTrue(assertThrows(InvocationTargetException::class.java) { method.invoke(NativeRuntimeBridge, request(), null) }.cause is IllegalArgumentException)
        noOwners()
    }

    @Test
    fun publicationAndBorrowFailuresReleaseNativeOutputsAndJavaBorrows() {
        val frame = request()
        for (point in listOf(1, 2)) {
            HostJniTestBridge.nativeFailMessageAt(point)
            assertThrows(OutOfMemoryError::class.java) { request() }
            noOwners()
        }
        for (point in listOf(1, 2, 3, 4)) {
            HostJniTestBridge.nativeFailMessageAt(point)
            assertThrows(OutOfMemoryError::class.java) { AndroidRuntimeMessages.decodeRequest(frame) }
            noOwners()
        }
        HostJniTestBridge.nativeFailMessageAt(2)
        assertThrows(OutOfMemoryError::class.java) { AndroidRuntimeMessages.reply(frame, "svc", byteArrayOf()) }
        noOwners()
        val response = AndroidRuntimeMessages.reply(frame, "svc", byteArrayOf(1))
        HostJniTestBridge.nativeFailMessageAt(2)
        assertThrows(OutOfMemoryError::class.java) { AndroidRuntimeMessages.responsePayload(response) }
        noOwners()
        assertArrayEquals(byteArrayOf(1), AndroidRuntimeMessages.responsePayload(response))
    }

    @Test
    fun statelessMessageCallsKeepOwnersSeparateAcrossConcurrentCallers() {
        val executor = Executors.newFixedThreadPool(3)
        try {
            val futures = (0..2).map { caller ->
                executor.submit {
                    repeat(100) { turn ->
                        val payload = byteArrayOf(caller.toByte(), turn.toByte())
                        val frame = AndroidRuntimeMessages.request("$caller-$turn", "client", "svc", "reply", payload, 5000)
                        assertArrayEquals(payload, AndroidRuntimeMessages.decodeRequest(frame).payload)
                        val response = AndroidRuntimeMessages.reply(frame, "svc", payload)
                        assertArrayEquals(payload, AndroidRuntimeMessages.responsePayload(response))
                    }
                }
            }
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        noOwners()
    }
}
