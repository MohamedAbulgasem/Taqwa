package world.taqwa.app.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.watchfeed.FakeWatchFeedMemory
import world.taqwa.app.watchfeed.FakeWatchLink
import world.taqwa.app.watchfeed.WatchFeedSync
import world.taqwa.app.watchfeed.WatchSyncReason
import world.taqwa.app.watchfeed.settleTheWatch
import world.taqwa.app.watchfeed.syncFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The action-to-trigger mapping, and the receiver's body with its steps faked ([afterSystemEvent]):
 * the receiver itself needs a `Context` and a live container, but the mapping is the part that
 * silently breaks the adhan when it is wrong — an action missing here is an event the app ignores.
 *
 * The literal strings are deliberate. They are what the manifest's `<intent-filter>` carries, and
 * asserting against `Intent.ACTION_*` would only prove the file agrees with itself; this way a
 * filter and a `when` that drift apart show up here. `Intent.ACTION_TIME_CHANGED` really is
 * `"android.intent.action.TIME_SET"`.
 *
 * Lives in `androidHostTest` because the mapping is an Android concern; only plain functions are
 * touched, so no Android class is ever loaded.
 */
class SystemEventReceiverTest {

    @Test
    fun `boot completed asks for a full reschedule`() {
        assertEquals(
            RescheduleTrigger.BOOT_COMPLETED,
            rescheduleTriggerFor("android.intent.action.BOOT_COMPLETED"),
        )
    }

    @Test
    fun `an app update is a reboot for scheduling purposes`() {
        // Replacing the package keeps the old build's alarms, with its extras and channel ids, so a
        // Play auto-update has to rebuild the plan as a reboot does (audit B1; the adhan report).
        assertEquals(
            RescheduleTrigger.BOOT_COMPLETED,
            rescheduleTriggerFor("android.intent.action.MY_PACKAGE_REPLACED"),
        )
    }

    @Test
    fun `a clock change and a timezone change are told apart`() {
        assertEquals(RescheduleTrigger.TIME_SET, rescheduleTriggerFor("android.intent.action.TIME_SET"))
        assertEquals(
            RescheduleTrigger.TIMEZONE_CHANGED,
            rescheduleTriggerFor("android.intent.action.TIMEZONE_CHANGED"),
        )
    }

    @Test
    fun `a change to the exact alarm grant re-arms the plan as a settings change`() {
        // Alarms keep the exactness they were armed with; the grant arriving after the fact would
        // otherwise do nothing until the app was next opened.
        assertEquals(
            RescheduleTrigger.SETTINGS_CHANGED,
            rescheduleTriggerFor("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"),
        )
    }

    @Test
    fun `anything else is ignored rather than rescheduled`() {
        // PACKAGE_REPLACED is the broadcast about *other* apps and would need a data element to
        // be delivered at all; it must not be mistaken for MY_PACKAGE_REPLACED.
        assertNull(rescheduleTriggerFor("android.intent.action.PACKAGE_REPLACED"))
        assertNull(rescheduleTriggerFor("world.taqwa.app.PRAYER_ALARM"))
        assertNull(rescheduleTriggerFor(null))
    }

    /**
     * The receiver's body ([afterSystemEvent]) with its steps faked, on virtual time: a watch link
     * that hangs costs the reminders and the widgets nothing. The reschedule only asks the watch,
     * every widget step runs at once, and the watch is settled last, at the end of the ask's own
     * 5-second budget, well inside the receiver's 8 seconds. Only plain Kotlin is touched, so no
     * Android class is loaded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theWidgetsAreRedrawnWhileTheWatchLinkHangs() = runTest {
        val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
        val events = mutableListOf<Pair<String, Long>>()
        val sync = WatchFeedSync(
            feedFor = { delay(3.seconds); syncFeed() },
            link = FakeWatchLink(confirmsDelivery = true).apply { gate = CompletableDeferred() },
            memory = FakeWatchFeedMemory(),
            scope = backgroundScope,
            work = StandardTestDispatcher(testScheduler),
        )
        var settled: Pair<Boolean, Long>? = null

        withTimeout(8_000) {
            afterSystemEvent(
                reschedule = {
                    events += "armed" to currentTime
                    sync.request(WatchSyncReason.RESCHEDULED, london)
                },
                refreshMirror = { events += "mirror" to currentTime },
                redrawWidgets = { events += "widgets" to currentTime },
                redrawAyah = { events += "ayah" to currentTime },
                settleWatch = { settled = settleTheWatch { sync } to currentTime },
            )
        }

        assertEquals(listOf("armed" to 0L, "mirror" to 0L, "widgets" to 0L, "ayah" to 0L), events)
        assertEquals(true to 5_000L, settled, "settled once the ask's budget ended")
        assertTrue(currentTime < 8_000, "returned at $currentTime ms")
    }
}
