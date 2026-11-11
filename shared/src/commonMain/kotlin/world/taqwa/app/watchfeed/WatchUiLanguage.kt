package world.taqwa.app.watchfeed

import org.jetbrains.compose.resources.getString
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.ui_language

/**
 * The app's own interface language, read off the composition for the watch feed: the very
 * `ui_language` resource `uiLanguage()` reads in a composition, which Compose Multiplatform resolves
 * outside one through the same environment. Never `UiLanguage.of(format.languageTag())`: a per-app
 * language or a regional variant once gave Arabic strings with the tag's English rule
 * (`uiLanguage()`'s KDoc), and the watch must write what the app writes.
 *
 * [resolve] is the read, so the tests can stand in for it. A read that throws passes to the caller,
 * whose sync then answers that it failed: the language is never guessed from the tag.
 */
internal suspend fun watchUiLanguage(
    resolve: suspend () -> String = { getString(Res.string.ui_language) },
): UiLanguage = UiLanguage.of(resolve())
