package coakka.v2.connector

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class NativeImage(val name: String, val bytes: ByteArray)

/**
 * Loader-owned, immutable module cache. Published slots are never automatically
 * deleted or overwritten: Windows may still map them in another process. Eight
 * retained identities bound storage; full admission fails closed. Administrators
 * may remove the cache only after stopping all JVMs that use it.
 */
internal class NativePairCache(private val root: Path) {
    fun prepare(core: NativeImage, bridge: NativeImage): Pair<Path, Path> {
        val images = listOf(core, bridge)
        require(core.name != bridge.name)
        images.forEach {
            require(it.name in imageNames && it.bytes.size.toLong() in 1..maxImageBytes)
        }
        val record = images.joinToString("\n", postfix = "\n") {
            "${it.name}:${it.bytes.size}:${digest(it.bytes)}"
        }.toByteArray(Charsets.US_ASCII)
        // FileChannel locks overlap JVM-wide, even across classloaders. Serialize
        // channel open/close too: closing another channel for the same inode can
        // release a process's existing POSIX lock. The interned key is deliberately
        // stable across classloaders/versions; no mutable registry or thread exists.
        return synchronized(admissionMonitor) {
            privateDirectory(root)
            validateRoot()
            val lockPath = root.resolve("admission.lock")
            require(!Files.exists(lockPath, NOFOLLOW_LINKS) ||
                (Files.isRegularFile(lockPath, NOFOLLOW_LINKS) && Files.size(lockPath) == 0L)) {
                "native cache lock is not an empty regular file"
            }
            FileChannel.open(lockPath, CREATE, READ, WRITE, NOFOLLOW_LINKS).use { channel ->
                admissionLock(channel).use {
                    validateRoot()
                    for (index in 0 until slotCount) {
                        val slot = root.resolve("pair-$index")
                        if (!Files.exists(slot, NOFOLLOW_LINKS)) continue
                        validateSlot(slot)
                        val identity = slot.resolve("identity")
                        if (Files.exists(identity, NOFOLLOW_LINKS) && readIdentity(identity).contentEquals(record)) {
                            images.forEach { verify(slot.resolve(it.name), it) }
                            return@synchronized slot.resolve(core.name) to slot.resolve(bridge.name)
                        }
                    }
                    for (index in 0 until slotCount) {
                        val slot = root.resolve("pair-$index")
                        if (Files.exists(slot.resolve("identity"), NOFOLLOW_LINKS)) continue
                        privateDirectory(slot)
                        validateSlot(slot)
                        // Publication is last, under admission. An interrupted
                        // unpublished slot was never returned to System.load.
                        // Remove only known regular files; never recursive-delete.
                        entries(slot).forEach { Files.delete(it) }
                        images.forEach { image ->
                            Files.write(slot.resolve(image.name), image.bytes, CREATE_NEW, WRITE)
                        }
                        Files.write(slot.resolve("identity"), record, CREATE_NEW, WRITE)
                        return@synchronized slot.resolve(core.name) to slot.resolve(bridge.name)
                    }
                    error("native module cache is full ($slotCount retained pairs); stop using JVMs before clearing it")
                }
            }
        }
    }

    private fun admissionLock(channel: FileChannel): FileLock {
        val started = System.nanoTime()
        do {
            channel.tryLock(0, 1, false)?.let { return it }
            // Loader-only bounded cross-process contention, not a runtime poll.
            Thread.sleep(10)
        } while (System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5))
        error("native cache admission remained busy for five seconds")
    }

    private fun privateDirectory(path: Path) {
        val posix = path.fileSystem.supportedFileAttributeViews().contains("posix")
        try {
            val attributes = if (posix) {
                arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            } else {
                emptyArray()
            }
            Files.createDirectory(path, *attributes)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            require(Files.isDirectory(path, NOFOLLOW_LINKS)) { "native cache directory is not a real directory" }
        }
        if (posix) {
            require(Files.getPosixFilePermissions(path, NOFOLLOW_LINKS)
                .all { it.name.startsWith("OWNER_") }) { "native cache directory must be owner-only" }
        }
    }

    private fun validateRoot() {
        entries(root).forEach {
            val name = it.fileName.toString()
            require(name == "admission.lock" || (0 until slotCount).any { index -> name == "pair-$index" }) {
                "foreign entry in native module cache"
            }
            require(!Files.isSymbolicLink(it)) { "link in native module cache" }
        }
    }

    private fun validateSlot(slot: Path) {
        require(Files.isDirectory(slot, NOFOLLOW_LINKS)) { "native cache slot is not a directory" }
        val files = entries(slot)
        require(files.size <= 3) { "native cache slot exceeds its file bound" }
        files.forEach {
            val name = it.fileName.toString()
            require((name == "identity" || name in imageNames) && Files.isRegularFile(it, NOFOLLOW_LINKS)) {
                "foreign entry or link in native cache slot"
            }
            require(Files.size(it) <= if (name == "identity") 4_096 else maxImageBytes) {
                "native cache file exceeds its bound"
            }
        }
        val identity = slot.resolve("identity")
        if (Files.exists(identity, NOFOLLOW_LINKS)) {
            val match = identityShape.matchEntire(readIdentity(identity).toString(Charsets.US_ASCII))
                ?: error("native cache identity is malformed; published slots are never repaired in place")
            val names = listOf(match.groupValues[1], match.groupValues[3])
            require(names in canonicalPairs && files.map { it.fileName.toString() }.toSet() == names.toSet() + "identity") {
                "native cache identity does not describe a complete canonical pair"
            }
            names.forEachIndexed { index, name ->
                val size = match.groupValues[2 + index * 2].toLong()
                require(size in 1..maxImageBytes && Files.size(slot.resolve(name)) == size) {
                    "native cache identity has an invalid image extent"
                }
            }
        }
    }

    private fun entries(path: Path): List<Path> = Files.newDirectoryStream(path).use { stream ->
        val result = ArrayList<Path>()
        for (entry in stream) {
            require(result.size < 1 + slotCount) { "native cache directory exceeds its entry bound" }
            result.add(entry)
        }
        result
    }

    private fun readIdentity(path: Path): ByteArray = Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
        val bytes = ByteArray(4_097)
        var total = 0
        while (total < bytes.size) {
            val count = input.read(bytes, total, bytes.size - total)
            if (count < 0) break
            total += count
        }
        require(total <= 4_096) { "native cache identity exceeds its bound" }
        bytes.copyOf(total)
    }

    private fun verify(path: Path, image: NativeImage) {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) == image.bytes.size.toLong()) {
            "cached native image has a different extent"
        }
        val hash = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(16_384)
            var remaining = image.bytes.size
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                require(count > 0) { "cached native image is truncated" }
                hash.update(buffer, 0, count)
                remaining -= count
            }
            require(input.read() == -1) { "cached native image exceeds its bound" }
        }
        require(hash.digest().contentEquals(MessageDigest.getInstance("SHA-256").digest(image.bytes))) {
            "cached native image differs from its embedded identity"
        }
    }

    companion object {
        internal const val slotCount = 8
        private const val maxImageBytes = 64L * 1024 * 1024
        private val admissionMonitor = "coakka.runtime.native.cache.admission.v1".intern()
        private val canonicalPairs = setOf(
            listOf("libcoakka_runtime_v2.dylib", "libcoakka_runtime_jvm.dylib"),
            listOf("libcoakka_runtime_v2.so", "libcoakka_runtime_jvm.so"),
            listOf("libcoakka_runtime_v2.dll", "coakka_runtime_jvm.dll"),
        )
        private val imageNames = canonicalPairs.flatten().toSet()
        private val identityShape = Regex(
            "([A-Za-z0-9._+-]+):([1-9][0-9]{0,8}):[0-9a-f]{64}\\n" +
                "([A-Za-z0-9._+-]+):([1-9][0-9]{0,8}):[0-9a-f]{64}\\n",
        )

        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
