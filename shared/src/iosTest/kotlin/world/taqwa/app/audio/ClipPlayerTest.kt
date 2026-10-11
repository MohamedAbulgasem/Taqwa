package world.taqwa.app.audio

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The end of a clip that cannot be played. `ClipPort`'s callers (the picker's audition, the
 * adhkar's first-run clips) mark themselves as playing and wait for [ClipPlayer.play]'s `onEnd`;
 * a player that swallowed a failure and never called it left them waiting in silence.
 */
class ClipPlayerTest {

    @Test
    fun `a clip whose bytes are not audio ends at once`() {
        var ended = 0

        ClipPlayer().play(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) { ended++ }

        assertEquals(1, ended)
    }

    @Test
    fun `an empty clip ends at once`() {
        var ended = 0

        ClipPlayer().play(ByteArray(0)) { ended++ }

        assertEquals(1, ended)
    }
}
