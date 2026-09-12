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
) {

    private val manifest = MutableStateFlow<RecitationManifest?>(null)
    private val sheetSurah = MutableStateFlow<Int?>(null)
    private val picker = MutableStateFlow(false)
    private val previewing = MutableStateFlow<String?>(null)
    private val previewable = MutableStateFlow<Set<String>>(emptySet())

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

    /**
     * The bar's progress line, held across emissions. Exactly one collector writes it — the
     * `state` pipeline below — which is what makes a `var` in a class honest here rather than a
     * race waiting to happen.
     */
    private var fraction = 0f

    private data class PendingPlay(val reciterId: String, val surah: Int, val ayah: Int)

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

    private val counts: StateFlow<Map<String, Int>> = manifest
        .filterNotNull()
        .flatMapLatest { catalogue ->
            if (catalogue.reciters.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    catalogue.reciters.map { r -> library.downloaded(r.id).map { r.id to it.size } },
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<RecitationState> = combine(
        combine(manifest, reciter, downloaded, counts) { catalogue, current, owned, byReciter ->
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
            settings.settings.map { it.downloadOnMobileData },
        ) { surah, pickerOpen, preview, clipsAvailable, mobileData ->
            Surface(surah, pickerOpen, preview, clipsAvailable, mobileData)
        },
    ) { catalogue, playing, surface ->
        assemble(catalogue, playing, surface)
    }.stateIn(scope, SharingStarted.Eagerly, RecitationState())

    private class Catalogue(
        val manifest: RecitationManifest?,
        val reciter: Reciter?,
        val downloaded: Set<Int>,
        val counts: Map<String, Int>,
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
    )

    private fun assemble(catalogue: Catalogue, playing: Playing, surface: Surface): RecitationState {
        val bar = barOf(playing, catalogue.manifest)
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
            )
        }
        return RecitationState(
            reciter = catalogue.reciter,
            reciters = catalogue.manifest?.reciters.orEmpty(),
            downloaded = catalogue.downloaded,
            downloadedCounts = catalogue.counts,
            downloads = playing.downloads,
            bar = bar,
            sheet = sheet,
            pickerOpen = surface.pickerOpen,
            previewing = surface.previewing,
            previewable = surface.previewable,
        )
    }

    /**
     * The bar, or null when there is nothing to show one for. The reciter comes from the
     * *playback*, not from the setting: picking a voice the current surah is not downloaded for
     * leaves the old one playing (see [pickReciter]), and the bar must name the voice being heard.
     */
    private fun barOf(playing: Playing, catalogue: RecitationManifest?): BarState? {
        val playback = playing.playback
        val surah = playback.surah
        val ayah = playback.ayah
        val voice = playback.reciterId?.let { catalogue?.reciter(it) }
        if (surah == null || ayah == null || voice == null) {
            fraction = 0f
            return null
        }
        fraction = barFraction(playback, fraction)
        return BarState(
            reciter = voice,
            surah = surah,
            ayah = ayah,
            ayahCount = playback.ayahCount,
            fraction = fraction,
            playing = playback.playing,
            buffering = playback.buffering || playing.loading,
        )
    }

    init {
        scope.launch { manifest.value = runCatching { manifests() }.getOrNull() }
        // The pending auto-play (spec §5.4). The library is the signal, not the downloader:
        // a surah leaves `states` the moment it commits, and "committed" means hashed and
        // renamed — the one moment at which it can actually be played.
        scope.launch {
            downloaded.collect { owned ->
                val waiting = pending ?: return@collect
                if (waiting.reciterId != reciter.value?.id) return@collect
                if (waiting.surah !in owned) return@collect
                pending = null
                sheetSurah.value = null
                start(waiting.surah, waiting.ayah)
            }
        }
    }

    /** The two lines a lock screen shows. The ayah is deliberately not among them: it changes
     * every few seconds and a lock screen that flickers is worse than one that says less. */
    fun setArabicUi(arabic: Boolean) {
        arabicUi = arabic
    }

    // ── What a tap means ────────────────────────────────────────────────────────────────

    /** The header button and the ayah row's Play, for a surah the reader may or may not own. */
    fun requestPlay(surah: Int, ayah: Int) {
        scope.launch {
            if (surah in downloaded.value) {
                pending = null
                start(surah, ayah)
            } else {
                sheetAyah = ayah
                sheetSurah.value = surah
                picker.value = false
            }
        }
    }

    /**
     * The header button (spec §5.1). One tap, three meanings, and the button's own state is what
     * says which: pause what is playing on this surah, or start it — which for a surah that is
     * not on the phone means the download sheet, whether it has never been asked for or is
     * arriving right now.
     */
    fun onHeaderTap(surah: Int, ayah: Int) {
        val playing = state.value.bar
        if (playing != null && playing.surah == surah) toggle() else requestPlay(surah, ayah)
    }

    /** The download sheet's primary button. [allowMobileOnce] is spec §5.4's one-tap override. */
    fun confirmDownload(allowMobileOnce: Boolean) {
        val surah = sheetSurah.value ?: return
        val voice = reciter.value ?: return
        pending = PendingPlay(voice.id, surah, sheetAyah)
        downloader.enqueue(DownloadKey(voice.id, surah), allowMobileOnce)
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

    fun openPicker() {
        picker.value = true
        probePreviews()
    }

    fun closePicker() {
        picker.value = false
        stopPreview()
    }

    fun toggle() = player.toggle()

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
     * A new voice (spec §5.5). Persisted first, so the choice survives whatever happens next.
     *
     * If a surah is playing and the new voice has it, the swap happens at the current ayah — near
     * enough the "next ayah boundary" the spec asks for, and honest about where the reader is. If
     * the new voice does not have this surah, **the old one keeps playing**: the picker's own
     * caption already says the new voice has nothing on this phone, and a download sheet thrown
     * up by a tap on a radio button would be a sheet nobody asked for.
     */
    fun pickReciter(id: String) {
        scope.launch {
            stopPreview()
            settings.setReciter(id)
            val voice = manifest.value?.reciter(id) ?: return@launch
            val playback = player.state.value
            val surah = playback.surah ?: return@launch
            if (playback.reciterId == id) return@launch
            if (!library.isDownloaded(id, surah)) return@launch
            start(surah, playback.ayah ?: 1, voice)
        }
    }

    /** The picker's play triangle. A second tap on the same row, or picking anything, stops it. */
    fun previewReciter(id: String) {
        scope.launch {
            if (previewing.value == id) {
                stopPreview()
                return@launch
            }
            val bytes = runCatching { previewBytes(id) }.getOrNull() ?: return@launch
            clips.play(bytes)
            previewing.value = id
            previewTimeout?.cancel()
            previewTimeout = scope.launch {
                delay(PREVIEW_MILLIS)
                if (previewing.value == id) previewing.value = null
            }
        }
    }

    private var previewTimeout: Job? = null
    private var probe: Job? = null

    private fun stopPreview() {
        previewTimeout?.cancel()
        previewTimeout = null
        clips.stop()
        previewing.value = null
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
        return NowPlayingText(title = title, subtitle = if (arabicUi) voice.nameAr else voice.nameEn)
    }

    /** For the one caller that needs the setting outside a composition: the sheet's override. */
    suspend fun downloadsOnMobileData(): Boolean = settings.settings.first().downloadOnMobileData
}

/** Long enough for the fifteen-second clip plus a moment, after which the row stops claiming to
 * be playing. Neither platform's clip player reports the end back to common code. */
private const val PREVIEW_MILLIS = 17_000L
