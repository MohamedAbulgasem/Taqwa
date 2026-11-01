package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhan_voice_row
import world.taqwa.app.resources.minutes_count
import world.taqwa.app.resources.notifications_adhkar
import world.taqwa.app.resources.notifications_duha
import world.taqwa.app.resources.notifications_duha_best
import world.taqwa.app.resources.notifications_duha_when
import world.taqwa.app.resources.notifications_exact_alarms_off
import world.taqwa.app.resources.notifications_iqama
import world.taqwa.app.resources.notifications_iqama_after
import world.taqwa.app.resources.notifications_iqama_note
import world.taqwa.app.resources.notifications_master_toggle
import world.taqwa.app.resources.notifications_prayers_label
import world.taqwa.app.resources.notifications_remind_before
import world.taqwa.app.resources.notifications_tahajjud
import world.taqwa.app.resources.notifications_voluntary_label
import world.taqwa.app.resources.prayer_fajr
import world.taqwa.app.resources.prayer_isha
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Settings › Notifications as a whole, layout B (Duha and Iqama spec §4.6): the master switch, then
 * PRAYERS with the five and the card of what applies to all five, then VOLUNTARY; while the master
 * switch is off, none of it; and the Iqama row holding still while the five lines above it come
 * and go. Expected words come from the resources: the simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationLayoutTest {

    private val format = createPlatformFormat()

    @Composable
    private fun Screen(
        settings: NotificationSettings,
        exactAlarmsUnavailable: Boolean = false,
        onToggleEnabled: (Boolean) -> Unit = {},
        onToggleIqama: (Boolean) -> Unit = {},
        reducedMotion: Boolean = false,
    ) {
        // Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test
        // process. The colours are all the screen reads from it.
        CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
            NotificationSettingsScreen(
                settings = settings,
                exactAlarmsUnavailable = exactAlarmsUnavailable,
                onBack = {},
                onToggleEnabled = onToggleEnabled,
                onPickLead = {},
                onPickSound = { _, _ -> },
                onPreviewSound = {},
                onToggleIqama = onToggleIqama,
                reducedMotion = reducedMotion,
            )
        }
    }

    private fun SemanticsNodeInteraction.top(): Dp = getUnclippedBoundsInRoot().top

    @Test
    fun thePrayersComeFirstThenWhatAppliesToAllFiveThenTheVoluntaryOnes() = runComposeUiTest {
        setContent { Screen(NotificationSettings()) }
        val order = listOf(
            "master" to onNode(isToggleable() and hasText(getString(Res.string.notifications_master_toggle))),
            "PRAYERS" to onNodeWithText(getString(Res.string.notifications_prayers_label)),
            "Fajr" to onNodeWithText(getString(Res.string.prayer_fajr)),
            "Isha" to onNodeWithText(getString(Res.string.prayer_isha)),
            "Remind me before" to onNodeWithText(getString(Res.string.notifications_remind_before)),
            "Adhan" to onNodeWithText(getString(Res.string.adhan_voice_row)),
            "Iqama" to onNode(isToggleable() and hasText(getString(Res.string.notifications_iqama))),
            "VOLUNTARY" to onNodeWithText(getString(Res.string.notifications_voluntary_label)),
            "Tahajjud" to onNode(isToggleable() and hasText(getString(Res.string.notifications_tahajjud))),
            "Duha" to onNode(isToggleable() and hasText(getString(Res.string.notifications_duha))),
            "adhkar" to onNode(isToggleable() and hasText(getString(Res.string.notifications_adhkar))),
        )
        order.zipWithNext().forEach { (above, below) ->
            assertTrue(above.second.top() < below.second.top(), "${above.first} should sit above ${below.first}")
        }

        // One group: the shared card sits 14 dp under the five, with no label between, and holds
        // its three rows a divider apart. The VOLUNTARY label starts the next group 28 dp down.
        val rows = order.toMap()
        assertGap(14.dp, rows.getValue("Isha"), rows.getValue("Remind me before"))
        assertGap(1.dp, rows.getValue("Remind me before"), rows.getValue("Adhan"))
        assertGap(1.dp, rows.getValue("Adhan"), rows.getValue("Iqama"))
        assertGap(28.dp, rows.getValue("Iqama"), rows.getValue("VOLUNTARY"))
        assertGap(1.dp, rows.getValue("Tahajjud"), rows.getValue("Duha"))
        assertGap(1.dp, rows.getValue("Duha"), rows.getValue("adhkar"))
    }

    @Test
    fun withTheMasterSwitchOffNothingBelowItShowsAndOnItAllComesBackAsItWas() = runComposeUiTest {
        var settings by mutableStateOf(
            NotificationSettings(enabled = false, remindBeforeMinutes = 10, iqama = true, duha = true),
        )
        setContent {
            Screen(
                settings,
                exactAlarmsUnavailable = true,
                onToggleEnabled = { settings = settings.copy(enabled = it) },
            )
        }
        val master = onNode(isToggleable() and hasText(getString(Res.string.notifications_master_toggle)))
        master.assertIsDisplayed().assertIsOff()
        val below = listOf(
            Res.string.notifications_exact_alarms_off,
            Res.string.notifications_prayers_label,
            Res.string.prayer_fajr,
            Res.string.notifications_remind_before,
            Res.string.adhan_voice_row,
            Res.string.notifications_iqama,
            Res.string.notifications_iqama_note,
            Res.string.notifications_voluntary_label,
            Res.string.notifications_tahajjud,
            Res.string.notifications_duha,
            Res.string.notifications_duha_when,
            Res.string.notifications_adhkar,
        ).map { getString(it) }
        for (text in below) onAllNodesWithText(text).assertCountEquals(0)

        master.performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        master.assertIsOn()
        for (text in below) assertTrue(onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(), "$text is back")
        // With the values it had: the lead, Fajr's iqama line and Duha's timing.
        val ten = getPluralString(Res.plurals.minutes_count, 10, format.localizedDigits(10))
        onNode(hasText(getString(Res.string.notifications_remind_before)) and hasText(ten)).assertExists()
        val fifteen = getPluralString(Res.plurals.notifications_iqama_after, 15, format.localizedDigits(15))
        onNode(hasText(getString(Res.string.prayer_fajr)) and hasText(fifteen)).assertExists()
        val best = getString(Res.string.notifications_duha_best)
        onNode(hasText(getString(Res.string.notifications_duha_when)) and hasText(best)).assertExists()

        // And off again, all of it goes.
        master.performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        for (text in below) onAllNodesWithText(text).assertCountEquals(0)
    }

    @Test
    fun theIqamaRowHoldsStillMidScreen() = iqamaMidScreen(reducedMotion = false)

    // Reduced motion: no reveal, the lines come and go in one frame.
    @Test
    fun theIqamaRowHoldsStillMidScreenUnderReducedMotion() = iqamaMidScreen(reducedMotion = true)

    private fun iqamaMidScreen(reducedMotion: Boolean) = runComposeUiTest {
        val iqama = iqamaScreen(480.dp, reducedMotion)
        iqama.performScrollTo()
        flipIqamaAndHoldStill(iqama, "mid-screen, on")
        // The five lines did arrive above it: Dhuhr's, Asr's and Isha's 10 minutes among them.
        val ten = getPluralString(Res.plurals.notifications_iqama_after, 10, format.localizedDigits(10))
        onAllNodesWithText(ten).assertCountEquals(3)
        flipIqamaAndHoldStill(iqama, "mid-screen, off")
    }

    // The range grows as the lines arrive, and the row only holds if the scroll may already use the
    // room it has just gained: the anchor dispatches after the scroller has measured, not before.
    @Test
    fun theIqamaRowHoldsStillAtTheEndOfTheScroll() = iqamaAtTheEnd(reducedMotion = false)

    // The whole shrink in one frame, so the scroll pulls in by all of it at once.
    @Test
    fun theIqamaRowHoldsStillAtTheEndOfTheScrollUnderReducedMotion() = iqamaAtTheEnd(reducedMotion = true)

    private fun iqamaAtTheEnd(reducedMotion: Boolean) = runComposeUiTest {
        val iqama = iqamaScreen(480.dp, reducedMotion)
        scrollToTheEnd()
        // Read now: the range's own numbers are live.
        val end = scrollRange().maxValue()
        assertTrue(end > 0f && scrollRange().value() == end, "at the end of a scrolling screen")
        flipIqamaAndHoldStill(iqama, "at the end, on")
        assertTrue(scrollRange().value() > end, "scrolled past the old end")
        // Iqama's subtitle, Sound and note arrived below the row: down to the end again, so the off
        // flip meets the range shrinking under the offset.
        scrollToTheEnd()
        flipIqamaOffAtTheEnd(iqama)
    }

    @Test
    fun theIqamaRowHoldsStillOnAScreenThatDidNotScroll() = runComposeUiTest {
        var height by mutableStateOf(480.dp)
        val iqama = iqamaScreen { height }
        // The whole screen, 8 dp to spare: nothing to scroll until the lines arrive. The box may be
        // taller than the test's window, hence requiredHeight below.
        val content = with(density) { scrollRange().maxValue().toDp() } + height
        height = content + 8.dp
        waitForIdle()
        assertTrue(scrollRange().maxValue() == 0f, "the screen fits")
        flipIqamaAndHoldStill(iqama, "fitting, on")
        assertTrue(scrollRange().value() > 0f, "scrolled to hold the row")
        flipIqamaAndHoldStill(iqama, "fitting, off")
    }

    private suspend fun ComposeUiTest.iqamaScreen(height: Dp, reducedMotion: Boolean = false) =
        iqamaScreen(reducedMotion) { height }

    /** The screen in a phone-wide box, Iqama off, its switch writing straight back into it. */
    private suspend fun ComposeUiTest.iqamaScreen(
        reducedMotion: Boolean = false,
        height: () -> Dp,
    ): SemanticsNodeInteraction {
        var settings by mutableStateOf(NotificationSettings())
        setContent {
            Box(Modifier.requiredWidth(390.dp).requiredHeight(height())) {
                Screen(
                    settings,
                    onToggleIqama = { settings = settings.copy(iqama = it) },
                    reducedMotion = reducedMotion,
                )
            }
        }
        return onNode(isToggleable() and hasText(getString(Res.string.notifications_iqama)))
    }

    private fun ComposeUiTest.scrollToTheEnd() {
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        waitForIdle()
    }

    private fun ComposeUiTest.scrollRange() =
        onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    /**
     * Flips Iqama and reads the row's top on every frame of the reveal and once it has settled: the
     * five lines above it come or go, and the screen has to scroll with them in the same frame.
     */
    private fun ComposeUiTest.flipIqamaAndHoldStill(iqama: SemanticsNodeInteraction, where: String) {
        val before = iqama.top()
        mainClock.autoAdvance = false
        iqama.performSemanticsAction(SemanticsActions.OnClick)
        repeat(30) { frame ->
            mainClock.advanceTimeByFrame()
            assertStill(before, iqama.top(), "$where, frame $frame")
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertStill(before, iqama.top(), "$where, settled")
    }

    /**
     * Iqama off at the very end of the range. Everything that goes takes the end up with it, and the
     * scroll pulls itself in by all of it before the anchor runs, so the anchor may add nothing: on
     * every frame the row has moved down by exactly what has gone below it so far (its subtitle, its
     * Sound row and the note), and the scroll settles on the new end.
     */
    private suspend fun ComposeUiTest.flipIqamaOffAtTheEnd(iqama: SemanticsNodeInteraction) {
        val voluntary = onNodeWithText(getString(Res.string.notifications_voluntary_label))
        val before = iqama.top()
        val toVoluntary = voluntary.top() - before
        fun wentBelow() = toVoluntary - (voluntary.top() - iqama.top())
        mainClock.autoAdvance = false
        iqama.performSemanticsAction(SemanticsActions.OnClick)
        repeat(30) { frame ->
            mainClock.advanceTimeByFrame()
            assertStill(before + wentBelow(), iqama.top(), "at the end, off, frame $frame")
        }
        mainClock.autoAdvance = true
        waitForIdle()
        iqama.assertIsOff()
        assertTrue(wentBelow() > 0.dp, "Iqama's subtitle, Sound and note went")
        assertStill(before + wentBelow(), iqama.top(), "at the end, off, settled")
        val range = scrollRange()
        assertTrue(range.value() == range.maxValue(), "at ${range.value()}, not the new end ${range.maxValue()}")
    }

    private fun assertStill(expected: Dp, actual: Dp, where: String) = assertTrue(
        abs(actual.value - expected.value) <= 0.5f,
        "the Iqama row moved from $expected to $actual ($where)",
    )

    /** Within half a dp: the bounds come back in dp from whole pixels. */
    private fun assertGap(expected: Dp, above: SemanticsNodeInteraction, below: SemanticsNodeInteraction) {
        val gap = below.top() - above.getUnclippedBoundsInRoot().bottom
        assertTrue(abs(gap.value - expected.value) <= 0.5f, "expected a $expected gap, found $gap")
    }
}
