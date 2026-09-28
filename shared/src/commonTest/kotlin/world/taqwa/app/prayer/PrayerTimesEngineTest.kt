package world.taqwa.app.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.SchoolChoice
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** adhan2 stays for the Qibla bearing; its API is still what the probe expects. */
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

/** The app's door to the engine: the stored location and settings in, the day the screens read out. */
class PrayerTimesEngineTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val istanbul = GeoLocation(41.0082, 28.9784, "Europe/Istanbul", "Istanbul", "TR")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
    private val longyearbyen = GeoLocation(78.2232, 15.6267, "Europe/Oslo", "Longyearbyen", "SJ")

    @Test
    fun timesAreStrictlyOrderedThroughTheDay() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        val instants = d.times.map { it.instant }
        assertEquals(instants.sorted(), instants)
        assertEquals(Prayer.entries, d.times.map { it.prayer })
    }

    @Test
    fun dhuhrIsNearSolarNoonInLondon() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        val h = d.time(Prayer.DHUHR).toLocalDateTime(TimeZone.of("Europe/London")).hour
        assertTrue(h in 12..13, "Dhuhr fell at hour $h, expected 12 or 13 local")
    }

    @Test
    fun theTimesAreTheEnginesForTheSameSettings() {
        val date = LocalDate(2026, 9, 26)
        val d = engine.timesFor(istanbul, date, PrayerSettings())
        val day = PrayerEngine.dayTimes(Place(41.0082, 28.9784, "Europe/Istanbul", "TR"), date, EngineSettings()).day
        assertEquals(listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha), d.times.map { it.instant })
        assertEquals(day.asrOther, d.asrOther)
        assertEquals(day.sunset, d.sunset)
        assertEquals(day.endOfEating, d.endOfEating)
        assertEquals(day.ends, d.ends)
        assertEquals("tr.diyanet", d.sourceEntryId)
        assertEquals(EntryClass.A, d.entryClass)
    }

    @Test
    fun theStoredSettingsMapOntoTheEngines() {
        val settings = PrayerSettings(
            timetable = "other.mwl",
            timetableConfirmed = true,
            school = "hanafi",
            saudiFajrLater = true,
            minuteAdjustments = mapOf(Prayer.FAJR to 2, Prayer.ISHA to 0),
            confirmedAdjustments = mapOf(Prayer.MAGHRIB to "other.mwl"),
            legacyHighLatitude = HighLatitudePreference.SEVENTH_OF_NIGHT,
            hijriOffsetDays = 1,
        )
        assertEquals(
            EngineSettings(
                timetable = TimetableChoice.Entry("other.mwl"),
                timetableConfirmed = true,
                school = SchoolChoice.Hanafi,
                saudiFajrLater = true,
                legacyHighLatitude = HighLatRule.Legacy.SEVENTH,
                adjustmentsMinutes = mapOf(Prayer.FAJR to 2),
                confirmedAdjustments = mapOf(Prayer.MAGHRIB to "other.mwl"),
                hijriOffsetDays = 1,
            ),
            PrayerTimesEngine.engineSettingsOf(settings),
        )
        assertEquals(EngineSettings(), PrayerTimesEngine.engineSettingsOf(PrayerSettings()))
        assertNull(PrayerTimesEngine.engineSettingsOf(PrayerSettings(legacyHighLatitude = HighLatitudePreference.AUTOMATIC)).legacyHighLatitude)
    }

    @Test
    fun aHanafiSchoolGivesALaterAsr() {
        val date = LocalDate(2026, 9, 6)
        val standard = engine.timesFor(istanbul, date, PrayerSettings(school = "standard"))
        val hanafi = engine.timesFor(istanbul, date, PrayerSettings(school = "hanafi"))
        assertTrue(hanafi.time(Prayer.ASR) > standard.time(Prayer.ASR))
        assertEquals(hanafi.time(Prayer.ASR), standard.asrOther)
    }

    @Test
    fun aLaterMinuteAdjustmentShiftsOnlyTheNamedPrayer() {
        val date = LocalDate(2026, 9, 6)
        val base = engine.timesFor(london, date, PrayerSettings())
        val shifted = engine.timesFor(london, date, PrayerSettings(minuteAdjustments = mapOf(Prayer.FAJR to 5)))
        assertEquals(base.time(Prayer.FAJR) + 5.minutes, shifted.time(Prayer.FAJR))
        assertEquals(base.time(Prayer.ISHA), shifted.time(Prayer.ISHA))
    }

    @Test
    fun anUnconfirmedEarlierAdjustmentIsNotApplied() {
        val date = LocalDate(2026, 9, 6)
        val base = engine.timesFor(london, date, PrayerSettings())
        val earlier = engine.timesFor(london, date, PrayerSettings(minuteAdjustments = mapOf(Prayer.MAGHRIB to -3)))
        assertEquals(base.time(Prayer.MAGHRIB), earlier.time(Prayer.MAGHRIB))
    }

    @Test
    fun aMigratedIsnaInTheUkIsNeverEarlierThanItsCautiousTimesUntilConfirmed() {
        // Spec §8: the migration keeps a chosen ISNA but never confirms it (ruling R52).
        val manchester = GeoLocation(53.48095, -2.23743, "Europe/London", "Manchester", "GB")
        val migrated = PrayerSettings(timetable = "other.isna")
        var date = LocalDate(2026, 1, 3)
        repeat(52) {
            val automatic = engine.timesFor(manchester, date, PrayerSettings())
            val isna = engine.timesFor(manchester, date, migrated)
            assertEquals("gb.cautious", automatic.sourceEntryId)
            assertEquals("other.isna", isna.sourceEntryId)
            for (prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
                assertTrue(isna.time(prayer) >= automatic.time(prayer), "$prayer on $date")
            }
            assertTrue(isna.time(Prayer.SUNRISE) <= automatic.time(Prayer.SUNRISE), "sunrise on $date")
            assertTrue(isna.endOfEating <= automatic.endOfEating, "end of eating on $date")
            date = date.plus(7, DateTimeUnit.DAY)
        }
    }

    @Test
    fun southernHemisphereAndDateLineDoNotBreakOrdering() {
        val auckland = GeoLocation(-36.8485, 174.7633, "Pacific/Auckland", "Auckland", "NZ")
        val d = engine.timesFor(auckland, LocalDate(2026, 1, 15), PrayerSettings())
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
    }

    @Test
    fun apiaKeepsEveryTimeOnItsOwnCivilDate() {
        val apia = GeoLocation(-13.83333, -171.76666, "Pacific/Apia", "Apia", "WS")
        val date = LocalDate(2026, 9, 25)
        val d = engine.timesFor(apia, date, PrayerSettings())
        val zone = TimeZone.of("Pacific/Apia")
        for (prayer in listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB)) {
            assertEquals(date, d.time(prayer).toLocalDateTime(zone).date, "$prayer")
        }
    }

    @Test
    fun aPolarDayReportsTheNearestLatitudeFlag() {
        val d = engine.timesFor(longyearbyen, LocalDate(2026, 6, 21), PrayerSettings())
        assertTrue(d.polar)
        assertTrue(d.nearestLatitudeFallbackApplied)
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
    }

    @Test
    fun aDayIrnsRuleGivesWholeFollowsNoNearestLatitude() {
        // Ruling R82: under Tromsø's midnight sun every time is IRN's Makkah time.
        val d = engine.timesFor(tromso, LocalDate(2026, 6, 21), PrayerSettings())
        assertEquals(false, d.polar)
        assertEquals(false, d.nearestLatitudeFallbackApplied)
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
    }

    @Test
    fun anOrdinaryDayReportsNoRuleAndNoPolarDay() {
        val d = engine.timesFor(london, LocalDate(2026, 11, 15), PrayerSettings())
        assertEquals(false, d.polar)
        assertEquals(false, d.nearestLatitudeFallbackApplied)
        assertEquals(emptySet(), d.setByRule)
        assertNull(d.highLatitudeRuleApplied)
    }

    @Test
    fun aRuleThatSetsFajrOrIshaIsReportedAsTheEnginesOwnOrTheLegacyOne() {
        // Oslo in June: the 18° dawn does not come, so the engine's own rule sets Fajr and Isha.
        val oslo = GeoLocation(59.91273, 10.74609, "Europe/Oslo", "Oslo", "NO")
        val june = LocalDate(2026, 6, 21)
        val mwl = PrayerSettings(timetable = "other.mwl", timetableConfirmed = true)
        val own = engine.timesFor(oslo, june, mwl)
        assertTrue(Prayer.FAJR in own.setByRule || Prayer.ISHA in own.setByRule, "${own.setByRule}")
        assertEquals(HighLatitudePreference.AUTOMATIC, own.highLatitudeRuleApplied)
        val seventh = engine.timesFor(oslo, june, mwl.copy(legacyHighLatitude = HighLatitudePreference.SEVENTH_OF_NIGHT))
        assertEquals(HighLatitudePreference.SEVENTH_OF_NIGHT, seventh.highLatitudeRuleApplied)
    }

    @Test
    fun aLocationWithNoCountryTakesItsCountryFromTheZone() {
        val noCountry = istanbul.copy(countryCode = null)
        assertEquals("TR", PrayerTimesEngine.placeOf(noCountry).countryCode)
        assertEquals("tr.diyanet", engine.timesFor(noCountry, LocalDate(2026, 9, 26), PrayerSettings()).sourceEntryId)
        // A zone the city list does not name: the safe default, never a crash.
        val nowhere = GeoLocation(0.0, -160.0, "UTC")
        assertEquals("", PrayerTimesEngine.placeOf(nowhere).countryCode)
        assertEquals("default.safe", engine.timesFor(nowhere, LocalDate(2026, 9, 26), PrayerSettings()).sourceEntryId)
    }

    @Test
    fun theRegionTravelsAsTheFirstLevelRegion() {
        val algiers = GeoLocation(36.73225, 3.08746, "Africa/Algiers", "Algiers", "DZ", region = "Algiers")
        assertEquals("Algiers", PrayerTimesEngine.placeOf(algiers).admin1)
    }
}
