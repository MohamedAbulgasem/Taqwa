package world.taqwa.app.wear.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.tasbeeh.TapEvent
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.tasbeeh.TasbeehState
import world.taqwa.app.watch.WatchPhrase
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.app.Feel
import world.taqwa.app.wear.app.Fitted
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.NASKH_DESCENT
import world.taqwa.app.wear.app.NASKH_INK_ACROSS
import world.taqwa.app.wear.app.NASKH_KASRA_OVER_ALIF
import world.taqwa.app.wear.app.NASKH_LINE_HEIGHT
import world.taqwa.app.wear.app.ROBOTO_DESCENT
import world.taqwa.app.wear.app.ROBOTO_LINE_HEIGHT
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.fakeFont
import world.taqwa.app.wear.app.farEdge
import world.taqwa.app.wear.app.fitAbove
import world.taqwa.app.wear.app.lineSpacing
import world.taqwa.app.wear.app.pixelFont

/**
 * The watch's counter (plan 3 Task 6): the phone's view model's rules restated (a set's end held
 * for a second, then the next round; one write 300 ms after the last change; a flush that settles
 * the hold first), the bezel's turn back, the counts kept per phrase on the watch, and the watch's
 * own haptic rule (call 14): one haptic for each count, undo or turn, the strongest it crossed, and
 * no tick while a part's or a set's buzz still plays, since a new vibration cuts the one playing.
 *
 * Each test runs on virtual time: the counter's scope is the test's, and its clock the scheduler's.
 */
class WatchTasbeehTest {

    /** The watch's memory, in memory: what is stored, and every save in order. */
    private class FakeMemory(selected: String? = null, counts: Map<String, String> = emptyMap()) : TasbeehMemory {
        var chosen: String? = selected
        val stored = counts.toMutableMap()
        val saves = mutableListOf<Pair<String, String>>()
        override fun selected(): String? = chosen
        override fun select(id: String) {
            chosen = id
        }
        override fun state(id: String): String? = stored[id]
        override fun save(id: String, raw: String) {
            stored[id] = raw
            saves += id to raw
        }
    }

    /** What the wrist felt, in order. */
    private class FakeFeel : Feel {
        val felt = mutableListOf<String>()
        override fun count() {
            felt += "count"
        }
        override fun partComplete() {
            felt += "part"
        }
        override fun setComplete() {
            felt += "set"
        }
        override fun aligned() {
            felt += "aligned"
        }
    }

    private fun TestScope.counter(memory: TasbeehMemory = FakeMemory(), feel: Feel = FakeFeel()) =
        WatchTasbeeh(memory, feel, backgroundScope, clock = { testScheduler.currentTime })

    private fun WatchTasbeeh.countAndRound() = ui.value.state.let { it.count to it.round }

    private fun from(count: Int, round: Int = 1, id: String = TasbeehPresets.DEFAULT_ID) =
        FakeMemory(selected = id, counts = mapOf(id to "$count,$round"))

    @Test
    fun thirtyThreeCountsBuzzThePartOnce() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(feel = feel)
        repeat(33) {
            tasbeeh.count()
            advanceTimeBy(200)
        }
        assertEquals(List(32) { "count" } + "part", feel.felt)
        assertEquals(33 to 1, tasbeeh.countAndRound())
        assertEquals(1, tasbeeh.ui.value.currentPart, "the next part shows from its first count")
    }

    @Test
    fun aHundredBuzzesTheSetThenRollsOverAfterASecond() = runTest {
        val feel = FakeFeel()
        val memory = from(99)
        val tasbeeh = counter(memory, feel)
        tasbeeh.count()
        assertEquals(100 to 1, tasbeeh.countAndRound())
        assertEquals(listOf("set"), feel.felt)
        assertEquals(TapEvent.SetComplete, tasbeeh.ui.value.flash)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(100 to 1, tasbeeh.countAndRound(), "still held at 999 ms")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(0 to 2, tasbeeh.countAndRound(), "rolled over at 1,000 ms")
        assertNull(tasbeeh.ui.value.flash)
        assertEquals("0,2", memory.stored["after_prayer"], "the rollover is written at once")
        assertEquals(listOf("set"), feel.felt, "the rollover is silent")
    }

    @Test
    fun aTurnBackInsideTheHoldStaysInTheRound() = runTest {
        val tasbeeh = counter(from(99))
        tasbeeh.count()
        advanceTimeBy(500)
        tasbeeh.undo()
        assertEquals(99 to 1, tasbeeh.countAndRound())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(99 to 1, tasbeeh.countAndRound(), "no rollover after the turn back")
    }

    @Test
    fun aCountInsideTheHoldOpensTheNextRoundAtOne() = runTest {
        val tasbeeh = counter(from(99))
        tasbeeh.count()
        advanceTimeBy(300)
        tasbeeh.count()
        assertEquals(1 to 2, tasbeeh.countAndRound())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1 to 2, tasbeeh.countAndRound(), "the hold does not land on the new round")
    }

    @Test
    fun aFastTurnAcrossAPartsEndBuzzesItAlone() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(31), feel)
        tasbeeh.turn(3)
        assertEquals(34 to 1, tasbeeh.countAndRound())
        assertEquals(listOf("part"), feel.felt)
        assertEquals(TapEvent.PartComplete(0), tasbeeh.ui.value.flash)

        val back = FakeFeel()
        val down = counter(from(1), back)
        down.turn(-3)
        assertEquals(0 to 1, down.countAndRound())
        assertEquals(listOf("count"), back.felt)
    }

    @Test
    fun aTurnAcrossAPartsEndAndTheSetsBuzzesTheSet() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(60), feel)
        tasbeeh.turn(40)
        assertEquals(100 to 1, tasbeeh.countAndRound())
        assertEquals(listOf("set"), feel.felt, "the strongest end crossed, not the first")
        assertEquals(TapEvent.SetComplete, tasbeeh.ui.value.flash)
    }

    @Test
    fun aTurnAcrossTheSetsEndCountsOnIntoTheNextRound() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(98), feel)
        tasbeeh.turn(4)
        assertEquals(2 to 2, tasbeeh.countAndRound(), "every detent is counted, through the set's end")
        assertEquals(listOf("set"), feel.felt)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2 to 2, tasbeeh.countAndRound(), "no hold: the set did not stand at its end")
    }

    @Test
    fun noTickCutsThePartsBuzz() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(31), feel)
        tasbeeh.turn(3)
        assertEquals(listOf("part"), feel.felt)
        advanceTimeBy(100)
        tasbeeh.turn(1)
        assertEquals(35, tasbeeh.ui.value.state.count)
        assertEquals(listOf("part"), feel.felt, "no tick at 100 ms")
        advanceTimeBy(40)
        tasbeeh.turn(1)
        assertEquals(36, tasbeeh.ui.value.state.count)
        assertEquals(listOf("part", "count"), feel.felt, "the tick comes back at 140 ms")
    }

    @Test
    fun noTickCutsTheSetsBuzz() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(98), feel)
        tasbeeh.turn(2)
        assertEquals(100 to 1, tasbeeh.countAndRound())
        assertEquals(listOf("set"), feel.felt)
        advanceTimeBy(399)
        tasbeeh.undo()
        assertEquals(99, tasbeeh.ui.value.state.count)
        assertEquals(listOf("set"), feel.felt, "no tick at 399 ms")
        advanceTimeBy(1)
        tasbeeh.undo()
        assertEquals(98, tasbeeh.ui.value.state.count)
        assertEquals(listOf("set", "count"), feel.felt, "the tick comes back at 400 ms")
    }

    @Test
    fun aPartsBuzzIsAlwaysSent() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(98), feel)
        tasbeeh.turn(2)
        advanceTimeBy(100)
        tasbeeh.turn(33)
        assertEquals(33 to 2, tasbeeh.countAndRound())
        assertEquals(listOf("set", "part"), feel.felt, "a part's end inside the set's window still buzzes")
    }

    @Test
    fun undoAtZeroIsSilent() = runTest {
        val feel = FakeFeel()
        val memory = FakeMemory()
        val tasbeeh = counter(memory, feel)
        tasbeeh.undo()
        tasbeeh.turn(-5)
        advanceUntilIdle()
        assertEquals(0 to 1, tasbeeh.countAndRound())
        assertEquals(emptyList(), feel.felt)
        assertEquals(emptyList(), memory.saves, "nothing changed, so nothing is written")
    }

    @Test
    fun undoNeverReopensTheRoundBefore() = runTest {
        val feel = FakeFeel()
        val tasbeeh = counter(from(0, round = 3), feel)
        tasbeeh.undo()
        assertEquals(0 to 3, tasbeeh.countAndRound())
        assertEquals(emptyList(), feel.felt)
    }

    @Test
    fun aLongPhraseOfHisOwnMayTakeTwoLines() {
        val own = TasbeehType.dhikr(transliterated = false)
        assertEquals(2, own.maxLines)
        assertEquals(11f..16.5f, own.range(1.0f))
        assertEquals(1.55f, own.maxLineHeight, "its lines at most 1.55 of its size apart")
        val builtIn = TasbeehType.dhikr(transliterated = true)
        assertEquals(1, builtIn.maxLines)
        assertEquals(16.5f..16.5f, builtIn.range(1.0f))
        assertNull(builtIn.maxLineHeight)
    }

    @Test
    fun aDhikrOfTwoLinesEndsAboveTheRound() {
        // His "سبحان الله وبحمده، سبحان الله العظيم" in the watch's Naskh: about 230 units of text
        // at 16.5 against a 152-unit line at y 56, so two lines at every size from 11 up. Drawn at
        // 16.5 from y 56, its second line ran into "ROUND 1" at y 92 (review round 1).
        val line = TasbeehType.dhikr(transliterated = false)
        val his = fakeFont(advance = 230f, width = 152f, lineHeight = naskh(line), descent = NASKH_DESCENT)
        for (fontScale in listOf(0.85f, 1f, 1.15f, 1.3f, 2f)) {
            val bottom = assertNotNull(TasbeehType.dhikrBottom(transliterated = false, fontScale))
            val roundTop = TasbeehType.round.top(TasbeehType.ROUND_Y, fontScale)
            assertTrue(bottom < roundTop, "at $fontScale its room ends above ROUND's letters")
            val fitted = fitAbove(line.range(fontScale), 0.5f, line.maxLines, room = bottom - TasbeehType.DHIKR_Y, measure = his)
            assertEquals(2, fitted.lines, "at $fontScale it keeps both lines")
            assertTrue(fitted.size >= line.floor, "at $fontScale it shrinks no smaller than its floor")
            val ends = TasbeehType.DHIKR_Y + his(fitted.size, fitted.lines).below
            assertTrue(ends <= bottom, "at $fontScale its box ends at $ends, above ROUND's letters at $roundTop")
        }
        // What the watch draws it at: 12 units at the default size, 11 at 1.3, where ROUND is 13.
        assertEquals(Fitted(12f, 2), fitAbove(line.range(1f), 0.5f, 2, TasbeehType.dhikrBottom(false, 1f)!! - TasbeehType.DHIKR_Y, his))
        assertEquals(Fitted(11f, 2), fitAbove(line.range(1.3f), 0.5f, 2, TasbeehType.dhikrBottom(false, 1.3f)!! - TasbeehType.DHIKR_Y, his))
        // With the font's own 1.70 between its lines, two lines at 11 would not fit above ROUND at
        // 1.3, and his phrase would lose its second line to an ellipsis.
        val ownSpacing = fakeFont(advance = 230f, width = 152f, lineHeight = NASKH_LINE_HEIGHT, descent = NASKH_DESCENT)
        assertEquals(Fitted(11f, 1), fitAbove(line.range(1.3f), 0.5f, 2, TasbeehType.dhikrBottom(false, 1.3f)!! - TasbeehType.DHIKR_Y, ownSpacing))
    }

    @Test
    fun aVowelledPhrasesMarksStayApartAcrossItsTwoLines() {
        // Lines 1.2 of the size apart drew review round 2's kasra under ٱللَّٰهِ and the dagger alif
        // above the next line's ٱللَّٰهِ as one mark. At 1.55 the most ink any of 3,888 vowelled
        // phrases brings across its break leaves a tenth of the size between its two lines.
        val apart = naskh(TasbeehType.dhikr(transliterated = false))
        assertTrue(1.2f < NASKH_KASRA_OVER_ALIF, "round 1's spacing brought them together")
        assertTrue(apart - NASKH_INK_ACROSS >= 0.1f, "${apart - NASKH_INK_ACROSS} of its size between the two lines' ink")
        assertTrue(apart < NASKH_LINE_HEIGHT, "nearer than the font's own, which leaves no room for two lines at 1.3")
    }

    @Test
    fun twoLinesAtItsFloorFitAboveTheRoundOnEveryScreen() {
        // Android rounds each line's spacing and the font's descent up to whole pixels, so the box
        // reaches a little lower than its sizes say. On every round screen from 384 to 480 pixels
        // across, his phrase keeps its two lines at every font size, at 11 at the largest; and one
        // too long for two lines even at 11 (60 capitals, 60 emoji) keeps two, cut at the end.
        val line = TasbeehType.dhikr(transliterated = false)
        val apart = naskh(line)
        for (across in listOf(384, 396, 432, 450, 454, 456, 466, 480)) {
            val unitPx = across / WatchUnits.ACROSS
            val his = pixelFont(advance = 230f, width = 152f, lineHeight = apart, descent = NASKH_DESCENT, unitPx = unitPx)
            val wide = pixelFont(advance = 600f, width = 152f, lineHeight = apart, descent = NASKH_DESCENT, unitPx = unitPx)
            for (fontScale in listOf(1f, 1.15f, 1.3f, 2f)) {
                val room = assertNotNull(TasbeehType.dhikrBottom(transliterated = false, fontScale)) - TasbeehType.DHIKR_Y
                val fitted = fitAbove(line.range(fontScale), 0.5f, line.maxLines, room, his)
                assertEquals(2, fitted.lines, "$across px at $fontScale")
                assertTrue(his(fitted.size, 2).below <= room, "$across px at $fontScale ends above ROUND")
                assertEquals(Fitted(11f, 2), fitAbove(line.range(fontScale), 0.5f, line.maxLines, room, wide), "$across px at $fontScale, cut")
            }
            assertEquals(11f, fitAbove(line.range(1.3f), 0.5f, 2, TasbeehType.dhikrBottom(false, 1.3f)!! - TasbeehType.DHIKR_Y, his).size, "$across px")
        }
    }

    @Test
    fun hisPhraseInLatinScriptKeepsItsFontsOwnSpacing() {
        // His "Astaghfirullah wa atubu ilayh" in Roboto, about 200 units at 16.5 against a 152-unit
        // line: two lines at the drawing's size, 1.17 apart as Roboto sets them, its box 23.4 units
        // below the first baseline, in its room of 27. Held 1.55 apart, it would shrink to 15.
        val line = TasbeehType.dhikr(transliterated = false)
        assertNull(lineSpacing(ROBOTO_LINE_HEIGHT, line.maxLineHeight), "Roboto's own")
        val his = fakeFont(advance = 200f, width = 152f, lineHeight = ROBOTO_LINE_HEIGHT, descent = ROBOTO_DESCENT)
        assertEquals(Fitted(16.5f, 2), fitAbove(line.range(1f), 0.5f, 2, TasbeehType.dhikrBottom(false, 1f)!! - TasbeehType.DHIKR_Y, his))
        val held = fakeFont(advance = 200f, width = 152f, lineHeight = 1.55f, descent = ROBOTO_DESCENT)
        assertEquals(Fitted(15f, 2), fitAbove(line.range(1f), 0.5f, 2, TasbeehType.dhikrBottom(false, 1f)!! - TasbeehType.DHIKR_Y, held))
    }

    /** How far apart the dhikr's lines stand in the watch's Naskh: its own, held to the line's most. */
    private fun naskh(line: Line): Float = lineSpacing(NASKH_LINE_HEIGHT, line.maxLineHeight) ?: NASKH_LINE_HEIGHT

    @Test
    fun aDhikrOfOneLineKeepsTheDrawingsSize() {
        // "سُبْحَانَ ٱللَّٰهِ" under an Arabic phone, or a short phrase of his: one line, which the
        // room above ROUND never shrinks, at StepBased's top step (16.5, then 18.5 from 1.15).
        val line = TasbeehType.dhikr(transliterated = false)
        val short = fakeFont(advance = 72f, width = 152f, lineHeight = naskh(line), descent = NASKH_DESCENT)
        for ((fontScale, size) in listOf(0.85f to 16.5f, 1f to 16.5f, 1.15f to 18.5f, 1.3f to 18.5f, 2f to 18.5f)) {
            val bottom = assertNotNull(TasbeehType.dhikrBottom(transliterated = false, fontScale))
            assertEquals(Fitted(size, 2), fitAbove(line.range(fontScale), 0.5f, line.maxLines, bottom - TasbeehType.DHIKR_Y, short), "at $fontScale")
        }
        assertNull(TasbeehType.dhikrBottom(transliterated = true, 1f), "a built-in above its transliteration is drawn as the drawing draws it")
        assertNull(TasbeehType.dhikrBottom(transliterated = true, 1.3f))
    }

    @Test
    fun theCountersLinesSitWhereTheDrawingSetsThem() {
        assertEquals(listOf(56f, 72f, 92f, 134f, 151f, 168f, 184f), with(TasbeehType) { listOf(DHIKR_Y, TRANSLITERATION_Y, ROUND_Y, COUNT_Y, OF_Y, DOTS_Y, HINT_Y) })
    }

    @Test
    fun atTheLargestFontTheCountKeepsItsSize() {
        assertEquals(46f..46f, TasbeehType.count.range(1.3f))
        val of = TasbeehType.of.range(1.3f)
        assertEquals(11f, of.start)
        assertEquals(14.3f, of.endInclusive, 1e-4f)
    }

    @Test
    fun theTypeTableIsThePlans() {
        assertEquals(Line(10f, 1.3f), TasbeehType.transliteration)
        assertEquals(Line(10f, 1.3f), TasbeehType.round)
        assertEquals(Line(46f, 1f), TasbeehType.count)
        assertEquals(Line(11f, 1.3f), TasbeehType.of)
        assertEquals(Line(10f, 1f), TasbeehType.hint)
        assertEquals(Line(12.5f, 1.3f), TasbeehType.title)
        assertEquals(Line(11f, 1.3f), TasbeehType.rowName)
        assertEquals(Line(10f, 1.3f), TasbeehType.rowTarget)
        assertEquals(25f, TasbeehType.rowHeight(11f))
        assertEquals(25f * 14.3f / 11f, TasbeehType.rowHeight(14.3f), 1e-4f)
    }

    @Test
    fun theCounterIsWrittenInThePhonesWordsAndDigits() {
        val afterPrayer = TasbeehPresets.default
        val at27 = counterLines(TestFeeds.sample, TasbeehUi.of(afterPrayer, TasbeehState(afterPrayer.id, 27, 1)))
        assertEquals("سُبْحَانَ ٱللَّٰهِ", at27.dhikr)
        assertEquals(TasbeehType.dhikr(transliterated = true), at27.dhikrLine)
        assertEquals("SubhanAllah", at27.transliteration)
        assertEquals("ROUND 1", at27.round)
        assertEquals("27", at27.count)
        assertEquals("of 100", at27.of)
        assertNull(at27.hint, "the hint goes with the first count")

        val arSa = TestFeeds.other("ar-sa")
        val sa = counterLines(arSa, TasbeehUi.of(afterPrayer, TasbeehState(afterPrayer.id, 27, 3)))
        assertEquals("سُبْحَانَ ٱللَّٰهِ", sa.dhikr)
        assertNull(sa.transliteration, "transliterations show only under a Latin-script interface")
        assertEquals(TasbeehType.dhikr(transliterated = false), sa.dhikrLine)
        assertEquals("٢٧", sa.count)
        assertEquals("من ١٠٠", sa.of)
        assertEquals("الجولة ٣", sa.round, "Arabic has no capitals")
    }

    @Test
    fun theHintShowsOnlyAtTheVeryStart() {
        val afterPrayer = TasbeehPresets.default
        assertEquals("Turn the bezel or tap", counterLines(TestFeeds.sample, TasbeehUi.of(afterPrayer, TasbeehState(afterPrayer.id, 0, 1))).hint)
        assertNull(counterLines(TestFeeds.sample, TasbeehUi.of(afterPrayer, TasbeehState(afterPrayer.id, 0, 2))).hint, "not as a later round opens")
        assertEquals("أدِر الإطار أو اضغط", counterLines(TestFeeds.other("ar-sa"), TasbeehUi.of(afterPrayer, TasbeehState(afterPrayer.id, 0, 1))).hint)
    }

    @Test
    fun theHintHasTheRoomTheDrawingGivesIt() {
        // gTasbeeh sets "Turn the bezel or tap" across the ring, out to the screen's own edge: 96.9
        // units wide at 10 (ref.py's drawing, 116 to 336 px at 2.27 px a unit). The frame's usual
        // 6-unit inset would leave it 88.4 at its descent, and cut it with an ellipsis.
        val size = TasbeehType.hint.range(1.0f).endInclusive
        val edge = farEdge(184f, centred = false, line = TasbeehType.hint, size = size)
        assertTrue(WatchUnits.chordUnits(edge, round = true, inset = TasbeehType.HINT_INSET) >= 96.9f)
        assertTrue(WatchUnits.chordUnits(edge, round = true) < 96.9f, "the default inset is what cut it")
    }

    @Test
    fun theRoundIsCasedInThePhonesLanguage() {
        // Turkish capitals dot their i, as Today's label and the face's do.
        val turkish = TestFeeds.sample.copy(
            ui = TestFeeds.sample.ui.copy(language = "tr", screens = TestFeeds.sample.ui.screens.copy(round = "%1\$s. tur, iyi")),
        )
        val afterPrayer = TasbeehPresets.default
        assertEquals("2. TUR, İYİ", counterLines(turkish, TasbeehUi.of(afterPrayer, TasbeehState(afterPrayer.id, 5, 2))).round)
    }

    @Test
    fun aPhraseOfHisOwnIsWrittenAsTyped() {
        val phrase = "سبحان الله وبحمده، سبحان الله العظيم"
        val own = TasbeehPresets.custom("custom_1730000000000", phrase, 100)
        val lines = counterLines(TestFeeds.sample, TasbeehUi.of(own, TasbeehState(own.id, 12, 1)))
        assertEquals(phrase, lines.dhikr)
        assertNull(lines.transliteration)
        assertEquals(2, lines.dhikrLine.maxLines, "his phrase may take the transliteration's line")
        assertEquals("of 100", lines.of)
    }

    @Test
    fun aBurstIsWrittenOnce() = runTest {
        val memory = FakeMemory()
        val tasbeeh = counter(memory)
        repeat(10) {
            tasbeeh.count()
            advanceTimeBy(10)
        }
        // The last count was at 90 ms: its write is due at 390.
        advanceTimeBy(289)
        runCurrent()
        assertEquals(emptyList(), memory.saves, "not yet at 389 ms")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("after_prayer" to "10,1"), memory.saves)
        advanceUntilIdle()
        assertEquals(1, memory.saves.size)
    }

    @Test
    fun flushSettlesTheHoldAndWritesTheNextRound() = runTest {
        val memory = from(99)
        val tasbeeh = counter(memory)
        tasbeeh.count()
        advanceTimeBy(500)
        tasbeeh.flush()
        assertEquals("0,2", memory.stored["after_prayer"])
        assertEquals(0 to 2, tasbeeh.countAndRound())
    }

    @Test
    fun aFlushedCountOutlivesItsScope() = runTest {
        // The pager composes only the page in front, and the counter's last 300 ms die with any
        // scope that ends first: so the activity holds the counter, and flushes it whenever its
        // page stops being the active one (erratum p3 #8). The flush writes at once.
        val memory = FakeMemory()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val tasbeeh = WatchTasbeeh(memory, FakeFeel(), scope, clock = { testScheduler.currentTime })
        repeat(5) { tasbeeh.count() }
        tasbeeh.flush()
        scope.cancel()
        advanceUntilIdle()
        assertEquals("5,1", memory.stored["after_prayer"])
    }

    @Test
    fun aCountIsReadBackWhereItWasLeft() = runTest {
        val tasbeeh = counter(from(27, round = 2))
        assertEquals(27 to 2, tasbeeh.countAndRound())
        assertEquals(0, tasbeeh.ui.value.currentPart)
        assertEquals(listOf(33f / 100, 66f / 100), tasbeeh.ui.value.ticks)
    }

    @Test
    fun aFinishedSetReadBackRollsOverAsAFreshOneDoes() = runTest {
        val memory = from(100)
        val tasbeeh = counter(memory)
        assertEquals(100 to 1, tasbeeh.countAndRound())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0 to 2, tasbeeh.countAndRound())
        assertEquals("0,2", memory.stored["after_prayer"])
    }

    @Test
    fun resetGoesBackToRoundOneAndIsWrittenAtOnce() = runTest {
        val memory = from(99, round = 4)
        val tasbeeh = counter(memory)
        tasbeeh.count()
        tasbeeh.reset()
        assertEquals(0 to 1, tasbeeh.countAndRound())
        assertEquals("0,1", memory.stored["after_prayer"])
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0 to 1, tasbeeh.countAndRound(), "the hold is dropped")
    }

    @Test
    fun eachPhraseKeepsItsOwnCount() = runTest {
        val memory = FakeMemory()
        val tasbeeh = counter(memory)
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample))
        repeat(5) { tasbeeh.count() }
        tasbeeh.select("subhanallah")
        assertEquals("subhanallah", tasbeeh.ui.value.preset.id)
        assertEquals(0 to 1, tasbeeh.countAndRound())
        repeat(3) { tasbeeh.count() }
        tasbeeh.select("custom_1730000000001")
        tasbeeh.count()
        tasbeeh.select("after_prayer")
        assertEquals(5 to 1, tasbeeh.countAndRound())
        tasbeeh.select("subhanallah")
        assertEquals(3 to 1, tasbeeh.countAndRound())
        tasbeeh.select("custom_1730000000001")
        assertEquals(1 to 1, tasbeeh.countAndRound())
        assertEquals("custom_1730000000001", memory.chosen)
        assertEquals(mapOf("after_prayer" to "5,1", "subhanallah" to "3,1", "custom_1730000000001" to "1,1"), memory.stored)
    }

    @Test
    fun theSelectionIsReadBackWithItsPhrase() = runTest {
        val memory = FakeMemory(selected = "custom_1730000000001", counts = mapOf("custom_1730000000001" to "12,1"))
        val tasbeeh = counter(memory)
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample))
        assertEquals("custom_1730000000001", tasbeeh.ui.value.preset.id)
        assertEquals(12 to 1, tasbeeh.countAndRound())
        assertEquals("custom_1730000000001", memory.chosen)
    }

    @Test
    fun aSetLeftInsideTheHoldIsKeptAsTheNextRound() = runTest {
        val memory = from(99)
        val tasbeeh = counter(memory)
        tasbeeh.count()
        advanceTimeBy(200)
        tasbeeh.select("subhanallah")
        assertEquals("0,2", memory.stored["after_prayer"])
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("subhanallah", tasbeeh.ui.value.preset.id)
        assertEquals(0 to 1, tasbeeh.countAndRound(), "the hold does not land on the phrase chosen")
    }

    @Test
    fun aPhraseRemovedOnThePhoneFallsBackToAfterPrayer() = runTest {
        val memory = FakeMemory()
        val tasbeeh = counter(memory)
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample))
        repeat(4) { tasbeeh.count() }
        tasbeeh.select("custom_1730000000001")
        repeat(2) { tasbeeh.count() }
        val kept = TestFeeds.sample.tasbeeh.filter { it.id != "custom_1730000000001" }
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample.copy(tasbeeh = kept)))
        assertEquals("after_prayer", tasbeeh.ui.value.preset.id)
        assertEquals(4 to 1, tasbeeh.countAndRound())
        assertEquals("after_prayer", memory.chosen)
        assertEquals("2,1", memory.stored["custom_1730000000001"], "its count is kept, not lost mid-debounce")
        tasbeeh.select("custom_1730000000001")
        assertEquals("after_prayer", tasbeeh.ui.value.preset.id, "a phrase that has gone cannot be chosen")
    }

    @Test
    fun aTargetLoweredOnThePhoneHoldsTheCountToIt() = runTest {
        val memory = FakeMemory(selected = "custom_1730000000001", counts = mapOf("custom_1730000000001" to "30,1"))
        val tasbeeh = counter(memory)
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample))
        assertEquals(30 to 1, tasbeeh.countAndRound())
        val lowered = TestFeeds.sample.tasbeeh.map { if (it.id == "custom_1730000000001") it.copy(target = 20) else it }
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample.copy(tasbeeh = lowered)))
        assertEquals(20, tasbeeh.ui.value.preset.total)
        assertEquals(20 to 1, tasbeeh.countAndRound(), "a finished set, shown whole")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0 to 2, tasbeeh.countAndRound(), "and rolled over as any finished set is")
    }

    @Test
    fun aPhraseRewordedOnThePhoneKeepsItsCount() = runTest {
        val memory = FakeMemory()
        val tasbeeh = counter(memory)
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample))
        tasbeeh.select("custom_1730000000001")
        repeat(7) { tasbeeh.count() }
        val reworded = TestFeeds.sample.tasbeeh.map { if (it.id == "custom_1730000000001") WatchPhrase(it.id, "Astaghfirullah", it.target) else it }
        tasbeeh.setPhrases(PhraseList.of(TestFeeds.sample.copy(tasbeeh = reworded)))
        assertEquals("Astaghfirullah", tasbeeh.ui.value.preset.parts.single().dhikr.arabic)
        assertEquals(7 to 1, tasbeeh.countAndRound())
    }
}
