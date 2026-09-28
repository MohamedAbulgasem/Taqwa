package world.taqwa.app.feature.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.prayer.ClockIssue
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.settings.ResolvedCityName
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Counts writes rather than only recording them: the whole point of C5 is how *often* the mirror
 * is written, not what ends up in it. */
private class CountingKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    var writes = 0
        private set
    override fun putString(key: String, value: String) {
        writes++
        map[key] = value
    }
    override fun getString(key: String): String? = map[key]
}

/**
 * Counts every write the settings store takes, for the same reason [CountingKeyValueStore] counts
 * the mirror's: remembering the header's name must cost one write when the answer changes, not one
 * per tick of a screen that refreshes every second.
 */
private class CountingDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    var writes = 0
        private set
    override val data: Flow<Preferences> get() = delegate.data
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writes++
        return delegate.updateData(transform)
    }
}

/**
 * English in every respect but the language tag, which is the only thing the city-name lookup
 * reads. Delegating keeps the dates and digits in these tests readable while the header resolves
 * as it would for a reader in Arabic.
 */
private class ArabicPlatformFormat : PlatformFormat by EnglishPlatformFormat {
    override fun languageTag(): String = "ar-LY"
}

/**
 * A [TodayViewModel] whose clock is driven by hand and whose two widget side effects — the mirror
 * write and the platform nudge — are counted instead of performed.
 *
 * `start()`'s own one-second loop is not used: it never terminates under `runTest`. Advancing
 * [now] by hand reproduces exactly what that loop does to `refresh()`.
 */
private class WidgetTrafficProbe(private val location: GeoLocation, private val repo: SettingsRepository) {
    val store = CountingKeyValueStore()
    var refreshes = 0
        private set
    var now: Instant = Instant.parse("2026-09-06T14:30:00Z")

    val viewModel = TodayViewModel(
        engine = PrayerTimesEngine(),
        settings = repo,
        locationOf = { location },
        now = { now },
        widgetStore = { store },
        widgetFormat = { EnglishPlatformFormat },
        onWidgetsChanged = { refreshes++ },
    )
}

class TodayViewModelTest {

    // Same absolute-path rule as SettingsRepositoryTest: DataStore's OkioStorage rejects relative
    // paths at runtime on iosSimulatorArm64Test. One file per test keeps the cases isolated, and
    // the file is removed first: it outlives the process, and a city name remembered by the last
    // run — or by the JVM run of this same suite, moments earlier — is exactly the stale state
    // the city-name tests exist to catch. The iOS run failed on precisely that until this did.
    // The store's writes run on the test's own scheduler (`scope = backgroundScope`), as Android's
    // DataStore testing guidance has it. On its default IO scope a write raced the virtual clock,
    // and a test waiting for the stored value could hang until runTest gave up (seen in CI).
    private fun TestScope.settings(name: String): SettingsRepository {
        val path = freshStorePath(name)
        return SettingsRepository(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { path })
    }

    private fun freshStorePath(name: String): Path {
        val path = "/tmp/taqwa-test-today-$name.preferences_pb".toPath()
        FileSystem.SYSTEM.delete(path, mustExist = false)
        return path
    }

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
    private val longyearbyen = GeoLocation(78.2232, 15.6267, "Europe/Oslo", "Longyearbyen", "SJ")

    /**
     * The view model is built with a fake location lookup and a frozen clock, so every case here
     * runs without a device, a GPS fix or a real wall clock. `refresh()` is awaited directly
     * rather than starting the one-second tick, which would never terminate under `runTest`.
     */
    private suspend fun TestScope.viewModel(
        store: String,
        location: GeoLocation?,
        now: Instant,
        hijriOffset: Int = 0,
    ): TodayViewModel {
        val repo = settings(store)
        repo.setHijriOffsetDays(hijriOffset)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { location },
            now = { now },
        )
        vm.refresh()
        return vm
    }

    // Each case gets its own store file: DataStore refuses two live instances over one path,
    // and two tests sharing a name would collide inside a single test binary.
    private suspend fun TestScope.todayViewModelWithNoLocation() =
        viewModel("no-location", null, Instant.parse("2026-09-06T14:30:00Z"))

    private suspend fun TestScope.todayViewModelForLondon(
        store: String,
        now: Instant = Instant.parse("2026-09-06T14:30:00Z"),
        hijriOffset: Int = 0,
    ) = viewModel(store, london, now, hijriOffset)

    private suspend fun TestScope.todayViewModelForTromso(store: String, now: Instant) =
        viewModel(store, tromso, now)

    @Test
    fun withoutALocationTheScreenAsksForOneRatherThanShowingNothing() = runTest {
        val vm = todayViewModelWithNoLocation()
        assertEquals(TodayUiState.NeedsLocation, vm.state.first { it !is TodayUiState.Loading })
    }

    @Test
    fun withALocationTheScreenIsReadyAndNamesTheNextPrayer() = runTest {
        val vm = todayViewModelForLondon("ready", now = Instant.parse("2026-09-06T14:30:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals(5, ready.today.rows.size)
        assertTrue(ready.hijri.isNotBlank())
    }

    // Iteration 8: the Prayer screen shows both calendars on one line and carries a Qibla card,
    // so the state has to supply the Gregorian date in the location's zone and the geometry.
    @Test
    fun readyCarriesTheGregorianDateAndTheQiblaGeometry() = runTest {
        val vm = todayViewModelForLondon("ready-extras", now = Instant.parse("2026-09-06T14:30:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals("6 September 2026", ready.gregorian)
        // London to Makkah: roughly 119 degrees and 4,700 km (spec §12 quotes 118.9°).
        assertTrue(ready.qiblaBearingDegrees in 118.0..120.0, "bearing ${ready.qiblaBearingDegrees}")
        assertTrue(ready.qiblaDistanceKm in 4_600.0..4_800.0, "distance ${ready.qiblaDistanceKm}")
    }

    private val oslo = GeoLocation(59.91273, 10.74609, "Europe/Oslo", "Oslo", "NO")
    private val bergen = GeoLocation(60.39299, 5.32415, "Europe/Oslo", "Bergen", "NO")

    @Test
    fun anOtherMethodsLegacyRuleMarksFajrAndIshaSetByRule() = runTest {
        // Oslo in June: the 18° dawn never comes, so MWL's Fajr and Isha are set by the rule the
        // user chose for it, and each row says so in its pill (spec §2.1).
        val repo = settings("oslo-legacy-rule")
        repo.setTimetable("other.mwl")
        repo.setTimetableConfirmed("other.mwl", PrayerTimesEngine().automaticEntryId(oslo))
        repo.setLegacyHighLatitude(HighLatitudePreference.SEVENTH_OF_NIGHT)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { oslo },
            now = { Instant.parse("2026-06-21T12:00:00Z") },
        )
        vm.refresh()
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(Prayer.FAJR in ready.setByRule && Prayer.ISHA in ready.setByRule, "${ready.setByRule}")
        assertFalse(ready.polar)
    }

    @Test
    fun theEnginesOwnRuleMarksOnlyWhatItSet() = runTest {
        // The engine's own rule at Bergen on the solstice: the real dawn comes, the nightfall that
        // ends Isha's twilight does not, so Isha alone is set by rule. (Oslo itself follows IRN's and
        // Diyanet's own calendars' curves since Task 7g, so nothing there is set by rule.)
        val vm = viewModel("bergen-own-rule", bergen, Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals(setOf(Prayer.ISHA), ready.setByRule)
    }

    @Test
    fun aPolarDayShowsItsLineAndNoPills() = runTest {
        val vm = viewModel("longyearbyen-polar", longyearbyen, Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(ready.polar)
        assertTrue(ready.setByRule.isEmpty(), "${ready.setByRule}")
    }

    @Test
    fun underTromsosMidnightSunIrnsMakkahTimeShowsItsEstimatesAsSetByRule() = runTest {
        // Ruling R82: IRN's rule gives the whole day, so nothing follows the nearest latitude and no
        // polar line says so; its Makkah-time estimates carry the pill, its noon Dhuhr does not.
        val vm = todayViewModelForTromso("tromso-makkah-time", Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertFalse(ready.polar)
        assertTrue(ready.setByRule.containsAll(listOf(Prayer.FAJR, Prayer.MAGHRIB, Prayer.ISHA)), "${ready.setByRule}")
        assertFalse(Prayer.DHUHR in ready.setByRule)
    }

    // -- Whose times and the one-time cards (spec §2.1) -----------------------------------------

    private val toronto = GeoLocation(43.6532, -79.3832, "America/Toronto", "Toronto", "CA")
    private val dammam = GeoLocation(26.4344, 50.1033, "Asia/Riyadh", "Dammam", "SA")
    private val istanbul = GeoLocation(41.0082, 28.9784, "Europe/Istanbul", "Istanbul", "TR")

    private fun TestScope.cardViewModel(
        store: String,
        location: GeoLocation,
        session: OneTimeCardSession = OneTimeCardSession(),
        repo: SettingsRepository = settings(store),
    ) = TodayViewModel(
        engine = PrayerTimesEngine(),
        settings = repo,
        locationOf = { location },
        now = { Instant.parse("2026-10-01T18:00:00Z") },
        cardSession = session,
    )

    @Test
    fun istanbulNamesDiyanetAsAClassATimetableWithNoCard() = runTest {
        val vm = cardViewModel("istanbul-whose", istanbul)
        vm.refresh()
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals(WhoseTimes("authority_diyanet", EntryClass.A), ready.whose)
        assertNull(ready.oneTimeCard)
    }

    @Test
    fun torontoIsCautiousAndShowsTheCautiousCardUntilItIsAnswered() = runTest {
        val repo = settings("toronto-card")
        val session = OneTimeCardSession()
        val vm = cardViewModel("toronto-card", toronto, session, repo)
        vm.refresh()
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(ready.whose.cautious)
        assertEquals(OneTimeCard.CAUTIOUS, ready.oneTimeCard)
        // Once-only: stored as seen as soon as it is shown, and still on screen this launch.
        assertTrue(repo.cautiousCardSeen.first())
        vm.refresh()
        assertEquals(OneTimeCard.CAUTIOUS, (vm.state.value as TodayUiState.Ready).oneTimeCard)

        vm.answerOneTimeCard(OneTimeCard.CAUTIOUS)
        assertNull((vm.state.value as TodayUiState.Ready).oneTimeCard)
        vm.refresh()
        assertNull((vm.state.value as TodayUiState.Ready).oneTimeCard)
    }

    @Test
    fun aCardShownButNeverAnsweredDoesNotComeBackNextLaunch() = runTest {
        val repo = settings("toronto-unanswered")
        val first = cardViewModel("toronto-unanswered", toronto, OneTimeCardSession(), repo)
        first.refresh()
        assertEquals(OneTimeCard.CAUTIOUS, (first.state.first { it is TodayUiState.Ready } as TodayUiState.Ready).oneTimeCard)
        val nextLaunch = cardViewModel("toronto-unanswered", toronto, OneTimeCardSession(), repo)
        nextLaunch.refresh()
        assertNull((nextLaunch.state.first { it is TodayUiState.Ready } as TodayUiState.Ready).oneTimeCard)
    }

    @Test
    fun dammamShowsTheSunniCard() = runTest {
        val vm = cardViewModel("dammam-card", dammam)
        vm.refresh()
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals(OneTimeCard.SUNNI, ready.oneTimeCard)
    }

    @Test
    fun anAnsweredCardKeepsTheOtherAwayUntilTheNextLaunch() = runTest {
        // Answered in Toronto; a move to Dammam in the same launch shows nothing until the next.
        val session = OneTimeCardSession()
        val first = cardViewModel("launch-toronto", toronto, session)
        first.refresh()
        first.answerOneTimeCard(OneTimeCard.CAUTIOUS)
        val second = cardViewModel("launch-dammam", dammam, session)
        second.refresh()
        assertNull((second.state.first { it is TodayUiState.Ready } as TodayUiState.Ready).oneTimeCard)
        val nextLaunch = cardViewModel("launch-dammam-next", dammam, OneTimeCardSession())
        nextLaunch.refresh()
        assertEquals(
            OneTimeCard.SUNNI,
            (nextLaunch.state.first { it is TodayUiState.Ready } as TodayUiState.Ready).oneTimeCard,
        )
    }

    // -- The other Asr and where timetables differ (spec §2.1) ----------------------------------

    private suspend fun TestScope.readyAt(
        store: String,
        location: GeoLocation,
        now: Instant,
        configure: suspend (SettingsRepository) -> Unit,
    ): TodayUiState.Ready {
        val repo = settings(store)
        configure(repo)
        val vm = TodayViewModel(engine = PrayerTimesEngine(), settings = repo, locationOf = { location }, now = { now })
        vm.refresh()
        return vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
    }

    @Test
    fun showBothAsrTimesGivesTheOtherSchoolsAsr() = runTest {
        // Istanbul follows Diyanet's Standard Asr, so the other is the Hanafi one, later.
        val ready = readyAt("both-asr", istanbul, Instant.parse("2026-09-26T10:00:00Z")) { it.setShowBothAsr(true) }
        val other = ready.otherAsr!!
        assertEquals(AsrSchool.HANAFI, other.school)
        assertTrue(other.instant > ready.today.rows.first { it.prayer == Prayer.ASR }.instant)
    }

    @Test
    fun bothAsrTimesAreOffByDefault() = runTest {
        assertNull(readyAt("both-asr-off", istanbul, Instant.parse("2026-09-26T10:00:00Z")) {}.otherAsr)
    }

    private val torontoMembers = setOf("authority_ift", "authority_iit", "authority_mac_toronto")

    @Test
    fun beforeTheEarliestIshaTheLineSaysWhenItBegins() = runTest {
        // 19:30 in Toronto on 1 October: Maghrib is current; the earliest listed timetable begins
        // Isha before the Isha Taqwa shows.
        val ready = readyAt("differ-before", toronto, Instant.parse("2026-10-01T23:30:00Z")) { it.setShowWhereDiffer(true) }
        val line = ready.differ!!
        assertEquals(Prayer.MAGHRIB, line.under)
        assertEquals(Prayer.ISHA, line.prayer)
        assertTrue(line.memberNameKey in torontoMembers, line.memberNameKey)
        assertTrue(line.memberStart < line.shownStart)
        assertEquals(ready.today.next.instant, line.shownStart)
        assertFalse(line.begun)
    }

    @Test
    fun afterTheEarliestIshaTheLineSaysItHasBegun() = runTest {
        // 20:20: the earliest member's Isha has begun, Taqwa's has not.
        val ready = readyAt("differ-after", toronto, Instant.parse("2026-10-02T00:20:00Z")) { it.setShowWhereDiffer(true) }
        val line = ready.differ!!
        assertEquals(Prayer.ISHA, line.prayer)
        assertTrue(line.begun)
    }

    @Test
    fun whereTimetablesDifferIsOffByDefault() = runTest {
        assertNull(readyAt("differ-off", toronto, Instant.parse("2026-10-01T23:30:00Z")) {}.differ)
    }

    @Test
    fun aPlaceWithOneTimetableHasNoDifferLine() = runTest {
        assertNull(readyAt("differ-istanbul", istanbul, Instant.parse("2026-09-26T15:30:00Z")) { it.setShowWhereDiffer(true) }.differ)
    }

    // -- The clock line (spec §3.9) -------------------------------------------------------------

    private val casablanca = GeoLocation(33.5731, -7.5898, "Africa/Casablanca", "Casablanca", "MA")

    private suspend fun TestScope.casablancaReady(store: String, phoneOffset: Int, setByHand: Boolean?): TodayUiState.Ready {
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = settings(store),
            locationOf = { casablanca },
            now = { Instant.parse("2026-09-26T21:02:00Z") },
            clockSetByHand = { setByHand },
            zoneOffsetSeconds = { _, _ -> phoneOffset },
        )
        vm.refresh()
        return vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
    }

    @Test
    fun casablancaOnOldZoneDataWarnsThatTimesMayBeAnHourLate() = runTest {
        val warning = casablancaReady("casa-stale", phoneOffset = 3600, setByHand = false).clockWarning!!
        assertEquals(ClockIssue.STALE_ZONE_DATA, warning.issue)
        assertFalse(warning.timesEarly)
    }

    @Test
    fun casablancaOnAHandSetClockWarnsToo() = runTest {
        assertEquals(ClockIssue.SET_BY_HAND, casablancaReady("casa-hand", phoneOffset = 0, setByHand = true).clockWarning?.issue)
    }

    @Test
    fun casablancaOnCurrentZoneDataAndAutomaticTimeHasNoClockLine() = runTest {
        assertNull(casablancaReady("casa-fine", phoneOffset = 0, setByHand = false).clockWarning)
    }

    @Test
    fun theHijriOffsetShiftsTheDisplayedDate() = runTest {
        val zero = todayViewModelForLondon("hijri-0", hijriOffset = 0).state.first { it is TodayUiState.Ready }
        val plus = todayViewModelForLondon("hijri-1", hijriOffset = 1).state.first { it is TodayUiState.Ready }
        assertTrue((zero as TodayUiState.Ready).hijri != (plus as TodayUiState.Ready).hijri)
    }

    // -- C5: the once-per-second widget refresh storm ------------------------------------------
    //
    // `start()` ticks once a second, and every tick used to re-serialise the mirror, write it, and
    // nudge both widget systems: 60 writes and 60 reloads a minute. On iOS that outran WidgetKit's
    // ~40-70 reloads/day budget in under a minute, after which the widget froze for the rest of
    // the day; on Android it blocked the composition's Main dispatcher on a SharedPreferences
    // write plus two Glance recompositions plus AppWidgetManager IPC, once a second, on the app's
    // primary screen.

    private suspend fun TestScope.probeFor(storeName: String): WidgetTrafficProbe {
        val repo = settings(storeName)
        repo.setHijriOffsetDays(0)
        return WidgetTrafficProbe(london, repo)
    }

    /**
     * Anchors the clock so the countdown to the real next prayer is exactly 40 min 30 s. Offsets
     * are taken from the engine's own instant rather than from a fixed wall-clock time, so the
     * "same minute" pair below is provably inside one minute of the countdown (40:30 and 40:29
     * both floor to 40 minutes) instead of depending on where 14:30 happens to fall.
     */
    private suspend fun WidgetTrafficProbe.anchorAtFortyMinutesThirtySeconds() {
        viewModel.refresh()
        val ready = viewModel.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        now = ready.today.next.instant - 40.minutes - 30.seconds
    }

    @Test
    fun twoTicksOneSecondApartInsideOneMinuteWriteTheMirrorOnceAndRefreshOnce() = runTest {
        val probe = probeFor("widget-traffic-same-minute")
        probe.anchorAtFortyMinutesThirtySeconds()

        val writesBefore = probe.store.writes
        val refreshesBefore = probe.refreshes
        probe.viewModel.refresh()
        probe.now += 1.seconds
        probe.viewModel.refresh()

        assertEquals(1, probe.store.writes - writesBefore)
        assertEquals(1, probe.refreshes - refreshesBefore)
    }

    @Test
    fun crossingAMinuteBoundaryWritesTheMirrorASecondTime() = runTest {
        val probe = probeFor("widget-traffic-minute-boundary")
        probe.anchorAtFortyMinutesThirtySeconds()

        val writesBefore = probe.store.writes
        val refreshesBefore = probe.refreshes
        probe.viewModel.refresh()
        // 40:30 -> 39:59. The whole-minute countdown changes, so the widgets must hear about it:
        // the dedupe must suppress redundant ticks, never a real one.
        probe.now += 31.seconds
        probe.viewModel.refresh()

        assertEquals(2, probe.store.writes - writesBefore)
        assertEquals(2, probe.refreshes - refreshesBefore)
    }

    /**
     * The device-level claim behind C5, asserted in code: 90 seconds with Today open must cost a
     * handful of widget updates, not ninety. Ninety seconds crosses one or two whole minutes, and
     * the ring — quantised to 1/120 of the gap between prayers — may add a step of its own, so the
     * ceiling is deliberately loose. What matters is that it is nowhere near the tick count.
     */
    @Test
    fun ninetySecondsOfTickingCostsAHandfulOfWidgetUpdatesNotNinety() = runTest {
        val probe = probeFor("widget-traffic-ninety-seconds")
        probe.anchorAtFortyMinutesThirtySeconds()

        val writesBefore = probe.store.writes
        val refreshesBefore = probe.refreshes
        repeat(90) {
            probe.viewModel.refresh()
            probe.now += 1.seconds
        }

        val writes = probe.store.writes - writesBefore
        assertTrue(writes in 1..6, "90 ticks produced $writes mirror writes")
        assertEquals(writes, probe.refreshes - refreshesBefore)
    }

    // -- the lifecycle-gated tick -----------------------------------------------------------
    //
    // `tickWhileActive()` replaced `start(scope)`: it owns no scope and no `launch` of its own,
    // so it runs only for as long as its *caller's* coroutine survives. In `App.kt` that caller is
    // `repeatOnLifecycle(STARTED) { … }`, which cancels the block the moment the activity is
    // stopped (screen off, Home pressed) rather than merely leaving composition. This is the
    // behaviour-level guard for that: drive the loop from a child job, let it tick for real
    // (through a minute boundary, so the dedupe in `refresh()` doesn't hide a stalled loop),
    // cancel the job, and prove nothing it does — mirror write or widget nudge — continues.

    @Test
    fun cancellingTheTickingJobStopsFurtherWidgetActivity() = runTest {
        val repo = settings("tick-while-active-cancel")
        repo.setHijriOffsetDays(0)
        val store = CountingKeyValueStore()
        var refreshes = 0
        val base = Instant.parse("2026-09-06T14:30:00Z")
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { london },
            // Tied to the test dispatcher's own virtual clock rather than a value advanced by
            // hand, so `tickWhileActive()`'s real `delay(1_000)` loop is what moves time forward —
            // exactly what a fake `now` driven manually (as the rest of this file uses) can't
            // exercise, since nothing here calls `refresh()` directly.
            now = { base + currentTime.milliseconds },
            widgetStore = { store },
            widgetFormat = { EnglishPlatformFormat },
            onWidgetsChanged = { refreshes++ },
        )

        val job = launch { vm.tickWhileActive() }
        // The loop refreshes once immediately, before its first delay — the screen must be
        // correct the instant Today appears, not a second late. Awaited through the state flow
        // rather than `runCurrent()`: the settings DataStore's first read is real (non-virtual)
        // I/O, which a scheduler advance does not wait out, but suspending on the state this
        // refresh produces does.
        vm.state.first { it is TodayUiState.Ready }
        assertEquals(1, store.writes, "entry must write the mirror once before the first tick")

        // Tick through whole-minute boundaries the dedupe cannot collapse into the first write.
        // Each virtual second only *schedules* the next `refresh()`; running it still crosses
        // through the settings DataStore's real (non-virtual) read, so a one-shot
        // `advanceTimeBy(3.minutes)` would race ahead of that real completion and starve the loop
        // after tick one. Ceding to a real dispatcher between virtual steps gives that read
        // somewhere to actually finish.
        repeat(180) {
            advanceTimeBy(1.seconds)
            runCurrent()
            withContext(Dispatchers.Default) { yield() }
            runCurrent()
        }
        val writesWhileActive = store.writes
        val refreshesWhileActive = refreshes
        assertTrue(writesWhileActive > 1, "expected minute-boundary writes while active, saw $writesWhileActive")

        job.cancelAndJoin()
        val writesAtCancel = store.writes
        val refreshesAtCancel = refreshes
        assertEquals(refreshesWhileActive, refreshesAtCancel)

        // Five more minutes would produce several more writes if the loop were still running.
        repeat(300) {
            advanceTimeBy(1.seconds)
            runCurrent()
        }

        assertEquals(writesAtCancel, store.writes, "no further mirror writes once the tick job is cancelled")
        assertEquals(refreshesAtCancel, refreshes, "no further widget refresh once the tick job is cancelled")
    }

    // -- the header's city name, and the id a pre-0.4.0 location was saved without ---------------
    //
    // The saved location carries the GeoNames id of the city it came from, so the header can be
    // re-rendered in whatever language the phone is in. `cityName` stays the English snapshot it
    // has always been, which is both the fallback and what an upgraded install has until the id
    // is backfilled.

    private val citiesCsv = """
        id,name,region,country,countryCode,lat,lon,tz
        2643743,London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
        360630,Cairo,Cairo Governorate,Egypt,EG,30.06263,31.24967,Africa/Cairo
    """.trimIndent()

    /** London has an Arabic name; Cairo deliberately does not, so the fallback is exercised. */
    private val arabicNames = """
        id,name
        2643743,لندن
    """.trimIndent()

    private fun cities() = CityRepository(
        loadCsv = { citiesCsv },
        loadNames = { language -> if (language == "ar") arabicNames else null },
    )

    /** Nothing is near anything: `nearest` returns null, which is the no-match case. */
    private fun noCities() = CityRepository(loadCsv = { "id,name,region,country,countryCode,lat,lon,tz" })

    // Saved by this build: the id, the country and the region all stored, so nothing is backfilled.
    private val londonWithId = london.copy(cityId = 2643743, region = "England")
    private val cairoWithId =
        GeoLocation(30.06263, 31.24967, "Africa/Cairo", "Cairo", "EG", cityId = 360630, region = "Cairo Governorate")

    private suspend fun TestScope.readyFor(
        store: String,
        location: GeoLocation,
        format: PlatformFormat,
        cityRepository: CityRepository = cities(),
    ): TodayUiState.Ready {
        val repo = settings(store)
        repo.setHijriOffsetDays(0)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { location },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cityRepository,
            format = format,
        )
        vm.refresh()
        return vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
    }

    @Test
    fun aSavedCityIsNamedInArabicWhenTheInterfaceIs() = runTest {
        val ready = readyFor("city-name-ar", londonWithId, ArabicPlatformFormat())
        assertEquals("لندن", ready.cityDisplayName)
        // The stored English snapshot is untouched — it is a record of what was saved, not what
        // is on screen.
        assertEquals("London", ready.location.cityName)
    }

    @Test
    fun theSameCityIsNamedInEnglishWhenTheInterfaceIs() = runTest {
        val ready = readyFor("city-name-en", londonWithId, EnglishPlatformFormat)
        assertEquals("London", ready.cityDisplayName)
    }

    @Test
    fun aCityWithNoNameInTheInterfaceLanguageFallsBackToTheEnglishOne() = runTest {
        val ready = readyFor("city-name-ar-missing", cairoWithId, ArabicPlatformFormat())
        assertEquals("Cairo", ready.cityDisplayName)
    }

    @Test
    fun aLocationWithNoIdAndNoMatchingCityKeepsShowingItsStoredName() = runTest {
        val ready = readyFor("city-name-no-match", london, ArabicPlatformFormat(), noCities())
        assertEquals("London", ready.cityDisplayName)
        assertNull(ready.location.cityId)
    }

    @Test
    fun aLocationSavedBeforeIdsExistedGetsItsIdFromItsCoordinates() = runTest {
        val repo = settings("city-id-migration")
        repo.setHijriOffsetDays(0)
        repo.setLocation(london)
        assertNull(repo.location.first()!!.cityId, "the fixture must start without an id")

        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = ArabicPlatformFormat(),
        )
        vm.refresh()

        assertEquals(2643743, repo.location.first()!!.cityId, "the id must be written back")
        // And the header follows immediately, without waiting for the next launch.
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals("لندن", ready.cityDisplayName)
    }

    @Test
    fun aLocationSavedWithoutItsCountryOrRegionGetsThemFromTheNearestCity() = runTest {
        // What the prayer engine resolves the timetable by (spec §8, ruling R32).
        val repo = settings("city-region-migration")
        repo.setHijriOffsetDays(0)
        repo.setLocation(londonWithId.copy(countryCode = null, region = null))
        var refreshes = 0
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = EnglishPlatformFormat,
            onLocationBackfilled = { refreshes++ },
        )
        vm.refresh()
        val stored = repo.location.first()!!
        assertEquals("GB", stored.countryCode)
        assertEquals("England", stored.region)
        assertEquals(2643743, stored.cityId)
        assertEquals("London", stored.cityName)
        // The timetable can have changed: notifications and widgets are refreshed once (ruling R53).
        assertEquals(1, refreshes)
        repeat(5) { vm.refresh() }
        assertEquals(1, refreshes)
    }

    @Test
    fun aCityWithNoRegionIsBackfilledOnceNotOnEveryLaunch() = runTest {
        // Singapore's row has no region: the backfill cannot complete the location, so without a
        // done flag every launch would parse and scan the whole city list again.
        val singapore = GeoLocation(1.28967, 103.85007, "Asia/Singapore", "Singapore", "SG", cityId = 1880252)
        val csv = citiesCsv + "\n1880252,Singapore,,Singapore,SG,1.28967,103.85007,Asia/Singapore"
        val repo = settings("city-no-region-once")
        repo.setHijriOffsetDays(0)
        repo.setLocation(singapore)
        var parses = 0
        val first = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = CityRepository(loadCsv = { parses++; csv }),
            format = EnglishPlatformFormat,
        )
        first.refresh()
        assertEquals(1, parses)
        assertEquals(true, repo.locationBackfilled.first())
        assertNull(repo.location.first()!!.region)
        // The next launch: a fresh view model and a fresh city list, with the name already stored.
        val next = viewModelFor(repo, repo.location.first()!!, noCityListAtAll(), EnglishPlatformFormat)
        next.refresh()
    }

    @Test
    fun aLocationThatNamesItsCityIsBackfilledFromThatCityNotTheNearest() = runTest {
        // A neighbour sits exactly on the stored point; the location's own city is a little away.
        val csv = """
            id,name,region,country,countryCode,lat,lon,tz
            2643743,London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
            9999999,Southwark,Southwark Region,United Kingdom,GB,51.5074,-0.1278,Europe/London
        """.trimIndent()
        val repo = settings("city-by-id-backfill")
        repo.setHijriOffsetDays(0)
        repo.setLocation(london.copy(cityId = 2643743))
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = CityRepository(loadCsv = { csv }),
            format = EnglishPlatformFormat,
        )
        vm.refresh()
        assertEquals("England", repo.location.first()!!.region)
    }

    @Test
    fun aBackfillOfTheIdAloneDoesNotRefreshTheNotifications() = runTest {
        // An id changes the header's name, never the times.
        val repo = settings("city-id-only-backfill")
        repo.setHijriOffsetDays(0)
        repo.setLocation(london.copy(region = "England"))
        var refreshes = 0
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = EnglishPlatformFormat,
            onLocationBackfilled = { refreshes++ },
        )
        vm.refresh()
        assertEquals(2643743, repo.location.first()!!.cityId)
        assertEquals(0, refreshes)
    }

    /**
     * A language change swaps the view model for one whose format reports the new language. A
     * fresh one starts at `Loading` and the Prayer screen emptied to its spinner until the first
     * refresh landed; seeded with the outgoing state it simply re-renders in the new language.
     */
    @Test
    fun aLanguageChangeCarriesTheOutgoingStateIntoTheSuccessorRatherThanBlankingTheScreen() = runTest {
        val outgoing = readyFor("city-language-swap-ar", londonWithId, ArabicPlatformFormat())
        assertEquals("\u0644\u0646\u062F\u0646", outgoing.cityDisplayName)

        val repo = settings("city-language-swap-en")
        repo.setHijriOffsetDays(0)
        val successor = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { londonWithId },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = EnglishPlatformFormat,
            initialState = outgoing,
        )
        // Before a single refresh: the same rows, still on screen.
        assertEquals(outgoing, successor.state.value)

        successor.refresh()
        val ready = successor.state.value as TodayUiState.Ready
        assertEquals("London", ready.cityDisplayName, "and then the new language's name")
        assertEquals(outgoing.today.rows.size, ready.today.rows.size)
    }

    /**
     * The backfill writes the id, but `settings.location` is a DataStore flow — the next tick can
     * still read a location without one. Holding the migrated id keeps the header from flickering
     * Arabic \u2192 English \u2192 Arabic while the store catches up.
     */
    @Test
    fun theMigratedIdSurvivesTicksTakenBeforeTheStoreCatchesUp() = runTest {
        val repo = settings("city-id-migration-lag")
        repo.setHijriOffsetDays(0)
        repo.setLocation(london)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { london }, // a store that never reports the id back
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = ArabicPlatformFormat(),
        )
        vm.refresh()
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            (vm.state.value as TodayUiState.Ready).cityDisplayName,
        )

        repeat(3) { vm.refresh() }
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            (vm.state.value as TodayUiState.Ready).cityDisplayName,
            "the header fell back to the English snapshot while the store caught up",
        )
    }

    /**
     * The name lookup suspends on a 1.6 MB parse. A city picked while it was running has already
     * published a state about somewhere else, and the resolved name must not be written over it —
     * the same guard [migrateCityId] has always had.
     */
    @Test
    fun aNameResolvedForACityTheScreenHasAlreadyLeftIsNotPublished() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = CityRepository(
            loadCsv = { gate.await(); citiesCsv },
            loadNames = { language -> if (language == "ar") arabicNames else null },
        )
        val store = settings("city-name-stale")
        store.setHijriOffsetDays(0)
        var current = londonWithId
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = store,
            locationOf = { current },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = slow,
            format = ArabicPlatformFormat(),
        )

        // Everything the state flow publishes, captured before the race rather than sampled after
        // it: the corrupt value the missing guard produced was overwritten by the next tick.
        val seen = mutableListOf<TodayUiState>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.toList(seen) }

        val first = launch { vm.refresh() } // publishes London, then blocks in the lookup
        runCurrent()
        current = cairoWithId
        val second = launch { vm.refresh() } // publishes Cairo, then queues behind the same lock
        runCurrent()

        gate.complete(Unit)
        first.join()
        second.join()
        watcher.cancel()

        val misattributed = seen.filterIsInstance<TodayUiState.Ready>().filter {
            it.location == cairoWithId && it.cityDisplayName == "\u0644\u0646\u062F\u0646"
        }
        assertTrue(misattributed.isEmpty(), "London's Arabic name was published under Cairo")
        assertEquals("Cairo", (vm.state.value as TodayUiState.Ready).cityDisplayName)
    }

    // -- D1: the header remembers its translated name -------------------------------------------
    //
    // The name cannot be looked up without parsing the 1.6 MB city list, and that parse is
    // deliberately off the first frame so the prayer times are not held behind it. On a cold start
    // the header therefore showed the English snapshot for ~0.4 s and then swapped — visibly,
    // after the screen had settled. The answer is remembered next to the id instead, together with
    // the language it was resolved in, so an ordinary launch needs no lookup at all.

    /** Any read of the city list at all fails the test outright. */
    private fun noCityListAtAll() = CityRepository(
        loadCsv = { fail("the city list was parsed on a launch that already knew the name") },
    )

    private fun viewModelFor(
        repo: SettingsRepository,
        location: GeoLocation,
        cityRepository: CityRepository,
        format: PlatformFormat,
    ) = TodayViewModel(
        engine = PrayerTimesEngine(),
        settings = repo,
        locationOf = { location },
        now = { Instant.parse("2026-09-06T14:30:00Z") },
        cityRepository = cityRepository,
        format = format,
    )

    @Test
    fun aColdStartWhoseStoredLanguageMatchesOpensOnTheTranslatedNameAndLooksNothingUp() = runTest {
        val repo = settings("city-name-remembered")
        repo.setHijriOffsetDays(0)
        repo.setLocation(londonWithId, ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"))

        val vm = viewModelFor(repo, londonWithId, noCityListAtAll(), ArabicPlatformFormat())
        val seen = mutableListOf<TodayUiState>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.toList(seen) }
        vm.refresh()
        // The collector is resumed by the scheduler, not by the emission itself: without this it
        // is cancelled before it has seen anything at all.
        runCurrent()
        watcher.cancel()

        val ready = seen.filterIsInstance<TodayUiState.Ready>()
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            ready.first().cityDisplayName,
            "the first state a cold start publishes must already carry the reader's own name",
        )
        // The English snapshot is never on screen, not even for one frame — this is D1 itself.
        assertTrue(
            ready.none { it.cityDisplayName == "London" },
            "the English snapshot was published before the stored name",
        )
    }

    @Test
    fun aColdStartWhoseStoredLanguageDiffersFallsBackToTheSnapshotAndSwapsWhenTheLookupLands() = runTest {
        val repo = settings("city-name-remembered-other-language")
        repo.setHijriOffsetDays(0)
        // Resolved when the phone was in English; the reader has since switched to Arabic.
        repo.setLocation(londonWithId, ResolvedCityName("London", "en-GB"))

        val gate = CompletableDeferred<Unit>()
        val slow = CityRepository(
            loadCsv = { gate.await(); citiesCsv },
            loadNames = { language -> if (language == "ar") arabicNames else null },
        )
        val vm = viewModelFor(repo, londonWithId, slow, ArabicPlatformFormat())

        val refreshing = launch { vm.refresh() }
        // Awaited through the state rather than with `runCurrent()`: the settings store's first
        // read is real I/O, which advancing the scheduler does not wait out. The lookup is still
        // held at the gate, so this is genuinely the state the first frame would paint.
        val first = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        // Today's behaviour, kept on purpose for exactly this case: a stored English name must not
        // be printed to a reader in Arabic, so this one launch flickers as it always did.
        assertEquals("London", first.cityDisplayName)

        gate.complete(Unit)
        refreshing.join()
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            (vm.state.value as TodayUiState.Ready).cityDisplayName,
        )
        // And the next launch will not: the Arabic name is now what is stored.
        assertEquals(
            ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"),
            repo.resolvedCityName.first(),
        )
    }

    @Test
    fun theNameIsRememberedOnceNotOnEveryTick() = runTest {
        val writeOncePath = freshStorePath("city-name-write-once")
        val store = CountingDataStore(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { writeOncePath })
        val repo = SettingsRepository(store)
        repo.setHijriOffsetDays(0)
        repo.setLocation(londonWithId)
        assertNull(repo.resolvedCityName.first(), "a location stored without a name must have none")
        // Spends the Task 9 migration's own one-time write (spec §8) before writesBefore is taken,
        // same as refresh()'s first-ever prayerSettings read would otherwise do on tick 1 — this
        // test is about the per-tick cost, not that one-time cost.
        repo.prayerSettings.first()

        val vm = viewModelFor(repo, londonWithId, cities(), ArabicPlatformFormat())
        val writesBefore = store.writes
        // A minute and a half with the Prayer screen open.
        repeat(90) { vm.refresh() }

        assertEquals(
            1,
            store.writes - writesBefore,
            "90 ticks must write the remembered name exactly once",
        )
        assertEquals(
            ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"),
            repo.resolvedCityName.first(),
        )
    }

    @Test
    fun theIdBackfillIsAttemptedOnceARunNotOnceASecond() = runTest {
        val repo = settings("city-id-migration-once")
        repo.setHijriOffsetDays(0)
        repo.setLocation(london)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = EnglishPlatformFormat,
        )
        vm.refresh()
        assertEquals(2643743, repo.location.first()!!.cityId)

        // Put the store back the way an old install left it. Ninety more ticks — a minute and a
        // half with Today open — must not scan the city list or write the id again, which is what
        // the id staying absent proves.
        repo.setLocation(london)
        repeat(90) { vm.refresh() }
        assertNull(repo.location.first()!!.cityId, "the backfill ran more than once")
    }
}
