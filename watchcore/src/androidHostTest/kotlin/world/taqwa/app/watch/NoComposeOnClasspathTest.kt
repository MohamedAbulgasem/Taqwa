package world.taqwa.app.watch

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * `watchcore` is what every watch surface reads: the Wear OS app's complication sources and Tile,
 * and (plan 4) the watchOS app and its complications, which run in a watch's memory and on its
 * battery. It is plain Kotlin and kotlinx.serialization, and nothing that draws: a dependency on
 * Compose or Skia added to `watchcore/build.gradle.kts` in a hurry fails here, as `widgetcore`'s
 * does, and not on a watch.
 */
class NoComposeOnClasspathTest {

    @Test
    fun composeIsNotOnTheModuleClasspath() {
        listOf(
            "androidx.compose.runtime.Composer",
            "androidx.compose.ui.graphics.Color",
            "org.jetbrains.skia.Canvas",
        ).forEach { className ->
            assertFailsWith<ClassNotFoundException>("$className must not be reachable from watchcore") {
                Class.forName(className)
            }
        }
    }
}
