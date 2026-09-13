package world.taqwa.app.feature.recitation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Everything the reader and the Mushaf need from recitation, and nothing else — one parameter
 * apiece rather than six, because the two screens already take a dozen and neither of them should
 * grow a second constructor's worth of arguments for a feature that is one button and one
 * highlight.
 *
 * Defaulted throughout, so a screen composed without recitation (a test, a preview) behaves
 * exactly as it did before this slice.
 */
data class QuranRecitation(
    /** What the header button shows for the surah on screen. */
    val header: HeaderState = HeaderState.Idle,
    /** The ayah being recited, as `surah to ayah`, whichever surah the screen is showing. */
    val playing: Pair<Int, Int>? = null,
    /** True while the voice is going; false when it is paused on [playing]. */
    val live: Boolean = false,
    /** How much room the player bar is taking at the foot of the screen, so the last ayah of a
     * surah is never underneath it. */
    val barSpace: Dp = 0.dp,
    /** A tap on the header button, with the ayah the screen would start from — the reader's
     * first visible ayah, the Mushaf page's first. */
    val onHeader: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    /** "Play" on an ayah's own action row. */
    val onPlayAyah: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    /** The play/pause of an ayah that is already the one playing. */
    val onToggle: () -> Unit = {},
    /**
     * A request from outside the screen — the bar, the media notification — to show the ayah
     * being recited (spec §15.5): each new value is one request, and the screen scrolls to the
     * ayah and re-arms following, exactly as its own "Back to ayah" pill does. Zero is none.
     */
    val jumpToken: Int = 0,
) {
    /** The ayah of [surah] being recited, or null when the voice is elsewhere. */
    fun ayahIn(surah: Int): Int? = playing?.takeIf { it.first == surah }?.second
}

/** How long the reader leaves the list alone after the user has touched it (spec §5.3). */
const val FOLLOW_GRACE_MS = 4_000L

/** Where a followed ayah is put: a third of the way down the viewport, so the ayah before it is
 * still on screen and the eye has somewhere to have come from. */
const val FOLLOW_VIEWPORT_FRACTION = 3

/** The gap between the "Back to ayah" pill and the player bar under it. */
val PillGap: Dp = 12.dp
