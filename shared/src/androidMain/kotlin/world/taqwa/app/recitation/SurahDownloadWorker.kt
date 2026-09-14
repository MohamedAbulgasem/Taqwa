package world.taqwa.app.recitation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.di.appContainer
import world.taqwa.app.notifications.notificationSmallIconResId

/**
 * One surah, one download (spec 3a §7). The transfer itself is [DownloadLoop] — resume from the
 * `.part` with a `Range`, verify the SHA-256, commit — and everything here is what Android needs
 * around it: a foreground notification so the system lets a 58 MB file finish, a progress channel
 * the app can read while it is running, and a failure reason the download sheet can phrase.
 *
 * Nothing is looked up: the URL, the size, the hash and the notification's own words all arrive
 * in the input data, baked while the app was still running and could tell what language it is in.
 */
class SurahDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val reciterId = inputData.getString(RecitationWork.KEY_RECITER)
    private val surah = inputData.getInt(RecitationWork.KEY_SURAH, 0)
    private val surahName = inputData.getString(RecitationWork.KEY_SURAH_NAME).orEmpty()
    private val languageTag = inputData.getString(RecitationWork.KEY_LANGUAGE) ?: "en"
    private val numberStyle = inputData.getString(RecitationWork.KEY_NUMBER_STYLE)
        ?.let { name -> NumberStyle.entries.firstOrNull { it.name == name } } ?: NumberStyle.WESTERN
    private val totalBytes = inputData.getLong(RecitationWork.KEY_BYTES, 0L)
    private val reciterName = inputData.getString(RecitationWork.KEY_RECITER_NAME).orEmpty()
    private val reciterTotal = inputData.getInt(RecitationWork.KEY_RECITER_TOTAL, 0)

    /**
     * The batch line (spec §16.3): how many of this voice's surahs the phone now has, shown in
     * place of this surah's own progress while more than one surah of the voice is in flight.
     * Null while this surah downloads on its own. Kept by a collector that runs beside the
     * transfer; every worker of a batch computes the same two numbers from the same two sources,
     * which is what lets all of them draw the one notification without disagreeing about it.
     */
    @Volatile
    private var batch: Batch? = null

    private class Batch(val owned: Int, val total: Int)

    /**
     * WorkManager may ask for this before `doWork`, so it has to stand on its own; the same
     * builder produces every later update.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0L)

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val reciter = reciterId ?: return failure(DownloadFailure.SERVER)
        val url = inputData.getString(RecitationWork.KEY_URL) ?: return failure(DownloadFailure.SERVER)
        val sha = inputData.getString(RecitationWork.KEY_SHA) ?: return failure(DownloadFailure.SERVER)
        if (surah !in 1..114 || totalBytes <= 0L) return failure(DownloadFailure.SERVER)
        val key = DownloadKey(reciter, surah)
        val asset = SurahAsset(n = surah, bytes = totalBytes, sha256 = sha)

        // At most two surahs are actually moving bytes at any moment, whatever WorkManager decides
        // to run. The permit is taken *before* the foreground notification, so a worker waiting
        // its turn is silent rather than posting a third "downloading" line the user cannot
        // account for; it reports itself as Queued until it starts.
        // Rather than hold a worker (and its ten-minute execution budget) against a queue that
        // may be an hour long, a worker that waits too long gives the turn back to WorkManager.
        if (withTimeoutOrNull(SLOT_WAIT_MILLIS) { slots.acquire() } == null) {
            return androidx.work.ListenableWorker.Result.retry()
        }

        try {
            val outcome = coroutineScope {
                val batches = batchLine(reciter)
                // Decided before the first post, so a batch never flashes one surah's own line for
                // the instant before the watcher's first word.
                if (batches != null) {
                    withTimeoutOrNull(FIRST_LOOK_MILLIS) { batches.first() }?.let { (pending, owned) -> note(pending, owned) }
                }
                val watcher = launch { batches?.collect { (pending, owned) -> note(pending, owned) } }
                try {
                    runCatching { setForeground(foregroundInfo(0L)) }
                    val loop = DownloadLoop(
                        library = appContainer.recitationLibrary,
                        source = HttpByteSource(),
                        conditions = AndroidDownloadConditions(applicationContext),
                    )
                    loop.run(
                        key = key,
                        url = url,
                        asset = asset,
                        allowMetered = inputData.getBoolean(RecitationWork.KEY_ALLOW_METERED, false),
                    ) { state ->
                        when (state) {
                            is DownloadState.Downloading -> {
                                setProgress(
                                    workDataOf(
                                        RecitationWork.KEY_DONE to state.bytes,
                                        RecitationWork.KEY_TOTAL to state.total,
                                        RecitationWork.KEY_VERIFYING to false,
                                    )
                                )
                                runCatching { setForeground(foregroundInfo(state.bytes)) }
                            }
                            DownloadState.Verifying -> {
                                setProgress(
                                    workDataOf(
                                        RecitationWork.KEY_DONE to totalBytes,
                                        RecitationWork.KEY_TOTAL to totalBytes,
                                        RecitationWork.KEY_VERIFYING to true,
                                    )
                                )
                            }
                            else -> Unit
                        }
                    }
                } finally {
                    watcher.cancel()
                }
            }
            return when (outcome) {
                DownloadOutcome.Done -> androidx.work.ListenableWorker.Result.success()
                is DownloadOutcome.Failed -> failure(outcome.reason)
            }
        } finally {
            slots.release()
        }
    }

    private fun failure(reason: DownloadFailure) = androidx.work.ListenableWorker.Result.failure(
        workDataOf(RecitationWork.KEY_REASON to reason.name)
    )

    /**
     * What [batch] is kept current from, for the life of the transfer: how many works of this
     * voice are unfinished, and how many of its surahs the phone owns. Null for a work enqueued
     * without the voice's numbers (a build before 0.19.1).
     */
    private fun batchLine(reciterId: String): Flow<Pair<Int, Int>>? {
        if (reciterTotal <= 0) return null
        return combine(
            WorkManager.getInstance(applicationContext).getWorkInfosByTagFlow(RecitationWork.reciterTag(reciterId)),
            appContainer.recitationLibrary.downloaded(reciterId),
        ) { infos, owned -> infos.count { !it.state.isFinished } to owned.size }
            // A Room query and a DataStore read: either can throw, and a shade that shows the
            // surah's own line instead of the batch's is not a reason to fail the download.
            .catch { }
    }

    /**
     * More than one unfinished work for this voice — now, or at any moment while this surah was
     * moving — means the shade gets the batch line, and keeps it through the batch's last surah
     * rather than switching back to that surah's own numbers for the final minute. The count is
     * the library's, not the batch's: what the reader wants to know is how much of this voice
     * they now have, and a batch that skipped the 30 surahs they already owned would otherwise
     * start at zero out of 84.
     */
    private fun note(pending: Int, owned: Int) {
        if (pending > 1 || batch != null) batch = Batch(owned, reciterTotal)
    }

    /**
     * "Al-Baqarah · 9.3 of 58.2 MB" with its bar — or, in a batch, "Mishary Rashid Alafasy ·
     * 12 of 114 surahs" with the bar counting surahs. Always under [NOTIFICATION_ID].
     */
    private fun foregroundInfo(done: Long): ForegroundInfo {
        ensureChannel(applicationContext, languageTag)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(notificationSmallIconResId)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val line = batch
        if (line != null) {
            builder
                .setContentTitle(DownloadCopy.batch(reciterName, line.owned, line.total, languageTag, numberStyle))
                .setProgress(line.total, line.owned.coerceIn(0, line.total), false)
        } else {
            val percent = if (totalBytes <= 0L) 0 else ((done * 100) / totalBytes).toInt().coerceIn(0, 100)
            builder
                .setContentTitle(DownloadCopy.progress(surahName, done, totalBytes, languageTag, numberStyle))
                .setProgress(100, percent, done <= 0L)
        }
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        /** Spec §7: a low-importance channel, so a download never makes a sound. */
        const val CHANNEL_ID = "downloads"

        /**
         * Process-wide, because every worker runs in this one process. Two at a time: enough to
         * keep a connection saturated while one surah is being hashed, few enough that a batch of
         * 114 does not open 114 sockets against one CDN.
         */
        private val slots = Semaphore(2)

        /** Five minutes waiting for a slot, then hand the turn back to WorkManager. */
        private const val SLOT_WAIT_MILLIS = 5L * 60L * 1000L

        /** How long the first post may wait for the batch numbers; a slow read costs one flash. */
        private const val FIRST_LOOK_MILLIS = 1_500L

        /**
         * One id for every download worker (spec §16.3). WorkManager posts each worker's
         * `ForegroundInfo` under the id the worker names, so with an id per surah two surahs in
         * flight were two lines in the shade, and a batch summary made three. Under one id the
         * shade holds one line — whichever worker updated it last — and in a batch every worker
         * writes the same line, so nothing flickers. Well clear of the prayer notifications, which
         * are numbered from 1. When the last worker finishes, WorkManager takes the notification
         * down with the foreground service, as before.
         */
        private const val NOTIFICATION_ID = 770_000

        /** Idempotent, and re-created deliberately so a locale change relabels it. */
        fun ensureChannel(context: Context, languageTag: String) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    DownloadCopy.channelName(languageTag),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
    }
}
