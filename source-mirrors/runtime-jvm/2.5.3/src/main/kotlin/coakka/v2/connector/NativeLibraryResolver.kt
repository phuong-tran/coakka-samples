package coakka.v2.connector

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Resolves the native runtime library either from an explicit filesystem path
 * or from an embedded `native/<os>-<arch>/...` resource packaged inside the jar.
 */
object NativeLibraryResolver {
    private const val runtimeProperty = "coakka.runtime.lib"
    private const val runtimeLibraryBaseName = "libcoakka_runtime_v2"
    @Volatile private var cachedEmbeddedPath: String? = null

    fun resolve(
        explicitPath: String? = null,
        propertyName: String = runtimeProperty,
    ): String {
        val configuredPath = explicitPath
            ?: System.getProperty(propertyName)
            ?.takeIf(String::isNotBlank)
        if (configuredPath != null) {
            return configuredPath
        }
        cachedEmbeddedPath?.let { return it }

        return synchronized(this) {
            cachedEmbeddedPath?.let { return@synchronized it }

            val platformId = platformId()
            val tempDir = Files.createTempDirectory("coakka-native-")
            tempDir.toFile().deleteOnExit()

            val resourcePath = resolveResource(platformId, resourceFileNamesForCurrentPlatform())
                ?: error(
                    "native runtime not found for platform=$platformId under /native/$platformId and -D$propertyName was not set",
                )

            val (resolvedResourcePath, resource) = resourcePath
            val tempFile = extractToTemp(tempDir, resolvedResourcePath, resource, resolvedResourcePath.substringAfterLast('/'))
            val resolvedPath = tempFile.toAbsolutePath().toString()
            cachedEmbeddedPath = resolvedPath
            resolvedPath
        }
    }

    internal fun platformId(osName: String = System.getProperty("os.name"), archName: String = System.getProperty("os.arch")): String =
        "${normalizeOs(osName)}-${normalizeArch(archName)}"

    internal fun normalizeOs(osName: String): String {
        val normalized = osName.lowercase()
        return when {
            normalized.contains("mac") || normalized.contains("darwin") -> "macos"
            normalized.contains("linux") -> "linux"
            normalized.contains("windows") -> "windows"
            else -> error("unsupported os.name=$osName; supported platforms are macOS, Linux, and Windows")
        }
    }

    internal fun normalizeArch(archName: String): String {
        val normalized = archName.lowercase()
        return when (normalized) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> error("unsupported os.arch=$archName; supported architectures are aarch64 and x86_64")
        }
    }

    internal fun resourceFileNamesForCurrentPlatform(osName: String = System.getProperty("os.name")): List<String> =
        when (normalizeOs(osName)) {
            "macos" -> listOf(
                "$runtimeLibraryBaseName-${NativeRuntimePackaging.nativePackageVersion}.dylib",
                "$runtimeLibraryBaseName.dylib",
                "$runtimeLibraryBaseName.so",
            )
            "linux" -> listOf(
                "$runtimeLibraryBaseName-${NativeRuntimePackaging.nativePackageVersion}.so",
                "$runtimeLibraryBaseName.so",
            )
            "windows" -> listOf(
                "$runtimeLibraryBaseName-${NativeRuntimePackaging.nativePackageVersion}.dll",
                "$runtimeLibraryBaseName.dll",
            )
            else -> error("unsupported os.name=$osName")
        }

    private fun resolveResource(platformId: String, candidateFileNames: List<String>): Pair<String, java.io.InputStream>? =
        candidateFileNames.asSequence()
            .map { fileName -> "/native/$platformId/$fileName" }
            .firstNotNullOfOrNull { candidate ->
                NativeLibraryResolver::class.java.getResourceAsStream(candidate)
                    ?.let { resource -> candidate to resource }
            }

    private fun extractToTemp(tempDir: Path, resourcePath: String, input: java.io.InputStream, libraryFileName: String): Path {
        input.use { stream ->
            val tempFile = tempDir.resolve(libraryFileName)
            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING)
            tempFile.toFile().deleteOnExit()
            return tempFile
        }
    }
}
