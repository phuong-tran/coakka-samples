package coakka.v2.connector

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

class NativePairCacheTest {
    @TempDir lateinit var directory: Path

    private fun cache() = NativePairCache(directory.resolve("cache"))
    private fun core(value: Int = 1) = NativeImage("libcoakka_runtime_v2.dll", byteArrayOf(value.toByte()))
    private fun bridge() = NativeImage("coakka_runtime_jvm.dll", byteArrayOf(42))

    @Test fun reusesVerifiedPairWithoutCreatingAnotherSlot() {
        val path = cache().prepare(core(), bridge()).first
        assertContentEquals(core().bytes, Files.readAllBytes(path))
        assertEquals(path, cache().prepare(core(), bridge()).first)
        Files.newDirectoryStream(path.parent.parent, "pair-*").use { assertEquals(1, it.count()) }
    }

    @Test fun publishedPairIsNeverEvictedOrOverwritten() {
        val original = cache().prepare(core(), bridge()).first
        val other = cache().prepare(core(2), bridge()).first
        assertTrue(original != other)
        assertContentEquals(core().bytes, Files.readAllBytes(original))
        assertEquals(original, cache().prepare(core(), bridge()).first)
    }

    @Test fun eightRetainedIdentitiesFailClosedWithoutGrowingTheCache() {
        repeat(NativePairCache.slotCount) { cache().prepare(core(it), bridge()) }
        val error = assertFailsWith<IllegalStateException> { cache().prepare(core(99), bridge()) }
        assertTrue(error.message!!.contains("8 retained pairs"))
        Files.newDirectoryStream(directory.resolve("cache"), "pair-*").use { assertEquals(8, it.count()) }
        assertContentEquals(core().bytes, Files.readAllBytes(cache().prepare(core(), bridge()).first))
    }

    @Test fun corruptionIsRejectedAndNeverRepairedInPlace() {
        val path = cache().prepare(core(), bridge()).first
        Files.write(path, byteArrayOf(2))
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), bridge()) }
        assertContentEquals(byteArrayOf(2), Files.readAllBytes(path))
    }

    @Test fun incompleteSlotFromDeadPublisherIsRecovered() {
        val path = cache().prepare(core(), bridge()).first
        Files.delete(path.parent.resolve("identity"))
        Files.write(path, byteArrayOf(9))
        assertEquals(path, cache().prepare(core(), bridge()).first)
        assertContentEquals(core().bytes, Files.readAllBytes(path))
    }

    @Test fun malformedPublishedIdentityIsRejectedWithoutRepair() {
        val identity = cache().prepare(core(), bridge()).first.parent.resolve("identity")
        Files.write(identity, byteArrayOf(42))
        assertFailsWith<IllegalStateException> { cache().prepare(core(), bridge()) }
        assertContentEquals(byteArrayOf(42), Files.readAllBytes(identity))
    }

    @Test fun oversizedPublishedIdentityIsRejectedWithoutReadingIt() {
        val identity = cache().prepare(core(), bridge()).first.parent.resolve("identity")
        Files.write(identity, ByteArray(4_097))
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), bridge()) }
        assertEquals(4_097L, Files.size(identity))
    }

    @Test fun unexpectedSlotContentIsNotDeleted() {
        val slot = cache().prepare(core(), bridge()).first.parent
        val foreign = slot.resolve("application-data")
        Files.write(foreign, byteArrayOf(7))
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(2), bridge()) }
        assertContentEquals(byteArrayOf(7), Files.readAllBytes(foreign))
    }

    @Test fun unexpectedRootContentIsNotDeleted() {
        cache().prepare(core(), bridge())
        val foreign = directory.resolve("cache/application-data")
        Files.write(foreign, byteArrayOf(7))
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), bridge()) }
        assertTrue(Files.exists(foreign))
    }

    @Test fun invalidOrAliasedImageNamesAreRejectedBeforeCreatingCache() {
        assertFailsWith<IllegalArgumentException> { cache().prepare(NativeImage("../core.dll", byteArrayOf(1)), bridge()) }
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), core()) }
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), NativeImage(bridge().name, byteArrayOf())) }
        assertTrue(!Files.exists(directory.resolve("cache")))
    }

    @Test fun nonDirectoryRootIsNotOverwritten() {
        val path = directory.resolve("cache")
        Files.write(path, byteArrayOf(7))
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), bridge()) }
        assertContentEquals(byteArrayOf(7), Files.readAllBytes(path))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun linksAndPubliclyWritableDirectoryAreRejected() {
        val target = directory.resolve("target")
        Files.createDirectory(target)
        Files.createSymbolicLink(directory.resolve("cache"), target)
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), bridge()) }
        Files.delete(directory.resolve("cache"))
        Files.createDirectory(directory.resolve("cache"))
        Files.setPosixFilePermissions(directory.resolve("cache"), java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"))
        assertFailsWith<IllegalArgumentException> { cache().prepare(core(), bridge()) }
    }
}
