package world.taqwa.app.feature.quran

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure rules a printed page is laid out by (spec §2.4): one size per page, words spread
 * across the line, taps resolved by span, highlight fields per run. All in pixels, none of it
 * needing a font.
 */
class MushafPageViewTest {

    private val accent = Color(0xFFB5820B)

    // --- fittedSize ---

    @Test
    fun aPageWhoseWidestLineFitsKeepsTheBaseSize() {
        assertEquals(28f, fittedSize(base = 28f, widestAtBase = 900f, frameWidth = 1000f))
        assertEquals(28f, fittedSize(base = 28f, widestAtBase = 1000f, frameWidth = 1000f))
    }

    @Test
    fun aPageWhoseWidestLineOverflowsShrinksToTheHalfStepThatFits() {
        // Exact fit would be 28 * 1000 / 1150 = 24.35; the half-step at or below it is 24.0.
        assertEquals(24f, fittedSize(base = 28f, widestAtBase = 1150f, frameWidth = 1000f))
        // 28 * 1000 / 1100 = 25.45 -> 25.0; and the fitted size never exceeds the base.
        assertEquals(25f, fittedSize(base = 28f, widestAtBase = 1100f, frameWidth = 1000f))
        assertTrue(fittedSize(28f, 1001f, 1000f) < 28f)
    }

    @Test
    fun aPageWithNoTextLinesKeepsTheBaseSize() {
        assertEquals(28f, fittedSize(base = 28f, widestAtBase = 0f, frameWidth = 1000f))
    }

    // --- placeWords ---

    @Test
    fun wordsRunRightToLeftFromTheLineEdge() {
        val placed = placeWords(widths = listOf(100f, 50f, 80f), lineWidth = 500f, naturalGap = 10f, justify = false)
        assertEquals(PlacedWord(400f, 500f), placed[0])
        assertEquals(PlacedWord(340f, 390f), placed[1])
        assertEquals(PlacedWord(250f, 330f), placed[2])
    }

    @Test
    fun aJustifiedLineSpreadsItsSlackEvenlyAndEndsOnTheLeftEdge() {
        val placed = placeWords(widths = listOf(100f, 50f, 80f), lineWidth = 500f, naturalGap = 10f, justify = true)
        // 500 - 230 = 270 of slack over two gaps: 135 each.
        assertEquals(PlacedWord(400f, 500f), placed[0])
        assertEquals(PlacedWord(215f, 265f), placed[1])
        assertEquals(PlacedWord(0f, 80f), placed[2])
    }

    @Test
    fun aSingleWordLineSitsAtTheRightEdgeWhetherJustifiedOrNot() {
        assertEquals(listOf(PlacedWord(400f, 500f)), placeWords(listOf(100f), 500f, 10f, justify = true))
        assertEquals(listOf(PlacedWord(400f, 500f)), placeWords(listOf(100f), 500f, 10f, justify = false))
    }

    @Test
    fun aJustifiedLineThatIsAlreadyTooWideNeverOverlapsItsWords() {
        // Only sub-pixel rounding can produce this; the words then touch rather than overlap.
        val placed = placeWords(widths = listOf(300f, 300f), lineWidth = 500f, naturalGap = 10f, justify = true)
        assertEquals(PlacedWord(200f, 500f), placed[0])
        assertEquals(PlacedWord(-100f, 200f), placed[1])
    }

    @Test
    fun noWordsPlaceNothing() {
        assertEquals(emptyList(), placeWords(emptyList(), 500f, 10f, justify = true))
    }

    // --- wordAtX ---

    @Test
    fun aTapResolvesToTheWordUnderItAndTheGapBelongsToTheWordBeforeIt() {
        val placed = placeWords(widths = listOf(100f, 50f, 80f), lineWidth = 500f, naturalGap = 10f, justify = false)
        assertEquals(0, wordAtX(placed, 450f, 500f))
        assertEquals(1, wordAtX(placed, 360f, 500f))
        assertEquals(2, wordAtX(placed, 300f, 500f))
        // In the gap after the first word (to its left, on a right-to-left line).
        assertEquals(0, wordAtX(placed, 395f, 500f))
        // In the slack an unjustified line leaves at its left end: nobody's.
        assertNull(wordAtX(placed, 100f, 500f))
    }

    @Test
    fun onAJustifiedLineEveryPointBelongsToSomeWord() {
        val placed = placeWords(widths = listOf(100f, 50f, 80f), lineWidth = 500f, naturalGap = 10f, justify = true)
        listOf(0f, 40f, 100f, 240f, 300f, 499f).forEach { x ->
            assertTrue(wordAtX(placed, x, 500f) != null, "x=$x should hit a word")
        }
    }

    // --- markedRuns ---

    @Test
    fun consecutiveMarkedWordsFormOneRunNotOnePerWord() {
        val marks = listOf(false, true, true, true, false, true)
        assertEquals(listOf(1..3, 5..5), markedRuns(marks.size) { marks[it] })
        assertEquals(emptyList(), markedRuns(3) { false })
        assertEquals(listOf(0..2), markedRuns(3) { true })
    }

    // --- annotateWord / digitsStart ---

    @Test
    fun onlyAWordsTrailingDigitsTakeTheAccent() {
        val word = MUSHAF_PAGE_2.lines[2].words.first().text // "الٓمٓ" + separator + "١"
        val digits = digitsStart(word)
        assertEquals(word.length - 1, digits)
        val annotated = annotateWord(word, accent)
        assertEquals(word, annotated.text)
        val spans = annotated.spanStyles.filter { it.item.color == accent }
        assertEquals(1, spans.size)
        assertEquals(digits, spans.first().start)
        assertEquals(word.length, spans.first().end)
    }

    @Test
    fun aWordWithoutDigitsIsLeftAlone() {
        val word = MUSHAF_PAGE_2.lines[2].words[1].text
        assertEquals(-1, digitsStart(word))
        assertTrue(annotateWord(word, accent).spanStyles.isEmpty())
    }

    // --- isJustified ---

    @Test
    fun onlyOrdinaryLinesBeyondAlFaatihasPagesAreJustified() {
        // Pages 1 and 2 keep the printed Mushaf's own centred, unjustified setting.
        MUSHAF_PAGE_1.lines.forEach { assertFalse(isJustified(MUSHAF_PAGE_1, it)) }
        MUSHAF_PAGE_2.lines.forEach { assertFalse(isJustified(MUSHAF_PAGE_2, it)) }

        val ordinary = MUSHAF_PAGE_3.lines.first { it.type == world.taqwa.app.quran.LineType.TEXT && !it.endsSurah }
        assertTrue(isJustified(MUSHAF_PAGE_3, ordinary))
    }
}
