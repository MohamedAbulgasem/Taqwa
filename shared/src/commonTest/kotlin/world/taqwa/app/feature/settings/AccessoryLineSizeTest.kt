package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The size the Lock Screen picture sets each of its lines at (the Lead's follow-up 2, 31 Oct), as
 * the real accessory sets them with `.minimumScaleFactor(0.6)`: at its own size while it fits on
 * one line, shrunk in half points to the largest size that fits otherwise, and never below 60 % of
 * its size, where a line still too long wraps rather than being cut.
 */
class AccessoryLineSizeTest {

    @Test
    fun `a line that fits keeps its size`() {
        assertEquals(13f, accessoryLineSize(13f) { true })
    }

    @Test
    fun `a line too long shrinks to the largest size at which it fits`() {
        assertEquals(10f, accessoryLineSize(13f) { it <= 10.2f })
    }

    @Test
    fun `a line too long even at sixty percent is set at sixty percent and wraps`() {
        val tried = mutableListOf<Float>()
        assertEquals(13f * AccessoryMinScale, accessoryLineSize(13f) { tried += it; false }, 0.001f)
        assertEquals(13f * AccessoryMinScale, tried.min(), 0.5f)
        assertEquals(true, tried.all { it >= 13f * AccessoryMinScale })
    }

    @Test
    fun `it shrinks in half points`() {
        val tried = mutableListOf<Float>()
        accessoryLineSize(11f) { tried += it; false }
        assertEquals(listOf(11f, 10.5f, 10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f), tried)
    }
}
