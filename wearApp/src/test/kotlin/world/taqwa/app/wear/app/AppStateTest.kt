package world.taqwa.app.wear.app

import android.view.View
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.words
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TestFeeds

/**
 * What the watch app's pages are drawn from (plan 3 Task 4, call 5): no feed, a feed, a spent one,
 * and a newer format seen with or without one. The status screen's rule (`statusHeadline`, Ruling
 * 13) comes here unchanged: a newer format outranks the rest, with a feed or without. On the
 * committed Cape Town sample (`TestFeeds`, `Fixtures.capeTownSample()`).
 */
class AppStateTest {

    private val sample = TestFeeds.sample
    private val words = sample.words()

    /** 14:58 on Sunday 18 October 2026 in Cape Town, the mockups' moment: Asr is next, at 16:10. */
    private val at1458 = TestFeeds.at(0, 14, 58)

    @Test
    fun withNoFeedTheWatchAsksForThePhone() {
        assertEquals(AppState.NoFeed(update = false), appState(null, newerVersionSeen = false, now = at1458))
        assertEquals(R.string.open_taqwa_on_phone, noFeedLine(update = false))
    }

    @Test
    fun aNewerFormatWithNoFeedAsksForTheUpdate() {
        // StatusHeadlineTest.aNewerFormatAsksForAnUpdateWhateverElseIsStored's case without a feed:
        // "Open Taqwa on your phone" would send the person to a phone that has already sent what
        // this watch cannot read.
        assertEquals(AppState.NoFeed(update = true), appState(null, newerVersionSeen = true, now = at1458))
        assertEquals(R.string.update_taqwa_on_watch, noFeedLine(update = true))
    }

    @Test
    fun aNewerVersionKeepsTheLastFeedAndAsksForTheUpdate() {
        val state = appState(sample, newerVersionSeen = true, now = at1458)

        assertEquals(AppState.Ready(sample, spent = false, update = true), state)
        assertEquals("Update Taqwa on your watch", assertIs<AppState.Ready>(state).notice(words))
    }

    @Test
    fun pastTheLastStartTheFeedIsSpent() {
        // The sample's last day is Monday 16 November; 31 days after the mock day there is no start left.
        val spent = assertIs<AppState.Ready>(appState(sample, newerVersionSeen = false, now = TestFeeds.at(31, 14, 58)))
        assertTrue(spent.spent)
        assertEquals("Open Taqwa on your phone", spent.notice(words))

        val inPlace = assertIs<AppState.Ready>(appState(sample, newerVersionSeen = false, now = at1458))
        assertFalse(inPlace.spent)
    }

    @Test
    fun theUpdateOutranksTheSpentNotice() {
        val state = assertIs<AppState.Ready>(appState(sample, newerVersionSeen = true, now = TestFeeds.at(31, 14, 58)))

        assertTrue(state.spent)
        assertEquals("Update Taqwa on your watch", state.notice(words))
    }

    @Test
    fun aFeedInPlaceNeedsNoNotice() {
        assertNull(assertIs<AppState.Ready>(appState(sample, newerVersionSeen = false, now = at1458)).notice(words))
    }

    @Test
    fun theNoticesAreTheFeedsOwnWords() {
        // A held feed speaks the phone's language: an Arabic phone's notices are its own, whatever
        // the watch is set to. Only a watch with no feed reads its own resources (noFeedLine).
        val arabic = words.copy(openOnPhone = "افتح تقوى على هاتفك", updateOnWatch = "حدّث تقوى على ساعتك")

        assertEquals("حدّث تقوى على ساعتك", AppState.Ready(sample, spent = false, update = true).notice(arabic))
        assertEquals("افتح تقوى على هاتفك", AppState.Ready(sample, spent = true, update = false).notice(arabic))
    }

    @Test
    fun theDirectionIsThePhonesNotTheWatchs() {
        // A Compose test needs Robolectric, so the rule is tested as the pure function WatchTheme
        // gives each page: the feed's direction when there is a feed, the watch's own without one.
        assertEquals(LayoutDirection.Rtl, layoutDirectionOf(rtl = true, system = LayoutDirection.Ltr))
        assertEquals(LayoutDirection.Ltr, layoutDirectionOf(rtl = false, system = LayoutDirection.Rtl))
        assertEquals(LayoutDirection.Rtl, layoutDirectionOf(rtl = null, system = LayoutDirection.Rtl))
        assertEquals(LayoutDirection.Ltr, layoutDirectionOf(rtl = null, system = LayoutDirection.Ltr))
    }

    @Test
    fun theWatchsOwnDirectionIsReadFromItsConfiguration() {
        // Configuration.layoutDirection, never LocalLayoutDirection, which the frame sets to Ltr for the pager.
        assertEquals(LayoutDirection.Rtl, systemDirectionOf(View.LAYOUT_DIRECTION_RTL))
        assertEquals(LayoutDirection.Ltr, systemDirectionOf(View.LAYOUT_DIRECTION_LTR))
    }

    @Test
    fun thePagesRunTodayTasbeehQiblaAndTheActivityStartsOnTheOneItIsNamed() {
        // Left to right in every language (call 4): a swipe left from Today is Tasbeeh.
        assertEquals(listOf(Page.TODAY, Page.TASBEEH, Page.QIBLA), Page.entries)
        assertEquals("world.taqwa.app.wear.PAGE", EXTRA_PAGE)
        assertEquals(Page.TASBEEH, pageOf("TASBEEH"))
        assertEquals(Page.QIBLA, pageOf("QIBLA"))
        assertEquals(Page.TODAY, pageOf("TODAY"))
        assertEquals(Page.TODAY, pageOf(null), "no extra: Today")
        assertEquals(Page.TODAY, pageOf("compass"), "a name that is no page: Today")
    }
}
