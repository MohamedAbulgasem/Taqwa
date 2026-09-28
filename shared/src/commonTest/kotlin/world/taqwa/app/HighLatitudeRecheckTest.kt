package world.taqwa.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.settings.CheckedTime
import world.taqwa.app.feature.settings.TimetableCheck
import world.taqwa.app.feature.settings.TimetableChooser
import world.taqwa.app.feature.settings.highLatitudeNeedsCheck
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Review I1: a confirmed Other method applies as it is, so changing its high-latitude rule is
 * checked against Automatic again, as a new timetable would be.
 */
class HighLatitudeRecheckTest {

    // Berlin (Automatic: Germany's cautious times): Edinburgh's cautious Fajr runs so far after MWL's own on
    // the nights 18° barely occurs since Task 7g's UK families that Fajr is the worst there under either rule.
    private val berlin = GeoLocation(52.52437, 13.41053, "Europe/Berlin", cityName = "Berlin", countryCode = "DE")
    private val from = LocalDate(2026, 9, 27)
    private val engine = PrayerTimesEngine()
    private val mwl = PrayerSettings(timetable = "other.mwl", timetableConfirmed = true, legacyHighLatitude = HighLatitudePreference.AUTOMATIC)

    @Test
    fun onlyAConfirmedOtherMethodIsCheckedAgain() {
        assertTrue(highLatitudeNeedsCheck(mwl))
        assertFalse(highLatitudeNeedsCheck(mwl.copy(timetableConfirmed = false)), "paused: already held to Automatic")
        assertFalse(highLatitudeNeedsCheck(PrayerSettings()), "Automatic follows no legacy rule")
        assertFalse(highLatitudeNeedsCheck(PrayerSettings(timetable = "ca.ift", timetableConfirmed = true)), "an authority brings its own rule")
    }

    @Test
    fun oneSeventhRunsMwlsIshaFurtherAheadAtBerlin() = runTest {
        // From 27 September 2026: with MWL's own rule its Fajr runs furthest ahead of the cautious times;
        // with one-seventh of the night its Isha runs further still.
        val own = checkTimetable(engine, berlin, mwl, from, "other.mwl") {}
        val seventh = checkTimetable(engine, berlin, mwl.copy(legacyHighLatitude = HighLatitudePreference.SEVENTH_OF_NIGHT), from, "other.mwl") {}
        assertIs<TimetableCheck.Earlier>(own)
        assertIs<TimetableCheck.Earlier>(seventh)
        assertEquals(CheckedTime.ISHA, seventh.finding.time)
        assertTrue(seventh.finding.minutes > own.finding.minutes, "$own, then $seventh")
    }

    private fun recheck(
        settings: PrayerSettings,
        scope: CoroutineScope,
        applied: MutableList<Pair<String, Boolean>>,
    ) = TimetableChooser(
        checkScope = scope,
        check = { rule, onProgress ->
            checkTimetable(engine, berlin, settings.copy(legacyHighLatitude = HighLatitudePreference.valueOf(rule)), from, settings.timetable, onProgress)
        },
        nameKeyOf = { "method_muslim_world_league" },
        apply = { rule, confirmed -> applied += rule to confirmed },
        needsCheck = { highLatitudeNeedsCheck(settings) },
    )

    /** The screen's scope: the check itself runs on `Dispatchers.Default`, so the test waits for it for real. */
    private val screen = Job()

    private suspend fun checked() = screen.children.toList().joinAll()

    @Test
    fun theNewRuleIsWrittenOnlyWhenFollowedWithItsNewFigures() = runTest {
        val applied = mutableListOf<Pair<String, Boolean>>()
        val chooser = recheck(mwl, CoroutineScope(screen), applied)
        chooser.choose(HighLatitudePreference.SEVENTH_OF_NIGHT.name)
        checked()
        val warning = assertNotNull(chooser.warning)
        assertEquals(CheckedTime.ISHA, warning.finding.time)
        val ownRule = checkTimetable(engine, berlin, mwl, from, "other.mwl") {} as TimetableCheck.Earlier
        assertTrue(warning.finding.minutes > ownRule.finding.minutes, "the warning names the new rule's figures")
        assertTrue(applied.isEmpty(), "nothing is written before the answer")
        chooser.follow()
        assertEquals(listOf(HighLatitudePreference.SEVENTH_OF_NIGHT.name to true), applied)
    }

    @Test
    fun keepingTheRuleInUseWritesNothing() = runTest {
        val applied = mutableListOf<Pair<String, Boolean>>()
        val chooser = recheck(mwl, CoroutineScope(screen), applied)
        chooser.choose(HighLatitudePreference.SEVENTH_OF_NIGHT.name)
        checked()
        assertNotNull(chooser.warning)
        chooser.keep()
        assertTrue(applied.isEmpty())
    }

    @Test
    fun anUnconfirmedMethodTakesTheRuleAtOnce() = runTest {
        val applied = mutableListOf<Pair<String, Boolean>>()
        val chooser = recheck(mwl.copy(timetableConfirmed = false), CoroutineScope(screen), applied)
        chooser.choose(HighLatitudePreference.SEVENTH_OF_NIGHT.name)
        checked()
        assertEquals(listOf(HighLatitudePreference.SEVENTH_OF_NIGHT.name to false), applied)
    }
}
