package world.taqwa.app.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.credit_adhkar
import world.taqwa.app.resources.credit_adhkar_detail
import kotlin.test.Test

/**
 * Settings › Attribution & licences credits the adhkar (adhkar spec §4.2): Hisn al-Muslim, the
 * OpenDua dataset and its CC BY 4.0, with the source the row opens. The words are the resources',
 * in whatever language the simulator runs.
 */
@OptIn(ExperimentalTestApi::class)
class AdhkarCreditTest {

    @Test
    fun theAttributionScreenCreditsTheAdhkarAndTheirSource() = runComposeUiTest {
        setContent {
            // Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test
            // process. The colours are all the screen reads from it.
            CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
                AttributionScreen(translations = emptyList(), reciters = emptyList(), onBack = {})
            }
        }
        onNodeWithText(getString(Res.string.credit_adhkar)).performScrollTo().assertIsDisplayed()
        onNodeWithText(getString(Res.string.credit_adhkar_detail), substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("opendua.org", substring = true).performScrollTo().assertIsDisplayed()
    }
}
