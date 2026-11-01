package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The branching the Widgets screen does beneath each widget preview, tested as the pure function it
 * is, no composable and no platform; and what a screen keeps when it reads placement again.
 *
 * The cases that matter most are the reads that cannot tell. No AppWidgetManager, a WidgetKit
 * failure or its two-second timeout is not an answer, so it must never put a check on a card or
 * count a widget as added; and a read that cannot tell after one that could must not take a true
 * answer away. A regression in either does not crash or fail anywhere else: it just tells the
 * reader a widget is on the home screen when it is not, or flips a correct check off for a moment.
 */
class WidgetPlacementTest {

    /** A source that gives [answers] in turn, the last one for every read after. */
    private class Answers(private vararg val answers: WidgetPlacement?) : WidgetPlacementSource {
        var reads = 0
            private set

        override suspend fun current(): WidgetPlacement? = answers[minOf(reads++, answers.lastIndex)]
    }

    @Test
    fun `a first read that cannot tell leaves nothing shown`() = runTest {
        assertNull(Answers(null).reread(shown = null))
    }

    @Test
    fun `a read that cannot tell keeps the answer already shown`() = runTest {
        val shown = WidgetPlacement(prayer = true, ayah = false, lockScreen = true)
        assertEquals(shown, Answers(null).reread(shown = shown))
    }

    @Test
    fun `a read that answers replaces the answer shown even when a widget has gone`() = runTest {
        val shown = WidgetPlacement(prayer = true, ayah = true, lockScreen = true)
        val now = WidgetPlacement(prayer = false, ayah = true, lockScreen = false)
        assertEquals(now, Answers(now).reread(shown = shown))
    }

    @Test
    fun `answers then a read that cannot tell then answers again shows each answer in turn`() = runTest {
        val first = WidgetPlacement(prayer = true, ayah = false)
        val second = WidgetPlacement(prayer = false, ayah = false)
        val source = Answers(first, null, second)
        var shown: WidgetPlacement? = null
        shown = source.reread(shown)
        assertEquals(first, shown)
        shown = source.reread(shown)
        assertEquals(first, shown)
        shown = source.reread(shown)
        assertEquals(second, shown)
        assertEquals(3, source.reads)
    }

    @Test
    fun `the Lock Screen widget is not reported unless a platform says so`() {
        // Android has no Lock Screen widget and builds its answer from the two home-screen ones.
        assertFalse(WidgetPlacement(prayer = true, ayah = true).lockScreen)
    }

    @Test
    fun `a placed widget is offered nothing whatever the platform could do`() {
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
    fun `a missing widget the launcher can be asked for gets the row naming that widget`() {
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
    fun `a pin request the launcher refused turns the row into the steps`() {
        for (widget in PinnableWidget.entries) {
            assertEquals(
                WidgetOffer.Instructions(WidgetAddPath.ANDROID_PIN),
                widgetOffer(widget, placed = false, pinnable = true, addPath = WidgetAddPath.ANDROID_PIN, refused = true),
            )
        }
    }

    @Test
    fun `a refused request says nothing once the widget is on the home screen`() {
        assertEquals(
            WidgetOffer.None,
            widgetOffer(PinnableWidget.PRAYER, placed = true, pinnable = true, addPath = WidgetAddPath.ANDROID_PIN, refused = true),
        )
    }

    @Test
    fun `all three add paths are covered so a new one cannot slip through untested`() {
        assertEquals(
            listOf(WidgetAddPath.ANDROID_PIN, WidgetAddPath.IOS_HOLD_ICON, WidgetAddPath.IOS_PLUS_BUTTON),
            WidgetAddPath.entries.toList(),
        )
    }
}
