package world.taqwa.app.recitation

/**
 * One entry in a surah's playback queue. The gaps are entries of their own rather than a property
 * of the ayah before them, because that is how both platforms play them — a silent item on
 * Android, a timed wait on iOS — and because it is the only way the queue index the platform
 * reports back can be turned into an ayah without guessing.
 */
sealed interface QueueItem {
    /** An ayah of the surah, played from its byte range inside the `.taqa`. */
    data class Ayah(val n: Int) : QueueItem

    /** The reciter's own inter-ayah silence (spec §2), between two ayahs and never at either end. */
    data class Gap(val durationMs: Long) : QueueItem
}

/**
 * The index arithmetic of a surah's playback, and nothing else: no player, no files, no platform.
 *
 * Four of the ten reciters begin at full voice on sample zero and sound rushed played back to
 * back, so the manifest carries an inter-ayah gap per reciter which the player inserts between
 * items (spec §2). That single decision is what makes this class worth having — with gaps in the
 * queue, the platform's "current item" is no longer the ayah number, half the items are not ayahs
 * at all, and previous/next have to skip over them. Getting that wrong shows up as an ayah
 * highlight that flickers to nothing between every two ayahs, which is exactly the sort of thing
 * a unit test should catch rather than a device.
 *
 * **While a gap plays the queue keeps reporting the ayah that just ended.** The alternative —
 * reporting nothing, or the ayah about to start — would either blank the reader's highlight for
 * 300 ms every ayah or move it before the voice does.
 *
 * @param ayahs the ayah numbers of the surah in playing order, normally 1..n.
 * @param gapMs the reciter's gap; zero or less means no gap items at all.
 */
class RecitationQueue(
    val surah: Int,
    val ayahs: List<Int>,
    val gapMs: Long,
) {
    init {
        require(ayahs.isNotEmpty()) { "A recitation queue needs at least one ayah" }
    }

    val ayahCount: Int get() = ayahs.size

    /** True when gap items are actually in the queue: a positive gap and more than one ayah. */
    val hasGaps: Boolean = gapMs > 0L && ayahs.size > 1

    /** How many items the platform is given. With gaps, one fewer than twice the ayah count. */
    val size: Int = if (hasGaps) ayahs.size * 2 - 1 else ayahs.size

    /**
     * The queue the platform builds its items from, in order. Both players walk this list, so the
     * mapping below is the mapping they get — there is no second place to keep in step.
     */
    val items: List<QueueItem> = buildList(size) {
        ayahs.forEachIndexed { position, n ->
            if (position > 0 && hasGaps) add(QueueItem.Gap(gapMs))
            add(QueueItem.Ayah(n))
        }
    }

    fun isGap(index: Int): Boolean = hasGaps && index % 2 == 1

    fun contains(ayah: Int): Boolean = ayah in ayahs

    /** The queue index ayah [n] starts at, or null if this surah has no such ayah. */
    fun indexOfAyah(n: Int): Int? {
        val position = ayahs.indexOf(n)
        return if (position < 0) null else indexOfPosition(position)
    }

    /**
     * The ayah to report while queue item [index] is playing — the ayah itself, or, for a gap, the
     * ayah that just ended. Out-of-range indices are clamped rather than thrown on: the platform
     * can report an index from a timeline this queue has already been replaced in.
     */
    fun ayahAt(index: Int): Int = ayahs[positionAt(index)]

    /** The queue index of the next ayah, or null when the last ayah is the one playing. */
    fun next(index: Int): Int? {
        val position = positionAt(index)
        return if (position + 1 <= ayahs.lastIndex) indexOfPosition(position + 1) else null
    }

    /**
     * Where "previous" goes from queue item [index] at [positionMs] into it: the previous ayah
     * within the first [RESTART_WINDOW_MS] of an ayah, otherwise the start of the ayah playing.
     * At the first ayah it always restarts, so this never returns null.
     *
     * A gap always restarts the ayah that just ended: the ayah has by then finished, so "back to
     * the start of what I am hearing" is that ayah, and treating the gap's own position as an
     * ayah's would send the listener a whole ayah backwards for pressing the button late.
     */
    fun previous(index: Int, positionMs: Long): Int {
        val position = positionAt(index)
        val stepBack = !isGap(index) && positionMs < RESTART_WINDOW_MS && position > 0
        return indexOfPosition(if (stepBack) position - 1 else position)
    }

    /** The position in [ayahs] a queue index belongs to, clamped into the queue. */
    private fun positionAt(index: Int): Int {
        val safe = index.coerceIn(0, size - 1)
        return (if (hasGaps) safe / 2 else safe).coerceIn(0, ayahs.lastIndex)
    }

    private fun indexOfPosition(position: Int): Int = if (hasGaps) position * 2 else position

    companion object {
        /** Past this into an ayah, "previous" restarts it instead of going back one (spec §6). */
        const val RESTART_WINDOW_MS = 2_000L

        /** The queue a container's own index describes, with the reciter's gap between ayahs. */
        fun of(index: TaqaIndex, gapMs: Long): RecitationQueue = RecitationQueue(
            surah = index.surah,
            ayahs = index.ayahs.map { it.n },
            gapMs = gapMs,
        )
    }
}
