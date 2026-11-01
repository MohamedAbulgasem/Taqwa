package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a card's steps make themselves known as they open ([stepsOpening]). Under a screen reader
 * they are made known by moving its focus into them, and only when the reader's own tap opened
 * them; never by a live region, which would read them out wherever the focus is. Without a screen
 * reader nothing takes focus, since moving focus scrolls (the owner's rule: the screen is unchanged
 * at default settings), and steps that replace a refused button are a polite live region.
 */
class StepsOpeningTest {

    @Test
    fun `without a screen reader a refused panel is read out and moves no focus`() {
        assertEquals(
            StepsOpening(focus = null, readOut = true),
            stepsOpening(refused = true, screenReader = false, openedByThisTap = true),
        )
    }

    @Test
    fun `under a screen reader the refused panel the tap opened takes focus at why and is not read out`() {
        assertEquals(
            StepsOpening(focus = PanelFocus.WHY, readOut = false),
            stepsOpening(refused = true, screenReader = true, openedByThisTap = true),
        )
    }

    @Test
    fun `under a screen reader Show me how takes focus to the first step and is not read out`() {
        assertEquals(
            StepsOpening(focus = PanelFocus.FIRST_STEP, readOut = false),
            stepsOpening(refused = false, screenReader = true, openedByThisTap = true),
        )
    }

    /**
     * The Lead's follow-up 1 (31 Oct): a refused panel that took focus had no live region, and
     * gained one as soon as another card's refusal or "Show me how" moved the focus away from it,
     * so TalkBack could read it out over the steps it had just been taken to. The same panel built
     * again after a rotation, which loses the tap, is not read out either.
     */
    @Test
    fun `under a screen reader a refused panel the tap did not just open is never read out`() {
        assertEquals(
            StepsOpening(focus = null, readOut = false),
            stepsOpening(refused = true, screenReader = true, openedByThisTap = false),
        )
    }

    @Test
    fun `without a screen reader Show me how moves no focus and is not read out`() {
        assertEquals(
            StepsOpening(focus = null, readOut = false),
            stepsOpening(refused = false, screenReader = false, openedByThisTap = true),
        )
    }

    @Test
    fun `steps open from the start neither take focus nor are read out`() {
        for (screenReader in listOf(false, true)) {
            assertEquals(
                StepsOpening(focus = null, readOut = false),
                stepsOpening(refused = false, screenReader = screenReader, openedByThisTap = false),
            )
        }
    }
}
