package coakka.v2.connector

import java.util.Locale

/** TCP connection mode value from the public runtime C ABI. */
data class RuntimeTcpConnectionMode(val value: Int) {
    companion object {
        @JvmField val PER_EXCHANGE = RuntimeTcpConnectionMode(0)
        @JvmField val BOUNDED_POOL = RuntimeTcpConnectionMode(1)
        @JvmField val PERSISTENT_SINGLE_FLIGHT = RuntimeTcpConnectionMode(2)
        @JvmField val MULTIPLEXING = RuntimeTcpConnectionMode(3)

        /** Preserves an unknown ABI value so native validation can reject it structurally. */
        @JvmStatic fun of(value: Int): RuntimeTcpConnectionMode = RuntimeTcpConnectionMode(value)

        /** Parses the stable connector configuration names used by framework adapters. */
        @JvmStatic
        fun fromConfigValue(value: String): RuntimeTcpConnectionMode =
            when (value.normalizedConfigValue()) {
                "per-exchange" -> PER_EXCHANGE
                "bounded-pool" -> BOUNDED_POOL
                "persistent-single-flight" -> PERSISTENT_SINGLE_FLIGHT
                "multiplexing" -> MULTIPLEXING
                else -> throw IllegalArgumentException("unknown TCP connection strategy: $value")
            }
    }
}

/** TLS mode value from the public runtime C ABI. */
data class RuntimeTcpSecurityMode(val value: Int) {
    companion object {
        @JvmField val PLAINTEXT = RuntimeTcpSecurityMode(0)
        @JvmField val TLS = RuntimeTcpSecurityMode(1)
        @JvmField val MUTUAL_TLS = RuntimeTcpSecurityMode(2)

        /** Preserves an unknown ABI value so native validation can reject it structurally. */
        @JvmStatic fun of(value: Int): RuntimeTcpSecurityMode = RuntimeTcpSecurityMode(value)

        /** Parses plaintext, TLS, and mTLS connector configuration names. */
        @JvmStatic
        fun fromConfigValue(value: String): RuntimeTcpSecurityMode =
            when (value.normalizedConfigValue()) {
                "plaintext" -> PLAINTEXT
                "tls" -> TLS
                "mutual-tls", "mtls" -> MUTUAL_TLS
                else -> throw IllegalArgumentException("unknown TCP security mode: $value")
            }
    }
}

/** Existing-session policy used after a successful TLS credential reload. */
data class RuntimeTlsReloadMode(val value: Int) {
    companion object {
        @JvmField val GRACEFUL = RuntimeTlsReloadMode(0)
        @JvmField val DRAIN_EXISTING_CONNECTIONS = RuntimeTlsReloadMode(1)

        /** Preserves an unknown ABI value so native validation can reject it structurally. */
        @JvmStatic fun of(value: Int): RuntimeTlsReloadMode = RuntimeTlsReloadMode(value)

        /** Parses the stable existing-session policy names used by framework adapters. */
        @JvmStatic
        fun fromConfigValue(value: String): RuntimeTlsReloadMode =
            when (value.normalizedConfigValue()) {
                "graceful" -> GRACEFUL
                "drain-existing-connections" -> DRAIN_EXISTING_CONNECTIONS
                else -> throw IllegalArgumentException("unknown TLS reload mode: $value")
            }
    }
}

/** Startup-only connection strategy. Null tuning values request runtime defaults. */
data class RuntimeTcpConnectionStrategySpec @JvmOverloads constructor(
    val mode: RuntimeTcpConnectionMode = RuntimeTcpConnectionMode.PER_EXCHANGE,
    val maxConnections: Int? = null,
    val maxRequestsPerConnection: Long? = null,
    val idleTimeoutMs: Long? = null,
)

/**
 * File-backed TCP security policy.
 *
 * Paths are borrowed only during the synchronous native apply. The runtime loads an immutable
 * private credential context and never returns paths or secret bytes through result snapshots.
 */
data class RuntimeTcpSecuritySpec @JvmOverloads constructor(
    val mode: RuntimeTcpSecurityMode = RuntimeTcpSecurityMode.PLAINTEXT,
    val reloadMode: RuntimeTlsReloadMode = RuntimeTlsReloadMode.GRACEFUL,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
)

/** Copy-safe effective TCP connection configuration. */
data class RuntimeTcpConnectionConfigSnapshot(
    val defaultsRevision: Int,
    val mode: RuntimeTcpConnectionMode,
    val applicableFields: Long,
    val explicitlyConfiguredFields: Long,
    val defaultedFields: Long,
    val configurableFields: Long,
    val maxConnections: Int,
    val maxRequestsPerConnection: Long,
    val idleTimeoutMs: Long,
)

/** Structured result of one atomic connection-strategy apply attempt. */
data class RuntimeTcpConnectionApplyResult(
    val status: Int,
    val changed: Boolean,
    val reason: Int,
    val reasonName: String,
    val runtimeState: Int,
    val validationCode: Int,
    val validationField: Long,
    val minimumValue: Long,
    val maximumValue: Long,
    val activeConfig: RuntimeTcpConnectionConfigSnapshot,
) {
    fun applied(): Boolean = status == CoakkaStatus.OK
}

/** Copy-safe, non-secret active TLS configuration and certificate identity metadata. */
data class RuntimeTcpSecurityInfoSnapshot(
    val mode: RuntimeTcpSecurityMode,
    val credentialSourceKind: Int,
    val reloadMode: RuntimeTlsReloadMode,
    val reloadStatus: Int,
    val credentialGeneration: Long,
    val credentialId: String,
    val minimumProtocolVersion: Int,
    val maximumProtocolVersion: Int,
    val inboundVerificationFlags: Long,
    val outboundVerificationFlags: Long,
    val identityNotBeforeUnixSeconds: Long,
    val identityNotAfterUnixSeconds: Long,
    val identityFingerprintSha256: String,
)

/** Structured result of one atomic security apply or credential reload attempt. */
data class RuntimeTcpSecurityApplyResult(
    val status: Int,
    val changed: Boolean,
    val reason: Int,
    val reasonName: String,
    val runtimeState: Int,
    val validationCode: Int,
    val validationField: Long,
    val activeSecurity: RuntimeTcpSecurityInfoSnapshot,
) {
    fun applied(): Boolean = status == CoakkaStatus.OK
}

/** Compiled, entitled, and effective capability truth exported by the runtime binary. */
data class RuntimeCapabilitiesSnapshot(
    val edition: Int,
    val licenseStatus: Int,
    val compiledCapabilities: Long,
    val entitledCapabilities: Long,
    val effectiveCapabilities: Long,
    val tcpConnectionDefaultsRevision: Int,
) {
    fun supports(capabilities: Long): Boolean =
        (effectiveCapabilities and capabilities) == capabilities
}

/** Startup connection rejection with the effective configuration that remains selected. */
class RuntimeTcpConnectionApplyException(
    val result: RuntimeTcpConnectionApplyResult,
) : CoakkaException(
    "tcp connection strategy apply failed rc=${result.status} " +
        "(${statusName(result.status)}) reason=${result.reasonName} " +
        "validation=${result.validationCode} category=CONFIGURATION",
)

/** Startup security rejection with the non-secret credential generation that remains active. */
class RuntimeTcpSecurityApplyException(
    val result: RuntimeTcpSecurityApplyResult,
) : CoakkaException(
    "tcp security apply failed rc=${result.status} (${statusName(result.status)}) " +
        "reason=${result.reasonName} validation=${result.validationCode} " +
        "category=CONFIGURATION",
)

private fun String.normalizedConfigValue(): String =
    trim().lowercase(Locale.ROOT).replace('_', '-')
