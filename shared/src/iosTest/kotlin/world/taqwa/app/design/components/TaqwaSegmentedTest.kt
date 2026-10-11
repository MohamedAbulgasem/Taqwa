package world.taqwa.app.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pill switch the Quran root, the reader's sheet and the adhkar screen share. It is 44 dp at
 * the default text size — the size every screen was drawn at — and grows only when its labels
 * need it, as at a 200 % font, where a fixed 44 dp cut them off at the bottom.
 *
 * Both scenes give it a bounded height with room to spare, as the adhkar column does: the switch
 * must take its labels' height and not the room's.
 */
@OptIn(ExperimentalTestApi::class)
class TaqwaSegmentedTest {

    @Composable
    private fun Scene(fontScale: Float) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            // Material's own typography, as the app's theme hands it down: its bodyLarge line height
            // (24 sp) is what each label inherits under its 12 sp size.
            MaterialTheme {
                Box(Modifier.height(300.dp)) {
                    Box(Modifier.testTag(SWITCH)) {
                        TaqwaSegmented(listOf("Morning", "Evening"), selectedIndex = 0, onSelect = {})
                    }
                }
            }
        }
    }

    @Test
    fun theSwitchIsFortyFourDpAtTheDefaultTextSizeWhateverRoomItIsGiven() = runComposeUiTest {
        setContent { Scene(fontScale = 1f) }
        onNodeWithTag(SWITCH).assertHeightIsEqualTo(44.dp)
        assertFalse(layoutOf("Morning").didOverflowHeight)
    }

    @Test
    fun theSwitchGrowsToItsLabelsAtTwiceTheTextSize() = runComposeUiTest {
        setContent { Scene(fontScale = 2f) }
        val height = onNodeWithTag(SWITCH).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue(height > 44.dp, "the switch is $height tall")
        assertTrue(height < 300.dp, "the switch filled the room it was given")
        assertFalse(layoutOf("Morning").didOverflowHeight)
        assertFalse(layoutOf("Evening").didOverflowHeight)
    }

    // The checks read the height overflow, not `hasVisualOverflow`: a centred label's paragraph is
    // laid out across the whole width it may take, so it always "overflows" sideways.
    private fun ComposeUiTest.layoutOf(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}

private const val SWITCH = "switch"
