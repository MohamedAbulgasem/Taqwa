package world.taqwa.app.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.watchfeed.FakeWatchFeedMemory
import world.taqwa.app.watchfeed.FakeWatchLink
import world.taqwa.app.watchfeed.WatchFeedSync
import world.taqwa.app.watchfeed.WatchSyncReason
import world.taqwa.app.watchfeed.settleTheWatch
import world.taqwa.app.watchfeed.syncFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The background refresh's body ([afterBackgroundRefresh]) with its steps faked, on virtual time:
 * the iPhone's twin of Android's `SystemEventReceiverTest` and `NotificationTopUpWorkerTest`. The
 * widgets are reloaded before the watch is given its turn, so a watch link that hangs costs the
 * reminders and the widgets nothing (plan 2's Global Constraints: `settle()` last, after every
 * widget step, `BackgroundRefreshBridge` among the entry points).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundRefreshBridgeTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")

    @Test
    fun theWidgetsAreReloadedWhileTheWatchLinkHangs() = runTest {
        val events = mutableListOf<Pair<String, Long>>()
        val sync = WatchFeedSync(
            feedFor = { delay(3.seconds); syncFeed() },
            link = FakeWatchLink(confirmsDelivery = false).apply { gate = CompletableDeferred() },
            memory = FakeWatchFeedMemory(),
            scope = backgroundScope,
            work = StandardTestDispatcher(testScheduler),
        )
        var settled: Pair<Boolean, Long>? = null

        val rescheduled = afterBackgroundRefresh(
            reschedule = {
                events += "armed" to currentTime
                sync.request(WatchSyncReason.RESCHEDULED, london)
            },
            refreshMirror = { events += "mirror" to currentTime },
            redrawWidgets = { events += "widgets" to currentTime },
            settleWatch = { settled = settleTheWatch { sync } to currentTime },
        )

        assertTrue(rescheduled)
        assertEquals(listOf("armed" to 0L, "mirror" to 0L, "widgets" to 0L), events, "the widgets went first")
        assertEquals(true to 5_000L, settled, "settled once the ask's budget ended")
    }

    @Test
    fun eachStepHasItsTurnAndTheAnswerIsTheReschedules() = runTest {
        val steps = mutableListOf<String>()
        val answer = afterBackgroundRefresh(
            reschedule = { steps += "reschedule"; error("no stored zone") },
            refreshMirror = { steps += "mirror"; error("the store failed") },
            redrawWidgets = { steps += "widgets"; error("no hook") },
            settleWatch = { steps += "settle"; error("the watch broke") },
        )

        assertEquals(listOf("reschedule", "mirror", "widgets", "settle"), steps)
        assertFalse(answer, "a reschedule that failed is reported as one")
        assertTrue(
            afterBackgroundRefresh(
                reschedule = {},
                refreshMirror = {},
                redrawWidgets = {},
                settleWatch = { error("the watch broke") },
            ),
            "the watch failing never fails the refresh",
        )
    }
}
