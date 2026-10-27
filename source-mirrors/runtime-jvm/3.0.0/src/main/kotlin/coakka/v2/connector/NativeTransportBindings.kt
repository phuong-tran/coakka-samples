package coakka.v2.connector

import coakka.v2.connector.internal.NativeRecord

object CoakkaRuntimeCapabilities {
    const val TCP_BOUNDED_POOL = 1L shl 0
    const val TCP_POOL_TUNING = 1L shl 1
    const val TCP_TLS = 1L shl 2
    const val TCP_MUTUAL_TLS = 1L shl 3
    const val TLS_CREDENTIAL_RELOAD = 1L shl 4
    const val TLS_EXTERNAL_PROVIDER = 1L shl 5
    const val TCP_PERSISTENT_SINGLE_FLIGHT = 1L shl 6
    const val TCP_MULTIPLEXING = 1L shl 7
}

object CoakkaRuntimeEditions {
    const val UNKNOWN = 0
    const val COMMUNITY = 1
    const val PRO = 2
    const val ENTERPRISE = 3
    const val PRO_MAX = 4
}

object CoakkaRuntimeLicenseStatuses {
    const val NOT_REQUIRED = 0
    const val ACTIVE = 1
    const val GRACE = 2
    const val EXPIRED = 3
    const val INVALID = 4
    const val TIME_UNTRUSTED = 5
}

object CoakkaTlsCredentialSources {
    const val NONE = 0
    const val FILE = 1
    const val MEMORY = 2
    const val PROVIDER = 3
}

object CoakkaTlsReloadStatuses {
    const val NOT_CONFIGURED = 0
    const val NEVER_LOADED = 1
    const val ACTIVE = 2
    const val FAILED = 3
    const val EXPIRED = 4
}

object CoakkaTlsProtocolVersions {
    const val PROVIDER_DEFAULT = 0
    const val TLS_1_2 = 0x0303
    const val TLS_1_3 = 0x0304
}

object CoakkaTlsVerificationFlags {
    const val VERIFY_PEER = 1L shl 0
    const val VERIFY_PEER_IDENTITY = 1L shl 1
    const val REQUIRE_CLIENT_CERTIFICATE = 1L shl 2
}

/** Presence/provenance bits used by TCP connection option and config snapshots. */
object CoakkaTcpConnectionFields {
    const val MODE = 1L shl 0
    const val MAX_CONNECTIONS = 1L shl 1
    const val MAX_REQUESTS_PER_CONNECTION = 1L shl 2
    const val IDLE_TIMEOUT_MS = 1L shl 3
}

/** Presence and validation-field bits used by TCP security options. */
object CoakkaTcpSecurityFields {
    const val MODE = 1L shl 0
    const val CREDENTIAL_SOURCE = 1L shl 1
    const val RELOAD_MODE = 1L shl 2
    const val CREDENTIAL_GENERATION = 1L shl 3
    const val CREDENTIAL_ID = 1L shl 4
    const val CA_CERTIFICATE_FILE = 1L shl 5
    const val IDENTITY_CERTIFICATE_FILE = 1L shl 6
    const val PRIVATE_KEY_FILE = 1L shl 7
}

object CoakkaTransportApplyReasons {
    const val NONE = 0
    const val INVALID_ARGUMENT = 1
    const val FEATURE_UNAVAILABLE = 2
    const val FEATURE_NOT_ENTITLED = 3
    const val RUNTIME_NOT_CONFIGURABLE = 4
    const val SECURITY_MODE_CHANGE_REQUIRES_RECREATE = 5
    const val STALE_CREDENTIAL_GENERATION = 6
    const val CREDENTIAL_REJECTED = 7
    const val RESOURCE_FAILURE = 8
    const val ADAPTER_REJECTED = 9
}

object CoakkaTcpConnectionValidationCodes {
    const val VALID = 0
    const val INVALID_STRUCT_SIZE = 1
    const val UNKNOWN_FIELD = 2
    const val MODE_REQUIRED = 3
    const val UNKNOWN_MODE = 4
    const val FIELD_NOT_APPLICABLE = 5
    const val VALUE_OUT_OF_RANGE = 6
    const val FEATURE_UNAVAILABLE = 7
    const val FEATURE_NOT_ENTITLED = 8
    const val RESERVED_NONZERO = 9
    const val FIELD_OUTSIDE_STRUCT = 10
    const val VALUE_WITHOUT_FIELD = 11
}

object CoakkaTcpSecurityValidationCodes {
    const val VALID = 0
    const val INVALID_STRUCT_SIZE = 1
    const val UNKNOWN_FIELD = 2
    const val MODE_REQUIRED = 3
    const val UNKNOWN_MODE = 4
    const val RESERVED_NONZERO = 5
    const val FIELD_OUTSIDE_STRUCT = 6
    const val FIELD_NOT_APPLICABLE = 7
    const val REQUIRED_FIELD_MISSING = 8
    const val SOURCE_UNAVAILABLE = 9
    const val FEATURE_UNAVAILABLE = 10
    const val INVALID_GENERATION = 11
    const val CREDENTIAL_ID_TOO_LONG = 12
    const val VALUE_WITHOUT_FIELD = 13
}


internal fun RuntimeTcpConnectionStrategySpec.toNativeValues(): LongArray {
    var fields = CoakkaTcpConnectionFields.MODE
    if (maxConnections != null) fields = fields or CoakkaTcpConnectionFields.MAX_CONNECTIONS
    if (maxRequestsPerConnection != null) fields = fields or CoakkaTcpConnectionFields.MAX_REQUESTS_PER_CONNECTION
    if (idleTimeoutMs != null) fields = fields or CoakkaTcpConnectionFields.IDLE_TIMEOUT_MS
    return longArrayOf(fields, mode.value.toLong(), maxConnections?.toLong() ?: 0,
        maxRequestsPerConnection ?: 0, idleTimeoutMs ?: 0)
}

internal fun RuntimeTcpSecuritySpec.toNativeValues(): LongArray {
    var fields = CoakkaTcpSecurityFields.MODE
    var source = CoakkaTlsCredentialSources.NONE
    if (mode != RuntimeTcpSecurityMode.PLAINTEXT) {
        fields = 0xffL
        source = CoakkaTlsCredentialSources.FILE
    } else {
        if (reloadMode != RuntimeTlsReloadMode.GRACEFUL) fields = fields or CoakkaTcpSecurityFields.RELOAD_MODE
        if (credentialGeneration != 0L) fields = fields or CoakkaTcpSecurityFields.CREDENTIAL_GENERATION
        if (credentialId.isNotEmpty()) fields = fields or CoakkaTcpSecurityFields.CREDENTIAL_ID
        if (caCertificateFile.isNotEmpty() || identityCertificateFile.isNotEmpty() || privateKeyFile.isNotEmpty()) {
            fields = fields or CoakkaTcpSecurityFields.CREDENTIAL_SOURCE
            source = CoakkaTlsCredentialSources.FILE
        }
        if (caCertificateFile.isNotEmpty()) fields = fields or CoakkaTcpSecurityFields.CA_CERTIFICATE_FILE
        if (identityCertificateFile.isNotEmpty()) fields = fields or CoakkaTcpSecurityFields.IDENTITY_CERTIFICATE_FILE
        if (privateKeyFile.isNotEmpty()) fields = fields or CoakkaTcpSecurityFields.PRIVATE_KEY_FILE
    }
    return longArrayOf(fields, mode.value.toLong(), source.toLong(), reloadMode.value.toLong(), credentialGeneration)
}

internal fun RuntimeTcpSecuritySpec.toNativeBytes(): Array<ByteArray?> =
    arrayOf(
        credentialId.takeIf(String::isNotEmpty)?.utf8(),
        caCertificateFile.takeIf(String::isNotEmpty)?.utf8(),
        identityCertificateFile.takeIf(String::isNotEmpty)?.utf8(),
        privateKeyFile.takeIf(String::isNotEmpty)?.utf8(),
    )

internal fun NativeRecord.connectionSnapshot(offset: Int = 1): RuntimeTcpConnectionConfigSnapshot =
    RuntimeTcpConnectionConfigSnapshot(
        defaultsRevision = value(offset).toInt(),
        mode = RuntimeTcpConnectionMode.of(value(offset + 1).toInt()),
        applicableFields = value(offset + 2),
        explicitlyConfiguredFields = value(offset + 3),
        defaultedFields = value(offset + 4),
        configurableFields = value(offset + 5),
        maxConnections = value(offset + 6).toInt(),
        maxRequestsPerConnection = value(offset + 7),
        idleTimeoutMs = value(offset + 8),
    )

internal fun NativeRecord.startupConfigSnapshot(): RuntimeStartupConfigSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_startup_config")
    val selectedCount = value(10).toInt()
    require(selectedCount in 0..2) { "Core returned an invalid selected CPU count" }
    return RuntimeStartupConfigSnapshot(
        defaultsRevision = value(1).toInt(),
        defaultedGroups = value(2),
        strictNoDrop = value(3) != 0L,
        queueCapacity = value(4).toInt(),
        requestMaxFrameSize = value(5),
        runtimeState = value(6).toInt(),
        execution = RuntimeStartupExecutionSnapshot(
            requestedPolicy = value(7).toInt(),
            supported = value(8) != 0L,
            availableCpuCount = value(9).toInt(),
            selectedCpuIds = listOf(value(11), value(12)).take(selectedCount),
            startupApplied = value(13) != 0L,
        ),
        connection = connectionSnapshot(14),
        io = RuntimeStartupIoSnapshot(
            requested = value(23) != 0L,
            compiled = value(24) != 0L,
            probed = value(25) != 0L,
            supported = value(26) != 0L,
            effective = value(27) != 0L,
            probeError = value(28).toInt(),
        ),
        monitorEnabled = value(29) != 0L,
    )
}

internal fun NativeRecord.deliveryConfigSnapshot(): RuntimeDeliveryConfigSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_delivery_config")
    return RuntimeDeliveryConfigSnapshot(
        compiled = value(1) != 0L,
        requestMode = value(2).toInt(),
        responseMode = value(3).toInt(),
        remoteBatchMaxCalls = value(4).toInt(),
        slotCapacity = value(5).toInt(),
        byteCapacity = value(6),
    )
}

internal fun NativeRecord.connectionApplySnapshot(): RuntimeTcpConnectionApplyResult =
    RuntimeTcpConnectionApplyResult(
        status = value(0).toInt(), changed = value(1) != 0L,
        reason = value(2).toInt(), reasonName = text(0), runtimeState = value(3).toInt(),
        validationCode = value(4).toInt(), validationField = value(5),
        minimumValue = value(6), maximumValue = value(7), activeConfig = connectionSnapshot(8),
    )

internal fun NativeRecord.securitySnapshot(offset: Int = 1, textOffset: Int = 0): RuntimeTcpSecurityInfoSnapshot =
    RuntimeTcpSecurityInfoSnapshot(
        mode = RuntimeTcpSecurityMode.of(value(offset).toInt()),
        credentialSourceKind = value(offset + 1).toInt(),
        reloadMode = RuntimeTlsReloadMode.of(value(offset + 2).toInt()),
        reloadStatus = value(offset + 3).toInt(), credentialGeneration = value(offset + 4),
        credentialId = text(textOffset), minimumProtocolVersion = value(offset + 5).toInt(),
        maximumProtocolVersion = value(offset + 6).toInt(), inboundVerificationFlags = value(offset + 7),
        outboundVerificationFlags = value(offset + 8), identityNotBeforeUnixSeconds = value(offset + 9),
        identityNotAfterUnixSeconds = value(offset + 10), identityFingerprintSha256 = text(textOffset + 1),
    )

internal fun NativeRecord.securityApplySnapshot(): RuntimeTcpSecurityApplyResult =
    RuntimeTcpSecurityApplyResult(
        status = value(0).toInt(), changed = value(1) != 0L, reason = value(2).toInt(),
        reasonName = text(0), runtimeState = value(3).toInt(), validationCode = value(4).toInt(),
        validationField = value(5), activeSecurity = securitySnapshot(6, 1),
    )

internal fun NativeRecord.capabilitiesSnapshot(): RuntimeCapabilitiesSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_capabilities")
    return RuntimeCapabilitiesSnapshot(
        edition = value(1).toInt(), licenseStatus = value(2).toInt(),
        compiledCapabilities = value(3), entitledCapabilities = value(4),
        effectiveCapabilities = value(5), tcpConnectionDefaultsRevision = value(6).toInt(),
    )
}

internal fun String.utf8(): ByteArray = toByteArray(Charsets.UTF_8)
