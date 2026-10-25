package world.taqwa.app.design.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The settings reveal (Duha and Iqama spec §4.3): rows that follow a switch slide and fade in and
 * out, and not at all when the system asks for reduced motion.
 */
class SettingsRevealTest {

    @Test
    fun reducedMotionGivesNoTransitions() {
        assertEquals(EnterTransition.None, revealEnter(reduceMotion = true))
        assertEquals(ExitTransition.None, revealExit(reduceMotion = true))
    }

    @Test
    fun otherwiseTheRevealMoves() {
        assertNotEquals(EnterTransition.None, revealEnter(reduceMotion = false))
        assertNotEquals(ExitTransition.None, revealExit(reduceMotion = false))
    }
}
