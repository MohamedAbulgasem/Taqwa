package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchDelivery

/**
 * What a send over the link came to, and what an ask of [WatchFeedSync] came to.
 *
 * - [SENT]: handed to the platform. On Android only queued (`MessageClient`'s success); on the
 *   iPhone the system keeps it until the watch can take it.
 * - [UNCHANGED]: the watch is known to hold this very feed, so nothing was sent.
 * - [NO_WATCH]: no watch with Taqwa is there. As an ask's answer to the cheap question, asked
 *   before the feed is built, nothing was built either; a send's own answer comes after.
 * - [NO_FEED]: there is nothing to send: no location, or a day the engine cannot give.
 * - [FAILED]: anything else, a budget cut short among it. Never a throw.
 * - [TOO_LARGE]: the link's own answer for a feed over its limit. An ask never ends in it: the
 *   sync sends the feed again without its phrases.
 */
internal enum class WatchSendResult { SENT, UNCHANGED, NO_WATCH, NO_FEED, FAILED, TOO_LARGE }

/**
 * One way to the user's own watch: the Wear Data Layer on Android (plan 2 Task 5) and
 * WatchConnectivity on the iPhone (Task 8).
 */
internal interface WatchLink {
    /**
     * Whether only the watch's echo confirms a delivery (Android, where a send is only queued:
     * the Lead's call 1); false where the platform itself delivers the latest feed it was handed
     * and nothing older after it (the iPhone's application context), so a send that got through
     * confirms its number (Ruling 50 item 4).
     */
    val confirmsDelivery: Boolean

    /**
     * Runs [block], one ask of [WatchFeedSync], inside the ask's budget (Ruling 54 item 4). A link
     * that reads the watches may read them once in it and answer [reachable] and [watchKeys] from
     * that one reading: Android's does. That reading is taken before the feed is built, so a watch
     * switched to during the build is sent the feed only at an ask of its own: its capability
     * change once the old watch has left, otherwise its handshake (`WatchFeedSync` step 4's known
     * limit). By default nothing is read here and [block] simply runs.
     */
    suspend fun <T> oneAsk(block: suspend () -> T): T = block()

    /**
     * The cheap question, asked before anything is built: is a watch with Taqwa there? The ask's
     * one capability reading on Android, one property read on the iPhone.
     */
    suspend fun reachable(): Boolean

    /**
     * The key of the one watch a send now goes to (Ruling 50, amendment A3): on the iPhone a random
     * UUID the bridge keeps in that watch's own `WCSession.watchDirectoryURL` (written when
     * absent; iOS deletes the directory on unpair or app removal), so a confirmation counts only
     * for the watch it was made for. Null when there is none to read, and on Android, where a send
     * goes to every nearby watch ([watchKeys]).
     */
    suspend fun watchKey(): String?

    /**
     * The keys of every watch a send now goes to, read once where an ask needs them: a confirmation
     * counts only while its watch is one of them (amendment A3, Ruling 52 item 9). By default the
     * one [watchKey]: the iPhone's session watch, from which alone an echo is taken in. Android's
     * link answers the node ids of the nearby watches with Taqwa, so a watch switched to between
     * asks never inherits another's confirmation; there every echo is taken in, for the node it
     * came from.
     */
    suspend fun watchKeys(): Set<String?> = setOf(watchKey())

    /**
     * Sends [json], the feed's UTF-8 JSON, with [delivery] beside it (Ruling 50): the path after
     * `/taqwa/feed/` on Android, the context's `delivery` key on the iPhone. Answers only
     * [WatchSendResult.SENT], [WatchSendResult.NO_WATCH], [WatchSendResult.FAILED] or
     * [WatchSendResult.TOO_LARGE].
     *
     * The delivery's number is on record before this is called, so a send cut off, thrown or
     * refused never leaves it to be used again. Only `SENT` may confirm, and only on a link that
     * does not confirm by echo: every other answer means the feed was handed over to nothing, or
     * may not have been (Tasks 5 and 8 keep that contract).
     */
    suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult
}

/** No watch, ever: the link until a platform's sender is built, and never confirming. */
internal object NoWatchLink : WatchLink {
    override val confirmsDelivery: Boolean = false

    override suspend fun reachable(): Boolean = false

    override suspend fun watchKey(): String? = null

    override suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult = WatchSendResult.NO_WATCH
}

/** The platform's link to the user's own watch. */
internal expect fun createWatchLink(): WatchLink
