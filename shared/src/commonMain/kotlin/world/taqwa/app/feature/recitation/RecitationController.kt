package world.taqwa.app.feature.recitation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.NowPlayingText
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.RecitationManifest
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.recitation.SurahSkip

/**
 * One per app: everything the recitation surface knows, and every decision it makes (spec 3a §5).
 *
 * The four pieces below it — the catalogue, the library, the downloader and the player — each
 * answer one question well and none of them knows about the others. Deciding that a tap on the
 * header means *play from here* for a surah the reader owns and *offer the download* for one they
 * do not, that a download which commits should start the ayah the reader originally asked for,
 * and that changing voice mid-surah should be silent about a voice that has nothing to play, is
 * this class's whole job. It holds no Compose types and no strings: the screens draw
 * [RecitationState] and resolve their own words.
 *
 * It survives navigation because [world.taqwa.app.di.AppContainer] holds it, which is what lets
 * the bar go on playing while the reader walks from the Mushaf to Settings and back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecitationController(
    /** The catalogue in force, fetched once. Null means it could not be read at all. */
    private val manifests: suspend () -> RecitationManifest?,
    private val library: LibraryPort,
    private val downloader: DownloaderPort,
    private val player: PlayerPort,
    private val settings: RecitationSettingsPort,
    private val quran: QuranSource,
    private val clips: ClipPort,
    /** The bundled fifteen-second clip for a reciter, or null when this build has none. */
    private val previewBytes: suspend (String) -> ByteArray?,
    private val scope: CoroutineScope,
    /**
     * Privacy spec §2.2: recorded, fire-and-forget, at the first line of every entry point.
     * Until it has been called once, the app makes no network request at all.
     */
    private val markEngaged: suspend () -> Unit = {},
    /**
     * Spec §2.4: the daily catalogue check, run opportunistically when the picker or the
     * Recitation settings open. The refresher's own 24-hour window keeps this to one a day.
     */
    private val refreshCatalogue: suspend () -> Unit = {},
) {

    private val manifest = MutableStateFlow<RecitationManifest?>(null)
    private val sheetSurah = MutableStateFlow<Int?>(null)
    private val picker = MutableStateFlow(false)
    private val previewing = MutableStateFlow<String?>(null)
    private val previewable = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The reciters this process asked for a whole-Quran batch of (spec §12.8). Held here rather
     * than inferred from the downloader, because one surah in flight looks exactly like a batch of
     * one; see [wholeQuranOf], which also has a process-free fallback for the batch that outlives
     * the app. Dropped when the reciter's last surah stops moving, and by Cancel.
     */
    private val batching = MutableStateFlow<Set<String>>(emptySet())

    /**
     * True while [PlayerPort.load] is in flight. The iOS player splits the container into per-ayah
     * files on the first play of a surah, which for Al-Baqarah is a visible pause, and it reports
     * no `buffering` of its own during it (it has no `AVPlayerItem` to ask yet). So the bar's
     * buffering state is set here, around the call, on both platforms.
     */
    private val loading = MutableStateFlow(false)

    /**
     * The play the reader asked for before the surah was on the phone (spec §5.4: "playback starts
     * from the ayah you chose as soon as it lands"). Cleared when it fires, when the sheet's
     * download is cancelled, and when another play is requested.
     */
    private var pending: PendingPlay? = null

    /** The ayah the open sheet was opened for, so a confirm knows where to start. */
    private var sheetAyah = 1

    /** Whether the UI is Arabic, for the lock screen's two lines. Set by the composition. */
    private var arabicUi = false

    /** The Android notification's two ayah buttons, localised by the composition (spec §15.1). */
    private var previousAyahLabel = ""
    private var nextAyahLabel = ""

    /**
     * Downloads whose refusal has already been put in front of the reader (spec §15.3): with
     * auto-download on there is no sheet to show a refusal on, so the sheet opens itself the
     * first time a fetch the reader is waiting on fails — once per failure, not on every emission.
     */
    private val surfacedFailures = mutableSetOf<DownloadKey>()

    /**
     * The bar's progress line, held across emissions. Exactly one collector writes it — the
     * `state` pipeline below — which is what makes a `var` in a class honest here rather than a
     * race waiting to happen.
     */
    private var fraction = 0f

    /** The surah [fraction] was measured in, so a new surah's line starts at nothing. */
    private var fractionSurah: Int? = null

    /**
     * [follow] is spec §14.3's switch: the surah was being recited by another voice when the
     * download was asked for, so when it lands the new voice starts at the ayah being *heard
     * then*, not the one on screen when the sheet opened.
     */
    private data class PendingPlay(
        val reciterId: String,
        val surah: Int,
        val ayah: Int,
        val follow: Boolean = false,
    )

    /**
     * True while a preview is playing over a recitation this controller paused for it (spec
     * §14.5). The recitation is given back when the clip ends, when the picker closes, or when
     * a pick leaves the old voice playing — and not when the reader has already pressed play
     * themselves, which is the one case the flag has to be cleared without acting on.
     */
    private var pausedForPreview = false

    /** The current reciter: what the reader chose, or the catalogue's first voice if that id has
     * been withdrawn from the manifest since it was chosen. */
    private val reciter: StateFlow<Reciter?> =
        combine(manifest, settings.settings) { catalogue, chosen ->
            catalogue?.let { it.reciter(chosen.reciterId) ?: it.reciters.firstOrNull() }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val downloaded: StateFlow<Set<Int>> = reciter
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else library.downloaded(id) }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Every reciter's surahs, not just the current one's. The picker's caption only wants the
     * counts, but Settings › Recitation lists a card per reciter that has anything at all and the
     * Downloads screen lists that reciter's surahs by number — all three off this one collection,
     * so the three of them can never disagree about what is on the phone.
     */
    private val allDownloaded: StateFlow<Map<String, Set<Int>>> = manifest
        .filterNotNull()
        .flatMapLatest { catalogue ->
            if (catalogue.reciters.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    catalogue.reciters.map { r -> library.downloaded(r.id).map { r.id to it } },
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<RecitationState> = combine(
        combine(manifest, reciter, downloaded, allDownloaded) { catalogue, current, owned, byReciter ->
            Catalogue(catalogue, current, owned, byReciter)
        },
        combine(player.state, downloader.states, loading) { playback, downloads, busy ->
            Playing(playback, downloads, busy)
        },
        combine(
            sheetSurah,
            picker,
            previewing,
            previewable,
            combine(settings.settings, batching) { prefs, batches -> prefs to batches },
        ) { surah, pickerOpen, preview, clipsAvailable, (prefs, batches) ->
            Surface(
                surah, pickerOpen, preview, clipsAvailable, prefs.downloadOnMobileData, batches,
                autoDownload = prefs.autoDownload,
                autoDownloadAsked = prefs.autoDownloadAsked,
            )
        },
    ) { catalogue, playing, surface ->
        assemble(catalogue, playing, surface)
    }.stateIn(scope, SharingStarted.Eagerly, RecitationState())

    private class Catalogue(
        val manifest: RecitationManifest?,
        val reciter: Reciter?,
        val downloaded: Set<Int>,
        val byReciter: Map<String, Set<Int>>,
    )

    private class Playing(
        val playback: PlaybackState,
        val downloads: Map<DownloadKey, DownloadState>,
        val loading: Boolean,
    )

    private class Surface(
        val sheetSurah: Int?,
        val pickerOpen: Boolean,
        val previewing: String?,
        val previewable: Set<String>,
        val downloadOnMobileData: Boolean,
        val batching: Set<String>,
        val autoDownload: Boolean,
        val autoDownloadAsked: Boolean,
    )

    private fun assemble(catalogue: Catalogue, playing: Playing, surface: Surface): RecitationState {
        val bar = barOf(playing, catalogue)
        val sheet = surface.sheetSurah?.let { surah ->
            val voice = catalogue.reciter ?: return@let null
            val total = voice.surah(surah)?.bytes ?: 0L
            DownloadSheetState(
                surah = surah,
                reciter = voice,
                bytes = total,
                phase = sheetPhaseOf(
                    playing.downloads[DownloadKey(voice.id, surah)],
                    surface.downloadOnMobileData,
                    total,
                ),
                playingMeanwhile = bar?.takeIf { it.surah == surah && it.reciter.id != voice.id }?.reciter,
                autoDownloadDefault = if (surface.autoDownloadAsked) surface.autoDownload else true,
            )
        }
        return RecitationState(
            reciter = catalogue.reciter,
            reciters = catalogue.manifest?.reciters.orEmpty(),
            downloaded = catalogue.downloaded,
            downloadedByReciter = catalogue.byReciter,
            downloads = playing.downloads,
            bar = bar,
            sheet = sheet,
            pickerOpen = surface.pickerOpen,
            previewing = surface.previewing,
            previewable = surface.previewable,
            downloadOnMobileData = surface.downloadOnMobileData,
            autoDownload = surface.autoDownload,
            wholeQuran = wholeQuranOf(
                reciter = catalogue.reciter,
                owned = catalogue.downloaded,
                downloads = playing.downloads,
                declared = catalogue.reciter?.id in surface.batching,
            ),
        )
    }

    /**
     * The bar, or null when there is nothing to show one for. The reciter comes from the
     * *playback*, not from the setting: picking a voice the current surah is not downloaded for
     * leaves the old one playing (see [pickReciter]), and the bar must name the voice being heard.
     * The setting's voice appears only as [BarState.incoming], while its copy of the surah is on
     * its way.
     */
    private fun barOf(playing: Playing, catalogue: Catalogue): BarState? {
        val playback = playing.playback
        val surah = playback.surah
        val ayah = playback.ayah
        val voice = playback.reciterId?.let { catalogue.manifest?.reciter(it) }
        if (surah == null || ayah == null || voice == null) {
            fraction = 0f
            fractionSurah = null
            return null
        }
        // A held fraction belongs to the surah it was measured in. Without this the line opened a
        // new surah at wherever the last one ended — Al-Faatiha finishes at 1.0, and the first
        // frames of Al-Baqarah showed a full progress line easing back down, because the first
        // item of a surah reports no duration yet and [barFraction] answers with `previous`.
        if (surah != fractionSurah) {
            fraction = 0f
            fractionSurah = surah
        }
        fraction = barFraction(playback, fraction)
        // The ring on the monogram: whatever the bar is waiting on - a new voice's copy of this
        // surah (§14.3) or the next surah fetched without asking (§15.3) - and, failing a play
        // that is waiting, the chosen voice's copy of this surah arriving by some other route.
        val chosen = catalogue.reciter
        val waiting = pending
        val incoming = waiting?.let { downloadFraction(playing.downloads[DownloadKey(it.reciterId, it.surah)]) }
            ?: chosen?.takeIf { it.id != voice.id }?.let { next ->
                downloadFraction(playing.downloads[DownloadKey(next.id, surah)])
            }
        return BarState(
            reciter = voice,
            surah = surah,
            ayah = ayah,
            ayahCount = playback.ayahCount,
            fraction = fraction,
            playing = playback.playing,
            buffering = playback.buffering || playing.loading,
            positionMs = playback.surahPositionMs,
            durationMs = playback.surahDurationMs,
            incoming = incoming,
        )
    }

    init {
        scope.launch { manifest.value = runCatching { manifests() }.getOrNull() }
        // The pending auto-play (spec §5.4). The library is the signal, not the downloader:
        // a surah leaves `states` the moment it commits, and "committed" means hashed and
        // renamed — the one moment at which it can actually be played.
        // A batch is over when the downloader has nothing of that reciter left in flight. Dropped
        // here rather than by the row that reads it, so a reader who never opens Settings does not
        // leave the app believing a finished batch is still running.
        //
        // `seen` is what stops the drop firing on the empty state map that is still there in the
        // frames between the button and the downloader's first emission: a reciter is only ever
        // forgotten once it has actually been observed moving. A refusal is kept — the row has a
        // sentence to show for it, and the reader clears it by trying again or walking away.
        scope.launch {
            val seen = mutableSetOf<String>()
            downloader.states.collect { states ->
                if (batching.value.isEmpty()) {
                    seen.clear()
                    return@collect
                }
                val moving = states.filterValues { it !is DownloadState.Failed }.keys.map { it.reciterId }.toSet()
                val known = states.keys.map { it.reciterId }.toSet()
                seen += moving
                batching.value = batching.value.filter { it !in seen || it in known }.toSet()
                seen.retainAll(batching.value)
            }
        }
        // The lock screen's previous and next (spec §15.1): by surah, decided here like every
        // other press, so a surah not on the phone goes the same way it would from the bar.
        scope.launch {
            player.skips.collect { skip ->
                when (skip) {
                    SurahSkip.NEXT -> nextSurah()
                    SurahSkip.PREVIOUS -> previousSurah()
                }
            }
        }
        // A refusal the reader would otherwise never see (spec §15.3): auto-download asks
        // nothing, so when the fetch behind a pending play fails - no Wi-Fi, no network, no room
        // - the sheet opens on the failure face, once, with the sentence and its Retry.
        scope.launch {
            downloader.states.collect { states ->
                surfacedFailures.retainAll { states[it] is DownloadState.Failed }
                val waiting = pending ?: return@collect
                val key = DownloadKey(waiting.reciterId, waiting.surah)
                if (states[key] !is DownloadState.Failed || key in surfacedFailures) return@collect
                surfacedFailures += key
                if (sheetSurah.value == null) {
                    sheetAyah = waiting.ayah
                    sheetSurah.value = waiting.surah
                }
            }
        }
        scope.launch {
            downloaded.collect { owned ->
                val waiting = pending ?: return@collect
                if (waiting.reciterId != reciter.value?.id) return@collect
                if (waiting.surah !in owned) return@collect
                pending = null
                sheetSurah.value = null
                // A switch (spec §14.3) picks up where the old voice has got to, and in the state
                // the listener left it: a surah they had paused does not start reading itself
                // out in a new voice because a download finished — and a surah that has since
                // ended, or been dismissed, does not start again at all. Only the plain §5.4
                // play, which the reader asked for by name, goes ahead with nothing playing.
                val live = player.state.value.takeIf { it.surah == waiting.surah }
                if (waiting.follow && live == null) return@collect
                start(waiting.surah, if (waiting.follow) live?.ayah ?: waiting.ayah else waiting.ayah)
                if (waiting.follow && live != null && !live.playing) player.pause()
            }
        }
    }

    /** The two lines a lock screen shows. The ayah is deliberately not among them: it changes
     * every few seconds and a lock screen that flickers is worse than one that says less. */
    fun setArabicUi(arabic: Boolean) {
        arabicUi = arabic
    }

    /** The Android notification's "previous ayah" and "next ayah" buttons (spec §15.1). */
    fun setAyahButtonLabels(previous: String, next: String) {
        previousAyahLabel = previous
        nextAyahLabel = next
    }

    // ── What a tap means ────────────────────────────────────────────────────────────────

    /** The header button and the ayah row's Play, for a surah the reader may or may not own. */
    fun requestPlay(surah: Int, ayah: Int) {
        engage()
        playOrOffer(surah, ayah)
    }

    /**
     * [requestPlay] without the engagement mark, for [onHeaderTap], which has already made it.
     *
     * A surah the reader owns plays. One they do not is offered on the sheet — or, once they
     * have said "without asking" (spec §15.3), fetched at once and played the moment it lands,
     * the header's ring being the only thing that moves in between.
     */
    private fun playOrOffer(surah: Int, ayah: Int) {
        scope.launch {
            if (surah in downloaded.value) {
                pending = null
                start(surah, ayah)
                return@launch
            }
            picker.value = false
            val voice = reciter.value
            if (voice != null && settings.settings.first().autoDownload) {
                pending = PendingPlay(voice.id, surah, ayah)
                downloader.enqueue(DownloadKey(voice.id, surah), allowMobileOnce = false)
                return@launch
            }
            sheetAyah = ayah
            sheetSurah.value = surah
        }
    }

    /**
     * The bar's next and previous, and the lock screen's (spec §15.1): the neighbouring surah
     * from its first ayah, through the same path as a tap on its header, so a surah not on the
     * phone is offered or fetched exactly as it would be there. At the ends of the Quran, nothing.
     */
    fun nextSurah() {
        val playing = state.value.bar?.surah ?: return
        if (playing < LAST_SURAH) requestPlay(playing + 1, 1)
    }

    fun previousSurah() {
        val playing = state.value.bar?.surah ?: return
        if (playing > 1) requestPlay(playing - 1, 1)
    }

    /**
     * The header button (spec §5.1). One tap, three meanings, and the button's own state is what
     * says which: pause what is playing on this surah, or start it — which for a surah that is
     * not on the phone means the download sheet, whether it has never been asked for or is
     * arriving right now.
     */
    fun onHeaderTap(surah: Int, ayah: Int) {
        engage()
        val playing = state.value.bar
        if (playing != null && playing.surah == surah) toggle() else playOrOffer(surah, ayah)
    }

    /** The download sheet's primary button. [allowMobileOnce] is spec §5.4's one-tap override. */
    fun confirmDownload(allowMobileOnce: Boolean, autoDownload: Boolean = false) {
        val surah = sheetSurah.value ?: return
        val voice = reciter.value ?: return
        // The sheet's switch (spec §15.3), written whatever position it is in: a confirm is the
        // moment the reader has been asked.
        scope.launch { settings.setAutoDownload(autoDownload) }
        // Another voice reciting this surah right now is what makes this a switch (spec §14.3).
        pending = PendingPlay(voice.id, surah, sheetAyah, follow = player.state.value.surah == surah)
        downloader.enqueue(DownloadKey(voice.id, surah), allowMobileOnce)
    }

    /** Settings › Recitation's "Download without asking" (spec §15.3). */
    fun setAutoDownload(value: Boolean) {
        scope.launch { settings.setAutoDownload(value) }
    }

    /** The quiet Cancel under the progress bar. The sheet stays up, showing Ready again. */
    fun cancelDownload() {
        val surah = sheetSurah.value ?: return
        val voice = reciter.value ?: return
        pending = null
        downloader.cancel(DownloadKey(voice.id, surah))
    }

    fun retryDownload() {
        val surah = sheetSurah.value ?: return
        val voice = reciter.value ?: return
        scope.launch { downloader.retry(DownloadKey(voice.id, surah)) }
    }

    /** Closes the sheet without cancelling: spec §5.4 lets the reader go on reading while a
     * surah arrives, and the header button's ring is what keeps the download visible. */
    fun dismissSheet() {
        sheetSurah.value = null
    }

    // ── The whole Quran (spec §5.4, §12.8) ──────────────────────────────────────────────

    /**
     * Every surah of the current reciter the reader does not already own, as one batch. The
     * downloader decides the order, the two-at-a-time limit and the free-space refusal; all this
     * does is say which voice, remember that a batch is running, and — as with a single surah —
     * pass the sheet's one-time mobile-data override straight through.
     */
    fun downloadWholeQuran(allowMobileOnce: Boolean = false) {
        engage()
        val voice = reciter.value ?: return
        batching.value = batching.value + voice.id
        downloader.enqueueReciter(voice.id, allowMobileOnce)
    }

    /**
     * The Cancel beside a running batch. Every surah still queued or in flight is dropped; the
     * ones that have already committed stay on the phone, because they are in the library and the
     * library is not what a cancel touches.
     */
    fun cancelWholeQuran() {
        val voice = reciter.value ?: return
        batching.value = batching.value - voice.id
        downloader.cancelReciter(voice.id)
    }

    // ── Settings › Recitation (spec §5.6) ───────────────────────────────────────────────

    fun setDownloadOnMobileData(value: Boolean) {
        scope.launch { settings.setDownloadOnMobileData(value) }
    }

    /** What every reciter's downloads occupy, off the disk. Asked for rather than observed: it
     * stats every file, and the two screens that show it ask again when the registry changes. */
    suspend fun storage(): RecitationStorage {
        val ids = (manifest.value?.reciters?.map { it.id }.orEmpty() + allDownloaded.value.keys).distinct()
        val byReciter = ids.associateWith { runCatching { library.bytesUsed(it) }.getOrDefault(0L) }
        return RecitationStorage(
            total = runCatching { library.bytesUsedTotal() }.getOrDefault(byReciter.values.sum()),
            byReciter = byReciter.filterValues { it > 0L },
        )
    }

    /**
     * Forgets one surah. **Playback stops first** when it is the surah being recited: the file is
     * about to go, and on Android the player holds it open through a `DataSource` that would go on
     * reading a deleted inode until the queue ran out.
     */
    suspend fun deleteSurah(reciterId: String, surah: Int) {
        stopIfPlaying(reciterId) { it == surah }
        library.delete(reciterId, surah)
    }

    /** Forgets everything of one reciter — file, registry and, if it was this voice, the playback. */
    suspend fun deleteReciter(reciterId: String) {
        batching.value = batching.value - reciterId
        downloader.cancelReciter(reciterId)
        stopIfPlaying(reciterId) { true }
        library.deleteReciter(reciterId)
    }

    private fun stopIfPlaying(reciterId: String, surah: (Int) -> Boolean) {
        val playback = player.state.value
        if (playback.reciterId != reciterId) return
        val current = playback.surah ?: return
        if (surah(current)) stop()
    }

    fun openPicker() {
        engage(refresh = true)
        picker.value = true
        probePreviews()
    }

    /**
     * Settings › Quran › Recitation opened (privacy spec §2.2): engagement on purpose — the screen
     * shows the reciter list and "Download the whole Quran", and whoever went there wants the
     * current catalogue.
     */
    fun onSettingsOpened() = engage(refresh = true)

    /**
     * The first line of every entry point. [refresh] only where a fresh catalogue is what the
     * reader is about to look at; a tap to play is not a reason to fetch it.
     */
    private fun engage(refresh: Boolean = false) {
        scope.launch {
            markEngaged()
            if (refresh) refreshCatalogue()
        }
    }

    fun closePicker() {
        picker.value = false
        endPreview()
    }

    /** The bar's play/pause. A preview playing over a paused recitation ends first, and does not
     * resume anything itself: the reader's own press is the resume. */
    fun toggle() {
        if (previewing.value != null) endPreview(resume = false)
        player.toggle()
    }

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekToAyah(n: Int) = player.seekToAyah(n)

    fun stop() {
        pending = null
        player.stop()
    }

    /** The bar's "×" and its downward swipe. Dismissing the bar is stopping — there is no
     * hidden playback with no way back to it. */
    fun dismissBar() = stop()

    /**
     * A new voice (spec §5.5, §14.3, §14.4). Persisted first, so the choice survives whatever
     * happens next; and it supersedes any switch still waiting on a download, because the reader
     * has just said which voice they want and it is this one.
     *
     * Then, by what is playing:
     * - **the same voice, paused** — resume it. The row is the voice they are listening to, and a
     *   tap on it while nothing is coming out of the phone can only mean "go on".
     * - **another voice that has this surah** — swap at the current ayah, near enough the "next
     *   ayah boundary" the spec asks for, and honest about where the reader is.
     * - **another voice that does not** — offer the download, and **the old voice keeps playing**
     *   until it lands, when the new one takes over at the ayah being heard (the sheet says so:
     *   [DownloadSheetState.playingMeanwhile]). Before this the tap did nothing visible at all,
     *   and the reader had to stop the bar to be offered the download.
     */
    fun pickReciter(id: String) {
        scope.launch {
            val hadPausedForPreview = endPreview(resume = false)
            // Before the settings write suspends, not after: the library collector shares this
            // scope, and a switch to some other voice landing inside that suspension must not
            // still be armed. A switch to *this* voice is the one thing a re-pick must keep.
            if (pending?.reciterId != id) pending = null
            settings.setReciter(id)
            val voice = manifest.value?.reciter(id) ?: return@launch
            val playback = player.state.value
            val surah = playback.surah
            if (surah == null) return@launch
            if (playback.reciterId == id) {
                if (!playback.playing) player.play()
                return@launch
            }
            if (library.isDownloaded(id, surah)) {
                start(surah, playback.ayah ?: 1, voice)
                return@launch
            }
            if (hadPausedForPreview) player.play()
            picker.value = false
            // The copy may already be on its way — this very switch, dismissed and re-picked, or a
            // whole-Quran batch. The sheet then opens on its progress bar, which has no button to
            // arm anything with, so the switch is armed here; a Ready face's confirm overwrites it.
            val key = DownloadKey(id, surah)
            if (pending == null && downloader.states.value[key] != null) {
                pending = PendingPlay(id, surah, playback.ayah ?: 1, follow = true)
            }
            // "Without asking" (spec §15.3) covers a new voice too: the old one keeps playing, the
            // ring on the monogram shows the new one arriving, and no sheet comes up.
            if (settings.settings.first().autoDownload) {
                if (pending == null) pending = PendingPlay(id, surah, playback.ayah ?: 1, follow = true)
                if (downloader.states.value[key] == null) downloader.enqueue(key, allowMobileOnce = false)
                return@launch
            }
            sheetAyah = playback.ayah ?: 1
            sheetSurah.value = surah
        }
    }

    /**
     * The picker's play triangle. A second tap on the same row, or picking anything, stops it.
     *
     * A recitation that is playing is paused for the audition and resumed when the clip ends
     * (spec §14.5): the clip used to play straight over the surah, two voices at once, which is
     * not what a person auditioning a reciter wants to hear.
     */
    fun previewReciter(id: String) {
        scope.launch {
            if (previewing.value == id) {
                endPreview()
                return@launch
            }
            // Claim the row before the clip is read: the read suspends, and a previous preview's
            // end arriving in that gap must find its row already gone rather than resume the
            // recitation under a preview that is about to start. If the old preview had paused
            // the recitation, this one inherits the pause.
            val inherited = endPreview(resume = false)
            previewing.value = id
            val bytes = runCatching { previewBytes(id) }.getOrNull()
            if (bytes == null) {
                previewing.value = null
                if (inherited) player.play()
                return@launch
            }
            if (inherited) {
                pausedForPreview = true
            } else if (player.state.value.playing) {
                pausedForPreview = true
                player.pause()
            }
            clips.play(bytes) {
                scope.launch { if (previewing.value == id) endPreview() }
            }
            previewTimeout?.cancel()
            previewTimeout = scope.launch {
                delay(PREVIEW_MILLIS)
                if (previewing.value == id) endPreview()
            }
        }
    }

    private var previewTimeout: Job? = null
    private var probe: Job? = null

    /**
     * Stops the clip, and — when [resume] — gives the recitation back if the preview had paused
     * it. Returns whether it had, for the one caller that decides about the resume itself
     * ([pickReciter]: a pick that starts a new voice resumes nothing, a pick that leaves the old
     * voice playing resumes it).
     */
    private fun endPreview(resume: Boolean = true): Boolean {
        previewTimeout?.cancel()
        previewTimeout = null
        clips.stop()
        previewing.value = null
        val hadPaused = pausedForPreview
        pausedForPreview = false
        if (hadPaused && resume) player.play()
        return hadPaused
    }

    /**
     * Which reciters this build actually carries a clip for. The pipeline adds the other nine
     * later, and a picker that drew a triangle for a clip that is not there would be a button
     * that does nothing — so the file is asked for once, when the picker opens, and a reciter
     * whose clip is missing simply has no triangle.
     */
    private fun probePreviews() {
        if (probe?.isActive == true) return
        probe = scope.launch {
            val ids = manifest.value?.reciters?.map { it.id }.orEmpty()
            val found = ids.filter { runCatching { previewBytes(it) }.getOrNull() != null }
            previewable.value = found.toSet()
        }
    }

    private suspend fun start(surah: Int, ayah: Int, voice: Reciter? = null) {
        val playWith = voice ?: reciter.value ?: return
        loading.value = true
        try {
            player.load(playWith, surah, ayah, nowPlaying(playWith, surah))
            player.play()
        } finally {
            loading.value = false
        }
    }

    private suspend fun nowPlaying(voice: Reciter, surah: Int): NowPlayingText {
        val named = runCatching { quran.surah(surah) }.getOrNull()
        val title = named?.let { if (arabicUi) it.nameArabic else it.nameLatin }.orEmpty()
        return NowPlayingText(
            title = title,
            subtitle = if (arabicUi) voice.nameAr else voice.nameEn,
            previousAyahLabel = previousAyahLabel,
            nextAyahLabel = nextAyahLabel,
        )
    }

    /** For the one caller that needs the setting outside a composition: the sheet's override. */
    suspend fun downloadsOnMobileData(): Boolean = settings.settings.first().downloadOnMobileData
}

/** Long enough for the fifteen-second clip plus a moment: the backstop for a clip player that
 * never reports its end, so the row stops claiming to play and a paused recitation is resumed. */
private const val PREVIEW_MILLIS = 17_000L

private const val LAST_SURAH = 114
