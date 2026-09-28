package world.taqwa.app.feature.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EarlierThanCheckTest {

    private val date = LocalDate(2026, 10, 1)
    private val utc = TimeZone.UTC

    private fun at(h: Int, m: Int) = date.atTime(h, m).toInstant(utc)

    private val automatic = PrayerDay(
        date = date,
        fajr = at(5, 30), sunrise = at(7, 5), dhuhr = at(12, 55), asr = at(16, 1), asrOther = at(16, 40),
        maghrib = at(18, 42), isha = at(20, 0), sunset = at(18, 41), endOfEating = at(5, 25), imsak = null,
        methodId = "automatic",
    )

    @Test
    fun theSameDayIsNeverEarlier() {
        val tally = EarlierThanTally()
        tally.add(automatic, automatic)
        assertNull(tally.finding())
    }

    @Test
    fun laterStartsAndAnEarlierSunriseAreNeverEarlier() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(fajr = automatic.fajr + 10.minutes, isha = automatic.isha + 20.minutes, sunrise = automatic.sunrise - 3.minutes), automatic)
        assertNull(tally.finding())
    }

    @Test
    fun theStartFurthestAheadOnAnyDayIsNamedWithItsMostMinutes() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(fajr = automatic.fajr - 21.minutes, isha = automatic.isha - 5.minutes), automatic)
        tally.add(automatic.copy(fajr = automatic.fajr - 3.minutes, isha = automatic.isha - 30.minutes), automatic)
        assertEquals(EarlierFinding(CheckedTime.ISHA, 30), tally.finding())
    }

    @Test
    fun equalMinutesNameTheEarlierPrayer() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(maghrib = automatic.maghrib - 8.minutes, fajr = automatic.fajr - 8.minutes), automatic)
        assertEquals(EarlierFinding(CheckedTime.FAJR, 8), tally.finding())
    }

    @Test
    fun theOtherSchoolsAsrIsCheckedAndNamedAsAsr() {
        // "Show both Asr times" puts it under Asr, so an earlier one is as early as any start.
        val tally = EarlierThanTally()
        tally.add(automatic.copy(asrOther = automatic.asrOther - 7.minutes), automatic)
        val finding = tally.finding()
        assertEquals(EarlierFinding(CheckedTime.ASR_OTHER, 7), finding)
        assertEquals(Prayer.ASR, finding!!.time.prayer())
    }

    @Test
    fun theShownAsrOutranksTheOtherSchoolsAtEqualMinutes() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(asr = automatic.asr - 4.minutes, asrOther = automatic.asrOther - 4.minutes), automatic)
        assertEquals(EarlierFinding(CheckedTime.ASR, 4), tally.finding())
    }

    @Test
    fun aLaterSunriseIsFoundWhenNoStartIsEarlier() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(sunrise = automatic.sunrise + 4.minutes, endOfEating = automatic.endOfEating + 9.minutes), automatic)
        assertEquals(EarlierFinding(CheckedTime.SUNRISE, 4), tally.finding())
    }

    @Test
    fun aLaterEndOfEatingIsFoundLast() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(endOfEating = automatic.endOfEating + 10.minutes), automatic)
        assertEquals(EarlierFinding(CheckedTime.END_OF_EATING, 10), tally.finding())
    }

    @Test
    fun anEarlierStartOutranksALaterSunrise() {
        val tally = EarlierThanTally()
        tally.add(automatic.copy(sunrise = automatic.sunrise + 40.minutes, dhuhr = automatic.dhuhr - 1.minutes), automatic)
        assertEquals(EarlierFinding(CheckedTime.DHUHR, 1), tally.finding())
    }

    @Test
    fun theCheckReadsEveryDayOfTheNextTwelveMonths() = runTest {
        val seen = mutableListOf<LocalDate>()
        val progress = mutableListOf<Float>()
        val result = checkAgainstAutomatic(date, { d -> seen += d; automatic to automatic }, { progress += it })
        assertEquals(TimetableCheck.NeverEarlier, result)
        assertEquals(365, seen.size)
        assertEquals(date, seen.first())
        assertEquals(LocalDate(2027, 9, 30), seen.last())
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun oneEarlierDayInTheYearIsEnough() = runTest {
        val late = LocalDate(2027, 6, 21)
        val result = checkAgainstAutomatic(date, { d -> (if (d == late) automatic.copy(isha = automatic.isha - 12.minutes) else automatic) to automatic })
        assertEquals(TimetableCheck.Earlier(EarlierFinding(CheckedTime.ISHA, 12)), result)
    }

    @Test
    fun theOtherSchoolsAsrIsFoundUnderTheSchoolTheCallerNames() = runTest {
        // "Its Hanafi Asr begins 7 minutes before…": the warning needs the school, not only "Asr".
        val result = checkAgainstAutomatic(
            date,
            { automatic.copy(asrOther = automatic.asrOther - 7.minutes) to automatic },
            otherSchool = AsrSchool.HANAFI,
        )
        assertEquals(TimetableCheck.Earlier(EarlierFinding(CheckedTime.ASR_OTHER, 7, AsrSchool.HANAFI)), result)
    }

    @Test
    fun onlyTheOtherSchoolsAsrCarriesASchool() = runTest {
        val result = checkAgainstAutomatic(
            date,
            { automatic.copy(isha = automatic.isha - 12.minutes) to automatic },
            otherSchool = AsrSchool.STANDARD,
        )
        assertEquals(TimetableCheck.Earlier(EarlierFinding(CheckedTime.ISHA, 12)), result)
    }

    @Test
    fun anEntryThatDoesNotApplyIsNotHere() = runTest {
        assertEquals(TimetableCheck.NotHere, checkAgainstAutomatic(date, { null }))
    }

    @Test
    fun leavingTheScreenStopsTheCheck() = runTest {
        var days = 0
        val job = async {
            checkAgainstAutomatic(date, { days++; automatic to automatic }, { if (days > 30) throw CancellationException("left") })
        }
        assertFailsWith<CancellationException> { job.await() }
        assertTrue(days < 60, "read $days days after it was stopped")
    }

    // The engine's own days (ruling R52's check), at the places the Task 8 tests pin.

    private val manchester = Place(53.48095, -2.23743, "Europe/London", "GB")
    private val istanbul = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
    private val london = Place(51.5074, -0.1278, "Europe/London", "GB")

    private suspend fun check(place: Place, id: String): TimetableCheck {
        val settings = EngineSettings()
        return checkAgainstAutomatic(date, { d ->
            val own = PrayerEngine.ownDay(place, d, TimetableChoice.Entry(id), settings)
            val auto = PrayerEngine.ownDay(place, d, TimetableChoice.Automatic, settings)
            if (own == null || auto == null) null else own to auto
        })
    }

    @Test
    fun isnaInManchesterBeginsBeforeTheCautiousTimesOnSomeDays() = runTest {
        val result = check(manchester, "other.isna")
        assertIs<TimetableCheck.Earlier>(result)
        assertTrue(result.finding.minutes > 0)
        assertTrue(result.finding.time.prayer() != null, "a start is named: ${result.finding}")
    }

    @Test
    fun automaticsOwnEntryChosenByNameIsNeverEarlier() = runTest {
        assertEquals(TimetableCheck.NeverEarlier, check(istanbul, "tr.diyanet"))
    }

    @Test
    fun aTimetableThatDoesNotApplyHereIsNotHere() = runTest {
        assertEquals(TimetableCheck.NotHere, check(london, "sa.ummalqura"))
        assertEquals(TimetableCheck.NotHere, check(london, "no.such.entry"))
    }
}
