package world.taqwa.app.feature.recitation

import androidx.compose.ui.unit.dp
import world.taqwa.app.recitation.Reciter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The room a screen keeps clear at its foot for the player bar (spec 3a §5.3). */
class PlayerBarSpaceTest {

    private val reciter = Reciter(
        id = "ar.alafasy",
        nameEn = "Mishary Rashid Alafasy",
        nameAr = "مشاري راشد العفاسي",
        style = "murattal",
        kbps = 64,
        gapMs = 300,
        hue = "amber",
        release = "audio-ar.alafasy-v1",
        totalBytes = 1L,
    )

    private val bar = BarState(reciter, surah = 114, ayah = 1, ayahCount = 6, fraction = 0f, playing = true, buffering = false)

    @Test
    fun `a bar on the Quran tab takes its whole height`() {
        assertTrue(playerBarShown(bar, onQuranTab = true))
        assertEquals(PlayerBarHeight, playerBarSpace(bar, onQuranTab = true))
        assertEquals(83.dp, playerBarSpace(bar, onQuranTab = true))
    }

    @Test
    fun `a bar waiting on a download takes the strip as well`() {
        val waiting = bar.copy(incoming = IncomingDownload(surah = 1, reciter = reciter, fraction = 0.5f))
        assertEquals(PlayerBarHeight + IncomingStripHeight, playerBarSpace(waiting, onQuranTab = true))
    }

    @Test
    fun `no bar takes no room`() {
        assertFalse(playerBarShown(null, onQuranTab = true))
        assertEquals(0.dp, playerBarSpace(null, onQuranTab = true))
    }

    @Test
    fun `a bar hidden on Prayer and Settings takes no room`() {
        assertFalse(playerBarShown(bar, onQuranTab = false))
        assertEquals(0.dp, playerBarSpace(bar, onQuranTab = false))
    }
}
