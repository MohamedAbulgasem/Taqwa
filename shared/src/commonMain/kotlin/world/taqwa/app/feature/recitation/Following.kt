package world.taqwa.app.feature.recitation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Clock

/** What to do when the voice moves to an ayah (spec §5.3). */
enum class Follow {
    /** Bring it into view. */
    SCROLL,

    /** Too far to drag the reader back without warning: offer the way instead. */
    PILL,

    /** The reader's hand was on the screen a moment ago. Their reading wins. */
    LEAVE_ALONE,
}

/**
 * Whether the page may follow the voice, and the "Back to ayah" offer when it may not (spec §5.3).
 *
 * Two rules, and the second is the one that matters: **the app never moves the page under a
 * finger that has just been on it.** Four seconds after the last touch, following resumes; a
 * reader who has gone more than a screen away is not dragged back at all, because at that
 * distance a scroll is not "following", it is the app deciding where they should be reading.
 *
 * [selfMoving] is why a programmatic scroll does not count as a touch. Both `animateScrollToItem`
 * and `animateScrollToPage` set `isScrollInProgress`, so without this the very act of following
 * would stamp the clock and stop the next ayah from being followed — the bug shows up as
 * following that works exactly once.
 *
 * [ownStop] is the same rule for the end of that scroll. The list says it has stopped through a
 * `snapshotFlow`, which hears it a frame later, after [move] has already returned; that stop used
 * to count as a touch, so for four seconds after every scroll of its own the page stayed put. On
 * the S23 an ayah that came within those four seconds (short ones, and every ayah with read-aloud
 * off in Juz ʿAmma) was not followed at all.
 *
 * [armedOnEntry] is the start of the screen's life. The list reports that it is not moving as
 * soon as it is laid out, before anything has moved. A screen opened at the voice (the bar's words,
 * the media notification, an iOS return with a voice going, the voice moving on to the next surah
 * (followSurahSkip)) is armed: that report is not a touch, and it follows at once. A screen opened any other way (a widget tap, a bookmark, Continue, the
 * lists, a search hit, the mode toggle) was opened by a touch, and the report counts as that touch:
 * armed, a Mushaf opened a page from the voice turned to the voice's page as it opened, leaving the
 * widget's ayah behind and the saved place overwritten. After that first report, a stop counts only
 * after a start that was somebody else's, or a scroll of ours that a finger took over.
 */
@Stable
class FollowingState(armedOnEntry: Boolean = false, private val now: () -> Long) {

    /** The ayah the pill is offering to return to, or null when it is not showing. */
    var pill: Int? by mutableStateOf(null)

    private var lastTouch = 0L
    private var selfMoving = false
    private var ownStop = false
    private var othersMoving = !armedOnEntry

    /** Called whenever the list or pager starts or stops moving: [scrolling] is which. */
    fun moved(scrolling: Boolean) {
        if (selfMoving) return
        if (!scrolling && ownStop) {
            // The end of a scroll of our own, heard after [move] returned.
            ownStop = false
            return
        }
        // Nothing of anyone's was moving: the list's first report as it is laid out, on a screen
        // opened at the voice.
        if (!scrolling && !othersMoving) return
        // A start is somebody else's scroll, and any stop still owed to one of ours was never
        // going to be heard: it ended within a frame.
        othersMoving = scrolling
        ownStop = false
        lastTouch = now()
    }

    /** The reader asked to go back, so following starts again immediately rather than in four
     * seconds' time. */
    fun rearm() {
        lastTouch = 0L
    }

    /** True when the reader's hand has been off the screen long enough for the page to move. */
    fun quiet(): Boolean = now() - lastTouch >= FOLLOW_GRACE_MS

    /**
     * Runs a scroll of our own without it counting as the reader touching the screen. Only a
     * scroll that finishes leaves its stop to be ignored: one cancelled part way was taken over,
     * most often by a finger, and the stop that comes next is that finger's.
     */
    suspend fun move(block: suspend () -> Unit) {
        selfMoving = true
        var finished = false
        try {
            block()
            ownStop = true
            finished = true
        } finally {
            selfMoving = false
            // Cancelled part way: a finger took the scroll over, and its stop is a touch.
            if (!finished) othersMoving = true
        }
    }

    /**
     * The rule itself. [away] is how far the ayah is beyond what is on screen, in screenfuls:
     * zero while it is visible, one for the screen either side of it.
     */
    fun decide(away: Int): Follow = when {
        away > 1 -> Follow.PILL
        !quiet() -> Follow.LEAVE_ALONE
        else -> Follow.SCROLL
    }
}

/**
 * Where a followed ayah's card comes to rest (spec §5.3): how far below the top of the list its
 * top edge lands, in pixels.
 *
 * A card that fits below the resting line rests there, a third of the way down, as every card
 * used to. A longer one rises until its foot clears the player bar: an ayah with its translation
 * is on screen whole while it is recited, instead of running under the bar until the reader
 * scrolls it up by hand. It rises no higher than [edge], so a card taller than the screen starts
 * at the top and the rest of it is the reader's to scroll, as it always was.
 *
 * @param rest the resting line.
 * @param room how far down the list a card may reach before the player bar covers it.
 * @param card the card's height, or null when it has not been laid out yet.
 * @param edge the highest a card goes: the 8 dp the reader keeps above an ayah it jumps to.
 */
fun followOffset(rest: Int, room: Int, card: Int?, edge: Int): Int =
    if (card == null) rest else minOf(rest, maxOf(edge, room - card))

/**
 * How long after the reader's scroll has settled the "more than a viewport away" question is asked
 * again (spec §5.3).
 *
 * The rule used to be evaluated only when the *voice* moved to a new ayah, which meant the pill
 * appeared at the next ayah boundary rather than when the reader actually scrolled away — and
 * Al-Baqarah 282 runs for minutes, so the reader could be several screens from the recitation with
 * nothing on screen offering the way back. Scrolling is now an event of its own; the delay is what
 * keeps the pill from flickering in and out under a moving thumb.
 */
const val PILL_SETTLE_MS = 300L

/** [armedOnEntry]: the screen was opened at the voice (see [FollowingState]). */
@Composable
fun rememberFollowing(armedOnEntry: Boolean = false): FollowingState =
    remember { FollowingState(armedOnEntry) { Clock.System.now().toEpochMilliseconds() } }
