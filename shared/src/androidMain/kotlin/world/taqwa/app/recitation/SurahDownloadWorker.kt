package world.taqwa.app.recitation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
    private val arabic = inputData.getBoolean(RecitationWork.KEY_ARABIC, false)
    private val totalBytes = inputData.getLong(RecitationWork.KEY_BYTES, 0L)

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
            runCatching { setForeground(foregroundInfo(0L)) }
            val loop = DownloadLoop(
                library = appContainer.recitationLibrary,
                source = HttpByteSource(),
                conditions = AndroidDownloadConditions(applicationContext),
            )
            val outcome = loop.run(
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

    /** "Al-Baqarah · 9.3 of 58.2 MB", and a bar the notification shade can draw. */
    private fun foregroundInfo(done: Long): ForegroundInfo {
        ensureChannel(applicationContext, arabic)
        val percent = if (totalBytes <= 0L) 0 else ((done * 100) / totalBytes).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(notificationSmallIconResId)
            .setContentTitle(DownloadCopy.progress(surahName, done, totalBytes, arabic))
            .setProgress(100, percent, done <= 0L)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(reciterId?.let { GROUP_PREFIX + it } ?: GROUP_PREFIX)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val id = NOTIFICATION_BASE + surah
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
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

        /**
         * Well clear of the prayer notifications, which are numbered sequentially from 1. Two
         * reciters downloading the same surah at the same moment would share a notification;
         * at two concurrent downloads out of a per-reciter queue that cannot arise, and the cost
         * if it ever did is one line of the shade showing the other one's progress.
         */
        private const val NOTIFICATION_BASE = 770_000

        const val GROUP_PREFIX = "world.taqwa.app.downloads."

        /** Idempotent, and re-created deliberately so a locale change relabels it. */
        fun ensureChannel(context: Context, arabic: Boolean) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    DownloadCopy.channelName(arabic),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
    }
}
