package world.taqwa.app.wear.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.TestFeeds

/**
 * [PhoneFeeds] writes what the phone writes, held to the feeds the phone's own builder wrote
 * where there are any: the Saudi Arabic and English 12-hour samples, and the golden Urdu feed.
 */
class PhoneFeedsTest {

    @Test
    fun theFeedsAreWrittenAsThePhoneWritesThem() {
        fun holds(golden: WatchFeed, english: WatchFeed, phone: PhoneFeeds.Phone) =
            assertSameWords(golden, PhoneFeeds.localised(english, phone, city = golden.place.city))
        holds(TestFeeds.other("ar-sa"), TestFeeds.sample, PhoneFeeds.Phone("ar-SA", twelveHour = false))
        holds(TestFeeds.other("en12"), TestFeeds.sample, PhoneFeeds.Phone("en", twelveHour = true))
        holds(TestFeeds.phone("capetown-ur24"), TestFeeds.phone("capetown-en"), PhoneFeeds.Phone("ur-PK", twelveHour = false))
    }

    @Test
    fun everyLanguageIsOnBothClocks() {
        assertEquals(14, PhoneFeeds.ALL.size)
        val tromso = TestFeeds.phone("tromso-may-en")
        for (phone in PhoneFeeds.ALL) {
            val feed = PhoneFeeds.localised(tromso, phone)
            assertEquals(phone.twelveHour, feed.ui.twelveHour, "$phone")
            assertEquals(phone.twelveHour, feed.days.all { it.clockTime != null }, "$phone")
        }
    }

    /** What the Tile and Today read of a feed, the same in [expected] and [actual]. */
    private fun assertSameWords(expected: WatchFeed, actual: WatchFeed) {
        val tag = expected.ui.language
        assertEquals(expected.ui.rtl, actual.ui.rtl, tag)
        assertEquals(expected.ui.digitZero, actual.ui.digitZero, tag)
        assertEquals(expected.ui.twelveHour, actual.ui.twelveHour, tag)
        assertEquals(expected.ui.am to expected.ui.pm, actual.ui.am to actual.ui.pm, tag)
        assertEquals(expected.ui.countdown, actual.ui.countdown, tag)
        assertEquals(expected.ui.nextIn, actual.ui.nextIn, tag)
        assertEquals(expected.ui.spoken, actual.ui.spoken, tag)
        assertEquals(expected.ui.latinScript, actual.ui.latinScript, tag)
        val e = expected.ui.screens
        val a = actual.ui.screens
        assertEquals(listOf(e.openOnPhone, e.updateOnWatch, e.rowCurrent, e.rowPassed, e.rowNext), listOf(a.openOnPhone, a.updateOnWatch, a.rowCurrent, a.rowPassed, a.rowNext), tag)
        assertEquals(expected.place.city, actual.place.city, tag)
        assertEquals(expected.days.size, actual.days.size, tag)
        for ((x, y) in expected.days.zip(actual.days)) {
            assertEquals(x.times, y.times, "$tag ${x.date}")
            assertEquals(x.names, y.names, "$tag ${x.date}")
            assertEquals(x.clock, y.clock, "$tag ${x.date}")
            assertEquals(x.clockTime, y.clockTime, "$tag ${x.date}")
        }
    }
}
