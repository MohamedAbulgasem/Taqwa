package world.taqwa.app.notifications

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class NotificationPlannerTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val newYork = GeoLocation(40.7128, -74.0060, "America/New_York", "New York", "US")
    private val defaults = NotificationSettings()

    private fun planFor(
        location: GeoLocation = london,
        notifications: NotificationSettings = defaults,
        from: Instant = Instant.parse("2026-09-06T00:30:00Z"),
        windowDays: Int = 12,
        capacity: Int = 64,
    ) = NotificationPlanner.plan(
        location = location,
        settings = PrayerSettings(),
        notifications = notifications,
        engine = engine,
        from = from,
        windowDays = windowDays,
        capacity = capacity,
    )

    @Test
    fun sixtyFourSlotsAtFiveAPrayerDayIsATwelveDayWindow() {
        assertEquals(12, NotificationPlanner.windowDaysFor(64, defaults))
    }

    @Test
    fun turningOnRemindersHalvesTheWindowBecauseEachPrayerCostsTwoSlots() {
        assertEquals(6, NotificationPlanner.windowDaysFor(64, defaults.copy(remindBeforeMinutes = 10)))
    }

    @Test
    fun aTwelveDayWindowProducesSixtyPrayerNotifications() {
        assertEquals(60, planFor().size)
    }

    @Test
    fun theCapIsNeverExceededHoweverLongTheWindow() {
        val plan = planFor(windowDays = 30)
        assertEquals(64, plan.size)
    }

    @Test
    fun thePlanIsSortedSoTheCapKeepsTheSoonest() {
        val plan = planFor(windowDays = 30)
        assertEquals(plan.map { it.instant }.sorted(), plan.map { it.instant })
    }

    @Test
    fun nothingIsScheduledInThePast() {
        val from = Instant.parse("2026-09-06T18:00:00Z")
        assertTrue(planFor(from = from).all { it.instant > from })
    }

    @Test
    fun sunriseIsNeverNotified() {
        assertTrue(planFor().none { it.prayer == Prayer.SUNRISE })
    }

    @Test
    fun disablingNotificationsProducesAnEmptyPlan() {
        assertTrue(planFor(notifications = defaults.copy(enabled = false)).isEmpty())
    }

    @Test
    fun eachEntryCarriesThatPrayersOwnSound() {
        val plan = planFor(
            notifications = defaults.copy(
                sounds = defaults.sounds + mapOf(
                    Prayer.FAJR to PrayerSound.ADHAN,
                    Prayer.ISHA to PrayerSound.SILENT,
                ),
            ),
        )
        assertTrue(plan.filter { it.prayer == Prayer.FAJR }.all { it.sound == PrayerSound.ADHAN })
        assertTrue(plan.filter { it.prayer == Prayer.ISHA }.all { it.sound == PrayerSound.SILENT })
        assertTrue(plan.filter { it.prayer == Prayer.ASR }.all { it.sound == PrayerSound.TAKBIR })
    }

    @Test
    fun remindersLandBeforeTheirPrayerAndUseTheDefaultToneNotTheAdhan() {
        val plan = planFor(notifications = defaults.copy(remindBeforeMinutes = 10), windowDays = 1)
        val reminders = plan.filter { it.kind == NotificationKind.REMINDER }
        assertTrue(reminders.isNotEmpty())
        assertTrue(reminders.all { it.sound == PrayerSound.NOTIFICATION })
        val fajr = plan.first { it.prayer == Prayer.FAJR && it.kind == NotificationKind.PRAYER }
        val fajrReminder = plan.first { it.prayer == Prayer.FAJR && it.kind == NotificationKind.REMINDER }
        assertEquals(600L, fajr.instant.epochSeconds - fajrReminder.instant.epochSeconds)
    }

    @Test
    fun neverMeansNoRemindersAtAll() {
        assertTrue(planFor().none { it.kind == NotificationKind.REMINDER })
    }

    @Test
    fun springForwardStillYieldsFiveDistinctPrayersOnTheShortDay() {
        // 2027-03-14 is the US spring-forward date: 02:00 becomes 03:00.
        val plan = NotificationPlanner.plan(
            location = newYork, settings = PrayerSettings(), notifications = defaults,
            engine = engine, from = Instant.parse("2027-03-14T05:00:00Z"),
            windowDays = 1, capacity = 64,
        )
        val zone = TimeZone.of("America/New_York")
        val onTheDay = plan.filter { it.instant.toLocalDateTime(zone).date.dayOfMonth == 14 }
        assertEquals(5, onTheDay.size)
        assertEquals(5, onTheDay.map { it.instant }.toSet().size)
    }

    @Test
    fun fallBackStillYieldsFiveDistinctPrayersOnTheLongDay() {
        // 2026-11-01 is the US fall-back date: 02:00 happens twice.
        val plan = NotificationPlanner.plan(
            location = newYork, settings = PrayerSettings(), notifications = defaults,
            engine = engine, from = Instant.parse("2026-11-01T04:00:00Z"),
            windowDays = 1, capacity = 64,
        )
        val zone = TimeZone.of("America/New_York")
        val onTheDay = plan.filter { it.instant.toLocalDateTime(zone).date.dayOfMonth == 1 }
        assertEquals(5, onTheDay.size)
        assertEquals(5, onTheDay.map { it.instant }.toSet().size)
    }

    @Test
    fun idsAreUniqueWithinAPlanSoNoEntryOverwritesAnother() {
        val plan = planFor(notifications = defaults.copy(remindBeforeMinutes = 15), windowDays = 6)
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test
    fun idsAreStableAcrossTwoIdenticalPlansSoReschedulingIsIdempotent() {
        assertEquals(planFor().map { it.id }, planFor().map { it.id })
    }

    @Test
    fun everyEntryCarriesTheLocationsTimeZoneForTheCalendarTrigger() {
        assertTrue(planFor().all { it.timeZoneId == "Europe/London" })
    }

    @Test
    fun copyNamesThePrayerAndItsClockTime() {
        val fajr = planFor().first { it.prayer == Prayer.FAJR }
        assertEquals("Fajr", fajr.title)
        assertTrue(fajr.body.contains(":"), "body should carry a clock time, was '${fajr.body}'")
    }

    @Test
    fun aSilentPrayerIsStillScheduledBecauseTheBannerStillShows() {
        val plan = planFor(notifications = defaults.copy(
            sounds = defaults.sounds + (Prayer.ISHA to PrayerSound.SILENT),
        ))
        assertFalse(plan.none { it.prayer == Prayer.ISHA })
    }
}
