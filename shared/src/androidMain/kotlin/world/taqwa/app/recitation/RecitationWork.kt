package world.taqwa.app.recitation

import androidx.work.Data
import androidx.work.WorkInfo

/**
 * The names WorkManager knows a surah download by, and the translation back from what WorkManager
 * reports to what the UI reads.
 *
 * Everything a worker needs travels in its input data, baked at enqueue time: the URL, the size,
 * the hash, and the two strings its notification is written from. A worker can be started into a
 * process with no Activity and no composition, so it must never have to look a name up.
 */
internal object RecitationWork {

    /** Every surah download of every reciter — what [SurahDownloader.states] is derived from. */
    const val TAG_ALL = "recitation"

    /** One reciter's downloads, which is what makes "Download the whole Quran" one batch. */
    fun reciterTag(reciterId: String) = "$TAG_ALL-$reciterId"

    /**
     * The key itself, as a tag. It has to be a tag rather than input data because an ENQUEUED
     * work reports neither progress nor output, and the download sheet still has to know which
     * surah is waiting.
     */
    fun keyTag(key: DownloadKey) = "$TAG_ALL-key-${key.wire}"

    /** Spec: unique work name `recitation-<reciter>-<surah>`. */
    fun uniqueName(key: DownloadKey) = "$TAG_ALL-${key.reciterId}-${key.surah}"

    const val KEY_RECITER = "reciter"
    const val KEY_SURAH = "surah"
    const val KEY_URL = "url"
    const val KEY_BYTES = "bytes"
    const val KEY_SHA = "sha256"
    const val KEY_ALLOW_METERED = "allowMetered"
    const val KEY_SURAH_NAME = "surahName"
    const val KEY_ARABIC = "arabic"

    /** Progress and output. */
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_VERIFYING = "verifying"
    const val KEY_REASON = "reason"

    private const val KEY_TAG_PREFIX = "$TAG_ALL-key-"

    /**
     * The flattened `WorkInfo` the state map is built from. Pulled out as a plain value so the
     * mapping below can be unit-tested without conjuring `WorkInfo` instances, whose constructor
     * changes shape between WorkManager releases.
     */
    data class Snapshot(
        val key: DownloadKey,
        val state: WorkInfo.State,
        val done: Long = 0L,
        val total: Long = 0L,
        val verifying: Boolean = false,
        val reason: DownloadFailure? = null,
    )

    fun snapshotOf(info: WorkInfo): Snapshot? {
        val key = info.tags.firstOrNull { it.startsWith(KEY_TAG_PREFIX) }
            ?.removePrefix(KEY_TAG_PREFIX)
            ?.let(DownloadKey::parse)
            ?: return null
        return Snapshot(
            key = key,
            state = info.state,
            done = info.progress.getLong(KEY_DONE, 0L),
            total = info.progress.getLong(KEY_TOTAL, 0L),
            verifying = info.progress.getBoolean(KEY_VERIFYING, false),
            reason = reasonOf(info.outputData),
        )
    }

    fun reasonOf(data: Data): DownloadFailure? =
        data.getString(KEY_REASON)?.let { name ->
            DownloadFailure.entries.firstOrNull { it.name == name }
        }

    /**
     * What the download sheet reads.
     *
     * A **succeeded** download is not in the map: the surah is in the library from the moment it
     * committed, and the library is the one place that answers "the reader owns this". A
     * **cancelled** one is not in it either — the reader asked for it to go away, and a failure
     * chip in its place would be an argument. Everything else is in flight or is a failure with a
     * sentence attached.
     *
     * WorkManager keeps finished work around until it prunes, so this filter is what stops
     * yesterday's downloads from reappearing as state.
     */
    fun statesOf(snapshots: List<Snapshot>): Map<DownloadKey, DownloadState> {
        val states = mutableMapOf<DownloadKey, DownloadState>()
        for (snapshot in snapshots) {
            val state = when (snapshot.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued
                WorkInfo.State.RUNNING -> when {
                    snapshot.verifying -> DownloadState.Verifying
                    snapshot.total > 0L -> DownloadState.Downloading(snapshot.done, snapshot.total)
                    // Running, but not downloading yet: waiting for one of the two slots.
                    else -> DownloadState.Queued
                }
                WorkInfo.State.FAILED ->
                    DownloadState.Failed(snapshot.reason ?: DownloadFailure.SERVER)
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
            } ?: continue
            // Two works can share a key only while a replacement is starting as its predecessor
            // finishes; the live one wins.
            val existing = states[snapshot.key]
            if (existing == null || existing is DownloadState.Failed) states[snapshot.key] = state
        }
        return states
    }
}
