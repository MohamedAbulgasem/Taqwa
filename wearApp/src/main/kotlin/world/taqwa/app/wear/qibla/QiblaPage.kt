package world.taqwa.app.wear.qibla

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import world.taqwa.app.qibla.DialState
import world.taqwa.app.qibla.WatchQibla
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.LocalRoundScreen
import world.taqwa.app.wear.app.LocalWatchUnits
import world.taqwa.app.wear.app.UnitsBox
import world.taqwa.app.wear.app.WatchColors
import world.taqwa.app.wear.app.WatchFonts
import world.taqwa.app.wear.app.WatchHaptics
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.atUnits

/**
 * Qibla (spec 2.5, `gQibla`): the app's dial across the whole screen, its rim on the screen's edge,
 * turned by the watch's own compass ([WristCompass]) and set true by the feed's declination, with no
 * location asked. The bearing and the distance sit in the dial's lower half, the distance on one
 * line clear of the turning letters at every heading (Ruling 66, [QiblaFit]).
 *
 * While [active] (the current page, the activity resumed) the compass is heard, into a [WatchQibla]
 * over the feed's bearing, reset on each start, and a [Tilt.Level]; the watch buzzes once on each
 * entry into alignment. Otherwise nothing is registered. With no compass the dial is
 * [DialState.NoSensor] and no sensor is ever registered. Before the first reading the dial shows no
 * needle, as a heading that is not a number does (Ruling 58 item 1).
 *
 * For a screen reader: the page's title is its pane title; while it searches, the dial says the
 * turn, as a polite live region; the caption is a polite live region too, so "Facing qibla", a bad
 * state's title and "Hold your wrist level" are said as they come (the phone's round-2 rule), and
 * "Compass unavailable" and "No compass sensor" with the bearing and the distance after them; the
 * detail is read as the phone's phrase, "Makkah · 6,600 km away", where the dial draws the watch's
 * short form, wherever the caption does not say it ([QiblaScreen.detailRead], `detailSpoken`).
 *
 * How the caption is heard (review round 1, on emulator-5558 with TalkBack):
 * - A change of a text's words alone reaches TalkBack as a content change of undefined type, which
 *   it did not say for the live region (its log); a change of size comes as a SUBTREE change, which
 *   it says. So a live region of words is heard only when its size changes with them, which every
 *   change of a pointing dial's caption does ("23°", "Facing qibla" and "Hold your wrist level" are
 *   three sizes and boxes), and one bad state's title after another in the same box did not
 *   ("Magnetic interference" to "Compass unavailable", two lines each, was never said).
 * - So a dimmed dial's caption has a content description ([QiblaScreen.captionDescription]), whose
 *   every change Compose sends.
 * - A pointing dial's caption has none. TalkBack queues a live region's description in the group
 *   the dial's turns are in, and the next turn clears it, so a described "23°" coming back as the
 *   wrist leaves the Qibla was lost; a dimmed dial says no turn.
 * - The dial, the caption and the detail are composed in every state, the detail hidden from a
 *   screen reader where it is not read, so that no change of state adds or removes a node either.
 */
@Composable
internal fun QiblaPage(feed: WatchFeed, active: Boolean) {
    val context = LocalContext.current
    val declination = feed.place.declination
    val bearing = feed.place.qiblaBearing
    val compass = remember(context, declination) { WristCompass(context, declination) }
    val feel = remember(context) { WatchHaptics(context) }
    val dial = remember(bearing) { WatchQibla(bearing) }
    var state by remember(compass, dial) { mutableStateOf(if (compass.hasSensor) NO_HEADING_YET else DialState.NoSensor) }
    var tilted by remember(compass, dial) { mutableStateOf(false) }

    LaunchedEffect(active, compass, dial) {
        if (!active || !compass.hasSensor) return@LaunchedEffect
        // After a spell away the last heading is stale and a compass mid-calibration is judged on
        // what it does now, as the phone resets on every start.
        dial.reset()
        state = NO_HEADING_YET
        tilted = false
        val level = Tilt.Level()
        compass.readings.collect { wrist ->
            val step = dial.onReading(wrist.reading)
            state = step.state
            tilted = level.tilted(wrist.tiltDegrees)
            if (step.buzz) feel.aligned()
        }
    }

    QiblaFace(qiblaScreen(state, tilted, feed), title = feed.words().qibla)
}

/** The dial, then the caption at y 141 and the detail under it, in the drawing's units. */
@Composable
private fun QiblaFace(screen: QiblaScreen, title: String) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val round = LocalRoundScreen.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fonts = LocalFontFamilyResolver.current
    val measurer = rememberTextMeasurer()
    val fontScale = density.fontScale
    val unitPx = with(density) { units.x(1f).toPx() }
    // The glyphs' own ink, as the watch's fonts draw them (Ruling 66 item 4), in the typefaces
    // Compose draws the page's text in.
    val ink = remember(fonts, unitPx) {
        QiblaInk({ weight -> fonts.resolve(WatchFonts.manrope, WatchFonts.weight(weight)).value as Typeface }, unitPx)
    }

    /** A dial letter at [grow]: its layout, to draw, and its ink about its middle, to keep clear of. */
    fun letterAt(letter: String, north: Boolean, grow: Float): Letter {
        val line = if (north) QiblaType.north else QiblaType.cardinal
        val size = line.units * grow
        val style = TextStyle(
            color = if (north) WatchColors.ink else WatchColors.mist,
            fontFamily = WatchFonts.manrope,
            fontWeight = WatchFonts.weight(if (north) 800 else 700),
            fontSize = units.sp(size, density),
        )
        val layout = measurer.measure(letter, style, maxLines = 1, softWrap = false, density = density)
        val glyphs = ink.line(letter, if (north) 800 else 700, size, rtl)
        return Letter(layout, grow, QiblaFit.letterInk(glyphs, width = layout.size.width / unitPx, penStart = layout.getLineLeft(0) / unitPx, grow = grow))
    }

    // The dial's letters, measured once for each language and font size: they grow with the
    // wearer's font size and never shrink (QiblaType.north and cardinal).
    val grow = QiblaType.north.range(fontScale).endInclusive / QiblaType.north.units
    val letters = remember(screen.cardinals, fontScale, units, ink, rtl) {
        screen.cardinals.mapIndexed { i, letter -> letterAt(letter, north = i == 0, grow = grow) }
    }

    val line = screen.captionLine
    // The caption's box: its lines, its width, its first baseline and its largest size
    // (QiblaType.captionBox, on the half anchor), from the lines its text takes in that box as
    // TextAutoSize will lay it out there: a caption over a dial that turns at its floor, in the
    // letters' room (Ruling 68 item 4), a title at its largest size.
    val box = remember(screen.caption, screen.captionAccent, screen.dialLetters, line, density, units, round, ink, rtl) {
        val turning = if (screen.dialLetters.isEmpty()) null else {
            val glyphs = ink.line(screen.caption, 800, line.units, rtl).boxes
            QiblaType.Turning(
                top = (glyphs.minOfOrNull { it.top } ?: 0f) / line.units,
                bottom = (glyphs.maxOfOrNull { it.bottom } ?: 0f) / line.units,
            ) { at -> screen.dialLetters.mapIndexed { i, letter -> letterAt(letter, north = i == 0, grow = at).ink } }
        }
        QiblaType.captionBox(line, fontScale, round, turning) { size, width ->
            measurer.measure(
                screen.caption,
                captionStyle(screen, line.maxLines).copy(fontSize = units.sp(size, density)),
                constraints = Constraints(maxWidth = with(density) { units.x(width).roundToPx() }.coerceAtLeast(0)),
                density = density,
            ).lineCount
        }
    }
    val captionStyle = captionStyle(screen, box.maxLines)

    // The detail (Ruling 66): the short form on one line, at the first size of its grid that keeps
    // clear of the turning letters, judged at their largest above the drawing's font size, and of
    // the ticks, on the half anchor (QiblaFit.detail), from the glyphs' ink.
    val detail = remember(screen.detail, screen.dialLetters, line, fontScale, ink, rtl) {
        QiblaFit.detail(line, fontScale, ink = { size -> ink.line(screen.detail, 600, size, rtl) }) { at ->
            screen.dialLetters.mapIndexed { i, letter -> letterAt(letter, north = i == 0, grow = at).ink }
        }.also { laid ->
            QiblaLog.line {
                val drawn = screen.dialLetters.mapIndexed { i, letter -> letterAt(letter, north = i == 0, grow = grow).ink }
                val digit = ink.line("0", 600, 10f, rtl = false).boxes.let { b -> (b.maxOf { it.bottom } - b.minOf { it.top }) / 10f }
                "detail '${screen.detail}' at font size $fontScale: size ${laid.size}, y ${"%.2f".format(laid.y)}, " +
                    "least letter gap ${"%.2f".format(QiblaFit.letterGap(laid.ink, drawn))} as drawn (${"%.2f".format(laid.letterGap)} at the judged size), " +
                    "ticks ${"%.2f".format(laid.tickGap)}, caption y ${"%.2f".format(box.y)}, a digit's ink ${"%.3f".format(digit)} em"
            }
        }
    }

    UnitsBox(Modifier.semantics { paneTitle = title }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (screen.dimmed) DIMMED else 1f }
                .semantics {
                    screen.dialSpoken?.let {
                        contentDescription = it
                        liveRegion = LiveRegionMode.Polite
                    }
                },
        ) { drawDial(screen, letters, detail.ink) }

        CaptionText(screen.caption, screen.captionDescription, line, captionStyle, box)
        // Composed in every state, empty or not, so that no change of state adds or removes a node.
        val detailRead = screen.detailRead
        BasicText(
            text = screen.detail,
            modifier = Modifier
                .atUnits(detail.y)
                .semantics { if (detailRead) contentDescription = screen.detailSpoken else hideFromAccessibility() },
            style = detailStyle(detail.size, units, density),
            overflow = QiblaType.DETAIL_OVERFLOW,
            softWrap = QiblaType.DETAIL_SOFT_WRAP,
            maxLines = QiblaType.detail.maxLines,
        )
    }
}

/**
 * The detail's look at [size] units: SemiBold, in mist, as `LineText` draws a line, centred on one
 * line at its own width, which `UnitsBox` centres across the dial, so its advance's middle is the
 * dial's, as [QiblaFit] measured it.
 */
internal fun detailStyle(size: Float, units: WatchUnits, density: Density): TextStyle = TextStyle(
    color = WatchColors.mist,
    fontFamily = WatchFonts.manrope,
    fontWeight = WatchFonts.weight(600),
    fontSize = units.sp(size, density),
    textAlign = TextAlign.Center,
)

/** The caption's look: ExtraBold, in ink, or in the accent facing the Qibla; a second line [QiblaType.TITLE_PITCH] below its first when it may take [maxLines] > 1. */
private fun captionStyle(screen: QiblaScreen, maxLines: Int) = TextStyle(
    color = if (screen.captionAccent) WatchColors.accent else WatchColors.ink,
    fontFamily = WatchFonts.manrope,
    fontWeight = WatchFonts.weight(800),
    textAlign = TextAlign.Center,
    lineHeight = if (maxLines > 1) (QiblaType.TITLE_PITCH / screen.captionLine.units).em else TextUnit.Unspecified,
)

/**
 * The caption, as `LineText` draws a line (its size with `TextAutoSize` from [Line.range]'s floor to
 * its [box]'s largest size, cut with an ellipsis after its box's `maxLines`), its first baseline at
 * its box's y (141 at the drawing's size, or 125 for a caption of two lines), in that box
 * ([QiblaType.captionBox]), and a polite live region, described by [description] when it has one.
 * Over a turning dial a caption of two lines has a box whose size is the one it is drawn at, so its
 * last line lies on the half anchor (141 at the drawing's size), to the pixel: Android sets the
 * pitch between them in whole pixels, rounded up (on emulator-5558 the last baseline of "Hold your
 * / wrist level" at 15 lies 0.30 units below 141, its debug log line says). A dimmed dial's title is
 * set from its largest size ([QiblaType.captionBox]). The box's size is given a hair above itself
 * ([SIZE_SLACK]), so that `StepBased`'s last step up to it, a sum of pixels, is never lost to a
 * float's rounding and the size drawn is the box's.
 */
@Composable
private fun CaptionText(text: String, description: String?, line: Line, style: TextStyle, box: QiblaType.CaptionBox) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    BasicText(
        text = text,
        modifier = Modifier
            .atUnits(box.y)
            .width(units.x(box.width))
            .semantics {
                liveRegion = LiveRegionMode.Polite
                description?.let { contentDescription = it }
            },
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = box.maxLines,
        autoSize = TextAutoSize.StepBased(
            minFontSize = units.sp(line.range(density.fontScale).start, density),
            maxFontSize = units.sp(box.size + SIZE_SLACK, density),
            stepSize = units.sp(QiblaType.CAPTION_STEP, density),
        ),
        // A debug build's layout log: the size drawn and the last line's baseline as laid out, the
        // device's word on the half anchor (a release build's QiblaLog does nothing).
        onTextLayout = { laid ->
            QiblaLog.line {
                val unitPx = with(density) { units.x(1f).toPx() }
                val drawn = with(density) { laid.layoutInput.style.fontSize.toPx() } / unitPx
                val last = box.y + (laid.getLineBaseline(laid.lineCount - 1) - laid.firstBaseline) / unitPx
                "caption '$text' at font size ${density.fontScale}: size ${"%.2f".format(drawn)} in a box of ${"%.2f".format(box.size)} " +
                    "on ${laid.lineCount} lines, baselines ${"%.2f".format(box.y)} to ${"%.2f".format(last)}, " +
                    "the half anchor ${"%.2f".format(QiblaFit.captionY(line, density.fontScale))}"
            }
        },
    )
}

/** A dial letter as measured, how far the wearer's font size grew it, and its glyphs' ink about its middle, in units. */
private class Letter(val layout: TextLayoutResult, val grow: Float, val ink: List<InkBox>)

/**
 * `qiblaDial(100, 100, 92, heading, bearing, A, 1.05)`, in the drawing's units across the screen's
 * square: the halo and the lit rim when facing, else the hairline rim; the 24 ticks and the letters
 * turned by −heading, never mirrored, each faded only where it would come within LETTER_AIR of the
 * detail's [detail] ink (Ruling 66 item 5, idle wherever the detail's size kept the air); then the
 * needle and its hub, and the Kaaba's marker on the rim at bearing − heading, upright.
 */
private fun DrawScope.drawDial(screen: QiblaScreen, letters: List<Letter>, detail: List<InkBox>) {
    val side = min(size.width, size.height)
    val unit = side / WatchUnits.ACROSS
    val centre = Offset(size.width / 2, size.height / 2)
    fun at(radius: Float, degrees: Double): Offset {
        val a = Math.toRadians(degrees - 90.0)
        return Offset(centre.x + (radius * unit * cos(a)).toFloat(), centre.y + (radius * unit * sin(a)).toFloat())
    }
    val heading = screen.heading

    if (screen.lit) {
        drawCircle(WatchColors.accent.copy(alpha = HALO_ALPHA), radius = RIM * unit, center = centre, style = Stroke(width = 9f * K * unit))
    }
    drawCircle(
        if (screen.lit) WatchColors.accent else WatchColors.hairline,
        radius = RIM * unit,
        center = centre,
        style = Stroke(width = (if (screen.lit) 2.6f else 1.4f) * K * unit),
    )

    for (i in 0 until QiblaDialMeasures.TICKS) {
        val degrees = i * 360.0 / QiblaDialMeasures.TICKS - heading
        val major = i % (QiblaDialMeasures.TICKS / 4) == 0
        drawLine(
            if (major) WatchColors.mist else WatchColors.slate,
            start = at(RIM - QiblaDialMeasures.TICK_FROM * K, degrees),
            end = at(RIM - QiblaDialMeasures.TICK_TO * K, degrees),
            strokeWidth = (if (major) 1.4f else 1f) * K * unit,
            cap = StrokeCap.Butt,
        )
    }

    letters.forEachIndexed { i, letter ->
        val p = at(QiblaType.LETTER_RADIUS, i * 90.0 - heading)
        val baseline = p.y + QiblaDialMeasures.LETTER_DROP * K * letter.grow * unit
        val alpha = QiblaFit.letterAlpha(
            WatchUnits.ACROSS / 2 + (p.x - centre.x) / unit,
            WatchUnits.ACROSS / 2 + (p.y - centre.y) / unit,
            detail,
            letter.ink,
        )
        if (alpha > 0f) drawText(letter.layout, topLeft = Offset(p.x - letter.layout.size.width / 2f, baseline - letter.layout.firstBaseline), alpha = alpha)
    }

    val toKaaba = screen.bearing - heading
    if (screen.needle) {
        drawLine(WatchColors.accent, start = centre, end = at(RIM - 26f * K, toKaaba), strokeWidth = 3f * K * unit, cap = StrokeCap.Round)
        drawCircle(WatchColors.accent, radius = QiblaDialMeasures.HUB * K * unit, center = centre)
    }
    if (screen.marker) drawKaaba(at(RIM, toKaaba), QiblaDialMeasures.KAABA * K * unit)
}

/** `cube()`: the Kaaba, an accent square with rounded corners and its dark band, in its halo, upright. */
private fun DrawScope.drawKaaba(at: Offset, side: Float) {
    val half = side / 2
    drawCircle(WatchColors.accent.copy(alpha = KAABA_HALO_ALPHA), radius = side * QiblaDialMeasures.KAABA_HALO, center = at)
    drawRoundRect(WatchColors.accent, topLeft = Offset(at.x - half, at.y - half), size = Size(side, side), cornerRadius = CornerRadius(side * 0.16f))
    drawRect(WatchColors.black, topLeft = Offset(at.x - half, at.y - half + side * 0.26f), size = Size(side, side * 0.17f))
}

/** The dial before its first reading: no needle, as for a heading that is not a number. */
private val NO_HEADING_YET = DialState.Searching(Double.NaN, guidance = null)

/** The dial's rim, on the screen's edge inside the bezel, and the drawing's scale for it ([QiblaDialMeasures], `qiblaDial(…, 92, …, 1.05)`). */
private const val RIM = QiblaDialMeasures.RIM
private const val K = QiblaDialMeasures.K

private const val DIMMED = 0.4f
private const val HALO_ALPHA = 0.18f
private const val KAABA_HALO_ALPHA = 0.22f

/**
 * How far above its box's size, in units, the caption's `TextAutoSize` may go: far below a step
 * ([QiblaType.CAPTION_STEP]), so no larger step is reached, and far above a float's rounding of
 * the pixels it sums (about 10⁻⁵ of a unit), so the box's own size, a step, is.
 */
private const val SIZE_SLACK = 1e-3f
