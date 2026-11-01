package world.taqwa.app.feature.settings

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.widgets_background_note
import world.taqwa.app.resources.widgets_ios_fallback
import world.taqwa.app.resources.widgets_step_android_1
import world.taqwa.app.resources.widgets_step_android_2
import world.taqwa.app.resources.widgets_step_android_3
import world.taqwa.app.resources.widgets_step_ios_hold_1
import world.taqwa.app.resources.widgets_step_ios_hold_2
import world.taqwa.app.resources.widgets_step_ios_hold_3
import world.taqwa.app.resources.widgets_step_ios_plus_1
import world.taqwa.app.resources.widgets_step_ios_plus_2
import world.taqwa.app.resources.widgets_step_ios_plus_3
import world.taqwa.app.resources.widgets_step_lock_1
import world.taqwa.app.resources.widgets_step_lock_2
import world.taqwa.app.resources.widgets_step_lock_3
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.translucentOrFrostedSubtitleKey

/**
 * The Widgets screen's decisions that need no composable: which steps a platform's add path
 * writes out, which card shows them open, and which note sits under the background choice.
 *
 * Three phones' steps that must never be swapped, so each path is pinned to its own keys in order,
 * not only counted. Then how a step's sentence is shown: the name and the marked words in bold.
 */
class WidgetStepsTest {

    @Test
    fun `the Android path is its three steps with no fallback line`() {
        val steps = widgetSteps(WidgetAddPath.ANDROID_PIN)
        assertEquals(3, steps.steps.size)
        assertEquals(
            listOf(Res.string.widgets_step_android_1, Res.string.widgets_step_android_2, Res.string.widgets_step_android_3),
            steps.steps,
        )
        assertNull(steps.fallback)
    }

    @Test
    fun `holding the icon on iPhone carries the fallback for a phone without it`() {
        val steps = widgetSteps(WidgetAddPath.IOS_HOLD_ICON)
        assertEquals(
            listOf(Res.string.widgets_step_ios_hold_1, Res.string.widgets_step_ios_hold_2, Res.string.widgets_step_ios_hold_3),
            steps.steps,
        )
        assertEquals(Res.string.widgets_ios_fallback, steps.fallback)
    }

    @Test
    fun `the plus button path on older iPhones has no fallback line`() {
        val steps = widgetSteps(WidgetAddPath.IOS_PLUS_BUTTON)
        assertEquals(
            listOf(Res.string.widgets_step_ios_plus_1, Res.string.widgets_step_ios_plus_2, Res.string.widgets_step_ios_plus_3),
            steps.steps,
        )
        assertNull(steps.fallback)
    }

    @Test
    fun `the lock screen steps are their own three with no fallback line`() {
        assertEquals(
            listOf(Res.string.widgets_step_lock_1, Res.string.widgets_step_lock_2, Res.string.widgets_step_lock_3),
            LockScreenSteps.steps,
        )
        assertNull(LockScreenSteps.fallback)
    }

    @Test
    fun `every step has its own illustration`() {
        (WidgetAddPath.entries.map(::widgetSteps) + LockScreenSteps).forEach {
            assertEquals(it.steps.size, it.art.size)
        }
    }

    @Test
    fun `only the widget that needs steps shows them open`() {
        val offers = listOf(
            PinnableWidget.PRAYER to WidgetOffer.None,
            PinnableWidget.AYAH to WidgetOffer.Instructions(WidgetAddPath.IOS_HOLD_ICON),
        )
        assertEquals(PinnableWidget.AYAH, expandedStepsWidget(offers))
    }

    @Test
    fun `when both widgets need steps only the first shows them open`() {
        val offers = listOf(
            PinnableWidget.PRAYER to WidgetOffer.Instructions(WidgetAddPath.IOS_HOLD_ICON),
            PinnableWidget.AYAH to WidgetOffer.Instructions(WidgetAddPath.IOS_HOLD_ICON),
        )
        assertEquals(PinnableWidget.PRAYER, expandedStepsWidget(offers))
    }

    @Test
    fun `no widget shows steps when each is placed or can be added with a tap`() {
        val offers = listOf(
            PinnableWidget.PRAYER to WidgetOffer.Pin(PinnableWidget.PRAYER),
            PinnableWidget.AYAH to WidgetOffer.None,
        )
        assertNull(expandedStepsWidget(offers))
        assertNull(expandedStepsWidget(emptyList()))
    }

    @Test
    fun `on iPhone with both widgets missing only the first card opens its steps`() {
        val offers = PinnableWidget.entries.map { it to WidgetOffer.Instructions(WidgetAddPath.IOS_HOLD_ICON) }
        assertEquals(setOf(PinnableWidget.PRAYER), stepsShownOpen(offers, refused = emptySet()))
    }

    @Test
    fun `a card whose button the launcher refused opens its steps at once`() {
        val offers = listOf(
            PinnableWidget.PRAYER to WidgetOffer.Pin(PinnableWidget.PRAYER),
            PinnableWidget.AYAH to WidgetOffer.Instructions(WidgetAddPath.ANDROID_PIN),
        )
        assertEquals(setOf(PinnableWidget.AYAH), stepsShownOpen(offers, refused = setOf(PinnableWidget.AYAH)))
    }

    /** The ayah card was refused first and is showing its steps; refusing the prayer card after it
     * must not fold them away under the reader, so both stay open, each the answer to its own tap. */
    @Test
    fun `steps a refusal opened stay open when an earlier card is refused after`() {
        val offers = PinnableWidget.entries.map { it to WidgetOffer.Instructions(WidgetAddPath.ANDROID_PIN) }
        assertEquals(PinnableWidget.entries.toSet(), stepsShownOpen(offers, refused = PinnableWidget.entries.toSet()))
    }

    @Test
    fun `a refused widget that is now on the home screen opens nothing`() {
        val offers = listOf(
            PinnableWidget.PRAYER to WidgetOffer.None,
            PinnableWidget.AYAH to WidgetOffer.Pin(PinnableWidget.AYAH),
        )
        assertEquals(emptySet<PinnableWidget>(), stepsShownOpen(offers, refused = setOf(PinnableWidget.PRAYER)))
    }

    @Test
    fun `the translucent or frosted choice keeps its own platform's subtitle as the note`() {
        assertEquals(translucentOrFrostedSubtitleKey(true), widgetBackgroundNote(WidgetBackground.TRANSLUCENT_OR_FROSTED, isIos = true))
        assertEquals(translucentOrFrostedSubtitleKey(false), widgetBackgroundNote(WidgetBackground.TRANSLUCENT_OR_FROSTED, isIos = false))
    }

    @Test
    fun `every other choice reads the note for every widget`() {
        listOf(WidgetBackground.FOLLOW_THEME, WidgetBackground.LIGHT, WidgetBackground.DARK).forEach { value ->
            assertEquals(Res.string.widgets_background_note, widgetBackgroundNote(value, isIos = false))
            assertEquals(Res.string.widgets_background_note, widgetBackgroundNote(value, isIos = true))
        }
    }

    /** The stretches of [text] in bold, in order, leaving out empty ones. */
    private fun bold(text: AnnotatedString): List<String> = text.spanStyles
        .filter { it.item.fontWeight == FontWeight.Bold && it.end > it.start }
        .map { text.text.substring(it.start, it.end) }

    @Test
    fun `marked words show in bold without their stars`() {
        val shown = markedText("Tap **Widgets**.", name = "Ayah of the day")
        assertEquals("Tap Widgets.", shown.text)
        assertEquals(listOf("Widgets"), bold(shown))
    }

    @Test
    fun `the name goes in bold where the placeholder is beside marked words`() {
        val shown = markedText("Find **Taqwa**, then drag %1\$s into place.", name = "Prayer times")
        assertEquals("Find Taqwa, then drag Prayer times into place.", shown.text)
        assertEquals(listOf("Taqwa", "Prayer times"), bold(shown))
    }

    @Test
    fun `the name elsewhere in the sentence stays plain`() {
        val shown = markedText("Taqwa lists it: choose %1\$s.", name = "Taqwa")
        assertEquals("Taqwa lists it: choose Taqwa.", shown.text)
        assertEquals(listOf("Taqwa"), bold(shown))
        assertEquals(23, shown.spanStyles.single { it.end > it.start }.start)
    }

    @Test
    fun `a sentence with neither marks nor a name is shown as written`() {
        val shown = markedText("It lands on your Home Screen.", name = "")
        assertEquals("It lands on your Home Screen.", shown.text)
        assertEquals(emptyList(), bold(shown))
    }
}
