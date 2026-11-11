package world.taqwa.app.notifications

import androidx.work.ListenableWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watchfeed.FakeWatchFeedMemory
import world.taqwa.app.watchfeed.FakeWatchLink
import world.taqwa.app.watchfeed.WatchFeedSync
import world.taqwa.app.watchfeed.WatchSyncReason
import world.taqwa.app.watchfeed.settleTheWatch
import world.taqwa.app.watchfeed.syncFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The top-up worker's body ([topUp]) with its steps faked: the order they run in, and that none
 * of them can cost another its turn or the worker its success. The watch is settled last, and
 * never costs the widget or more than its bound (plan 2 Task 4).
 *
 * With notifications off the plan is empty and no prayer alarm ever fires, so this worker is the
 * one thing left in the background to move the widget's two-day horizon. The mirror is rewritten
 * after the reschedule, which may store a new location (a journey noticed from the phone's last
 * known position), and before the redraw, which reads it.
 *
 * Only WorkManager's `Result` is touched, which is plain Java, so no Android class is loaded and
 * no Robolectric is needed.
 */
class NotificationTopUpWorkerTest {

    private val steps = mutableListOf<String>()

    private val reschedule: suspend () -> Unit = { steps += "reschedule" }
    private val refreshMirror: suspend () -> Unit = { steps += "mirror" }
    private val redraw: suspend () -> Unit = { steps += "redraw" }

    @Test
    fun `the plan is rebuilt first and then the widget is redrawn from a fresh mirror`() = runTest {
        topUp(reschedule, refreshMirror, redraw)

        assertEquals(listOf("reschedule", "mirror", "redraw"), steps)
    }

    @Test
    fun `a reschedule that throws still refreshes the mirror and redraws the widget`() = runTest {
        // The "Alarms & reminders" grant revoked mid-reschedule, say: the widget is not the plan's
        // to lose.
        topUp(reschedule = { error("reschedule failed") }, refreshMirror, redraw)

        assertEquals(listOf("mirror", "redraw"), steps)
    }

    @Test
    fun `a mirror refresh that throws still redraws the widget`() = runTest {
        // The countdown is drawn from the device clock, so a redraw of the old mirror still moves it.
        topUp(reschedule, refreshMirror = { error("mirror refresh failed") }, redraw)

        assertEquals(listOf("reschedule", "redraw"), steps)
    }

    @Test
    fun `the work succeeds whichever steps fail`() = runTest {
        val fails: suspend () -> Unit = { error("step failed") }
        val success = ListenableWorker.Result.success()

        assertEquals(success, topUp(reschedule, refreshMirror, redraw))
        assertEquals(success, topUp(fails, refreshMirror, redraw))
        assertEquals(success, topUp(reschedule, fails, redraw))
        assertEquals(success, topUp(reschedule, refreshMirror, fails))
        assertEquals(success, topUp(fails, fails, fails))
    }

    @Test
    fun theWatchIsSettledLastAndNeverCostsTheWidget() = runTest {
        topUp(reschedule, refreshMirror, redraw, settleWatch = { steps += "settle" })
        assertEquals(listOf("reschedule", "mirror", "redraw", "settle"), steps)

        val success = ListenableWorker.Result.success()
        assertEquals(success, topUp(reschedule, refreshMirror, redraw, settleWatch = { error("the watch broke") }))
    }

    /**
     * `topUp` has no budget of its own, so the bound around the watch's settle is the worker's:
     * even with the sync's worker gone, the top-up costs no more than [WatchFeedSync.CALLER_BOUND].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aStoppedWorkerCostsTheTopUpOnlyItsBound() = runTest {
        val stopped = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val sync = WatchFeedSync(
            feedFor = { syncFeed() },
            link = FakeWatchLink(),
            memory = FakeWatchFeedMemory(),
            scope = stopped,
            work = StandardTestDispatcher(testScheduler),
        )
        stopped.cancel()
        sync.request(WatchSyncReason.RESCHEDULED)

        val result = topUp(reschedule, refreshMirror, redraw, settleWatch = { settleTheWatch { sync } })

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("reschedule", "mirror", "redraw"), steps)
        assertTrue(currentTime <= WatchFeedSync.CALLER_BOUND.inWholeMilliseconds, "returned at $currentTime ms")
    }
}
