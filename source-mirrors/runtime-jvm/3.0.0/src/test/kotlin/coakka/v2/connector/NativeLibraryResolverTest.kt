package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Paths

class NativeLibraryResolverTest {
    @Test
    fun omittedCacheDirectoryPreservesHomeDefault() {
        assertEquals(Paths.get("home").toAbsolutePath().resolve(".coakka-runtime-native-v1"),
            NativeLibraryResolver.nativeCacheRoot(null, "home"))
    }

    @Test
    fun explicitCacheDirectoryDoesNotUseHomeOrTrimPath() {
        val path = Paths.get(" native cache").toAbsolutePath()
        assertEquals(path, NativeLibraryResolver.nativeCacheRoot(" native cache", "tiếng-Việt-🙂"))
        assertEquals(path, NativeLibraryResolver.nativeCacheRoot(path.toString(), "ignored-home"))
    }

    @Test
    fun blankCacheDirectoryIsRefusedRatherThanDefaulted() {
        for (value in listOf("", " ", "\t\n")) {
            assertFailsWith<IllegalArgumentException> {
                NativeLibraryResolver.nativeCacheRoot(value, "home")
            }
        }
    }

    @Test
    fun platformIdNormalizesSupportedNames() {
        assertEquals("macos-aarch64", NativeLibraryResolver.platformId("Mac OS X", "arm64"))
        assertEquals("linux-x86_64", NativeLibraryResolver.platformId("Linux", "amd64"))
        assertEquals("windows-aarch64", NativeLibraryResolver.platformId("Windows 11", "aarch64"))
        assertEquals("windows-x86_64", NativeLibraryResolver.platformId("Windows 11", "amd64"))
    }

    @Test
    fun macResolverRequiresTheCoreAndJniDylibPair() {
        assertEquals(
            listOf(
                "libcoakka_runtime_v2.dylib",
                "libcoakka_runtime_jvm.dylib",
            ),
            NativeLibraryResolver.resourceFileNamesForCurrentPlatform("Mac OS X"),
        )
    }

    @Test
    fun linuxResolverRequiresTheCoreAndJniSoPair() {
        assertEquals(
            listOf(
                "libcoakka_runtime_v2.so",
                "libcoakka_runtime_jvm.so",
            ),
            NativeLibraryResolver.resourceFileNamesForCurrentPlatform("Linux"),
        )
    }

    @Test
    fun windowsResolverRequiresTheCoreAndJniDllPair() {
        assertEquals(
            listOf(
                "libcoakka_runtime_v2.dll",
                "coakka_runtime_jvm.dll",
            ),
            NativeLibraryResolver.resourceFileNamesForCurrentPlatform("Windows 11"),
        )
    }
}
