package world.taqwa.app.widget

import world.taqwa.app.domain.WidgetBackground
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The reason this module exists. `widgetcore` is what the iOS WidgetKit extension links, and a
 * WidgetKit extension runs under a ~30 MB memory ceiling; letting Compose (and with it Skia) back
 * onto this classpath would blow it, and the failure mode on a real device is a widget that
 * silently never appears. A dependency added to `widgetcore/build.gradle.kts` in a hurry fails
 * here rather than in the field.
 */
class NoComposeOnClasspathTest {

    @Test
    fun composeIsNotOnTheModuleClasspath() {
        listOf(
            "androidx.compose.runtime.Composer",
            "androidx.compose.ui.graphics.Color",
            "org.jetbrains.skia.Canvas",
        ).forEach { className ->
            assertFailsWith<ClassNotFoundException>("$className must not be reachable from widgetcore") {
                Class.forName(className)
            }
        }
    }

    /** The palette hands out plain ARGB numbers precisely so no UI-framework colour type — and
     * therefore no UI framework — is ever needed to consume it. */
    @Test
    fun paletteColoursArePlainArgbNumbersRatherThanAUiFrameworkType() {
        val colors = WidgetPalette.colorsFor(WidgetBackground.DARK, systemIsDark = true)
        val fields = colors::class.java.declaredFields.associate { it.name to it.type.name }
        kotlin.test.assertEquals("long", fields["backgroundArgb"])
        kotlin.test.assertEquals("long", fields["textArgb"])
        kotlin.test.assertEquals("long", fields["accentArgb"])
        kotlin.test.assertEquals("float", fields["backgroundAlpha"])
    }
}
