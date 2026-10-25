package world.taqwa.app.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import world.taqwa.app.feature.settings.AudioOptionRow
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.feature.settings.SectionLabel
import world.taqwa.app.feature.settings.SettingsScaffold
import world.taqwa.app.feature.settings.TimetableChoiceRow
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.nav_a11y_back
import world.taqwa.app.resources.sheet_a11y_drag_handle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ControlSemanticsTest {

    @Test
    fun theBackChevronIsANamedButton() = runComposeUiTest {
        var backed = false
        setContent { BackChevron(onBack = { backed = true }) }
        // The resource, not a literal: the simulator's language decides what "Back" is.
        val back = getString(Res.string.nav_a11y_back)
        onNodeWithContentDescription(back)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performClick()
        assertTrue(backed)
    }

    @Test
    fun aPrimaryButtonIsAButton() = runComposeUiTest {
        var pressed = false
        setContent { TaqwaPrimaryButton("Done", onClick = { pressed = true }) }
        // The role is what makes TalkBack say "button" and gives VoiceOver its button trait; a
        // bare clickable is read as "Done, double-tap to activate" and nothing more.
        onNodeWithText("Done")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performClick()
        assertTrue(pressed)
    }

    @Test
    fun aSettingsTitleAndItsSectionLabelsAreHeadings() = runComposeUiTest {
        setContent {
            SettingsScaffold("Prayer times", onBack = {}) {
                SectionLabel("Asr")
                TaqwaRow("Show both Asr times", onClick = {})
            }
        }
        // The title and the label are what heading navigation and the rotor jump between; the
        // row under them and the back chevron above are not headings.
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        onNodeWithText("Prayer times").assert(heading)
        onNodeWithText("Asr").assert(heading)
        onNodeWithText("Show both Asr times").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
        onAllNodes(heading).assertCountEquals(2)
    }

    @Test
    fun aSwitchRowIsOneSwitchThatFlipsOnActivation() = runComposeUiTest {
        var checked = false
        setContent { TaqwaRow("Prayer notifications", toggle = RowToggle(checked) { checked = it }) }
        // One toggleable node — the row — and not a second, nameless one for the drawn switch.
        onAllNodes(isToggleable()).assertCountEquals(1)
        // The row's click action is what a screen reader's double-tap calls. Not performClick():
        // that taps the middle of the row, and only the drawn switch at its end takes a touch.
        onNodeWithText("Prayer notifications")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(checked)
    }

    @Test
    fun aFingerOnTheDrawnSwitchStillFlipsItAndTheRestOfTheRowStillDoesNot() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { TaqwaRow("Prayer notifications", toggle = RowToggle(checked) { checked = it }) }
        val row = onNodeWithText("Prayer notifications")
        // Touch is what the row's semantics must leave alone. The middle of the row is the label,
        // which takes no tap, as before the switch was hidden from semantics.
        row.performTouchInput { click(center) }
        waitForIdle()
        assertFalse(checked)
        // The drawn switch sits at the row's end (the test window is left to right): inside the
        // row's 16 dp padding, the middle of its 48 dp target.
        row.performTouchInput { click(Offset(width - (16 + 24).dp.toPx(), centerY)) }
        waitForIdle()
        assertTrue(checked)
        row.assertIsOn()
    }

    @Test
    fun aSheetsDragHandleIsNamed() = runComposeUiTest {
        setContent { TaqwaBottomSheet(onDismissRequest = {}) { Text("Time format") } }
        // material3's clickable wrapper around the handle, with the sheet's dismiss action, takes
        // the name the handle carries; without it the node was clickable and nameless.
        onNode(hasContentDescription(getString(Res.string.sheet_a11y_drag_handle)) and hasClickAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
    }

    @Test
    fun anOptionRowIsARadioButtonWithItsState() = runComposeUiTest {
        setContent {
            TaqwaRow("Dark", onClick = {}, selectable = true, selected = true)
            TaqwaRow("Light", onClick = {}, selectable = true, selected = false)
        }
        onNodeWithText("Dark")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        onNodeWithText("Light").assertIsNotSelected()
    }

    @Test
    fun aSegmentedControlExposesTheSelectedSegmentAsATab() = runComposeUiTest {
        var picked = -1
        setContent { TaqwaSegmented(listOf("Surah", "Juz", "Bookmarks"), selectedIndex = 1) { picked = it } }
        onNodeWithText("Juz")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        onNodeWithText("Bookmarks").assertIsNotSelected().performClick()
        assertTrue(picked == 2)
        onAllNodes(isSelectable()).assertCountEquals(3)
    }

    @Test
    fun aTimetableChoiceSaysItIsSelectedAndStaysClickableWhenSelected() = runComposeUiTest {
        var confirmed = false
        var chosen = false
        setContent {
            Column {
                TimetableChoiceRow(label = "Umm al-Qura", detail = null, selected = true, checking = null, onClick = { confirmed = true })
                TimetableChoiceRow(label = "Muslim World League", detail = null, selected = false, checking = null, onClick = { chosen = true })
            }
        }
        // No role: a selected radio button would lose its click on Android, and the selected row
        // is how a paused timetable is confirmed.
        onNodeWithText("Umm al-Qura")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assertIsSelected()
            .assertHasClickAction()
            .performClick()
        assertTrue(confirmed)
        onNodeWithText("Muslim World League").assertIsNotSelected().performClick()
        assertTrue(chosen)
    }

    @Test
    fun anAudioOptionSaysItIsSelectedAndStaysClickableWhenSelected() = runComposeUiTest {
        var picked = false
        setContent { AudioOptionRow(label = "Mishary Alafasy", caption = "Not downloaded", selected = true, onSelect = { picked = true }) }
        onNodeWithText("Mishary Alafasy")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assertIsSelected()
            .assertHasClickAction()
            .performClick()
        assertTrue(picked)
    }
}
