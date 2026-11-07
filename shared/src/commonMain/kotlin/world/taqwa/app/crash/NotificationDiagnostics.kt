package world.taqwa.app.crash

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * What the phone says about Taqwa's notifications, for the support email (the adhan report of
 * 7 Nov 2026). "The adhan doesn't sound" can be a notification that never came or one that came
 * silently, and each has its own cause: a switch in the phone's settings, its battery manager
 * stopping the app, the level chosen for the prayer, or the app itself. Two short lines tell them
 * apart as far as two lines can; the owner limited them to two.
 *
 * Read when the email is written, from the system and from the one moment the app notes for
 * itself; nothing here identifies a person. None of the person's own Taqwa settings is in it, not
 * even the prayer's level: About, the site and the policy say settings never leave the phone, so
 * support asks for the level in its reply. Times are relative to the moment of writing and no
 * finer than the hour: the mail carries its own send time, so "47 min ago" beside it would give a
 * prayer's clock time, and with it roughly the place.
 *
 * Null wherever a fact could not be read or the platform has no such thing, and a null fact
 * prints nothing: a platform that reports nothing sends exactly the email it always has.
 */
data class NotificationState(
    /** The system's switch for the whole app; from Android 13 it is the notification permission. */
    val notificationsAllowed: Boolean? = null,
    val exactAlarmsAllowed: Boolean? = null,
    /** True while the system optimises the app's battery use, which is its default. */
    val batteryOptimised: Boolean? = null,
    /** The phone's "Restricted" battery setting for the app; it outranks the two above. */
    val backgroundRestricted: Boolean? = null,
    /** Android's ringer mode: 0 silent, 1 vibrate, 2 normal. */
    val ringerMode: Int? = null,
    /** Android's interruption filter: 1 is Do Not Disturb off; 2, 3 and 4 are its three kinds of on. */
    val interruptionFilter: Int? = null,
    /** Whether this platform notes the last prayer alarm, so that none recorded is a finding. */
    val recordsAlarms: Boolean = false,
    /** The last prayer alarm that reached the app and posted, whatever the system then did with it. */
    val lastAlarm: RecordedAlarm? = null,
    /** When this build was installed or last updated, so that "none recorded" can be read. */
    val appUpdatedAt: Instant? = null,
)

/**
 * The last prayer alarm, as the app noted it: when it reached the app, and whether its channel was
 * still missing after the post, which means Android dropped it. Not its level, a setting of the
 * person's own (see [NotificationState]).
 */
data class RecordedAlarm(val at: Instant, val channelMissing: Boolean = false)

/** Read from the platform; null where it reports none of this (iOS, for now). */
expect fun notificationState(): NotificationState?

/** The lines for the email, or none at all if reading the state is what failed. */
fun notificationDiagnosticsOrNone(now: Instant = Clock.System.now()): List<String> =
    runCatching { notificationState()?.let { NotificationDiagnostics.lines(it, now) } }.getOrNull() ?: emptyList()

object NotificationDiagnostics {

    /**
     * At most two `key: value` lines, as the device lines above them are: the phone's settings
     * now, then what has happened since the update.
     *
     * `notifications: on, exact alarms: off, battery: restricted, ringer: vibrate`
     * `last alarm: 3 d ago, app updated: 5 d ago`
     */
    fun lines(state: NotificationState, now: Instant): List<String> = listOfNotNull(
        settingsLine(state),
        historyLine(state, now),
    )

    private fun settingsLine(state: NotificationState): String? = listOfNotNull(
        state.notificationsAllowed?.let { "notifications: ${onOff(it)}" },
        state.exactAlarmsAllowed?.let { "exact alarms: ${onOff(it)}" },
        battery(state)?.let { "battery: $it" },
        ringer(state)?.let { "ringer: $it" },
    ).joinToString(", ").ifEmpty { null }

    private fun historyLine(state: NotificationState, now: Instant): String? = listOfNotNull(
        if (state.recordsAlarms) "last alarm: " + (state.lastAlarm?.let { alarm(it, now) } ?: NONE_RECORDED) else null,
        state.appUpdatedAt?.let { "app updated: ${ago(it, now)}" },
    ).joinToString(", ").ifEmpty { null }

    private const val NONE_RECORDED = "none recorded"

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    /** "Restricted" stops the app in the background whatever the optimisation says, so it wins. */
    private fun battery(state: NotificationState): String? = when {
        state.backgroundRestricted == true -> "restricted"
        else -> state.batteryOptimised?.let { if (it) "optimised" else "not optimised" }
    }

    /** Do Not Disturb silences the adhan whatever the ringer says, so it wins. */
    private fun ringer(state: NotificationState): String? = when {
        state.interruptionFilter?.let { it in DND_ON } == true -> "do not disturb"
        else -> state.ringerMode?.let { ringerName(it) }
    }

    private val DND_ON = setOf(2, 3, 4)

    private fun ringerName(code: Int) = when (code) {
        0 -> "silent"
        1 -> "vibrate"
        2 -> "normal"
        else -> code.toString()
    }

    /** "3 d ago", and " (no channel)" when Android had no channel to post it into. */
    private fun alarm(a: RecordedAlarm, now: Instant) =
        ago(a.at, now) + if (a.channelMissing) " (no channel)" else ""

    /** A moment, to the hour at best (see [NotificationState]); a future one after a clock change. */
    private fun ago(at: Instant, now: Instant): String =
        if (at <= now) "${coarse(now - at)} ago" else "in ${coarse(at - now)}"

    /** No finer than the hour, so that no moment can be put back on the clock. */
    private fun coarse(d: Duration): String = when {
        d < 1.hours -> "under 1 h"
        d < 2.days -> "${d.inWholeHours} h"
        else -> "${d.inWholeDays} d"
    }
}
