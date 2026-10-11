package world.taqwa.app.feature.adhkar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.adhkar.AdhkarItem
import world.taqwa.app.adhkar.AdhkarKind
import world.taqwa.app.adhkar.AdhkarPhase
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.adhkar.Source
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.uppercaseIn
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_a11y_count
import world.taqwa.app.resources.adhkar_a11y_next
import world.taqwa.app.resources.adhkar_a11y_previous
import world.taqwa.app.resources.adhkar_progress
import world.taqwa.app.resources.adhkar_times
import world.taqwa.app.resources.adhkar_until_sunrise
import world.taqwa.app.resources.tasbeeh_hint
import world.taqwa.app.resources.ui_language
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The session screen on a two-page fixture: page one said three times and counted once, page two
 * said once. Expected words come from the resources, never from literals — the simulator's
 * language decides them — and digits from the English format the scene is given, so "1" is "1".
 */
@OptIn(ExperimentalTestApi::class)
class AdhkarScreenTest {

    private class Calls {
        var taps = 0
        val goTo = mutableListOf<Int>()
    }

    private fun item(id: String, count: Int) = AdhkarItem(
        id = id,
        hisn = 86,
        kind = AdhkarKind.DUA,
        sets = setOf(AdhkarSet.MORNING, AdhkarSet.EVENING),
        count = count,
        countDaily = false,
        quran = null,
        arabic = mapOf("both" to "بِسْمِ اللَّهِ"),
        transliteration = mapOf("both" to "Bismillah"),
        sources = listOf(Source("abu_dawud", "5088")),
    )

    private val state = AdhkarUiState(
        set = AdhkarSet.MORNING,
        phase = AdhkarPhase.BEST,
        until = "6:12",
        pages = listOf(
            AdhkarPageUi(item("hisn-086", 3), "بِسْمِ اللَّهِ", "Bismillah", "In the name of Allah", null, count = 1, target = 3, done = false),
            AdhkarPageUi(item("hisn-088", 1), "يَا حَيُّ يَا قَيُّومُ", "Ya Hayyu ya Qayyum", "O Ever-Living", null, count = 0, target = 1, done = false),
        ),
        currentPage = 0,
        completed = 0,
        showTranslation = true,
        arabicSizeSp = 28,
        transliteration = true,
        loaded = true,
    )

    @Composable
    private fun Screen(calls: Calls) {
        // Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash
        // the simulator test process. The colours are all the screen reads from it.
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
        ) {
            AdhkarScreen(
                state = state,
                onBack = {},
                onSelectSet = {},
                onGoTo = { calls.goTo += it },
                onTap = { calls.taps++ },
                onPlay = null,
                playing = false,
                onOpenReading = {},
            )
        }
    }

    private suspend fun label(count: Int): String =
        getPluralString(Res.plurals.adhkar_times, count, count.toString())
            .uppercaseIn(UiLanguage.of(getString(Res.string.ui_language)))

    @Test
    fun thePageShowsItsLabelItsCountAndTheSetsProgress() = runComposeUiTest {
        setContent { Screen(Calls()) }
        onNodeWithText(label(3), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("1").assertIsDisplayed()
        onNodeWithText(getString(Res.string.adhkar_progress, "0", "2")).assertIsDisplayed()
        onNodeWithText(getString(Res.string.adhkar_until_sunrise, "6:12")).assertIsDisplayed()
        // The count is said as "1 of 3".
        onNodeWithContentDescription(getString(Res.string.adhkar_progress, "1", "3")).assertIsDisplayed()
    }

    @Test
    fun aTapOnThePageCounts() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls) }
        onNode(hasText(label(3)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, calls.taps)
    }

    @Test
    fun thePageOffersCountAsACustomAction() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls) }
        val count = getString(Res.string.adhkar_a11y_count)
        val page = onNode(hasText(label(3)) and hasCustomAction(count))
        page.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == count }.action()
        assertEquals(1, calls.taps)
    }

    @Test
    fun theChevronsAreLabelledAndPreviousRestsOnTheFirstPage() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls) }
        onNodeWithContentDescription(getString(Res.string.adhkar_a11y_previous)).assertIsNotEnabled()
        onNodeWithContentDescription(getString(Res.string.adhkar_a11y_next)).assertIsEnabled().performClick()
        // The pager reports the page it settled on (0) when it appears; the chevron asks for 1.
        assertEquals(listOf(1), calls.goTo.filter { it != 0 })
    }

    @Test
    fun theHintIsGoneOnceTheSessionHasACount() = runComposeUiTest {
        setContent { Screen(Calls()) }
        // Page one is already at 1, so "Tap anywhere to count" has nothing left to teach.
        onAllNodesWithText(getString(Res.string.tasbeeh_hint)).assertCountEquals(0)
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }
}
