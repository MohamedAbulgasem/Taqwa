package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The branching the Appearance screen does beneath each widget preview, tested as the pure
 * function it is — no composable, no platform.
 *
 * The case that matters most is the first one: [WidgetPlacement.Unknown] is what every platform
 * reports when it cannot answer, and it must silence both offers. A regression there does not
 * crash or fail anywhere else; it just invites a user to add a widget already sitting on their
 * home screen.
 */
class WidgetPlacementTest {

    @Test
    fun `Unknown reports both widgets placed, so neither is offered`() {
        val unknown = WidgetPlacement.Unknown
        assertTrue(unknown.prayer)
        assertTrue(unknown.ayah)
        assertEquals(
            WidgetOffer.None,
            widgetOffer(PinnableWidget.PRAYER, unknown.prayer, pinnable = true, addPath = WidgetAddPath.ANDROID_PIN),
        )
        assertEquals(
            WidgetOffer.None,
            widgetOffer(PinnableWidget.AYAH, unknown.ayah, pinnable = true, addPath = WidgetAddPath.ANDROID_PIN),
        )
    }

    @Test
    fun `a placed widget is offered nothing, whatever the platform could do`() {
        for (widget in PinnableWidget.entries) {
            for (pinnable in listOf(true, false)) {
                for (path in WidgetAddPath.entries) {
                    assertEquals(
                        WidgetOffer.None,
                        widgetOffer(widget, placed = true, pinnable = pinnable, addPath = path),
                        "$widget placed, pinnable=$pinnable, path=$path",
                    )
                }
            }
        }
    }

    @Test
    fun `a missing widget the launcher can be asked for gets the row, naming that widget`() {
        for (widget in PinnableWidget.entries) {
            assertEquals(
                WidgetOffer.Pin(widget),
                widgetOffer(widget, placed = false, pinnable = true, addPath = WidgetAddPath.ANDROID_PIN),
            )
        }
    }

    @Test
    fun `a missing widget that cannot be asked for gets this platform's own steps`() {
        for (widget in PinnableWidget.entries) {
            for (path in WidgetAddPath.entries) {
                assertEquals(
                    WidgetOffer.Instructions(path),
                    widgetOffer(widget, placed = false, pinnable = false, addPath = path),
                    "$widget not placed, not pinnable, path=$path",
                )
            }
        }
    }

    @Test
    fun `all three add paths are covered, so a new one cannot slip through untested`() {
        assertEquals(
            listOf(WidgetAddPath.ANDROID_PIN, WidgetAddPath.IOS_HOLD_ICON, WidgetAddPath.IOS_PLUS_BUTTON),
            WidgetAddPath.entries.toList(),
        )
    }
}
