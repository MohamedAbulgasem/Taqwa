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
 */
@Stable
class FollowingState(private val now: () -> Long) {

    /** The ayah the pill is offering to return to, or null when it is not showing. */
    var pill: Int? by mutableStateOf(null)

    private var lastTouch = 0L
    private var selfMoving = false

    /** Called whenever the list or pager starts or stops moving. */
    fun moved() {
        if (!selfMoving) lastTouch = now()
    }

    /** The reader asked to go back, so following starts again immediately rather than in four
     * seconds' time. */
    fun rearm() {
        lastTouch = 0L
    }

    /** True when the reader's hand has been off the screen long enough for the page to move. */
    fun quiet(): Boolean = now() - lastTouch >= FOLLOW_GRACE_MS

    /** Runs a scroll of our own without it counting as the reader touching the screen. */
    suspend fun move(block: suspend () -> Unit) {
        selfMoving = true
        try {
            block()
        } finally {
            selfMoving = false
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

@Composable
fun rememberFollowing(): FollowingState =
    remember { FollowingState { Clock.System.now().toEpochMilliseconds() } }
