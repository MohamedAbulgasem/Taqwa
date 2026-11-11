package world.taqwa.app.wear.link

import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules

/** Why the watch would shake hands with its phone. */
internal enum class AskWhy {
    /** The app's process started. */
    STARTED,

    /** The app was opened. */
    OPENED,

    /** The phone's `taqwa_phone` capability came back nearby: a reconnect. */
    PHONE_NEARBY,

    /** The daily nudge. */
    DAILY,
}

/**
 * When the watch shakes hands (plan 2 Task 6, the Lead's call 1): its handshake echoes the delivery
 * it holds, and the phone sends the current feed unless the watch holds it. Each reason is damped by
 * the time since the last handshake that went out (`lastAskedAt`), so a link that flaps, or an app
 * opened again and again, asks once.
 */
internal object RefreshPolicy {

    /** The least time between two handshakes for every reason but the daily one, in seconds. */
    const val GAP_SHORT = 60L

    /** The least time between the last handshake and the daily one, in seconds. */
    const val GAP_DAILY = 3_600L

    /**
     * Whether to shake hands now ([now], epoch seconds), for [why], with [feed] stored (null for
     * none), [fromPhone] when its delivery is held (`FeedStore.delivery() != null`: a phone sent it),
     * and the last handshake that went out at [lastAskedAt] (null for none):
     * - [AskWhy.STARTED] when the watch holds no feed from the phone (none, or one without a
     *   delivery, as the debug sample is): the heal for a watch the phone cannot know is empty (a
     *   reinstalled app, a new watch, a reset), since the phone damps its own reconnect;
     * - [AskWhy.OPENED] when there is no feed or it needs one ([WatchRules.needsRefresh]: three days
     *   left, or a week old; spec 2.6's "each time the app opens" once it is spent);
     * - [AskWhy.PHONE_NEARBY] and [AskWhy.DAILY] always: the reconnect handshake, and the daily one,
     *   which heals a return that changed no capability.
     *
     * The daily one waits [GAP_DAILY] after the last handshake, the rest [GAP_SHORT]. A stamp ahead
     * of [now] (the watch's clock set back) holds nothing back: it says nothing of when the watch
     * last asked.
     */
    fun shouldAsk(why: AskWhy, feed: WatchFeed?, fromPhone: Boolean, now: Long, lastAskedAt: Long?): Boolean {
        val gap = if (why == AskWhy.DAILY) GAP_DAILY else GAP_SHORT
        if (lastAskedAt != null && lastAskedAt <= now && now - lastAskedAt < gap) return false
        return when (why) {
            AskWhy.STARTED -> feed == null || !fromPhone
            AskWhy.OPENED -> feed == null || WatchRules.needsRefresh(feed, now)
            AskWhy.PHONE_NEARBY, AskWhy.DAILY -> true
        }
    }
}
