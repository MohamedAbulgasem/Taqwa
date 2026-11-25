@file:OptIn(ExperimentalTestApi::class)

package world.taqwa.app.feature.prayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.LocalScreenReaderOverride
import world.taqwa.app.design.LocalTaqwaDark
import world.taqwa.app.design.TaqwaColors
import world.taqwa.app.watchfeed.WatchTarget
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

// What the watch card's UI suites share (WatchCardTest, WatchCardOnHomeTest): a watch, the card's
// slot as the tests compose it, the taps they count, and how they read a line. Home itself is in
// HomeFixtures.kt.

/** The owner's kind of watch, with a name that fits a 412 dp phone's line. */
internal val galaxy = WatchTarget(nodeId = "4a1f", name = "Galaxy Watch7")

/** An offer as the holder publishes it. */
internal fun cardOf(
    step: WatchCardStep = WatchCardStep.INSTALL,
    name: String = galaxy.name,
    answer: WatchCardAnswer? = null,
    tapped: Boolean = false,
) = WatchCardUi.Offer(step, name, answer, tapped)

/** The app in front or not, as the test moves it (WatchSectionTest's own). */
internal class Front : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)
    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
    fun leave() {
        registry.currentState = Lifecycle.State.CREATED
    }
    fun come() {
        registry.currentState = Lifecycle.State.RESUMED
    }
}

/** What the card and Home were asked to do. */
internal class CardTaps {
    var open = 0
    var dismiss = 0
    var undo = 0
    var touched = 0

    /** How long the x asked the undo line to stay, the last time. */
    var window: Duration? = null

    /** Each press on Home (true) and each lift of the last finger (false), in order. */
    val pressed = mutableListOf<Boolean>()
}

/** Android's "Time to take action" as Compose hands it on: [millis] for anything, as a reader set it. */
internal class TimeToAct(private val millis: Long) : AccessibilityManager {
    override fun calculateRecommendedTimeoutMillis(
        originalTimeoutMillis: Long,
        containsIcons: Boolean,
        containsText: Boolean,
        containsControls: Boolean,
    ): Long = maxOf(originalTimeoutMillis, millis)
}

/**
 * The card's slot on a phone [width] wide, in its 24 dp gutter, at [fontScale] times the text, in
 * [direction], in [colors], with a screen reader running or not ([screenReader]) and the platform's
 * [accessibility] (the simulator's own unless a test gives one): still (Reduce Motion), so each
 * frame is the settled one. Not TaqwaTheme: its system-bar effects crash the simulator test process.
 */
@Composable
internal fun Slot(
    card: WatchCardUi,
    width: Dp = 360.dp,
    fontScale: Float = 1f,
    direction: LayoutDirection = LayoutDirection.Ltr,
    colors: TaqwaColors = LightColors,
    taps: CardTaps = CardTaps(),
    screenReader: Boolean = false,
    accessibility: AccessibilityManager? = LocalAccessibilityManager.current,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalTaqwaColors provides colors,
        LocalTaqwaDark provides (colors == DarkColors),
        LocalLayoutDirection provides direction,
        LocalDensity provides Density(density.density, fontScale),
        LocalScreenReaderOverride provides screenReader,
        LocalAccessibilityManager provides accessibility,
    ) {
        Box(Modifier.width(width)) {
            WatchCardSlot(
                card = card,
                extraTop = 0.dp,
                horizontalPadding = 24.dp,
                onOpen = { taps.open++ },
                onDismiss = {
                    taps.dismiss++
                    taps.window = it
                },
                onUndo = { taps.undo++ },
                reducedMotion = true,
            )
        }
    }
}

/** The layout of the one drawn text that holds [text] (the unmerged tree: the card's own label clears it from the merged one). */
internal fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, substring = true, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.single()
}

/**
 * Every line of [text] drawn to its end: none cut with an ellipsis, the text not stopped by the
 * room it had, and none cut at the side. Not `didOverflowHeight` or `didOverflowWidth`: a plain
 * String Text answers GetTextLayoutResult with its drawn size but a paragraph laid out again at the
 * column's width, whose height is not the drawn one (on the simulator 43 against 38 for one Urdu
 * line, 170 against 152 for two Arabic ones) and whose width is the column's, so they read a cut
 * where there is none. A text is cut at the bottom only where it ran out of room: drawn exactly as
 * tall as it was allowed. It is cut at the side where it needs more width on one line than it is
 * drawn with and is still drawn on one line (`softWrap = false`, or one line at most): measured
 * afresh in the drawn text's own words, style, font, density and inline glyphs.
 */
internal fun ComposeUiTest.assertWhole(text: String) {
    val layout = layoutOf(text)
    assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "\"$text\" ends in an ellipsis")
    val room = layout.layoutInput.constraints.maxHeight
    assertTrue(layout.size.height < room, "\"$text\" is cut at the bottom: ${layout.size.height} px of the $room it had")
    val input = layout.layoutInput
    val oneLine = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
        .measure(input.text, input.style, softWrap = false, maxLines = 1, placeholders = input.placeholders)
    if (oneLine.size.width > layout.size.width + 1) {
        assertTrue(
            layout.size.height * 2 >= oneLine.size.height * 3,
            "\"$text\" is cut at the side: it needs ${oneLine.size.width} px on one line, and is drawn " +
                "${layout.size.width} px wide and ${layout.size.height} px tall, one line of ${oneLine.size.height}",
        )
    }
}

/** A node's bounds in the root, in dp. */
internal fun ComposeUiTest.dpRect(r: Rect): DpRect =
    with(density) { DpRect(r.left.toDp(), r.top.toDp(), r.right.toDp(), r.bottom.toDp()) }

/** Within half a dp of [expected]. */
internal fun near(expected: Dp, actual: Dp, what: String) =
    assertTrue(abs((expected - actual).value) <= 0.5f, "$what: expected $expected, was $actual")
