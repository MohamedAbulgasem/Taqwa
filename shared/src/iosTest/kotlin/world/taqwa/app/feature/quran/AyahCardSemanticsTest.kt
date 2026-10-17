package world.taqwa.app.feature.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_a11y_read_aloud
import world.taqwa.app.resources.quran_a11y_selected
import world.taqwa.app.resources.quran_action_bookmark
import world.taqwa.app.resources.quran_action_bookmarked
import world.taqwa.app.resources.quran_action_copy
import world.taqwa.app.resources.quran_action_share
import world.taqwa.app.resources.quran_ayah_n
import world.taqwa.app.resources.quran_remove_bookmark
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_play
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An ayah card to a screen reader (design §5): one node that names the ayah and reads its
 * translation, never the Arabic or the transliteration, with its states restated and the action
 * row's four actions on the card itself. The selected card's visible row stays four buttons of its
 * own, for Voice Access and Voice Control. Labels come from the resources; the digits from the
 * English format the scene is given. Only the tree a screen reader walks (the merged one) is
 * asserted: the unmerged tree keeps cleared nodes on purpose, for tests.
 *
 * Selection is spoken as a state in the card's own words and the semantic `selected` is never set:
 * on Android that key alone makes every unselected card say "Not selected" and turns each card
 * into a checkable toggle. Only iOS runs these tests, so they pin the key's absence, which is what
 * Android's delegate reads.
 */
@OptIn(ExperimentalTestApi::class)
class AyahCardSemanticsTest {

    private val arabic = "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ"
    private val transliteration = "Allahu la ilaha illa huwa"
    private val translation = "Allah - there is no deity except Him."

    private var clicks = 0
    private val calls = mutableListOf<String>()

    /** Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test process. */
    @Composable
    private fun Card(
        selected: Boolean = false,
        playing: Boolean = false,
        live: Boolean = false,
        speaking: Boolean = false,
        bookmarked: Boolean = false,
        withRow: Boolean = false,
    ) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
        ) {
            AyahCard(
                text = arabic,
                ayahNumber = 255,
                transliteration = transliteration,
                translation = translation,
                translationLanguage = "en",
                sizeSp = 28,
                selected = selected,
                playing = playing,
                live = live,
                speaking = speaking,
                bookmarked = bookmarked,
                // The reader's own row, wired to its own record so a tap on it is told apart from
                // the card's screen-reader actions.
                actions = if (!withRow) null else {
                    {
                        AyahActions(
                            bookmarked = bookmarked,
                            onBookmark = { calls += "row bookmark" },
                            onCopy = { calls += "row copy"; true },
                            onShare = { calls += "row share" },
                            onPlay = { calls += "row play" },
                        )
                    }
                },
                onClick = { clicks++ },
                onPlay = { calls += "play" },
                onBookmark = { calls += "bookmark" },
                onCopy = { calls += "copy" },
                onShare = { calls += "share" },
            )
        }
    }

    private suspend fun spokenText() = "${getString(Res.string.quran_ayah_n, "255")}. $translation"

    private suspend fun ComposeUiTest.card(): SemanticsNodeInteraction = onNodeWithText(spokenText())

    private fun SemanticsNodeInteraction.actionLabels(): List<String> =
        fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    @Test
    fun theCardNamesTheAyahAndReadsItsTranslationButNeverTheArabic() = runComposeUiTest {
        setContent { Card() }
        card()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        onAllNodesWithText(arabic, substring = true).assertCountEquals(0)
        onAllNodesWithText(transliteration, substring = true).assertCountEquals(0)
        // The translation is spoken once, as part of the card, not again as a node of its own.
        onAllNodesWithText(translation, substring = true).assertCountEquals(1)
    }

    @Test
    fun aTapOnTheCardStillSelectsIt() = runComposeUiTest {
        setContent { Card() }
        card().assert(hasClickAction()).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun aSelectedCardSaysSoWithoutBecomingAToggle() = runComposeUiTest {
        setContent { Card(selected = true) }
        card()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, getString(Res.string.quran_a11y_selected)))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun theBadgeTheReadAloudMarkAndTheSelectionAreRestatedOnTheCard() = runComposeUiTest {
        setContent { Card(selected = true, speaking = true, bookmarked = true) }
        val states = listOf(
            getString(Res.string.quran_a11y_selected),
            getString(Res.string.quran_action_bookmarked),
            getString(Res.string.quran_a11y_read_aloud),
        ).joinToString(", ")
        card()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, states))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            // The badge and the read-aloud mark say nothing of their own: a description merged
            // into the card is what TalkBack would read in place of its text.
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun theSelectedCardsRowKeepsItsOwnFourButtons() = runComposeUiTest {
        setContent { Card(selected = true, withRow = true) }
        // The card is still only its number and translation: no label of the row merges into it.
        assertEquals(listOf(spokenText()), card().fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
        onAllNodesWithText(arabic, substring = true).assertCountEquals(0)
        val labels = listOf(
            getString(Res.string.recitation_play),
            getString(Res.string.quran_action_bookmark),
            getString(Res.string.quran_action_copy),
            getString(Res.string.quran_action_share),
        )
        labels.forEach { onNodeWithText(it).assert(hasClickAction()) }
        onNodeWithText(getString(Res.string.quran_action_copy)).performClick()
        assertEquals(listOf("row copy"), calls)
        assertEquals(0, clicks)
    }

    @Test
    fun theFourActionsOfTheRowAreOnEveryCard() = runComposeUiTest {
        setContent { Card() }
        val card = card()
        val expected = listOf(
            getString(Res.string.recitation_play),
            getString(Res.string.quran_action_bookmark),
            getString(Res.string.quran_action_copy),
            getString(Res.string.quran_action_share),
        )
        assertEquals(expected, card.actionLabels())
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        runOnIdle { actions.forEach { it.action() } }
        assertEquals(listOf("play", "bookmark", "copy", "share"), calls)
        assertEquals(0, clicks)
    }

    @Test
    fun aKeptAyahOffersToRemoveItsBookmark() = runComposeUiTest {
        setContent { Card(bookmarked = true) }
        assertEquals(getString(Res.string.quran_remove_bookmark), card().actionLabels()[1])
    }

    @Test
    fun playSaysPauseOnlyWhileTheVoiceIsGoingOnThisAyah() = runComposeUiTest {
        var live by mutableStateOf(true)
        var playing by mutableStateOf(true)
        setContent { Card(playing = playing, live = live) }
        val play = getString(Res.string.recitation_play)
        val pause = getString(Res.string.recitation_a11y_pause)
        assertEquals(pause, card().actionLabels().first())
        // Paused on this ayah: the action resumes it, so it says Play.
        live = false
        waitForIdle()
        assertEquals(play, card().actionLabels().first())
        // The voice is going, but on another ayah.
        live = true
        playing = false
        waitForIdle()
        assertEquals(play, card().actionLabels().first())
    }
}
