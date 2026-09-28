package world.taqwa.app.prayer.engine

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * A bounded least-recently-used memo that any thread may read and fill at once, with no lock and
 * no platform code (spec §3.1: the Prayer screen on Main, notification and widget receivers on
 * `Dispatchers.Default`, iOS background refresh; `tools/timetables` compiles this on a plain JVM).
 *
 * The entries are an immutable map behind one atomic reference, replaced whole on every insert by
 * compare-and-set; a hit only stamps its entry's last use. When an insert would pass [capacity],
 * the entry used least recently goes. Two callers that miss the same key at once may both compute
 * it, but only the first to publish is kept and both get that one object back, so a key always
 * answers with the same object while it stays cached.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class BoundedCache<K : Any, V : Any>(private val capacity: Int) {
    init {
        require(capacity > 0) { "a cache of $capacity entries" }
    }

    private class Slot<V>(val value: V, stamp: Long) {
        val used = AtomicLong(stamp)
    }

    private val clock = AtomicLong(0L)
    private val slots = AtomicReference<Map<K, Slot<V>>>(emptyMap())

    /** How many entries are held now. */
    val size: Int get() = slots.load().size

    /** The value cached for [key], computing and caching it with [compute] on a miss. */
    fun getOrPut(key: K, compute: () -> V): V {
        slots.load()[key]?.let { hit ->
            hit.used.store(clock.incrementAndFetch())
            return hit.value
        }
        val fresh = Slot(compute(), clock.incrementAndFetch())
        while (true) {
            val current = slots.load()
            current[key]?.let { return it.value }
            val next = LinkedHashMap<K, Slot<V>>(current.size + 2)
            next.putAll(current)
            next[key] = fresh
            if (next.size > capacity) {
                next.remove(next.entries.minBy { it.value.used.load() }.key)
            }
            if (slots.compareAndSet(current, next)) return fresh.value
        }
    }

    /** Drops every entry (tests). */
    fun clear() {
        slots.store(emptyMap())
    }
}
