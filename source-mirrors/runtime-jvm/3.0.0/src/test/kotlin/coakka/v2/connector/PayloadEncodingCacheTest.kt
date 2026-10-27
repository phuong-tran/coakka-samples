package coakka.v2.connector

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PayloadEncodingCacheTest {
    @Test
    fun exactUtf8LimitCachesButOneOverDoesNot() {
        val cache = PayloadEncodingCache()
        for (key in listOf("a".repeat(128), "é".repeat(64), "🙂".repeat(32))) {
            assertSame(cache.source(key), cache.source(key))
            assertSame(cache.target(key), cache.target(key))
        }
        for (key in listOf("a".repeat(129), "é".repeat(65), "🙂".repeat(33), "x".repeat(1024 * 1024))) {
            val source = cache.source(key)
            assertNotSame(source, cache.source(key))
            assertNotSame(cache.target(key), cache.target(key))
            assertContentEquals(key.utf8(), source.source)
            assertContentEquals((key + "/replies").utf8(), source.replyTo)
        }
    }

    @Test
    fun entryCeilingDoesNotChangeEncodedValues() {
        val cache = PayloadEncodingCache()
        val sources = (0 until 64).map { cache.source("key-$it") }
        val targets = (0 until 64).map { cache.target("key-$it") }
        repeat(64) {
            assertSame(sources[it], cache.source("key-$it"))
            assertSame(targets[it], cache.target("key-$it"))
        }
        assertNotSame(cache.source("overflow"), cache.source("overflow"))
        assertNotSame(cache.target("overflow"), cache.target("overflow"))
        assertContentEquals("overflow".utf8(), cache.target("overflow"))
    }

    @Test
    fun retirementPreservesBorrowedValuesAndPreventsReadoption() {
        val cache = PayloadEncodingCache()
        val before = cache.source("borrowed")
        cache.retire()
        cache.retire()
        assertContentEquals("borrowed/replies".utf8(), before.replyTo)
        assertNotSame(before, cache.source("borrowed"))
        assertNotSame(cache.source("borrowed"), cache.source("borrowed"))
        assertNotSame(cache.target("borrowed"), cache.target("borrowed"))
    }

    @Test
    fun concurrentAdmissionKeepsTheSameBoundAndRetires() {
        val cache = PayloadEncodingCache()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val calls = (0 until 256).map { index -> Callable { cache.source("key-$index") } }
            val results = workers.invokeAll(calls, 5, TimeUnit.SECONDS).map { it.get() }
            assertEquals(64, results.indices.count { results[it] === cache.source("key-$it") })
            val racing = (0 until 256).map { index -> Callable {
                if (index % 7 == 0) cache.retire()
                cache.source("key-$index")
            } }
            workers.invokeAll(racing, 5, TimeUnit.SECONDS).forEach { it.get() }
            cache.retire()
            assertNotSame(cache.source("key-0"), cache.source("key-0"))
        } finally {
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
