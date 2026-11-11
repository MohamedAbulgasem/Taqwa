package world.taqwa.app.wear.link

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.wear.TaqwaWearApp
import world.taqwa.app.wear.complication.ComplicationSources
import world.taqwa.app.wear.face.FACE_BOUND_MS
import world.taqwa.app.wear.face.FaceCapability
import world.taqwa.app.wear.face.FacePushes

/**
 * Ruling 22's safety net on the watch, and the daily handshake (plan 2 Task 6): once a day the
 * complication sources are asked to be requested again, so that what they drew ahead never runs
 * out unseen, and the watch shakes hands with its phone ([AskWhy.DAILY]), which heals a watch that
 * missed a change while away and came back without any capability changing.
 *
 * The alarm is inexact and on the elapsed clock without a wake-up: it never wakes the watch, it
 * needs no permission, and it runs when the watch is next awake.
 */
object DailyNudge {

    /**
     * The file in the app's files that holds the nudge's anchor, in epoch seconds: when the alarm
     * last went off, or when it was first set. The next nudge is due a day after it.
     */
    const val ANCHOR = "nudge.anchor"

    /** The nudge's period, in seconds. */
    internal const val DAY = 86_400L

    /**
     * How long the anchor may go without the alarm going off before the alarm is taken as lost, in
     * seconds: a day and a half. A live alarm that is this late is only overdue, and setting it due
     * now costs nothing; one that is lost is set again.
     */
    internal const val STALE = DAY + DAY / 2

    /**
     * What [arm] does when the alarm is to be set: due [inSeconds] from now, and [anchor] written as
     * the nudge's new anchor, or the anchor left as it is when null.
     */
    internal data class Arming(val inSeconds: Long, val anchor: Long?)

    /**
     * Whether, and how, the alarm is set at a process start at [now] (epoch seconds); null leaves it
     * as it is. The system cannot be asked whether the alarm is still set, so this reads what can be
     * seen:
     * - [found]: the alarm's `PendingIntent` is still there. It is gone after a reboot, and after a
     *   force stop from Android 15 on. On Wear OS 3 to 5 (API 30-34) a force stop removes the alarm
     *   but keeps the `PendingIntent`, so on its own it says nothing.
     * - [stoppedByUser]: the last process ended by `REASON_USER_REQUESTED`, a force stop or a clear
     *   data among others. A force stop while no process runs leaves no such reason. It can also be
     *   wrong after the clock was moved, because the system sorts its exit records by the clock.
     *   That is why a stop by the user sets the alarm on its own schedule: when it is wrong, nothing
     *   changes.
     * - [anchor]: the nudge's anchor, or null when none is kept (a new install, a clear data, or a
     *   build from before the anchor). An anchor more than [STALE] from [now], either way, is stale.
     *
     * A live alarm is left alone: [found], a fresh anchor and no stop by the user. Setting it again
     * at every start would push its next nudge out each time. Otherwise:
     * - with no anchor, the alarm is set a day away, anchored now, as at a first install;
     * - with a stale anchor, the alarm is lost or long overdue: it is set due now, anchored now;
     * - with a fresh anchor, it is set for when it was due, a day after the anchor, never later than
     *   a day from now and never before now. The anchor is kept, so a stop now and then never pushes
     *   the nudge out.
     */
    internal fun arming(found: Boolean, anchor: Long?, now: Long, stoppedByUser: Boolean): Arming? {
        val fresh = anchor != null && abs(now - anchor) <= STALE
        return when {
            found && fresh && !stoppedByUser -> null
            anchor == null -> Arming(DAY, now)
            !fresh -> Arming(0, now)
            else -> Arming((anchor + DAY - now).coerceIn(0, DAY), null)
        }
    }

    /**
     * Arms the daily alarm when [arming] says it is lost or not yet set: [TaqwaWearApp.onCreate] runs
     * it at every process start, off the main thread. A reboot clears alarms, and is a process start
     * too. `FLAG_NO_CREATE` finds the alarm's own `PendingIntent` without making one, and setting an
     * alarm with that same `PendingIntent` replaces the alarm, so there is never more than one.
     */
    fun arm(context: Context) {
        val intent = Intent(context, DailyNudgeReceiver::class.java)
        val found = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) != null
        val stopped = stoppedByUser(context)
        val anchorFile = File(context.filesDir, ANCHOR)
        val arming = arming(found, readStamp(anchorFile), System.currentTimeMillis() / 1000, stopped)
        if (arming == null) {
            feedLog("NUDGE kept")
            return
        }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + arming.inSeconds * 1000,
            AlarmManager.INTERVAL_DAY,
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE),
        )
        arming.anchor?.let { writeStamp(anchorFile, it) }
        feedLog("NUDGE armed in ${arming.inSeconds}s found=$found stopped=$stopped")
    }

    /** The alarm went off at [now] (epoch seconds): it is alive, and the next nudge is a day away. */
    fun wentOff(context: Context, now: Long) {
        writeStamp(File(context.filesDir, ANCHOR), now)
    }

    /** The nudge itself: the complications first, then the daily handshake; true when it went out. */
    suspend fun nudge(context: Context, now: Long): Boolean {
        ComplicationSources.requestUpdates(context, why = "daily")
        val app = context.applicationContext as TaqwaWearApp
        val asked = PhoneAsk(app, app.feedStore).askIfDue(AskWhy.DAILY, now)
        // Plan 5 Task 5: the face's word to the phone, said again once a day, so that the phone's
        // "Add the Taqwa face" row is at most a day stale.
        try {
            val push = FacePushes.of(app)
            withTimeoutOrNull(FACE_BOUND_MS) { push.state() }?.let { FaceCapability.publish(app, it, push.spent) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Tomorrow's nudge says it again.
        }
        return asked
    }

    /**
     * Whether the app's last process ended by the user's request ([ApplicationExitInfo]'s newest
     * record, API 30 on, the watch's own minimum). False when it cannot be read: the anchor still
     * heals.
     */
    private fun stoppedByUser(context: Context): Boolean =
        try {
            context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                ?.firstOrNull()
                ?.reason == ApplicationExitInfo.REASON_USER_REQUESTED
        } catch (_: RuntimeException) {
            false
        }
}

/**
 * The daily alarm's receiver. Not exported: only the app's own `PendingIntent` reaches it. It
 * anchors the next nudge a day from now, then runs the nudge off the main thread, inside
 * [PhoneAsk.BOUND], and holds the broadcast until then. Only this receiver writes the anchor when
 * the alarm goes off, so the debug build's nudge on demand never passes for the alarm.
 */
class DailyNudgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TaqwaWearApp
        val pending = goAsync()
        app.scope.launch {
            try {
                val now = System.currentTimeMillis() / 1000
                DailyNudge.wentOff(app, now)
                withTimeoutOrNull(PhoneAsk.BOUND) { DailyNudge.nudge(app, now) }
            } catch (_: Exception) {
                // Tomorrow's nudge asks again.
            } finally {
                pending.finish()
            }
        }
    }
}
