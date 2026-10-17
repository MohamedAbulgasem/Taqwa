package world.taqwa.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [followSurahSkip], the voice moving on to another surah (spec §15.1): a reader on the surah that
 * was playing moves with it, and follows it from the moment it opens, as a reader the voice opened
 * does; nothing else moves.
 */
class SurahSkipTest {

    @Test
    fun `a reader on the surah that played moves to the next one and follows it`() {
        val navigator = Navigator(Screen.Quran)
        navigator.push(Screen.Reader(112, 4))

        navigator.followSurahSkip(before = 112, now = 113)

        assertEquals(Screen.Reader(113, 1, followVoice = true), navigator.current)
    }

    @Test
    fun `a reader on another surah is left where it is`() {
        val navigator = Navigator(Screen.Quran)
        navigator.push(Screen.Reader(67, 10))

        navigator.followSurahSkip(before = 112, now = 113)

        assertEquals(Screen.Reader(67, 10), navigator.current)
    }

    /** The Mushaf follows by page on its own: its pages run across surahs. */
    @Test
    fun `a Mushaf is left to follow by page`() {
        val navigator = Navigator(Screen.Quran)
        navigator.push(Screen.Mushaf(604))

        navigator.followSurahSkip(before = 112, now = 113)

        assertEquals(Screen.Mushaf(604), navigator.current)
    }
}
