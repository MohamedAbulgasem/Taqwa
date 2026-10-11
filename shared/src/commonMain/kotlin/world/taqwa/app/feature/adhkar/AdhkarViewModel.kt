package world.taqwa.app.feature.adhkar

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.adhkar.AdhkarItem
import world.taqwa.app.adhkar.AdhkarKind
import world.taqwa.app.adhkar.AdhkarPhase
import world.taqwa.app.adhkar.AdhkarProgress
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.adhkar.AdhkarText
import world.taqwa.app.adhkar.AdhkarWindows
import world.taqwa.app.adhkar.QuranPrefix
import world.taqwa.app.adhkar.pagesFor
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.today.formatClock
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.AdhkarStore
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.tasbeeh.Dhikr
import world.taqwa.app.tasbeeh.DhikrPart
import world.taqwa.app.tasbeeh.TapEvent
import world.taqwa.app.tasbeeh.TasbeehEngine
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehState
import kotlin.time.Instant

/**
 * A Quran page's text, read from `quran.db` by reference (spec §3.5): the catalogue carries none
 * of it.
 *
 * @property prefix what is recited before the passage — the istiʿadhah before Ayat al-Kursi (the
 * catalogue's own wording, drawn in the OS face), the basmala before each of the Quls (Al-Fatiha's
 * first ayah, drawn in the Hafs face). Which of the two it is, is the item's [QuranPrefix].
 * @property lines each ayah's Uthmani text and its number, for the roundel.
 * @property translation the reader's chosen translation of the passage, its ayahs joined with a
 * space; null when the reader reads without one.
 * @property translationLanguage [translation]'s own language, which decides its direction — not the
 * interface's: an English interface may read the Urdu translation.
 */
data class QuranPageText(
    val prefix: String?,
    val lines: List<Pair<String, Int?>>,
    val translation: String?,
    val translationLanguage: String = "en",
)

/**
 * One page of the pager: the item and everything the page draws, already chosen for the set and
 * the language. [count] is clamped to [target] — a stored count above it reads as done, never as
 * "5 of 3" ([AdhkarProgress.decode] does not clamp).
 *
 * [arabic] is null for a Quran page, whose text is [quran]'s; [translation] is the du'a's own in
 * the interface language, null where the build has no file for it.
 */
data class AdhkarPageUi(
    val item: AdhkarItem,
    val arabic: String?,
    val transliteration: String?,
    val translation: String?,
    val quran: QuranPageText?,
    val count: Int,
    val target: Int,
    val done: Boolean,
)

/**
 * Everything the adhkar screen draws.
 *
 * @property until when [phase] ends, already a clock time in the location's zone and the clock
 * setting's form ("6:12", "6:12 pm"); null outside the set's window.
 * @property currentPage the page on screen; `pages.size` is the done page after the last one. A
 * set opened with every page counted opens there (spec §3.1).
 * @property completed the pages counted to their target — counted, never visited (spec §3.3).
 * @property loaded false until a location and the set's pages are in; the screen then shows its
 * title and nothing else.
 */
data class AdhkarUiState(
    val set: AdhkarSet = AdhkarSet.MORNING,
    val phase: AdhkarPhase = AdhkarPhase.NONE,
    val until: String? = null,
    val pages: List<AdhkarPageUi> = emptyList(),
    val currentPage: Int = 0,
    val completed: Int = 0,
    val showTranslation: Boolean = true,
    val arabicSizeSp: Int = ReadingSettings().arabicSizeSp,
    val transliteration: Boolean = false,
    val loaded: Boolean = false,
) {
    /**
     * The reading sheet's preview line (spec §3.6): the first [PREVIEW_WORDS] words of the du'a on
     * screen, all of it when it is shorter. A Quran page and the done page have no du'a of their
     * own — the Quran is drawn in another face, and the preview is the du'a face's — so the set's
     * first du'a stands in. Empty until the pages are in.
     */
    val readingPreview: String
        get() {
            val arabic = pages.getOrNull(currentPage)?.arabic ?: pages.firstNotNullOfOrNull { it.arabic } ?: return ""
            return arabic.split(' ').filter { it.isNotEmpty() }.take(PREVIEW_WORDS).joinToString(" ")
        }

    private companion object {
        const val PREVIEW_WORDS = 6
    }
}

/**
 * The adhkar session (spec §3): which set, which page, and what a tap does.
 *
 * **One day, decided once.** Each load asks [AdhkarWindows.dayFor] for the day the adhkar are read
 * against and hands that same [DayPrayerTimes] to [AdhkarWindows.setFor], [AdhkarWindows.windowFor]
 * and [AdhkarWindows.dateKey]. Before Fajr that is yesterday's: the evening set opened at 00:30 is
 * last evening's, keyed by last evening's Asr, still in its make-up window.
 *
 * **Every tap is written at once** (spec §3.3), not debounced as the tasbeeh's is: a set is a few
 * dozen taps spread over minutes, not a hundred in a burst, and a page counted is a page that must
 * still be counted when the app comes back. Each write is a snapshot taken at the tap, started
 * before the tap returns ([CoroutineStart.UNDISPATCHED]) and finished even if the screen leaves
 * ([NonCancellable]), one at a time in tap order behind [writes] — so a later tap's snapshot can
 * never be overtaken by an earlier one, and a load that reads the store waits for the writes ahead
 * of it.
 *
 * **The daily istighfar** is one counter for both sets ([AdhkarStore.istighfar]), kept by the same
 * day as the sets: [AdhkarWindows.dayFor]'s day, the local date of its Fajr. Between midnight and
 * Fajr the evening set and its istighfar page are last evening's together; keyed by the calendar,
 * "14 of 15" at 00:30 would open on an istighfar page back at 0. Its slot in a set's own list is
 * written as 0: the count lives in the one place, and a set read without it would otherwise carry
 * a stale copy.
 *
 * **Haptics.** A count is the light tick; a page said in full is a part of the session, the double
 * pulse; the last undone page completing is the session done, the firm one, once.
 *
 * Failures are swallowed rather than allowed to take the process down — a full disk loses a count,
 * not the app — as [world.taqwa.app.feature.tasbeeh.TasbeehViewModel] does. [scope] is the screen's;
 * leaving the screen cancels everything but a write already under way.
 */
class AdhkarViewModel(
    private val catalogue: suspend () -> List<AdhkarItem>,
    private val textFor: suspend (String) -> AdhkarText?,
    private val quran: QuranSource,
    private val store: AdhkarStore,
    private val settings: SettingsRepository,
    private val engine: PrayerTimesEngine,
    private val haptics: Haptics,
    private val now: () -> Instant,
    private val format: PlatformFormat,
    private val language: String,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AdhkarUiState())
    val state: StateFlow<AdhkarUiState> = _state.asStateFlow()

    /**
     * A child of [scope] whose coroutines fail alone and quietly: a failed write must not cancel
     * the settings watch, and nothing here is worth a crash.
     */
    private val work = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + CoroutineExceptionHandler { _, _ -> },
    )

    /**
     * What the pages on screen were loaded against. Null while a load is under way and when there
     * is no location: a tap then does nothing, so a tap can never be counted against a set that is
     * about to be replaced by what the store held a moment before it.
     */
    private class Session(
        val set: AdhkarSet,
        val location: GeoLocation,
        val zone: TimeZone,
        val prayerSettings: PrayerSettings,
        val dateKey: LocalDate,
        val istighfarDate: LocalDate,
    )

    private var session: Session? = null
    private var openJob: Job? = null
    private var advanceJob: Job? = null
    private var watchJob: Job? = null
    private val writes = Mutex()

    // Read once per screen: the catalogue and the language's translations do not change under it.
    private var items: List<AdhkarItem>? = null
    private var text: AdhkarText? = null
    private var textRead = false
    private var basmala: String? = null

    /** Opens [set], or the set the half day is in when it is null (spec §2.3). */
    fun open(set: AdhkarSet?) {
        watchReading()
        advanceJob?.cancel()
        openJob?.cancel()
        session = null
        // A set asked for by name is named in the header from the first frame, not after the load.
        if (set != null && !_state.value.loaded) _state.update { it.copy(set = set) }
        openJob = work.launch { load(set) }
    }

    /** Stops everything but a write already under way; the route calls it when it drops this one. */
    fun close() {
        work.cancel()
    }

    /** The segmented control. Switching never touches progress; each set keeps its own. */
    fun selectSet(set: AdhkarSet) {
        val current = _state.value
        if (current.loaded && current.set == set && session != null) return
        open(set)
    }

    /** The pager settled on [page]. Visiting is not counting: nothing is marked done by this. */
    fun goTo(page: Int) {
        _state.update { s -> if (!s.loaded) s else s.copy(currentPage = page.coerceIn(0, s.pages.size)) }
    }

    /**
     * Counts the page on screen. A page is a one-part [TasbeehPreset]; [TasbeehEngine.tap] decides
     * what the tap meant, and a completed page waits [ADVANCE_DELAY_MS] and moves to the next undone
     * one. A completed page, the done page and a screen still loading do nothing.
     */
    fun tap() {
        val session = session ?: return
        val current = _state.value
        if (!current.loaded || current.set != session.set) return
        val index = current.currentPage
        val page = current.pages.getOrNull(index) ?: return
        if (page.done) return
        val id = page.item.id
        val preset = TasbeehPreset(id, listOf(DhikrPart(Dhikr(id, page.arabic ?: ""), page.target)))
        val (next, event) = TasbeehEngine.tap(TasbeehState(id, page.count, 1), preset)
        val counted = page.copy(count = next.count, done = next.count >= page.target)
        val pages = current.pages.toMutableList().also { it[index] = counted }
        when {
            event != TapEvent.SetComplete -> haptics.count()
            pages.all { it.done } -> haptics.setComplete()
            else -> haptics.partComplete()
        }
        _state.value = current.copy(pages = pages, completed = pages.count { it.done })
        persist(session, counted, pages)
        if (counted.done) advanceFrom(index, session)
    }

    /** The Adhkar translation switch (spec §3.6); the state follows the store. */
    fun setTranslation(on: Boolean) {
        work.launch { store.setTranslation(on) }
    }

    /**
     * Once a minute while the screen is in front: the phase and its time move with the clock. The
     * set on screen stays the one that was opened. When the day the set is kept under has moved on
     * — the screen left open across Fajr, the one boundary the sets and the istighfar share;
     * midnight is not one — the set is opened afresh, since the counts on screen belong to a date
     * that is over.
     */
    fun tick() {
        val session = session ?: return
        val instant = now()
        val day = dayAt(instant, session.location, session.zone, session.prayerSettings)
        val dateKey = AdhkarWindows.dateKey(session.set, day, session.zone)
        val istighfarDate = istighfarDateOf(day, session.zone)
        if (dateKey != session.dateKey || istighfarDate != session.istighfarDate) {
            open(session.set)
            return
        }
        val window = AdhkarWindows.windowFor(session.set, instant, day)
        _state.update { it.copy(phase = window.phase, until = window.until?.let { end -> formatClock(end, session.zone, format) }) }
    }

    private suspend fun load(requested: AdhkarSet?) {
        val location = settings.location.first()
        if (location == null) {
            _state.update { it.copy(pages = emptyList(), currentPage = 0, completed = 0, loaded = false, phase = AdhkarPhase.NONE, until = null) }
            return
        }
        val prayerSettings = settings.prayerSettings.first()
        val zone = TimeZone.of(location.timeZoneId)
        val instant = now()
        val day = dayAt(instant, location, zone, prayerSettings)
        val set = requested ?: AdhkarWindows.setFor(instant, day)
        // Named before the heavier reads, so an evening open never shows "Morning adhkar" first.
        // Only on a first open: a set switch keeps the old set's name with the old set's pages
        // until the new ones are in, rather than naming one set over the other's pages.
        if (!_state.value.loaded) _state.update { it.copy(set = set) }
        val window = AdhkarWindows.windowFor(set, instant, day)
        val dateKey = AdhkarWindows.dateKey(set, day, zone)
        val istighfarDate = istighfarDateOf(day, zone)

        val pages = (items ?: catalogue().also { items = it }).pagesFor(set)
        val text = translations()
        val reading = settings.readingSettings(format.languageTag()).first()
        val quranTranslation = readerTranslation(reading.translationId)
        // Behind the writes, so a count still on its way to disk is read back as counted.
        val (progress, istighfar) = writes.withLock {
            AdhkarProgress.forDate(store.progress(set).first(), dateKey, pages.size) to
                AdhkarProgress.forDate(store.istighfar().first(), istighfarDate, 1).counts.single()
        }
        val ui = pages.mapIndexed { index, item ->
            val stored = if (item.countDaily) istighfar else progress.counts[index]
            val count = stored.coerceAtMost(item.count)
            AdhkarPageUi(
                item = item,
                arabic = if (item.kind == AdhkarKind.QURAN) null else item.arabicFor(set),
                transliteration = item.transliterationFor(set),
                translation = text?.translationFor(item.id, set),
                quran = if (item.kind == AdhkarKind.QURAN) quranText(item, set, quranTranslation) else null,
                count = count,
                target = item.count,
                done = count >= item.count,
            )
        }
        session = Session(set, location, zone, prayerSettings, dateKey, istighfarDate)
        _state.update {
            it.copy(
                set = set,
                phase = window.phase,
                until = window.until?.let { end -> formatClock(end, zone, format) },
                pages = ui,
                currentPage = ui.indexOfFirst { page -> !page.done }.takeIf { first -> first >= 0 } ?: ui.size,
                completed = ui.count { page -> page.done },
                loaded = true,
            )
        }
    }

    /**
     * The date the shared istighfar is kept under: the day's own, the local date of its Fajr — the
     * same day the sets are read against, so the two never disagree between midnight and Fajr.
     */
    private fun istighfarDateOf(day: DayPrayerTimes, zone: TimeZone): LocalDate =
        day.time(Prayer.FAJR).toLocalDateTime(zone).date

    /** The binding rule: one [AdhkarWindows.dayFor] answer per question, never "today's" before Fajr. */
    private fun dayAt(instant: Instant, location: GeoLocation, zone: TimeZone, prayerSettings: PrayerSettings): DayPrayerTimes =
        AdhkarWindows.dayFor(instant, zone) { date -> engine.timesFor(location, date, prayerSettings) }

    /**
     * The interface language's translations, or none: a language whose file is not in the build
     * shows the Arabic (and, under a Latin interface, the transliteration) rather than another
     * language's words (spec §4.2).
     */
    private suspend fun translations(): AdhkarText? {
        if (!textRead) {
            text = textFor(language)
            textRead = true
        }
        return text
    }

    /**
     * The translation the reader reads the Quran in, resolved as the reader resolves it: none when
     * it is switched off, Saheeh International when the stored id is one this build no longer
     * carries.
     */
    private suspend fun readerTranslation(id: String): TranslationInfo? {
        if (id == ReadingSettings.NO_TRANSLATION) return null
        val all = quran.translations()
        return all.firstOrNull { it.id == id } ?: all.firstOrNull { it.id == FALLBACK_TRANSLATION }
    }

    private suspend fun quranText(item: AdhkarItem, set: AdhkarSet, translation: TranslationInfo?): QuranPageText? {
        val ref = item.quran ?: return null
        val ayah = ref.ayah
        val lines: List<Pair<String, Int?>> = if (ayah != null) {
            listOfNotNull(quran.ayahText(ref.surah, ayah)?.let { it to ayah })
        } else {
            quran.ayahs(ref.surah).map { it.text to it.number }
        }
        val prefix = when (ref.prefix) {
            QuranPrefix.ISTIADHAH -> item.arabicFor(set)
            QuranPrefix.BASMALA -> basmala ?: quran.ayahText(1, 1).also { basmala = it }
            null -> null
        }
        val translated = translation?.let { info ->
            if (ayah != null) {
                quran.translationText(info.id, ref.surah, ayah)
            } else {
                quran.translationTexts(info.id, ref.surah).entries.sortedBy { it.key }
                    .joinToString(" ") { it.value }.ifEmpty { null }
            }
        }
        return QuranPageText(prefix, lines, translated, translation?.language ?: "en")
    }

    /**
     * The reading settings the page is drawn with, followed for as long as the screen lives: the
     * size and the transliteration are the reader's own (one knob for the whole app), the
     * translation switch is the adhkar's.
     */
    private fun watchReading() {
        if (watchJob != null) return
        watchJob = work.launch {
            combine(settings.readingSettings(format.languageTag()), store.translation) { reading, translation ->
                Triple(reading.arabicSizeSp, reading.transliteration, translation)
            }.distinctUntilChanged().collect { (size, transliteration, translation) ->
                _state.update { it.copy(arabicSizeSp = size, transliteration = transliteration, showTranslation = translation) }
            }
        }
    }

    /** Writes the page just counted: the daily counter for the istighfar, the set's list for the rest. */
    private fun persist(session: Session, counted: AdhkarPageUi, pages: List<AdhkarPageUi>) {
        val snapshot = if (counted.item.countDaily) {
            null
        } else {
            AdhkarProgress(session.dateKey, pages.map { if (it.item.countDaily) 0 else it.count })
        }
        work.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                writes.withLock {
                    if (snapshot == null) {
                        store.saveIstighfar(session.istighfarDate, counted.count)
                    } else {
                        store.save(session.set, snapshot)
                    }
                }
            }
        }
    }

    /**
     * After [ADVANCE_DELAY_MS], from the completed page at [index] to the next undone page — after
     * it first, then any left behind before it — or to the done page. Only if the reader is still on
     * that page of that load: a swipe or a set switch inside the hold is the reader's own choice.
     */
    private fun advanceFrom(index: Int, session: Session) {
        advanceJob?.cancel()
        advanceJob = work.launch {
            delay(ADVANCE_DELAY_MS)
            _state.update { s ->
                if (this@AdhkarViewModel.session !== session || s.currentPage != index) {
                    s
                } else {
                    val after = (index + 1 until s.pages.size).firstOrNull { !s.pages[it].done }
                    val before = (0 until index).firstOrNull { !s.pages[it].done }
                    s.copy(currentPage = after ?: before ?: s.pages.size)
                }
            }
        }
    }

    companion object {
        /** Spec §3.3: the completed page holds about this long before the pager moves on. */
        const val ADVANCE_DELAY_MS = 400L

        /** What the reader shows when the stored translation is not in this build. */
        private const val FALLBACK_TRANSLATION = "en.sahih"
    }
}
