package coakka.v2.connector.protocol

/** Host-friendly message kinds exposed by the connector API. */
enum class ConnectorMessageKind {
    /** A request that expects either a response or a delivery failure. */
    REQUEST,

    /** A response that correlates to a previous request. */
    RESPONSE,

    /** A one-way event-style message on the main channel. */
    EVENT,
}

/** Business outcome carried by response envelopes. */
enum class ConnectorBusinessStatus {
    /** Business handling completed successfully. */
    OK,

    /** Business handling completed with an application-level error. */
    ERROR,
}

/** Delivery preference requested by the caller. */
enum class ConnectorDeliveryHint {
    /** Let the runtime choose according to the active route policy. */
    ROUTER_DEFAULT,

    /** Prefer a local endpoint when policy allows it. */
    PREFER_LOCAL,

    /** Fail closed unless a local endpoint can satisfy the request. */
    REQUIRE_LOCAL,

    /** Fail closed unless the request is sent to a remote endpoint. */
    REQUIRE_REMOTE,
}

/** Byte encoding or container format declared for an envelope payload. */
enum class ConnectorPayloadFormat {
    /** Explicitly under-specified payload identity, reserved for raw-envelope escape hatches. */
    UNSPECIFIED,

    /** JSON payload bytes. */
    JSON,

    /** Protobuf payload bytes. */
    PROTOBUF,

    /** Thrift payload bytes. */
    THRIFT,

    /** MessagePack payload bytes. */
    MSGPACK,

    /** UTF-8 text payload bytes. Preferred public name for text-first APIs. */
    TEXT,

    /**
     * Compatibility alias for [TEXT].
     *
     * New user-facing connector APIs should use [TEXT].
     */
    @Deprecated("Use TEXT", ReplaceWith("ConnectorPayloadFormat.TEXT"))
    PLAIN_TEXT,

    /** Opaque binary payload bytes. */
    BINARY,
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
    val payloadSchemaVersion: Int = 1,
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
        fun text(messageType: String, payloadSchemaVersion: Int = 1): ConnectorPayloadIdentity =
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
            payloadSchemaVersion: Int = 1,
        ): ConnectorPayloadIdentity =
            ConnectorPayloadIdentity(
                messageType = messageType,
                payloadSchemaVersion = payloadSchemaVersion,
                payloadFormat = payloadFormat,
            )
    }
}

/**
 * Public connector envelope model, kept separate from generated protobuf classes.
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
    val payloadSchemaVersion: Int = 0,
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
 * @property originalEnvelope Original request that could not be delivered.
 * @property reason Deadletter reason name from the runtime schema.
 * @property detail Runtime-provided failure detail for logs or diagnostics.
 * @property activeGeneration Runtime generation active at failure time.
 * @property resolvedHost Resolved host if failure happened after route selection.
 * @property resolvedPort Resolved port if failure happened after route selection.
 */
data class ConnectorDeadletter(
    val originalEnvelope: ConnectorEnvelope,
    val reason: String,
    val detail: String,
    val activeGeneration: Long,
    val resolvedHost: String,
    val resolvedPort: Int,
)

/**
 * Deadletter observation emitted from the runtime deadletter lane.
 *
 * Unlike [RequestTerminalEvent.Deadletter], this stream is not limited to
 * submit-first requests. It observes every deadletter frame drained by the
 * connector, including deadletters that also fail an in-flight `ask`.
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
