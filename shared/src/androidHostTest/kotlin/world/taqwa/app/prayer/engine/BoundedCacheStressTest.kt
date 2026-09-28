package world.taqwa.app.prayer.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [BoundedCache] under real threads (review minor M6): the Prayer screen on Main, a notification or
 * widget receiver on `Dispatchers.Default` and iOS background refresh can all ask at once.
 */
class BoundedCacheStressTest {

    private class Value(val key: Int)

    private fun hammer(threads: Int, rounds: Int, body: (Random) -> Unit) {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val failures = ConcurrentHashMap.newKeySet<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                val random = Random(t)
                start.await()
                try {
                    repeat(rounds) { body(random) }
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "the threads did not finish")
        assertTrue(failures.isEmpty(), "failures: $failures")
    }

    @Test
    fun `every thread gets the same object for a key while it stays cached`() {
        val cache = BoundedCache<Int, Value>(64)
        val computed = AtomicInteger()
        val seen = ConcurrentHashMap<Int, Value>()
        hammer(threads = 8, rounds = 20_000) { random ->
            val key = random.nextInt(32) // fewer keys than the capacity: nothing is evicted
            val value = cache.getOrPut(key) {
                computed.incrementAndGet()
                Value(key)
            }
            val first = seen.putIfAbsent(key, value) ?: value
            check(first === value) { "key $key answered with two objects" }
        }
        assertEquals(32, cache.size)
        // Racing misses may compute a key more than once, but never once per call.
        assertTrue(computed.get() < 32 * 8 + 1, "computed ${computed.get()} times")
    }

    @Test
    fun `under eviction every answer is its own key's and the bound holds`() {
        val cache = BoundedCache<Int, Value>(16)
        hammer(threads = 8, rounds = 20_000) { random ->
            val key = random.nextInt(1_000)
            val value = cache.getOrPut(key) { Value(key) }
            check(value.key == key) { "key $key answered with key ${value.key}'s value" }
            check(cache.size <= 16) { "held ${cache.size}" }
        }
        assertTrue(cache.size in 1..16)
    }
}
