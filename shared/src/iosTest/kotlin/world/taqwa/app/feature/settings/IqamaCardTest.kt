package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.minutes_count
import world.taqwa.app.resources.notifications_iqama
import world.taqwa.app.resources.notifications_iqama_after
import world.taqwa.app.resources.notifications_iqama_not_friday
import world.taqwa.app.resources.notifications_iqama_note
import world.taqwa.app.resources.notifications_iqama_off
import world.taqwa.app.resources.notifications_iqama_subtitle
import world.taqwa.app.resources.notifications_section_iqama
import world.taqwa.app.resources.notifications_section_sound
import world.taqwa.app.resources.notifications_tahajjud_sound
import world.taqwa.app.resources.prayer_asr
import world.taqwa.app.resources.prayer_dhuhr
import world.taqwa.app.resources.prayer_fajr
import world.taqwa.app.resources.prayer_isha
import world.taqwa.app.resources.prayer_maghrib
import world.taqwa.app.resources.sound_sheet_title
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings › Notifications, the Iqama card (Duha and Iqama spec §4.2): off, one row and its switch
 * and nothing anywhere else; on, its subtitle, Sound, the note under the card, a line under each
 * prayer with a gap, and each prayer's sound sheet ends with that prayer's minutes (Dhuhr's with
 * the Friday note). Expected words come from the resources: the simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class IqamaCardTest {

    private val radioRow = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val format = createPlatformFormat()

    @Composable
    private fun Screen(
        settings: NotificationSettings,
        onToggleIqama: (Boolean) -> Unit = {},
        onPickIqamaMinutes: (Prayer, Int) -> Unit = { _, _ -> },
    ) {
        // Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test
        // process. The colours are all the screen reads from it.
        CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
            NotificationSettingsScreen(
                settings = settings,
                onBack = {},
                onToggleEnabled = {},
                onPickLead = {},
                onPickSound = { _, _ -> },
                onPreviewSound = {},
                onToggleIqama = onToggleIqama,
                onPickIqamaMinutes = onPickIqamaMinutes,
            )
        }
    }

    private suspend fun line(minutes: Int) =
        getPluralString(Res.plurals.notifications_iqama_after, minutes, format.localizedDigits(minutes))

    private suspend fun nameOf(prayer: Prayer) = getString(
        when (prayer) {
            Prayer.FAJR -> Res.string.prayer_fajr
            Prayer.DHUHR -> Res.string.prayer_dhuhr
            Prayer.ASR -> Res.string.prayer_asr
            Prayer.MAGHRIB -> Res.string.prayer_maghrib
            else -> Res.string.prayer_isha
        },
    )

    @Test
    fun offTheIqamaCardAddsNothingAnywhere() = runComposeUiTest {
        val toggled = mutableListOf<Boolean>()
        setContent { Screen(NotificationSettings(), onToggleIqama = { toggled += it }) }
        val iqama = onNode(isToggleable() and hasText(getString(Res.string.notifications_iqama)))
        iqama.performScrollTo().assertIsDisplayed().assertIsOff()
        onAllNodesWithText(getString(Res.string.notifications_iqama_subtitle)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.notifications_iqama_note)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.notifications_tahajjud_sound)).assertCountEquals(0)
        for (minutes in setOf(5, 10, 15)) onAllNodesWithText(line(minutes)).assertCountEquals(0)

        iqama.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(true), toggled)

        // Dhuhr's sound sheet is the sheet it always was: its own title, no Iqama list, no note.
        onNodeWithText(nameOf(Prayer.DHUHR)).performScrollTo().performClick()
        onNodeWithText(getString(Res.string.sound_sheet_title)).assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.notifications_section_sound)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.notifications_section_iqama)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.notifications_iqama_off)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.notifications_iqama_not_friday)).assertCountEquals(0)
        onAllNodes(radioRow).assertCountEquals(0)
    }

    @Test
    fun onTheIqamaCardShowsItsSoundTheNoteAndALineUnderEachPrayer() = runComposeUiTest {
        setContent { Screen(NotificationSettings(iqama = true)) }
        onNodeWithText(getString(Res.string.notifications_iqama_subtitle)).performScrollTo().assertIsDisplayed()
        onNodeWithText(getString(Res.string.notifications_iqama_note)).performScrollTo().assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.notifications_tahajjud_sound)).assertCountEquals(1)
        // The defaults, each read with its prayer's row as one element: name, line, sound.
        for (prayer in ObligatoryPrayers) {
            val minutes = NotificationSettings.IqamaDefaults.getValue(prayer)
            onNode(hasText(nameOf(prayer)) and hasText(line(minutes))).performScrollTo().assertIsDisplayed()
        }
        onAllNodesWithText(line(10)).assertCountEquals(3)
    }

    @Test
    fun anOffPrayerHasNoLine() = runComposeUiTest {
        val settings = NotificationSettings(iqama = true, iqamaMinutes = NotificationSettings.IqamaDefaults + (Prayer.FAJR to 0))
        setContent { Screen(settings) }
        onNodeWithText(nameOf(Prayer.FAJR)).performScrollTo().assertIsDisplayed()
        onAllNodesWithText(line(15)).assertCountEquals(0)
        onAllNodesWithText(line(10)).assertCountEquals(3)
    }

    @Test
    fun dhuhrsSheetEndsWithItsMinutesAndTheFridayNote() = runComposeUiTest {
        var picked: Pair<Prayer, Int>? = null
        setContent { Screen(NotificationSettings(iqama = true), onPickIqamaMinutes = { p, m -> picked = p to m }) }
        val dhuhr = nameOf(Prayer.DHUHR)
        onNode(hasText(dhuhr) and hasText(line(10))).performScrollTo().performClick()

        // Titled with the prayer's name over a SOUND label; then its iqama minutes as radio rows.
        onAllNodesWithText(getString(Res.string.sound_sheet_title)).assertCountEquals(0)
        onAllNodesWithText(dhuhr).assertCountEquals(2)
        onNodeWithText(getString(Res.string.notifications_section_sound)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.notifications_section_iqama)).performScrollTo().assertIsDisplayed()
        onAllNodes(radioRow).assertCountEquals(NotificationSettings.IqamaOptions.size)
        onNode(radioRow and hasText(getString(Res.string.notifications_iqama_off))).assertExists()
        val ten = getPluralString(Res.plurals.minutes_count, 10, format.localizedDigits(10))
        onNode(radioRow and hasText(ten) and isSelected()).assertExists()
        onNodeWithText(getString(Res.string.notifications_iqama_not_friday)).performScrollTo().assertIsDisplayed()

        val fifteen = getPluralString(Res.plurals.minutes_count, 15, format.localizedDigits(15))
        onNode(radioRow and hasText(fifteen)).performScrollTo().performClick()
        assertEquals(Prayer.DHUHR to 15, picked)
        waitForIdle()
        onAllNodes(radioRow).assertCountEquals(0)
    }

    @Test
    fun asrsSheetHasItsMinutesButNoFridayNote() = runComposeUiTest {
        setContent { Screen(NotificationSettings(iqama = true)) }
        onNode(hasText(nameOf(Prayer.ASR)) and hasText(line(10))).performScrollTo().performClick()
        onNodeWithText(getString(Res.string.notifications_section_iqama)).performScrollTo().assertIsDisplayed()
        onAllNodes(radioRow).assertCountEquals(NotificationSettings.IqamaOptions.size)
        onAllNodesWithText(getString(Res.string.notifications_iqama_not_friday)).assertCountEquals(0)
    }
}
