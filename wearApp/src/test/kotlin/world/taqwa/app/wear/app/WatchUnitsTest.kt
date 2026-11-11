package world.taqwa.app.wear.app

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drawings' 200 units across the screen's shorter side, and the sizes text is drawn at (call
 * 15). The font-scale tests run Compose's own converter on the JVM: on Android, `Density(density,
 * fontScale)` turns sp into dp through `FontScaleConverterFactory` from a scale of 1.03, which is
 * plain Kotlin, so no Robolectric is needed to watch it bend.
 */
class WatchUnitsTest {

    @Test
    fun twoHundredUnitsSpanTheShorterSide() {
        assertEquals(1.135f, WatchUnits.of(227, 227).dp, 1e-6f)
        assertEquals(0.96f, WatchUnits.of(192, 240).dp, 1e-6f)
        assertEquals(0.96f, WatchUnits.of(240, 192).dp, 1e-6f)
    }

    @Test
    fun aUnitIsItsShareOfTheScreen() {
        val units = WatchUnits.of(227, 227)

        assertEquals(52.21f, units.x(46f).value, 0.005f, "Tasbeeh's count")
        assertEquals(43.13f, units.x(38f).value, 0.005f, "Today's countdown")
    }

    @Test
    fun aLineIsDrawnAtItsUnitsWhateverTheFontScale() {
        val units = WatchUnits.of(227, 227)
        for (scale in listOf(1.0f, 1.15f, 1.24f, 1.3f)) {
            val density = Density(2f, scale)
            for (u in listOf(46f, 38f, 10.5f)) {
                val drawn = with(density) { units.sp(u, density).toDp() }
                assertEquals(units.x(u).value, drawn.value, 0.01f, "$u units at a font scale of $scale")
            }
        }
    }

    @Test
    fun theConverterIsNotLinear() {
        // The guard of the test above: dividing by the font scale, the old formula, would draw
        // Tasbeeh's count about 12 dp small at 1.3, because the converter draws 30 to 100 sp
        // almost one to one there (40.16 dp measured on ui-unit 1.10.0).
        val density = Density(2f, 1.3f)

        val divided = with(density) { (52.21f / 1.3f).sp.toDp() }

        assertTrue(divided.value < 41f, "the count divided by the scale draws at ${divided.value} dp")
    }

    @Test
    fun theChordAt184Is108Units() {
        assertEquals(108.5f, WatchUnits.chordUnits(184f, round = true, inset = 0f), 0.1f)
        assertEquals(188f, WatchUnits.chordUnits(100f, round = true), 1e-4f)
    }

    @Test
    fun aSquareScreensChordIsItsWidth() {
        assertEquals(188f, WatchUnits.chordUnits(184f, round = false), 1e-4f)
    }

    @Test
    fun aLineOffTheRoundScreenHasNoRoom() {
        assertEquals(0f, WatchUnits.chordUnits(0f, round = true, inset = 0f), 1e-4f)
        assertEquals(0f, WatchUnits.chordUnits(-5f, round = true), 1e-4f, "above the screen")
        assertEquals(0f, WatchUnits.chordUnits(205f, round = true), 1e-4f, "below it")
        assertEquals(0f, WatchUnits.chordUnits(1f, round = true, inset = 20f), 1e-4f, "an inset wider than the chord")
    }
}
