package world.taqwa.app.wear.app

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How large a line is drawn at each font size (call 15): its drawing's size at the default, grown
 * with the wearer's size up to its cap, and never shrunk below the drawing's unless it has a floor.
 */
class WatchTypeTest {

    @Test
    fun atTheDefaultSizeEveryLineIsTheDrawings() {
        assertRange(10.5f, 10.5f, Line(10.5f, 1.3f).range(1.0f))
    }

    @Test
    fun aSmallerSizeNeverShrinksALine() {
        assertRange(10.5f, 10.5f, Line(10.5f, 1.3f).range(0.85f))
    }

    @Test
    fun aLargerSizeGrowsToTheCap() {
        assertRange(10.5f, 12.075f, Line(10.5f, 1.3f).range(1.15f))
        assertRange(10.5f, 13.65f, Line(10.5f, 1.3f).range(2.0f))
    }

    @Test
    fun aLineCappedAtOneNeverGrows() {
        assertRange(38f, 38f, Line(38f, 1f).range(2.0f))
    }

    @Test
    fun aFloorLetsANamedLineShrinkToFit() {
        assertRange(11f, 15f, FrameType.notice.range(1.0f))
        assertEquals(3, FrameType.notice.maxLines)
    }

    @Test
    fun theFramesLinesAreOneLineEach() {
        assertEquals(Line(12f, 1.3f), FrameType.title)
        assertEquals(Line(10.5f, 1.3f), FrameType.city)
        assertEquals(1, FrameType.title.maxLines)
        assertEquals(1, FrameType.city.maxLines)
    }

    @Test
    fun theDrawingsWeightsAreManropesFour() {
        // faces.js draws in 300, 600, 700 and 800; the watch carries Light, Regular, SemiBold and
        // ExtraBold, and 700 is drawn SemiBold, never ExtraBold (Global Constraints, "Drawing").
        assertEquals(FontWeight.Light, WatchFonts.weight(300))
        assertEquals(FontWeight.Normal, WatchFonts.weight(400))
        assertEquals(FontWeight.SemiBold, WatchFonts.weight(600))
        assertEquals(FontWeight.SemiBold, WatchFonts.weight(700))
        assertEquals(FontWeight.ExtraBold, WatchFonts.weight(800))
    }

    @Test
    fun arabicIsNeverTracked() {
        assertEquals(1.6f, trackingFor("ASR IN", 1.6f))
        assertEquals(0f, trackingFor("متبقٍّ على العصر", 1.6f))
        assertEquals(0f, trackingFor("ROUND ١", 1.3f), "any Arabic letter or digit in the line")
        assertEquals(0f, trackingFor("عصر میں باقی", 1.6f), "Urdu is Arabic script")
        assertEquals(1.3f, trackingFor("ROUND 1", 1.3f))
    }

    @Test
    fun aLinesGlyphsReachEightTenthsOfItsSizeAboveItsBaseline() {
        // "ROUND n" at y 92: 10 units at the default size, 13 at 1.3.
        assertEquals(84f, Line(10f, 1.3f).top(92f, 1.0f), 1e-4f)
        assertEquals(81.6f, Line(10f, 1.3f).top(92f, 1.3f), 1e-4f)
        assertEquals(81.6f, Line(10f, 1.3f).top(92f, 2.0f), 1e-4f, "no higher than its cap allows")
    }

    @Test
    fun withRoomToSpareABlockIsSizedOnStepBasedsSteps() {
        val roomy = { _: Float, _: Int -> Tried(cut = false, below = 0f) }
        assertEquals(Fitted(16.5f, 2), fitAbove(11f..16.5f, 0.5f, 2, room = 100f, measure = roomy))
        // StepBased counts its steps up from the floor, so at 1.15 the dhikr's 18.975 is drawn 18.5.
        assertEquals(Fitted(18.5f, 2), fitAbove(11f..18.975f, 0.5f, 2, room = 100f, measure = roomy))
        assertEquals(Fitted(16.5f, 1), fitAbove(16.5f..16.5f, 0.5f, 1, room = 100f, measure = roomy))
    }

    @Test
    fun aBlockTooWideForItsLinesShrinksUntilItFits() {
        // 290 units of text at 16.5 in 150-unit lines: two lines hold it whole.
        val wide = fakeFont(advance = 290f, width = 150f, lineHeight = 1.2f, descent = 0.25f)
        assertEquals(Fitted(16.5f, 2), fitAbove(11f..16.5f, 0.5f, 2, room = 100f, measure = wide))
        // 400 units need three lines above 12.375, so the largest step in two is 12.
        val wider = fakeFont(advance = 400f, width = 150f, lineHeight = 1.2f, descent = 0.25f)
        assertEquals(Fitted(12f, 2), fitAbove(11f..16.5f, 0.5f, 2, room = 100f, measure = wider))
    }

    @Test
    fun aBlockTooTallForItsRoomShrinksToEndAboveIt() {
        // Two lines of a script whose lines are 1.68 of its size apart and reach 0.52 below the
        // last baseline: 2.2 of its size below the first baseline, 36.3 units at 16.5.
        val tall = fakeFont(advance = 250f, width = 150f, lineHeight = 1.68f, descent = 0.52f)
        val fitted = fitAbove(11f..16.5f, 0.5f, 2, room = 27f, measure = tall)
        assertEquals(Fitted(12f, 2), fitted)
        assertTrue(tall(fitted.size, fitted.lines).below <= 27f)
        assertTrue(tall(fitted.size + 0.5f, fitted.lines).below > 27f, "the largest step that fits")
    }

    @Test
    fun atItsFloorABlockGivesUpItsLastLineBeforeItRunsBelowItsRoom() {
        val tall = fakeFont(advance = 250f, width = 150f, lineHeight = 1.68f, descent = 0.52f)
        // At 11 two lines reach 24.2 below the first baseline: with 20 there is room for one, cut.
        assertEquals(Fitted(11f, 1), fitAbove(11f..16.5f, 0.5f, 2, room = 20f, measure = tall))
        assertEquals(Fitted(11f, 1), fitAbove(11f..16.5f, 0.5f, 2, room = 1f, measure = tall), "one line at least")
        assertEquals(Fitted(11f, 2), fitAbove(11f..16.5f, 0.5f, 2, room = 24.2f, measure = tall))
    }

    @Test
    fun aBlockTooWideForItsLinesEvenAtItsFloorKeepsThemWhenTheyFitItsRoom() {
        // 600 units of text at 16.5 in 150-unit lines need three lines even at 11, so every size is
        // cut: 60 capitals, or 60 emoji. Its two lines at 11 reach 15.95 below the first baseline,
        // well within 27, so it is drawn in both, cut at the end of the second (review round 2).
        val wide = fakeFont(advance = 600f, width = 150f, lineHeight = 1.2f, descent = 0.25f)
        assertEquals(Fitted(11f, 2), fitAbove(11f..16.5f, 0.5f, 2, room = 27f, measure = wide))
        assertEquals(Fitted(11f, 2), fitAbove(11f..16.5f, 0.5f, 2, room = 15.95f, measure = wide))
        assertEquals(Fitted(11f, 1), fitAbove(11f..16.5f, 0.5f, 2, room = 15f, measure = wide), "no room for the second")
    }

    @Test
    fun atItsFloorABlockGivesUpOnlyTheLinesThatRunBelowItsRoom() {
        // 1,200 units in 150-unit lines need six lines at 11, so three are cut. Three lines at 11
        // reach 29.15 below the first baseline, two 15.95 and one 2.75.
        val wider = fakeFont(advance = 1200f, width = 150f, lineHeight = 1.2f, descent = 0.25f)
        assertEquals(Fitted(11f, 3), fitAbove(11f..16.5f, 0.5f, 3, room = 30f, measure = wider))
        assertEquals(Fitted(11f, 2), fitAbove(11f..16.5f, 0.5f, 3, room = 29f, measure = wider))
        assertEquals(Fitted(11f, 1), fitAbove(11f..16.5f, 0.5f, 3, room = 15f, measure = wider))
        assertEquals(Fitted(11f, 1), fitAbove(11f..16.5f, 0.5f, 3, room = 1f, measure = wider), "one line at least")
    }

    @Test
    fun aBlocksLinesStandAsItsFontSetsThemUpToTheirMost() {
        // The watch's Naskh sets its own lines 1.70 of its size apart: held to the most, 1.55.
        assertEquals(1.55f, lineSpacing(own = NASKH_LINE_HEIGHT, most = 1.55f))
        // Roboto sets its own 1.17 apart, nearer than the most: they stay its own.
        assertNull(lineSpacing(own = ROBOTO_LINE_HEIGHT, most = 1.55f))
        assertNull(lineSpacing(own = 1.55f, most = 1.55f), "a font already at the most keeps its own")
        assertNull(lineSpacing(own = NASKH_LINE_HEIGHT, most = null), "a line with no most keeps its font's own")
    }

    private fun assertRange(start: Float, end: Float, range: ClosedFloatingPointRange<Float>) {
        assertEquals(start, range.start, 1e-3f, "from")
        assertEquals(end, range.endInclusive, 1e-3f, "to")
    }
}
