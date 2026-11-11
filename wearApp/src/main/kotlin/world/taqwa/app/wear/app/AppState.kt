package world.taqwa.app.wear.app

import androidx.annotation.StringRes
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.wear.R

/**
 * What the watch app's pages are drawn from (spec 2.6, the Lead's call 5): no feed, or a feed with
 * what may need saying over it. It is plan 1's status screen's rule (`statusHeadline`, Ruling 13),
 * unchanged: a newer format outranks the rest, with a feed or without.
 */
internal sealed interface AppState {

    /**
     * No feed is held. Every page and the Tile say the watch's own line ([noFeedLine]), in the
     * watch's language: "Update Taqwa on your watch" when the phone has already sent a format this
     * build cannot read ([update]), so that the wearer is not sent to the phone; else "Open Taqwa on
     * your phone".
     */
    data class NoFeed(val update: Boolean) : AppState

    /**
     * A feed is held, and every page draws from it. [spent] says it holds no start after now, so
     * Today and the Tile have nothing to count down to (Tasbeeh and Qibla read no time and go on as
     * usual); [update] says a newer format has been seen, and the last feed is still drawn from.
     */
    data class Ready(val feed: WatchFeed, val spent: Boolean, val update: Boolean) : AppState
}

/**
 * The state [feed] and [newerVersionSeen] (`FeedStore.changes` and `newerVersionSeen`) give at
 * [now], in epoch seconds: no feed, or [ready].
 */
internal fun appState(feed: WatchFeed?, newerVersionSeen: Boolean, now: Long): AppState =
    if (feed == null) AppState.NoFeed(update = newerVersionSeen) else ready(feed, newerVersionSeen, now)

/** The state of a page that holds [feed]: spent when the feed has no start after [now] (`WatchRules.moment`). */
internal fun ready(feed: WatchFeed, newerVersionSeen: Boolean, now: Long): AppState.Ready =
    AppState.Ready(feed, spent = WatchRules.moment(feed, now) == null, update = newerVersionSeen)

/** The watch's own line for a watch with no feed, from its resources, in the watch's language. */
@StringRes
internal fun noFeedLine(update: Boolean): Int =
    if (update) R.string.update_taqwa_on_watch else R.string.open_taqwa_on_phone

/**
 * What Today and the Tile say in place of a part of what they draw, in the feed's words: the update
 * when a newer format has been seen, spent or not; else "Open Taqwa on your phone" once the feed is
 * spent; else nothing.
 */
internal fun AppState.Ready.notice(words: WatchScreenWords): String? = when {
    update -> words.updateOnWatch
    spent -> words.openOnPhone
    else -> null
}
