package coakka.v2.android.devicesmoke

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coakka.v2.android.AndroidRuntimeConfig
import coakka.v2.android.AndroidRuntimeFeatures
import coakka.v2.android.AndroidRuntimeRoute
import coakka.v2.android.AndroidStreamConsumer
import coakka.v2.android.AndroidStreamConsumerDecision
import coakka.v2.android.AndroidStreamSource
import coakka.v2.android.AndroidStreamSourceResult
import coakka.v2.android.CoAkkaAndroidRuntime
import coakka.v2.android.FileLane
import coakka.v2.android.FileLaneConfig
import coakka.v2.android.FileLaneFlags
import coakka.v2.android.FileReceiveSpec
import coakka.v2.android.FileTransferDirection
import coakka.v2.android.FileTransferSnapshot
import coakka.v2.android.LaneOwnerConfig
import coakka.v2.android.StreamDirection
import coakka.v2.android.StreamFrameFlags
import coakka.v2.android.StreamLane
import coakka.v2.android.StreamLaneConfig
import coakka.v2.android.StreamLaneFlags
import coakka.v2.android.StreamPublishSpec
import coakka.v2.android.StreamSessionSnapshot
import coakka.v2.transport.BusinessStatus
import coakka.v2.transport.DeliveryHint
import coakka.v2.transport.Envelope
import coakka.v2.transport.MessageKind
import coakka.v2.transport.PayloadFormat
import com.google.protobuf.ByteString
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackagedAarInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedAarLoadsExactCoreAndAllPublicRuntimeShapesWork() {
        assertTrue(
            "device does not advertise ${BuildConfig.EXPECTED_ABI}: ${Build.SUPPORTED_ABIS.toList()}",
            BuildConfig.EXPECTED_ABI in Build.SUPPORTED_ABIS,
        )
        assertPackagedIdentityAndLibraries()
        assertRuntimeIdentityAndMessageOutcome()
        assertOwnerPinnedFileTransfer()
        assertOwnerPinnedStreamDelivery()
    }

    private fun assertPackagedIdentityAndLibraries() {
        val metadata = context.assets.open("coakka/runtime-package.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        assertEquals(2, metadata.getInt("schema_version"))
        assertEquals(BuildConfig.EXPECTED_CONNECTOR_VERSION, metadata.getString("connector_version"))
        assertEquals(BuildConfig.EXPECTED_NATIVE_PACKAGE, metadata.getString("bundled_native_package_version"))
        assertEquals(BuildConfig.EXPECTED_CORE_COMMIT, metadata.getString("bundled_native_git_commit"))
        assertEquals(
            BuildConfig.EXPECTED_CONNECTOR_SOURCE_COMMIT,
            metadata.getString("connector_source_git_commit"),
        )
        assertEquals(BuildConfig.EXPECTED_CORE_COMMIT, metadata.getString("core_source_git_commit"))
        assertEquals(BuildConfig.EXPECTED_CORE_COMMIT, metadata.getString("core_checkout_git_commit"))
        assertFalse(metadata.getBoolean("connector_source_tree_dirty"))
        assertFalse(metadata.getBoolean("core_source_tree_dirty"))
        assertFalse(metadata.getBoolean("source_tree_dirty"))
        assertTrue(metadata.getBoolean("native_source_verified"))
        val abis = metadata.getJSONArray("included_android_abis")
        assertEquals(
            setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
            (0 until abis.length()).map(abis::getString).toSet(),
        )
        listOf("LICENSE", "NATIVE-LICENSE.md", "PACKAGE-LICENSE.md", "NOTICE").forEach { name ->
            context.assets.open(name).use { legalFile ->
                assertTrue("$name is empty", legalFile.readBytes().isNotEmpty())
            }
        }

        ZipFile(context.applicationInfo.sourceDir).use { apk ->
            val expectedEntries = setOf(
                "lib/${BuildConfig.EXPECTED_ABI}/${System.mapLibraryName("coakka_runtime_v2")}",
                "lib/${BuildConfig.EXPECTED_ABI}/${System.mapLibraryName("coakka_android_jni")}",
            )
            val packagedEntries = apk.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith("/${System.mapLibraryName("coakka_runtime_v2")}") ||
                    it.endsWith("/${System.mapLibraryName("coakka_android_jni")}") }
                .toSet()
            assertEquals(expectedEntries, packagedEntries)
        }
        assertPackagedLibraryAbi("coakka_runtime_v2")
        assertPackagedLibraryAbi("coakka_android_jni")
    }

    private fun assertPackagedLibraryAbi(library: String) {
        val entryName = "lib/${BuildConfig.EXPECTED_ABI}/${System.mapLibraryName(library)}"
        val header = ZipFile(context.applicationInfo.sourceDir).use { apk ->
            val entry = checkNotNull(apk.getEntry(entryName)) { "missing packaged library $entryName" }
            apk.getInputStream(entry).use { input -> ByteArray(20).also { bytes ->
                assertEquals(bytes.size, input.read(bytes))
            } }
        }
        assertArrayEquals(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()), header.copyOf(4))
        val elfClass = header[4].toInt() and 0xff
        val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        val expected = when (BuildConfig.EXPECTED_ABI) {
            "arm64-v8a" -> 2 to 183
            "armeabi-v7a" -> 1 to 40
            "x86" -> 1 to 3
            "x86_64" -> 2 to 62
            else -> error("unsupported expected ABI ${BuildConfig.EXPECTED_ABI}")
        }
        assertEquals("ELF class for $library", expected.first, elfClass)
        assertEquals("ELF machine for $library", expected.second, machine)
        assertEquals(BuildConfig.EXPECTED_ABI.endsWith("64-v8a") || BuildConfig.EXPECTED_ABI == "x86_64", android.os.Process.is64Bit())
    }

    private fun assertRuntimeIdentityAndMessageOutcome() {
        val info = CoAkkaAndroidRuntime.runtimeInfo()
        assertEquals(CoAkkaAndroidRuntime.ABI_VERSION, info.abiVersion)
        assertEquals(BuildConfig.EXPECTED_CORE_VERSION, info.runtimeVersion)
        assertEquals(BuildConfig.EXPECTED_CORE_COMMIT, info.gitCommit)
        assertTrue(info.supports(AndroidRuntimeFeatures.FILE_LANE))
        assertTrue(info.supports(AndroidRuntimeFeatures.STREAM_LANE))
        assertTrue(info.supports(AndroidRuntimeFeatures.LANE_OWNER_GRANTS))

        val executor = Executors.newFixedThreadPool(2)
        try {
            CoAkkaAndroidRuntime.open(
                config = AndroidRuntimeConfig("android-device-smoke", "android-${BuildConfig.EXPECTED_ABI}"),
                routes = listOf(AndroidRuntimeRoute.local("svc.echo", "android-${BuildConfig.EXPECTED_ABI}")),
            ).use { runtime ->
                val request = requestEnvelope()
                val deliveredFuture = executor.submit<Envelope> {
                    Envelope.parseFrom(checkNotNull(runtime.readDeliveredRequest()))
                }
                assertTrue(runtime.submitEnvelope(request.toByteArray()))
                val delivered = deliveredFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertEquals(MessageKind.MESSAGE_KIND_REQUEST, delivered.kind)
                assertEquals(request.messageId, delivered.messageId)

                val responseFuture = executor.submit<Envelope> {
                    Envelope.parseFrom(checkNotNull(runtime.readResponse()))
                }
                assertTrue(runtime.submitEnvelope(responseEnvelope(delivered).toByteArray()))
                val response = responseFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertEquals(MessageKind.MESSAGE_KIND_RESPONSE, response.kind)
                assertEquals(request.messageId, response.correlationId)
                assertEquals("reply-from-android", response.payload.toStringUtf8())
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    private fun assertOwnerPinnedFileTransfer() {
        val source = File(context.cacheDir, "coakka-file-source.bin")
        val destination = File(context.cacheDir, "coakka-file-destination.bin")
        source.writeBytes(ByteArray(128 * 1024) { (it * 31).toByte() })
        destination.delete()
        val digest = FileLane.sha256(source)
        val transferId = "android-owner-file"
        val token = "android-owner-file-token"

        try {
            FileLane.openOwned(
                config = FileLaneConfig(flags = FileLaneFlags.RECEIVER),
                owner = LaneOwnerConfig("android-receiver-2", "127.0.0.1"),
            ).use { receiver ->
                FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER)).use { sender ->
                    val grant = receiver.prepareReceiveGrant(
                        FileReceiveSpec(
                            transferId,
                            token,
                            destination,
                            digest.size,
                            digest.sha256,
                        ),
                    )
                    assertEquals("android-receiver-2", grant.owner.ownerInstanceId)
                    assertEquals("127.0.0.1", grant.owner.advertisedHost)
                    assertNotEquals(0, grant.owner.port)
                    sender.submitSend(grant.toSendSpec(source))

                    val send = awaitFileTerminal(sender, transferId, FileTransferDirection.SEND)
                    val receive = awaitFileTerminal(receiver, transferId, FileTransferDirection.RECEIVE)
                    assertTrue("send did not complete: $send", send.completed)
                    assertTrue("receive did not complete: $receive", receive.completed)
                    assertArrayEquals(source.readBytes(), destination.readBytes())
                    sender.forget(transferId, FileTransferDirection.SEND)
                    receiver.forget(transferId, FileTransferDirection.RECEIVE)
                }
            }
        } finally {
            source.delete()
            destination.delete()
            File(context.cacheDir, "${destination.name}.coakka.part").delete()
            File(context.cacheDir, "${destination.name}.coakka.ckpt").delete()
        }
    }

    private fun assertOwnerPinnedStreamDelivery() {
        val payload = "frame-from-android".toByteArray()
        val produced = AtomicBoolean(false)
        val consumed = AtomicReference<ByteArray>()
        val sessionId = "android-owner-stream"
        val token = "android-owner-stream-token"
        val source = AndroidStreamSource { destination ->
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
            val frame = ByteArray(data.remaining())
            data.get(frame)
            consumed.set(frame)
            AndroidStreamConsumerDecision.CONTINUE
        }

        StreamLane.openOwned(
            config = StreamLaneConfig(
                flags = StreamLaneFlags.PUBLISHER,
                maxFrameBytes = 1024,
                maxWindowBytes = 4096,
            ),
            owner = LaneOwnerConfig("android-publisher-3", "127.0.0.1"),
        ).use { publisher ->
            StreamLane.open(
                StreamLaneConfig(
                    flags = StreamLaneFlags.SUBSCRIBER,
                    maxFrameBytes = 1024,
                    maxWindowBytes = 4096,
                ),
            ).use { subscriber ->
                val grant = publisher.preparePublishGrant(
                    StreamPublishSpec(sessionId, token, 0x102, 1024, source),
                )
                assertEquals("android-publisher-3", grant.owner.ownerInstanceId)
                assertEquals("127.0.0.1", grant.owner.advertisedHost)
                assertNotEquals(0, grant.owner.port)
                subscriber.subscribe(grant.toSubscribeSpec(4096, consumer))

                val publish = awaitStreamTerminal(publisher, sessionId, StreamDirection.PUBLISH)
                val subscribe = awaitStreamTerminal(subscriber, sessionId, StreamDirection.SUBSCRIBE)
                assertTrue("publisher did not end cleanly: $publish", publish.completed)
                assertTrue("subscriber did not end cleanly: $subscribe", subscribe.completed)
                assertArrayEquals(payload, consumed.get())
                publisher.forget(sessionId, StreamDirection.PUBLISH)
                subscriber.forget(sessionId, StreamDirection.SUBSCRIBE)
            }
        }
    }

    private fun awaitFileTerminal(
        lane: FileLane,
        transferId: String,
        direction: FileTransferDirection,
    ): FileTransferSnapshot {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_SECONDS * 1000
        var snapshot = lane.transfer(transferId, direction)
        while (!snapshot.terminal && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(20)
            snapshot = lane.transfer(transferId, direction)
        }
        return snapshot
    }

    private fun awaitStreamTerminal(
        lane: StreamLane,
        sessionId: String,
        direction: StreamDirection,
    ): StreamSessionSnapshot {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_SECONDS * 1000
        var snapshot = lane.session(sessionId, direction)
        while (!snapshot.terminal && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(20)
            snapshot = lane.session(sessionId, direction)
        }
        return snapshot
    }

    private fun requestEnvelope(): Envelope = Envelope.newBuilder()
        .setMessageId("android-request-1")
        .setCorrelationId("android-request-1")
        .setSource("android-device-smoke")
        .setTarget("svc.echo")
        .setReplyTo("android-device-smoke/replies")
        .setKind(MessageKind.MESSAGE_KIND_REQUEST)
        .setOneWay(false)
        .setTimeoutMs(5000)
        .setPayload(ByteString.copyFromUtf8("hello-from-android"))
        .setStatus(BusinessStatus.BUSINESS_STATUS_OK)
        .setDeliveryHint(DeliveryHint.DELIVERY_HINT_ROUTER_DEFAULT)
        .setMessageType("android.smoke.request")
        .setPayloadSchemaVersion(1)
        .setPayloadFormat(PayloadFormat.PAYLOAD_FORMAT_PLAIN_TEXT)
        .build()

    private fun responseEnvelope(request: Envelope): Envelope = Envelope.newBuilder()
        .setMessageId("${request.messageId}.reply")
        .setCorrelationId(request.messageId)
        .setSource(request.target)
        .setTarget(request.source)
        .setKind(MessageKind.MESSAGE_KIND_RESPONSE)
        .setOneWay(false)
        .setTimeoutMs(request.timeoutMs)
        .setPayload(ByteString.copyFromUtf8("reply-from-android"))
        .setStatus(BusinessStatus.BUSINESS_STATUS_OK)
        .setDeliveryHint(DeliveryHint.DELIVERY_HINT_ROUTER_DEFAULT)
        .setMessageType("android.smoke.reply")
        .setPayloadSchemaVersion(1)
        .setPayloadFormat(PayloadFormat.PAYLOAD_FORMAT_PLAIN_TEXT)
        .build()

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
