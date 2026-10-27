package coakka.v2.connector

import java.util.concurrent.ConcurrentHashMap

/**
 * JVM representation cache, never runtime policy or proof of Core admission.
 * Returned arrays are read-only borrows held by the caller for one native call.
 * Retirement drops cache roots but does not invalidate outstanding JVM borrows.
 */
internal class PayloadEncodingCache {
    internal class Source(val source: ByteArray, val replyTo: ByteArray)

    private val admission = Any()
    private val sources = ConcurrentHashMap<String, Source>()
    private val targets = ConcurrentHashMap<String, ByteArray>()
    private var retired = false // Only accessed under admission.

    fun source(value: String): Source {
        sources[value]?.let { return it }
        val bytes = value.utf8()
        val encoded = Source(bytes, bytes + REPLY_SUFFIX)
        // Large values are not rejected: Core still decides their validity.
        // They simply cannot become long-lived roots in a small host cache.
        if (value.length > MAX_KEY_BYTES || bytes.size > MAX_KEY_BYTES) return encoded
        return synchronized(admission) {
            sources[value] ?: encoded.also {
                if (!retired && sources.size < CAPACITY) sources[value] = it
            }
        }
    }

    fun target(value: String): ByteArray {
        targets[value]?.let { return it }
        val encoded = value.utf8()
        if (value.length > MAX_KEY_BYTES || encoded.size > MAX_KEY_BYTES) return encoded
        return synchronized(admission) {
            targets[value] ?: encoded.also {
                if (!retired && targets.size < CAPACITY) targets[value] = it
            }
        }
    }

    fun retire() = synchronized(admission) {
        retired = true
        sources.clear()
        targets.clear()
    }

    private companion object {
        const val CAPACITY = 64
        const val MAX_KEY_BYTES = 128
        val REPLY_SUFFIX = "/replies".toByteArray(Charsets.UTF_8)
    }
}
