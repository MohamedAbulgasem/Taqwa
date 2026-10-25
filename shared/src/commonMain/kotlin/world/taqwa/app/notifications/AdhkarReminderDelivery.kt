package world.taqwa.app.notifications

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What the Android receiver does with an adhkar reminder when its alarm fires (adhkar spec §5).
 * Kept out of the receiver, which has no host test, so the rules are pinned by one.
 *
 * The receiver applies [shouldPost] and [timeout] to every entry that carries a
 * [ScheduledNotification.windowEnd], whatever its kind (Duha and Iqama spec §3.4). So a late Duha
 * is never posted after its end, ten minutes before noon, and a late iqama reminder, possible only
 * where exact alarms are revoked, is never posted more than ten minutes after it was due. Only the
 * adhkar reminder is tagged ([tagFor]).
 *
 * The alarm is inexact by design, and from Android 12 the platform's bound on an inexact alarm is
 * "within an hour" of the time asked for. A morning reminder set near sunrise can therefore arrive
 * after sunrise, saying "Until sunrise" about a sunrise that has passed. It is dropped instead, and
 * one that arrives in time leaves the shade when its window closes rather than staying on to say
 * the same thing about a window that has.
 */
object AdhkarReminderDelivery {

    /** Whether a reminder whose window closes at [windowEnd] is still worth posting at [now]. */
    fun shouldPost(now: Instant, windowEnd: Instant): Boolean = now < windowEnd

    /** How long a reminder posted at [now] stays on screen: until [windowEnd]; null when it is not posted at all. */
    fun timeout(now: Instant, windowEnd: Instant): Duration? =
        if (shouldPost(now, windowEnd)) windowEnd - now else null

    /**
     * The tag an entry is posted under. An adhkar reminder carries its own id, so cancelling a
     * finished set finds it on screen by that id alone. That holds even after a reschedule has
     * forgotten the request code it was posted under, which every cold start can do. Every other
     * kind posts untagged, exactly as it always has.
     */
    fun tagFor(kind: NotificationKind, id: String): String? = if (kind.isAdhkar) id else null
}
