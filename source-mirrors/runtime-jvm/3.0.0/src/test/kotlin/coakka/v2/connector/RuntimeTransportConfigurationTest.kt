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
    fun usesTheFrozenCoreAbiThroughThePrivateJniProjection() {
        assertEquals(1, CoakkaRuntimeAbi.VERSION)
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
            val startup = handle.startupConfig()
            val delivery = handle.deliveryConfig()
            val security = handle.tcpSecurityInfo()
            assertTrue(startup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.EXECUTION))
            assertTrue(startup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION))
            assertEquals(CoakkaRuntimeStartupCpuPolicies.AUTO, startup.execution.requestedPolicy)
            assertEquals(connection, startup.connection)
            if (startup.execution.supported) {
                assertTrue(startup.execution.selectedCpuIds.size in 1..2)
                assertTrue(
                    startup.execution.selectedCpuIds.none {
                        it == CoakkaRuntimeStartupCpuPolicies.UNKNOWN_CPU_ID
                    },
                )
            } else {
                assertTrue(startup.execution.selectedCpuIds.isEmpty())
            }
            assertEquals(capabilities.tcpConnectionDefaultsRevision, connection.defaultsRevision)
            assertEquals(RuntimeTcpConnectionMode.MULTIPLEXING, connection.mode)
            assertTrue(delivery.compiled)
            assertEquals(CoakkaRuntimeDeliveryModes.OWNED_QUEUE, delivery.requestMode)
            assertEquals(CoakkaRuntimeDeliveryModes.OWNED_QUEUE, delivery.responseMode)
            assertEquals(8, delivery.remoteBatchMaxCalls)
            assertTrue(delivery.slotCapacity in 1..64)
            assertEquals(0, delivery.slotCapacity and (delivery.slotCapacity - 1))
            assertTrue(delivery.byteCapacity in 64L * 1024L..4L * 1024L * 1024L)
            assertEquals(RuntimeTcpSecurityMode.PLAINTEXT, security.mode)
            assertEquals(0, security.credentialGeneration)
            assertTrue(security.credentialId.isEmpty())
            assertTrue(security.identityFingerprintSha256.isEmpty())
        } finally {
            handle.close()
        }
    }

    @Test
    fun omittedConnectionIntentUsesCoreDefaultAndExplicitIntentIsPreserved() {
        val omitted = openHandle("default-connection-omitted")
        val explicit = openHandle(
            suffix = "default-connection-explicit",
            connection = RuntimeTcpConnectionStrategySpec(RuntimeTcpConnectionMode.MULTIPLEXING),
        )
        try {
            val omittedStartup = omitted.startupConfig()
            val explicitStartup = explicit.startupConfig()
            assertTrue(omittedStartup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION))
            assertFalse(explicitStartup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION))
            assertEquals(RuntimeTcpConnectionMode.MULTIPLEXING, omittedStartup.connection.mode)
            assertEquals(
                RuntimeTcpConnectionMode.MULTIPLEXING,
                explicitStartup.connection.mode,
            )
        } finally {
            omitted.close()
            explicit.close()
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
            val startupConnection = handle.startupConfig().connection
            val startupSecurity = assertNotNull(handle.startupSecurityResult())
            assertEquals(startupMode, startupConnection.mode)
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
                assertEquals(mode, handle.startupConfig().connection.mode)
                assertEquals(mode, handle.tcpConnectionConfig().mode)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun invalidStartupModeIsRejectedByCoreDuringCreation() {
        val error = assertFailsWith<IllegalStateException> {
            openHandle(
                suffix = "invalid-mode",
                connection = RuntimeTcpConnectionStrategySpec(RuntimeTcpConnectionMode.of(999)),
            )
        }
        assertTrue(error.message.orEmpty().contains("runtime_create_native failed rc=-1"))
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
