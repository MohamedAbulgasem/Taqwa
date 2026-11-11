package world.taqwa.app.wear.qibla

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.CompassReading
import world.taqwa.app.qibla.DialState
import world.taqwa.app.qibla.Guidance
import world.taqwa.app.qibla.Turn
import world.taqwa.app.qibla.WatchQibla
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.words
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.complication.ComplicationFields

/**
 * What the Qibla page draws and says (plan 3 Task 7), a pure function of the dial's state, the
 * tilt and the feed. On the committed sample: Cape Town, whose Qibla the phone rounded to 23° and
 * whose distance it wrote "6,600" (Ruling 39: the city, the bearing to a whole degree, never a
 * coordinate).
 */
class QiblaModelTest {

    private val sample = TestFeeds.sample
    private val arSa = TestFeeds.other("ar-sa")
    private val km = sample.place.qiblaKm

    private fun screenOf(state: DialState, tilted: Boolean = false) = qiblaScreen(state, tilted, sample)

    /**
     * The turning dial's detail on [feed], [km] in the dial's short form as the feed carries it, and
     * the dimmed dial's with [bearing] (Ruling 66 item 1). The forms are `WatchWords`', which a
     * reviewer may change, so these tests hold the figures a form is filled with and never the form
     * itself (Ruling 67 item 2); `WatchWordsTest` holds the forms' shape, and `QiblaFitTest` their fit.
     */
    private fun onDial(feed: WatchFeed, km: String) = WatchRules.filled(feed.words().distanceOnDial, km)

    private fun onDial(feed: WatchFeed, bearing: String, km: String) = WatchRules.filled(feed.words().qiblaDetailOnDial, bearing, km)

    /** No needle, no marker, the dial dimmed and north up: every state that has no heading to show. */
    private fun assertNotPointing(screen: QiblaScreen) {
        assertFalse(screen.needle, "no needle")
        assertFalse(screen.marker, "no marker")
        assertTrue(screen.dimmed, "dimmed")
        assertFalse(screen.lit)
        assertEquals(0.0, screen.heading, "the dial at rest")
        assertEquals(emptyList(), screen.cardinals, "no letters: at rest they would name a north the watch does not know, as the phone's dimmed dial has none")
        assertNull(screen.spoken)
        assertNull(screen.dialSpoken)
        assertEquals(QiblaType.title, screen.captionLine)
        assertFalse(screen.captionAccent)
    }

    @Test
    fun searchingShowsTheBearingAndTheDistanceAndSaysTheTurn() {
        val screen = screenOf(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)))

        assertTrue(screen.needle)
        assertTrue(screen.marker)
        assertFalse(screen.dimmed)
        assertFalse(screen.lit)
        assertEquals(340.0, screen.heading)
        assertEquals(23.0, screen.bearing)
        assertEquals("23°", screen.caption)
        assertEquals(QiblaType.bearing, screen.captionLine)
        assertFalse(screen.captionAccent)
        // Ruling 66 item 1: the dial draws the watch's short form, and a screen reader hears the phone's phrase.
        assertEquals(onDial(sample, km), screen.detail)
        assertEquals("Makkah · $km km away", screen.detailSpoken)
        assertEquals("Turn right about 45 degrees", screen.spoken)
        assertEquals("Turn right about 45 degrees", screen.dialSpoken, "the dial says the turn")
        assertEquals(listOf("N", "E", "S", "W"), screen.cardinals)
    }

    @Test
    fun searchingToTheLeftSaysTheLeftTurn() {
        val screen = screenOf(DialState.Searching(60.0, Guidance(Turn.LEFT, 30)))

        assertEquals("Turn left about 30 degrees", screen.spoken)
        assertEquals(60.0, screen.heading)
    }

    @Test
    fun searchingWithNoTurnYetSaysNothing() {
        val screen = screenOf(DialState.Searching(340.0, guidance = null))

        assertTrue(screen.needle)
        assertNull(screen.spoken)
        assertNull(screen.dialSpoken)
        assertEquals("23°", screen.caption)
    }

    @Test
    fun aTiltedWristIsAskedToHoldItLevel() {
        val screen = screenOf(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)), tilted = true)

        // The heading is tilt-compensated, so the needle keeps pointing (open question 4).
        assertTrue(screen.needle)
        assertTrue(screen.marker)
        assertEquals("Hold your wrist level", screen.caption)
        assertEquals(QiblaType.level, screen.captionLine, "one line that may shrink, inside the dial's marks")
        assertEquals("Hold your wrist level", screen.spoken)
        assertNull(screen.dialSpoken, "the caption says it, as a live region: the dial does not say it twice")
        assertEquals(onDial(sample, km), screen.detail)
        assertEquals("Makkah · $km km away", screen.detailSpoken)
    }

    @Test
    fun facingTheQiblaLightsTheRimAndSaysSo() {
        val screen = screenOf(DialState.Aligned(24.0))

        assertTrue(screen.lit)
        assertTrue(screen.needle)
        assertTrue(screen.marker)
        assertFalse(screen.dimmed)
        assertEquals(24.0, screen.heading)
        assertEquals(listOf("N", "E", "S", "W"), screen.cardinals)
        assertEquals("Facing qibla", screen.caption)
        assertEquals(QiblaType.facing, screen.captionLine)
        assertTrue(screen.captionAccent)
        assertEquals("Facing qibla", screen.spoken)
        assertNull(screen.dialSpoken, "the caption says it")
        assertEquals(onDial(sample, km), screen.detail)
        assertEquals("Makkah · $km km away", screen.detailSpoken)
    }

    @Test
    fun facingTheQiblaTiltedStillSaysSo() {
        val screen = screenOf(DialState.Aligned(24.0), tilted = true)

        assertEquals("Facing qibla", screen.caption)
        assertTrue(screen.lit)
    }

    @Test
    fun aCompassThatNeedsCalibratingDimsTheDialAndDropsTheNeedle() {
        val screen = screenOf(DialState.Low(CompassLowReason.CALIBRATION))

        assertNotPointing(screen)
        assertEquals("Compass needs calibrating", screen.caption)
        assertEquals("", screen.detail, "the phone's low state shows no readout")
    }

    @Test
    fun interferenceSaysSo() {
        val screen = screenOf(DialState.Low(CompassLowReason.INTERFERENCE))

        assertNotPointing(screen)
        assertEquals("Magnetic interference", screen.caption)
    }

    @Test
    fun aLowCompassIsNotAskedToHoldLevel() {
        assertEquals("Magnetic interference", screenOf(DialState.Low(CompassLowReason.INTERFERENCE), tilted = true).caption)
    }

    @Test
    fun bestEffortShowsTheBearingAndTheDistanceWithNoNeedle() {
        val screen = screenOf(DialState.BestEffort(CompassLowReason.INTERFERENCE))

        assertNotPointing(screen)
        assertEquals("Compass unavailable", screen.caption)
        assertEquals(onDial(sample, "23", km), screen.detail)
    }

    @Test
    fun noSensorShowsTheBearingAndTheDistanceWithNoNeedle() {
        val screen = screenOf(DialState.NoSensor)

        assertNotPointing(screen)
        assertEquals("No compass sensor", screen.caption)
        assertEquals(onDial(sample, "23", km), screen.detail)
    }

    @Test
    fun aPointingDialsCaptionIsReadAsItsWordsAndTheDetailBesideIt() {
        // No description while the dial points and says turns: the caption is read, and heard as it
        // comes, as its own words (a description would be cleared by the next turn), and the detail
        // is an item of its own.
        val searching = screenOf(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)))
        assertNull(searching.captionDescription)
        assertTrue(searching.detailRead)

        val tilted = screenOf(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)), tilted = true)
        assertNull(tilted.captionDescription)
        assertTrue(tilted.detailRead)

        val facing = screenOf(DialState.Aligned(24.0))
        assertNull(facing.captionDescription)
        assertTrue(facing.detailRead)

        assertNull(screenOf(DialState.Searching(Double.NaN, guidance = null)).captionDescription)
    }

    @Test
    fun aDimmedDialsCaptionIsDescribedSoEveryChangeOfItIsSaid() {
        // TalkBack did not say a text whose words alone change (a content change of undefined type):
        // one low reason after the other, two lines in the same box, would be silent. A dimmed dial
        // says no turn, so its caption's description cannot be cleared by one.
        val calibration = screenOf(DialState.Low(CompassLowReason.CALIBRATION))
        assertEquals("Compass needs calibrating", calibration.captionDescription)
        assertFalse(calibration.detailRead, "no detail to read")
        assertEquals("Magnetic interference", screenOf(DialState.Low(CompassLowReason.INTERFERENCE)).captionDescription)
    }

    @Test
    fun aCompassThatHasGivenUpSaysSoFirstAndThenTheBearingAndTheDistance() {
        // Review round 1: TalkBack heard "Magnetic interference" and then nothing as the page gave
        // up. Its title and the bearing and distance that come with it are one announcement, the
        // title first, and the detail is not read a second time.
        val bestEffort = screenOf(DialState.BestEffort(CompassLowReason.INTERFERENCE))
        assertEquals("Compass unavailable, 23° · $km km to Makkah", bestEffort.captionDescription)
        assertFalse(bestEffort.detailRead)

        val noSensor = screenOf(DialState.NoSensor)
        assertEquals("No compass sensor, 23° · $km km to Makkah", noSensor.captionDescription)
        assertFalse(noSensor.detailRead)

        // A right-to-left phone's comma (ComplicationFields.spokenSeparator).
        val arabic = qiblaScreen(DialState.BestEffort(CompassLowReason.CALIBRATION), tilted = false, feed = arSa)
        assertEquals("البوصلة غير متاحة", arabic.caption)
        assertEquals(onDial(arSa, "٢٣", "٦٬٦٠٠"), arabic.detail)
        assertEquals("${arabic.caption}، ٢٣° · ٦٬٦٠٠ كم إلى مكة", arabic.captionDescription, "the phone's long phrase is heard")
    }

    @Test
    fun saudiArabicWritesItsDigitsAndLettersAndNeverMirrorsTheDial() {
        val screen = qiblaScreen(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)), tilted = false, feed = arSa)

        assertEquals("٢٣°", screen.caption)
        // ARABIC_SCREENS's letters, north, east, south and west.
        assertEquals(listOf("ش", "ق", "ج", "غ"), screen.cardinals)
        assertEquals(340.0, screen.heading, "the same heading: a compass never mirrors")
        assertEquals(23.0, screen.bearing)
        assertEquals(onDial(arSa, "٦٬٦٠٠"), screen.detail)
        assertEquals("مكة · على بُعد ٦٬٦٠٠ كم", screen.detailSpoken)
        assertEquals("استدر يمينًا حوالي ٤٥ درجة", screen.spoken)
    }

    /**
     * T8 (Ruling 66 items 1 and 6): on every sample the phone may send, the detail the dial draws is
     * the feed's bearing, its distance and the unit, and nothing else (Ruling 39: no coordinate, no
     * other word), while what a screen reader hears is the phone's long phrase: the detail's own
     * description on a pointing dial, and the caption's on a compass that has given up. The unit is
     * the short form's one word, read from the feed (Ruling 67 item 2), so a reviewer's form, with a
     * direction mark or a no-break space between its parts, is held as the phone sends it.
     */
    @Test
    fun theDialDrawsOnlyTheFeedsFiguresAndTheUnitAndSaysThePhonesPhrase() {
        val feeds = listOf("en" to sample, "ar-sa" to arSa, "ar-ly12" to TestFeeds.other("ar-ly12"), "en12" to TestFeeds.other("en12"))
        for ((name, feed) in feeds) {
            val words = feed.words()
            val bearing = WatchRules.digits(feed, feed.place.qiblaBearing.toInt().toLong())
            val distance = feed.place.qiblaKm
            val unit = Regex("""[\p{L}\p{M}]+""").findAll(words.distanceOnDial.replace(Regex("""%\d+\$[sd]"""), " ")).single().value
            val turning = listOf(
                qiblaScreen(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)), tilted = false, feed = feed),
                qiblaScreen(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)), tilted = true, feed = feed),
                qiblaScreen(DialState.Searching(Double.NaN, guidance = null), tilted = false, feed = feed),
                qiblaScreen(DialState.Aligned(24.0), tilted = false, feed = feed),
            )
            for (screen in turning) {
                assertEquals(onDial(feed, distance), screen.detail, name)
                assertTrue(unit in screen.detail, "$name: '${screen.detail}' has the unit '$unit'")
                assertEquals(WatchRules.filled(words.distance, distance), screen.detailSpoken, name)
                assertTrue(screen.detailRead, name)
            }
            for (state in listOf(DialState.BestEffort(CompassLowReason.INTERFERENCE), DialState.NoSensor)) {
                val screen = qiblaScreen(state, tilted = false, feed = feed)
                assertEquals(onDial(feed, bearing, distance), screen.detail, name)
                val heard = WatchRules.filled(words.qiblaDetail, bearing, distance)
                assertEquals(screen.caption + ComplicationFields.spokenSeparator(feed.ui) + heard, screen.captionDescription, name)
                assertFalse(screen.detailRead, "$name: said with the caption")
            }
            val low = qiblaScreen(DialState.Low(CompassLowReason.CALIBRATION), tilted = false, feed = feed)
            assertEquals("", low.detail, name)
            assertEquals("", low.detailSpoken, name)
            // Nothing drawn but the feed's figures, the unit and the marks between them: no other
            // letter and no other digit.
            for (screen in turning + qiblaScreen(DialState.NoSensor, tilted = false, feed = feed)) {
                val rest = screen.detail.replace(distance, "").replace("$bearing°", "").replace(unit, "")
                assertTrue(rest.none { it.isLetterOrDigit() }, "$name: '${screen.detail}' draws '$rest' besides its figures")
            }
        }
    }

    @Test
    fun aBadStatesTitleMayTakeTwoLines() {
        assertEquals(2, QiblaType.title.maxLines)
        assertEquals(11f..15f, QiblaType.title.range(1.0f))
        assertEquals(15f..18f, QiblaType.bearing.range(1.3f))
        // The detail is the drawing's 10 units, grown to 1.3, and may shrink to 9, on one line
        // always (Ruling 66 item 3; QiblaFitTest sweeps it).
        assertEquals(9f..10f, QiblaType.detail.range(1.0f))
        assertEquals(9f..13f, QiblaType.detail.range(1.3f))
        assertEquals(1, QiblaType.detail.maxLines)
        // The dial's letters grow with the font size and never shrink (the phone dial's rule).
        assertEquals(11.55f..11.55f * 1.3f, QiblaType.north.range(1.5f))
        assertEquals(9.975f..9.975f, QiblaType.cardinal.range(0.85f))
    }

    @Test
    fun aTitleKeepsInsideTheDialsRim() {
        // One line: the round screen's chord where its glyphs come nearest the edge, its descent
        // below the baseline at 141, as every line's (LineText).
        assertEquals(WatchUnits.chordUnits(141f + 15f * 0.25f, round = true), QiblaType.captionWidth(QiblaType.bearing, 1f, round = true), 1e-3f)
        // A title's last line keeps y 141 (it grows upward), and its dial is dimmed and has no
        // letters: it keeps inside the rim, 6 units in at each end, as the screen's chord keeps in.
        assertEquals(circleChord(92.0, 141.0 + 15 * 0.25) - 12, QiblaType.captionWidth(QiblaType.title, 1f, round = true).toDouble(), 1e-3)
        // The box the title had with its second line below, at y 155 (149.8): its seven languages
        // break where review rounds 1 and 2 saw them break.
        assertEquals(148.8, QiblaType.captionWidth(QiblaType.title, 1f, round = true).toDouble(), 0.05)
        // Grown by the font size to its cap (1.2), its last line rises on the half anchor (Ruling 66
        // item 2: 155 − 14 × 1.2) and its descent reaches further down.
        assertEquals(circleChord(92.0, 155 - 14 * 1.2 + 18 * 0.25) - 12, QiblaType.captionWidth(QiblaType.title, 1.3f, round = true).toDouble(), 1e-3)
        // The rim is a circle on any screen.
        assertEquals(QiblaType.captionWidth(QiblaType.title, 1f, round = true), QiblaType.captionWidth(QiblaType.title, 1f, round = false))
    }

    @Test
    fun theTiltNoticeStaysInsideTheDialsMarksAndTheLettersRoom() {
        // "Hold your wrist level" is drawn over a dial that still turns and points, shrinking to 11
        // before it takes a second line: never wider than the dial's innermost marks, its Kaaba
        // marker's halo (92 - 0.95 x 11 x 1.05 units from the middle, ref.py's room), and over the
        // dial's letters inside their room (Ruling 68 item 4, theCaptionOverATurningDial…).
        assertEquals(1, QiblaType.level.maxLines)
        assertEquals(11f..15f, QiblaType.level.range(1f))
        assertEquals(11f..18f, QiblaType.level.range(1.3f))
        assertEquals(circleChord(92.0 - 0.95 * 11 * 1.05, 141.0 + 15 * 0.25), QiblaType.captionWidth(QiblaType.level, 1f, round = true).toDouble(), 1e-3)
        assertEquals(QiblaType.captionWidth(QiblaType.level, 1f, round = true), QiblaType.captionWidth(QiblaType.level, 1f, round = false), "the dial is round on any screen")
    }

    /** The circle's chord, in units, of a circle of [radius] round the drawing's middle at height [y]. */
    private fun circleChord(radius: Double, y: Double): Double {
        val dy = y - 100
        return 2 * kotlin.math.sqrt(radius * radius - dy * dy)
    }

    /** English "Hold your wrist level" over the English dial's turning letters, as the page hands it to the box. */
    private fun englishNotice(): QiblaType.Turning {
        val form = QiblaSweep.form("en", "west")
        val glyphs = QiblaSweep.host(HostInk.Variant.UI).line("Hold your wrist level", 800, rtl = false).boxes
        return QiblaType.Turning(glyphs.minOf { it.top }, glyphs.maxOf { it.bottom }) { grow -> QiblaSweep.letters(form, grow, HostInk.Variant.UI) }
    }

    @Test
    fun aCaptionOverATurningDialThatFitsOneLineAtItsFloorKeepsToOneInTheLettersRoom() {
        val asked = mutableListOf<Pair<Float, Float>>()
        val box = QiblaType.captionBox(QiblaType.level, 1f, round = true, englishNotice()) { size, width -> asked += size to width; 1 }

        assertEquals(1, box.maxLines)
        assertEquals(141f, box.y, "at the bearing's place")
        assertEquals(15f, box.size)
        // Asked once, at its floor in the letters' room, which is narrower than the dial's marks:
        // the letters turn 72.05 units out, inside the Kaaba's halo.
        assertEquals(listOf(11f to box.width), asked)
        assertTrue(box.width < QiblaType.captionWidth(QiblaType.level, 1f, round = true) - 30f, "the letters' room ${box.width}")
        // One box at every font size, so the caption never shrinks as the font grows.
        for (fontScale in listOf(0.85f, 1.06f, 1.18f, 1.3f, 2f)) {
            val grown = QiblaType.captionBox(QiblaType.level, fontScale, round = true, englishNotice()) { _, _ -> 1 }
            assertEquals(box.width, grown.width, "at $fontScale")
            assertEquals(QiblaFit.captionY(QiblaType.level, fontScale), grown.y)
            assertEquals(QiblaType.level.range(fontScale).endInclusive, grown.size)
        }
    }

    @Test
    fun aCaptionOverATurningDialTooWideForOneLineAtItsFloorTakesASecondAboveIt() {
        // English "Hold your wrist level" is about 110 units on one line at 11, against the letters'
        // room of about 95: on one line the turning E met its last "l" (P3.9's qibla-minors/).
        val box = QiblaType.captionBox(QiblaType.level, 1f, round = true, englishNotice()) { _, _ -> 2 }

        assertEquals(QiblaType.LEVEL_LINES, box.maxLines)
        assertEquals(2, QiblaType.LEVEL_LINES)
        // It grows upward over a dial that still turns (the half anchor's rule): its last line keeps
        // the bearing's place at y 141 and its first stands 16 above, at 125, so the distance keeps
        // y 155, clear of the ticks and letters a second line at 155 would push it onto
        // (emulator-5558, review round 1).
        assertEquals(125f, box.y)
        assertEquals(15f, box.size)
        assertEquals(155f, QiblaFit.detailY(QiblaType.level, fontScale = 1f, size = 10f))
        // Grown by the font size to its cap (1.2): the last line on the half anchor (155 − 14 × 1.2,
        // Ruling 66 item 2) and the first a pitch above it, at the size that keeps the first line's
        // "H" two units under the needle's hub (17, short of the line's 18).
        val grown = QiblaType.captionBox(QiblaType.level, 1.3f, round = true, englishNotice()) { _, _ -> 2 }
        assertEquals(17f, grown.size)
        assertEquals(155f - 14f * 1.2f - 16f * 17f / 15f, grown.y, 1e-4f)
        assertEquals(box.width, grown.width, "one box at every font size")
        assertEquals(grown, QiblaType.captionBox(QiblaType.level, 1.3f, round = false, englishNotice()) { _, _ -> 2 }, "the dial is round on any screen")
    }

    @Test
    fun aCaptionOfTwoLinesOverATurningDialIsSetFromTheSizeTextAutoSizeDrawsItAt() {
        // Words that take two lines in the letters' room only at 13.5 or less (French "Gardez le /
        // poignet à plat"): TextAutoSize draws them at 13.5, so the box's size is 13.5 and its first
        // baseline a pitch at 13.5 above the half anchor, where its last line lies (review round 1:
        // set from 15, the last line stood 1.6 units high at 1.0, and 3.2 at 1.3).
        val asked = mutableListOf<Float>()
        val fits = { size: Float, _: Float -> asked += size; if (size <= 13.5f) 2 else 3 }
        val box = QiblaType.captionBox(QiblaType.level, 1f, round = true, englishNotice(), fits)
        assertEquals(13.5f, box.size)
        assertEquals(141f - 16f * 13.5f / 15f, box.y, 1e-4f)
        // Its floor, for one line; then from the floor a step at a time, as StepBased steps, until a
        // step does not fit.
        assertEquals(listOf(11f, 11.5f, 12f, 12.5f, 13f, 13.5f, 14f), asked)
        val grown = QiblaType.captionBox(QiblaType.level, 1.3f, round = true, englishNotice(), fits)
        assertEquals(13.5f, grown.size)
        assertEquals(155f - 14f * 1.2f - 16f * 13.5f / 15f, grown.y, 1e-4f)
        // A largest size between two steps (Turkish "Kıbleye / dönüksünüz" at 1.1: 13 × 1.1 = 14.3) is
        // drawn at the step below it, 14, and set from it.
        val facing = QiblaType.captionBox(QiblaType.facing, 1.1f, round = true, englishNotice()) { _, _ -> 2 }
        assertEquals(14f, facing.size)
        assertEquals(QiblaFit.captionY(QiblaType.facing, 1.1f) - 16f * 14f / 13f, facing.y, 1e-4f)
    }

    @Test
    fun aTurningDialsCaptionBoxIsTheNarrowerOfItsRoomAtTheDrawingsFontSizeAndAtTheLettersLargest() {
        // Letters that grow far more than the dial's (each box twice its size at their largest, 1.3):
        // the room at 1.3, where the caption's last line also stands higher, is the narrower, and the
        // box's at every font size, the drawing's included (review round 1: a box judged at 1.0 alone
        // passed every Qibla test).
        val en = englishNotice()
        val swollen = QiblaType.Turning(en.top, en.bottom) { grow -> en.letters(grow).map { letter -> letter.map { it.scaled(if (grow > 1.01f) 2f else 1f) } } }
        val line = QiblaType.level
        fun room(f: Float) = QiblaFit.captionRoom(
            QiblaFit.captionY(line, f), en.top * line.range(f).endInclusive, en.bottom * line.range(f).endInclusive, swollen.letters(QiblaFit.letterGrow(f)),
        )
        assertTrue(room(1.3f) < room(1f) - 1f, "the room at 1.3 ${room(1.3f)}, at 1.0 ${room(1f)}")
        for (fontScale in listOf(1f, 1.3f)) {
            assertEquals(room(1.3f), QiblaType.captionBox(line, fontScale, round = true, swollen) { _, _ -> 2 }.width, 1e-4f, "at $fontScale")
        }
    }

    @Test
    fun aTitleTakesTheLinesItTakesAtItsLargestSizeGrowingUpward() {
        val width = QiblaType.captionWidth(QiblaType.title, 1f, round = true)
        val asked = mutableListOf<Pair<Float, Float>>()

        // Two lines at 15 before one at 11: asked once, at its largest size in its box. Its last
        // line keeps y 141 and its first stands 16 above, at 125, so the detail keeps y 155.
        assertEquals(QiblaType.CaptionBox(2, width, 125f, 15f), QiblaType.captionBox(QiblaType.title, 1f, round = true) { size, w -> asked += size to w; 2 })
        assertEquals(listOf(15f to width), asked)
        // One line ("No compass sensor"): at the bearing's place, free to take a second as it shrinks.
        assertEquals(QiblaType.CaptionBox(2, width, 141f, 15f), QiblaType.captionBox(QiblaType.title, 1f, round = true) { _, _ -> 1 })
        // More than two at 15 ("La boussole doit être calibrée" in a narrower box): two, shrunk to fit.
        assertEquals(QiblaType.CaptionBox(2, width, 125f, 15f), QiblaType.captionBox(QiblaType.title, 1f, round = true) { _, _ -> 3 })
        // Grown by the font size to its cap (1.2): asked at 18, its last line on the half anchor
        // (155 − 14 × 1.2) and its first 16 x 1.2 above it.
        val grown = QiblaType.captionBox(QiblaType.title, 1.3f, round = true) { size, _ -> assertEquals(18f, size); 2 }
        assertEquals(155f - 14f * 1.2f - 16f * 1.2f, grown.y, 1e-4f)
    }

    @Test
    fun aOneLineCaptionWithNoLettersToKeepFromIsNeverMeasured() {
        val never: (Float, Float) -> Int = { _, _ -> error("the bearing and \"Facing qibla\" take one line with no letters") }
        assertEquals(QiblaType.CaptionBox(1, QiblaType.captionWidth(QiblaType.bearing, 1f, round = true), 141f, 15f), QiblaType.captionBox(QiblaType.bearing, 1f, round = true, lineCount = never))
        assertEquals(QiblaType.CaptionBox(1, QiblaType.captionWidth(QiblaType.facing, 1.3f, round = false), 155f - 14f * 1.2f, QiblaType.facing.range(1.3f).endInclusive), QiblaType.captionBox(QiblaType.facing, 1.3f, round = false, lineCount = never))
    }

    @Test
    fun aHeadingThatIsNotANumberDrawsNoNeedleAndSaysNoTurn() {
        // Ruling 58 item 1: a trusted heading that is not a number keeps the phone's behaviour,
        // Searching with no turn until reset. The page draws neither needle nor marker, says no
        // turn, and keeps the Searching caption.
        val dial = WatchQibla(sample.place.qiblaBearing)
        dial.onReading(CompassReading(340.0, timestampMillis = 0, isLowAccuracy = false))
        val step = dial.onReading(CompassReading(Double.NaN, timestampMillis = 50, isLowAccuracy = false))
        assertTrue(step.state is DialState.Searching, "still Searching: ${step.state}")

        val screen = screenOf(step.state)
        assertFalse(screen.needle)
        assertFalse(screen.marker)
        assertFalse(screen.dimmed, "the compass is not low: it is searching")
        assertFalse(screen.lit)
        assertEquals(0.0, screen.heading, "the dial at rest")
        assertEquals(emptyList(), screen.cardinals, "no heading, so no letters")
        assertNull(screen.spoken)
        assertNull(screen.dialSpoken)
        assertEquals("23°", screen.caption)
        assertEquals(QiblaType.bearing, screen.captionLine)
        assertEquals(onDial(sample, km), screen.detail)

        assertEquals("Hold your wrist level", screenOf(step.state, tilted = true).caption)
    }

    @Test
    fun anInfiniteHeadingIsNoHeadingEvenWithATurn() {
        val screen = screenOf(DialState.Searching(Double.POSITIVE_INFINITY, Guidance(Turn.RIGHT, 45)))

        assertFalse(screen.needle)
        assertFalse(screen.marker)
        assertNull(screen.spoken)
        assertEquals(0.0, screen.heading)
        assertEquals(emptyList(), screen.cardinals)
    }
}
