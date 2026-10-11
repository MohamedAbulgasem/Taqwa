package world.taqwa.app.feature.adhkar

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath
import world.taqwa.app.adhkar.AdhkarCatalogue
import world.taqwa.app.adhkar.AdhkarItem
import world.taqwa.app.adhkar.AdhkarKind
import world.taqwa.app.adhkar.AdhkarPhase
import world.taqwa.app.adhkar.AdhkarProgress
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.adhkar.AdhkarText
import world.taqwa.app.adhkar.EN_JSON
import world.taqwa.app.adhkar.ITEMS_JSON
import world.taqwa.app.adhkar.pagesFor
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.feature.quran.FakeQuranSource
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.quran.Ayah
import world.taqwa.app.settings.AdhkarStore
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The store with nothing behind it but three flows: what the view model writes is what it reads. */
private class MemoryAdhkarStore : AdhkarStore {
    val progress = mapOf(
        AdhkarSet.MORNING to MutableStateFlow<String?>(null),
        AdhkarSet.EVENING to MutableStateFlow<String?>(null),
    )
    val istighfar = MutableStateFlow<String?>(null)
    private val translationOn = MutableStateFlow(true)

    override fun progress(set: AdhkarSet): Flow<String?> = progress.getValue(set)

    override suspend fun save(set: AdhkarSet, progress: AdhkarProgress) {
        this.progress.getValue(set).value = progress.encode()
    }

    override fun istighfar(): Flow<String?> = istighfar

    override suspend fun saveIstighfar(date: LocalDate, count: Int) {
        istighfar.value = AdhkarProgress(date, listOf(count)).encode()
    }

    override val translation: Flow<Boolean> = translationOn

    override suspend fun setTranslation(on: Boolean) {
        translationOn.value = on
    }
}

/** Haptics that only remember which of them fired, in order. */
private class RecordingHaptics : Haptics {
    val fired = mutableListOf<String>()
    override fun tick() { fired += "tick" }
    override fun count() { fired += "count" }
    override fun partComplete() { fired += "partComplete" }
    override fun setComplete() { fired += "setComplete" }
}

/**
 * The view model over the real catalogue (the bundled JSON, embedded as [ITEMS_JSON]), the English
 * translations, a Quran source with fixed text, a memory store, a real settings store and the real
 * prayer-time engine for Tripoli. The clock is a field: each test puts it where it needs it, and the
 * expectations are read from the same engine the view model asks, so nothing here hard-codes a time.
 */
class AdhkarViewModelTest {

    private val tripoli = GeoLocation(32.8872, 13.1913, "Africa/Tripoli", "Tripoli", "LY")
    private val zone = TimeZone.of("Africa/Tripoli")
    private val date = LocalDate(2026, 10, 11)
    private val items: List<AdhkarItem> = AdhkarCatalogue.parse(ITEMS_JSON)
    private val morningPages = items.pagesFor(AdhkarSet.MORNING)
    private val eveningPages = items.pagesFor(AdhkarSet.EVENING)

    private val basmala = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"
    private val kursi = "ٱللَّهُ لَآ إِلَـٰهَ إِلَّا هُوَ"
    private val quran = FakeQuranSource(
        ayahsBySurah = mapOf(
            1 to listOf(Ayah(1, 1, basmala, 1, 1, 1, 0)),
            2 to listOf(Ayah(2, 255, kursi, 42, 3, 9, 0)),
            112 to (1..4).map { Ayah(112, it, "ikhlas-$it", 604, 30, 240, 0) },
            113 to (1..5).map { Ayah(113, it, "falaq-$it", 604, 30, 240, 0) },
            114 to (1..6).map { Ayah(114, it, "nas-$it", 604, 30, 240, 0) },
        ),
        translationTextsById = mapOf(
            "en.sahih" to mapOf(
                2 to mapOf(255 to "Allah - there is no deity except Him"),
                112 to (1..4).associateWith { "ikhlas translation $it" },
                113 to (1..5).associateWith { "falaq translation $it" },
                114 to (1..6).associateWith { "nas translation $it" },
            ),
        ),
    )

    /**
     * [reopen] is the screen opened again: a new view model over the same store, settings, clock
     * and haptics, as the route builds one each time the screen is pushed.
     */
    private class Rig(
        val viewModel: AdhkarViewModel,
        val store: MemoryAdhkarStore,
        val haptics: RecordingHaptics,
        val day: DayPrayerTimes,
        val clock: Clock,
        val reopen: () -> AdhkarViewModel,
    )

    private class Clock(var now: Instant)

    /**
     * One file per test, removed first, with absolute paths and the store's writes on the test's
     * own scheduler — TodayViewModelTest's rules, for TodayViewModelTest's reasons.
     */
    private suspend fun TestScope.rig(
        name: String,
        at: (DayPrayerTimes) -> Instant,
        location: GeoLocation? = tripoli,
        catalogue: suspend () -> List<AdhkarItem> = { items },
    ): Rig {
        val path = "/tmp/taqwa-test-adhkar-$name.preferences_pb".toPath()
        FileSystem.SYSTEM.delete(path, mustExist = false)
        val settings = SettingsRepository(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { path })
        if (location != null) settings.setLocation(location)
        val prayerSettings = settings.prayerSettings.first()
        val engine = PrayerTimesEngine()
        val day = engine.timesFor(tripoli, date, prayerSettings)
        val clock = Clock(at(day))
        val store = MemoryAdhkarStore()
        val haptics = RecordingHaptics()
        val build = {
            AdhkarViewModel(
                catalogue = catalogue,
                textFor = { language: String -> if (language == "en") AdhkarCatalogue.parseText(EN_JSON) else null },
                quran = quran,
                store = store,
                settings = settings,
                engine = engine,
                haptics = haptics,
                now = { clock.now },
                format = EnglishPlatformFormat,
                language = "en",
                scope = backgroundScope,
            )
        }
        return Rig(build(), store, haptics, day, clock, build)
    }

    /**
     * Waits for a load of [set] to land. The view model works on `backgroundScope`, which
     * `advanceUntilIdle` does not wait for; suspending on the state it produces does, and lets any
     * real (non-virtual) DataStore read finish, as TodayViewModelTest explains.
     */
    private suspend fun TestScope.loaded(rig: Rig, set: AdhkarSet): AdhkarUiState = loaded(rig.viewModel, set)

    private suspend fun TestScope.loaded(viewModel: AdhkarViewModel, set: AdhkarSet): AdhkarUiState {
        runCurrent()
        return viewModel.state.first { it.loaded && it.set == set }
    }

    private fun tenPastFajr(day: DayPrayerTimes): Instant = day.time(Prayer.FAJR) + 10.minutes

    private fun clock(instant: Instant): String {
        val local = instant.toLocalDateTime(zone)
        return EnglishPlatformFormat.clockTime(local.hour, local.minute)
    }

    private fun counts(set: AdhkarSet, vararg leading: Int): AdhkarProgress {
        val size = (if (set == AdhkarSet.MORNING) morningPages else eveningPages).size
        return AdhkarProgress(date, leading.toList() + List(size - leading.size) { 0 })
    }

    @Test fun `opening with no set picks the half day`() = runTest {
        val rig = rig("half-day", ::tenPastFajr)
        rig.viewModel.open(null)
        val state = loaded(rig, AdhkarSet.MORNING)
        assertTrue(state.loaded)
        assertEquals(AdhkarSet.MORNING, state.set)
        assertEquals(morningPages.map { it.id }, state.pages.map { it.item.id })
        assertEquals(0, state.currentPage)
        assertEquals(0, state.completed)

        rig.clock.now = rig.day.time(Prayer.ASR) + 10.minutes
        rig.viewModel.open(null)
        loaded(rig, AdhkarSet.EVENING)
        assertEquals(AdhkarSet.EVENING, rig.viewModel.state.value.set)
        assertEquals(eveningPages.map { it.id }, rig.viewModel.state.value.pages.map { it.item.id })
    }

    @Test fun `an evening open names the evening before its pages are in`() = runTest {
        val pagesHeld = CompletableDeferred<Unit>()
        val rig = rig("named-first", { day -> day.time(Prayer.ASR) + 10.minutes }, catalogue = { pagesHeld.await(); items })
        rig.viewModel.open(null)
        runCurrent()
        // The catalogue has not been read yet, but the header already says which set this is.
        assertFalse(rig.viewModel.state.value.loaded)
        assertEquals(AdhkarSet.EVENING, rig.viewModel.state.value.set)
        pagesHeld.complete(Unit)
        assertEquals(eveningPages.size, loaded(rig, AdhkarSet.EVENING).pages.size)
    }

    @Test fun `opens on the first undone page`() = runTest {
        val rig = rig("first-undone", ::tenPastFajr)
        rig.store.save(AdhkarSet.MORNING, counts(AdhkarSet.MORNING, 1, 3))
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        val state = rig.viewModel.state.value
        // Ayat al-Kursi (once) and Al-Ikhlas (three times) are both said in full.
        assertEquals(2, state.currentPage)
        assertEquals(2, state.completed)
        assertTrue(state.pages[1].done)
        assertEquals(3, state.pages[1].count)
        assertFalse(state.pages[2].done)
    }

    @Test fun `a tap counts and the last tap completes the page and advances`() = runTest {
        val rig = rig("tap", ::tenPastFajr)
        rig.store.save(AdhkarSet.MORNING, counts(AdhkarSet.MORNING, 1))
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        assertEquals(1, rig.viewModel.state.value.currentPage)

        repeat(3) { rig.viewModel.tap() }
        runCurrent()
        // A page is a part of the session, not the whole of it: the light tick per count, the
        // double pulse when the page is said in full.
        assertEquals(listOf("count", "count", "partComplete"), rig.haptics.fired)
        val counted = rig.viewModel.state.value
        assertEquals(3, counted.pages[1].count)
        assertTrue(counted.pages[1].done)
        assertEquals(2, counted.completed)
        // Every tap is on disk at once, not after a debounce.
        assertEquals(counts(AdhkarSet.MORNING, 1, 3).encode(), rig.store.progress.getValue(AdhkarSet.MORNING).value)

        // The completed page holds for a moment before the pager moves on.
        advanceTimeBy(399)
        runCurrent()
        assertEquals(1, rig.viewModel.state.value.currentPage)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, rig.viewModel.state.value.currentPage)
    }

    @Test fun `the last undone page fires the set haptic once and lands on the done page`() = runTest {
        val rig = rig("last-page", ::tenPastFajr)
        // Every page said but Al-Ikhlas (three times), the session's last undone page.
        val full = morningPages.mapIndexed { index, item -> if (index == 1 || item.countDaily) 0 else item.count }
        rig.store.save(AdhkarSet.MORNING, AdhkarProgress(date, full))
        rig.store.saveIstighfar(date, 100)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        assertEquals(1, rig.viewModel.state.value.currentPage)

        repeat(3) { rig.viewModel.tap() }
        runCurrent()
        assertEquals(listOf("count", "count", "setComplete"), rig.haptics.fired)
        assertEquals(morningPages.size, rig.viewModel.state.value.completed)
        advanceTimeBy(401)
        runCurrent()
        assertEquals(morningPages.size, rig.viewModel.state.value.currentPage)
        // The done page takes the tap and nothing fires again.
        rig.viewModel.tap()
        runCurrent()
        assertEquals(1, rig.haptics.fired.count { it == "setComplete" })
    }

    @Test fun `completing the last page fires the firm haptic once and lands on the done page`() = runTest {
        val rig = rig("whole-set", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        // The whole set read in order on a fresh day, each page counted to its target and the
        // pager left to move on by itself.
        morningPages.forEachIndexed { index, item ->
            assertEquals(index, rig.viewModel.state.value.currentPage)
            repeat(item.count) { rig.viewModel.tap() }
            runCurrent()
            advanceTimeBy(AdhkarViewModel.ADVANCE_DELAY_MS + 1)
            runCurrent()
        }
        val state = rig.viewModel.state.value
        assertEquals(morningPages.size, state.currentPage)
        assertEquals(morningPages.size, state.completed)
        // A page said in full is the double pulse; the last page is the set done, the firm one.
        val expected = morningPages.flatMapIndexed { index, item ->
            List(item.count - 1) { "count" } + (if (index == morningPages.lastIndex) "setComplete" else "partComplete")
        }
        assertEquals(expected, rig.haptics.fired)
        assertEquals(1, rig.haptics.fired.count { it == "setComplete" })
    }

    @Test fun `reopening a finished set opens on the done page`() = runTest {
        val rig = rig("reopen-done", ::tenPastFajr)
        // Every page said but Al-Ikhlas; the reader says it, and leaves.
        val full = morningPages.mapIndexed { index, item -> if (index == 1 || item.countDaily) 0 else item.count }
        rig.store.save(AdhkarSet.MORNING, AdhkarProgress(date, full))
        rig.store.saveIstighfar(date, 100)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        repeat(3) { rig.viewModel.tap() }
        runCurrent()
        rig.viewModel.close()

        val again = rig.reopen()
        again.open(AdhkarSet.MORNING)
        val state = loaded(again, AdhkarSet.MORNING)
        assertEquals(morningPages.size, state.currentPage)
        assertEquals(morningPages.size, state.completed)
        assertTrue(state.pages.all { it.done })
        // Opening on the done page is not finishing the set again: the firm haptic was the first
        // visit's alone.
        assertEquals(1, rig.haptics.fired.count { it == "setComplete" })
    }

    @Test fun `leaving mid-way and coming back resumes on the same page and count`() = runTest {
        val rig = rig("resume", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        // Ayat al-Kursi said once; then Al-Ikhlas twice of its three, and the reader leaves.
        rig.viewModel.tap()
        runCurrent()
        advanceTimeBy(AdhkarViewModel.ADVANCE_DELAY_MS + 1)
        runCurrent()
        repeat(2) { rig.viewModel.tap() }
        runCurrent()
        rig.viewModel.close()

        val again = rig.reopen()
        again.open(AdhkarSet.MORNING)
        val state = loaded(again, AdhkarSet.MORNING)
        assertEquals(1, state.currentPage)
        assertEquals(2, state.pages[1].count)
        assertEquals(1, state.completed)
        // The third time is the one that completes it, not a fourth.
        again.tap()
        runCurrent()
        assertTrue(again.state.value.pages[1].done)
    }

    @Test fun `taps on a completed page do nothing`() = runTest {
        val rig = rig("completed", ::tenPastFajr)
        rig.store.save(AdhkarSet.MORNING, counts(AdhkarSet.MORNING, 1))
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        rig.viewModel.goTo(0)
        rig.viewModel.tap()
        runCurrent()
        val state = rig.viewModel.state.value
        assertEquals(emptyList(), rig.haptics.fired)
        assertEquals(1, state.pages[0].count)
        assertEquals(0, state.currentPage)
        assertEquals(counts(AdhkarSet.MORNING, 1).encode(), rig.store.progress.getValue(AdhkarSet.MORNING).value)
    }

    @Test fun `progress is counted never visited`() = runTest {
        val rig = rig("never-visited", ::tenPastFajr)
        rig.store.save(AdhkarSet.MORNING, counts(AdhkarSet.MORNING, 1, 3))
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        rig.viewModel.goTo(5)
        runCurrent()
        val state = rig.viewModel.state.value
        assertEquals(5, state.currentPage)
        assertEquals(2, state.completed)
        assertTrue((2..4).none { state.pages[it].done })
    }

    @Test fun `the istighfar page shares one daily counter across sets`() = runTest {
        val rig = rig("istighfar", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        val morningIndex = morningPages.indexOfFirst { it.countDaily }
        rig.viewModel.goTo(morningIndex)
        repeat(2) { rig.viewModel.tap() }
        runCurrent()
        assertEquals(2, rig.viewModel.state.value.pages[morningIndex].count)
        assertEquals(AdhkarProgress(date, listOf(2)).encode(), rig.store.istighfar.value)

        rig.viewModel.selectSet(AdhkarSet.EVENING)
        loaded(rig, AdhkarSet.EVENING)
        val evening = rig.viewModel.state.value
        val eveningIndex = eveningPages.indexOfFirst { it.countDaily }
        assertEquals(AdhkarSet.EVENING, evening.set)
        assertEquals(2, evening.pages[eveningIndex].count)

        rig.viewModel.goTo(eveningIndex)
        rig.viewModel.tap()
        runCurrent()
        assertEquals(AdhkarProgress(date, listOf(3)).encode(), rig.store.istighfar.value)
        rig.viewModel.selectSet(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        assertEquals(3, rig.viewModel.state.value.pages[morningIndex].count)
    }

    @Test fun `the phase caption follows the window`() = runTest {
        val rig = rig("phase", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        assertEquals(AdhkarPhase.BEST, rig.viewModel.state.value.phase)
        assertEquals(clock(rig.day.time(Prayer.SUNRISE)), rig.viewModel.state.value.until)

        rig.clock.now = rig.day.time(Prayer.SUNRISE) + 1.hours
        rig.viewModel.tick()
        runCurrent()
        assertEquals(AdhkarPhase.MAKEUP, rig.viewModel.state.value.phase)
        assertEquals(clock(rig.day.time(Prayer.DHUHR)), rig.viewModel.state.value.until)

        rig.clock.now = rig.day.time(Prayer.DHUHR) + 1.minutes
        rig.viewModel.tick()
        runCurrent()
        assertEquals(AdhkarPhase.NONE, rig.viewModel.state.value.phase)
        assertNull(rig.viewModel.state.value.until)
        // The set on screen is the one that was opened; the clock moving on does not swap it.
        assertEquals(AdhkarSet.MORNING, rig.viewModel.state.value.set)
    }

    @Test fun `switching sets keeps each set's progress`() = runTest {
        val rig = rig("switch", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        rig.viewModel.tap()
        runCurrent()
        assertTrue(rig.viewModel.state.value.pages[0].done)

        rig.viewModel.selectSet(AdhkarSet.EVENING)
        loaded(rig, AdhkarSet.EVENING)
        val evening = rig.viewModel.state.value
        assertEquals(AdhkarSet.EVENING, evening.set)
        assertEquals(0, evening.pages[0].count)
        assertEquals(0, evening.completed)
        assertEquals(0, evening.currentPage)

        rig.viewModel.goTo(1)
        rig.viewModel.tap()
        runCurrent()
        rig.viewModel.selectSet(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        val morning = rig.viewModel.state.value
        assertEquals(AdhkarSet.MORNING, morning.set)
        assertEquals(1, morning.pages[0].count)
        assertEquals(0, morning.pages[1].count)
        assertEquals(1, morning.completed)
        assertEquals(1, morning.currentPage)
        assertEquals(counts(AdhkarSet.EVENING, 0, 1).encode(), rig.store.progress.getValue(AdhkarSet.EVENING).value)
    }

    @Test fun `before fajr the evening is still yesterday's`() = runTest {
        val rig = rig("before-fajr", { day -> day.time(Prayer.FAJR) - 30.minutes })
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        val size = eveningPages.size
        rig.store.save(AdhkarSet.EVENING, AdhkarProgress(yesterday, listOf(1) + List(size - 1) { 0 }))
        rig.viewModel.open(null)
        val state = loaded(rig, AdhkarSet.EVENING)
        // Last evening's set, keyed by last evening's Asr: its first page is already said.
        assertEquals(AdhkarSet.EVENING, state.set)
        assertEquals(1, state.pages[0].count)
        assertEquals(1, state.currentPage)
        rig.viewModel.tap()
        runCurrent()
        assertEquals(
            AdhkarProgress(yesterday, listOf(1, 1) + List(size - 2) { 0 }).encode(),
            rig.store.progress.getValue(AdhkarSet.EVENING).value,
        )
    }

    @Test fun `before fajr the istighfar is the day's not the calendar's`() = runTest {
        val rig = rig("istighfar-day", { day -> day.time(Prayer.FAJR) - 30.minutes })
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        // Counted last evening; after midnight and before Fajr it is still last evening's count.
        rig.store.saveIstighfar(yesterday, 14)
        rig.viewModel.open(null)
        val state = loaded(rig, AdhkarSet.EVENING)
        val index = eveningPages.indexOfFirst { it.countDaily }
        assertEquals(14, state.pages[index].count)
        rig.viewModel.goTo(index)
        rig.viewModel.tap()
        runCurrent()
        assertEquals(AdhkarProgress(yesterday, listOf(15)).encode(), rig.store.istighfar.value)
    }

    @Test fun `a tick across midnight keeps the evening and its istighfar`() = runTest {
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        val rig = rig("midnight", { LocalDateTime(yesterday, LocalTime(23, 50)).toInstant(zone) })
        rig.viewModel.open(null)
        loaded(rig, AdhkarSet.EVENING)
        val index = eveningPages.indexOfFirst { it.countDaily }
        rig.viewModel.goTo(index)
        repeat(5) { rig.viewModel.tap() }
        runCurrent()

        rig.clock.now = LocalDateTime(date, LocalTime(0, 10)).toInstant(zone)
        rig.viewModel.tick()
        runCurrent()
        val after = rig.viewModel.state.value
        assertEquals(AdhkarSet.EVENING, after.set)
        assertEquals(index, after.currentPage)
        assertEquals(5, after.pages[index].count)
        rig.viewModel.tap()
        runCurrent()
        assertEquals(AdhkarProgress(yesterday, listOf(6)).encode(), rig.store.istighfar.value)
    }

    @Test fun `a stored count above its target is shown as the target`() = runTest {
        val rig = rig("clamp", ::tenPastFajr)
        rig.store.save(AdhkarSet.MORNING, counts(AdhkarSet.MORNING, 7, 9))
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        val state = rig.viewModel.state.value
        assertEquals(1, state.pages[0].count)
        assertEquals(3, state.pages[1].count)
        assertEquals(2, state.completed)
    }

    @Test fun `without a location nothing is loaded`() = runTest {
        val rig = rig("no-location", ::tenPastFajr, location = null)
        rig.viewModel.open(null)
        runCurrent()
        val state = rig.viewModel.state.value
        assertFalse(state.loaded)
        assertEquals(emptyList(), state.pages)
        rig.viewModel.tap()
        runCurrent()
        assertEquals(emptyList(), rig.haptics.fired)
    }

    @Test fun `a page carries its arabic and its words`() = runTest {
        val rig = rig("texts", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.EVENING)
        loaded(rig, AdhkarSet.EVENING)
        val text: AdhkarText = AdhkarCatalogue.parseText(EN_JSON)
        val page = rig.viewModel.state.value.pages.first { it.item.id == "hisn-077" }
        val item = page.item
        assertEquals(item.arabicFor(AdhkarSet.EVENING), page.arabic)
        assertEquals(item.transliterationFor(AdhkarSet.EVENING), page.transliteration)
        assertEquals(text.translationFor("hisn-077", AdhkarSet.EVENING), page.translation)
        assertNull(page.quran)
        assertEquals(1, page.target)
    }

    @Test fun `the quran pages read their text from the database`() = runTest {
        val rig = rig("quran", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        val pages = rig.viewModel.state.value.pages
        val kursiPage = pages[0].quran!!
        assertEquals(items.first { it.id == "hisn-075" }.arabicFor(AdhkarSet.MORNING), kursiPage.prefix)
        assertEquals(listOf(kursi to 255), kursiPage.lines)
        assertEquals("Allah - there is no deity except Him", kursiPage.translation)

        val ikhlas = pages[1].quran!!
        assertEquals(basmala, ikhlas.prefix)
        assertEquals((1..4).map { "ikhlas-$it" to it }, ikhlas.lines)
        assertEquals((1..4).joinToString(" ") { "ikhlas translation $it" }, ikhlas.translation)
        assertEquals(3, pages[1].target)
    }

    @Test fun `the speaker plays ayat al-kursi alone and each qul whole`() = runTest {
        val rig = rig("recited", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        val pages = loaded(rig, AdhkarSet.MORNING).pages
        val recited = pages.filter { it.item.kind == AdhkarKind.QURAN }.map { it.recited }
        assertEquals(
            listOf(
                RecitedPassage(2, listOf(255), wholeSurah = false),
                RecitedPassage(112, (1..4).toList(), wholeSurah = true),
                RecitedPassage(113, (1..5).toList(), wholeSurah = true),
                RecitedPassage(114, (1..6).toList(), wholeSurah = true),
            ),
            recited,
        )
        // A du'a has no audio of any kind, so its page has no speaker.
        assertTrue(pages.filter { it.item.kind == AdhkarKind.DUA }.all { it.recited == null })
    }

    @Test fun `the lock screen names the surah and the passage`() {
        val kursi = RecitedPassage(2, listOf(255), wholeSurah = false)
        assertEquals("Al-Baqarah · 2:255", kursi.subtitle("Al-Baqarah", EnglishPlatformFormat))
        assertEquals("Al-Ikhlas · 112", RecitedPassage(112, (1..4).toList(), wholeSurah = true).subtitle("Al-Ikhlas", EnglishPlatformFormat))
        // Before the name has been read, the reference stands alone rather than after a bare dot.
        assertEquals("2:255", kursi.subtitle("", EnglishPlatformFormat))
    }

    @Test fun `the translation switch is the store's`() = runTest {
        val rig = rig("translation", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        assertTrue(rig.viewModel.state.value.showTranslation)
        rig.viewModel.setTranslation(false)
        runCurrent()
        assertFalse(rig.store.translation.first())
        assertFalse(rig.viewModel.state.value.showTranslation)
    }

    @Test fun `the translation toggle persists`() = runTest {
        val rig = rig("translation-persists", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        loaded(rig, AdhkarSet.MORNING)
        rig.viewModel.setTranslation(false)
        runCurrent()
        rig.viewModel.close()

        val again = rig.reopen()
        again.open(AdhkarSet.MORNING)
        loaded(again, AdhkarSet.MORNING)
        runCurrent()
        assertFalse(again.state.value.showTranslation)
    }

    @Test fun `the reading preview is the first six words of the du'a on screen`() = runTest {
        val rig = rig("preview", ::tenPastFajr)
        rig.viewModel.open(AdhkarSet.MORNING)
        val state = loaded(rig, AdhkarSet.MORNING)

        // The catalogue's Arabic is single-spaced, so its words are what lies between the spaces.
        fun words(index: Int): List<String> = state.pages[index].arabic!!.split(' ')

        // Ayat al-Kursi opens the set: the Quran is not the du'a face's to show, so the set's
        // first du'a stands in for it — a long one, cut to its first six words.
        val firstDua = state.pages.indexOfFirst { it.arabic != null }
        assertEquals(0, state.currentPage)
        assertNull(state.pages[0].arabic)
        assertTrue(words(firstDua).size > 6)
        assertEquals(words(firstDua).take(6).joinToString(" "), state.readingPreview)

        // The istighfar is four words, and shown whole.
        val lastDua = state.pages.indexOfLast { it.arabic != null }
        assertNotEquals(firstDua, lastDua)
        assertTrue(words(lastDua).size < 6)
        rig.viewModel.goTo(lastDua)
        assertEquals(state.pages[lastDua].arabic, rig.viewModel.state.value.readingPreview)

        // The done page has no words of its own either.
        rig.viewModel.goTo(state.pages.size)
        assertEquals(words(firstDua).take(6).joinToString(" "), rig.viewModel.state.value.readingPreview)
    }

    @Test fun `the reading preview splits on any whitespace`() {
        // Today's catalogue is single-spaced; a line break or a doubled space in a later one must
        // neither count as a word nor reach the preview.
        val newline = Char(10)
        val tab = Char(9)
        val arabic = "w1${newline}w2${tab}w3  w4 w5$newline${newline}w6 w7"
        val item = items.first { it.id == "hisn-086" }
        val state = AdhkarUiState(
            pages = listOf(AdhkarPageUi(item, arabic, null, null, null, count = 0, target = 3, done = false)),
            loaded = true,
        )
        assertEquals("w1 w2 w3 w4 w5 w6", state.readingPreview)
    }
}
