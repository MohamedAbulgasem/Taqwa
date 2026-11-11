package world.taqwa.app.wear.qibla

import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.DialState
import world.taqwa.app.qibla.Turn
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.farEdge
import world.taqwa.app.wear.complication.ComplicationFields

/**
 * The dial's own measures, `qiblaDial(100, 100, 92, heading, bearing, A, 1.05)` in `faces.js`, in the
 * drawing's units: what the page draws (`QiblaPage`) and what its lines keep clear of ([QiblaType]),
 * from one place, so that the two cannot part.
 */
internal object QiblaDialMeasures {
    /** The rim's radius, from the middle: on the screen's edge inside the bezel, a circle on any screen. */
    const val RIM = 92f

    /** The scale the dial is drawn at: every measure below is drawn [K] times over, round the [RIM]. */
    const val K = 1.05f

    /** How many ticks run round the rim, one every 15°. */
    const val TICKS = 24

    /** Where each tick begins and ends, in units inside the rim. */
    const val TICK_FROM = 9f
    const val TICK_TO = 5f

    /** The Kaaba marker's side, centred on the rim, and its halo's radius as a share of that side. */
    const val KAABA = 11f
    const val KAABA_HALO = 0.95f

    /**
     * Where the letters turn: each letter's middle [LETTERS] units inside the rim, and its baseline
     * [LETTER_DROP] below that middle, grown with the letter by the wearer's font size
     * ([QiblaType.LETTER_RADIUS]). The detail keeps clear of them at every heading ([QiblaFit]).
     */
    const val LETTERS = 19f
    const val LETTER_DROP = 3.6f

    /** The needle's hub, round the middle: its radius, drawn [K] times over. */
    const val HUB = 4.6f
}

/**
 * The Qibla page's lines (call 15), in the drawing's units (`gQibla`, `qiblaDial` at k 1.05): the
 * caption's last line at y 141, and the detail under it at y 155 at the drawing's size, both on
 * Ruling 66's half anchor as the wearer's font size grows ([QiblaFit]).
 */
internal object QiblaType {
    /** The bearing, "23°". */
    val bearing = Line(15f, 1.2f)

    /** "Facing qibla", in the accent; on two lines where one is too wide for the turning letters' room (Turkish "Kıbleye dönüksünüz", [captionBox]). */
    val facing = Line(13f, 1.2f)

    /**
     * A bad state's title ("Compass needs calibrating" and the rest): two lines at most, shrinking to
     * 11 before an ellipsis. Two lines grow upward ([captionBox]): the last keeps y 141 and the first
     * stands [TITLE_PITCH] above it, at y 125, so the detail keeps y 155 under it. Its dial is dimmed
     * and has no letters, so it keeps inside the dial's rim rather than its marks ([captionWidth]).
     */
    val title = Line(15f, 1.2f, maxLines = 2, floor = 11f)

    /**
     * "Hold your wrist level", drawn over a dial that still turns and points: shrinking to 11 inside
     * the turning letters' room ([Turning], Ruling 68 item 4), never wider than the dial's innermost
     * marks ([DIAL_ROOM]), so no letter or tick meets it and the detail keeps its place. A phrase too
     * wide for one line there at 11 (English "Hold your wrist level", about 110 units against a room
     * of about 95) takes [LEVEL_LINES] instead, growing upward as a title does ([captionBox]).
     */
    val level = Line(15f, 1.2f, floor = 11f)

    /** How many lines a caption over a turning dial may take when one line at its floor is too wide for the letters' room ([captionBox]). */
    const val LEVEL_LINES = 2

    /**
     * The distance, or the bearing and the distance, in the watch's short form (Ruling 66): one
     * line always, at the drawing's 10 units grown by the font size to 1.3 or shrunk to 9, on a grid
     * of halves, the first size that keeps clear of the turning letters and the ticks ([QiblaFit]).
     * It never wraps ([DETAIL_SOFT_WRAP]) and never ends in an ellipsis ([DETAIL_OVERFLOW]), so a
     * device font wider than the host's measure can neither cut it nor give it a second line.
     */
    val detail = Line(10f, 1.3f, floor = 9f)

    /** The detail never wraps: one line, whatever the device's font draws (Ruling 66 item 3). */
    const val DETAIL_SOFT_WRAP = false

    /** The detail is never cut with an ellipsis (Ruling 66 item 3). */
    val DETAIL_OVERFLOW = TextOverflow.Visible

    /**
     * The dial's letters on the turning dial: `qiblaDial`'s 11 and 9.5 units at k 1.05. They grow
     * with the wearer's font size and never shrink, as the phone dial's letters do.
     */
    val north = Line(11.55f, 1.3f)
    val cardinal = Line(9.975f, 1.3f)

    /** Where the caption's last baseline sits, and the detail's at the drawing's size (`gQibla`). */
    const val CAPTION_Y = 141f
    const val DETAIL_Y = 155f

    /**
     * How far apart a caption's two lines stand, at the drawing's size of 15 units: 16 units, 1.067
     * of the size, so a title's first line sits at y 125 over its last at 141. Manrope ExtraBold's ink
     * reaches 0.81 of the size above the baseline (the grave of "à") and 0.255 below it (the tail of
     * "g"), 1.065 in all, so no glyph of one line can meet a glyph of the other, wherever the text
     * breaks (`QiblaInkTest`). At 14 (0.933) the English "Magnetic" / "interference" joined its g to
     * the f, and "Compass needs" / "calibrating" its p to the l (review round 2). Arabic and Bengali
     * are drawn in the watch's own fonts, which this cannot hold; HarfBuzz in their Noto fonts found
     * them clear (plan 3 Task 7's as-built note).
     */
    const val TITLE_PITCH = 16f

    /** The dial's rim, from the middle in units ([QiblaDialMeasures.RIM]): a circle on any screen. */
    const val RIM_RADIUS = QiblaDialMeasures.RIM

    /**
     * How far from the middle the dial's innermost marks begin, in units: its Kaaba marker's halo,
     * 0.95 of the 11-unit marker round a point on the 92 rim, drawn 1.05 times over, inside the
     * ticks (9 units in); `ref.py`'s room for `g-qibla`'s words. The rim is a circle on any screen.
     */
    const val DIAL_ROOM = RIM_RADIUS - QiblaDialMeasures.KAABA_HALO * QiblaDialMeasures.KAABA * QiblaDialMeasures.K

    /** How far inside the rim a title keeps at each end, in units: the inset the screen's chord keeps from its edge ([WatchUnits.chordUnits]). */
    const val RIM_INSET = 6f

    /**
     * How far from the middle the dial's ticks begin, in units: `qiblaDial`'s ticks run from 9 to 5
     * units inside the rim, drawn 1.05 times over. The detail's ink keeps [QiblaFit.LETTER_AIR]
     * inside them on every dial (Ruling 66 item 3), since they turn under its ends.
     */
    const val TICK_ROOM = RIM_RADIUS - QiblaDialMeasures.TICK_FROM * QiblaDialMeasures.K

    /**
     * How far from the middle the turning letters' middles stand, in units: 19 inside the rim,
     * drawn 1.05 times over, 72.05 out; `QiblaPage` draws them here, and the detail keeps
     * [QiblaFit.LETTER_AIR] from their ink at every heading ([QiblaFit]).
     */
    const val LETTER_RADIUS = RIM_RADIUS - QiblaDialMeasures.LETTERS * QiblaDialMeasures.K

    /**
     * How wide the caption's box may be, in units, where its last line's glyphs come nearest the
     * edge ([farEdge] at its last baseline, [QiblaFit.captionY]: y 141 at the drawing's size):
     * - [level]: the chord of the dial's room ([DIAL_ROOM]), as it is drawn over a dial that turns;
     * - [title]: the chord of the dial's rim, [RIM_INSET] in at each end, over a dial dimmed and
     *   without letters. At the drawing's size it is within a unit of the box the title had when its
     *   second line went below (148.8 units, against 149.8), so the titles break where review rounds
     *   1 and 2 saw them break;
     * - every other caption: the round screen's chord, as every one-line line's (`LineText`).
     */
    fun captionWidth(line: Line, fontScale: Float, round: Boolean): Float {
        val edge = farEdge(QiblaFit.captionY(line, fontScale), centred = false, line = line, size = line.range(fontScale).endInclusive)
        return when (line) {
            level -> circleChord(DIAL_ROOM, edge)
            title -> (circleChord(RIM_RADIUS, edge) - 2 * RIM_INSET).coerceAtLeast(0f)
            else -> WatchUnits.chordUnits(edge, round)
        }
    }

    /** The chord, in units, of a circle of [radius] round the drawing's middle at height [y]. */
    private fun circleChord(radius: Float, y: Float): Float {
        val dy = y - WatchUnits.ACROSS / 2
        val half = radius * radius - dy * dy
        return if (half <= 0f) 0f else 2 * sqrt(half)
    }

    /**
     * How many lines a caption may take, how wide its box is, where its first baseline sits, and the
     * largest size it is drawn at there, in units ([captionBox]).
     */
    data class CaptionBox(val maxLines: Int, val width: Float, val y: Float, val size: Float)

    /**
     * A caption drawn over a dial that turns (the bearing, "Facing qibla" and the tilt notice, on a
     * dial with letters: Ruling 68 item 4): its glyphs' highest and lowest ink about its baseline, in
     * ems ([top] below 0, y down, as [InkBox] measures), and the dial's [letters], each its ink about
     * its middle at a growth ([QiblaFit.letterInk]), which every line keeps [QiblaFit.LETTER_AIR] from.
     */
    class Turning(val top: Float, val bottom: Float, val letters: (grow: Float) -> List<List<InkBox>>)

    /** How far, in units, a caption of two lines over a turning dial keeps its first line's ink from the needle's hub ([turningBox]). */
    const val HUB_AIR = 2f

    /**
     * The caption's box under [fontScale]. [lineCount] says how many lines the caption's text takes
     * at a size in a width, both in units, as the page lays it out. [turning] is given for a caption
     * over a dial that turns, which keeps to the letters' room ([turningBox], Ruling 68 item 4); the
     * page gives it for every caption but a dimmed dial's title, so the rules below are the title's,
     * and the others' with no letters to keep from.
     *
     * - [level]: one line while the text fits it at the line's floor (11); else [LEVEL_LINES]. One
     *   line first, so a notice that can shrink onto one is drawn as the drawing's caption, and only
     *   a phrase that would lose its end to an ellipsis takes a second.
     * - [title]: the lines it takes at its largest size, two at most, so two lines at 15 come before
     *   one at 11.
     * - Every other caption: one line.
     *
     * A caption of two lines grows upward: its last line keeps its baseline ([QiblaFit.captionY], y
     * 141 at the drawing's size) and its first stands [TITLE_PITCH] above it (y 125, grown with the
     * font size), in the same box, since the last line still comes nearest the edge. So the detail
     * keeps its place under it ([QiblaFit.detailY]). A title's first baseline is set from its
     * largest size, so a title drawn smaller (between two of TextAutoSize's steps at Wear's 1.06,
     * 1.12 and 1.18, or with words that fit two lines only below it) sits as much higher, up to 0.43
     * units for the former, as since plan 3 Task 7; a caption over a turning dial is set from the
     * size it is drawn at ([turningBox]), so its last line lies on the anchor. Downward, a
     * second line at y 155 pushed the detail to y 169, across the ticks and the "S" of a dial that
     * still turns (review round 1), and at [TITLE_PITCH] it would have pushed a title's detail out of
     * the screen's room at font size 1.3, cutting the English one (review round 2's fix).
     */
    fun captionBox(line: Line, fontScale: Float, round: Boolean, turning: Turning? = null, lineCount: (sizeUnits: Float, widthUnits: Float) -> Int): CaptionBox {
        if (turning != null) return turningBox(line, fontScale, round, turning, lineCount)
        val width = captionWidth(line, fontScale, round)
        val sizes = line.range(fontScale)
        val lines = when (line) {
            level -> if (lineCount(sizes.start, width) <= 1) 1 else LEVEL_LINES
            title -> lineCount(sizes.endInclusive, width).coerceIn(1, line.maxLines)
            else -> return CaptionBox(line.maxLines, width, QiblaFit.captionY(line, fontScale), sizes.endInclusive)
        }
        val grow = QiblaFit.growth(line, fontScale)
        return CaptionBox(if (line == level) lines else line.maxLines, width, QiblaFit.captionY(line, fontScale) - (lines - 1) * TITLE_PITCH * grow, sizes.endInclusive)
    }

    /**
     * The box of a caption over a dial that turns (Ruling 68 item 4), the same at every font size, so
     * that as the font grows the caption never shrinks and never changes its lines:
     *
     * - **Its width** is the letters' room at its last baseline ([QiblaFit.captionRoom]), for its
     *   glyphs at their largest size there and the letters as drawn, at the drawing's font size and
     *   at the largest the letters grow to ([ROOM_SCALES]), the narrower, and never wider than
     *   [captionWidth]. So no glyph of it comes within [QiblaFit.LETTER_AIR] of a turning letter at
     *   any heading, and the dial is never cleared for it.
     * - **One line** while its words fit that width at the line's floor; else two, by the half
     *   anchor's rule (Ruling 66 item 2): the last line keeps the half anchor and the first stands
     *   [TITLE_PITCH] above it (at the size drawn), growing upward, so the detail keeps its place. The first line, between
     *   the hub and the last, is nearer the middle at both its ends, so its room is wider and the last
     *   line's is the box's.
     * - **Two lines are drawn no larger** than keeps the first line's ink [HUB_AIR] from the needle's
     *   hub with the caption at its highest (its half anchor at its cap), and at the size
     *   TextAutoSize draws them in the box ([drawnSize]): [CaptionBox.size], from which the first
     *   baseline is set, so the last line lies on the half anchor at the size drawn.
     */
    private fun turningBox(line: Line, fontScale: Float, round: Boolean, turning: Turning, lineCount: (sizeUnits: Float, widthUnits: Float) -> Int): CaptionBox {
        val sizes = line.range(fontScale)
        fun room(f: Float, y: Float, size: Float) =
            QiblaFit.captionRoom(y, turning.top * size, turning.bottom * size, turning.letters(QiblaFit.letterGrow(f)))
        val last = ROOM_SCALES.minOf { f -> room(f, QiblaFit.captionY(line, f), line.range(f).endInclusive) }
        val one = min(captionWidth(line, fontScale, round), last)
        if (lineCount(sizes.start, one) <= 1) return CaptionBox(1, one, QiblaFit.captionY(line, fontScale), sizes.endInclusive)
        val most = max(sizes.start, min(sizes.endInclusive, twoLineMost(line, turning)))
        val size = drawnSize(sizes.start, most) { lineCount(it, one) <= LEVEL_LINES }
        return CaptionBox(LEVEL_LINES, one, QiblaFit.captionY(line, fontScale) - TITLE_PITCH * size / line.units, size)
    }

    /**
     * The size, in units, TextAutoSize draws a caption at in a box whose largest size is [most]
     * (`QiblaPage`'s `StepBased`, from the line's [floor] by [CAPTION_STEP]): the largest of its
     * steps at or below [most] at which the caption [fits], or the floor when none above it does.
     * A box set from [most] itself, where the words fit only smaller, lifted a second line off the
     * half anchor (French "Gardez le / poignet à plat" drawn at 13.5 in a box of 15, 1.6 units high
     * at 1.0, 3.2 at 1.1 to 1.3), as did a [most] between two steps (Turkish "Kıbleye / dönüksünüz"
     * drawn at 14 in a box of 14.3 at 1.1).
     */
    private fun drawnSize(floor: Float, most: Float, fits: (Float) -> Boolean): Float {
        var size = floor
        while (size + CAPTION_STEP <= most + EPSILON && fits(size + CAPTION_STEP)) size += CAPTION_STEP
        return size
    }

    /**
     * The largest size, on the grid of halves, a caption of two lines over a turning dial is drawn
     * at: its first line, [TITLE_PITCH] (grown with the size) above its last on the half anchor at its
     * highest, keeps its glyphs' highest ink ([Turning.top]) [HUB_AIR] below the needle's hub.
     */
    private fun twoLineMost(line: Line, turning: Turning): Float {
        val highest = QiblaFit.captionY(line, ROOM_SCALES.last())
        val hub = WatchUnits.ACROSS / 2 + QiblaDialMeasures.HUB * QiblaDialMeasures.K
        val most = (highest - hub - HUB_AIR) / (TITLE_PITCH / line.units - turning.top)
        return floor(most * 2 + 1e-3f) / 2
    }

    /** The font sizes a turning dial's caption box is judged at: the drawing's, and the largest its letters grow to ([north]'s cap). */
    private val ROOM_SCALES = listOf(1f, north.cap)

    /** How finely the caption's size is searched, in units: `QiblaPage`'s `TextAutoSize` step, as `LineText` searches. */
    const val CAPTION_STEP = 0.5f

    private const val EPSILON = 1e-3f
}

/**
 * What the Qibla page draws and says.
 *
 * - [needle] and [marker]: the needle from the centre, and the Kaaba's marker on the rim at
 *   [bearing] − [heading]. Only a heading worth pointing with draws them.
 * - [dimmed]: the dial at alpha 0.4; [lit]: the rim lit, facing the Qibla.
 * - [heading]: what the dial is turned by, in degrees true; 0, north up, when there is none.
 * - [caption] at y 141 in [captionLine], in the accent when [captionAccent]; [detail] under it, the
 *   watch's short form (Ruling 66 item 1: the distance, or the bearing and the distance, with the
 *   unit alone), and [detailSpoken], the phone's long phrase for it, which a screen reader hears.
 * - [spoken]: what a screen reader is told of the state, if anything beyond the caption.
 * - [captionDescription]: what the caption says to a screen reader in place of its words, on a
 *   dimmed dial: the title, and in BestEffort and NoSensor the bearing and the distance after it,
 *   so that a compass that has given up says so first and then what it can still give, in one
 *   announcement. Null while the dial points, when the caption is read as its words. [detailRead]:
 *   whether the detail is a screen-reader item of its own, which it is wherever it is shown and
 *   not said with the caption. How the page uses them, and why, is in `QiblaPage`.
 * - [cardinals]: north, east, south and west in the phone's language, on the turning dial; none
 *   while there is no heading, since a dial at rest would name a north the watch does not know (the
 *   phone's dimmed dial draws no letters either).
 * - [dialLetters]: the letters the detail keeps clear of (Ruling 66 item 3): the feed's cardinals
 *   on every turning dial, Searching before its first reading among them, so a reading's arrival
 *   never resizes or moves the detail; none on a dimmed dial.
 */
internal data class QiblaScreen(
    val needle: Boolean,
    val marker: Boolean,
    val dimmed: Boolean,
    val lit: Boolean,
    val heading: Double,
    val caption: String,
    val captionAccent: Boolean,
    val detail: String,
    val detailSpoken: String,
    val spoken: String?,
    val bearing: Double,
    val cardinals: List<String>,
    val dialLetters: List<String>,
    val captionLine: Line,
    val captionDescription: String?,
    val detailRead: Boolean,
) {
    /**
     * What the dial says, as a polite live region: [spoken], unless the caption already says it.
     * The caption is a live region too, so "Facing qibla" and "Hold your wrist level" are said once,
     * by the caption, and the dial speaks only the turn.
     */
    val dialSpoken: String? get() = spoken?.takeIf { it != caption }
}

/**
 * The page for the dial's [state], with the screen [tilted] or not, from the [feed]'s words and
 * digits (`feed.words()`, `WatchRules`): the bearing is the feed's whole degree,
 * `qiblaBearing.toInt()`, in the feed's digits, and the distance the phone's own string. The watch
 * never learns where it is (Ruling 39): these are the phone's figures as it rounded them. The dial
 * draws them with the unit alone (`distanceOnDial`, `qiblaDetailOnDial`), and a screen reader hears
 * the phone's phrases (`distance`, `qiblaDetail`), Ruling 66 item 1.
 *
 * - **Searching:** the needle and the marker. The caption is the bearing, or "Hold your wrist level"
 *   while tilted; the detail is the distance; a screen reader is told the turn ("Turn right about
 *   45 degrees", the phrase its number takes), or to hold the wrist level. A heading that is not a
 *   number (Ruling 58 item 1: the phone's filter keeps a NaN until it is reset) draws neither needle
 *   nor marker nor letters and tells no turn, and keeps the Searching caption. The page shows this
 *   too before the first reading.
 * - **Aligned:** the rim lit, the needle and the marker, "Facing qibla" in the accent, said.
 * - **Low:** dimmed, with no needle and no marker (the phone's B41), and the phone's title for its
 *   reason. The phone shows no readout here, and neither does the watch.
 * - **BestEffort and NoSensor:** dimmed, no needle or marker; the phone's title, and the bearing
 *   and the distance, which need no compass.
 *
 * The dial never mirrors: [QiblaScreen.heading] is the same in every language.
 */
internal fun qiblaScreen(state: DialState, tilted: Boolean, feed: WatchFeed): QiblaScreen {
    val words = feed.words()
    val bearing = WatchRules.digits(feed, feed.place.qiblaBearing.toInt().toLong())
    val km = feed.place.qiblaKm
    // What the dial draws (the watch's short forms) and what a screen reader hears (the phone's
    // phrases), Ruling 66 item 1.
    val distance = WatchRules.filled(words.distanceOnDial, km)
    val distanceSpoken = WatchRules.filled(words.distance, km)
    val both = WatchRules.filled(words.qiblaDetailOnDial, bearing, km)
    val bothSpoken = WatchRules.filled(words.qiblaDetail, bearing, km)
    val resting = QiblaScreen(
        needle = false,
        marker = false,
        dimmed = true,
        lit = false,
        heading = 0.0,
        caption = "",
        captionAccent = false,
        detail = "",
        detailSpoken = "",
        spoken = null,
        bearing = feed.place.qiblaBearing,
        cardinals = emptyList(),
        dialLetters = emptyList(),
        captionLine = QiblaType.title,
        captionDescription = null,
        detailRead = false,
    )
    val screen = when (state) {
        is DialState.Searching -> {
            val pointing = state.heading.isFinite()
            val turn = state.guidance?.takeIf { pointing }?.let { guidance ->
                val phrases = if (guidance.turn == Turn.RIGHT) words.turnRight else words.turnLeft
                WatchRules.counted(feed, phrases, guidance.degrees.toLong())
            }
            resting.copy(
                needle = pointing,
                marker = pointing,
                dimmed = false,
                heading = if (pointing) state.heading else 0.0,
                cardinals = if (pointing) words.cardinals else emptyList(),
                dialLetters = words.cardinals,
                caption = if (tilted) words.holdLevel else WatchRules.filled(words.bearing, bearing),
                captionLine = if (tilted) QiblaType.level else QiblaType.bearing,
                detail = distance,
                detailSpoken = distanceSpoken,
                spoken = if (tilted) words.holdLevel else turn,
            )
        }
        is DialState.Aligned -> resting.copy(
            needle = true,
            marker = true,
            dimmed = false,
            lit = true,
            heading = state.heading,
            cardinals = words.cardinals,
            dialLetters = words.cardinals,
            caption = words.facingQibla,
            captionAccent = true,
            captionLine = QiblaType.facing,
            detail = distance,
            detailSpoken = distanceSpoken,
            spoken = words.facingQibla,
        )
        is DialState.Low -> resting.copy(
            caption = when (state.reason) {
                CompassLowReason.CALIBRATION -> words.needsCalibrating
                CompassLowReason.INTERFERENCE -> words.interference
            },
        )
        is DialState.BestEffort -> resting.copy(caption = words.bestEffort, detail = both, detailSpoken = bothSpoken)
        DialState.NoSensor -> resting.copy(caption = words.noSensor, detail = both, detailSpoken = bothSpoken)
    }
    // A given-up compass's bearing and distance are said after its title, with the comma the
    // phone's direction reads (ComplicationFields.spokenSeparator, as Today joins its lines).
    val withTitle = state is DialState.BestEffort || state == DialState.NoSensor
    return screen.copy(
        captionDescription = when {
            withTitle -> screen.caption + ComplicationFields.spokenSeparator(feed.ui) + screen.detailSpoken
            screen.dimmed -> screen.caption
            else -> null
        },
        detailRead = screen.detail.isNotEmpty() && !withTitle,
    )
}
