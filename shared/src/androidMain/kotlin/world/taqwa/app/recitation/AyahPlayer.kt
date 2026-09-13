package world.taqwa.app.recitation

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The queue seen as *ayahs on the surah's clock*, for every control the app does not draw itself:
 * the media notification, the lock screen, a headset button, Bluetooth and Android Auto — and,
 * through the app's own `MediaController`, the player bar.
 *
 * The player underneath holds `[ayah, gap, ayah, gap, …]` (see [RecitationQueue]), and Media3's
 * own Previous and Next step one **item**. On a device that means the lock screen's Previous lands
 * on a 300 ms silence and runs straight back into the ayah it was meant to leave — so from the
 * lock screen you cannot go back an ayah at all, while the app's own bar, which goes through
 * [RecitationQueue], does it in one press. This class is what makes those two the same button.
 *
 * It does four things and nothing else:
 *
 * 1. **Previous and Next are ayah moves**, decided by [RecitationQueue] itself rather than by a
 *    second copy of the rule — previous within the first two seconds of an ayah goes back one,
 *    later restarts the ayah, and a press during a gap restarts the ayah that just ended.
 * 2. **The gaps are invisible.** While one plays, the current item reported is the ayah that just
 *    ended, so the notification neither blinks nor changes its title between ayahs.
 * 3. **Position and duration are the surah's** (spec §14.1) while a [timeline] is set: the lock
 *    screen's bar runs the length of the surah rather than restarting every few seconds, a scrub
 *    on it lands on the start of the ayah under the thumb, and the app's bar reads the same clock
 *    through its controller.
 * 4. **Previous and Next are always offered** while there is a queue, exactly as the bar offers
 *    them: at the last ayah Next does nothing, which is what the bar does too.
 *
 * The queue is rebuilt from the timeline rather than passed in: the gap items name themselves
 * ([SILENCE_SCHEME]), the service is the only thing holding the player, and a copy of the queue
 * kept on this side would be one more thing that can fall out of step with the app's. The clock
 * *is* passed in, with the queue's text, because it is read off the container the app has just
 * opened and the two sides must be reading the same one.
 */
@UnstableApi
class AyahPlayer(private val real: Player) : ForwardingPlayer(real) {

    /**
     * The surah's clock, sent by the app before it sets the queue. Honoured only while it has one
     * entry per item actually loaded ([clock]), so a clock from the last surah can never be read
     * against this one's items in the frames between two loads.
     */
    var timeline: SurahTimeline? = null

    /**
     * How long the ayah that is playing — or has just played — runs for. Held because a gap cannot
     * be asked for the duration of the item before it: the platform's clock during those 300 ms is
     * the silence's own. Only read when there is no [timeline].
     */
    private var lastAyahDurationMs = 0L

    init {
        real.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (onGap()) return
                val duration = real.duration
                if (duration != C.TIME_UNSET && duration > 0L) lastAyahDurationMs = duration
            }
        })
    }

    // ── what the session is told ────────────────────────────────────────────────────────────

    override fun getCurrentMediaItemIndex(): Int = ayahIndex()

    override fun getCurrentPeriodIndex(): Int = ayahIndex()

    override fun getCurrentMediaItem(): MediaItem? =
        if (onGap()) real.getMediaItemAt(ayahIndex()) else real.currentMediaItem

    override fun getCurrentPosition(): Long =
        onClock(real.currentPosition) { if (onGap()) lastAyahDurationMs else real.currentPosition }

    override fun getContentPosition(): Long =
        onClock(real.contentPosition) { if (onGap()) lastAyahDurationMs else real.contentPosition }

    override fun getBufferedPosition(): Long =
        onClock(real.bufferedPosition) { if (onGap()) lastAyahDurationMs else real.bufferedPosition }

    override fun getContentBufferedPosition(): Long =
        onClock(real.contentBufferedPosition) { if (onGap()) lastAyahDurationMs else real.contentBufferedPosition }

    override fun getDuration(): Long =
        clock()?.totalMs ?: if (onGap()) lastAyahDurationMs else real.duration

    override fun getContentDuration(): Long =
        clock()?.totalMs ?: if (onGap()) lastAyahDurationMs else real.contentDuration

    // The siblings Media3 bundles beside the six above. Left to the real player they would
    // describe the *item* next to numbers that describe the surah — a buffered percentage of
    // 100 beside a buffered position a third of the way along.
    override fun getBufferedPercentage(): Int {
        val clock = clock() ?: return real.bufferedPercentage
        return (bufferedPosition * 100L / clock.totalMs.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
    }

    override fun getTotalBufferedDuration(): Long =
        if (clock() != null) (bufferedPosition - currentPosition).coerceAtLeast(0L) else real.totalBufferedDuration

    /** Rewind and fast-forward move by ayah here ([seekBack], [seekForward]); a surface that
     * prints the increment inside its button must not print fifteen seconds. */
    override fun getSeekBackIncrement(): Long = if (queue() == null) real.seekBackIncrement else 0L

    override fun getSeekForwardIncrement(): Long = if (queue() == null) real.seekForwardIncrement else 0L

    /**
     * [itemPosition] on the surah's clock, or [fallback] when there is no clock to put it on.
     *
     * The item's slot on the clock is an estimate; the item's real length is known once it is
     * prepared. Where the two differ the position is scaled into the slot rather than clamped,
     * so the clock runs a shade fast or slow through that ayah and never stalls at the end of
     * the slot or jumps at the start of the next — which a variable-bit-rate ayah in a
     * constant-bit-rate edition would otherwise make it do.
     */
    private inline fun onClock(itemPosition: Long, fallback: () -> Long): Long {
        val clock = clock() ?: return fallback()
        val index = real.currentMediaItemIndex
        return clock.elapsed(index, SurahTimeline.fitToSlot(itemPosition, real.duration, clock.durationOf(index)))
    }

    // ── what the session may ask for ────────────────────────────────────────────────────────

    override fun getAvailableCommands(): Player.Commands {
        val commands = real.availableCommands
        if (queue() == null) return commands
        // Constant for the life of a queue, and deliberately so: a Next that comes and goes at the
        // last ayah would be a button moving under the reader's thumb, and the bar does not do
        // that either. At the last ayah it simply does nothing.
        return commands.buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean =
        queue()?.let { it.next(real.currentMediaItemIndex) != null } == true

    override fun hasPreviousMediaItem(): Boolean = queue() != null

    // ── the buttons and the bar ─────────────────────────────────────────────────────────────

    override fun seekToPrevious() = previousAyah()

    override fun seekToPreviousMediaItem() = previousAyah()

    override fun seekToNext() = nextAyah()

    override fun seekToNextMediaItem() = nextAyah()

    /** Rewind and fast-forward — a headset's long press, a car's dial — move by ayah too. A
     * recitation has no fifteen seconds to skip that would not land inside a word. */
    override fun seekBack() = previousAyah()

    override fun seekForward() = nextAyah()

    /**
     * A scrub on the lock screen's bar, which is on the surah's clock: it lands on the start of
     * the ayah under the thumb ([SurahTimeline.snapToAyah]), never inside one. Without a clock
     * the position is the item's own, as the platform means it.
     */
    override fun seekTo(positionMs: Long) {
        val clock = clock()
        val queue = queue()
        if (clock == null || queue == null) {
            real.seekTo(positionMs)
            return
        }
        real.seekTo(clock.snapToAyah(positionMs, queue::isGap), 0L)
    }

    private fun previousAyah() {
        val queue = queue() ?: return
        val at = real.currentMediaItemIndex
        // A gap's own clock is not the ayah's, so it counts as zero and the rule restarts the ayah
        // that has just been read — the same answer `RecitationPlayer.previous` gives the bar.
        val position = if (queue.isGap(at)) 0L else real.currentPosition
        real.seekTo(queue.previous(at, position), 0L)
    }

    private fun nextAyah() {
        val queue = queue() ?: return
        val target = queue.next(real.currentMediaItemIndex) ?: return
        real.seekTo(target, 0L)
    }

    // ── the queue and the clock, read off the timeline ──────────────────────────────────────

    /**
     * The same arithmetic the app's bar uses, over the same shape of queue. Only the count and
     * whether there are gaps can be read off a timeline, and only those two are needed: ayah
     * *numbers* never reach the session, and the indices are all this class moves between.
     *
     * Kept between calls: Media3 asks the getters above many times per state bundle, the app
     * polls four times a second on top, and a 571-item queue rebuilt on each ask is a lot of
     * garbage to make on the application thread to learn two integers.
     */
    private fun queue(): RecitationQueue? {
        val count = real.mediaItemCount
        if (count == 0) return null
        val gapped = count > 1 && isGapItem(1)
        cachedQueue?.let { if (cachedCount == count && cachedGapped == gapped) return it }
        val ayahs = if (gapped) (count + 1) / 2 else count
        return RecitationQueue(
            surah = 0,
            ayahs = (1..ayahs).toList(),
            gapMs = if (gapped) 1L else 0L,
        ).also {
            cachedQueue = it
            cachedCount = count
            cachedGapped = gapped
        }
    }

    private var cachedQueue: RecitationQueue? = null
    private var cachedCount = -1
    private var cachedGapped = false

    /** The clock, while it describes the queue that is actually loaded. */
    private fun clock(): SurahTimeline? {
        val count = real.mediaItemCount
        return timeline?.takeIf { count > 0 && it.size == count }
    }

    private fun isGapItem(index: Int): Boolean =
        real.getMediaItemAt(index).mediaId.startsWith("$SILENCE_SCHEME://")

    private fun onGap(): Boolean {
        val queue = queue() ?: return false
        return queue.isGap(real.currentMediaItemIndex)
    }

    /** The queue index of the ayah being heard: itself, or the one a gap follows. */
    private fun ayahIndex(): Int {
        val at = real.currentMediaItemIndex
        val queue = queue() ?: return at
        return if (queue.isGap(at)) (at - 1).coerceAtLeast(0) else at
    }
}
