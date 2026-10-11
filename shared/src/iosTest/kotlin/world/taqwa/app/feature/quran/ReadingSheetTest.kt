package world.taqwa.app.feature.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_size_mushaf_note
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader's sheet around its size slider, which the adhkar's sheet shares
 * ([ReadingSizeSlider]): a commit writes the size and keeps every other setting, and under the
 * Mushaf the slider is still and the note says why. Words come from the resources; digits from the
 * English format the scene is given.
 */
@OptIn(ExperimentalTestApi::class)
class ReadingSheetTest {

    private val settings = ReadingSettings(
        mode = ReadingMode.TRANSLATION,
        arabicSizeSp = 28,
        transliteration = true,
        translationId = "fr.hamidullah",
    )

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    /** Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test process. */
    @Composable
    private fun Sheet(mushafMode: Boolean, changes: MutableList<ReadingSettings>) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
        ) {
            ReadingSheet(
                settings = settings,
                translations = emptyList(),
                previewAyah = "بِسْمِ ٱللَّهِ",
                mushafMode = mushafMode,
                onChange = { changes += it },
                onDismiss = {},
            )
        }
    }

    @Test
    fun theSizeCommitsOnceWithEveryOtherSettingKept() = runComposeUiTest {
        val changes = mutableListOf<ReadingSettings>()
        setContent { Sheet(mushafMode = false, changes = changes) }
        onNodeWithText("28").assertIsDisplayed()
        onNode(slider).assertIsEnabled().performSemanticsAction(SemanticsActions.SetProgress) { it(32f) }
        assertEquals(listOf(settings.copy(arabicSizeSp = 32)), changes)
        // The row's number follows the slider before the store does.
        onNodeWithText("32").assertIsDisplayed()
    }

    @Test
    fun underTheMushafTheSliderIsStillAndSaysWhy() = runComposeUiTest {
        setContent { Sheet(mushafMode = true, changes = mutableListOf()) }
        onNode(slider).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.quran_size_mushaf_note)).assertIsDisplayed()
    }
}
