package world.taqwa.app.wear

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watch.WatchStoreStep

/** What [FeedStore.write] made of the text it was given. */
enum class FeedWriteResult {
    /** A feed this build reads. It is stored, and it is now the feed. */
    ACCEPTED,

    /**
     * A feed in a newer format than this build reads, so a phone that has been updated before the
     * watch. It is not stored: the feed already stored stays the feed, and
     * [FeedStore.newerVersionSeen] says the watch must be updated (spec 1.1 and 2.6).
     */
    NEWER_VERSION,

    /** Text that is not a feed, or is cut short. It is not stored, and nothing is said about it. */
    MALFORMED,

    /**
     * A feed whose delivery does not move the watch forward (Ruling 50): the same number of the
     * phone install the watch holds a feed of, or a lower one, so a duplicate or a message that
     * was overtaken. It is not even read: the feed, its delivery and the update notice stay as they
     * are, and nothing is acknowledged (`WatchLinkContract.stores`).
     */
    STALE,
}

/**
 * The feed the phone sent last, kept in [file] (spec 2.2). A watch away from its phone goes on
 * drawing from it for the weeks it holds, so it is a file and not something in memory: the process
 * that wrote it is long gone by the time anyone reads it.
 *
 * Only a feed this build reads is ever stored, and a feed that is stored is stored whole:
 *
 * - [write] first asks whether the delivery moves the watch forward ([FeedWriteResult.STALE] when
 *   not, before the text is read), then decodes the text and keeps the old feed unless the text is
 *   a feed. Text that is not one is [FeedWriteResult.MALFORMED], and a feed of a newer format than
 *   this build reads is [FeedWriteResult.NEWER_VERSION]; either way the feed that was stored still
 *   is.
 * - A feed is written to a temporary file beside [file], put on disk, and then renamed over [file].
 *   A rename is atomic: a reader sees the old feed or the new one and never a part of either, and
 *   a process killed in the middle leaves a temporary file that nothing reads and the next write
 *   replaces. The file is only ever replaced, never cut off and filled.
 *
 * Reading is not locked. [read] and [changes] never wait for a write, and a write never waits for
 * them; the writes wait for each other, so two of them cannot mix.
 *
 * [newerVersionSeen] is kept in a small file of its own too. The notice that the watch is out of
 * date is for a person who may look long after the process that learned of it has been ended, and
 * the notice has to survive that. It names the version it was for, so that once the watch has been
 * updated to a build that reads that version it no longer applies, without a new feed having to
 * arrive first. Keeping it is best effort: the watch says it is out of date for as long as the
 * process lives whether or not the file could be written.
 *
 * **The delivery** the phone sent the feed with (Ruling 50: the phone install's origin, the
 * delivery's number and the feed's fingerprint) is kept beside it in [deliveryFile], so that the
 * watch can say back what it holds, in its acknowledgement and its handshake, and store only what
 * moves it forward. The receivers' rules are `WatchLinkContract`'s, built on and never copied: a
 * write with a delivery is first put to [WatchLinkContract.stores], and a feed is stored in
 * [WatchLinkContract.STORE_STEPS]' order, its old delivery removed before the feed is put in place
 * and the new one written after. So a crash or a failed write in between leaves the watch holding
 * a feed and no delivery (its echo says nothing, and the phone sends once more), never a delivery
 * beside a feed it does not name. A feed stored without one, the debug build's sample, holds none.
 * The watch never works a fingerprint out for itself: one of its own re-encoding would not be the
 * phone's when the two apps' versions differ.
 *
 * [onStored] is told after each write that stored a feed, the feed already being the stored one,
 * so that whatever draws from the feed (the complication sources) is asked to draw again. It is
 * told of an identical feed too, which changes nothing a flow would carry but still means that the
 * phone is in reach and that what was drawn ahead of time is a little older than it was. It runs
 * outside the write's lock, and a listener that throws cannot undo or fail a feed that is stored.
 *
 * [onNewerVersion] is told after each write that answered [FeedWriteResult.NEWER_VERSION], the
 * store already saying so ([newerVersionSeen]), so that the Tile says "Update Taqwa on your watch"
 * at once rather than at its next refresh (plan 3 Task 8). It is told by the same rule as
 * [onStored]: outside the write's lock, and a listener that throws cannot fail the write that
 * reported the newer format. It comes before [onStored], so that a trailing lambda is still
 * [onStored], as every caller written before it means (`FeedStore(file) { … }`).
 */
class FeedStore(
    private val file: File,
    private val onNewerVersion: () -> Unit = {},
    private val onStored: () -> Unit = {},
) {

    /**
     * Where [write] puts a feed before it renames it over [file]: a name beside [file], on the same
     * file system, so that the rename is one. Nothing reads it. It is internal so that a test can
     * leave one behind, as a crash does.
     */
    internal val temporary: File = partialFileFor(file)

    /**
     * Holds the version of the newest feed that was turned away for being newer than this build
     * reads, when one was; it is removed when a feed this build reads is stored.
     */
    internal val newerNotice: File = File(file.path + ".newer")

    /**
     * Holds the delivery the stored feed came with, as [WatchDelivery.text], when it came with one;
     * there is none for a feed stored without one, and none for a moment while a feed is put in place.
     */
    internal val deliveryFile: File = File(file.path + ".delivery")

    private val writeLock = Any()
    private val feed = MutableStateFlow(readFeed())
    private val newer = MutableStateFlow(readNoticeVersion() > WatchFeedJson.VERSION)

    /**
     * The stored feed: what [file] held when the store was made, and after that what each accepted
     * [write] stored; null while there is none. It is what a surface draws from, and each accepted
     * write is what tells it to draw again.
     */
    val changes: StateFlow<WatchFeed?> = feed.asStateFlow()

    /**
     * Whether the phone has sent a feed in a newer format than this build reads, and no feed this
     * build reads has been stored since: the watch then says "Update Taqwa on your watch" and goes
     * on drawing from the last feed it understood (spec 2.6). Text that is no feed at all does not
     * change it.
     */
    val newerVersionSeen: StateFlow<Boolean> = newer.asStateFlow()

    /** The stored feed, read from [file] now; null when there is none or it cannot be read. */
    fun read(): WatchFeed? = readFeed()

    /**
     * The delivery the stored feed came with, read from [deliveryFile] now through
     * [WatchLinkContract.deliveryOf]; null when there is none, or the file cannot be read or holds
     * anything else.
     */
    fun delivery(): WatchDelivery? =
        try {
            WatchLinkContract.deliveryOf(deliveryFile.readText(Charsets.UTF_8))
        } catch (_: IOException) {
            null
        }

    /**
     * Stores [json], the text of a feed as the phone sent it, when this build reads it, and says
     * what it made of it. An [IOException] from the file system, a full disk say, is not made
     * into a result: it is thrown, with the feed that was stored still stored and no temporary
     * file left behind, for the caller to see. (The update notice is the exception: it is kept
     * if it can be, and a write that reports a newer feed does not fail for want of it.)
     *
     * [delivery] is the one the phone sent the feed with. When there is one and it does not move
     * the watch forward ([WatchLinkContract.stores] against [delivery]()), the write is
     * [FeedWriteResult.STALE] and touches nothing. A feed that is stored is stored in
     * [WatchLinkContract.STORE_STEPS]' order: the delivery held is removed (a removal that fails
     * is thrown, with the old feed and its delivery as they were), the feed is put in place, and
     * then [delivery] is written beside it, the way the feed is. A write of the delivery that fails
     * leaves none, and does not fail the write: the feed is stored. A feed stored without one (plan
     * 1's sample) is stored whatever the watch holds, and holds none. A feed that is not stored, a
     * newer or a broken one, leaves the delivery the stored feed's.
     */
    fun write(json: String, delivery: WatchDelivery? = null): FeedWriteResult {
        val result = synchronized(writeLock) {
            if (delivery != null && !WatchLinkContract.stores(delivery(), delivery)) return FeedWriteResult.STALE
            val accepted = WatchFeedJson.decode(json)
            // decode answers null for a newer feed and for a broken one alike; the version tells them apart.
            val newerVersion =
                if (accepted == null) WatchFeedJson.versionOf(json)?.takeIf { it > WatchFeedJson.VERSION } else null
            when {
                accepted != null -> {
                    for (step in WatchLinkContract.STORE_STEPS) {
                        when (step) {
                            WatchStoreStep.REMOVE_DELIVERY -> Files.deleteIfExists(deliveryFile.toPath())
                            WatchStoreStep.PLACE_FEED -> replaceAtomically(file, temporary, json)
                            WatchStoreStep.WRITE_DELIVERY -> if (delivery != null) writeDelivery(delivery)
                        }
                    }
                    feed.value = accepted
                    // The feed is stored: nothing past this point may fail the write. The notice was
                    // for a feed this build could not read, and this one it could.
                    newerNotice.delete()
                    newer.value = false
                    FeedWriteResult.ACCEPTED
                }
                newerVersion != null -> {
                    // Said first: the watch is out of date whether or not the notice can be kept for
                    // the next process, and a full disk must not make it say otherwise.
                    newer.value = true
                    try {
                        replaceAtomically(newerNotice, partialFileFor(newerNotice), newerVersion.toString())
                    } catch (_: IOException) {
                        // Best effort: without the file the notice lasts as long as this process does.
                    }
                    FeedWriteResult.NEWER_VERSION
                }
                else -> FeedWriteResult.MALFORMED
            }
        }
        when (result) {
            FeedWriteResult.ACCEPTED -> tell(onStored)
            FeedWriteResult.NEWER_VERSION -> tell(onNewerVersion)
            else -> Unit
        }
        return result
    }

    /**
     * Forgets everything the store holds, for the debug build's `--ez clear true`
     * (`DebugFeedReceiver`) and for tests: under the write lock, it deletes the feed, the update
     * notice and the delivery, and each one's temporary file, then says there is no feed and no
     * newer format. A watch cleared this way is a watch that has never held a feed (spec 2.6).
     *
     * It tells no listener ([onStored] is for a feed stored, [onNewerVersion] for a newer one
     * turned away): the debug receiver asks for the redraws itself. A file that cannot be deleted
     * is thrown, as [write] throws, and the store then still says what it said.
     */
    internal fun clear() {
        synchronized(writeLock) {
            for (held in listOf(file, temporary, newerNotice, partialFileFor(newerNotice), deliveryFile, partialFileFor(deliveryFile))) {
                Files.deleteIfExists(held.toPath())
            }
            feed.value = null
            newer.value = false
        }
    }

    /**
     * The last of the store's steps: best effort, since the feed is already stored and nothing past
     * that may fail the write. Without it the watch holds no delivery, and the phone sends once more.
     */
    private fun writeDelivery(delivery: WatchDelivery) {
        try {
            replaceAtomically(deliveryFile, partialFileFor(deliveryFile), delivery.text)
        } catch (_: IOException) {
            // No delivery: the echo says nothing, never another feed's number.
        }
    }

    private fun tell(listener: () -> Unit) {
        try {
            listener()
        } catch (_: Exception) {
            // The write is done, and a listener that cannot be told is the listener's to report.
        }
    }

    private fun readFeed(): WatchFeed? =
        try {
            WatchFeedJson.decode(file.readText(Charsets.UTF_8))
        } catch (_: IOException) {
            null
        }

    private fun readNoticeVersion(): Int =
        try {
            newerNotice.readText(Charsets.UTF_8).trim().toIntOrNull() ?: 0
        } catch (_: IOException) {
            0
        }

    /**
     * Puts [text] in [partial], on disk, and renames [partial] over [target]. The rename is the
     * only moment [target] changes. Whatever [partial] held before, from a write that was cut
     * short, is replaced.
     */
    private fun replaceAtomically(target: File, partial: File, text: String) {
        target.absoluteFile.parentFile?.mkdirs()
        try {
            FileOutputStream(partial).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            partial.delete()
            throw e
        }
    }

    private fun partialFileFor(target: File): File = File(target.path + ".tmp")
}
