package world.taqwa.app.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierIslamicUmmAlQura
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Covers the one part of [IosNotificationScheduler] that is testable without a real
 * notification centre: the calendar/timezone pinning on the trigger's date components.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationSchedulerTest {

    /**
     * The scenario the fix exists for: the notification belongs to a city in `Asia/Dubai` while
     * the process — standing in for the device — is on `Europe/London`. Before the fix the
     * components were bare numbers and the trigger re-read them in the device's zone, firing
     * three hours late. `nextTriggerDate()` is the resolved fire date, so this asserts the whole
     * round trip to the second.
     */
    @Test
    fun triggerResolvesToTheEntrysInstantWhenTheDeviceIsInAnotherTimeZone() {
        withDefaultTimeZone("Europe/London") {
            val instant = futureInstant()

            val fireDate = IosNotificationScheduler.triggerFor(instant, "Asia/Dubai").nextTriggerDate()

            assertNotNull(fireDate, "trigger produced no fire date")
            assertEquals(instant.epochSeconds, fireDate.timeIntervalSince1970.toLong())
        }
    }

    /** The same instant with the entry's own zone as the device's zone must not move either. */
    @Test
    fun triggerResolvesToTheEntrysInstantWhenTheZonesAgree() {
        withDefaultTimeZone("Asia/Dubai") {
            val instant = futureInstant()

            val fireDate = IosNotificationScheduler.triggerFor(instant, "Asia/Dubai").nextTriggerDate()

            assertNotNull(fireDate)
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
     * components the trigger is built from carry their own Gregorian calendar and the entry's
     * zone. The first two assertions are the control, showing the shape of the old failure — the
     * same instant read through an Umm al-Qura calendar yields a year around 1448, which the
     * trigger would then interpret as Gregorian 1448 and never fire.
     */
    @Test
    fun triggerPinsItsOwnGregorianCalendarSoADeviceCalendarCannotReinterpretIt() {
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

        val pinned = IosNotificationScheduler.triggerFor(instant, "Asia/Dubai").dateComponents

        assertEquals(NSCalendarIdentifierIslamicUmmAlQura, islamic.calendarIdentifier)
        assertTrue(pinned.year > 2000L, "expected a Gregorian year, got ${pinned.year}")
        assertEquals("Asia/Dubai", pinned.timeZone?.name)
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
