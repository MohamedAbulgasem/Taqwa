package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.widgets_step_android_1
import world.taqwa.app.resources.widgets_step_android_2
import world.taqwa.app.resources.widgets_step_android_3
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.widgetAddPath
import world.taqwa.app.widget.widgetOffer

/**
 * The Widgets screen's refused path as Android takes it (review round 2: until now only the iOS
 * simulator tested a refusal, and there the steps are the iPhone's). Built on Android's own
 * [widgetAddPath], so a refused "Add to home screen" is followed from the launcher's answer to the
 * panel that replaces the button: the by-hand panel, open where the button was, with the launcher's
 * three steps. The panel itself is drawn in WidgetOfferAreaTest on the simulator, from this path.
 *
 * A JVM test with no Android class loaded: the screen's decisions are pure functions, and Compose
 * UI tests run only on the simulator in this project (no Robolectric).
 */
class WidgetsByHandAndroidTest {

    /** The screen's offers for both widgets on a launcher that takes requests, with [refused] the
     * ones it has turned down. */
    private fun offers(refused: Set<PinnableWidget>, pinnable: Boolean = true) =
        PinnableWidget.entries.map { widget ->
            widget to widgetOffer(widget, placed = false, pinnable = pinnable, addPath = widgetAddPath, refused = widget in refused)
        }

    @Test
    fun `Android adds a widget through its launcher's request`() {
        assertEquals(WidgetAddPath.ANDROID_PIN, widgetAddPath)
    }

    @Test
    fun `a refused prayer widget gets the by-hand panel open where its button was`() {
        val refused = setOf(PinnableWidget.PRAYER)
        val offers = offers(refused)
        assertEquals(
            listOf(
                PinnableWidget.PRAYER to WidgetOffer.Instructions(WidgetAddPath.ANDROID_PIN),
                PinnableWidget.AYAH to WidgetOffer.Pin(PinnableWidget.AYAH),
            ),
            offers,
        )
        assertEquals(setOf(PinnableWidget.PRAYER), stepsShownOpen(offers, refused))
        assertEquals(StepsPanel.BY_HAND, stepsPanel(WidgetAddPath.ANDROID_PIN))
        assertEquals(
            listOf(Res.string.widgets_step_android_1, Res.string.widgets_step_android_2, Res.string.widgets_step_android_3),
            widgetSteps(WidgetAddPath.ANDROID_PIN).steps,
        )
    }

    @Test
    fun `both widgets refused open both by-hand panels and neither folds behind Show me how`() {
        val refused = PinnableWidget.entries.toSet()
        val offers = offers(refused)
        assertEquals(PinnableWidget.entries.map { it to WidgetOffer.Instructions(WidgetAddPath.ANDROID_PIN) }, offers)
        assertEquals(refused, stepsShownOpen(offers, refused))
    }

    @Test
    fun `a launcher that never takes a request opens the first card's steps and folds the second's`() {
        val offers = offers(refused = emptySet(), pinnable = false)
        assertEquals(setOf(PinnableWidget.PRAYER), stepsShownOpen(offers, emptySet()))
    }

    @Test
    fun `only Android's path is written out in the by-hand panel`() {
        assertEquals(
            mapOf(
                WidgetAddPath.ANDROID_PIN to StepsPanel.BY_HAND,
                WidgetAddPath.IOS_HOLD_ICON to StepsPanel.HOW_TO,
                WidgetAddPath.IOS_PLUS_BUTTON to StepsPanel.HOW_TO,
            ),
            WidgetAddPath.entries.associateWith(::stepsPanel),
        )
    }
}
