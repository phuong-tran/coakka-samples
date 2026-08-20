package coakka.v2.connector

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeTransportConfigurationTest {
    @Test
    fun jnaLayoutsMatchThePublic64BitCAbi() {
        assertEquals(48, NativeTcpConnectionOptions().size())
        assertEquals(40, NativeTcpConnectionValidation().size())
        assertEquals(72, NativeTcpConnectionConfig().size())
        assertEquals(136, NativeTcpConnectionApplyResult().size())
        assertEquals(72, NativeTcpSecurityOptions().size())
        assertEquals(24, NativeTcpSecurityValidation().size())
        assertEquals(40, NativeTcpSecurityConfig().size())
        assertEquals(248, NativeTcpSecurityIdentityInfo().size())
        assertEquals(296, NativeTcpSecurityInfo().size())
        assertEquals(344, NativeTcpSecurityApplyResult().size())
        assertEquals(48, NativeRuntimeCapabilities().size())
    }

    @Test
    fun capabilityDiscoveryAndRuntimeDefaultsAreCoherent() {
        val capabilities = RuntimeHandle.readRuntimeCapabilities(TestSupport.runtimeLibPath())
        assertTrue(capabilities.supports(0))
        assertTrue(capabilities.supports(capabilities.effectiveCapabilities))
        assertEquals(
            capabilities.effectiveCapabilities,
            capabilities.effectiveCapabilities and capabilities.compiledCapabilities,
        )
        assertEquals(
            capabilities.effectiveCapabilities,
            capabilities.effectiveCapabilities and capabilities.entitledCapabilities,
        )

        val handle = openHandle("defaults")
        try {
            assertEquals(capabilities, handle.runtimeCapabilities())
            val connection = handle.tcpConnectionConfig()
            val security = handle.tcpSecurityInfo()
            assertEquals(capabilities.tcpConnectionDefaultsRevision, connection.defaultsRevision)
            assertEquals(RuntimeTcpConnectionMode.PER_EXCHANGE, connection.mode)
            assertEquals(RuntimeTcpSecurityMode.PLAINTEXT, security.mode)
            assertEquals(0, security.credentialGeneration)
            assertTrue(security.credentialId.isEmpty())
            assertTrue(security.identityFingerprintSha256.isEmpty())
        } finally {
            handle.close()
        }
    }

    @Test
    fun startupApplyAndPostStartRejectionPreserveTheSelectedConnectionMode() {
        val capabilities = RuntimeHandle.readRuntimeCapabilities(TestSupport.runtimeLibPath())
        val startupMode = if (capabilities.supports(CoakkaRuntimeCapabilities.TCP_BOUNDED_POOL)) {
            RuntimeTcpConnectionMode.BOUNDED_POOL
        } else {
            RuntimeTcpConnectionMode.PER_EXCHANGE
        }
        val handle = openHandle(
            suffix = "startup-connection",
            connection = RuntimeTcpConnectionStrategySpec(startupMode),
            security = RuntimeTcpSecuritySpec(),
        )
        try {
            val startupConnection = assertNotNull(handle.startupConnectionResult())
            val startupSecurity = assertNotNull(handle.startupSecurityResult())
            assertTrue(startupConnection.applied())
            assertEquals(startupMode, startupConnection.activeConfig.mode)
            assertTrue(startupSecurity.applied())
            assertEquals(RuntimeTcpSecurityMode.PLAINTEXT, startupSecurity.activeSecurity.mode)

            handle.start()
            val rejected = handle.applyTcpConnectionStrategy(
                RuntimeTcpConnectionStrategySpec(RuntimeTcpConnectionMode.PER_EXCHANGE),
            )
            assertEquals(CoakkaStatus.ERR_BAD_STATE, rejected.status)
            assertFalse(rejected.changed)
            assertEquals(CoakkaTransportApplyReasons.RUNTIME_NOT_CONFIGURABLE, rejected.reason)
            assertEquals(startupMode, rejected.activeConfig.mode)
            assertEquals(startupMode, handle.tcpConnectionConfig().mode)
        } finally {
            handle.close()
        }
    }

    @Test
    fun everyEffectiveAdvancedConnectionModeCanBeSelectedAtStartup() {
        val capabilities = RuntimeHandle.readRuntimeCapabilities(TestSupport.runtimeLibPath())
        val cases = listOf(
            CoakkaRuntimeCapabilities.TCP_BOUNDED_POOL to RuntimeTcpConnectionMode.BOUNDED_POOL,
            CoakkaRuntimeCapabilities.TCP_PERSISTENT_SINGLE_FLIGHT to
                RuntimeTcpConnectionMode.PERSISTENT_SINGLE_FLIGHT,
            CoakkaRuntimeCapabilities.TCP_MULTIPLEXING to RuntimeTcpConnectionMode.MULTIPLEXING,
        )
        cases.filter { capabilities.supports(it.first) }.forEach { (_, mode) ->
            val handle = openHandle(
                suffix = "mode-${mode.value}",
                connection = RuntimeTcpConnectionStrategySpec(mode),
            )
            try {
                assertEquals(mode, assertNotNull(handle.startupConnectionResult()).activeConfig.mode)
                assertEquals(mode, handle.tcpConnectionConfig().mode)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun invalidStartupModeReturnsStructuredNativeValidation() {
        val error = assertFailsWith<RuntimeTcpConnectionApplyException> {
            openHandle(
                suffix = "invalid-mode",
                connection = RuntimeTcpConnectionStrategySpec(RuntimeTcpConnectionMode.of(999)),
            )
        }
        assertEquals(CoakkaStatus.ERR_INVALID_ARG, error.result.status)
        assertFalse(error.result.changed)
        assertEquals(CoakkaTransportApplyReasons.INVALID_ARGUMENT, error.result.reason)
        assertEquals(CoakkaTcpConnectionValidationCodes.UNKNOWN_MODE, error.result.validationCode)
        assertEquals(RuntimeTcpConnectionMode.PER_EXCHANGE, error.result.activeConfig.mode)
    }

    @Test
    fun plaintextDoesNotSilentlyDiscardCredentialFields() {
        val error = assertFailsWith<RuntimeTcpSecurityApplyException> {
            openHandle(
                suffix = "invalid-plaintext-fields",
                security = RuntimeTcpSecuritySpec(
                    mode = RuntimeTcpSecurityMode.PLAINTEXT,
                    credentialGeneration = 1,
                    credentialId = "must-not-be-ignored",
                ),
            )
        }
        assertEquals(CoakkaStatus.ERR_INVALID_ARG, error.result.status)
        assertFalse(error.result.changed)
        assertEquals(CoakkaTransportApplyReasons.INVALID_ARGUMENT, error.result.reason)
        assertEquals(
            CoakkaTcpSecurityValidationCodes.FIELD_NOT_APPLICABLE,
            error.result.validationCode,
        )
        assertEquals(RuntimeTcpSecurityMode.PLAINTEXT, error.result.activeSecurity.mode)
        assertEquals(0, error.result.activeSecurity.credentialGeneration)
    }

    @Test
    fun tlsReloadRejectionPreservesTheActiveGeneration() {
        val fixtureRoot = System.getProperty("coakka.tls.fixture.root")?.let(::File) ?: return
        val capabilities = RuntimeHandle.readRuntimeCapabilities(TestSupport.runtimeLibPath())
        if (!capabilities.supports(CoakkaRuntimeCapabilities.TCP_TLS)) {
            return
        }
        val handle = openHandle(
            suffix = "tls-reload",
            security = tlsSpec(fixtureRoot, 1, "jvm-generation-1"),
        )
        try {
            val startup = assertNotNull(handle.startupSecurityResult())
            assertTrue(startup.applied())
            assertEquals(1, startup.activeSecurity.credentialGeneration)
            assertEquals("jvm-generation-1", startup.activeSecurity.credentialId)
            val fingerprint = startup.activeSecurity.identityFingerprintSha256
            assertEquals(64, fingerprint.length)

            handle.start()
            val rejected = handle.applyTcpSecurity(
                tlsSpec(fixtureRoot, 2, "jvm-generation-2-bad").copy(
                    privateKeyFile = fixtureRoot.resolve("client.key").absolutePath,
                ),
            )
            assertEquals(CoakkaStatus.ERR_INVALID_ARG, rejected.status)
            assertFalse(rejected.changed)
            assertEquals(CoakkaTransportApplyReasons.CREDENTIAL_REJECTED, rejected.reason)
            assertEquals(1, rejected.activeSecurity.credentialGeneration)
            assertEquals("jvm-generation-1", rejected.activeSecurity.credentialId)
            assertEquals(fingerprint, rejected.activeSecurity.identityFingerprintSha256)
            assertEquals(1, handle.tcpSecurityInfo().credentialGeneration)

            val applied = handle.applyTcpSecurity(tlsSpec(fixtureRoot, 2, "jvm-generation-2"))
            assertTrue(applied.applied())
            assertTrue(applied.changed)
            assertEquals(2, applied.activeSecurity.credentialGeneration)

            val stale = handle.applyTcpSecurity(tlsSpec(fixtureRoot, 1, "jvm-generation-1-stale"))
            assertEquals(CoakkaStatus.ERR_INVALID_ARG, stale.status)
            assertFalse(stale.changed)
            assertEquals(CoakkaTransportApplyReasons.STALE_CREDENTIAL_GENERATION, stale.reason)
            assertEquals(2, stale.activeSecurity.credentialGeneration)
        } finally {
            handle.close()
        }
    }

    private fun openHandle(
        suffix: String,
        connection: RuntimeTcpConnectionStrategySpec? = null,
        security: RuntimeTcpSecuritySpec? = null,
    ): RuntimeHandle =
        RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-transport-test",
                nodeId = "node-$suffix",
                routes = TestSupport.localRoutes("svc.echo"),
                connectionStrategy = connection,
                security = security,
            ),
        )

    private fun tlsSpec(root: File, generation: Long, credentialId: String) =
        RuntimeTcpSecuritySpec(
            mode = RuntimeTcpSecurityMode.TLS,
            credentialGeneration = generation,
            credentialId = credentialId,
            caCertificateFile = root.resolve("ca.pem").absolutePath,
            identityCertificateFile = root.resolve("server.pem").absolutePath,
            privateKeyFile = root.resolve("server.key").absolutePath,
        )
}
