package world.taqwa.app.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdhanApiProbeTest {
    @Test
    fun adhanReturnsOrderedInstantsForLondon() {
        val times = PrayerTimes(
            coordinates = Coordinates(51.5074, -0.1278),
            dateComponents = DateComponents(2026, 9, 6),
            calculationParameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters,
        )
        assertTrue(times.fajr < times.sunrise)
        assertTrue(times.sunrise < times.dhuhr)
        assertTrue(times.dhuhr < times.asr)
        assertTrue(times.asr < times.maghrib)
        assertTrue(times.maghrib < times.isha)
    }
}

class PrayerTimesEngineTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")

    private fun localHourMinute(instant: kotlin.time.Instant, zone: String): Pair<Int, Int> {
        val t = instant.toLocalDateTime(TimeZone.of(zone))
        return t.hour to t.minute
    }

    @Test
    fun timesAreStrictlyOrderedThroughTheDay() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        val instants = d.times.map { it.instant }
        assertEquals(instants.sorted(), instants)
    }

    @Test
    fun dhuhrIsNearSolarNoonInLondon() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        val (h, _) = localHourMinute(d.time(Prayer.DHUHR), "Europe/London")
        assertTrue(h in 12..13, "Dhuhr fell at hour $h, expected 12 or 13 local")
    }

    @Test
    fun hanafiAsrIsLaterThanStandardAsr() {
        val date = LocalDate(2026, 9, 6)
        val standard = engine.timesFor(london, date, PrayerSettings(madhab = AsrMadhab.STANDARD))
        val hanafi = engine.timesFor(london, date, PrayerSettings(madhab = AsrMadhab.HANAFI))
        assertTrue(hanafi.time(Prayer.ASR) > standard.time(Prayer.ASR))
    }

    @Test
    fun minuteAdjustmentsShiftOnlyTheNamedPrayer() {
        val date = LocalDate(2026, 9, 6)
        val base = engine.timesFor(london, date, PrayerSettings())
        val shifted = engine.timesFor(
            london, date, PrayerSettings(minuteAdjustments = mapOf(Prayer.FAJR to 5)),
        )
        assertEquals(base.time(Prayer.FAJR).epochSeconds + 300, shifted.time(Prayer.FAJR).epochSeconds)
        assertEquals(base.time(Prayer.ISHA), shifted.time(Prayer.ISHA))
    }

    @Test
    fun southernHemisphereAndDateLineDoNotBreakOrdering() {
        val auckland = GeoLocation(-36.8485, 174.7633, "Pacific/Auckland", "Auckland", "NZ")
        val d = engine.timesFor(auckland, LocalDate(2026, 1, 15), PrayerSettings())
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
    }

    @Test
    fun tromsoInJuneStillProducesOrderedTimesAndReportsItsRule() {
        val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
        val d = engine.timesFor(tromso, LocalDate(2026, 6, 21), PrayerSettings())
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
        assertEquals(
            world.taqwa.app.domain.HighLatitudePreference.TWILIGHT_ANGLE,
            d.highLatitudeRuleApplied,
        )
    }

    @Test
    fun tromsoAtSummerSolsticeReportsNearestLatitudeFallback() {
        val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
        val d = engine.timesFor(tromso, LocalDate(2026, 6, 21), PrayerSettings())
        assertTrue(
            d.nearestLatitudeFallbackApplied,
            "Expected true polar day at Tromsø on the summer solstice to trigger the nearest-latitude fallback",
        )
    }

    @Test
    fun ordinaryLondonDayDoesNotReportNearestLatitudeFallback() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        assertEquals(false, d.nearestLatitudeFallbackApplied)
    }

    @Test
    fun tromsoAtEquinoxHasARealSunriseAndDoesNotReportFallback() {
        val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
        val d = engine.timesFor(tromso, LocalDate(2026, 9, 23), PrayerSettings())
        assertEquals(false, d.nearestLatitudeFallbackApplied)
    }

    @Test
    fun londonInSeptemberGenuinelyEngagesTheSeventhOfNightRuleUnderMwl() {
        // Investigated directly against adhan2 (all three HighLatitudeRule values, several
        // latitudes, every month of 2026) rather than assumed: at London's automatically-selected
        // SEVENTH_OF_NIGHT rule, the one-seventh-of-the-night bound genuinely moves both Fajr
        // (03:19 -> 03:49 UTC) and Isha (20:29 -> 20:08 UTC) on 2026-09-06 under the default
        // Muslim World League method — MIDDLE_OF_THE_NIGHT and TWILIGHT_ANGLE both agree on the
        // unclamped 03:19/20:29, so the substitution is real, not a no-op. The same seventh-of-
        // night divergence appears every year from roughly March to September at this latitude
        // (verified at 40 degrees and even, by a single minute, at 25 degrees near the June
        // solstice) — it tracks night *length*, not proximity to a pole. So the exact screenshot
        // date does not go quiet on its own; what the fix actually buys is that the note now only
        // appears when a rule change is real (see the November/January tests below), instead of
        // unconditionally for any location north of the 48-degree threshold as it did before.
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        assertEquals(HighLatitudePreference.SEVENTH_OF_NIGHT, d.highLatitudeRuleApplied)
    }

    @Test
    fun anExplicitlyChosenRuleIsStillReportedWhenItEngages() {
        // Same London date as the test above, where the seventh-of-night bound genuinely moves
        // Fajr and Isha — but with the rule picked by hand rather than resolved automatically.
        // The note used to be suppressed for exactly this user, who had shown they care which
        // rule is in force and was the one person never told it was changing their Fajr.
        val chosen = PrayerSettings(highLatitude = HighLatitudePreference.SEVENTH_OF_NIGHT)
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), chosen)
        assertEquals(HighLatitudePreference.SEVENTH_OF_NIGHT, d.highLatitudeRuleApplied)
    }

    @Test
    fun anExplicitlyChosenRuleIsStillSilentWhenNothingBinds() {
        val chosen = PrayerSettings(highLatitude = HighLatitudePreference.TWILIGHT_ANGLE)
        val d = engine.timesFor(london, LocalDate(2026, 11, 15), chosen)
        assertEquals(null, d.highLatitudeRuleApplied)
    }

    @Test
    fun repeatedCallsWithTheSameInputsAgreeWithTheFirst() {
        // The engaged-rule answer is memoised per (date, settings, location); the memo must not
        // leak an answer across a change of any of the three.
        val settings = PrayerSettings()
        val september = engine.timesFor(london, LocalDate(2026, 9, 6), settings)
        val november = engine.timesFor(london, LocalDate(2026, 11, 15), settings)
        val septemberAgain = engine.timesFor(london, LocalDate(2026, 9, 6), settings)
        assertEquals(HighLatitudePreference.SEVENTH_OF_NIGHT, september.highLatitudeRuleApplied)
        assertEquals(null, november.highLatitudeRuleApplied)
        assertEquals(september.highLatitudeRuleApplied, septemberAgain.highLatitudeRuleApplied)
    }

    @Test
    fun londonInNovemberHasNoHighLatitudeNoteBecauseNoRuleActuallyBinds() {
        // An ordinary autumn night: long enough that the raw angle-based Fajr and Isha already
        // sit inside every rule's bound, so all three HighLatitudeRule values agree and the note
        // correctly disappears — this is the behaviour the fix is actually for.
        val d = engine.timesFor(london, LocalDate(2026, 11, 15), PrayerSettings())
        assertEquals(null, d.highLatitudeRuleApplied)
    }

    @Test
    fun londonInDecemberAlsoHasNoHighLatitudeNoteBecauseWinterNightsAreLong() {
        // Investigated directly: contrary to the assumption that a London winter would engage the
        // rule, 2026-12-21 (and every mid-winter date checked) has all three HighLatitudeRule
        // values agreeing exactly (05:59/17:51 UTC for Fajr/Isha) — winter nights here are long
        // enough that the seventh-of-night bound never binds. The genuine divergence window is
        // the *shorter*-night half of the year (see the September test above), not the longer-
        // night half, which is the opposite of what the bug report assumed but consistent with
        // why high-latitude substitution rules exist in the first place (short nights, not long
        // ones, are what leave too little room for a full angle-based twilight).
        val d = engine.timesFor(london, LocalDate(2026, 12, 21), PrayerSettings())
        assertEquals(null, d.highLatitudeRuleApplied)
    }
}
