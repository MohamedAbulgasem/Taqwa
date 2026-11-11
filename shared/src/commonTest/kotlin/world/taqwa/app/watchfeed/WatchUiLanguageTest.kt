package world.taqwa.app.watchfeed

import kotlinx.coroutines.test.runTest
import world.taqwa.app.i18n.UiLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The feed's language is the app's own: the `ui_language` resource, as `uiLanguage()` reads it in a
 * composition, and never the locale tag. A read that throws is passed on, so the sync fails rather
 * than guess English.
 */
class WatchUiLanguageTest {

    @Test
    fun theLanguageIsTheResourcesString() = runTest {
        assertEquals(UiLanguage.ARABIC, watchUiLanguage { "ar" })
        assertEquals(UiLanguage.URDU, watchUiLanguage { "ur" })
        assertEquals(UiLanguage.INDONESIAN, watchUiLanguage { "id" })
        assertEquals(UiLanguage.ENGLISH, watchUiLanguage { "en" })
        val thrown = assertFailsWith<IllegalStateException> {
            watchUiLanguage { throw IllegalStateException("no resource environment") }
        }
        assertEquals("no resource environment", thrown.message)
    }
}
