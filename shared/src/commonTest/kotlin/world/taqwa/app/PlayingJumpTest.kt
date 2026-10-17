package world.taqwa.app

import kotlinx.coroutines.test.runTest
import world.taqwa.app.nav.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [jumpToPlaying], "show me the ayah being recited" (spec §15.5), with the navigator and the
 * reading settings faked. Al-Kahf 18:50 is being recited throughout; a return to the front passes
 * the request count from when the app left, 5 here unless a test says otherwise.
 */
class PlayingJumpTest {

    private var requests = 5L
    private val opened = mutableListOf<Screen>()
    private var scrolls = 0

    private suspend fun jump(
        playing: Pair<Int, Int>? = 18 to 50,
        since: Long? = 5L,
        showsSurah: (Int) -> Boolean = { false },
        target: suspend (Int, Int) -> Screen = { surah, ayah -> Screen.Reader(surah, ayah) },
    ) = jumpToPlaying(
        playing = playing,
        yieldToRequestsSince = since,
        requestsMade = { requests },
        showsSurah = showsSurah,
        scroll = { scrolls++ },
        target = target,
        open = { opened += it },
    )

    @Test
    fun aReturnWithAVoicePlayingOpensTheRecitedAyah() = runTest {
        jump()
        assertEquals(listOf<Screen>(Screen.Reader(18, 50)), opened)
    }

    /** A widget tap or an adhkar reminder brought the app back: that wins, and nothing moves. */
    @Test
    fun aRequestBetweenTheLeaveAndTheReturnOpensNothing() = runTest {
        requests = 6L
        jump()
        assertEquals(emptyList(), opened)
        assertEquals(0, scrolls)
    }

    /** The request lands while the reading settings load, after the first look: it still wins. */
    @Test
    fun aRequestWhileTheReadingSettingsLoadOpensNothing() = runTest {
        jump(target = { surah, ayah -> requests++; Screen.Reader(surah, ayah) })
        assertEquals(emptyList(), opened)
    }

    @Test
    fun aScreenAlreadyShowingTheRecitationIsAskedToScroll() = runTest {
        jump(showsSurah = { it == 18 })
        assertEquals(1, scrolls)
        assertEquals(emptyList(), opened)
    }

    /** The scroll yields too: a token bumped for a screen about to be replaced is left behind. */
    @Test
    fun aScreenAlreadyShowingTheRecitationYieldsToARequestToo() = runTest {
        requests = 6L
        jump(showsSurah = { true })
        assertEquals(0, scrolls)
    }

    /** The bar's own words and the media notification's tap are the request, and never yield. */
    @Test
    fun theBarAndTheMediaTapNeverYield() = runTest {
        requests = 9L
        jump(since = null)
        assertEquals(listOf<Screen>(Screen.Reader(18, 50)), opened)
    }

    @Test
    fun nothingPlayingOpensNothing() = runTest {
        jump(playing = null)
        assertEquals(emptyList(), opened)
        assertEquals(0, scrolls)
    }

    /** A page that cannot be looked up leaves the screen as it was. */
    @Test
    fun aTargetThatCannotBeResolvedOpensNothing() = runTest {
        jump(target = { _, _ -> error("no page for that ayah") })
        assertEquals(emptyList(), opened)
    }
}
