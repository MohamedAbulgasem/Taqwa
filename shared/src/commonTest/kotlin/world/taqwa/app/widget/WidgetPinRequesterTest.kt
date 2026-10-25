package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The owner's rule for "Add widget": the steps by hand appear only when the app knows the request
 * did not work. Onboarding and Appearance both ask through [askToPin], so this is that rule.
 */
class WidgetPinRequesterTest {

    private class Launcher(private val takes: Boolean) : WidgetPinRequester {
        val asked = mutableListOf<PinnableWidget>()
        override fun isSupported(widget: PinnableWidget) = true
        override fun requestPin(widget: PinnableWidget): Boolean {
            asked += widget
            return takes
        }
    }

    @Test
    fun `a request the launcher took runs only the taken branch`() {
        for (widget in PinnableWidget.entries) {
            val launcher = Launcher(takes = true)
            val ran = mutableListOf<String>()
            launcher.askToPin(widget, onTaken = { ran += "taken" }, onRefused = { ran += "refused" })
            assertEquals(listOf(widget), launcher.asked)
            assertEquals(listOf("taken"), ran)
        }
    }

    @Test
    fun `a request the launcher refused runs only the refused branch`() {
        for (widget in PinnableWidget.entries) {
            val launcher = Launcher(takes = false)
            val ran = mutableListOf<String>()
            launcher.askToPin(widget, onTaken = { ran += "taken" }, onRefused = { ran += "refused" })
            assertEquals(listOf(widget), launcher.asked)
            assertEquals(listOf("refused"), ran)
        }
    }
}
