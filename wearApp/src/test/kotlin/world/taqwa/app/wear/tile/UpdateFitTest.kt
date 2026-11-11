package world.taqwa.app.wear.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.farEdge

/**
 * "Update Taqwa on your watch" in the city's place (call 5): one line at the lowest baseline from
 * the city's 184 upwards at which it fits, never reaching above Isha's row; else two lines, the
 * second at 191 and the first its line's pitch above it; at the largest size from the city's down
 * to 8 units at which every line fits its own chord.
 */
class UpdateFitTest {

    /** The system font's widths, roughly: half an em and a twentieth for every character. */
    private val width = { text: String, units: Float -> text.length * units * 0.55f }

    /** The lines' heights, Roboto's or, for Arabic script, the watch's Naskh. */
    private val extent = { text: String, units: Float -> TestMeasures.extent(text, units) }

    private fun fit(text: String, scale: Float = 1f, width: (String, Float) -> Float = this.width) =
        fitUpdate(text, TileType.update.range(scale), round = true, width = width, extent = extent)

    /** The room at [baseline] for a line at [size], with [MEASURE_SLACK] to spare. */
    private fun room(baseline: Float, size: Float) =
        WatchUnits.chordUnits(farEdge(baseline, centred = false, line = TileType.update, size = size), round = true) - MEASURE_SLACK

    /**
     * Whether each line fits its chord at the baseline it is drawn at, with [MEASURE_SLACK] to spare
     * (on emulator-5558 a line whose measured width came 0.3 units inside its box was drawn cut,
     * "Update Taqwa on y…"), and reaches no higher than [UPDATE_TOP].
     */
    private fun fits(fit: UpdateFit, width: (String, Float) -> Float = this.width) = fit.lines.indices.all { i ->
        width(fit.lines[i], fit.size) <= room(fit.baselines[i], fit.size) + 1e-3f &&
            fit.baselines[i] - extent(fit.lines[i], fit.size).above >= UPDATE_TOP - 1e-3f
    }

    @Test
    fun aShortUpdateKeepsTheCitysLine() {
        val fit = fit("Update Taqwa")

        assertEquals(listOf("Update Taqwa"), fit.lines)
        assertEquals(10.5f, fit.size)
        assertEquals(listOf(184f), fit.baselines)
    }

    @Test
    fun theEnglishUpdateTakesTwoLines() {
        val fit = fit("Update Taqwa on your watch")

        assertEquals(listOf("Update Taqwa on", "your watch"), fit.lines)
        // "your watch" is 57.75 units at 10.5 in these widths, a unit short of the chord at 191
        // with the slack, so it is drawn at the next step down.
        assertEquals(10f, fit.size)
        assertEquals(191f, fit.baselines.last())
        assertEquals(191f - 1.2f * 10f, fit.baselines.first(), 1e-4f)
        assertTrue(fits(fit))
    }

    @Test
    fun aLineWiderThanTheCitysRoomRisesUntilItFitsWhole() {
        // Review round 1: a line too wide for the chord at 184 but narrower than the first line's
        // was checked against the first line's chord and drawn at the second line's baseline,
        // 191, where the room is 58 units: both Arabic samples drew "حدّث تقوى ع…". Every width
        // from just over the city's room to the highest line's is drawn whole on one line, at the
        // city's size, on the lowest baseline where it fits, and never into Isha's row.
        val text = "حدّث تقوى على ساعتك"
        val highest = UPDATE_TOP + extent(text, 10.5f).above
        var drawn = room(184f, 10.5f) + 0.5f
        while (drawn <= room(highest, 10.5f)) {
            val k = drawn / 10.5f
            val wide = { t: String, u: Float -> if (t == text) k * u else t.length * u * 0.55f }
            val fit = fit(text, width = wide)
            assertEquals(listOf(text), fit.lines, "$drawn units")
            assertEquals(10.5f, fit.size, "$drawn units")
            assertTrue(fit.baselines.single() < 184f && fits(fit, wide), "$drawn units at ${fit.baselines}")
            assertTrue(room(fit.baselines.single() + 0.5f, 10.5f) < drawn, "the lowest that fits: $drawn units at ${fit.baselines}")
            drawn += 0.5f
        }
    }

    @Test
    fun everyUpdateIsDrawnWholeOrAtTheFloor() {
        // Of every width from a word to three times the chord, in one to six words, at both font
        // sizes: each line fits its chord at the baseline it is drawn at, unless even the floor
        // cannot hold it.
        for (scale in listOf(1f, 1.1f)) {
            for (count in 1..6) {
                for (letters in 4..60) {
                    val text = List(count) { "w".repeat(letters / count + 1) }.joinToString(" ")
                    val fit = fit(text, scale)
                    assertTrue(fits(fit) || fit.size == TileType.update.floor, "$text at $scale: $fit")
                    assertEquals(text, fit.lines.joinToString(" "))
                }
            }
        }
    }

    @Test
    fun theArabicAndUrduUpdatesAreWhole() {
        // The two Arabic samples' update, in the Naskh's own widths (8.5 of the em, HarfBuzz on
        // the emulator's NotoNaskhArabic-Bold), is one line at the city's size; the phone's Urdu
        // (11.5 of the em) is too, a little smaller, rather than two lines of Naskh. The Arabic was
        // measured with the kasra under its shadda that M8 point A1 took out: a mark, of no
        // advance.
        val naskh = mapOf("حدّث تقوى على ساعتك" to 8.514f, "اپنی گھڑی پر تقویٰ اپڈیٹ کریں" to 11.457f)
        val measured = { t: String, u: Float -> (naskh[t] ?: (t.length * 0.55f)) * u }
        val arabic = fit("حدّث تقوى على ساعتك", width = measured)
        assertEquals(1, arabic.lines.size)
        assertEquals(10.5f, arabic.size)
        assertTrue(fits(arabic, measured))

        val urdu = fit("اپنی گھڑی پر تقویٰ اپڈیٹ کریں", width = measured)
        assertEquals(1, urdu.lines.size, "$urdu")
        assertTrue(urdu.size >= 9f && fits(urdu, measured), "$urdu")
    }

    @Test
    fun aLongerOneShrinksUntilItFits() {
        // The Indonesian line, 33 characters to the English 26.
        val indonesian = "Perbarui Taqwa di jam tangan Anda"
        val fit = fit(indonesian)

        assertEquals(indonesian, fit.lines.joinToString(" "))
        assertEquals(2, fit.lines.size)
        assertTrue(fit.size < 10.5f && fit.size >= 8f, "${fit.size}")
        assertTrue(fits(fit))
    }

    @Test
    fun twoLinesInArabicScriptStandAsTheirFontSetsThem() {
        // Two lines of Naskh 1.2 apart would run into each other: they stand 1.55 apart, the most
        // Tasbeeh's phrases take, and the first never rises into Isha's row.
        val text = "اپنی گھڑی پر تقویٰ اپڈیٹ کریں"
        val fit = fit(text, width = { t, u -> t.length * u * 0.6f })

        assertEquals(2, fit.lines.size, "$fit")
        assertEquals(UPDATE_MAX_PITCH * fit.size, fit.baselines[1] - fit.baselines[0], 1e-3f)
        assertTrue(fits(fit) || fit.size == 8f, "$fit")
    }

    @Test
    fun twoLinesNeverRiseIntoIshasRow() {
        // At a larger font size the city's line may grow, but two lines are held where they fit
        // between Isha's row (its glyphs end above 168) and 191.
        for (scale in listOf(1f, 1.1f)) {
            val fit = fit("Update Taqwa on your watch", scale)
            if (fit.lines.size == 2) {
                assertTrue(fit.baselines.first() - extent(fit.lines.first(), fit.size).above >= UPDATE_TOP - 1e-3f, "at $scale: ${fit.size}")
            }
        }
    }

    @Test
    fun whatNeverFitsIsCutAtTheFloor() {
        val fit = fit("Updateupdateupdateupdate Taqwaaaaaaaaaaaaaaaaaaaa on your watch, please, now")

        assertEquals(8f, fit.size)
        assertEquals(2, fit.lines.size)
        assertTrue(fit.lines.first().isNotEmpty())
    }
}
