package world.taqwa.app.feature.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_a11y_ayah_count
import world.taqwa.app.resources.quran_a11y_page_n
import world.taqwa.app.resources.quran_a11y_read_aloud
import world.taqwa.app.resources.quran_a11y_selected
import world.taqwa.app.resources.quran_ayah_n
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_play
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A printed Mushaf page to a screen reader (design §5): one invisible stop per ayah, over the
 * first run of its words on the page, named "Ayah N" and never by its text. A double-tap selects
 * the ayah as a tap does, and the stop's one action plays the reciter from there. The stop says in
 * its own words when its ayah is selected; the ayah being recited is told by its action, Pause, and
 * never by a state. No node is given the page's words or the basmala. The stops leave touch alone: a finger still lands on the drawn words beneath them.
 * Only the merged tree, the one screen readers walk, is asserted.
 *
 * Page 3 of the fixtures: ayah 7 starts on the second line and runs on to the third, where ayah 8
 * starts; the ninth line opens with ayah 12 and starts ayah 13 further left.
 */
@OptIn(ExperimentalTestApi::class)
class MushafPageSemanticsTest {

    private val taps = mutableListOf<Pair<Int, Int>>()
    private val plays = mutableListOf<Pair<Int, Int>>()

    /** Al-Faatiha's first ayah without its number is the basmala: taken from the fixtures, not retyped. */
    private val basmala = MUSHAF_PAGE_1.lines[1].text!!.let { it.substring(0, digitsStart(it)).trimEnd() }

    /**
     * An upright phone's page inside the 1024 x 768 test window. Not TaqwaTheme: its system-bar
     * and keep-screen-on effects crash the simulator test process.
     */
    @Composable
    private fun Page(
        page: MushafPage = MUSHAF_PAGE_3,
        highlighted: Pair<Int, Int>? = null,
        playing: Pair<Int, Int>? = null,
        live: Boolean = false,
        surahOf: (Int) -> Surah? = { null },
        onTapAyah: (Int, Int) -> Unit = { surah, ayah -> taps += surah to ayah },
    ) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
        ) {
            Box(Modifier.size(411.dp, 760.dp)) {
                MushafPageView(
                    page = page,
                    surahOf = surahOf,
                    basmala = basmala,
                    highlighted = highlighted,
                    bookmarked = false,
                    onTapAyah = onTapAyah,
                    onClearHighlight = {},
                    onBookmark = {},
                    onCopy = {},
                    onShare = {},
                    playing = playing,
                    playingLive = live,
                    onPlayAyah = { surah, ayah -> plays += surah to ayah },
                )
            }
        }
    }

    private suspend fun label(ayah: Int) = getString(Res.string.quran_ayah_n, ayah.toString())

    private suspend fun ComposeUiTest.stop(ayah: Int): SemanticsNodeInteraction = onNodeWithContentDescription(label(ayah))

    private fun SemanticsNodeInteraction.actionLabels(): List<String> =
        fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun SemanticsNodeInteraction.state(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    /** Every text, description and state in the merged tree: all a screen reader could say on the page. */
    private fun ComposeUiTest.everythingSaid(): List<String> =
        onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.StateDescription))
        }

    private suspend fun ComposeUiTest.saysNoWordOfTheQuran(page: MushafPage) {
        setContent { Page(page) }
        val said = everythingSaid()
        // Not vacuous: the page's stops are in the tree.
        assertTrue(label(page.firstAyah) in said, "said: $said")
        for (word in page.lines.flatMap { it.words }.map { it.text } + basmala) {
            assertTrue(said.none { word in it }, "a node says \"$word\"")
        }
    }

    @Test
    fun everyAyahOnThePageIsOneStopNamedByItsNumber() = runComposeUiTest {
        setContent { Page() }
        val ayahs = MUSHAF_PAGE_3.lines.flatMap { it.words }.map { it.ayah }.distinct()
        assertEquals((6..16).toList(), ayahs)
        for (ayah in ayahs) {
            onAllNodesWithContentDescription(label(ayah)).assertCountEquals(1)
            stop(ayah)
                .assert(hasClickAction())
                // Never the semantic selected: on Android that key alone says "Not selected" and
                // makes the stop a checkable toggle.
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
                // Nothing selected and nothing recited: no state to speak.
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }

    @Test
    fun anAyahThatRunsOnToTheNextLineHasItsStopOnTheLineItStartsOn() = runComposeUiTest {
        setContent { Page() }
        // Ayah 8 starts on the line ayah 7 runs on to, so ayah 7's one stop is on the line above.
        val seven = stop(7).fetchSemanticsNode().boundsInRoot
        val eight = stop(8).fetchSemanticsNode().boundsInRoot
        assertTrue(seven.bottom <= eight.top, "7 at $seven, 8 at $eight")
    }

    @Test
    fun theWordsOfThePageAreGivenToNoNode() = runComposeUiTest {
        saysNoWordOfTheQuran(MUSHAF_PAGE_3)
    }

    @Test
    fun theBasmalaAndTheWordsOfAPageThatOpensASurahAreGivenToNoNode() = runComposeUiTest {
        saysNoWordOfTheQuran(MUSHAF_PAGE_2)
    }

    @Test
    fun aDoubleTapSelectsTheAyahAsATapDoes() = runComposeUiTest {
        setContent { Page() }
        stop(7).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(2 to 7), taps)
        assertEquals(emptyList(), plays)
    }

    @Test
    fun aFingerStillLandsOnTheDrawnWordsUnderAStop() = runComposeUiTest {
        setContent { Page() }
        // A touch, not the semantics action: the stop has no pointer input of its own, so the
        // tap reaches the line's word-by-word hit test beneath it.
        stop(13).performClick()
        assertEquals(listOf(2 to 13), taps)
    }

    @Test
    fun playStartsTheReciterFromThatAyah() = runComposeUiTest {
        setContent { Page() }
        val stop = stop(8)
        assertEquals(listOf(getString(Res.string.recitation_play)), stop.actionLabels())
        val play = stop.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        runOnIdle { play.action() }
        assertEquals(listOf(2 to 8), plays)
        assertEquals(emptyList(), taps)
    }

    @Test
    fun playSaysPauseOnlyWhileTheVoiceIsGoingOnThatAyah() = runComposeUiTest {
        var live by mutableStateOf(true)
        setContent { Page(playing = 2 to 7, live = live) }
        val play = getString(Res.string.recitation_play)
        val pause = getString(Res.string.recitation_a11y_pause)
        assertEquals(listOf(pause), stop(7).actionLabels())
        assertEquals(listOf(play), stop(8).actionLabels())
        // Paused on this ayah: the same action resumes it, so it says Play.
        live = false
        waitForIdle()
        assertEquals(listOf(play), stop(7).actionLabels())
    }

    @Test
    fun aSelectedAyahsStopSaysSoWithoutBecomingAToggle() = runComposeUiTest {
        setContent { Page(highlighted = 2 to 7) }
        stop(7)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, getString(Res.string.quran_a11y_selected)))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        stop(8).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun aSecondDoubleTapDeselectsAndTheStopSaysSo() = runComposeUiTest {
        // The screen's own toggle (spec §2.4): the same ayah tapped again clears the selection.
        var highlighted by mutableStateOf<Pair<Int, Int>?>(null)
        setContent {
            Page(highlighted = highlighted, onTapAyah = { surah, ayah ->
                highlighted = if (highlighted == surah to ayah) null else surah to ayah
            })
        }
        val selected = getString(Res.string.quran_a11y_selected)
        stop(7).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(selected, stop(7).state())
        stop(7).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(null, stop(7).state())
    }

    @Test
    fun theAyahTheReciterIsOnIsToldByItsPauseAndNeverByAState() = runComposeUiTest {
        var live by mutableStateOf(true)
        setContent { Page(playing = 2 to 7, live = live) }
        // Not "Being read aloud": on the ayah cards that is the phone's voice reading the
        // translation, and no phone voice reads the Quran. A state that moved with the reciter
        // could also be announced over the recitation.
        val readAloud = getString(Res.string.quran_a11y_read_aloud)
        assertTrue(everythingSaid().none { readAloud in it }, "said: ${everythingSaid()}")
        assertEquals(null, stop(7).state())
        assertEquals(listOf(getString(Res.string.recitation_a11y_pause)), stop(7).actionLabels())
        // Paused there: still no state, and the action is Play again.
        live = false
        waitForIdle()
        assertEquals(null, stop(7).state())
        assertEquals(listOf(getString(Res.string.recitation_play)), stop(7).actionLabels())
    }

    @Test
    fun aSelectedAyahTheReciterIsOnSaysSelectedAndNothingMore() = runComposeUiTest {
        setContent { Page(highlighted = 2 to 7, playing = 2 to 7, live = true) }
        assertEquals(getString(Res.string.quran_a11y_selected), stop(7).state())
        assertEquals(listOf(getString(Res.string.recitation_a11y_pause)), stop(7).actionLabels())
    }

    @Test
    fun thePageNumberSaysPageInTheInterfacesDigits() = runComposeUiTest {
        setContent { Page() }
        onAllNodesWithContentDescription(getString(Res.string.quran_a11y_page_n, "3")).assertCountEquals(1)
        // Not the bare numeral printed under the frame, which TalkBack used to say on its own.
        val said = everythingSaid()
        assertTrue(said.none { QuranText.arabicIndic(3) in it }, "said: $said")
    }

    @Test
    fun aSurahBandSaysHowManyAyahsTheSurahHasAndKeepsItsName() = runComposeUiTest {
        // Not the real names: only the count is under test, and the band draws what it is given.
        val surah = Surah(2, "arabic-name", "Al-Baqarah", "The Cow", Revelation.MADANI, 286, 2, 1)
        setContent { Page(page = MUSHAF_PAGE_2, surahOf = { surah }) }
        onAllNodesWithContentDescription(getPluralString(Res.plurals.quran_a11y_ayah_count, 286, "286")).assertCountEquals(1)
        val said = everythingSaid()
        assertTrue(said.none { QuranText.arabicIndic(286) in it }, "said: $said")
        // The surah's name stays readable: names are not recitation.
        assertTrue(surah.nameArabic in said, "said: $said")
    }

    @Test
    fun theStopsOfOneLineReadRightToLeftWhateverTheInterfaceDirection() = runComposeUiTest {
        // The test window is left to right, as an English interface is.
        setContent { Page() }
        val twelve = stop(12).fetchSemanticsNode()
        val thirteen = stop(13).fetchSemanticsNode()
        assertEquals(twelve.boundsInRoot.top, thirteen.boundsInRoot.top)
        // Ayah 12 opens the line at its right edge and ayah 13 starts to its left: the stops sit
        // over their own words, not mirrored.
        assertTrue(
            twelve.boundsInRoot.left > thirteen.boundsInRoot.right,
            "12 at ${twelve.boundsInRoot}, 13 at ${thirteen.boundsInRoot}",
        )
        assertEquals(0f, twelve.config[SemanticsProperties.TraversalIndex])
        assertEquals(1f, thirteen.config[SemanticsProperties.TraversalIndex])
        // Ordered inside their own line's group.
        val group = twelve.parent
        assertEquals(group?.id, thirteen.parent?.id)
        assertEquals(true, group?.config?.getOrNull(SemanticsProperties.IsTraversalGroup))
    }
}
