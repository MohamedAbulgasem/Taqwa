package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Task 12's writers: a timetable with its confirmation, and one prayer's adjustment with its own. */
class TimetableChoiceStorageTest {

    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-t12-$name-${Random.nextULong()}.preferences_pb".toPath() },
    )

    @Test
    fun aConfirmedChoiceIsStoredWithItsConfirmation() = runTest {
        val r = repo("choose-confirmed")
        r.chooseTimetable("other.isna", confirmedUnder = "gb.london.lupt")
        val s = r.prayerSettings.first()
        assertEquals("other.isna", s.timetable)
        assertEquals(true, s.timetableConfirmed)
        assertEquals("gb.london.lupt", s.timetableConfirmedUnder)
    }

    @Test
    fun aConfirmationStoredWithoutItsAutomaticWaitsToBeConfirmedAgain() = runTest {
        // Ruling R70, migration: a confirmation written before R70 has no Automatic entry beside it.
        val store = PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-t12-legacy-${Random.nextULong()}.preferences_pb".toPath() }
        val r = SettingsRepository(store)
        r.setTimetable("other.mwl")
        store.edit { it[SettingsKeys.PRAYER_TIMETABLE_CONFIRMED] = "other.mwl" }
        val s = r.prayerSettings.first()
        assertEquals(false, s.timetableConfirmed)
        assertEquals(null, s.timetableConfirmedUnder)
        r.setTimetableConfirmed("other.mwl", "tr.diyanet")
        assertEquals(true, r.prayerSettings.first().timetableConfirmed)
        assertEquals("tr.diyanet", r.prayerSettings.first().timetableConfirmedUnder)
    }

    @Test
    fun anOtherMethodConfirmedInLondonIsPausedInIstanbul() = runTest {
        // Ruling R70: the confirmation is bound to London's Automatic entry and does not travel.
        val engine = PrayerTimesEngine()
        val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
        val istanbul = GeoLocation(41.0082, 28.9784, "Europe/Istanbul", "İstanbul", "TR")
        val date = LocalDate(2026, 9, 26)
        val r = repo("choose-abroad")
        r.chooseTimetable("other.mwl", confirmedUnder = engine.automaticEntryId(london))
        val s = r.prayerSettings.first()
        assertEquals("gb.london.lupt", s.timetableConfirmedUnder)
        assertFalse(engine.dayFor(london, date, s).timetablePaused)
        val abroad = engine.dayFor(istanbul, date, s)
        assertTrue(abroad.timetablePaused)
        assertEquals("other.mwl", abroad.effectiveEntry.id)
        // Held to Automatic exactly as an unconfirmed choice is (ruling R52).
        assertEquals(engine.dayFor(istanbul, date, s.copy(timetableConfirmed = false, timetableConfirmedUnder = null)).day, abroad.day)
        // Confirmed again in İstanbul: its own there, and now paused back in London.
        r.chooseTimetable("other.mwl", confirmedUnder = engine.automaticEntryId(istanbul))
        val again = r.prayerSettings.first()
        assertFalse(engine.dayFor(istanbul, date, again).timetablePaused)
        assertTrue(engine.dayFor(london, date, again).timetablePaused)
    }

    @Test
    fun anUnconfirmedChoiceDropsTheOldConfirmation() = runTest {
        val r = repo("choose-unconfirmed")
        r.chooseTimetable("other.isna", confirmedUnder = "gb.london.lupt")
        r.chooseTimetable("other.mwl", confirmedUnder = null)
        assertEquals(false, r.prayerSettings.first().timetableConfirmed)
        // Coming back to ISNA needs its own confirmation again: the old one is gone.
        r.setTimetable("other.isna")
        assertEquals(false, r.prayerSettings.first().timetableConfirmed)
    }

    @Test
    fun choosingAutomaticNeedsNoConfirmation() = runTest {
        val r = repo("choose-automatic")
        r.chooseTimetable("other.isna", confirmedUnder = "gb.london.lupt")
        r.chooseTimetable("automatic", confirmedUnder = null)
        val s = r.prayerSettings.first()
        assertEquals("automatic", s.timetable)
        assertEquals(false, s.timetableConfirmed)
    }

    @Test
    fun anEarlierAdjustmentIsStoredWithTheTimetableItWasConfirmedUnder() = runTest {
        val r = repo("adjust-confirmed")
        r.setMinuteAdjustment(Prayer.MAGHRIB, -2, confirmedUnder = "tr.diyanet")
        val s = r.prayerSettings.first()
        assertEquals(mapOf(Prayer.MAGHRIB to -2), s.minuteAdjustments)
        assertEquals(mapOf(Prayer.MAGHRIB to "tr.diyanet"), s.confirmedAdjustments)
    }

    @Test
    fun anAdjustmentWithoutAConfirmationKeepsTheOneStored() = runTest {
        val r = repo("adjust-keeps")
        r.setMinuteAdjustment(Prayer.MAGHRIB, -1, confirmedUnder = "tr.diyanet")
        r.setMinuteAdjustment(Prayer.MAGHRIB, -3, confirmedUnder = null)
        r.setMinuteAdjustment(Prayer.ISHA, 5, confirmedUnder = null)
        val s = r.prayerSettings.first()
        assertEquals(mapOf(Prayer.MAGHRIB to -3, Prayer.ISHA to 5), s.minuteAdjustments)
        assertEquals(mapOf(Prayer.MAGHRIB to "tr.diyanet"), s.confirmedAdjustments)
    }

    @Test
    fun zeroRemovesThePrayersAdjustment() = runTest {
        val r = repo("adjust-zero")
        r.setMinuteAdjustment(Prayer.FAJR, 4, confirmedUnder = null)
        r.setMinuteAdjustment(Prayer.FAJR, 0, confirmedUnder = null)
        assertEquals(emptyMap(), r.prayerSettings.first().minuteAdjustments)
    }
}
