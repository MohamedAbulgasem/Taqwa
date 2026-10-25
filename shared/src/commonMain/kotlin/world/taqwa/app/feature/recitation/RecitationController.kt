package world.taqwa.app.feature.recitation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.NowPlayingText
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.RecitationManifest
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.recitation.SpeechText
import world.taqwa.app.recitation.SpeechVoice
import world.taqwa.app.recitation.SpokenTranslation
import world.taqwa.app.recitation.SurahSkip
import world.taqwa.app.recitation.VoiceStatus
import kotlin.coroutines.cancellation.CancellationException

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
    /**
     * The bundled clip of an adhkar Quran page for a reciter (reciter id, surah, ayah or null for
     * a whole surah), or null when this build has none (adhkar spec §3.5, first run).
     */
    private val adhkarClipBytes: suspend (String, Int, Int?) -> ByteArray?,
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
    /** Read-aloud spec §5.4: the reading translation and the phone's voices. None by default. */
    private val readAloud: ReadAloudPort = NoReadAloud,
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

    /**
     * The adhkar passage the open sheet was opened for (adhkar spec §3.5), so a confirm plays
     * the passage and not the surah from its first ayah. Set wherever [sheetAyah] is.
     */
    private var sheetPassage: AdhkarPassage? = null

    /**
     * The surah of the adhkar passage loaded or loading (adhkar spec §3.5), or null for an
     * ordinary recitation or none. Its end is the end ([advance]); [start] clears it, so an
     * ordinary play after a passage goes on from surah to surah again.
     */
    private val adhkarSurah = MutableStateFlow<Int?>(null)

    /**
     * True while an adhkar page's bundled clip is playing (adhkar spec §3.5, first run): a session
     * of its own, which never touches the player and never goes on — see [playAdhkarClip].
     */
    private val adhkarClip = MutableStateFlow(false)

    /** True while the clip paused a recitation that its end, or the page turning, gives back. */
    private var pausedForClip = false

    /** Which clip is the current one, so the end of one the reader has replaced is not heard. */
    private var adhkarClipRun = 0

    /**
     * How many times a clip has been stopped or abandoned ([stopAdhkar], [abandonAdhkarClip]): a
     * clip still being read finds it changed.
     */
    private var adhkarStops = 0

    /**
     * How many times [stop] has been asked for. A passage's load reads it on both sides: a stop
     * that overtook the load — the page turned, the screen left, before the service had even
     * bound — arrived when there was nothing to stop, and is made again once there is.
     */
    private var stopCount = 0

    /**
     * The move to the next surah after one has played out (spec §16.1), while it is still in its
     * breath. Cancelled by anything the reader does that starts or stops a recitation, because a
     * tap during the breath is an answer to the question the breath was asking.
     */
    private var advancing: Job? = null

    /** Whether the UI is Arabic, for the lock screen's two lines. Set by the composition. */
    private var arabicUi = false

    /** The Android notification's two ayah buttons, localised by the composition (spec §15.1). */
    private var previousAyahLabel = ""
    private var nextAyahLabel = ""

    /**
     * The refusal already put in front of the reader for each download (spec §15.3): with
     * auto-download on there is no sheet to show a refusal on, so the sheet opens itself the
     * first time a fetch the reader is waiting on fails — once per failure, not on every emission.
     * Kept by value, because each failure the downloader reports is numbered afresh
     * (DownloadState.Failed.attempt): a new request refused the same way is a new failure.
     */
    private val surfacedFailures = mutableMapOf<DownloadKey, DownloadState.Failed>()

    /**
     * The reciters a whole-Quran batch has been seen moving for since it was last asked for. A
     * batch is over when a reciter seen moving has nothing left in the downloader; a reciter not
     * yet seen moving since the last ask is kept, so the moment between a request and the
     * downloader's answer (a Retry clears its surah first) is not taken for the batch ending.
     */
    private val batchSeen = mutableSetOf<String>()

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
        /** An adhkar page's passage, played as one when the surah lands (adhkar spec §3.5). */
        val passage: AdhkarPassage? = null,
    )

    /** Which ayahs of the surah an adhkar page plays, and what its lock screen says. */
    private data class AdhkarPassage(val ayahs: List<Int>, val text: NowPlayingText)

    /**
     * True while a preview is playing over a recitation this controller paused for it (spec
     * §14.5). The recitation is given back when the clip ends, when the picker closes, or when
     * a pick leaves the old voice playing — and not when the reader has already pressed play
     * themselves, which is the one case the flag has to be cleared without acting on.
     */
    private var pausedForPreview = false

    /** The UI language, for the reading translation's default; set by the composition. */
    private val languageTag = MutableStateFlow<String?>(null)

    /** The bundled translations, read once. */
    private val translationInfos = MutableStateFlow<List<TranslationInfo>>(emptyList())

    /** What the phone said for each language this process has asked about (read-aloud spec §4). */
    private val voices = MutableStateFlow<Map<String, VoiceStatus>>(emptyMap())

    /**
     * The questions still on their way to the phone, one per language (read-aloud spec §4). An
     * Android ask binds every speech engine in turn, and the reader's sheet, a return to the app
     * and a load can all want the same language within a moment of each other: a second ask joins
     * the one in flight instead of binding the engines again. Only touched on [scope]'s one thread.
     */
    private val asking = mutableMapOf<String, Deferred<VoiceStatus>>()

    /** The kind the loaded surah's translation is, for the bar's caption. */
    private var speechKind: TextKind? = null

    /** What the loaded surah was built with, so a rebuild only follows a real change. */
    private var appliedSpeech: SpeechKey? = null

    private data class SpeechKey(val surah: Int, val translationId: String?, val voice: SpeechVoice?)

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

    /**
     * The translation the reader shows, as the reader resolves it: none for Translation off, the
     * bundled default for an id that is not bundled, and nothing for the transliteration.
     */
    private val readingTranslation: StateFlow<TranslationInfo?> =
        combine(
            languageTag.filterNotNull().flatMapLatest { readAloud.translationId(it) },
            translationInfos,
        ) { id, infos ->
            if (id == ReadingSettings.NO_TRANSLATION) {
                null
            } else {
                (infos.firstOrNull { it.id == id } ?: infos.firstOrNull { it.id == FALLBACK_TRANSLATION })
                    ?.takeIf { it.kind != TextKind.TRANSLITERATION }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The switch, or null where it must not show (read-aloud spec §4). */
    private val readAloudState: StateFlow<ReadAloudState?> =
        combine(settings.settings.map { it.readAloud }.distinctUntilChanged(), readingTranslation, voices) { enabled, info, statuses ->
            if (info == null) return@combine null
            when (val status = statuses[info.language]) {
                is VoiceStatus.Ready -> ReadAloudState(enabled, info.name, info.language, info.kind)
                is VoiceStatus.Missing -> ReadAloudState(enabled, info.name, info.language, info.kind, missingEngine = status.engine)
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

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
        readAloudState,
    ) { catalogue, playing, surface, spoken ->
        assemble(catalogue, playing, surface, spoken)
    }.stateIn(scope, SharingStarted.Eagerly, RecitationState())

    /**
     * True while an adhkar page's passage is being heard (adhkar spec §3.5): the speaker in the
     * adhkar header becomes the equaliser. An ordinary recitation playing is not a passage, and
     * a passage paused — a call, the headphones pulled out — is not playing. A bundled clip is
     * playing for as long as it plays: it has no pause, and its end is the end.
     */
    val adhkarPlaying: StateFlow<Boolean> = combine(player.state, adhkarSurah, adhkarClip) { playback, surah, clip ->
        clip || (surah != null && playback.surah == surah && playback.playing)
    }.stateIn(scope, SharingStarted.Eagerly, false)

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

    private fun assemble(catalogue: Catalogue, playing: Playing, surface: Surface, spoken: ReadAloudState?): RecitationState {
        val bar = barOf(playing, catalogue)?.let { if (playing.playback.speaking) it.copy(readingAloud = speechKind) else it }
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
            readAloud = spoken,
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
        val incoming = waiting?.let { wait ->
            val who = catalogue.manifest?.reciter(wait.reciterId) ?: return@let null
            downloadFraction(playing.downloads[DownloadKey(wait.reciterId, wait.surah)])
                ?.let { IncomingDownload(wait.surah, who, it) }
        } ?: chosen?.takeIf { it.id != voice.id }?.let { next ->
            downloadFraction(playing.downloads[DownloadKey(next.id, surah)])
                ?.let { IncomingDownload(surah, next, it) }
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
            downloader.states.collect { states ->
                if (batching.value.isEmpty()) {
                    batchSeen.clear()
                    return@collect
                }
                val moving = states.filterValues { it !is DownloadState.Failed }.keys.map { it.reciterId }.toSet()
                val known = states.keys.map { it.reciterId }.toSet()
                batchSeen += moving
                batching.value = batching.value.filter { it !in batchSeen || it in known }.toSet()
                batchSeen.retainAll(batching.value)
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
        // A surah that has played out (spec §16.1): on to the next one, or an explicit stop. The
        // player is holding the ended surah for this answer and never has to lapse on its own.
        scope.launch {
            player.surahEnds.collect { ended -> advance(ended) }
        }
        // A refusal the reader would otherwise never see (spec §15.3): auto-download asks
        // nothing, so when the fetch behind a pending play fails - no Wi-Fi, no network, no room
        // - the sheet opens on the failure face, once, with the sentence and its Retry.
        scope.launch {
            downloader.states.collect { states ->
                surfacedFailures.keys.retainAll { states[it] is DownloadState.Failed }
                val waiting = pending ?: return@collect
                val key = DownloadKey(waiting.reciterId, waiting.surah)
                val failed = states[key] as? DownloadState.Failed ?: return@collect
                if (surfacedFailures[key] == failed) return@collect
                surfacedFailures[key] = failed
                if (sheetSurah.value == null) {
                    sheetAyah = waiting.ayah
                    sheetPassage = waiting.passage
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
                sheetPassage = null
                // An adhkar page's passage (adhkar spec §3.5) is played as the page asked for it,
                // whatever else is loaded: it is never a switch.
                waiting.passage?.let { passage ->
                    startPassage(waiting.surah, passage)
                    return@collect
                }
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
        scope.launch { translationInfos.value = runCatching { quran.translations() }.getOrDefault(emptyList()) }
        // Read-aloud changes while a surah is loaded (read-aloud spec §2): the switch, the
        // translation, or its voice arriving — rebuild around the ayah being heard.
        scope.launch {
            combine(settings.settings.map { it.readAloud }.distinctUntilChanged(), readingTranslation, voices) { on, info, statuses ->
                Triple(on, info?.id, info?.language?.let { statuses[it] })
            }.distinctUntilChanged().collect { applyReadAloud() }
        }
        // A new reading language while the switch is in use: ask the phone about it at once, so
        // the switch does not vanish for a language it can read.
        scope.launch {
            readingTranslation.filterNotNull().map { it.language }.distinctUntilChanged().collect { language ->
                if (voices.value.isNotEmpty() && language !in voices.value) checkVoice(language)
            }
        }
    }

    /** The two lines a lock screen shows. The ayah is deliberately not among them: it changes
     * every few seconds and a lock screen that flickers is worse than one that says less. */
    fun setArabicUi(arabic: Boolean) {
        arabicUi = arabic
    }

    /** The UI language, for the reading translation's default (read-aloud spec §5.4). */
    fun setLanguageTag(tag: String) {
        languageTag.value = tag
    }

    /**
     * The reading sheet or Settings › Recitation opened (read-aloud spec §4): ask the phone again.
     * These are the two screens that show the switch, and so the two places a voice installed or
     * deleted since the last ask must be noticed.
     */
    fun refreshVoices() {
        val info = readingTranslation.value ?: return
        scope.launch { checkVoice(info.language) }
    }

    /**
     * The reader or the Mushaf opened (read-aloud spec §4): ask only about a reading language the
     * phone has not answered for yet, so the sheet's switch is ready when it opens and the first
     * play does not wait on the question. The reader opens far more often than its sheet — every
     * surah, every change of mode — and an answer this process already holds is not worth binding
     * the engines for again; the sheet asks afresh whenever it opens.
     */
    fun onReaderOpened() {
        val info = readingTranslation.value ?: return
        if (info.language in voices.value) return
        scope.launch { checkVoice(info.language) }
    }

    /**
     * The app came back to the front (read-aloud spec §4). The one answer worth asking again is a
     * Missing voice: the reader may be back from the engine's installer, and the switch's caption
     * should not go on asking for a download that has just finished. Ready and Unsupported wait
     * for the sheet or Settings to open.
     */
    fun onForeground() {
        val info = readingTranslation.value ?: return
        if (voices.value[info.language] !is VoiceStatus.Missing) return
        scope.launch { checkVoice(info.language) }
    }

    /** Read-aloud's switch (read-aloud spec §6). */
    fun setReadAloud(on: Boolean) {
        scope.launch { settings.setReadAloud(on) }
    }

    /** "Get the voice": the engine's own installer, for the language the reader shows. */
    fun getVoice() {
        val info = readingTranslation.value ?: return
        val engine = (voices.value[info.language] as? VoiceStatus.Missing)?.engine ?: return
        readAloud.installVoice(engine)
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
     * An adhkar page's speaker (adhkar spec §3.5): [ayahs] of [surah] — Ayat al-Kursi, or a whole
     * short surah — in the chosen voice, with [title] and [subtitle] on the lock screen, never
     * read aloud, and stopping at its end rather than going on to the next surah. A surah not on
     * the phone is offered on the download sheet, and the confirm plays the passage when it
     * lands — unless Alafasy is the voice and the build carries his clip of it, which plays at
     * once ([playAdhkarClip]).
     *
     * A second tap while this surah's passage is still loading is a stop: the speaker shows Play
     * until the passage sounds, and another load would start it over audibly. Only while it loads
     * — a passage that is loaded and paused (a phone call) shows Play too, and that tap plays it.
     */
    fun requestPlayAyahs(surah: Int, ayahs: List<Int>, title: String, subtitle: String) {
        if (ayahs.isEmpty()) return
        if (loading.value && adhkarSurah.value == surah) {
            stopAdhkar()
            return
        }
        engage()
        playOrOffer(surah, ayahs.min(), AdhkarPassage(ayahs, NowPlayingText(title, subtitle)))
    }

    /**
     * The adhkar page turned, or the screen was left (adhkar spec §3.5). A passage playing stops,
     * and so does a bundled clip — handing back the recitation it paused; one still waiting on
     * its download is forgotten, its sheet with it, and the download goes on. Anything else — a
     * surah the reader was listening to before opening the adhkar — is left alone.
     */
    fun stopAdhkar() {
        adhkarStops++
        endAdhkarClip(cut = true)
        if (pending?.passage != null) pending = null
        if (sheetPassage != null) {
            sheetPassage = null
            sheetSurah.value = null
        }
        if (adhkarSurah.value != null) stop()
    }

    /**
     * [requestPlay] without the engagement mark, for [onHeaderTap], which has already made it.
     *
     * A surah the reader owns plays. One they do not is offered on the sheet — or, once they
     * have said "without asking" (spec §15.3), fetched at once and played the moment it lands,
     * the header's ring being the only thing that moves in between.
     *
     * An adhkar [passage] always asks: the adhkar screen has no ring to show a surah arriving,
     * and a speaker that answered a tap with a silent 58 MB fetch would look like a dead button.
     */
    private fun playOrOffer(surah: Int, ayah: Int, passage: AdhkarPassage? = null) {
        advancing?.cancel()
        scope.launch {
            if (surah in downloaded.value) {
                pending = null
                if (passage != null) startPassage(surah, passage) else start(surah, ayah)
                return@launch
            }
            // The first run (adhkar spec §3.5): not on the phone yet, but the build carries it.
            if (passage != null && playAdhkarClip(surah, passage.ayahs)) return@launch
            picker.value = false
            val voice = reciter.value
            if (passage == null && voice != null && settings.settings.first().autoDownload) {
                pending = PendingPlay(voice.id, surah, ayah)
                downloader.enqueue(DownloadKey(voice.id, surah), allowMobileOnce = false)
                return@launch
            }
            sheetAyah = ayah
            sheetPassage = passage
            sheetSurah.value = surah
        }
    }

    /**
     * The bar's next and previous, and the lock screen's (spec §15.1): the neighbouring surah
     * from its first ayah, through the same path as a tap on its header, so a surah not on the
     * phone is offered or fetched exactly as it would be there. At the ends of the Quran, nothing.
     *
     * During an adhkar page's passage (adhkar spec §3.5) a skip stops it and starts nothing: the
     * passage was all that was asked for, and a press on the lock screen is no request for
     * Al-Imran after Ayat al-Kursi.
     */
    fun nextSurah() {
        if (adhkarSurah.value != null) {
            stopAdhkar()
            return
        }
        val playing = state.value.bar?.surah ?: return
        if (playing < LAST_SURAH) requestPlay(playing + 1, 1)
    }

    fun previousSurah() {
        if (adhkarSurah.value != null) {
            stopAdhkar()
            return
        }
        val playing = state.value.bar?.surah ?: return
        if (playing > 1) requestPlay(playing - 1, 1)
    }

    /**
     * The surah [ended] has played out (spec §16.1), and the player is holding it. The next surah
     * follows by itself after a breath, in the chosen voice, through the same rules as a tap on
     * Next except that nothing is *asked*: a surah not on the phone is fetched only under "without
     * asking" (§15.3), and otherwise the recitation simply ends where a tap would have opened the
     * sheet — a sheet nobody asked for, over whatever screen they are on, is no answer to a phone
     * that has gone quiet. An-Nas ends it. Every branch that does not go on stops the player
     * explicitly, so the hold never has to run out.
     *
     * A move during the breath — a seek back into the surah from the lock screen, a tap on another
     * ayah — leaves the player playing something, and that wins: the breath then ends nothing.
     *
     * An adhkar page's passage (adhkar spec §3.5) never goes on: it was the whole of what was
     * asked for, and its end stops the player. An end reported for some other surah while a
     * passage is loaded is one the passage has already replaced, and is answered with nothing.
     */
    private fun advance(ended: Int) {
        val passage = adhkarSurah.value
        if (passage != null) {
            if (passage == ended) {
                advancing?.cancel()
                adhkarSurah.value = null
                player.stop()
            }
            return
        }
        advancing?.cancel()
        advancing = scope.launch {
            val next = ended + 1
            val voice = reciter.value
            if (next > LAST_SURAH || voice == null) {
                player.stop()
                return@launch
            }
            if (next in downloaded.value) {
                pending = null
                delay(SURAH_BREATH_MS)
                val live = player.state.value
                if (live.surah != ended || live.playing) return@launch
                start(next, 1)
                return@launch
            }
            if (settings.settings.first().autoDownload) {
                pending = PendingPlay(voice.id, next, 1)
                downloader.enqueue(DownloadKey(voice.id, next), allowMobileOnce = false)
            }
            player.stop()
        }
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
        // Another voice reciting this surah right now is what makes this a switch (spec §14.3) —
        // unless the sheet was opened for an adhkar passage, which is never a switch.
        val passage = sheetPassage
        pending = PendingPlay(
            voice.id,
            surah,
            sheetAyah,
            follow = passage == null && player.state.value.surah == surah,
            passage = passage,
        )
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
        // A confirmed passage waits in [pending], and a failure reopens the sheet from there. But
        // a sheet closed on a refusal is the reader having seen it and walked away: the play no
        // longer waits, so a later request that refuses the surah again (a whole Quran asked for
        // from Settings) does not bring the sheet back, and a copy that lands later does not start.
        val waiting = pending
        if (waiting != null && waiting.surah == sheetSurah.value &&
            downloader.states.value[DownloadKey(waiting.reciterId, waiting.surah)] is DownloadState.Failed
        ) {
            pending = null
        }
        sheetSurah.value = null
        sheetPassage = null
    }

    // ── The whole Quran (spec §5.4, §12.8) ──────────────────────────────────────────────

    /**
     * Every surah of the current reciter the reader does not already own, as one batch. The
     * downloader decides the order and the free-space refusal (Android also runs two at a time;
     * iOS's refusal counts what is already on its way); all this does is say which voice,
     * remember that a batch is running, and — as with a single surah — pass the sheet's one-time
     * mobile-data override straight through.
     */
    fun downloadWholeQuran(allowMobileOnce: Boolean = false) {
        engage()
        val voice = reciter.value ?: return
        // Asked for again: not seen moving since this ask, so the moment before the downloader
        // answers cannot end it.
        batchSeen -= voice.id
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
     *
     * It also holds read-aloud's card, which shows only once the phone has answered for the
     * reading language (read-aloud spec §4). Nothing else asks after a cold start, so without
     * this ask the card was missing from Settings until the reading sheet had been opened.
     */
    fun onSettingsOpened() {
        engage(refresh = true)
        refreshVoices()
    }

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

    /**
     * A tap [fraction] of the way along the bar's line (spec §17.5), from the reading edge. The
     * line is the surah's clock, so that is the clock the fraction is taken of; with no clock
     * yet there is no line to have tapped, and nothing happens.
     */
    fun seekToFraction(fraction: Float) {
        val total = player.state.value.surahDurationMs
        if (total <= 0L || fraction.isNaN()) return
        player.seekToSurahTime((total * fraction.coerceIn(0f, 1f).toDouble()).toLong())
    }

    fun stop() {
        advancing?.cancel()
        pending = null
        adhkarSurah.value = null
        stopCount++
        player.stop()
    }

    /** The bar's "×" and its downward swipe. Dismissing the bar is stopping — there is no
     * hidden playback with no way back to it. */
    fun dismissBar() = stop()

    /**
     * A new voice (spec §5.5, §14.3, §16.4). Persisted first, so the choice survives whatever
     * happens next; and it supersedes any switch still waiting on a download, because the reader
     * has just said which voice they want and it is this one.
     *
     * Then, by what is loaded — and a pick never starts or resumes a recitation on its own
     * (§16.4): a paused surah stays paused, in the new voice, and one that was playing goes on
     * playing in it.
     * - **nothing loaded** — the setting changes and that is all.
     * - **the same voice** — nothing. The one exception is a recitation the *preview* paused,
     *   which gets its resume back: the reader was listening before the audition.
     * - **another voice that has this surah** — swap at the current ayah, near enough the "next
     *   ayah boundary" the spec asks for; playing stays playing, paused stays paused.
     * - **another voice that does not** — offer the download, and **the old voice keeps playing**
     *   until it lands, when the new one takes over at the ayah being heard (the sheet says so:
     *   [DownloadSheetState.playingMeanwhile]); a paused one takes over paused.
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
            // What the reader was doing before any audition got in the way.
            val wasPlaying = playback.playing || hadPausedForPreview
            if (playback.reciterId == id) {
                if (hadPausedForPreview) player.play()
                return@launch
            }
            if (library.isDownloaded(id, surah)) {
                start(surah, playback.ayah ?: 1, voice)
                if (!wasPlaying) player.pause()
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
                // Asked again after a failure too: a copy refused for room (a whole Quran that fitted
                // only in part) is checked afresh, and a refusal again opens the sheet.
                val now = downloader.states.value[key]
                if (now == null || now is DownloadState.Failed) downloader.enqueue(key, allowMobileOnce = false)
                return@launch
            }
            sheetAyah = playback.ayah ?: 1
            sheetPassage = null
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
        // A whole surah, whatever was loaded before: its end goes on to the next one again.
        adhkarSurah.value = null
        abandonAdhkarClip()
        loading.value = true
        try {
            val speech = spokenFor(surah)
            speechKind = speech?.kind
            appliedSpeech = keyOf(surah, speech)
            player.load(playWith, surah, ayah, nowPlaying(playWith, surah), speech)
            player.play()
        } finally {
            loading.value = false
        }
        // A switch, translation or voice change that arrived while the load above was in flight
        // found `applyReadAloud` a no-op (see there) and is otherwise lost for the rest of this
        // surah; this call catches it up. The key guard makes it a no-op itself when nothing
        // changed, so a plain load is never followed by a redundant `setSpeech`.
        applyReadAloud()
    }

    /**
     * An adhkar page's passage (adhkar spec §3.5), loaded as a session of its own: only its
     * ayahs, under the page's own lock-screen text, never read aloud, and marked so that its end
     * stops the player ([advance]).
     */
    private suspend fun startPassage(surah: Int, passage: AdhkarPassage) {
        val playWith = reciter.value ?: return
        val stopsBefore = stopCount
        adhkarSurah.value = surah
        abandonAdhkarClip()
        loading.value = true
        try {
            player.load(playWith, surah, passage.ayahs.min(), passage.text, null, passage.ayahs)
        } finally {
            loading.value = false
        }
        // The speech bookkeeping is the loaded surah's, so it follows the passage only once the
        // passage is what is loaded: a container that could not be read leaves the recitation
        // before it read aloud as it was, and the bar saying so.
        if (player.state.value.surah == surah) {
            speechKind = null
            appliedSpeech = keyOf(surah, null)
        }
        if (stopCount != stopsBefore) {
            // The page turned or the screen was left while it loaded — on Android the first
            // play binds the service, long enough for a swipe — and that stop found nothing to
            // stop. Left playing, the passage would sound over the next page unmarked, and go
            // on into the next surah at its end.
            player.stop()
            return
        }
        if (player.state.value.surah != surah) {
            // The container could not be read, so nothing was loaded and whatever was before is
            // still there. That is not this passage: it keeps going on at its end, and it is not
            // resumed by a play meant for the passage.
            if (adhkarSurah.value == surah) adhkarSurah.value = null
            return
        }
        player.play()
    }

    /**
     * The first run (adhkar spec §3.5): an adhkar passage of a surah that is not on the phone,
     * played from the clip this build carries for it — Alafasy's, and only when Alafasy is the
     * voice the reader chose, so nobody hears a voice they did not pick. The clip is a whole ayah
     * or a whole short surah, which is why a passage of several ayahs must start at the first.
     *
     * A session of its own, and everything a passage's session is not: the clip never loads the
     * player, so it has no lock screen, no bar and no end to go on from — it plays out and that is
     * all ([adhkarClip]). A recitation that was playing is paused for it and given back at its
     * end, the way an audition does ([previewReciter]); one the reader had already paused stays
     * paused. Nothing it plays is counted.
     *
     * @return true when the request has been answered: the clip is playing, or the page turned
     * while it was being read and there is nothing to play and nothing to offer. False when there
     * is no clip — another voice, or one this build does not carry — and the request goes on to
     * the download sheet.
     */
    private suspend fun playAdhkarClip(surah: Int, ayahs: List<Int>): Boolean {
        val voice = reciter.value?.takeIf { it.id == RecitationManifest.DEFAULT_RECITER } ?: return false
        val ayah = when {
            ayahs.size == 1 -> ayahs.single()
            ayahs.min() == 1 -> null
            else -> return false
        }
        val stopsBefore = adhkarStops
        val bytes = try {
            adhkarClipBytes(voice.id, surah, ayah)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        // The page turned, the screen was left, or an ordinary play took the player while the file
        // was being read: that stop found no clip to stop. Playing now would sound over the next
        // page, unmarked, or pause the recitation the reader has just started.
        if (adhkarStops != stopsBefore) return true
        if (bytes == null) return false
        // A passage in the player gives way to the clip.
        if (adhkarSurah.value != null) stop()
        if (!adhkarClip.value && player.state.value.playing) {
            pausedForClip = true
            player.pause()
        }
        adhkarClip.value = true
        val run = ++adhkarClipRun
        clips.play(bytes) {
            scope.launch { if (adhkarClipRun == run) endAdhkarClip(cut = false) }
        }
        return true
    }

    /**
     * The clip is over: it played out ([cut] false) or the page turned and it was stopped. Gives
     * back the recitation the clip paused — once, and only if there is still one loaded to give
     * it back to: the reader may have dismissed the bar in the meantime. Nothing at all when no
     * clip is playing, so a stop for a passage in the player leaves an audition's clip alone.
     */
    private fun endAdhkarClip(cut: Boolean) {
        if (!adhkarClip.value) return
        adhkarClip.value = false
        if (cut) clips.stop()
        val paused = pausedForClip
        pausedForClip = false
        if (paused && player.state.value.surah != null) player.play()
    }

    /**
     * An ordinary play — [start], or a passage in the player — has taken the player, so a clip
     * still sounding is over and what it paused is not to be given back: the new play owns the
     * player now, in whatever state the reader leaves it, and a clip's end resuming the surah the
     * reader has since paused would be a press they never made. The way [toggle] ends an audition
     * with `resume = false`. A clip whose bytes are still being read is abandoned too, before it
     * sounds: [adhkarStops] moves first, which [playAdhkarClip] reads when the read returns, since
     * until then there is no clip playing to end.
     */
    private fun abandonAdhkarClip() {
        adhkarStops++
        if (!adhkarClip.value) return
        adhkarClip.value = false
        pausedForClip = false
        clips.stop()
    }

    /**
     * The surah's translation as the voice will read it (read-aloud spec §5.4), or null: the
     * switch off, Translation off, no voice on the phone, or nothing to read.
     *
     * Never throws anything but cancellation (spec: "a failed voice never stops the recitation"):
     * the settings read, [SpeechText.prepare] and the rest are wrapped so a surprise from any of
     * them is "nothing to read" rather than a crash out of [start] or the read-aloud collector.
     */
    private suspend fun spokenFor(surah: Int): SpokenTranslation? = try {
        if (!settings.settings.first().readAloud) return null
        val info = readingTranslation.value ?: return null
        val status = voices.value[info.language] ?: checkVoice(info.language)
        val voice = (status as? VoiceStatus.Ready)?.voice ?: return null
        val texts = runCatching { quran.translationTexts(info.id, surah) }.getOrNull() ?: return null
        val prepared = SpeechText.prepare(info.kind, info.language, surah, texts)
        if (prepared.isEmpty()) return null
        SpokenTranslation(info.id, info.kind, info.language, voice, prepared)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        null
    }

    private fun keyOf(surah: Int, speech: SpokenTranslation?) = SpeechKey(surah, speech?.translationId, speech?.voice)

    /** Rebuilds the loaded surah's speech when what it should be has changed. */
    private suspend fun applyReadAloud() {
        val surah = player.state.value.surah ?: return
        if (loading.value) return
        // An adhkar passage is never read aloud (adhkar spec §3.5), whatever the switch says.
        if (adhkarSurah.value != null) return
        val speech = spokenFor(surah)
        // Stale by the time the suspension above returns: the surah changed under it, or a fresh
        // `start()` (same surah or not) began and will call this itself when it finishes — either
        // way this call must not overwrite `appliedSpeech` for a build that raced it.
        if (loading.value || adhkarSurah.value != null || player.state.value.surah != surah) return
        val key = keyOf(surah, speech)
        if (key == appliedSpeech) return
        appliedSpeech = key
        speechKind = speech?.kind
        player.setSpeech(speech)
    }

    /**
     * Asks the phone about [language] and remembers the answer — or, when that same question is
     * already on its way, waits for its answer instead of asking twice (see [asking]). The ask
     * itself runs on [scope], not in the caller, so a caller that is cancelled (a load another one
     * replaced) leaves it to finish for whoever else is waiting on it.
     */
    private suspend fun checkVoice(language: String): VoiceStatus {
        asking[language]?.takeIf { !it.isCompleted }?.let { return it.await() }
        val ask = scope.async(start = CoroutineStart.LAZY) { askVoice(language) }
        asking[language] = ask
        ask.invokeOnCompletion { if (asking[language] === ask) asking.remove(language) }
        return ask.await()
    }

    /**
     * One question to the phone, and its answer remembered. A failure, or a query that takes too
     * long, is "cannot read it" (spec: "a failed voice never stops the recitation") — never a
     * crash and never a hang across the breath before the next ayah's translation.
     */
    private suspend fun askVoice(language: String): VoiceStatus {
        val status = try {
            withTimeoutOrNull(VOICE_QUERY_TIMEOUT_MS) { readAloud.status(language) } ?: VoiceStatus.Unsupported
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            VoiceStatus.Unsupported
        }
        voices.value = voices.value + (language to status)
        return status
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

/** What the reader loads when the stored translation id is not bundled. */
private const val FALLBACK_TRANSLATION = "en.sahih"

/** How long a voice query waits before treating the phone as unreadable (spec: "a failed voice
 * never stops the recitation"). */
private const val VOICE_QUERY_TIMEOUT_MS = 6_000L

/** The pause between a surah's last ayah and the first of the next (spec §16.1): a breath. */
internal const val SURAH_BREATH_MS = 1_000L
