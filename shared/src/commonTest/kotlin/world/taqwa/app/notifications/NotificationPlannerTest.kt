package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.AdhanVoice
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
    fun aReminderIsClampedSoItNeverLandsBeforeThePreviousPrayer() {
        // London's natural Maghrib->Isha gap on this date is about 90 minutes; manual adjustments
        // pull it under 30, closer than the longest lead the UI offers. High latitude in summer
        // produces the same shape naturally, but this way the case is exact and season-independent.
        // The earlier Isha is confirmed under London's own timetable, or the engine would pause it.
        val date = kotlinx.datetime.LocalDate(2026, 9, 6)
        val londonEntry = engine.dayFor(london, date, PrayerSettings()).effectiveEntry.id
        val squeezed = PrayerSettings(
            minuteAdjustments = mapOf(Prayer.MAGHRIB to 35, Prayer.ISHA to -35),
            confirmedAdjustments = mapOf(Prayer.ISHA to londonEntry),
        )
        val times = engine.timesFor(london, date, squeezed)
        val maghrib = times.time(Prayer.MAGHRIB)
        val isha = times.time(Prayer.ISHA)
        // Precondition: without this the assertion below would pass for the wrong reason.
        assertTrue(isha - maghrib < 30.minutes, "gap was ${isha - maghrib}")

        val plan = NotificationPlanner.plan(
            location = london,
            settings = squeezed,
            notifications = defaults.copy(remindBeforeMinutes = 30),
            engine = engine,
            from = Instant.parse("2026-09-06T00:30:00Z"),
            windowDays = 1,
            capacity = 64,
        )
        val reminder = plan.single {
            it.prayer == Prayer.ISHA && it.kind == NotificationKind.REMINDER
        }
        assertEquals(maghrib, reminder.instant)
        assertTrue(reminder.instant >= maghrib, "reminder for Isha preceded Maghrib")
    }

    @Test
    fun noReminderInAWholePlanEverPrecedesThePrayerBeforeIt() {
        val plan = planFor(notifications = defaults.copy(remindBeforeMinutes = 30))
        val prayers = plan.filter { it.kind == NotificationKind.PRAYER }.sortedBy { it.instant }
        plan.filter { it.kind == NotificationKind.REMINDER }.forEach { reminder ->
            val preceding = prayers.lastOrNull { it.instant < reminder.instant }
            val following = prayers.first { it.prayer == reminder.prayer && it.instant >= reminder.instant }
            assertTrue(
                preceding == null || preceding.instant <= reminder.instant,
                "${reminder.id} landed before ${preceding?.id}",
            )
            assertTrue(reminder.instant <= following.instant, reminder.id)
        }
    }

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
    fun aRescheduleAfterMidnightKeepsYesterdaysIshaWhenItFallsAfterMidnight() {
        // Spec §3.3: in a high-latitude summer the day's Isha falls after local midnight (Helsinki
        // on 21 June 2026 near 00:22, Edmonton on 27 June near 00:06). A reschedule between
        // midnight and that Isha (the app opened, a settings change, a boot) must keep its adhan
        // and its reminder, under the ids they were first armed with.
        val helsinki = GeoLocation(60.1699, 24.9384, "Europe/Helsinki", "Helsinki", "FI")
        val edmonton = GeoLocation(53.5461, -113.4938, "America/Edmonton", "Edmonton", "CA")
        for ((location, date) in listOf(helsinki to LocalDate(2026, 6, 21), edmonton to LocalDate(2026, 6, 27))) {
            val zone = TimeZone.of(location.timeZoneId)
            val isha = engine.timesFor(location, date, PrayerSettings()).time(Prayer.ISHA)
            val midnight = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            assertTrue(isha > midnight + 2.minutes, "$date: Isha $isha is not after midnight")
            val from = midnight + 30.seconds
            val plan = planFor(location, notifications = defaults.copy(remindBeforeMinutes = 1), from = from, windowDays = 2)
            val alarm = plan.single { it.id == "ISHA-PRAYER-$date" }
            assertEquals(isha, alarm.instant)
            assertEquals(isha - 1.minutes, plan.single { it.id == "ISHA-REMINDER-$date" }.instant)
            assertEquals(plan.first { it.kind == NotificationKind.PRAYER }, alarm, "the first adhan is yesterday's Isha")
            // Nothing else of yesterday's is ahead, and nothing is planned twice.
            assertEquals(listOf(alarm.id), plan.filter { it.kind == NotificationKind.PRAYER && it.id.endsWith("$date") }.map { it.id })
            assertEquals(plan.size, plan.map { it.id }.toSet().size)
        }
    }

    // R72/R83: no alarm for a prayer whose window is empty — a start at or after the next
    // obligatory prayer (for Maghrib and Isha, the next day's Fajr) has nothing to announce.
    @Test
    fun anIshaThatReachesTheNextDaysFajrGetsNoAlarmButTheRestOfTheDayKeepsTheirs() {
        // An invented Arctic Circle point, no real city, on Umm al-Qura's global "Other" method
        // (Isha = Maghrib + 90): the short June night lets Isha reach the next morning's Fajr
        // while Maghrib itself is an ordinary, un-substituted sunset, so only Isha's window is
        // empty — the case the notification planner must tell apart from Maghrib's own.
        val arctic = GeoLocation(66.5, 15.0, "Europe/Oslo", null, null)
        val settings = PrayerSettings(timetable = "other.ummalqura")
        val date = LocalDate(2026, 6, 2)
        val zone = TimeZone.of(arctic.timeZoneId)
        val times = engine.timesFor(arctic, date, settings)
        val nextFajr = engine.timesFor(arctic, date.plus(1, DateTimeUnit.DAY), settings).time(Prayer.FAJR)
        // Preconditions: without these the assertions below would pass for the wrong reason.
        assertTrue(times.time(Prayer.ISHA) >= nextFajr, "Isha ${times.time(Prayer.ISHA)} is not at/after next Fajr $nextFajr")
        assertTrue(times.time(Prayer.MAGHRIB) < nextFajr, "Maghrib ${times.time(Prayer.MAGHRIB)} already reaches next Fajr")

        val plan = NotificationPlanner.plan(
            location = arctic, settings = settings, notifications = defaults.copy(remindBeforeMinutes = 10),
            engine = engine, from = date.atStartOfDayIn(zone), windowDays = 2, capacity = 64,
        )
        assertTrue(
            plan.none { it.prayer == Prayer.ISHA && it.id.endsWith("-$date") },
            "an Isha notification or reminder was still planned for $date",
        )
        assertTrue(plan.any { it.id == "MAGHRIB-PRAYER-$date" }, "Maghrib itself should still ring")
        assertTrue(plan.any { it.id == "DHUHR-PRAYER-$date" }, "Dhuhr should be untouched")
        assertTrue(plan.any { it.id == "ASR-PRAYER-$date" }, "Asr should be untouched")
        assertTrue(plan.any { it.id == "FAJR-PRAYER-$date" }, "that morning's Fajr should be untouched")
        val nextDate = date.plus(1, DateTimeUnit.DAY)
        assertTrue(plan.any { it.id == "FAJR-PRAYER-$nextDate" }, "the next morning's Fajr should still ring")
    }

    @Test
    fun anIshaAfterMidnightButStillBeforeTheNextFajrKeepsItsAlarm() {
        // The night before what was Longyearbyen's R72 night (below, ruling R90): Isha falls after
        // local midnight, but there is still a real gap before that morning's Fajr, so nothing
        // here is skipped.
        val longyearbyen = GeoLocation(78.2232, 15.6267, "Europe/Oslo", "Longyearbyen", "NO")
        val zone = TimeZone.of(longyearbyen.timeZoneId)
        val date = LocalDate(2026, 5, 18)
        val times = engine.timesFor(longyearbyen, date, PrayerSettings())
        val nextFajr = engine.timesFor(longyearbyen, date.plus(1, DateTimeUnit.DAY), PrayerSettings()).time(Prayer.FAJR)
        val midnight = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val isha = times.time(Prayer.ISHA)
        // Preconditions: without these the assertions below would pass for the wrong reason.
        assertTrue(isha > midnight, "$date: Isha $isha is not after midnight")
        assertTrue(isha < nextFajr, "$date: Isha $isha already reaches next Fajr $nextFajr")

        val plan = NotificationPlanner.plan(
            location = longyearbyen, settings = PrayerSettings(), notifications = defaults.copy(remindBeforeMinutes = 10),
            engine = engine, from = date.atStartOfDayIn(zone), windowDays = 2, capacity = 64,
        )
        assertEquals(isha, plan.single { it.id == "ISHA-PRAYER-$date" }.instant)
        assertTrue(plan.any { it.id == "ISHA-REMINDER-$date" }, "Isha's reminder should still be planned")
    }

    @Test
    fun theRealEngineNoLongerHasALongyearbyenNightWhoseIshaReachesTheNextFajr() {
        // R72's nights were the repair's own doing: the combined polar Fajr was pulled to the
        // minute before the earliest borrowed sunrise, behind the night's Isha. Ruling R90 keeps
        // Fajr before the sunrise it shows instead, so over 2026 no Longyearbyen night's Isha
        // reaches the next Fajr any more (nor, probed once over 2026–2030, at Bodø, Alta, Vadsø,
        // Finnsnes, Utqiagvik, Norilsk, Murmansk, Nuuk or Iqaluit), and the planner keeps every
        // Maghrib and Isha there. Scanning rather than naming a date keeps this true even if the
        // engine's own numbers move.
        val longyearbyen = GeoLocation(78.2232, 15.6267, "Europe/Oslo", "Longyearbyen", "NO")
        val zone = TimeZone.of(longyearbyen.timeZoneId)
        var probe = LocalDate(2026, 1, 1)
        val reaching = mutableListOf<LocalDate>()
        while (probe.year == 2026) {
            val isha = engine.timesFor(longyearbyen, probe, PrayerSettings()).time(Prayer.ISHA)
            val probeNextFajr = engine.timesFor(longyearbyen, probe.plus(1, DateTimeUnit.DAY), PrayerSettings()).time(Prayer.FAJR)
            if (isha >= probeNextFajr) reaching += probe
            probe = probe.plus(1, DateTimeUnit.DAY)
        }
        assertTrue(reaching.isEmpty(), "nights whose Isha reaches the next Fajr at Longyearbyen in 2026: $reaching")

        // The night that was the R72 night before ruling R90: both evening alarms ring, and
        // everything of that date sounds before the next morning's Fajr, which rings too.
        val former = LocalDate(2026, 5, 19)
        val nextFajr = engine.timesFor(longyearbyen, former.plus(1, DateTimeUnit.DAY), PrayerSettings()).time(Prayer.FAJR)
        val plan = NotificationPlanner.plan(
            location = longyearbyen, settings = PrayerSettings(), notifications = defaults.copy(remindBeforeMinutes = 10),
            engine = engine, from = former.atStartOfDayIn(zone), windowDays = 2, capacity = 64,
        )
        assertTrue(plan.any { it.id == "MAGHRIB-PRAYER-$former" }, "Maghrib should ring on $former")
        assertTrue(plan.any { it.id == "ISHA-PRAYER-$former" }, "Isha should ring on $former")
        assertTrue(plan.none { it.instant >= nextFajr && it.id.endsWith("-$former") }, "nothing of $former's should sound at or after $nextFajr")
        val nextDate = former.plus(1, DateTimeUnit.DAY)
        assertTrue(plan.any { it.id == "FAJR-PRAYER-$nextDate" }, "that morning's Fajr should still ring")
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

    // The voice is one setting for all five prayers, and the receiver never looks settings up, so
    // it has to be stamped on every entry the plan produces — reminders included, even though the
    // chime they carry ignores it.
    @Test
    fun everyEntryCarriesTheChosenVoiceIncludingTheReminders() {
        val plan = planFor(
            notifications = defaults.copy(voice = AdhanVoice.AZEMI, remindBeforeMinutes = 10),
            windowDays = 1,
        )
        assertTrue(plan.any { it.kind == NotificationKind.REMINDER })
        assertTrue(plan.all { it.voice == AdhanVoice.AZEMI }, "${plan.map { it.voice }.toSet()}")
    }

    @Test
    fun thePlanIsTheOriginalVoiceUntilOneIsChosen() {
        assertTrue(planFor().all { it.voice == AdhanVoice.ORIGINAL })
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
