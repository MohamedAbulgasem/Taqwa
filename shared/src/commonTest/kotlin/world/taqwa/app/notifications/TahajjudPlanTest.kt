package world.taqwa.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.prayer.NightThirds
import world.taqwa.app.prayer.PrayerTimesEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tahajjud in the plan (spec §17.6): off by default, one a night, at the last third. */
class TahajjudPlanTest {
    private val engine = PrayerTimesEngine()
    private val tripoli = GeoLocation(32.8872, 13.1913, "Africa/Tripoli", "Tripoli", "LY")
    private val on = NotificationSettings(tahajjud = true)

    private fun planFor(
        notifications: NotificationSettings,
        from: Instant = Instant.parse("2026-09-18T10:00:00Z"),
        windowDays: Int = 3,
        capacity: Int = 64,
        location: GeoLocation = tripoli,
    ) = NotificationPlanner.plan(location, PrayerSettings(), notifications, engine, from, windowDays, capacity)

    @Test
    fun `nobody who has not asked for it is woken`() {
        assertTrue(planFor(NotificationSettings()).none { it.kind == NotificationKind.TAHAJJUD })
    }

    @Test
    fun `it lands where the last third of each night begins`() {
        val plan = planFor(on).filter { it.kind == NotificationKind.TAHAJJUD }
        // Planned from midday on the 18th: that morning's has passed, the 19th and 20th remain.
        assertEquals(listOf("TAHAJJUD-2026-09-19", "TAHAJJUD-2026-09-20"), plan.map { it.id })
        plan.forEach { entry ->
            val date = LocalDate.parse(entry.id.removePrefix("TAHAJJUD-"))
            val maghrib = engine.timesFor(tripoli, date.plus(-1, DateTimeUnit.DAY), PrayerSettings()).time(Prayer.MAGHRIB)
            val fajr = engine.timesFor(tripoli, date, PrayerSettings()).time(Prayer.FAJR)
            assertEquals(NightThirds.lastThirdStart(maghrib, fajr), entry.instant)
            assertTrue(entry.instant > maghrib && entry.instant < fajr)
        }
    }

    @Test
    fun `the first night of the window counts from the evening before it`() {
        // Just after midnight on the 19th: the night in progress began on the 18th.
        val plan = planFor(on, from = Instant.parse("2026-09-18T22:30:00Z"), windowDays = 1)
        assertEquals(listOf("TAHAJJUD-2026-09-19"), plan.filter { it.kind == NotificationKind.TAHAJJUD }.map { it.id })
    }

    @Test
    fun `one that has already passed today is not planned again`() {
        val fajr = engine.timesFor(tripoli, LocalDate(2026, 9, 19), PrayerSettings()).time(Prayer.FAJR)
        val plan = planFor(on, from = fajr, windowDays = 1)
        assertTrue(plan.none { it.kind == NotificationKind.TAHAJJUD })
    }

    @Test
    fun `it carries its own sound and quotes Fajr`() {
        val entry = planFor(on.copy(tahajjudSound = PrayerSound.SILENT)).first { it.kind == NotificationKind.TAHAJJUD }
        assertEquals(PrayerSound.SILENT, entry.sound)
        assertEquals(Prayer.FAJR, entry.prayer)
        assertEquals("Tahajjud", entry.title)
        val fajr = engine.timesFor(tripoli, LocalDate(2026, 9, 19), PrayerSettings()).time(Prayer.FAJR)
        assertTrue(entry.body.endsWith(isoClockTime(fajr, tripoli.timeZoneId)), entry.body)
    }

    @Test
    fun `the five prayers are untouched by it`() {
        val without = planFor(NotificationSettings())
        val with = planFor(on).filter { it.kind != NotificationKind.TAHAJJUD }
        assertEquals(without, with)
    }

    @Test
    fun `it takes one slot a day out of the window`() {
        assertEquals(12, NotificationPlanner.windowDaysFor(64, NotificationSettings()))
        assertEquals(10, NotificationPlanner.windowDaysFor(64, on))
        assertEquals(6, NotificationPlanner.windowDaysFor(64, NotificationSettings(remindBeforeMinutes = 10)))
        assertEquals(5, NotificationPlanner.windowDaysFor(64, on.copy(remindBeforeMinutes = 10)))
    }

    @Test
    fun `a whole window with it on still fits the platform's limit`() {
        val settings = on.copy(remindBeforeMinutes = 10)
        val days = NotificationPlanner.windowDaysFor(NotificationPlanner.IOS_PENDING_LIMIT, settings)
        val plan = planFor(settings, windowDays = days)
        assertTrue(plan.size <= NotificationPlanner.IOS_PENDING_LIMIT)
        assertEquals(plan.sortedBy { it.instant }, plan)
    }

    @Test
    fun `turning notifications off turns it off too`() {
        assertTrue(planFor(on.copy(enabled = false)).isEmpty())
    }

    @Test
    fun `a summer night in the far north never plans one outside the night`() {
        val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
        val plan = planFor(on, from = Instant.parse("2026-06-20T10:00:00Z"), windowDays = 5, location = tromso)
        plan.filter { it.kind == NotificationKind.TAHAJJUD }.forEach { entry ->
            val date = LocalDate.parse(entry.id.removePrefix("TAHAJJUD-"))
            val fajr = engine.timesFor(tromso, date, PrayerSettings()).time(Prayer.FAJR)
            assertTrue(entry.instant < fajr, "${entry.id} at ${entry.instant} is not before Fajr $fajr")
        }
    }
}
