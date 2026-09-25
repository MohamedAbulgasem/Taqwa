package world.taqwa.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The action-to-trigger mapping, and nothing else: the receiver's body needs a `Context` and a
 * live container, but the mapping is the part that silently breaks the adhan when it is wrong —
 * an action missing here is an event the app ignores.
 *
 * The literal strings are deliberate. They are what the manifest's `<intent-filter>` carries, and
 * asserting against `Intent.ACTION_*` would only prove the file agrees with itself; this way a
 * filter and a `when` that drift apart show up here. `Intent.ACTION_TIME_CHANGED` really is
 * `"android.intent.action.TIME_SET"`.
 *
 * Lives in `androidHostTest` because the mapping is an Android concern; only the pure function is
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
        // Replacing the package drops every AlarmManager entry the app owns, exactly as a reboot
        // does, so a Play auto-update has to rebuild the plan the same way (audit B1).
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
}
