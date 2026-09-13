package world.taqwa.app.recitation

/**
 * The surah as one clock (spec §14.1): every queue item's length in milliseconds, ayahs and gaps
 * alike, in queue order, and the arithmetic that turns "item 41 at 3.2 s" into "12:31 of 2:05:10".
 *
 * A surah is a queue of per-ayah files, and until this class the bar could only say how far
 * through the *ayah* the voice was — a line that filled and emptied every few seconds and a lock
 * screen whose seek bar did the same. Music players show the whole track, and a surah is the
 * whole track: the listener wants to know that Al-Baqarah has an hour and fifty minutes left, not
 * that this ayah has four seconds.
 *
 * **The lengths are estimates read off the container, not measured.** The corpus is constant
 * bit-rate MP3, so an ayah's audio bytes over its bit-rate is its length to within a frame; the
 * only correction needed is the ID3 tag at the head of each file, which is metadata rather than
 * sound (see [TaqaFile.ayahDurationsMs]). Measured to the millisecond on the corpus: Alafasy's
 * 6.113 s first ayah estimates at 6.138 s, Minshawi's at exactly its measured length. That error
 * is a tenth of a second per ayah at worst and never accumulates into anything a clock shows,
 * because the position *within* the item playing is always the platform's real one.
 *
 * Both players build one of these from the same container, so the app's bar and the service's
 * lock-screen bar are reading the same clock — there is deliberately no refinement from measured
 * durations, which would have let the two drift apart by a frame an ayah.
 */
class SurahTimeline(val itemsMs: List<Long>) {

    init {
        require(itemsMs.isNotEmpty()) { "A timeline needs at least one item" }
    }

    private val starts: LongArray = LongArray(itemsMs.size).also { starts ->
        var at = 0L
        itemsMs.forEachIndexed { i, ms ->
            starts[i] = at
            at += ms.coerceAtLeast(0L)
        }
    }

    /** The whole surah, gaps included. */
    val totalMs: Long = starts.last() + itemsMs.last().coerceAtLeast(0L)

    val size: Int get() = itemsMs.size

    /** Where queue item [index] begins on the surah's clock. Out-of-range indices are clamped. */
    fun startOf(index: Int): Long = starts[clamp(index)]

    fun durationOf(index: Int): Long = itemsMs[clamp(index)].coerceAtLeast(0L)

    /**
     * The surah's clock while item [index] is at [positionMs] into itself. The position is held
     * inside the item's own slot: a platform that reports a few frames past an estimate must not
     * show the next ayah's time before the next ayah starts.
     */
    fun elapsed(index: Int, positionMs: Long): Long {
        val i = clamp(index)
        return starts[i] + positionMs.coerceIn(0L, durationOf(i))
    }

    /** The queue item whose slot holds surah time [ms]; the last item for anything past the end. */
    fun indexAt(ms: Long): Int {
        if (ms <= 0L) return 0
        if (ms >= totalMs) return itemsMs.lastIndex
        var low = 0
        var high = itemsMs.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= ms) low = mid else high = mid - 1
        }
        return low
    }

    /**
     * Where a seek to surah time [ms] should land: the start of the ayah whose slot holds it, or,
     * when it falls inside a gap, the start of the ayah that gap leads into. Recitation moves by
     * ayah — a scrub on the lock screen that dropped the voice into the middle of a word would be
     * worse than one that snapped — so the snap is the rule and this is where it lives.
     */
    fun snapToAyah(ms: Long, isGap: (Int) -> Boolean): Int {
        val index = indexAt(ms)
        return if (isGap(index)) (index + 1).coerceAtMost(itemsMs.lastIndex) else index
    }

    private fun clamp(index: Int): Int = index.coerceIn(0, itemsMs.lastIndex)

    companion object {
        /** A constant bit-rate file's length from its audio bytes: `bytes × 8 / kbps` milliseconds. */
        fun estimateMs(audioBytes: Long, kbps: Int): Long =
            if (kbps <= 0 || audioBytes <= 0L) 0L else audioBytes * 8L / kbps

        /** The clock for [queue], asking [ayahMs] for each ayah's length; gaps carry their own. */
        fun of(queue: RecitationQueue, ayahMs: (Int) -> Long): SurahTimeline = SurahTimeline(
            queue.items.map { item ->
                when (item) {
                    is QueueItem.Ayah -> ayahMs(item.n)
                    is QueueItem.Gap -> item.durationMs
                }
            },
        )
    }
}
