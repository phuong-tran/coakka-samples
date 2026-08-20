package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeLibraryResolverTest {
    @Test
    fun platformIdNormalizesSupportedNames() {
        assertEquals("macos-aarch64", NativeLibraryResolver.platformId("Mac OS X", "arm64"))
        assertEquals("linux-x86_64", NativeLibraryResolver.platformId("Linux", "amd64"))
        assertEquals("windows-aarch64", NativeLibraryResolver.platformId("Windows 11", "aarch64"))
        assertEquals("windows-x86_64", NativeLibraryResolver.platformId("Windows 11", "amd64"))
    }

    @Test
    fun macResolverPrefersDylibButKeepsSoFallback() {
        assertEquals(
            listOf(
                "libcoakka_runtime_v2-${NativeRuntimePackaging.nativePackageVersion}.dylib",
                "libcoakka_runtime_v2.dylib",
                "libcoakka_runtime_v2.so",
            ),
            NativeLibraryResolver.resourceFileNamesForCurrentPlatform("Mac OS X"),
        )
    }

    @Test
    fun linuxResolverUsesSoOnly() {
        assertEquals(
            listOf(
                "libcoakka_runtime_v2-${NativeRuntimePackaging.nativePackageVersion}.so",
                "libcoakka_runtime_v2.so",
            ),
            NativeLibraryResolver.resourceFileNamesForCurrentPlatform("Linux"),
        )
    }

    @Test
    fun windowsResolverUsesDllOnly() {
        assertEquals(
            listOf(
                "libcoakka_runtime_v2-${NativeRuntimePackaging.nativePackageVersion}.dll",
                "libcoakka_runtime_v2.dll",
            ),
            NativeLibraryResolver.resourceFileNamesForCurrentPlatform("Windows 11"),
        )
    }
}
