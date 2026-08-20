package coakka.v2.connector

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileLaneTest {
    @Test
    fun ownerGrantLayoutsMatchPublic64BitAbi() {
        assertEquals(24, NativeLaneOwnerConfig().size())
        assertEquals(400, NativeLaneOwnerEndpoint().size())
        assertEquals(96, NativeFileLaneConfig().size())
        assertEquals(128, NativeFileLaneOwnedConfig().size())
        assertEquals(648, NativeFileReceiveGrant().size())
    }

    @Test
    fun publicConnectorRoundtripCrossesNativeQuantum() {
        val runtime = System.getProperty("coakka.fileLane.runtime.lib")
            ?: System.getenv("COAKKA_FILE_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current file-lane runtime not configured")
        val root = createTempDirectory("coakka-jvm-file-lane-")
        try {
            val source = root.resolve("source.bin")
            val destination = root.resolve("destination.bin")
            val bytes = ByteArray(9 * 1024 * 1024 + 731) { index -> ((index * 31 + 17) and 0xff).toByte() }
            Files.write(source, bytes)
            val digest = FileLane.sha256(source, runtime)
            assertEquals(bytes.size.toLong(), digest.size)

            FileLane.open(FileLaneConfig(flags = FileLaneFlags.RECEIVER), runtime).use { receiver ->
                FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER), runtime).use { sender ->
                    val transferId = "jvm-file-lane-multi-quantum"
                    val token = "jvm-file-lane-token"
                    receiver.prepareReceive(FileReceiveSpec(transferId, token, destination, digest.size, digest.sha256))
                    sender.submitSend(FileSendSpec(transferId, token, "127.0.0.1", receiver.boundPort, source, digest.size, digest.sha256))
                    val sent = waitTerminal(sender, transferId, FileTransferDirection.SEND)
                    val received = waitTerminal(receiver, transferId, FileTransferDirection.RECEIVE)
                    assertTrue(sent.completed, sent.detail)
                    assertTrue(received.completed, received.detail)
                    assertEquals(bytes.size.toLong(), sent.transferredBytes)
                    assertEquals(bytes.size.toLong(), received.committedOffset)
                    assertContentEquals(digest.sha256, FileLane.sha256(destination, runtime).sha256)
                    assertEquals(1, sender.stats().completedSends)
                    assertEquals(1, receiver.stats().completedReceives)
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun ownerGrantPinsEndpointAndCompletesTransfer() {
        val runtime = System.getProperty("coakka.fileLane.runtime.lib")
            ?: System.getenv("COAKKA_FILE_LANE_RUNTIME_LIB")
        assumeTrue(!runtime.isNullOrBlank(), "current file-lane runtime not configured")
        val root = createTempDirectory("coakka-jvm-file-owner-grant-")
        try {
            val source = root.resolve("source.bin")
            val destination = root.resolve("destination.bin")
            val bytes = ByteArray(1024 * 1024 + 37) { index -> ((index * 17 + 11) and 0xff).toByte() }
            Files.write(source, bytes)
            val digest = FileLane.sha256(source, runtime)

            FileLane.openOwned(
                FileLaneConfig(flags = FileLaneFlags.RECEIVER),
                LaneOwnerConfig("files-2", "127.0.0.1"),
                runtime,
            ).use { receiver ->
                FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER), runtime).use { sender ->
                    val token = "file-owner-grant-secret"
                    val grant = receiver.prepareReceiveGrant(
                        FileReceiveSpec("file-owner-grant", token, destination, digest.size, digest.sha256),
                    )
                    assertEquals("files-2", grant.owner.ownerInstanceId)
                    assertEquals("127.0.0.1", grant.owner.advertisedHost)
                    assertEquals(receiver.boundPort, grant.owner.port)
                    assertFalse(grant.toString().contains(token))
                    val receivedGrant = FileReceiveGrant(
                        grant.owner,
                        grant.transferId,
                        grant.authorizationToken,
                        grant.expectedSize,
                        grant.expectedSha256,
                    )
                    sender.submitSend(receivedGrant.toSendSpec(source))
                    assertTrue(waitTerminal(sender, grant.transferId, FileTransferDirection.SEND).completed)
                    assertTrue(waitTerminal(receiver, grant.transferId, FileTransferDirection.RECEIVE).completed)
                    assertContentEquals(bytes, Files.readAllBytes(destination))
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun waitTerminal(lane: FileLane, transferId: String, direction: FileTransferDirection): FileTransferSnapshot {
        var sequence = 0L
        repeat(64) {
            val snapshot = lane.waitTransfer(transferId, direction, sequence, 30_000)
            if (snapshot.terminal) return snapshot
            sequence = snapshot.updateSequence
        }
        error("file transfer did not reach a terminal state")
    }
}
