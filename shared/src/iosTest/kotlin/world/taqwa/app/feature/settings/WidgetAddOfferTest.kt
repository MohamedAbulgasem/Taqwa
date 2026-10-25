package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.WidgetPinRequester
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Appearance's "Add widget" rows, with a launcher that answers as told: only a refusal reaches the
 * screen, which then turns that row into the steps ([world.taqwa.app.widget.widgetOffer]).
 */
@OptIn(ExperimentalTestApi::class)
class WidgetAddOfferTest {

    private class Launcher(private val takes: Boolean) : WidgetPinRequester {
        val asked = mutableListOf<PinnableWidget>()
        override fun isSupported(widget: PinnableWidget) = true
        override fun requestPin(widget: PinnableWidget): Boolean {
            asked += widget
            return takes
        }
    }

    /** Both rows, prayer first, as the screen has them. */
    @Composable
    private fun BothRows(launcher: Launcher, refused: MutableList<PinnableWidget>) {
        Column {
            for (widget in PinnableWidget.entries) {
                WidgetAddOffer(
                    WidgetOffer.Pin(widget), launcher, onRefused = { refused += it },
                    showInstructions = true, announce = false,
                )
            }
        }
    }

    @Test
    fun aRowTheLauncherTookIsNotReportedAsRefused() = runComposeUiTest {
        val launcher = Launcher(takes = true)
        val refused = mutableListOf<PinnableWidget>()
        setContent { BothRows(launcher, refused) }
        val rows = onAllNodesWithText(getString(Res.string.appearance_widget_add))
        PinnableWidget.entries.indices.forEach { rows[it].performClick() }
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        assertEquals(emptyList<PinnableWidget>(), refused)
    }

    @Test
    fun aRowTheLauncherRefusedIsReportedForThatWidget() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        val refused = mutableListOf<PinnableWidget>()
        setContent { BothRows(launcher, refused) }
        val rows = onAllNodesWithText(getString(Res.string.appearance_widget_add))
        PinnableWidget.entries.indices.forEach { rows[it].performClick() }
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        assertEquals(PinnableWidget.entries.toList(), refused)
    }
}
