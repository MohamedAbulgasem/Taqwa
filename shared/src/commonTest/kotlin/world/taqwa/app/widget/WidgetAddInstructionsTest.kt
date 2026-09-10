package world.taqwa.app.widget

import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add_android
import world.taqwa.app.resources.appearance_widget_add_ios_hold
import world.taqwa.app.resources.appearance_widget_add_ios_plus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Appearance screen shows [WidgetOffer.Instructions] as one caption, and which caption it is
 * decides whether the user is told to hold an app icon, tap a plus, or open the launcher's widget
 * drawer — three different phones' steps that must never be swapped. [widgetAddInstructionsKey] is
 * a pure mapping precisely so that pairing is checked here rather than by eye on a device.
 */
class WidgetAddInstructionsTest {

    @Test
    fun `an android launcher without pin support gets the widget drawer steps`() {
        assertEquals(
            Res.string.appearance_widget_add_android,
            widgetAddInstructionsKey(WidgetAddPath.ANDROID_PIN),
        )
    }

    @Test
    fun `modern iOS is told to hold the app icon`() {
        assertEquals(
            Res.string.appearance_widget_add_ios_hold,
            widgetAddInstructionsKey(WidgetAddPath.IOS_HOLD_ICON),
        )
    }

    @Test
    fun `older iOS is told to use the plus button`() {
        assertEquals(
            Res.string.appearance_widget_add_ios_plus,
            widgetAddInstructionsKey(WidgetAddPath.IOS_PLUS_BUTTON),
        )
    }

    @Test
    fun `no two add paths share a caption`() {
        val keys = WidgetAddPath.entries.map { widgetAddInstructionsKey(it) }
        assertEquals(WidgetAddPath.entries.size, keys.toSet().size, "captions: $keys")
    }
}
