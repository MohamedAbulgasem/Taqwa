package world.taqwa.app.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The primary pill's label, which onboarding, the Widgets cards and the sheets share. Inside a
 * Widgets card at 360 dp and a larger font, Indonesian's "Tambahkan ke layar utama" ran into the
 * pill's rounded ends, because the label had the whole width to itself. With larger text it now
 * keeps clear of both ends, and a label too long for one line wraps centred under itself rather
 * than ragged. At the default text size the pill is drawn as it always was (Mohamed's rule that
 * accessibility work leaves the default look alone): an inset there wrapped "Keep" labels in the
 * timetable sheets that fit on one line before, such as French "Garder Comité d’observation du
 * croissant" at 411 dp.
 */
@OptIn(ExperimentalTestApi::class)
class PrimaryButtonLabelTest {

    /** The pill [width] wide, at the text size [fontScale] (1.3 is Android's and iOS's larger text
     * that the Widgets card was seen at). */
    @Composable
    private fun Scene(label: String, fontScale: Float = LARGER, width: Dp = 220.dp) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            Box(Modifier.width(width)) {
                TaqwaPrimaryButton(label, onClick = {}, modifier = Modifier.testTag(BUTTON))
            }
        }
    }

    @Test
    fun aLabelTooLongForTheRowKeepsClearOfThePillsEnds() = runComposeUiTest {
        setContent { Scene(LONG) }
        val pill = onNodeWithTag(BUTTON).getUnclippedBoundsInRoot()
        val label = onNodeWithText(LONG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(label.left - pill.left >= 20.dp, "the label starts ${label.left - pill.left} from the pill's start")
        assertTrue(pill.right - label.right >= 20.dp, "the label ends ${pill.right - label.right} from the pill's end")
    }

    @Test
    fun aLabelThatWrapsIsCentredLineByLine() = runComposeUiTest {
        setContent { Scene(LONG) }
        val layout = layoutOf(LONG)
        assertTrue(layout.lineCount > 1, "the label was meant to wrap at this width")
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
    }

    @Test
    fun aShortLabelStaysOnOneLineInTheMiddle() = runComposeUiTest {
        setContent { Scene(SHORT) }
        val pill = onNodeWithTag(BUTTON).getUnclippedBoundsInRoot()
        val label = onNodeWithText(SHORT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(1, layoutOf(SHORT).lineCount)
        val startGap = label.left - pill.left
        val endGap = pill.right - label.right
        assertTrue((startGap - endGap).value in -1f..1f, "the label sits $startGap from the start and $endGap from the end")
    }

    @Test
    fun atTheDefaultTextSizeALabelThatFitsThePillStaysOnOneLine() = runComposeUiTest {
        var natural = 0.dp
        setContent {
            // The label's own width at 16 sp ExtraBold, then a pill 8 dp wider: room for it on one
            // line, as a long "Keep" label has today, but not room for it and an inset each side.
            val measured = rememberTextMeasurer().measure(DEFAULT_FITS, TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp))
            natural = with(LocalDensity.current) { measured.size.width.toDp() }
            Scene(DEFAULT_FITS, fontScale = 1f, width = natural + 8.dp)
        }
        assertEquals(1, layoutOf(DEFAULT_FITS).lineCount, "a label that fit the pill at the default size wrapped")
    }

    @Test
    fun atTheDefaultTextSizeALabelTooLongForThePillWrapsAsItAlwaysDid() = runComposeUiTest {
        setContent { Scene(LONG, fontScale = 1f) }
        val layout = layoutOf(LONG)
        assertTrue(layout.lineCount > 1, "the label was meant to wrap at this width")
        assertEquals(0f, layout.getLineLeft(0), "the first line no longer starts at the label's start")
    }

    private fun ComposeUiTest.layoutOf(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}

private const val BUTTON = "button"

/** Longer than the 220 dp pill holds on one line, as Indonesian is in a card at 360 dp and 1.3. */
private const val LONG = "Tambahkan ke layar utama sekarang juga"

private const val SHORT = "Done"

/** About the length of the "Keep" labels in the timetable sheets. */
private const val DEFAULT_FITS = "Keep Islamic Foundation Bangladesh"

private const val LARGER = 1.3f
