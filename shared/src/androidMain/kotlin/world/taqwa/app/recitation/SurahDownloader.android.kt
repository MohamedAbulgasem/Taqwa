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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
    // SupervisorJob keeps one failed child from cancelling its siblings; it does nothing about an
    // uncaught throw, which still reaches the thread's default handler and takes the process down.
    // A download surface that failed is a chip with a Retry button, never a crash.
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private val batches = mutableMapOf<String, Job>()
    private val conditions = AndroidDownloadConditions(context)

    init {
        // Whatever a previous process left posted: watchBatch is re-attached only by a new
        // enqueueReciter, so without this a summary outlives the batch it was summarising. Only
        // this app's own notifications are listed, and only this class posts in the batch range.
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            manager.activeNotifications
                .map { it.id }
                .filter { it in BATCH_NOTIFICATION_BASE until BATCH_NOTIFICATION_BASE + BATCH_ID_SPAN }
                .forEach { manager.cancel(it) }
        }
    }

    /**
     * The two states WorkManager cannot report. A download the network refuses — mobile data
     * against the reader's wishes, or no connection at all — is never enqueued, so there is no
     * `WorkInfo` to read it from and the reader who tapped Download would otherwise be told
     * nothing. See [submit].
     *
     * Only ever holds [DownloadFailure.NEEDS_WIFI] and [DownloadFailure.NO_NETWORK], and only
     * until the same surah is asked for again on a network that allows it.
     */
    private val refusals = MutableStateFlow<Map<DownloadKey, DownloadState>>(emptyMap())

    /**
     * Work first, refusals over the top: a refusal is always the freshest thing known about a key,
     * because nothing was enqueued for it and any `WorkInfo` still lying around is an older
     * attempt. [forget] takes the refusal away again the moment the surah is re-enqueued.
     */
    actual val states: StateFlow<Map<DownloadKey, DownloadState>> =
        combine(
            workManager.getWorkInfosByTagFlow(RecitationWork.TAG_ALL)
                .map { infos -> RecitationWork.statesOf(infos.mapNotNull(RecitationWork::snapshotOf)) },
            refusals,
        ) { work, refused -> work + refused }
            // WorkManager's flow is a Room query and can throw; the sheet showing a stale map is
            // better than the collector dying in a scope nothing else is watching.
            .catch { }
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
        forget(listOf(key))
        workManager.cancelUniqueWork(RecitationWork.uniqueName(key))
    }

    actual fun cancelReciter(reciterId: String) {
        refusals.value = refusals.value.filterKeys { it.reciterId != reciterId }
        workManager.cancelAllWorkByTag(RecitationWork.reciterTag(reciterId))
        batches.remove(reciterId)?.cancel()
        clearBatchNotification(reciterId)
    }

    /** Drops a refusal, because the surah is being asked for again or dismissed. */
    private fun forget(keys: List<DownloadKey>) {
        if (refusals.value.keys.none { it in keys }) return
        refusals.value = refusals.value - keys.toSet()
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
        val keys = surahs.map { DownloadKey(reciterId, it) }
        // Refuse before enqueueing rather than inside the worker. The network constraint below is
        // what actually keeps a transfer off mobile data — including one already running when the
        // reader leaves the house — but a constraint that is not met leaves the work sitting in
        // the queue saying nothing, and two device rounds showed exactly that: on mobile data, and
        // then in flight mode, a download that reads "Queued" for ever. So the network is asked
        // here, where there is still a reader to answer it (spec §5.4), and both of its answers —
        // the Wi-Fi policy and no connection at all — become a sentence in the sheet.
        conditions.refusal(allowMetered)?.let { reason ->
            refusals.value = refusals.value + keys.associateWith { DownloadState.Failed(reason) }
            return
        }
        forget(keys)
        val arabic = isArabic()
        val names = runCatching {
            quran.surahs().associate { it.number to if (arabic) it.nameArabic else it.nameLatin }
        }.getOrDefault(emptyMap())
        val constraints = Constraints.Builder()
            // This is the rule that holds mid-transfer: a download running when the reader walks
            // out of Wi-Fi is stopped by the constraint and resumes from its `.part` when they
            // come back. The refusal above is the same rule asked once, at the moment of the tap.
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
                // Same reason as `states` above, plus a DataStore read on the other side of the
                // combine. `catch` is transparent to what the collector throws, so the deliberate
                // CancellationException below still ends the batch.
                .catch { }
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
            // Not ongoing. The per-surah notifications are the ongoing ones, and WorkManager owns
            // and cancels those; this summary is cleared only by watchBatch's collector, which
            // dies with the process — and a whole-Quran batch runs for hours across process
            // deaths. On API 26–33 setOngoing would then leave a frozen "12 of 114" the reader
            // cannot even swipe away.
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
        BATCH_NOTIFICATION_BASE + abs(reciterId.hashCode() % BATCH_ID_SPAN)

    private fun isArabic(): Boolean =
        PrayerNaming.isArabicLanguage(createPlatformFormat().languageTag())

    private companion object {
        const val BACKOFF_SECONDS = 10L

        /** Clear of the per-surah ids, which run from 770,000. */
        const val BATCH_NOTIFICATION_BASE = 760_000

        /** How many ids [batchNotificationId] can mint, and so how wide the start-up sweep is. */
        const val BATCH_ID_SPAN = 1_000
    }
}

actual fun createSurahDownloader(
    library: RecitationLibrary,
    manifests: ManifestProvider,
    settings: SettingsRepository,
    quran: QuranSource,
): SurahDownloader = SurahDownloader(library, manifests, settings, quran)
