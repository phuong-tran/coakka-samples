package coakka.v2.connector

import java.io.File
import java.io.InputStream
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Properties

/**
 * Loads one integrity-bound Core/JNI pair. Core is loaded first so the bridge's
 * private dynamic edge resolves from the same extracted directory.
 */
object NativeLibraryResolver {
    private const val runtimeProperty = "coakka.runtime.lib"
    private const val bridgeProperty = "coakka.runtime.bridge"
    private const val recordName = "coakka-runtime-native.properties"
    private const val maxNativeBytes = 64L * 1024L * 1024L

    @Volatile private var loadedPair: Pair<String, String>? = null
    @Volatile private var loadFailure: Throwable? = null

    /** Returns the Core path after loading the exact Core/JNI pair once. */
    @JvmOverloads
    fun resolve(explicitPath: String? = null, propertyName: String = runtimeProperty): String {
        require(propertyName == runtimeProperty) { "only the Core runtime property is supported" }
        return load(explicitPath).first
    }

    internal fun load(explicitCorePath: String? = null, explicitBridgePath: String? = null): Pair<String, String> {
        loadedPair?.let { existing ->
            requireCompatibleRequest(existing, explicitCorePath, explicitBridgePath)
            return existing
        }
        return synchronized(this) {
            loadFailure?.let { throw IllegalStateException("native pair loading previously failed", it) }
            loadedPair?.let { existing ->
                requireCompatibleRequest(existing, explicitCorePath, explicitBridgePath)
                return@synchronized existing
            }

            val configuredCore = explicitCorePath
                ?: System.getProperty(runtimeProperty)?.takeIf(String::isNotBlank)
            val configuredBridge = explicitBridgePath
                ?: System.getProperty(bridgeProperty)?.takeIf(String::isNotBlank)
            val pair = if (configuredCore != null || configuredBridge != null) {
                require(configuredCore != null && configuredBridge != null) {
                    "explicit native loading requires both -D$runtimeProperty and -D$bridgeProperty"
                }
                canonicalNative(configuredCore, "Core") to canonicalNative(configuredBridge, "JNI bridge")
            } else {
                extractEmbeddedPair().let {
                    it.first.toAbsolutePath().toString() to it.second.toAbsolutePath().toString()
                }
            }

            try {
                System.load(pair.first)
                System.load(pair.second)
            } catch (failure: Throwable) {
                // Core may already be mapped. Keep the immutable pair and fail closed;
                // do not retry with another pair/path after a partial load.
                loadFailure = failure
                throw failure
            }
            loadedPair = pair
            pair
        }
    }

    private fun requireCompatibleRequest(
        existing: Pair<String, String>,
        requestedCore: String?,
        requestedBridge: String?,
    ) {
        requestedCore?.let {
            require(File(it).canonicalPath == existing.first) {
                "native Core is already loaded from ${existing.first}"
            }
        }
        requestedBridge?.let {
            require(File(it).canonicalPath == existing.second) {
                "JNI bridge is already loaded from ${existing.second}"
            }
        }
    }

    private fun canonicalNative(value: String, role: String): String {
        val file = File(value).canonicalFile
        require(file.isFile && file.length() in 1..maxNativeBytes) {
            "$role must be a bounded nonempty regular file: $file"
        }
        return file.path
    }

    private fun extractEmbeddedPair(): Pair<java.nio.file.Path, java.nio.file.Path> {
        val platform = platformId()
        val prefix = "/native/$platform/"
        val recordBytes = readBoundedResource(prefix + recordName, 4_096)
        val properties = Properties().apply { recordBytes.inputStream().use(::load) }
        val required = setOf(
            "format", "platform",
            "core.file", "core.bytes", "core.sha256",
            "bridge.file", "bridge.bytes", "bridge.sha256",
        )
        require(
            properties.stringPropertyNames() == required &&
                properties.getProperty("format") == "1" &&
                properties.getProperty("platform") == platform,
        ) { "invalid embedded native pair record for $platform" }

        val core = verifiedImage(prefix, properties, "core")
        val bridge = verifiedImage(prefix, properties, "bridge")
        require(listOf(core.name, bridge.name) == resourceFileNamesForCurrentPlatform()) {
            "embedded native pair does not use canonical module names"
        }
        val root = nativeCacheRoot(System.getProperty("coakka.runtime.nativeDir"),
            System.getProperty("user.home"))
        return NativePairCache(root).prepare(core, bridge)
    }

    // Deployment storage only, resolved before first native load. Never change
    // the application's home or silently choose another directory on failure.
    internal fun nativeCacheRoot(configured: String?, userHome: String): java.nio.file.Path {
        if (configured == null) {
            return Paths.get(userHome).toAbsolutePath().resolve(".coakka-runtime-native-v1")
        }
        require(configured.isNotBlank()) { "coakka.runtime.nativeDir must not be blank" }
        return Paths.get(configured).toAbsolutePath()
    }

    private fun verifiedImage(
        prefix: String,
        properties: Properties,
        role: String,
    ): NativeImage {
        val name = properties.getProperty("$role.file")
        require(name.matches(Regex("[A-Za-z0-9._+-]+")) && !name.contains("..")) {
            "invalid embedded $role filename"
        }
        val expectedBytes = properties.getProperty("$role.bytes").toLongOrNull()
            ?: error("invalid embedded $role byte count")
        val expectedSha = properties.getProperty("$role.sha256")
        require(expectedBytes in 1..maxNativeBytes && expectedSha.matches(Regex("[0-9a-f]{64}"))) {
            "invalid embedded $role identity"
        }
        val bytes = readBoundedResource(prefix + name, maxNativeBytes.toInt())
        require(bytes.size.toLong() == expectedBytes && sha256(bytes) == expectedSha) {
            "embedded $role differs from its pair record"
        }
        return NativeImage(name, bytes)
    }

    private fun readBoundedResource(path: String, limit: Int): ByteArray {
        val stream = NativeLibraryResolver::class.java.getResourceAsStream(path)
            ?: error("native resource not found: $path")
        return stream.use { readBounded(it, limit) }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 65_536))
        val buffer = ByteArray(16_384)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "native resource exceeds its bound" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun platformId(
        osName: String = System.getProperty("os.name"),
        archName: String = System.getProperty("os.arch"),
    ): String = "${normalizeOs(osName)}-${normalizeArch(archName)}"

    internal fun normalizeOs(osName: String): String {
        val normalized = osName.lowercase()
        return when {
            normalized.contains("mac") || normalized.contains("darwin") -> "macos"
            normalized.contains("linux") -> "linux"
            normalized.contains("windows") -> "windows"
            else -> error("unsupported os.name=$osName; supported platforms are macOS, Linux, and Windows")
        }
    }

    internal fun normalizeArch(archName: String): String = when (archName.lowercase()) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x86_64"
        else -> error("unsupported os.arch=$archName; supported architectures are aarch64 and x86_64")
    }

    internal fun resourceFileNamesForCurrentPlatform(osName: String = System.getProperty("os.name")): List<String> =
        when (normalizeOs(osName)) {
            "macos" -> listOf("libcoakka_runtime_v2.dylib", "libcoakka_runtime_jvm.dylib")
            "linux" -> listOf("libcoakka_runtime_v2.so", "libcoakka_runtime_jvm.so")
            "windows" -> listOf("libcoakka_runtime_v2.dll", "coakka_runtime_jvm.dll")
            else -> error("unsupported os.name=$osName")
        }
}
