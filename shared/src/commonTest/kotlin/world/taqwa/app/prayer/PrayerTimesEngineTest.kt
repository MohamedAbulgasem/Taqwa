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
}
