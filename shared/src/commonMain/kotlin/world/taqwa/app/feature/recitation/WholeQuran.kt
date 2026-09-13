package world.taqwa.app.feature.recitation

import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.Reciter

/**
 * "Download the whole Quran for this reciter" (spec §5.4, §12.8), as a value the two places that
 * offer it can draw without either of them doing arithmetic: the quiet second button on the
 * download sheet, and the action row under Settings › Recitation's downloads card.
 *
 * Null is a state too, and the most common one: a reciter whose 114 surahs are all on the phone
 * has nothing to offer, and neither does one whose catalogue entry carries no surahs at all
 * (a manifest still being written, a reciter withdrawn between the picker and this row).
 */
sealed interface WholeQuran {

    /** Nothing running: the price of the surahs the reader does not yet have. */
    data class Offer(val bytes: Long, val missing: Int) : WholeQuran

    /**
     * A batch in flight. [done] and [total] are surahs of the whole reciter, not of the batch —
     * the reader who already owned thirty wants to read "31 of 114", not "1 of 84", and this is
     * the same arithmetic the Android summary notification prints (see `DownloadCopy.batch`), so
     * the screen and the notification can never disagree.
     */
    data class Running(val done: Int, val total: Int) : WholeQuran {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Every surah of the batch refused for the same reason — no space, no Wi-Fi, no network.
     * [bytes] is still the price, because what follows the sentence is the same offer again.
     */
    data class Failed(val reason: DownloadFailure, val bytes: Long) : WholeQuran
}

/** The surahs of [reciter] that are not on the phone, in surah order. */
fun missingSurahs(reciter: Reciter, owned: Set<Int>): List<Int> =
    reciter.surahs.map { it.n }.filter { it !in owned }.sorted()

/**
 * What the whole-Quran download would cost: the sum of the *missing* surahs' published sizes, not
 * [Reciter.totalBytes]. A reader who already has Al-Baqarah should not be quoted for it again,
 * and the difference is 58 MB on the very first surah anyone downloads.
 */
fun wholeQuranBytes(reciter: Reciter, owned: Set<Int>): Long =
    reciter.surahs.filter { it.n !in owned }.sumOf { it.bytes }

/**
 * The row's state (see [WholeQuran]).
 *
 * [declared] is the controller saying it started a batch for this reciter in this process. It is
 * needed because a single surah fetched from the reader is indistinguishable, key by key, from a
 * batch of one — and it is not sufficient, because the process can die under a running batch and
 * WorkManager will carry on without it. So a second, process-free signal stands in: **two or more
 * surahs of one reciter in flight at once is a batch**, since nothing but this button ever asks
 * for two. Either is enough.
 *
 * A failure only counts when nothing of this reciter is still moving. A batch on mobile data is
 * refused key by key (the downloader writes `NEEDS_WIFI` against all 114), and a batch that lost
 * the network halfway has both failures and live work in it — in which case what the reader needs
 * to see is the work.
 */
fun wholeQuranOf(
    reciter: Reciter?,
    owned: Set<Int>,
    downloads: Map<DownloadKey, DownloadState>,
    declared: Boolean,
): WholeQuran? {
    if (reciter == null || reciter.surahs.isEmpty()) return null
    val mine = downloads.filterKeys { it.reciterId == reciter.id }
    val inFlight = mine.count { it.value !is DownloadState.Failed }
    val catalogue = reciter.surahs.mapTo(HashSet()) { it.n }
    val total = catalogue.size
    if (inFlight > 0 && (declared || inFlight >= 2)) {
        // Counted against the catalogue, not off the registry's own size: a surah on the phone
        // that this manifest no longer publishes is not part of "of 114".
        return WholeQuran.Running(done = owned.count { it in catalogue }, total = total)
    }
    if (inFlight == 0) {
        val reasons = mine.values.filterIsInstance<DownloadState.Failed>()
        // Only a refusal of the whole batch is the row's business. One surah that failed on its
        // own belongs to the download sheet that asked for it, and printing it here would tell a
        // reader who never pressed this button that their whole Quran had failed.
        if (declared && reasons.isNotEmpty()) {
            return WholeQuran.Failed(reasons.first().reason, wholeQuranBytes(reciter, owned))
        }
    }
    val missing = missingSurahs(reciter, owned)
    if (missing.isEmpty()) return null
    return WholeQuran.Offer(bytes = wholeQuranBytes(reciter, owned), missing = missing.size)
}
