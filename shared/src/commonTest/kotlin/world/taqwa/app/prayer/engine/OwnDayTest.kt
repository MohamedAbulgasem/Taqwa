package world.taqwa.app.prayer.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Task 12: a timetable's own day, for Settings › Timetable and its earlier-than check. */
class OwnDayTest {

    private val date = LocalDate(2026, 10, 1)

    // At the precision the engine takes places, so its day is the pipeline's at the same point.
    private val london = Place(51.507, -0.128, "Europe/London", "GB")
    private val karachi = Place(24.861, 67.01, "Asia/Karachi", "PK")
    private val oslo = Place(59.913, 10.746, "Europe/Oslo", "NO")

    @Test
    fun automaticsOwnDayIsThePipelinesBeforeAnyAdjustment() {
        val settings = EngineSettings(adjustmentsMinutes = mapOf(Prayer.FAJR to 10))
        val own = PrayerEngine.ownDay(london, date, TimetableChoice.Automatic, settings)
        val resolution = Registry.resolve(london)
        assertEquals(DayPipeline.unended(resolution, date, TimeZone.of(london.zoneId)), own)
    }

    @Test
    fun aChosenEntrysOwnDayIsItsOwnEvenBeforeItIsConfirmed() {
        val own = PrayerEngine.ownDay(london, date, TimetableChoice.Entry("other.mwl"), EngineSettings())
        val mwl = Registry.resolveEntry(Registry.byId("other.mwl")!!, london)
        // In London's own school (Hanafi: its majority's is not known), whatever the timetable.
        assertEquals(DayPipeline.unended(mwl, date, TimeZone.of(london.zoneId), AsrSchool.HANAFI), own)
    }

    @Test
    fun theUsersSchoolAppliesToEveryTimetablesOwnDay() {
        val standard = EngineSettings(school = SchoolChoice.Standard)
        val own = PrayerEngine.ownDay(karachi, date, TimetableChoice.Entry("other.mwl"), standard)!!
        val hanafi = PrayerEngine.ownDay(karachi, date, TimetableChoice.Entry("other.mwl"), EngineSettings())!!
        assertEquals(hanafi.asrOther, own.asr)
    }

    @Test
    fun anEntryThatDoesNotApplyHereHasNoOwnDay() {
        assertNull(PrayerEngine.ownDay(london, date, TimetableChoice.Entry("sa.ummalqura"), EngineSettings()))
        assertNull(PrayerEngine.ownDay(london, date, TimetableChoice.Entry("no.such.entry"), EngineSettings()))
    }

    @Test
    fun anOtherMethodsOwnDayFollowsTheLegacyHighLatitudeRule() {
        val june = LocalDate(2026, 6, 21)
        val mwl = TimetableChoice.Entry("other.mwl")
        val own = PrayerEngine.ownDay(oslo, june, mwl, EngineSettings())
        val seventh = PrayerEngine.ownDay(oslo, june, mwl, EngineSettings(legacyHighLatitude = HighLatRule.Legacy.SEVENTH))
        assertNotEquals(own, seventh)
    }

    @Test
    fun ownDaysNeverEnterTheDayCache() {
        PrayerEngine.clearCache()
        repeat(40) { i ->
            val d = LocalDate.fromEpochDays(date.toEpochDays() + i)
            PrayerEngine.ownDay(london, d, TimetableChoice.Automatic, EngineSettings())
            PrayerEngine.ownDay(london, d, TimetableChoice.Entry("other.isna"), EngineSettings())
        }
        assertEquals(0, PrayerEngine.cachedDays)
    }

    @Test
    fun automaticsOwnDayIsKeptForTheNextCheck() {
        val first = PrayerEngine.ownDay(london, date, TimetableChoice.Automatic, EngineSettings())
        assertSame(first, PrayerEngine.ownDay(london, date, TimetableChoice.Automatic, EngineSettings()))
    }
}
