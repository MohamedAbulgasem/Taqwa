package world.taqwa.app.recitation

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.appContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * WorkManager (spec 3a §7). One unique work per surah, so a second tap on Download is a no-op
 * rather than a second transfer, and three tags per work: `recitation` for the whole state map,
 * `recitation-<reciter>` for the batch, and the key itself so an enqueued work — which reports no
 * progress and no output — can still say which surah it is.
 *
 * Every string a notification will use is baked here, while the app is running and can tell what
 * language it is being read in. This is `LocalizedNotificationCopy`'s rule applied to downloads:
 * a worker may wake in a process with no Activity and no composition, where a resource lookup
 * would either fail or answer in the system language rather than the app's.
 */
actual class SurahDownloader actual constructor(
    private val library: RecitationLibrary,
    private val manifests: ManifestProvider,
    private val settings: SettingsRepository,
    private val quran: QuranSource,
) {

    private val context = appContext
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val batches = mutableMapOf<String, Job>()

    actual val states: StateFlow<Map<DownloadKey, DownloadState>> =
        workManager.getWorkInfosByTagFlow(RecitationWork.TAG_ALL)
            .map { infos -> RecitationWork.statesOf(infos.mapNotNull(RecitationWork::snapshotOf)) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    actual fun enqueue(key: DownloadKey, allowMobileOnce: Boolean) {
        scope.launch {
            runCatching { submit(key.reciterId, listOf(key.surah), allowMobileOnce, ExistingWorkPolicy.KEEP) }
        }
    }

    actual fun enqueueReciter(reciterId: String, allowMobileOnce: Boolean) {
        scope.launch {
            runCatching {
                val manifest = manifests.current()
                val reciter = manifest.reciter(reciterId) ?: return@runCatching
                val owned = library.downloaded(reciterId).first()
                val wanted = reciter.surahs.map { it.n }.sorted().filter { it !in owned }
                if (wanted.isEmpty()) return@runCatching
                val arabic = isArabic()
                submit(reciterId, wanted, allowMobileOnce, ExistingWorkPolicy.KEEP)
                watchBatch(
                    reciterId = reciterId,
                    reciterName = if (arabic) reciter.nameAr else reciter.nameEn,
                    total = reciter.surahs.size,
                    arabic = arabic,
                )
            }
        }
    }

    actual fun cancel(key: DownloadKey) {
        workManager.cancelUniqueWork(RecitationWork.uniqueName(key))
    }

    actual fun cancelReciter(reciterId: String) {
        workManager.cancelAllWorkByTag(RecitationWork.reciterTag(reciterId))
        batches.remove(reciterId)?.cancel()
        clearBatchNotification(reciterId)
    }

    /**
     * REPLACE rather than KEEP: Retry is pressed on a failure, and a failed unique work is
     * finished work that KEEP would step around anyway — but a retry pressed while a stalled
     * attempt is still running has to displace it, or nothing happens.
     */
    actual suspend fun retry(key: DownloadKey) {
        runCatching { submit(key.reciterId, listOf(key.surah), false, ExistingWorkPolicy.REPLACE) }
    }

    private suspend fun submit(
        reciterId: String,
        surahs: List<Int>,
        allowMobileOnce: Boolean,
        policy: ExistingWorkPolicy,
    ) {
        val manifest = manifests.current()
        val reciter = manifest.reciter(reciterId) ?: return
        val allowMetered = allowMobileOnce || settings.recitationSettings.first().downloadOnMobileData
        val arabic = isArabic()
        val names = runCatching {
            quran.surahs().associate { it.number to if (arabic) it.nameArabic else it.nameLatin }
        }.getOrDefault(emptyMap())
        val constraints = Constraints.Builder()
            // The constraint and the worker's own check say the same thing for different reasons:
            // this one stops the transfer, the worker's turns a silent queue into a sentence.
            .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        for (surah in surahs) {
            val asset = reciter.surah(surah) ?: continue
            val key = DownloadKey(reciterId, surah)
            val request = OneTimeWorkRequestBuilder<SurahDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(RecitationWork.TAG_ALL)
                .addTag(RecitationWork.reciterTag(reciterId))
                .addTag(RecitationWork.keyTag(key))
                .setInputData(
                    workDataOf(
                        RecitationWork.KEY_RECITER to reciterId,
                        RecitationWork.KEY_SURAH to surah,
                        RecitationWork.KEY_URL to manifest.assetUrl(reciter, surah),
                        RecitationWork.KEY_BYTES to asset.bytes,
                        RecitationWork.KEY_SHA to asset.sha256,
                        RecitationWork.KEY_ALLOW_METERED to allowMetered,
                        RecitationWork.KEY_SURAH_NAME to (names[surah] ?: surah.toString()),
                        RecitationWork.KEY_ARABIC to arabic,
                    )
                )
                .build()
            workManager.enqueueUniqueWork(RecitationWork.uniqueName(key), policy, request)
        }
    }

    /**
     * One line for the whole batch — "Mishary Rashid Alafasy · 12 of 114 surahs" — as the summary
     * of the group the per-surah notifications already sit in.
     *
     * The count is the library's, not the batch's: what the reader wants to know is how much of
     * this reciter they now have, and a batch that skipped the 30 surahs they already owned would
     * otherwise start at zero out of 84. It lives only as long as the app's process does, which is
     * the honest limit of a summary nobody is holding a foreground service for.
     */
    private fun watchBatch(reciterId: String, reciterName: String, total: Int, arabic: Boolean) {
        batches.remove(reciterId)?.cancel()
        batches[reciterId] = scope.launch {
            var started = false
            combine(
                workManager.getWorkInfosByTagFlow(RecitationWork.reciterTag(reciterId)),
                library.downloaded(reciterId),
            ) { infos, owned -> infos.any { !it.state.isFinished } to owned.size }
                .collect { (active, owned) ->
                    if (active) {
                        started = true
                        postBatchNotification(
                            reciterId,
                            DownloadCopy.batch(reciterName, owned, total, arabic),
                            arabic,
                        )
                    } else if (started) {
                        clearBatchNotification(reciterId)
                        throw CancellationException("The batch has finished")
                    }
                }
        }
    }

    private fun postBatchNotification(reciterId: String, text: String, arabic: Boolean) {
        SurahDownloadWorker.ensureChannel(context, arabic)
        val notification = NotificationCompat.Builder(context, SurahDownloadWorker.CHANNEL_ID)
            .setSmallIcon(notificationSmallIconResId)
            .setContentTitle(text)
            .setGroup(SurahDownloadWorker.GROUP_PREFIX + reciterId)
            .setGroupSummary(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // POST_NOTIFICATIONS may not have been granted; a batch the reader cannot see still
        // downloads, and a SecurityException here must not take the queue down with it.
        runCatching { NotificationManagerCompat.from(context).notify(batchNotificationId(reciterId), notification) }
    }

    private fun clearBatchNotification(reciterId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(batchNotificationId(reciterId)) }
    }

    private fun batchNotificationId(reciterId: String) =
        BATCH_NOTIFICATION_BASE + abs(reciterId.hashCode() % 1000)

    private fun isArabic(): Boolean =
        PrayerNaming.isArabicLanguage(createPlatformFormat().languageTag())

    private companion object {
        const val BACKOFF_SECONDS = 10L

        /** Clear of the per-surah ids, which run from 770,000. */
        const val BATCH_NOTIFICATION_BASE = 760_000
    }
}

actual fun createSurahDownloader(
    library: RecitationLibrary,
    manifests: ManifestProvider,
    settings: SettingsRepository,
    quran: QuranSource,
): SurahDownloader = SurahDownloader(library, manifests, settings, quran)
