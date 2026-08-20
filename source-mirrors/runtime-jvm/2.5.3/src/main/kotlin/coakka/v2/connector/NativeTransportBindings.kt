package coakka.v2.connector

import com.sun.jna.Structure

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

internal object NativeTransportFields {
    const val SECURITY_ALL =
        CoakkaTcpSecurityFields.MODE or
            CoakkaTcpSecurityFields.CREDENTIAL_SOURCE or
            CoakkaTcpSecurityFields.RELOAD_MODE or
            CoakkaTcpSecurityFields.CREDENTIAL_GENERATION or
            CoakkaTcpSecurityFields.CREDENTIAL_ID or
            CoakkaTcpSecurityFields.CA_CERTIFICATE_FILE or
            CoakkaTcpSecurityFields.IDENTITY_CERTIFICATE_FILE or
            CoakkaTcpSecurityFields.PRIVATE_KEY_FILE
}

@Structure.FieldOrder(
    "struct_size",
    "fields",
    "mode",
    "reserved",
    "max_connections",
    "reserved2",
    "max_requests_per_connection",
    "idle_timeout_ms",
)
open class NativeTcpConnectionOptions : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var fields: Long = 0
    @JvmField var mode: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var max_connections: Int = 0
    @JvmField var reserved2: Int = 0
    @JvmField var max_requests_per_connection: Long = 0
    @JvmField var idle_timeout_ms: Long = 0
}

@Structure.FieldOrder(
    "struct_size",
    "code",
    "reserved",
    "field",
    "minimum_value",
    "maximum_value",
)
open class NativeTcpConnectionValidation : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var code: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var field: Long = 0
    @JvmField var minimum_value: Long = 0
    @JvmField var maximum_value: Long = 0
}

@Structure.FieldOrder(
    "struct_size",
    "defaults_revision",
    "mode",
    "applicable_fields",
    "explicitly_configured_fields",
    "defaulted_fields",
    "configurable_fields",
    "max_connections",
    "reserved",
    "max_requests_per_connection",
    "idle_timeout_ms",
)
open class NativeTcpConnectionConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var defaults_revision: Int = 0
    @JvmField var mode: Int = 0
    @JvmField var applicable_fields: Long = 0
    @JvmField var explicitly_configured_fields: Long = 0
    @JvmField var defaulted_fields: Long = 0
    @JvmField var configurable_fields: Long = 0
    @JvmField var max_connections: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var max_requests_per_connection: Long = 0
    @JvmField var idle_timeout_ms: Long = 0
}

@Structure.FieldOrder(
    "struct_size",
    "apply_status",
    "changed",
    "reason",
    "runtime_state",
    "validation",
    "effective_config",
)
open class NativeTcpConnectionApplyResult : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var apply_status: Int = 0
    @JvmField var changed: Int = 0
    @JvmField var reason: Int = 0
    @JvmField var runtime_state: Int = 0
    @JvmField var validation: NativeTcpConnectionValidation = NativeTcpConnectionValidation()
    @JvmField var effective_config: NativeTcpConnectionConfig = NativeTcpConnectionConfig()
}

@Structure.FieldOrder(
    "struct_size",
    "fields",
    "mode",
    "credential_source_kind",
    "reload_mode",
    "reserved",
    "credential_generation",
    "credential_id",
    "ca_certificate_file",
    "identity_certificate_file",
    "private_key_file",
)
open class NativeTcpSecurityOptions : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var fields: Long = 0
    @JvmField var mode: Int = 0
    @JvmField var credential_source_kind: Int = 0
    @JvmField var reload_mode: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var credential_generation: Long = 0
    @JvmField var credential_id: String? = null
    @JvmField var ca_certificate_file: String? = null
    @JvmField var identity_certificate_file: String? = null
    @JvmField var private_key_file: String? = null
}

@Structure.FieldOrder("struct_size", "code", "reserved", "field")
open class NativeTcpSecurityValidation : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var code: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var field: Long = 0
}

@Structure.FieldOrder(
    "struct_size",
    "mode",
    "credential_source_kind",
    "reload_mode",
    "reload_status",
    "credential_generation",
    "credential_id",
)
open class NativeTcpSecurityConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var mode: Int = 0
    @JvmField var credential_source_kind: Int = 0
    @JvmField var reload_mode: Int = 0
    @JvmField var reload_status: Int = 0
    @JvmField var credential_generation: Long = 0
    @JvmField var credential_id: String? = null
}

@Structure.FieldOrder(
    "struct_size",
    "minimum_protocol_version",
    "maximum_protocol_version",
    "inbound_verification_flags",
    "outbound_verification_flags",
    "identity_not_before_unix_seconds",
    "identity_not_after_unix_seconds",
    "credential_id_value",
    "identity_fingerprint_sha256",
)
open class NativeTcpSecurityIdentityInfo : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var minimum_protocol_version: Int = 0
    @JvmField var maximum_protocol_version: Int = 0
    @JvmField var inbound_verification_flags: Long = 0
    @JvmField var outbound_verification_flags: Long = 0
    @JvmField var identity_not_before_unix_seconds: Long = 0
    @JvmField var identity_not_after_unix_seconds: Long = 0
    @JvmField var credential_id_value: ByteArray = ByteArray(128)
    @JvmField var identity_fingerprint_sha256: ByteArray = ByteArray(65)
}

@Structure.FieldOrder("struct_size", "config", "identity")
open class NativeTcpSecurityInfo : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var config: NativeTcpSecurityConfig = NativeTcpSecurityConfig()
    @JvmField var identity: NativeTcpSecurityIdentityInfo = NativeTcpSecurityIdentityInfo()
}

@Structure.FieldOrder(
    "struct_size",
    "apply_status",
    "changed",
    "reason",
    "runtime_state",
    "validation",
    "active_security",
)
open class NativeTcpSecurityApplyResult : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var apply_status: Int = 0
    @JvmField var changed: Int = 0
    @JvmField var reason: Int = 0
    @JvmField var runtime_state: Int = 0
    @JvmField var validation: NativeTcpSecurityValidation = NativeTcpSecurityValidation()
    @JvmField var active_security: NativeTcpSecurityInfo = NativeTcpSecurityInfo()
}

@Structure.FieldOrder(
    "struct_size",
    "edition",
    "license_status",
    "compiled_capabilities",
    "entitled_capabilities",
    "effective_capabilities",
    "tcp_connection_defaults_revision",
    "reserved",
)
open class NativeRuntimeCapabilities : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var edition: Int = 0
    @JvmField var license_status: Int = 0
    @JvmField var compiled_capabilities: Long = 0
    @JvmField var entitled_capabilities: Long = 0
    @JvmField var effective_capabilities: Long = 0
    @JvmField var tcp_connection_defaults_revision: Int = 0
    @JvmField var reserved: Int = 0
}

internal fun RuntimeTcpConnectionStrategySpec.toNative(): NativeTcpConnectionOptions =
    NativeTcpConnectionOptions().also { options ->
        options.struct_size = options.size().toLong()
        options.fields = CoakkaTcpConnectionFields.MODE
        options.mode = mode.value
        maxConnections?.let {
            options.fields = options.fields or CoakkaTcpConnectionFields.MAX_CONNECTIONS
            options.max_connections = it
        }
        maxRequestsPerConnection?.let {
            options.fields = options.fields or CoakkaTcpConnectionFields.MAX_REQUESTS_PER_CONNECTION
            options.max_requests_per_connection = it
        }
        idleTimeoutMs?.let {
            options.fields = options.fields or CoakkaTcpConnectionFields.IDLE_TIMEOUT_MS
            options.idle_timeout_ms = it
        }
        options.write()
    }

internal fun RuntimeTcpSecuritySpec.toNative(): NativeTcpSecurityOptions =
    NativeTcpSecurityOptions().also { options ->
        options.struct_size = options.size().toLong()
        options.fields = CoakkaTcpSecurityFields.MODE
        options.mode = mode.value
        if (mode != RuntimeTcpSecurityMode.PLAINTEXT) {
            options.fields = NativeTransportFields.SECURITY_ALL
            options.credential_source_kind = CoakkaTlsCredentialSources.FILE
            options.reload_mode = reloadMode.value
            options.credential_generation = credentialGeneration
            options.credential_id = credentialId
            options.ca_certificate_file = caCertificateFile
            options.identity_certificate_file = identityCertificateFile
            options.private_key_file = privateKeyFile
        } else {
            if (reloadMode != RuntimeTlsReloadMode.GRACEFUL) {
                options.fields = options.fields or CoakkaTcpSecurityFields.RELOAD_MODE
                options.reload_mode = reloadMode.value
            }
            if (credentialGeneration != 0L) {
                options.fields = options.fields or CoakkaTcpSecurityFields.CREDENTIAL_GENERATION
                options.credential_generation = credentialGeneration
            }
            if (credentialId.isNotEmpty()) {
                options.fields = options.fields or CoakkaTcpSecurityFields.CREDENTIAL_ID
                options.credential_id = credentialId
            }
            if (
                caCertificateFile.isNotEmpty() ||
                identityCertificateFile.isNotEmpty() ||
                privateKeyFile.isNotEmpty()
            ) {
                options.fields = options.fields or CoakkaTcpSecurityFields.CREDENTIAL_SOURCE
                options.credential_source_kind = CoakkaTlsCredentialSources.FILE
            }
            if (caCertificateFile.isNotEmpty()) {
                options.fields = options.fields or CoakkaTcpSecurityFields.CA_CERTIFICATE_FILE
                options.ca_certificate_file = caCertificateFile
            }
            if (identityCertificateFile.isNotEmpty()) {
                options.fields = options.fields or CoakkaTcpSecurityFields.IDENTITY_CERTIFICATE_FILE
                options.identity_certificate_file = identityCertificateFile
            }
            if (privateKeyFile.isNotEmpty()) {
                options.fields = options.fields or CoakkaTcpSecurityFields.PRIVATE_KEY_FILE
                options.private_key_file = privateKeyFile
            }
        }
        options.write()
    }

internal fun NativeTcpConnectionConfig.toSnapshot(): RuntimeTcpConnectionConfigSnapshot =
    RuntimeTcpConnectionConfigSnapshot(
        defaultsRevision = defaults_revision,
        mode = RuntimeTcpConnectionMode.of(mode),
        applicableFields = applicable_fields,
        explicitlyConfiguredFields = explicitly_configured_fields,
        defaultedFields = defaulted_fields,
        configurableFields = configurable_fields,
        maxConnections = max_connections,
        maxRequestsPerConnection = max_requests_per_connection,
        idleTimeoutMs = idle_timeout_ms,
    )

internal fun NativeTcpConnectionApplyResult.toSnapshot(
    lib: CoakkaV2Library,
    callStatus: Int,
): RuntimeTcpConnectionApplyResult {
    check(callStatus == apply_status) {
        "tcp connection apply returned rc=$callStatus but result status=$apply_status"
    }
    return RuntimeTcpConnectionApplyResult(
        status = apply_status,
        changed = changed != 0,
        reason = reason,
        reasonName = transportApplyReasonName(lib, reason),
        runtimeState = runtime_state,
        validationCode = validation.code,
        validationField = validation.field,
        minimumValue = validation.minimum_value,
        maximumValue = validation.maximum_value,
        activeConfig = effective_config.toSnapshot(),
    )
}

internal fun NativeTcpSecurityInfo.toSnapshot(): RuntimeTcpSecurityInfoSnapshot =
    RuntimeTcpSecurityInfoSnapshot(
        mode = RuntimeTcpSecurityMode.of(config.mode),
        credentialSourceKind = config.credential_source_kind,
        reloadMode = RuntimeTlsReloadMode.of(config.reload_mode),
        reloadStatus = config.reload_status,
        credentialGeneration = config.credential_generation,
        credentialId = identity.credential_id_value.decodeFixedUtf8(),
        minimumProtocolVersion = identity.minimum_protocol_version,
        maximumProtocolVersion = identity.maximum_protocol_version,
        inboundVerificationFlags = identity.inbound_verification_flags,
        outboundVerificationFlags = identity.outbound_verification_flags,
        identityNotBeforeUnixSeconds = identity.identity_not_before_unix_seconds,
        identityNotAfterUnixSeconds = identity.identity_not_after_unix_seconds,
        identityFingerprintSha256 = identity.identity_fingerprint_sha256.decodeFixedUtf8(),
    )

internal fun NativeTcpSecurityApplyResult.toSnapshot(
    lib: CoakkaV2Library,
    callStatus: Int,
): RuntimeTcpSecurityApplyResult {
    check(callStatus == apply_status) {
        "tcp security apply returned rc=$callStatus but result status=$apply_status"
    }
    return RuntimeTcpSecurityApplyResult(
        status = apply_status,
        changed = changed != 0,
        reason = reason,
        reasonName = transportApplyReasonName(lib, reason),
        runtimeState = runtime_state,
        validationCode = validation.code,
        validationField = validation.field,
        activeSecurity = active_security.toSnapshot(),
    )
}

internal fun NativeRuntimeCapabilities.toSnapshot(): RuntimeCapabilitiesSnapshot =
    RuntimeCapabilitiesSnapshot(
        edition = edition,
        licenseStatus = license_status,
        compiledCapabilities = compiled_capabilities,
        entitledCapabilities = entitled_capabilities,
        effectiveCapabilities = effective_capabilities,
        tcpConnectionDefaultsRevision = tcp_connection_defaults_revision,
    )

private fun ByteArray.decodeFixedUtf8(): String {
    val length = indexOf(0).let { if (it >= 0) it else size }
    return copyOf(length).toString(Charsets.UTF_8)
}
