package coakka.v2.connector.protocol

/** Core numeric values are lossless, including unknown signed values. */
data class ConnectorMessageKind(val value: Int) {
    companion object {
        @JvmField val UNSPECIFIED = ConnectorMessageKind(0)
        @JvmField val REQUEST = ConnectorMessageKind(1)
        @JvmField val RESPONSE = ConnectorMessageKind(2)
        @JvmField val EVENT = ConnectorMessageKind(3)
        private val known = arrayOf(UNSPECIFIED, REQUEST, RESPONSE, EVENT)
        @JvmStatic fun of(value: Int): ConnectorMessageKind = known.getOrNull(value) ?: ConnectorMessageKind(value)
    }
}

/** Application outcome, never a native delivery cause. Unknown is not OK. */
data class ConnectorBusinessStatus(val value: Int) {
    companion object {
        @JvmField val UNSPECIFIED = ConnectorBusinessStatus(0)
        @JvmField val OK = ConnectorBusinessStatus(1)
        @JvmField val ERROR = ConnectorBusinessStatus(2)
        private val known = arrayOf(UNSPECIFIED, OK, ERROR)
        @JvmStatic fun of(value: Int): ConnectorBusinessStatus = known.getOrNull(value) ?: ConnectorBusinessStatus(value)
    }
}

data class ConnectorDeliveryHint(val value: Int) {
    companion object {
        @JvmField val UNSPECIFIED = ConnectorDeliveryHint(0)
        @JvmField val ROUTER_DEFAULT = ConnectorDeliveryHint(1)
        @JvmField val PREFER_LOCAL = ConnectorDeliveryHint(2)
        @JvmField val REQUIRE_LOCAL = ConnectorDeliveryHint(3)
        @JvmField val REQUIRE_REMOTE = ConnectorDeliveryHint(4)
        private val known = arrayOf(UNSPECIFIED, ROUTER_DEFAULT, PREFER_LOCAL, REQUIRE_LOCAL, REQUIRE_REMOTE)
        @JvmStatic fun of(value: Int): ConnectorDeliveryHint = known.getOrNull(value) ?: ConnectorDeliveryHint(value)
    }
}

/** Application-declared body encoding; Core does not decode the body. */
data class ConnectorPayloadFormat(val value: Int) {
    companion object {
        @JvmField val UNSPECIFIED = ConnectorPayloadFormat(0)
        @JvmField val JSON = ConnectorPayloadFormat(1)
        @JvmField val PROTOBUF = ConnectorPayloadFormat(2)
        @JvmField val THRIFT = ConnectorPayloadFormat(3)
        @JvmField val MSGPACK = ConnectorPayloadFormat(4)
        @JvmField val TEXT = ConnectorPayloadFormat(5)
        @JvmField val BINARY = ConnectorPayloadFormat(6)
        private val known = arrayOf(UNSPECIFIED, JSON, PROTOBUF, THRIFT, MSGPACK, TEXT, BINARY)
        @JvmStatic fun of(value: Int): ConnectorPayloadFormat = known.getOrNull(value) ?: ConnectorPayloadFormat(value)
    }
}

/**
 * First-class payload identity carried on typed request/reply paths.
 *
 * @property messageType Business / contract identity for the payload.
 * @property payloadSchemaVersion Version of the business payload schema.
 * @property payloadFormat Byte encoding or container format of the payload.
 */
data class ConnectorPayloadIdentity(
    val messageType: String,
    val payloadSchemaVersion: Long = 1,
    val payloadFormat: ConnectorPayloadFormat,
) {
    /** Returns whether this identity is strong enough for the typed connector path. */
    fun isTyped(): Boolean =
        messageType.isNotBlank() &&
            payloadSchemaVersion >= 1 &&
            payloadFormat != ConnectorPayloadFormat.UNSPECIFIED

    /** Enforces the typed connector payload-identity contract. */
    fun requireTyped(what: String = "payload identity"): ConnectorPayloadIdentity {
        require(messageType.isNotBlank()) { "$what requires messageType" }
        require(payloadSchemaVersion >= 1) { "$what requires payloadSchemaVersion >= 1" }
        require(payloadFormat != ConnectorPayloadFormat.UNSPECIFIED) {
            "$what requires declared payloadFormat"
        }
        return this
    }

    companion object {
        /**
         * Builds a typed payload identity for UTF-8 text payloads.
         *
         * This is the common first-run helper; advanced code can still construct
         * [ConnectorPayloadIdentity] directly for JSON, Protobuf, binary, or raw paths.
         */
        @JvmStatic
        @JvmOverloads
        fun text(messageType: String, payloadSchemaVersion: Long = 1): ConnectorPayloadIdentity =
            ConnectorPayloadIdentity(
                messageType = messageType,
                payloadSchemaVersion = payloadSchemaVersion,
                payloadFormat = ConnectorPayloadFormat.TEXT,
            )

        /** Builds a typed payload identity with explicit connector-facing format. */
        @JvmStatic
        @JvmOverloads
        fun of(
            messageType: String,
            payloadFormat: ConnectorPayloadFormat,
            payloadSchemaVersion: Long = 1,
        ): ConnectorPayloadIdentity =
            ConnectorPayloadIdentity(
                messageType = messageType,
                payloadSchemaVersion = payloadSchemaVersion,
                payloadFormat = payloadFormat,
            )
    }
}

/**
 * Copy-owned language values, encoded and decoded only by Core.
 *
 * @property messageId Unique identifier for this message instance.
 * @property correlationId Request identifier that a response refers to.
 * @property source Logical sender identity.
 * @property target Logical runtime target.
 * @property replyTo Optional reply target declared by the caller.
 * @property kind Message shape on the main channel.
 * @property oneWay Whether the caller expects no response.
 * @property timeoutMs Timeout hint carried to runtime and peers.
 * @property payload Opaque message body.
 * @property headers Extensible metadata bag for connector concerns.
 * @property status Business-layer outcome for response messages.
 * @property errorCode Stable business error code for responses.
 * @property errorMessage Human-readable business error detail.
 * @property deliveryHint Routing preference requested by the caller.
 * @property messageType Business / contract identity of the payload bytes.
 * @property payloadSchemaVersion Version of the business payload contract.
 * @property payloadFormat Byte encoding or container format of the payload bytes.
 */
data class ConnectorEnvelope(
    val messageId: String,
    val correlationId: String = "",
    val source: String,
    val target: String,
    val replyTo: String = "",
    val kind: ConnectorMessageKind,
    val oneWay: Boolean,
    val timeoutMs: Int = 0,
    val payload: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
    val status: ConnectorBusinessStatus = ConnectorBusinessStatus.OK,
    val errorCode: String = "",
    val errorMessage: String = "",
    val deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    val messageType: String = "",
    val payloadSchemaVersion: Long = 0,
    val payloadFormat: ConnectorPayloadFormat = ConnectorPayloadFormat.UNSPECIFIED,
) {
    /** Decodes the opaque payload as UTF-8 for simple text-first demos and tests. */
    fun payloadUtf8(): String = payload.toString(Charsets.UTF_8)

    /** Returns the envelope payload identity as a dedicated value object. */
    fun payloadIdentity(): ConnectorPayloadIdentity =
        ConnectorPayloadIdentity(
            messageType = messageType,
            payloadSchemaVersion = payloadSchemaVersion,
            payloadFormat = payloadFormat,
        )

    /** Returns whether this envelope carries a fully declared typed payload identity. */
    fun hasTypedPayloadIdentity(): Boolean = payloadIdentity().isTyped()

    /** Enforces that this envelope satisfies the typed connector payload contract. */
    fun requireTypedPayloadIdentity(what: String = "typed envelope"): ConnectorEnvelope {
        payloadIdentity().requireTyped(what)
        return this
    }

    /** Returns a copy with the provided typed payload identity attached. */
    fun withPayloadIdentity(payloadIdentity: ConnectorPayloadIdentity): ConnectorEnvelope {
        val typedIdentity = payloadIdentity.requireTyped("payloadIdentity")
        return copy(
            messageType = typedIdentity.messageType,
            payloadSchemaVersion = typedIdentity.payloadSchemaVersion,
            payloadFormat = typedIdentity.payloadFormat,
        )
    }
}

/**
 * Public deadletter view returned when runtime delivery fails before business handling.
 *
 * @property originalEnvelope Original request when present; absence is not a zero message.
 * @property reason Core numeric delivery outcome, including unknown values.
 * @property detail Runtime-provided failure detail for logs or diagnostics.
 * @property activeGeneration Runtime generation active at failure time.
 * @property resolvedHost Resolved host if failure happened after route selection.
 * @property resolvedPort Resolved port if failure happened after route selection.
 */
data class ConnectorDeadletter(
    val originalEnvelope: ConnectorEnvelope?,
    val reason: ConnectorDeadletterReason,
    val detail: String,
    val activeGeneration: Long,
    val resolvedHost: String,
    val resolvedPort: Long,
    val endpointAttemptCount: Long,
    val failure: ConnectorNativeFailure?,
    val failureDetails: Map<String, String>,
)

/** Native causal dimensions issued by Core; never inferred from detail text. */
data class ConnectorNativeFailure(
    val domain: Long,
    val phase: Long,
    val code: Long,
    val certainty: Long,
    val scope: Long,
)

data class ConnectorDeadletterReason(val value: Int) {
    companion object {
        @JvmField val UNSPECIFIED = ConnectorDeadletterReason(0)
        @JvmField val NO_ACTIVE_SNAPSHOT = ConnectorDeadletterReason(1)
        @JvmField val ROUTE_MISS = ConnectorDeadletterReason(2)
        @JvmField val NO_RESPONSIBLE_HOST = ConnectorDeadletterReason(3)
        @JvmField val QUEUE_REJECTED = ConnectorDeadletterReason(4)
        @JvmField val LOCAL_HANDOFF_FAILED = ConnectorDeadletterReason(5)
        @JvmField val DELIVERY_FAILED = ConnectorDeadletterReason(6)
        @JvmField val REMOTE_TRANSPORT_FAILED = ConnectorDeadletterReason(7)
        @JvmField val RUNTIME_STOPPED = ConnectorDeadletterReason(8)
        @JvmField val INVALID_ENVELOPE = ConnectorDeadletterReason(9)
        @JvmField val ENDPOINT_UNAVAILABLE = ConnectorDeadletterReason(10)
        @JvmField val REMOTE_REPLY_TIMEOUT = ConnectorDeadletterReason(11)
        @JvmField val LOCALITY_MISMATCH = ConnectorDeadletterReason(12)
        @JvmField val EXPIRED_BEFORE_DELIVERY = ConnectorDeadletterReason(13)
        @JvmField val ONE_WAY_DROPPED_BY_POLICY = ConnectorDeadletterReason(14)
        @JvmField val REMOTE_TRANSPORT_SECURITY_FAILED = ConnectorDeadletterReason(15)
        private val known = arrayOf(UNSPECIFIED, NO_ACTIVE_SNAPSHOT, ROUTE_MISS, NO_RESPONSIBLE_HOST,
            QUEUE_REJECTED, LOCAL_HANDOFF_FAILED, DELIVERY_FAILED, REMOTE_TRANSPORT_FAILED,
            RUNTIME_STOPPED, INVALID_ENVELOPE, ENDPOINT_UNAVAILABLE, REMOTE_REPLY_TIMEOUT,
            LOCALITY_MISMATCH, EXPIRED_BEFORE_DELIVERY, ONE_WAY_DROPPED_BY_POLICY, REMOTE_TRANSPORT_SECURITY_FAILED)
        @JvmStatic fun of(value: Int): ConnectorDeadletterReason = known.getOrNull(value) ?: ConnectorDeadletterReason(value)
    }
}

/**
 * Deadletter observation emitted from the runtime deadletter lane.
 *
 * Core owns deadletter consumption and matching. This stream projects matched
 * deadletters returned for submit-first and inline ask operations; the
 * connector never drains or classifies an independent deadletter lane.
 *
 * @property deadletter Runtime delivery failure details.
 * @property requestMessageId Original request message id when the connector can associate it.
 * @property correlationId Original request correlation id when the connector can associate it.
 * @property matchedPendingRequest Whether the connector matched this deadletter to a tracked request.
 */
data class ObservedDeadletter(
    val deadletter: ConnectorDeadletter,
    val requestMessageId: String?,
    val correlationId: String?,
    val matchedPendingRequest: Boolean,
)

/**
 * Identity returned after submitting a reply-capable request without waiting inline for its terminal outcome.
 *
 * @property messageId Unique wire identity of the submitted request.
 * @property correlationId Reply correlation identity that the terminal outcome must carry.
 */
data class SubmittedRequest(
    val messageId: String,
    val correlationId: String,
)

/**
 * Terminal outcome for a reply-capable request submitted through the connector.
 *
 * This stream intentionally carries both successful/handled business responses and delivery failures.
 */
sealed class RequestTerminalEvent {
    /** Unique wire identity of the original request. */
    abstract val requestMessageId: String

    /** Reply correlation identity of the original request. */
    abstract val correlationId: String

    /** Terminal outcome where the destination app-host produced a response envelope. */
    data class Response(
        override val requestMessageId: String,
        override val correlationId: String,
        val envelope: ConnectorEnvelope,
    ) : RequestTerminalEvent()

    /** Terminal outcome where runtime failed before the destination app-host accepted ownership. */
    data class Deadletter(
        override val requestMessageId: String,
        override val correlationId: String,
        val deadletter: ConnectorDeadletter,
    ) : RequestTerminalEvent()
}
