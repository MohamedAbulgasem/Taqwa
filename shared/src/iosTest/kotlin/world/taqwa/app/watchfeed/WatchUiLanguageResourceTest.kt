package world.taqwa.app.watchfeed

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.ui_language
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `watchUiLanguage()`'s default reads the very resource `uiLanguage()` reads in a composition,
 * through Compose Multiplatform's environment outside one, and never the locale tag.
 */
class WatchUiLanguageResourceTest {

    @Test
    fun theDefaultReadsTheUiLanguageString() = runTest {
        assertEquals(UiLanguage.of(getString(Res.string.ui_language)), watchUiLanguage())
    }
}
