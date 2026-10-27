package coakka.v2.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStartupProjectionTest {
    @Test
    fun projectsCoreValuesWithoutResolvingDefaults() {
        val values = LongArray(RUNTIME_STARTUP_VALUE_COUNT).apply {
            this[0] = 1
            this[1] = AndroidRuntimeStartupDefaultGroups.EXECUTION or
                AndroidRuntimeStartupDefaultGroups.CONNECTION or
                AndroidRuntimeStartupDefaultGroups.IO
            this[2] = 1
            this[3] = 128
            this[4] = 1_048_576
            this[5] = RuntimeState.STARTED.nativeValue.toLong()
            this[6] = AndroidRuntimeStartupCpuPolicies.AUTO.toLong()
            this[7] = 1
            this[8] = 8
            this[9] = 2
            this[10] = 3
            this[11] = 7
            this[12] = 1
            this[13] = 1
            this[14] = RuntimeTcpConnectionMode.MULTIPLEXED.nativeValue.toLong()
            this[15] = 0x0f
            this[16] = 0
            this[17] = 0x0e
            this[18] = 0
            this[19] = 1
            this[20] = 16
            this[21] = 30_000
            this[22] = 0
            this[23] = 1
            this[24] = 0
            this[25] = 0
            this[26] = 0
            this[27] = 0
            this[28] = 1
        }

        val actual = values.toAndroidRuntimeStartupConfig()
        assertEquals(1, actual.defaultsRevision)
        assertTrue(actual.wasDefaulted(AndroidRuntimeStartupDefaultGroups.EXECUTION))
        assertTrue(actual.wasDefaulted(AndroidRuntimeStartupDefaultGroups.CONNECTION))
        assertTrue(actual.wasDefaulted(AndroidRuntimeStartupDefaultGroups.IO))
        assertFalse(actual.wasDefaulted(AndroidRuntimeStartupDefaultGroups.MONITOR))
        assertEquals(RuntimeState.STARTED, actual.runtimeState)
        assertEquals(AndroidRuntimeStartupCpuPolicies.AUTO, actual.execution.requestedPolicy)
        assertEquals(listOf(3L, 7L), actual.execution.selectedCpuIds)
        assertEquals(RuntimeTcpConnectionMode.MULTIPLEXED, actual.connection.mode)
        assertEquals(16L, actual.connection.maxRequestsPerConnection)
        assertTrue(actual.monitorEnabled)
    }

    @Test
    fun rejectsMalformedNativeProjection() {
        assertThrows(IllegalArgumentException::class.java) {
            LongArray(RUNTIME_STARTUP_VALUE_COUNT - 1).toAndroidRuntimeStartupConfig()
        }
        assertThrows(IllegalArgumentException::class.java) {
            LongArray(RUNTIME_STARTUP_VALUE_COUNT).apply { this[9] = 3 }
                .toAndroidRuntimeStartupConfig()
        }
        assertThrows(IllegalStateException::class.java) {
            LongArray(RUNTIME_STARTUP_VALUE_COUNT).apply {
                this[5] = RuntimeState.CREATED.nativeValue.toLong()
                this[9] = 0
                this[14] = 99
            }.toAndroidRuntimeStartupConfig()
        }
    }

    @Test
    fun mapsOnlyCoreAskTerminalKinds() {
        assertEquals(
            AndroidRuntimeAskResultKind.RESPONSE,
            AndroidRuntimeAskResultKind.fromNative(1),
        )
        assertEquals(
            AndroidRuntimeAskResultKind.DEADLETTER,
            AndroidRuntimeAskResultKind.fromNative(2),
        )
        assertThrows(IllegalStateException::class.java) {
            AndroidRuntimeAskResultKind.fromNative(0)
        }
    }
}
