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

    /**
     * A silence: the reciter's own inter-ayah gap (spec §2), or the breath before a spoken
     * translation (read-aloud spec §2). Never at either end of the queue.
     */
    data class Gap(val durationMs: Long) : QueueItem

    /**
     * The translation read aloud after ayah [n] by the phone's own voice (read-aloud spec §2) —
     * for a run of ayahs that share one text, after the last of them.
     */
    data class Speech(val n: Int) : QueueItem
}

/** The breath between an ayah and its spoken translation (read-aloud spec §2). */
const val SPEECH_BREATH_MS = 400L

/**
 * The index arithmetic of a surah's playback, and nothing else: no player, no files, no platform.
 *
 * Four of the ten reciters begin at full voice on sample zero and sound rushed played back to
 * back, so the manifest carries an inter-ayah gap per reciter which the player inserts between
 * items (spec §2); with read-aloud on, a breath and the translation follow an ayah too. That is
 * what makes this class worth having — the platform's "current item" is no longer the ayah
 * number, most items are not ayahs at all, and previous/next have to skip over them. Getting that
 * wrong shows up as an ayah highlight that flickers to nothing between every two ayahs, which is
 * exactly the sort of thing a unit test should catch rather than a device.
 *
 * **While a gap or a translation plays the queue keeps reporting the ayah that just ended.** The
 * alternative — reporting nothing, or the ayah about to start — would either blank the reader's
 * highlight for 300 ms every ayah or move it before the voice does.
 *
 * Built from its [items], which is how the Android session rebuilds it from the ids it holds; the
 * secondary constructor builds the items from the surah's ayahs.
 */
class RecitationQueue(
    val surah: Int,
    val items: List<QueueItem>,
) {

    /**
     * [ayahs] in playing order, each followed — when it is in [spoken] — by a breath and its
     * translation, and then by the reciter's [gapMs] of silence unless it is the last ayah. A
     * zero or negative gap means no gap items at all.
     */
    constructor(surah: Int, ayahs: List<Int>, gapMs: Long, spoken: Set<Int> = emptySet()) :
        this(surah, build(ayahs, gapMs, spoken))

    /** The ayah numbers in playing order, normally 1..n. */
    val ayahs: List<Int> = items.mapNotNull { (it as? QueueItem.Ayah)?.n }

    init {
        require(ayahs.isNotEmpty()) { "A recitation queue needs at least one ayah" }
    }

    val ayahCount: Int get() = ayahs.size

    /** How many items the platform is given. */
    val size: Int get() = items.size

    /** True when any silence — a reciter's gap or a breath — is in the queue. */
    val hasGaps: Boolean = items.any { it is QueueItem.Gap }

    /** For each item, the position in [ayahs] of the ayah it is or follows. */
    private val owner: IntArray = IntArray(items.size).also { owners ->
        var position = -1
        items.forEachIndexed { i, item ->
            if (item is QueueItem.Ayah) position++
            owners[i] = position.coerceAtLeast(0)
        }
    }

    /** For each position in [ayahs], the queue index of that ayah's own item. */
    private val ayahItem: IntArray = IntArray(ayahs.size).also { at ->
        var position = 0
        items.forEachIndexed { i, item -> if (item is QueueItem.Ayah) at[position++] = i }
    }

    fun isAyah(index: Int): Boolean = items.getOrNull(index) is QueueItem.Ayah

    fun isSpeech(index: Int): Boolean = items.getOrNull(index) is QueueItem.Speech

    /** A silent item: the reciter's gap or the breath before a translation. */
    fun isGap(index: Int): Boolean = items.getOrNull(index) is QueueItem.Gap

    /** How long the silence at [index] lasts; zero for anything that is not a silence. */
    fun silenceMs(index: Int): Long = (items.getOrNull(index) as? QueueItem.Gap)?.durationMs ?: 0L

    fun contains(ayah: Int): Boolean = ayah in ayahs

    /** The queue index ayah [n] starts at, or null if this surah has no such ayah. */
    fun indexOfAyah(n: Int): Int? {
        val position = ayahs.indexOf(n)
        return if (position < 0) null else ayahItem[position]
    }

    /**
     * The ayah to report while queue item [index] is playing — the ayah itself, or, for a gap or
     * a translation, the ayah it follows. Out-of-range indices are clamped rather than thrown on:
     * the platform can report an index from a timeline this queue has already been replaced in.
     */
    fun ayahAt(index: Int): Int = ayahs[positionAt(index)]

    /** The queue index of the ayah item [index] belongs to: itself for an ayah. */
    fun ayahIndexAt(index: Int): Int = ayahItem[positionAt(index)]

    /** The queue index of the next ayah, or null when the last ayah is the one playing. */
    fun next(index: Int): Int? {
        val position = positionAt(index)
        return if (position + 1 <= ayahs.lastIndex) ayahItem[position + 1] else null
    }

    /**
     * Where "previous" goes from queue item [index] at [positionMs] into it: the previous ayah
     * within the first [RESTART_WINDOW_MS] of an ayah, otherwise the start of the ayah playing.
     * At the first ayah it always restarts, so this never returns null.
     *
     * A gap or a translation always restarts the ayah it follows: the ayah has by then finished,
     * so "back to the start of what I am hearing" is that ayah, and treating the gap's own
     * position as an ayah's would send the listener a whole ayah backwards for pressing late.
     */
    fun previous(index: Int, positionMs: Long): Int {
        val position = positionAt(index)
        val stepBack = isAyah(index) && positionMs < RESTART_WINDOW_MS && position > 0
        return ayahItem[if (stepBack) position - 1 else position]
    }

    /** The position in [ayahs] a queue index belongs to, clamped into the queue. */
    private fun positionAt(index: Int): Int = owner[index.coerceIn(0, items.size - 1)]

    companion object {
        /** Past this into an ayah, "previous" restarts it instead of going back one (spec §6). */
        const val RESTART_WINDOW_MS = 2_000L

        /** The queue a container's own index describes, with the reciter's gap between ayahs. */
        fun of(index: TaqaIndex, gapMs: Long, spoken: Set<Int> = emptySet()): RecitationQueue = RecitationQueue(
            surah = index.surah,
            ayahs = index.ayahs.map { it.n },
            gapMs = gapMs,
            spoken = spoken,
        )

        private fun build(ayahs: List<Int>, gapMs: Long, spoken: Set<Int>): List<QueueItem> = buildList {
            ayahs.forEachIndexed { position, n ->
                add(QueueItem.Ayah(n))
                if (n in spoken) {
                    add(QueueItem.Gap(SPEECH_BREATH_MS))
                    add(QueueItem.Speech(n))
                }
                if (gapMs > 0L && position < ayahs.lastIndex) add(QueueItem.Gap(gapMs))
            }
        }
    }
}
