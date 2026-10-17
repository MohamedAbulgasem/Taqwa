package world.taqwa.app.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarIdentifierIslamicUmmAlQura
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Covers the one part of [IosNotificationScheduler] that is testable without a real
 * notification centre: the calendar/timezone pinning on the trigger's date components.
 *
 * Each test sets the process's default zone, which stands in for the device's, to one where a
 * wrong trigger cannot land on the right second by luck. CI's macOS runners are on UTC, and a
 * London phone is on UTC's offset half the year, so neither can be the device here.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationSchedulerTest {

    /**
     * Bare date components are re-read by the trigger in the device's zone, so components that
     * lost their zone fire off by the device's whole offset. Asia/Kolkata is +05:30 all year,
     * never UTC's offset. `nextTriggerDate()` is the resolved fire date, so this asserts the whole
     * round trip to the second.
     */
    @Test
    fun triggerResolvesToTheEntrysInstantOnADeviceNeverAtUtcsOffset() {
        withDefaultTimeZone("Asia/Kolkata") {
            val instant = futureInstant()

            val fireDate = IosNotificationScheduler.triggerFor(instant).nextTriggerDate()

            assertNotNull(fireDate, "trigger produced no fire date")
            assertEquals(instant.epochSeconds, fireDate.timeIntervalSince1970.toLong())
        }
    }

    /**
     * The non-Gregorian half of the bug. `NSCalendar.currentCalendar` is derived from the
     * process's locale and there is **no public API to replace it in-process** — `AppleLocale` in
     * `NSUserDefaults` is read once, long before a test body runs — so an Umm al-Qura device
     * cannot be simulated here directly.
     *
     * What is asserted instead is the property that makes the device calendar irrelevant: the
     * components the trigger is built from carry their own Gregorian calendar and their own zone,
     * UTC. The first three assertions are the control, showing the shape of the old failure — the
     * same instant read through an Umm al-Qura calendar yields a year around 1448, which the
     * trigger would then interpret as Gregorian 1448 and never fire.
     *
     * The zone is checked by its offset on a July day, with the device on London: a zone pinned
     * from the device would be an hour ahead of UTC then, and UTC's own name comes back as "GMT",
     * so the name is not what is compared.
     */
    @Test
    fun triggerPinsItsOwnGregorianCalendarAndUtcSoTheDeviceCannotReinterpretIt() {
        val instant = futureInstant()
        val dubai = NSTimeZone.timeZoneWithName("Asia/Dubai")!!
        val date = NSDate.dateWithTimeIntervalSince1970(instant.epochSeconds.toDouble())

        val islamic = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierIslamicUmmAlQura)!!
            .apply { timeZone = dubai }
        val bare = islamic.components(
            unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = date,
        )
        assertTrue(bare.year in 1400L..1500L, "expected a Hijri year, got ${bare.year}")
        assertTrue(bare.calendar == null, "control: bare components carry no calendar")
        assertEquals(NSCalendarIdentifierIslamicUmmAlQura, islamic.calendarIdentifier)

        withDefaultTimeZone("Europe/London") {
            val pinned = IosNotificationScheduler.triggerFor(instant).dateComponents
            val july = NSDate.dateWithTimeIntervalSince1970(Instant.parse("2026-07-01T12:00:00Z").epochSeconds.toDouble())

            assertEquals(NSCalendarIdentifierGregorian, pinned.calendar?.calendarIdentifier)
            assertTrue(pinned.year > 2000L, "expected a Gregorian year, got ${pinned.year}")
            assertEquals(0L, pinned.timeZone?.secondsFromGMTForDate(july), "expected the components pinned to UTC")
        }
    }

    /**
     * The night the clocks go back repeats an hour of wall-clock time, so a time of day in a zone
     * that keeps summer time names two instants, and date components in that zone cannot say which
     * one is meant. Foundation settles a repeated time on its first occurrence, so an entry in the
     * second pass (Tahajjud's "the last third of the night has begun" in Madrid, London or Toronto
     * on that night) fired an hour early when its trigger was built from that zone's components.
     *
     * Each zone's next fall-back at least an hour from now is found at run time, so both instants
     * are always ahead (`nextTriggerDate()` is nil for a past date), even for a run inside the
     * repeated hour itself, and a zone that stopped keeping summer time fails here instead of
     * quietly testing nothing. 51 minutes before the transition and 9 minutes after are the same
     * minute of the wall clock, once on each pass. Each is checked on a device in Dubai, which
     * keeps no summer time, and on a device in the city's own zone, where a trigger built in the
     * device's zone has the same repeated hour to get wrong.
     */
    @Test
    fun aReminderOnEitherPassOfAFallBackNightFiresAtItsOwnSecond() {
        for (city in listOf("Europe/Madrid", "Europe/London", "America/Toronto")) {
            val zone = NSTimeZone.timeZoneWithName(city)!!
            val transition = nextFallBack(zone, city)
            val before = zone.secondsFromGMTForDate(transition.dateByAddingTimeInterval(-1.0))
            val after = zone.secondsFromGMTForDate(transition)
            assertEquals(3600L, before - after, "$city: the clocks go back an hour")
            val passes = listOf(
                "first pass" to transition.dateByAddingTimeInterval(-51.0 * 60),
                "second pass" to transition.dateByAddingTimeInterval(9.0 * 60),
            )
            for (device in listOf("Asia/Dubai", city)) {
                withDefaultTimeZone(device) {
                    for ((pass, date) in passes) {
                        val instant = Instant.fromEpochSeconds(date.timeIntervalSince1970.toLong())
                        val label = "$city $pass ($instant), device on $device"

                        val fireDate = IosNotificationScheduler.triggerFor(instant).nextTriggerDate()

                        assertNotNull(fireDate, "$label: no fire date")
                        assertEquals(instant.epochSeconds, fireDate.timeIntervalSince1970.toLong(), label)
                    }
                }
            }
        }
    }

    /**
     * [zone]'s next transition that sets the clocks back, at most two transitions away, searched
     * from an hour ahead so the first pass before it, 51 minutes earlier, is still in the future.
     */
    private fun nextFallBack(zone: NSTimeZone, name: String): NSDate {
        var from = NSDate().dateByAddingTimeInterval(3600.0)
        repeat(2) {
            val transition = zone.nextDaylightSavingTimeTransitionAfterDate(from)
                ?: fail("$name keeps no summer time any more")
            val before = zone.secondsFromGMTForDate(transition.dateByAddingTimeInterval(-1.0))
            if (before > zone.secondsFromGMTForDate(transition)) return transition
            from = transition
        }
        fail("$name: neither of its next two transitions sets the clocks back")
    }

    /** Whole seconds, comfortably in the future: `nextTriggerDate()` is nil for a past date. */
    private fun futureInstant(): Instant =
        Instant.fromEpochSeconds((Clock.System.now() + 30.days).epochSeconds)

    private fun withDefaultTimeZone(name: String, body: () -> Unit) {
        val original = NSTimeZone.defaultTimeZone
        NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName(name)!!)
        try {
            body()
        } finally {
            NSTimeZone.setDefaultTimeZone(original)
        }
    }
}
