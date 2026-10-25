package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import world.taqwa.app.domain.DuhaTiming
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.DuhaTimes
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Duha and the iqama in the plan (Duha and Iqama spec §3.3): both off by default, Duha once a
 * morning at the time §2 gives, the iqama at each adhan plus its own gap, and when the slots run
 * short, the iqama reminders on the furthest days are the only ones left out.
 */
class DuhaIqamaPlanTest {

    private val engine = PrayerTimesEngine()
    private val tripoli = GeoLocation(32.8872, 13.1913, "Africa/Tripoli", "Tripoli", "LY")
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
    private val defaults = NotificationSettings()

    private fun planFor(
        notifications: NotificationSettings,
        location: GeoLocation = london,
        from: Instant = Instant.parse("2026-09-06T00:30:00Z"),
        windowDays: Int = 1,
        capacity: Int = 64,
        settings: PrayerSettings = PrayerSettings(),
    ) = NotificationPlanner.plan(location, settings, notifications, engine, from, windowDays, capacity)

    private fun startOf(place: GeoLocation, date: LocalDate): Instant = date.atStartOfDayIn(TimeZone.of(place.timeZoneId))

    // ── Duha (spec §2, §3.2) ───────────────────────────────────────────────────────────────

    private val oct25 = LocalDate(2026, 10, 25)

    private fun tripoliDuha() =
        assertNotNull(DuhaTimes.forDay(tripoli, oct25, engine.timesFor(tripoli, oct25, PrayerSettings()).time(Prayer.SUNRISE)))

    @Test
    fun `Duha at its best time rings midway to noon and quotes its end`() {
        val duha = tripoliDuha()
        val plan = planFor(defaults.copy(duha = true, duhaSound = PrayerSound.TAKBIR), tripoli, startOf(tripoli, oct25))
        val entry = plan.single { it.kind.isDuha }
        assertEquals("DUHA-$oct25", entry.id)
        assertEquals(NotificationKind.DUHA_BEST, entry.kind)
        // Sunrise is the stand-in: it is never notified, so it cannot be taken for an adhan.
        assertEquals(Prayer.SUNRISE, entry.prayer)
        assertEquals(duha.best, entry.instant)
        assertEquals(PrayerSound.TAKBIR, entry.sound)
        assertEquals("Duha", entry.title)
        // §2.2: Tripoli's Duha ends at 12:41 that day, and that is the time the body quotes.
        assertEquals("12:41", isoClockTime(duha.end, tripoli.timeZoneId))
        assertEquals("The best time for Duha · until 12:41", entry.body)
        assertEquals(duha.end, entry.windowEnd)
    }

    @Test
    fun `Duha once its time is in rings at its start`() {
        val duha = tripoliDuha()
        val plan = planFor(defaults.copy(duha = true, duhaTiming = DuhaTiming.START), tripoli, startOf(tripoli, oct25))
        val entry = plan.single { it.kind.isDuha }
        assertEquals("DUHA-$oct25", entry.id)
        assertEquals(NotificationKind.DUHA_START, entry.kind)
        assertEquals(duha.start, entry.instant)
        assertEquals(PrayerSound.NOTIFICATION, entry.sound)
        assertEquals("Duha time is in · until 12:41", entry.body)
        assertEquals(duha.end, entry.windowEnd)
    }

    @Test
    fun `a Duha reminder already past is not planned again`() {
        val duha = tripoliDuha()
        val plan = planFor(defaults.copy(duha = true), tripoli, from = duha.best + 1.minutes, windowDays = 2)
        assertEquals(listOf("DUHA-${oct25.plus(1, DateTimeUnit.DAY)}"), plan.filter { it.kind.isDuha }.map { it.id })
    }

    @Test
    fun `nobody gets a Duha reminder who has not asked for one`() {
        assertFalse(NotificationSettings().duha)
        assertTrue(planFor(defaults, windowDays = 12).none { it.kind.isDuha || it.id.startsWith("DUHA-") })
    }

    @Test
    fun `Tromso gets no Duha in its dark winter or under the midnight sun`() {
        // 5 February: the sun rises but never climbs to 6.5°. 21 December: the polar night.
        // 21 June: the midnight sun, which climbs well past 6.5° but never rises, so only the
        // missing true sunrise keeps Duha away, whatever sunrise the timetable shows (spec §1).
        for (date in listOf(LocalDate(2026, 2, 5), LocalDate(2026, 12, 21), LocalDate(2026, 6, 21))) {
            val plan = planFor(defaults.copy(duha = true), tromso, startOf(tromso, date))
            assertTrue(plan.none { it.kind.isDuha }, "$date: ${plan.map { it.id }}")
            assertTrue(plan.any { it.id == "DHUHR-PRAYER-$date" }, "$date: the prayers are still planned")
        }
    }

    @Test
    fun `the prayers and their reminders are untouched by Duha and the iqama`() {
        val without = planFor(defaults.copy(remindBeforeMinutes = 10), windowDays = 3)
        val with = planFor(defaults.copy(remindBeforeMinutes = 10, duha = true, iqama = true), windowDays = 3)
        assertEquals(without, with.filterNot { it.kind.isDuha || it.kind == NotificationKind.IQAMA })
        // The 6th to the 8th of September 2026, Sunday to Tuesday: a Duha and five iqamas a day.
        assertEquals(3, with.count { it.kind.isDuha })
        assertEquals(15, with.count { it.kind == NotificationKind.IQAMA })
    }

    // ── The iqama (spec §3.3) ──────────────────────────────────────────────────────────────

    private val iqamaOn = defaults.copy(iqama = true, iqamaSound = PrayerSound.SILENT)

    /** A Sunday. */
    private val sixth = LocalDate(2026, 9, 6)

    @Test
    fun `each prayer's iqama reminder rings its own minutes after the adhan`() {
        val times = engine.timesFor(london, sixth, PrayerSettings())
        val plan = planFor(iqamaOn.copy(iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.ASR to 25)))
        val gaps = mapOf(Prayer.FAJR to 15, Prayer.DHUHR to 10, Prayer.ASR to 25, Prayer.MAGHRIB to 5, Prayer.ISHA to 10)
        for ((prayer, gap) in gaps) {
            val entry = plan.single { it.id == "${prayer.name}-IQAMA-$sixth" }
            assertEquals(NotificationKind.IQAMA, entry.kind)
            assertEquals(prayer, entry.prayer)
            assertEquals(times.time(prayer) + gap.minutes, entry.instant)
            assertEquals(entry.instant + 10.minutes, entry.windowEnd)
            // Its own sound, never the prayer's adhan.
            assertEquals(PrayerSound.SILENT, entry.sound)
            assertEquals(EnglishNotificationCopy.title(prayer, NotificationKind.PRAYER), entry.title)
            assertEquals("Iqama reminder · $gap minutes after the adhan", entry.body)
        }
        assertEquals(5, plan.count { it.kind == NotificationKind.IQAMA })
        // Notification is its sound until another is chosen, whatever the prayers' own are.
        assertTrue(planFor(defaults.copy(iqama = true)).filter { it.kind == NotificationKind.IQAMA }.all { it.sound == PrayerSound.NOTIFICATION })
    }

    @Test
    fun `a prayer whose iqama is off gets no iqama reminder`() {
        val plan = planFor(iqamaOn.copy(iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.ASR to 0)))
        assertTrue(plan.none { it.id == "ASR-IQAMA-$sixth" })
        assertEquals(4, plan.count { it.kind == NotificationKind.IQAMA })
        assertTrue(plan.any { it.id == "ASR-PRAYER-$sixth" })
    }

    @Test
    fun `nobody gets an iqama reminder who has not asked for one`() {
        assertFalse(NotificationSettings().iqama)
        assertTrue(planFor(defaults, windowDays = 12).none { it.kind == NotificationKind.IQAMA || "-IQAMA-" in it.id })
    }

    @Test
    fun `Friday's Dhuhr has no iqama reminder while Friday's Asr keeps its own`() {
        val friday = LocalDate(2026, 9, 11)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val plan = planFor(iqamaOn, from = startOf(london, friday))
        assertTrue(plan.none { it.id == "DHUHR-IQAMA-$friday" })
        assertTrue(plan.any { it.id == "DHUHR-PRAYER-$friday" })
        assertTrue(plan.any { it.id == "ASR-IQAMA-$friday" })
        assertEquals(4, plan.count { it.kind == NotificationKind.IQAMA && it.id.endsWith("-$friday") })
        // Saturday's Dhuhr has its own again.
        val saturday = friday.plus(1, DateTimeUnit.DAY)
        assertTrue(planFor(iqamaOn, from = startOf(london, saturday)).any { it.id == "DHUHR-IQAMA-$saturday" })
    }

    @Test
    fun `an iqama reminder that would reach the next prayer is not planned`() {
        // Maghrib and Isha drawn together by hand, as NotificationPlannerTest's clamp does, with the
        // earlier Isha confirmed under London's own timetable so the engine does not pause it.
        val londonEntry = engine.dayFor(london, sixth, PrayerSettings()).effectiveEntry.id
        val squeezed = PrayerSettings(
            minuteAdjustments = mapOf(Prayer.MAGHRIB to 35, Prayer.ISHA to -35),
            confirmedAdjustments = mapOf(Prayer.ISHA to londonEntry),
        )
        val times = engine.timesFor(london, sixth, squeezed)
        val gap = (times.time(Prayer.ISHA) - times.time(Prayer.MAGHRIB)).inWholeMinutes.toInt()
        // Precondition: without this the assertions below would pass for the wrong reason.
        assertTrue(gap in 2..30, "gap was $gap")

        fun maghribIqama(minutes: Int) = planFor(
            iqamaOn.copy(iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.MAGHRIB to minutes)),
            settings = squeezed,
        ).singleOrNull { it.id == "MAGHRIB-IQAMA-$sixth" }

        // At Isha itself is not before it, and later still is past it.
        assertNull(maghribIqama(gap))
        assertNull(maghribIqama(gap + 5))
        // A minute short of Isha is still before it.
        assertEquals(times.time(Prayer.MAGHRIB) + (gap - 1).minutes, maghribIqama(gap - 1)?.instant)
    }

    @Test
    fun `a late iqama reminder is never posted once the next adhan is in`() {
        // Isha drawn to 8 minutes after Maghrib, confirmed under London's own timetable so the
        // engine does not pause it: Maghrib's iqama at 5 minutes rings 3 minutes before Isha, and
        // a late post past Isha's adhan would land in the next prayer's time.
        val londonEntry = engine.dayFor(london, sixth, PrayerSettings()).effectiveEntry.id
        val plain = engine.timesFor(london, sixth, PrayerSettings())
        val naturalGap = (plain.time(Prayer.ISHA) - plain.time(Prayer.MAGHRIB)).inWholeMinutes.toInt()
        val squeezed = PrayerSettings(
            minuteAdjustments = mapOf(Prayer.ISHA to 8 - naturalGap),
            confirmedAdjustments = mapOf(Prayer.ISHA to londonEntry),
        )
        val times = engine.timesFor(london, sixth, squeezed)
        // Precondition: without this the assertions below would pass for the wrong reason.
        assertEquals(8.minutes, times.time(Prayer.ISHA) - times.time(Prayer.MAGHRIB))

        val entry = planFor(
            iqamaOn.copy(iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.MAGHRIB to 5)),
            settings = squeezed,
        ).single { it.id == "MAGHRIB-IQAMA-$sixth" }
        assertEquals(times.time(Prayer.MAGHRIB) + 5.minutes, entry.instant)
        assertEquals(times.time(Prayer.ISHA), entry.windowEnd)
    }

    @Test
    fun `Fajr's iqama reminder must come before sunrise and not merely before Dhuhr`() {
        // Reykjavik in late May: Fajr's time lasts a quarter of an hour, so the default gap lands
        // on sunrise, when Fajr's time is over and prayer is forbidden until the sun is up.
        val reykjavik = GeoLocation(64.1466, -21.9426, "Atlantic/Reykjavik", "Reykjavík", "IS")
        val date = LocalDate(2026, 5, 27)
        val times = engine.timesFor(reykjavik, date, PrayerSettings())
        val window = (times.time(Prayer.SUNRISE) - times.time(Prayer.FAJR)).inWholeMinutes.toInt()
        val defaultGap = NotificationSettings.IqamaDefaults.getValue(Prayer.FAJR)
        // Precondition: the default gap reaches sunrise, and a shorter one still fits before it.
        assertTrue(window in 2..defaultGap, "window was $window")

        fun fajrIqama(minutes: Int) = planFor(
            iqamaOn.copy(iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.FAJR to minutes)),
            reykjavik,
            startOf(reykjavik, date),
        ).singleOrNull { it.id == "FAJR-IQAMA-$date" }

        // At sunrise is not before it, and the default and the longest gap are past it.
        assertNull(fajrIqama(window))
        assertNull(fajrIqama(defaultGap))
        assertNull(fajrIqama(30))
        // A minute short of sunrise is still inside Fajr's time.
        assertEquals(times.time(Prayer.FAJR) + (window - 1).minutes, fajrIqama(window - 1)?.instant)
    }

    @Test
    fun `a prayer skipped for its empty window has no iqama reminder either`() {
        // NotificationPlannerTest's invented Arctic Circle point (R83): the short June night lets
        // Isha reach the next morning's Fajr, so Isha is skipped and its iqama with it.
        val arctic = GeoLocation(66.5, 15.0, "Europe/Oslo", null, null)
        val settings = PrayerSettings(timetable = "other.ummalqura")
        val date = LocalDate(2026, 6, 2)
        val plan = planFor(iqamaOn, arctic, startOf(arctic, date), windowDays = 2, settings = settings)
        // Precondition: the prayer itself was skipped.
        assertTrue(plan.none { it.id == "ISHA-PRAYER-$date" })
        assertTrue(plan.none { it.id == "ISHA-IQAMA-$date" })
        assertTrue(plan.any { it.id == "MAGHRIB-IQAMA-$date" }, "the rest of the day keeps theirs")
    }

    @Test
    fun `on a polar night no iqama reminder lands at or after the next Fajr`() {
        // The same point on 5 June: Isha's start is past the next morning's Fajr, so Maghrib's
        // time ends at that Fajr, and a 30-minute gap would ring after Fajr has begun.
        val arctic = GeoLocation(66.5, 15.0, "Europe/Oslo", null, null)
        val settings = PrayerSettings(timetable = "other.ummalqura")
        val date = LocalDate(2026, 6, 5)
        val maghrib = engine.timesFor(arctic, date, settings).time(Prayer.MAGHRIB)
        val nextFajr = engine.timesFor(arctic, date.plus(1, DateTimeUnit.DAY), settings).time(Prayer.FAJR)
        val window = (nextFajr - maghrib).inWholeMinutes.toInt()
        // Precondition: Maghrib is announced, and 30 minutes reach past the next Fajr.
        assertTrue(window in 2..29, "window was $window")

        fun maghribIqama(minutes: Int) = planFor(
            iqamaOn.copy(iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.MAGHRIB to minutes)),
            arctic,
            startOf(arctic, date),
            windowDays = 2,
            settings = settings,
        ).singleOrNull { it.id == "MAGHRIB-IQAMA-$date" }

        assertNull(maghribIqama(30))
        assertNull(maghribIqama(window))
        assertEquals(maghrib + (window - 1).minutes, maghribIqama(window - 1)?.instant)
    }

    // ── Slots (spec §3.3) ──────────────────────────────────────────────────────────────────

    @Test
    fun `window days follow the spec's table with a four day floor`() {
        fun days(settings: NotificationSettings) = NotificationPlanner.windowDaysFor(64, settings)
        val maximum = defaults.copy(remindBeforeMinutes = 10, tahajjud = true, adhkar = true)
        assertEquals(12, days(defaults))
        assertEquals(10, days(defaults.copy(duha = true)))
        assertEquals(6, days(defaults.copy(iqama = true)))
        assertEquals(5, days(defaults.copy(duha = true, iqama = true)))
        assertEquals(4, days(defaults.copy(remindBeforeMinutes = 10, iqama = true)))
        assertEquals(4, days(maximum))
        assertEquals(4, days(maximum.copy(iqama = true)))
        assertEquals(4, days(maximum.copy(iqama = true, duha = true)))
        // Iqama on Fajr and Isha only counts two a day: 64 / 7.
        val twoOnly = NotificationSettings.IqamaDefaults + mapOf(Prayer.DHUHR to 0, Prayer.ASR to 0, Prayer.MAGHRIB to 0)
        assertEquals(9, days(defaults.copy(iqama = true, iqamaMinutes = twoOnly)))
        // Gaps kept while Iqama is off count for nothing.
        assertEquals(12, days(defaults.copy(iqama = false)))
    }

    @Test
    fun `with everything on only the furthest iqama reminders are left out`() {
        val everything = defaults.copy(remindBeforeMinutes = 10, tahajjud = true, adhkar = true, duha = true, iqama = true)
        val days = NotificationPlanner.windowDaysFor(NotificationPlanner.IOS_PENDING_LIMIT, everything)
        assertEquals(4, days)
        val plan = planFor(everything, windowDays = days, capacity = NotificationPlanner.IOS_PENDING_LIMIT)
        val unlimited = planFor(everything, windowDays = days, capacity = 1000)
        // Precondition: there is more than fits, or nothing below is tested.
        assertTrue(unlimited.size > NotificationPlanner.IOS_PENDING_LIMIT, "${unlimited.size}")
        assertEquals(NotificationPlanner.IOS_PENDING_LIMIT, plan.size)

        val iqama = NotificationKind.IQAMA
        // Every other entry of the four days is armed: 4 × 14 = 56 at most.
        assertEquals(unlimited.filter { it.kind != iqama }, plan.filter { it.kind != iqama })
        for (kind in listOf(NotificationKind.TAHAJJUD, NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING, NotificationKind.DUHA_BEST)) {
            assertEquals(4, plan.count { it.kind == kind }, "$kind")
        }
        // And of the iqamas, the soonest.
        val kept = plan.filter { it.kind == iqama }
        assertTrue(kept.isNotEmpty())
        assertEquals(unlimited.filter { it.kind == iqama }.take(kept.size), kept)
        val dropped = unlimited.filterNot { it in plan }
        assertTrue(dropped.all { it.kind == iqama }, dropped.map { it.id }.toString())
        assertEquals(plan.sortedBy { it.instant }, plan)
    }

    @Test
    fun `with everything on the adhans reach exactly as far as at today's maximum`() {
        // The 4-day window is four dates from today, not four days of lead (spec §3.5): just after
        // Isha, before midnight, the furthest adhan is Isha three dates on, about 2¾ days ahead.
        // Duha and the iqama must not shorten that, as their slots come last in the fill order.
        val maximum = defaults.copy(remindBeforeMinutes = 30, tahajjud = true, adhkar = true)
        val everything = maximum.copy(duha = true, iqama = true)
        val from = Instant.parse("2026-10-25T21:50:00Z") // 23:50 in Tripoli, after Isha
        fun adhans(settings: NotificationSettings) = planFor(
            settings,
            location = tripoli,
            from = from,
            windowDays = NotificationPlanner.windowDaysFor(NotificationPlanner.IOS_PENDING_LIMIT, settings),
            capacity = NotificationPlanner.IOS_PENDING_LIMIT,
        ).filter { it.kind == NotificationKind.PRAYER }

        val withAll = adhans(everything)
        assertEquals(adhans(maximum), withAll)
        val last = withAll.last()
        assertEquals(Prayer.ISHA, last.prayer)
        assertTrue(last.instant > startOf(tripoli, LocalDate(2026, 10, 28)), "${last.instant}")
        assertTrue(last.instant < startOf(tripoli, LocalDate(2026, 10, 29)), "${last.instant}")
        assertTrue(last.instant - from > (2 * 24 + 18).hours, "${last.instant - from}")
    }

    @Test
    fun `Duha comes after the adhkar reminder and the iqama last in the fill order`() {
        val order = NotificationPlanner.FILL_ORDER
        assertEquals(setOf(NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING), order[order.size - 3])
        assertEquals(setOf(NotificationKind.DUHA_BEST, NotificationKind.DUHA_START), order[order.size - 2])
        assertEquals(setOf(NotificationKind.IQAMA), order.last())
    }
}
