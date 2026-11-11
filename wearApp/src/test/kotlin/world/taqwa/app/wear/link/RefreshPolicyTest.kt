package world.taqwa.app.wear.link

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.TestFeeds

/**
 * When the watch shakes hands with its phone (plan 2 Task 6, the Lead's call 1): at its start when
 * it holds nothing from the phone, when the app opens on a feed that needs one (spec 2.2 and 2.6),
 * whenever the phone comes back nearby, and daily; each damped by the time since the last ask that
 * went out.
 *
 * The sample is written at 08:00 on Sunday 18 October 2026 and its last day is Monday 16 November.
 */
class RefreshPolicyTest {

    private val feed = TestFeeds.sample
    private val written = feed.writtenAt
    private val hour = 3_600L
    private val day = 86_400L

    private fun ask(why: AskWhy, feed: WatchFeed?, fromPhone: Boolean, now: Long, lastAskedAt: Long? = null) =
        RefreshPolicy.shouldAsk(why, feed, fromPhone, now, lastAskedAt)

    @Test
    fun aStartWithNothingFromThePhoneAsks() {
        val now = written + hour
        assertTrue(ask(AskWhy.STARTED, null, fromPhone = false, now), "no feed")
        assertTrue(ask(AskWhy.STARTED, feed, fromPhone = false, now), "the sample, held without a delivery")
        assertTrue(ask(AskWhy.STARTED, null, fromPhone = true, now), "a delivery left without its feed is nothing from the phone")
        assertFalse(ask(AskWhy.STARTED, null, fromPhone = false, now, lastAskedAt = now - 30), "30 s after the last ask")
        assertTrue(ask(AskWhy.STARTED, null, fromPhone = false, now, lastAskedAt = now - RefreshPolicy.GAP_SHORT))
    }

    @Test
    fun aStartWithThePhonesFeedDoesNotAsk() {
        assertFalse(ask(AskWhy.STARTED, feed, fromPhone = true, written + hour))
        assertFalse(ask(AskWhy.STARTED, feed, fromPhone = true, written + 7 * day + hour), "a week old: OPENED, PHONE_NEARBY and DAILY cover it")
    }

    @Test
    fun openedWithNoFeedAsks() {
        assertTrue(ask(AskWhy.OPENED, null, fromPhone = false, written))
    }

    @Test
    fun openedWithAFreshFeedWithWeeksLeftDoesNotAsk() {
        assertFalse(ask(AskWhy.OPENED, feed, fromPhone = true, written + hour))
    }

    @Test
    fun openedThreeDaysFromTheEndAsks() {
        // The sample's own three days from its end are 26 days after it was written, where the week
        // rule asks first, so the feed is held as if written an hour before each look: only the
        // three days decide (WatchRules.needsRefresh).
        val lastStart = feed.days.last().times.isha
        val inside = lastStart - 3 * day + hour
        val outside = lastStart - 3 * day - hour
        assertTrue(ask(AskWhy.OPENED, feed.copy(writtenAt = inside - hour), fromPhone = true, inside))
        assertFalse(ask(AskWhy.OPENED, feed.copy(writtenAt = outside - hour), fromPhone = true, outside))
    }

    @Test
    fun openedOnAWeekOldFeedAsks() {
        assertFalse(ask(AskWhy.OPENED, feed, fromPhone = true, written + 7 * day - hour))
        assertTrue(ask(AskWhy.OPENED, feed, fromPhone = true, written + 7 * day + hour))
    }

    @Test
    fun aSpentFeedAsksEachTimeTheAppOpens() {
        val spent = feed.days.last().midnight + 2 * day
        assertTrue(ask(AskWhy.OPENED, feed, fromPhone = true, spent, lastAskedAt = spent - 61))
        assertFalse(ask(AskWhy.OPENED, feed, fromPhone = true, spent, lastAskedAt = spent - 30))
    }

    @Test
    fun thePhoneComingBackNearbyAlwaysShakesHands() {
        val now = written + hour
        assertTrue(ask(AskWhy.PHONE_NEARBY, feed, fromPhone = true, now, lastAskedAt = now - 120))
        assertFalse(ask(AskWhy.PHONE_NEARBY, feed, fromPhone = true, now, lastAskedAt = now - 30))
        assertTrue(ask(AskWhy.PHONE_NEARBY, null, fromPhone = false, now))
    }

    @Test
    fun theDailyNudgeAlwaysShakesHands() {
        val now = written + hour
        assertTrue(ask(AskWhy.DAILY, feed, fromPhone = true, now, lastAskedAt = now - 2 * hour))
        assertFalse(ask(AskWhy.DAILY, feed, fromPhone = true, now, lastAskedAt = now - 30 * 60))
        assertTrue(ask(AskWhy.DAILY, feed, fromPhone = true, now, lastAskedAt = now - RefreshPolicy.GAP_DAILY))
        assertTrue(ask(AskWhy.DAILY, null, fromPhone = false, now))
    }

    @Test
    fun anAskStampedAheadOfTheClockSilencesNothing() {
        // The watch's clock was set back (a reset, a wrong time corrected): a stamp ahead of now says
        // nothing about when the watch last asked, and must not hold its handshakes back for days.
        val now = written + hour
        for (why in AskWhy.entries) {
            assertTrue(ask(why, null, fromPhone = false, now, lastAskedAt = now + day), "$why")
        }
    }
}
