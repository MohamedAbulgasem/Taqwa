package world.taqwa.app.wear.qibla

import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Qibla page's lines stand apart by more than the ink of the font that draws them reaches (plan 3
 * Task 7, review round 2): a caption's two lines, and the detail under the caption at every font
 * size, so that a new language's words or a new font cannot bring back the joined glyphs round 2 saw
 * in English ("Magnetic" / "interference", the g's tail in the f).
 *
 * The ink is the app's own Manrope, read from the watch app's resources, over every character the
 * phone sends in these lines in each of its languages: `:shared`'s strings and its watch phrases
 * (`WatchWords.holdLevel`), and every digit. The check knows no line break, so it holds wherever the
 * text breaks. Arabic and Bengali letters are drawn in the watch's own fonts, which a host test
 * cannot read; they are left out here, and their figures are in plan 3 Task 7's as-built note.
 *
 * The detail's own fit, its one line clear of the turning letters and the ticks at every heading
 * and font size and its gap under the caption, is `QiblaFitTest`'s (Ruling 66, which replaced the
 * two-line detail of Rulings 62 and 63 and their guards here).
 */
class QiblaInkTest {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    private val fonts = File(System.getProperty("taqwa.wearRes") ?: "src/main/res", "font")
    private val phoneStrings = File(System.getProperty("taqwa.sharedStrings") ?: "../shared/src/commonMain/composeResources")
    private val watchWords = File(System.getProperty("taqwa.watchWords") ?: "../shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchWords.kt")

    /** Every caption the page draws in ExtraBold: the bearing, "Facing qibla", the bad states' titles and the tilt notice. */
    private val captionKeys = listOf("qibla_bearing", "qibla_facing_qibla", "qibla_needs_calibrating", "qibla_interference_title", "qibla_best_effort_title", "qibla_no_sensor_title")

    /** Any figure the bearing or the distance may hold, and the phone's thousands separators. */
    private val figures = "0123456789,.  "

    private fun manrope(file: String): Font = Font.createFont(Font.TRUETYPE_FONT, File(fonts, file)).deriveFont(EM)

    /** The phone's strings for [keys] in each of its languages (`values*`), as Compose prints them. */
    private fun phrases(keys: List<String>): List<String> {
        val folders = phoneStrings.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue(folders.size >= 7, "the phone's seven languages, at ${phoneStrings.path}")
        return folders.flatMap { folder ->
            val xml = File(folder, "strings.xml").readText()
            keys.map { key ->
                val raw = Regex("""<string name="$key">(.*?)</string>""").find(xml)?.groupValues?.get(1)
                    ?: error("${folder.name} has no $key")
                raw.replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace(Regex("""%\d\$[sd]"""), "")
            }
        }
    }

    /** The tilt notice in each of the phone's languages (`WatchWords`, the watch's own phrases until plan 5's resources). */
    private fun tiltNotices(): List<String> {
        val notices = Regex("""holdLevel = "([^"]*)"""").findAll(watchWords.readText()).map { it.groupValues[1] }.toList()
        assertTrue("Hold your wrist level" in notices && notices.size >= 7, "the seven tilt notices, at ${watchWords.path}: $notices")
        return notices
    }

    /** How far [font]'s ink reaches above and below the baseline, in ems, over the characters of [texts] it draws, and which character reaches furthest each way. */
    private fun reach(font: Font, texts: List<String>): Reach {
        val context = FontRenderContext(null, true, true)
        var reach = Reach(0.0, ' ', 0.0, ' ')
        for (c in texts.joinToString("").toSet()) {
            if (c.isWhitespace() || !font.canDisplay(c)) continue
            val ink = font.createGlyphVector(context, c.toString()).getGlyphOutline(0).bounds2D
            if (ink.isEmpty) continue
            val above = -ink.minY / EM
            val below = ink.maxY / EM
            if (above > reach.above) reach = reach.copy(above = above, highest = c)
            if (below > reach.below) reach = reach.copy(below = below, lowest = c)
        }
        return reach
    }

    private data class Reach(val above: Double, val highest: Char, val below: Double, val lowest: Char)

    @Test
    fun manropesInkIsMeasuredAsHarfBuzzMeasuresIt() {
        // The figures plan 3 Task 7's review round 2 shaped with hb-shape in these files.
        val bold = reach(manrope("manrope_extrabold.ttf"), listOf("Magnetic interference", "à"))
        assertEquals(0.81, bold.above, 0.005)
        assertEquals('à', bold.highest)
        assertEquals(0.255, bold.below, 0.005)
        assertEquals('g', bold.lowest)
    }

    @Test
    fun aCaptionsTwoLinesStandFurtherApartThanItsGlyphsReach() {
        // A title's two lines, and the two-line tilt notice's, stand TITLE_PITCH apart at their size
        // (QiblaPage's lineHeight, in ems of the size the text is drawn at). However the text breaks,
        // the lowest glyph of a first line and the highest of a second stay apart.
        val reach = reach(manrope("manrope_extrabold.ttf"), phrases(captionKeys) + tiltNotices() + figures)
        for (line in listOf(QiblaType.title, QiblaType.level)) {
            val pitch = QiblaType.TITLE_PITCH / line.units
            assertTrue(
                reach.above + reach.below <= pitch,
                "two lines ${QiblaType.TITLE_PITCH} apart at ${line.units} (${"%.3f".format(pitch)} of the size) against the ink of " +
                    "'${reach.highest}' ${"%.3f".format(reach.above)} above and '${reach.lowest}' ${"%.3f".format(reach.below)} below the baseline",
            )
        }
    }

    private companion object {
        /** The size the fonts are measured at: 1,000 to the em. */
        const val EM = 1000f
    }
}
