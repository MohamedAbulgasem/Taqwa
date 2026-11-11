package world.taqwa.app.wear.tile

import androidx.compose.ui.unit.Density
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.FontSetting
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.expression.RequiresSchemaVersion
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.app.EXTRA_PAGE
import world.taqwa.app.wear.app.Page
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.WearMainActivity
import world.taqwa.app.wear.app.farEdge
import world.taqwa.app.wear.app.trackingFor
import world.taqwa.app.wear.today.RowModel
import world.taqwa.app.wear.today.RowStatus
import world.taqwa.app.wear.today.TodayLayout

/**
 * What the Tile is drawn on, from the request's `DeviceParameters`: the screen in dp, its density
 * and the wearer's font scale, whether it is round, and whether the renderer moves dynamic values
 * (schema 1.2 and later, [dynamic]); below that, the line says the start's time instead (call 6).
 * [hides] is whether it can also hide an element by the platform's clock (schema 1.3 and later,
 * `Modifiers.setVisible`), which shows an entry's `after` once its change has come.
 */
internal data class TileScreen(
    val widthDp: Int,
    val heightDp: Int,
    val density: Float,
    val fontScale: Float,
    val round: Boolean,
    val dynamic: Boolean,
    val hides: Boolean = dynamic,
) {
    val units: WatchUnits get() = WatchUnits.of(widthDp, heightDp)

    /** The screen's pixels to a unit of the drawing: a text of so many units is drawn that many times this many pixels tall. */
    val pixelsPerUnit: Float get() = units.dp * density

    companion object {
        fun of(device: DeviceParametersBuilders.DeviceParameters): TileScreen {
            val schema = device.rendererSchemaVersion
            return TileScreen(
                widthDp = device.screenWidthDp,
                heightDp = device.screenHeightDp,
                density = device.screenDensity,
                fontScale = device.fontScale,
                round = device.screenShape != DeviceParametersBuilders.SCREEN_SHAPE_RECT,
                dynamic = schema.major > 1 || (schema.major == 1 && schema.minor >= 200),
                hides = schema.major > 1 || (schema.major == 1 && schema.minor >= 300),
            )
        }
    }
}

/**
 * How the watch will draw a Tile's text, measured on the watch before the Tile is sent
 * (`PaintMeasure`), since ProtoLayout lays a line out without saying how it came out: [width], how
 * wide [text] is drawn at [units] of the drawing, in units, in the watch's system font at the
 * drawing's [weight], with [tabular] figures or not; and [extent], how far its line reaches above
 * and below its baseline in every font it is set in. A line in Arabic script is set in the system's
 * Naskh, whose line is 1.70 of its size tall to Roboto's 1.17, and the renderer spaces a line by its
 * fallback fonts too, so a box sized for Roboto cuts an Arabic line (review round 1).
 */
internal interface TileMeasure {
    fun width(text: String, units: Float, weight: Int, tabular: Boolean): Float

    fun extent(text: String, units: Float, weight: Int): TextExtent
}

/**
 * A line's room in units: [above] and [below] its baseline (its fonts' ascent and descent, which
 * set where its baseline falls in its box), and the most it may be drawn [tall] (with the fonts' own
 * padding, should the renderer add it).
 */
internal data class TextExtent(val above: Float, val below: Float, val tall: Float) {

    /** The room of a line that may be set in this one's fonts or [other]'s. */
    fun union(other: TextExtent): TextExtent {
        val above = maxOf(above, other.above)
        val below = maxOf(below, other.below)
        return TextExtent(above, below, maxOf(tall, other.tall, above + below))
    }
}

/**
 * How far a pip's middle lies above its row's baseline, as a share of the row's size: half the
 * system font's ascent less its descent (Roboto's 0.93 and 0.24 of the em), where `gTile` has it.
 */
private const val PIP_ABOVE_BASELINE = 0.35f

/**
 * What a text's box keeps beyond its line, in units, each way: the renderer rounds a box to whole
 * pixels, so a box exactly a line tall can cut its last row of pixels.
 */
private const val LINE_ROOM = 0.6f

/**
 * What a measured line keeps to spare, in units: the renderer rounds a box to whole pixels, and a
 * text view asks a pixel more than its text's advance. On emulator-5558 "Jumuʿah", measured to
 * fill its box exactly, was drawn "Jumuʿ…", and "Update Taqwa on your", 0.3 units inside its line,
 * "Update Taqwa on y…".
 */
internal const val MEASURE_SLACK = 1.5f

/** The highest the update's lines may reach (each line's own top): below Isha's row, whose glyphs end above it. */
internal const val UPDATE_TOP = 168f

/** The baseline of the update's last line when it takes two, above the screen's narrowing bottom. */
internal const val UPDATE_LAST_BASELINE = 191f

/**
 * How far apart the update's two lines stand, as a share of their size: as their fonts set them,
 * at least [UPDATE_PITCH] and at most [UPDATE_MAX_PITCH] (Tasbeeh's own rule for a phrase of his:
 * the watch's Naskh sets its lines 1.70 apart, Roboto 1.17).
 */
internal const val UPDATE_PITCH = 1.2f
internal const val UPDATE_MAX_PITCH = 1.55f

/** How finely the update's size and its one line's baseline are searched, in units. */
private const val UPDATE_STEP = 0.5f

/** The update as it is drawn: its [lines] at [size] units, each at its [baselines] entry. */
internal data class UpdateFit(val size: Float, val lines: List<String>, val baselines: List<Float>)

/**
 * "Update Taqwa on your watch" in the city's place (call 5), fitted on the widths and line rooms the
 * watch measures ([width], [extent]), at the largest size from the top of [sizes] down to its
 * floor that it fits, in steps of half a unit. At each size it is first one line, at the lowest
 * baseline from the city's 184 upwards at which it fits the chord there, but never so high that its
 * line reaches above [UPDATE_TOP]; then two lines, the second at 191 and the first its line's pitch
 * above it ([UPDATE_PITCH] to [UPDATE_MAX_PITCH]), as many words on the first as fit it, each line
 * fitting its own chord and the first's top no higher than [UPDATE_TOP]. Every line is checked
 * against the chord at the baseline it is drawn at, with [MEASURE_SLACK] to spare. What fits at no
 * size is set at the floor in two lines, and the renderer ends the second in an ellipsis.
 */
internal fun fitUpdate(
    text: String,
    sizes: ClosedFloatingPointRange<Float>,
    round: Boolean,
    width: (text: String, units: Float) -> Float,
    extent: (text: String, units: Float) -> TextExtent,
): UpdateFit {
    val city = TodayLayout.list(List(6) { it == 1 }).city
    fun room(baseline: Float, size: Float) = WatchUnits.chordUnits(farEdge(baseline, centred = false, line = TileType.update, size = size), round) - MEASURE_SLACK
    fun fitsAt(line: String, baseline: Float, size: Float) = width(line, size) <= room(baseline, size) + FIT_EPSILON
    fun oneAt(size: Float): UpdateFit? {
        val highest = minOf(city, UPDATE_TOP + extent(text, size).above)
        var baseline = city
        while (baseline > highest + FIT_EPSILON) {
            if (fitsAt(text, baseline, size)) return UpdateFit(size, listOf(text), listOf(baseline))
            baseline -= UPDATE_STEP
        }
        return if (fitsAt(text, highest, size)) UpdateFit(size, listOf(text), listOf(highest)) else null
    }
    val words = text.split(' ').filter { it.isNotEmpty() }
    fun twoAt(size: Float): Pair<UpdateFit, Boolean> {
        val own = extent(text, size).let { (it.above + it.below) / size }
        val second = UPDATE_LAST_BASELINE
        val first = second - own.coerceIn(UPDATE_PITCH, UPDATE_MAX_PITCH) * size
        var split = 1
        while (split < words.size && fitsAt(words.take(split + 1).joinToString(" "), first, size)) split++
        val lines = listOf(words.take(split).joinToString(" "), words.drop(split).joinToString(" ")).filter { it.isNotEmpty() }
        if (lines.size < 2) return UpdateFit(size, lines, listOf(city)) to false
        val fits = first - extent(lines[0], size).above >= UPDATE_TOP - FIT_EPSILON && fitsAt(lines[0], first, size) && fitsAt(lines[1], second, size)
        return UpdateFit(size, lines, listOf(first, second)) to fits
    }
    var size = sizes.endInclusive
    while (size >= sizes.start - FIT_EPSILON) {
        oneAt(size)?.let { return it }
        val (fit, fits) = twoAt(size)
        if (fits) return fit
        size -= UPDATE_STEP
    }
    return twoAt(sizes.start).first
}

/** The countdown line's baseline in `gTile`, in units. */
private const val LINE_Y = 40f

/** The label's tracking in `gTile`, in units at the label's drawn size. */
private const val LABEL_TRACKING = 1.3f

/**
 * The room between the label and the countdown: `gTile`'s space before "in", at the countdown's
 * size, with the label's tracking after its last letter, 20 px on the emulator's 454. Every word
 * the countdown shows stands this far from the label, its room aligned towards it.
 */
internal const val LINE_GAP = 7.5f

/** How finely the countdown line's size is searched, in units. */
private const val LINE_STEP = 0.5f

/** How near a measure may come to its room and still fit, in units: float rounding, never a rule. */
private const val FIT_EPSILON = 1e-3f

/**
 * The countdown line as it is drawn: the label at [labelSize] and the countdown at
 * [countdownSize], their tracking and gap [scale] of the drawing's, and [widest], the widest of the
 * words it can show. The countdown's static value is [staticValue] and its room is as wide as
 * [room]: on a line that moves, the start's time held [MEASURE_SLACK] wider than [widest]
 * ([heldClock]), and that and a no-break space more; on one that does not, [widest] both. The room
 * runs [overrun] units past [widest], and a spacer as wide stands on the label's outer side. [fits]
 * is false when even the floor runs past the chord.
 */
internal data class LineFit(
    val labelSize: Float,
    val countdownSize: Float,
    val scale: Float,
    val widest: String,
    val fits: Boolean,
    val staticValue: String = widest,
    val room: String = widest,
    val overrun: Float = 0f,
)

/**
 * The spaces the countdown's static value is held wide with ([heldClock]): the figure space, a
 * digit wide, and the narrower no-break space for what is left. Neither is a space Android's line
 * breaking may drop at a line's end (its `isLineEndSpace` leaves U+2007 out, and U+00A0 is not
 * one), so a text view measures and draws both.
 */
private const val FIGURE_SPACE = '\u2007'
internal const val NO_BREAK_SPACE = '\u00A0'

/**
 * [text] led by a mark of the direction [rtl] gives (U+200E, or U+200F right to left), so that a
 * text view sets it in that direction whatever its letters: a time of digits alone has none.
 */
internal fun directed(text: String, rtl: Boolean): String = (if (rtl) '\u200F' else '\u200E') + text

/**
 * [clock], the start's time, at least [target] wide as [width] measures it, and less than a
 * no-break space wider: [directed] in the feed's direction ([rtl]), then figure spaces while they
 * keep it within [target], then no-break spaces until it reaches it. Set in that direction, the
 * spaces fall after the time, on the side away from the name, so it is drawn as the start's time
 * alone (Ruling 65 item 2), as wide as the words it stands for and the slack [fitLine] asks.
 */
internal fun heldClock(clock: String, rtl: Boolean, target: Float, width: (String) -> Float): String {
    var held = directed(clock, rtl)
    while (true) {
        val wider = held + FIGURE_SPACE
        if (width(wider) > target || width(wider) <= width(held)) break
        held = wider
    }
    while (width(held) < target - FIT_EPSILON) {
        val wider = held + NO_BREAK_SPACE
        if (width(wider) <= width(held)) break
        held = wider
    }
    return held
}

/**
 * The countdown line at 40 fitted on the widths the watch measures ([measure]): the label in the
 * accent at 800 with its tracking, `gTile`'s gap, and the countdown's room at 700 in tabular
 * figures, at the largest size from the top of `TileType.countdown`'s range under [fontScale]
 * down to its floor at which it fits the round screen's chord at the countdown's top, with
 * [MEASURE_SLACK] to spare. The room is the widest of [words] (every minute an entry can show, and
 * the start's time) or, on a line that moves, which names its start's time [clock], that time held
 * [MEASURE_SLACK] wider than them ([heldClock], in the feed's direction [rtl]) and a no-break space
 * more: a measured width keeps that to spare against the width a text view draws it at, which a
 * review's probe on the Wear OS 6 emulator found a pixel or two off it either way. What fits the
 * chord is what is seen, the label, its gap and the widest word: the room's overrun past that word
 * is empty, and the line balances it with a spacer as wide on the label's outer side, so the label
 * and the widest word are centred as `gTile` centres them, the two overruns reaching past the
 * chord into its inset ([TileLayout]'s `line`). The
 * label, its tracking and the gap shrink with the countdown, so the line keeps its drawn
 * proportions. At its top size it is `gTile`'s line; "MAGHRIB in under a minute", French and
 * Indonesian countdowns take less (review round 1).
 */
internal fun fitLine(
    label: String,
    words: List<String>,
    fontScale: Float,
    round: Boolean,
    measure: TileMeasure,
    clock: String? = null,
    rtl: Boolean = false,
): LineFit {
    require(words.isNotEmpty()) { "the words the countdown can show" }
    val countdownTop = TileType.countdown.range(fontScale).endInclusive
    val labelTop = TileType.label.range(fontScale).endInclusive
    val letters = label.codePointCount(0, label.length)
    fun at(countdownSize: Float): LineFit {
        val scale = countdownSize / countdownTop
        val labelSize = labelTop * scale
        fun width(text: String) = measure.width(text, countdownSize, 700, tabular = true)
        val (widest, widestWidth) = words.map { it to width(it) }.maxBy { it.second }
        val staticValue = clock?.let { heldClock(it, rtl, widestWidth + MEASURE_SLACK, ::width) } ?: widest
        val room = if (clock == null) widest else staticValue + NO_BREAK_SPACE
        val drawn = measure.width(label, labelSize, 800, tabular = false) + letters * trackingFor(label, LABEL_TRACKING) * scale + LINE_GAP * scale + widestWidth
        val chord = WatchUnits.chordUnits(farEdge(LINE_Y, centred = false, line = TileType.countdown, size = countdownSize), round) - MEASURE_SLACK
        val overrun = (width(room) - widestWidth).coerceAtLeast(0f)
        return LineFit(labelSize, countdownSize, scale, widest, drawn <= chord + FIT_EPSILON, staticValue, room, overrun)
    }
    var size = countdownTop
    while (size >= TileType.countdown.floor - FIT_EPSILON) {
        val fit = at(size)
        if (fit.fits) return fit
        size -= LINE_STEP
    }
    return at(TileType.countdown.floor)
}

/**
 * The Tile laid out in ProtoLayout (`gTile`, in the drawing's 200 units across the screen's
 * shorter side): the countdown line at 40, the day's six rows from 64, 21 units apart and Sunrise
 * 17, each with its pip at 46, its name from 56 and its time ending at 150, and the city at 184.
 * Where the phone hides Sunrise the five rows share the six rows' room, 24.4 units apart, from 64
 * to 161.6, with the line and the city where they were. The rows' baselines are Today's list's
 * (`TodayLayout.list`), whose end is the Tile.
 *
 * The Tile is a stack the drawing's 200 units tall, centred on the screen. Each line is a child of
 * it, set by its own top, in a box as tall as its line is in the fonts it is drawn in
 * ([TileMeasure.extent]) and placed so that its baseline is the drawing's: so a line in Arabic
 * script, whose Naskh line is 1.70 of its size tall, may reach into its neighbours' room, as the
 * glyphs of the drawing's rows do, and is never cut by a box sized for Roboto (review round 1).
 *
 * Positions come from fixed widths, spacers and padding; no START or END alignment is used, so the
 * one thing the watch's own direction can turn is a Row's order of children ([inTileOrder]). What
 * is seen is the phone's direction (`feed.ui.rtl`), whatever the watch's language.
 *
 * The Tile is drawn in the watch's system font (call 7): ProtoLayout cannot load one.
 */
internal object TileLayout {

    /**
     * The renderer's dynamic nodes for one layout, measured on the emulator (P3.8 Step 0), which
     * charges only the operations. Every layout is held to it counting its fixed values too, as
     * Google documents ("Wear OS considers constant expressions to be dynamic expressions, too"),
     * so a renderer that counts them binds it as well (review round 1).
     */
    const val QUOTA = 200

    /**
     * The most a whole timeline may weigh as the protobuf it is sent as. It goes to the renderer
     * in one one-way binder call (tiles 1.6.2's `TileCallback.updateTileData` transacts with
     * `FLAG_ONEWAY` and no reply), which lands in the renderer's async space: half of its
     * process's binder buffer of about a megabyte, shared by every one-way call in flight to it.
     * It is the project's own margin under that, not a limit the platform was seen to set, and
     * [fitted] keeps every timeline the Tile sends within it, for every feed the phone writes and,
     * by its last resort, whatever the city's length.
     */
    const val MAX_TIMELINE_BYTES = 200_000

    /**
     * The last entry's end: none. The renderer shows an entry only while its validity holds the
     * time (start ≤ now < end), and an entry given a start alone has an end of 0, which never holds
     * it, so it was shown only as the entry closest in time, the last real one (review round 1).
     * An end at the last instant there is keeps the notice from its start on, and never before.
     */
    const val OPEN_END = Long.MAX_VALUE

    private const val ACCENT = 0xFFF0B429.toInt()
    private const val HALO = 0x33F0B429
    private const val INK = 0xFFF1F3F1.toInt()
    private const val MIST = 0xFF8C948F.toInt()
    private const val SLATE = 0xFF5A625D.toInt()
    private const val HAIRLINE = 0xFF232825.toInt()
    private const val BLACK = 0xFF000000.toInt()

    // gTile's figures, in units.
    private const val HALO_DIAMETER = 11.2f
    private const val DISC_DIAMETER = 6.6f
    private const val PASSED_DIAMETER = 4.8f
    private const val UPCOMING_DIAMETER = 5.9f
    private const val UPCOMING_RING = 1.3f
    private const val NOTICE_Y = 100f

    /**
     * The timeline as it is sent ([fitted]): its [entries] as planned, the [timeline] they are laid
     * out as, and what that weighs as the protobuf it is sent as, [bytes].
     */
    class Fitted(val entries: List<TileEntry>, val timeline: TimelineBuilders.Timeline, val bytes: Int)

    /**
     * The Tile's timeline for [feed] at [now] as it is sent: [TilePlan.entries] laid out for
     * [screen], in the feed's direction on a host whose own is [hostRtl], within
     * [MAX_TIMELINE_BYTES]. Where the plan's whole moving part ([TilePlan.MOVING_SECONDS]) makes
     * it heavier than that, the moving part ends where one of its entries begins, the latest at
     * which the timeline fits ([TilePlan.entries]' `movingUntil`), and the entries from there say
     * the start's time (call 6), as past the moving hours: far north, where five changes may
     * fall within the hour and each brings its shorter entries, in a language of two- or three-byte
     * letters, on 12 hours or with a long city (review round 5: 225 KB in Bengali on 12 hours on
     * Tromsø's heaviest night). The Tile is then asked for again while it still moves
     * ([TilePlan.freshnessMillis]). The entries before the end are the same in every such plan,
     * so each entry is laid out once.
     *
     * The last resort (Ruling 68 item 2 (b)): where even nothing moving is too heavy, which takes
     * a city far longer than any the phone writes (its worst is 300 bytes; 6,000 made 308 KB at
     * 14:58), the city is cut to half its letters, and again, marked with an ellipsis, until the
     * timeline fits, and the moving part is fitted again for the city that is cut. The city is the
     * one text of the Tile no list of the phone's bounds: the names, the countdown words and the
     * notices are the phone's own strings.
     */
    fun fitted(feed: WatchFeed, newerVersionSeen: Boolean, now: Long, screen: TileScreen, hostRtl: Boolean, packageName: String, measure: TileMeasure): Fitted {
        var sent = movingFitted(feed, newerVersionSeen, now, screen, hostRtl, packageName, measure)
        var letters = feed.place.city
        while (sent.bytes > MAX_TIMELINE_BYTES && letters.isNotEmpty()) {
            letters = letters.substring(0, letters.offsetByCodePoints(0, letters.codePointCount(0, letters.length) / 2))
            val city = if (letters.isEmpty()) "" else "$letters…"
            sent = movingFitted(feed.copy(place = feed.place.copy(city = city)), newerVersionSeen, now, screen, hostRtl, packageName, measure)
        }
        return sent
    }

    /** [fitted] but for the last resort: the moving part ended where the timeline fits, or nothing moving. */
    private fun movingFitted(feed: WatchFeed, newerVersionSeen: Boolean, now: Long, screen: TileScreen, hostRtl: Boolean, packageName: String, measure: TileMeasure): Fitted {
        val done = HashMap<TileEntry, Laid>()
        fun laidOut(entries: List<TileEntry>) = entries.map { done.getOrPut(it) { laid(it, screen, feed.ui.rtl, hostRtl, packageName, measure) } }
        var entries = TilePlan.entries(feed, newerVersionSeen, now)
        var timeline = laidOut(entries)
        while (timeline.sumOf { it.bytes } > MAX_TIMELINE_BYTES) {
            val last = entries.indexOfLast { it.moves }
            if (last < 0) break
            entries = TilePlan.entries(feed, newerVersionSeen, now, movingUntil = entries[last].from)
            timeline = laidOut(entries)
        }
        return Fitted(entries, timelineOf(timeline), timeline.sumOf { it.bytes })
    }

    /**
     * Every entry of [entries] laid out for [screen], in the feed's direction ([rtl]) on a host whose
     * own is [hostRtl], each valid from its `from` to its `until`, and the last, the notice, from its
     * `from` on ([OPEN_END]).
     */
    fun timeline(entries: List<TileEntry>, screen: TileScreen, rtl: Boolean, hostRtl: Boolean, packageName: String, measure: TileMeasure): TimelineBuilders.Timeline =
        timelineOf(entries.map { laid(it, screen, rtl, hostRtl, packageName, measure) })

    /** One entry of a timeline, and its weight in the timeline's protobuf ([weight]). */
    private class Laid(val entry: TimelineBuilders.TimelineEntry, val bytes: Int)

    private fun timelineOf(entries: List<Laid>): TimelineBuilders.Timeline {
        val timeline = TimelineBuilders.Timeline.Builder()
        for (laid in entries) timeline.addTimelineEntry(laid.entry)
        return timeline.build()
    }

    /**
     * [entry] laid out as one entry of the timeline, valid from its `from` to its `until`, or from
     * its `from` on ([OPEN_END]), with its weight. The layout's own bytes come from
     * `Layout.toByteArray()`, the one public way to its protobuf, which protolayout 1.4.2 still
     * marks experimental; `theWeightIsThatOfTheProtobufSent` holds what it gives to the timeline's
     * own size.
     */
    @androidx.annotation.OptIn(markerClass = [ProtoLayoutExperimental::class])
    private fun laid(entry: TileEntry, screen: TileScreen, rtl: Boolean, hostRtl: Boolean, packageName: String, measure: TileMeasure): Laid {
        val startMillis = entry.from * 1_000L
        val endMillis = entry.until?.let { it * 1_000L } ?: OPEN_END
        val layout = LayoutElementBuilders.Layout.Builder().setRoot(entry(entry, screen, rtl, hostRtl, packageName, measure)).build()
        val built = TimelineBuilders.TimelineEntry.Builder()
            .setValidity(TimelineBuilders.TimeInterval.Builder().setStartMillis(startMillis).setEndMillis(endMillis).build())
            .setLayout(layout)
            .build()
        return Laid(built, weight(layout.toByteArray().size, startMillis, endMillis))
    }

    /**
     * What an entry adds to the timeline's protobuf, its layout [layoutBytes] long and valid from
     * [startMillis] to [endMillis]: the timeline's field for it (`timeline_entries`, 1) holds its
     * validity (field 1, a `TimeInterval` of two varints, fields 1 and 2) and its layout (field
     * 2), each a tag and a length before it. Neither time is ever 0, which the protobuf would leave
     * out. `theWeightIsThatOfTheProtobufSent` holds this to the protobuf itself.
     */
    private fun weight(layoutBytes: Int, startMillis: Long, endMillis: Long): Int {
        val validity = 1 + varintBytes(startMillis) + 1 + varintBytes(endMillis)
        val entry = 1 + varintBytes(validity.toLong()) + validity + 1 + varintBytes(layoutBytes.toLong()) + layoutBytes
        return 1 + varintBytes(entry.toLong()) + entry
    }

    /** The bytes a protobuf varint takes for [value], seven bits to a byte. */
    private fun varintBytes(value: Long): Int {
        var bytes = 1
        var rest = value ushr 7
        while (rest != 0L) {
            bytes++
            rest = rest ushr 7
        }
        return bytes
    }

    /**
     * One entry: the line, the rows and the city (or the update), or the notice alone. A tap opens
     * Today. [measure] says how wide and how tall the texts will be drawn.
     *
     * An entry the renderer may still show once its change has come ([TileEntry.after]) draws both
     * what comes before the change and what comes after it, each shown only on its own side of it
     * by the platform's clock, on a renderer that can hide an element ([TileScreen.hides]): the
     * renderer moves on to the next entry late (review round 3), and so a start lights its row and
     * counts down to the next one to the second. A renderer that cannot hide one draws what comes
     * before, whose line says the start's time once it has come, until it moves on.
     */
    fun entry(entry: TileEntry, screen: TileScreen, rtl: Boolean, hostRtl: Boolean, packageName: String, measure: TileMeasure): LayoutElement {
        val after = entry.after?.takeIf { screen.dynamic && screen.hides }
        val switchAt = entry.until
        if (after == null || switchAt == null) return root(content(entry, screen, rtl, hostRtl, measure), screen, packageName)
        // Schema 1.3 by the request's own version (TileScreen.hides, above), which lint cannot see.
        @Suppress("ProtoLayoutMinSchema")
        val both = Box.Builder()
            .addContent(shownWhile(content(entry, screen, rtl, hostRtl, measure), TileCountdown.before(switchAt), shown = true))
            .addContent(shownWhile(content(after, screen, rtl, hostRtl, measure), TileCountdown.from(switchAt), shown = false))
            .build()
        return root(both, screen, packageName)
    }

    /** What [entry] draws: the line, the rows and the city (or the update), or the notice alone. */
    private fun content(entry: TileEntry, screen: TileScreen, rtl: Boolean, hostRtl: Boolean, measure: TileMeasure): LayoutElement {
        val next = entry.next
        if (next == null || entry.notice != null) return noticeBox(entry.notice.orEmpty(), screen)
        val u = screen.units
        val rows = entry.rows
        val geometry = TodayLayout.list(rows.map { it.small })
        val timeEnd = timeEnd(rows, geometry.baselines, screen, measure)
        val stack = Box.Builder()
            .setWidth(dp(u.x(WatchUnits.ACROSS).value))
            .setHeight(dp(u.x(WatchUnits.ACROSS).value))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
        stack.addContent(line(next, screen, rtl, hostRtl, measure))
        rows.forEachIndexed { i, row -> stack.addContent(row(row, screen, rtl, hostRtl, geometry.baselines[i], timeEnd, measure)) }
        stack.addContent(if (entry.update != null) update(entry.update, screen, measure) else city(entry.city, screen, geometry.city, measure))
        return stack.build()
    }

    /**
     * [content] shown only while [visible] holds by the platform's clock, and [shown] by a
     * renderer that cannot bind it. A hidden element keeps its room, draws nothing and is not
     * read (`Modifiers.setVisible`, schema 1.3, still marked experimental in protolayout 1.4.2;
     * [TileScreen.hides] keeps it from any older renderer, which would draw both).
     */
    @androidx.annotation.OptIn(markerClass = [ProtoLayoutExperimental::class])
    @RequiresSchemaVersion(major = 1, minor = 300)
    private fun shownWhile(content: LayoutElement, visible: DynamicBuilders.DynamicBool, shown: Boolean): LayoutElement =
        Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setVisible(TypeBuilders.BoolProp.Builder(shown).setDynamicValue(visible).build())
                    .build(),
            )
            .addContent(content)
            .build()

    /** A line alone, centred: the notice when nothing is left to count down to, or the watch's own line with no feed. */
    fun noFeed(line: String, screen: TileScreen, packageName: String): LayoutElement = root(noticeBox(line, screen), screen, packageName)

    /** [line] centred in the round screen's chord at its middle, in three lines at most. */
    private fun noticeBox(line: String, screen: TileScreen): LayoutElement {
        val u = screen.units
        val type = TileType.notice
        val size = type.range(screen.fontScale).endInclusive
        val half = type.maxLines * size * 1.2f / 2
        val width = WatchUnits.chordUnits(NOTICE_Y - half, screen.round)
        val text = Text.Builder()
            .setText(line)
            .setFontStyle(style(size, 600, INK, screen))
            .setMaxLines(type.maxLines)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .build()
        return Box.Builder().setWidth(dp(u.x(width).value)).setHeight(wrap()).addContent(text).build()
    }

    /** The whole Tile: [content] centred on the screen, and a tap anywhere opening the app on Today. */
    private fun root(content: LayoutElement, screen: TileScreen, packageName: String): LayoutElement {
        val open = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MAIN_ACTIVITY)
                    .addKeyToExtraMapping(EXTRA_PAGE, ActionBuilders.AndroidStringExtra.Builder().setValue(Page.TODAY.name).build())
                    .build(),
            )
            .build()
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId(OPEN_ID).setOnClick(open).build())
                    .build(),
            )
            .addContent(content)
            .build()
    }

    /**
     * The countdown line: the next start's name in the accent and its countdown, moving each
     * minute, read as one sentence ("Asr in 1 hour 12 minutes, at 16:10"), fitted inside the round
     * screen's chord ([fitLine]) with the words set in the countdown's room against the name
     * ([towardsTheName]), and centred on the name and the widest word: what the room runs past that
     * word is matched by a spacer before the name ([LineFit.overrun]), and the line's box is the
     * chord and both of them. Past the timeline's moving hours ([TilePlan.MOVING_SECONDS]), on a
     * renderer that cannot move them, and where even the line's floor cannot hold its words
     * whole, the line draws the start's time in the countdown's place and says the prayer and its
     * time (call 6): never an ellipsis.
     *
     * The moving countdown and its sentence need schema 1.2 ([TileScreen.dynamic]), and their
     * static values are call 6's form too, "16:10" and "Asr, 16:10", true however late they are
     * shown: a renderer that drops a dynamic value (over its quota, on an evaluation error, or in
     * the frame before it binds it) keeps the static one (Ruling 65 item 2, [movingCountdown]).
     */
    private fun line(next: TileNext, screen: TileScreen, rtl: Boolean, hostRtl: Boolean, measure: TileMeasure): LayoutElement {
        val u = screen.units
        val movingWords = (next.minutes.map { it.text } + next.clock).distinct()
        val movingFit = if (screen.dynamic && next.minutes.isNotEmpty()) {
            fitLine(next.label, movingWords, screen.fontScale, screen.round, measure, clock = next.clock, rtl = rtl).takeIf { it.fits }
        } else {
            null
        }
        val moving = movingFit != null
        val words = if (moving) movingWords else listOf(next.clock)
        val fit = movingFit ?: fitLine(next.label, words, screen.fontScale, screen.round, measure)
        val labelSlot = Slot.at(LINE_Y, measure.extent(next.label, fit.labelSize, 800))
        val countdownSlot = Slot.at(LINE_Y, words.map { measure.extent(it, fit.countdownSize, 700) }.reduce(TextExtent::union))
        val top = minOf(labelSlot.top, countdownSlot.top)
        val bottom = maxOf(labelSlot.bottom, countdownSlot.bottom)
        val label = Text.Builder()
            .setText(next.label)
            .setFontStyle(style(fit.labelSize, 800, ACCENT, screen, tracking = trackingFor(next.label, LABEL_TRACKING) * fit.scale / fit.labelSize))
            .setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .setModifiers(centred(top, bottom, labelSlot.centre, u))
            .build()
        val countdown = Text.Builder()
            .setFontStyle(style(fit.countdownSize, 700, INK, screen, tabular = true))
            .setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .setModifiers(centred(top, bottom, countdownSlot.centre, u))
        // Schema 1.2 by the request's own version (TileScreen.dynamic, in movingFit), which lint cannot see.
        @Suppress("ProtoLayoutMinSchema")
        val description = if (moving) {
            movingCountdown(countdown, next, fit, towardsTheName(rtl, hostRtl))
            movingSentence(next) ?: TypeBuilders.StringProp.Builder(next.clockSentence).build()
        } else {
            countdown.setText(next.clock)
            TypeBuilders.StringProp.Builder(next.clockSentence).build()
        }
        val parts = inTileOrder(
            listOfNotNull(
                fit.overrun.takeIf { it > 0f }?.let { spacer(u, it) },
                label,
                spacer(u, LINE_GAP * fit.scale),
                countdown.build(),
            ),
            rtl,
            hostRtl,
        )
        val row = Row.Builder().setHeight(dp(u.x(bottom - top).value))
        parts.forEach { row.addContent(it) }
        val chord = WatchUnits.chordUnits(farEdge(LINE_Y, centred = false, line = TileType.countdown, size = fit.countdownSize), screen.round)
        val box = Box.Builder()
            .setWidth(dp(u.x(minOf(chord + 2 * fit.overrun, WatchUnits.ACROSS)).value))
            .setHeight(dp(u.x(bottom - top).value))
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build()).build())
            .addContent(row.build())
            .build()
        return placed(u, top, bottom - top, box)
    }

    /**
     * [countdown] moving each minute on the platform's clock (`TileCountdown.text`), its static
     * value the start's time (call 6) held wider than the widest of the words it can show by
     * [MEASURE_SLACK] ([LineFit.staticValue], [heldClock]), and its room that and a no-break space
     * more ([LineFit.room]), with its words set at [alignment] in the room and against the name in
     * their own box.
     *
     * Renderers make the countdown's box in two ways. Google's makes it as wide as the room asks
     * (ProtoLayoutInflater's `inflateText`: a frame `measureText` of the constraint wide), which
     * every emulator pass drew whole. Samsung's, on Mohamed's Galaxy Watch8 Classic (his report of
     * 9 November 2026, "DHUHR in 6 ..."), gave it a box as wide as the static value, measured on
     * the photo, and drew the moving words in it without laying the line out again: "in 6 h 10
     * min" in the room of "12:35" ended in an ellipsis. Held wider than the words, the static
     * value is a box that holds every one, and still reads as the start's time (Ruling 65 item 2);
     * the room, a no-break space wider, holds the static value itself in Google's frame. In a box
     * wider than the words, a text view sets them by its own alignment (`multilineAlignment`, the
     * centre unless told), which it takes in the paragraph's direction: START is the name's side,
     * since every word is set in the feed's direction, by its letters, or by the mark that leads
     * the start's time ([directed]). That much of Samsung's renderer is read from one photo; the
     * Galaxy pass on his watch is the proof.
     */
    @RequiresSchemaVersion(major = 1, minor = 200)
    private fun movingCountdown(countdown: Text.Builder, next: TileNext, fit: LineFit, alignment: Int) {
        countdown
            .setText(TypeBuilders.StringProp.Builder(fit.staticValue).setDynamicValue(TileCountdown.text(next)).build())
            .setLayoutConstraintsForDynamicText(TypeBuilders.StringLayoutConstraint.Builder(fit.room).setAlignment(alignment).build())
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_START)
    }

    /**
     * The line's sentence moving with it (`TileCountdown.spoken`), with the prayer and its time as
     * its static value (Ruling 65 item 2); null for a feed with no spoken words.
     */
    @RequiresSchemaVersion(major = 1, minor = 200)
    private fun movingSentence(next: TileNext): TypeBuilders.StringProp? =
        TileCountdown.spoken(next)?.let { TypeBuilders.StringProp.Builder(next.clockSentence).setDynamicValue(it).build() }

    /**
     * Where the countdown's words sit in their room, the widest of the words its entry can show:
     * against the name, so that every word stands `gTile`'s [LINE_GAP] from it (review round 2;
     * centred, the default, "in 59 min" stood 10 units further off in the room of "in 1 h 10 min").
     * The renderer sets the words in a frame that wide, at this alignment, with its start and end
     * in the host's own direction (ProtoLayoutInflater's `inflateText`), the direction it lays the
     * row in too: so the name's side is the start of the order the row is handed ([inTileOrder]),
     * the start when the feed and the host agree and the end when they differ.
     */
    private fun towardsTheName(rtl: Boolean, hostRtl: Boolean): Int =
        if (rtl xor hostRtl) LayoutElementBuilders.TEXT_ALIGN_END else LayoutElementBuilders.TEXT_ALIGN_START

    /**
     * A row of the day: its pip, its name and its time, in the colours of its status, each at the
     * row's [baseline] in a box as tall as the row's tallest line, and said as the phone's Prayer
     * screen says its rows, with its state.
     */
    private fun row(row: RowModel, screen: TileScreen, rtl: Boolean, hostRtl: Boolean, baseline: Float, timeEnd: Float, measure: TileMeasure): LayoutElement {
        val u = screen.units
        val nameType = if (row.small) TileType.sunrise else TileType.rowName
        val timeType = if (row.small) TileType.sunrise else TileType.rowTime
        val size = nameType.range(screen.fontScale).endInclusive
        val timeSize = timeType.range(screen.fontScale).endInclusive
        val nameColor = when {
            row.small -> SLATE
            row.status == RowStatus.CURRENT -> ACCENT
            row.status == RowStatus.PASSED -> SLATE
            else -> INK
        }
        val timeColor = if (row.small || row.status == RowStatus.PASSED) SLATE else INK
        val nameSlot = Slot.at(baseline, measure.extent(row.name, size, nameWeight(row)))
        val timeSlot = Slot.at(baseline, measure.extent(row.time, timeSize, 600))
        val pipCentre = baseline - PIP_ABOVE_BASELINE * size
        val top = minOf(nameSlot.top, timeSlot.top, pipCentre - HALO_DIAMETER / 2)
        val bottom = maxOf(nameSlot.bottom, timeSlot.bottom, pipCentre + HALO_DIAMETER / 2)
        val name = Text.Builder()
            .setText(row.name)
            .setFontStyle(style(size, nameWeight(row), nameColor, screen))
            .setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .build()
        val time = Text.Builder()
            .setText(row.time)
            .setFontStyle(style(timeSize, 600, timeColor, screen, tabular = true))
            .setMaxLines(1)
            .setModifiers(centred(top, bottom, timeSlot.centre, u))
            .build()
        val pipEnd = TodayLayout.PIP_X + HALO_DIAMETER / 2
        val ltr = listOf(
            slotted(u, top, bottom, pipCentre, pip(row, u), width = dp(u.x(HALO_DIAMETER).value)),
            spacer(u, TodayLayout.NAME_START - pipEnd),
            slotted(u, top, bottom, nameSlot.centre, name, width = DimensionBuilders.weight(1f), align = if (rtl) LayoutElementBuilders.HORIZONTAL_ALIGN_RIGHT else LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT),
            spacer(u, TodayLayout.GAP),
            time,
        )
        // The room before the pip and after the time, on the sides the feed's direction puts them.
        val before = TodayLayout.PIP_X - HALO_DIAMETER / 2
        val after = WatchUnits.ACROSS - timeEnd
        val semantics = ModifiersBuilders.Semantics.Builder().setContentDescription(TypeBuilders.StringProp.Builder(row.spoken).build())
        row.state?.let { semantics.setStateDescription(TypeBuilders.StringProp.Builder(it).build()) }
        val built = Row.Builder()
            .setWidth(dp(u.x(WatchUnits.ACROSS).value))
            .setHeight(dp(u.x(bottom - top).value))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(sides(u, left = if (rtl) after else before, right = if (rtl) before else after))
                    .setSemantics(semantics.build())
                    .build(),
            )
        inTileOrder(ltr, rtl, hostRtl).forEach { built.addContent(it) }
        return placed(u, top, bottom - top, built.build())
    }

    /**
     * Where the rows' times end, in units: Today's rule (`TodayLayout.timeEnd`), on the widths the
     * names and the times will be drawn at ([measure]): 150, as drawn, unless a row's name and time
     * need more room, when the whole column moves towards the round edge, as far as the narrowest
     * row allows. So a 12-hour Friday's "Jumuʿah" keeps its whole name beside "12:33 PM".
     */
    private fun timeEnd(rows: List<RowModel>, baselines: List<Float>, screen: TileScreen, measure: TileMeasure): Float {
        fun nameType(row: RowModel) = if (row.small) TileType.sunrise else TileType.rowName
        fun timeType(row: RowModel) = if (row.small) TileType.sunrise else TileType.rowTime
        return TodayLayout.timeEnd(
            nameWidths = rows.map { measure.width(it.name, nameType(it).range(screen.fontScale).endInclusive, nameWeight(it), tabular = false) + MEASURE_SLACK },
            timeWidths = rows.map { measure.width(it.time, timeType(it).range(screen.fontScale).endInclusive, 600, tabular = true) },
            maxEnds = rows.indices.map { TodayLayout.maxEnd(baselines[it], rows[it].small, screen.round) },
        )
    }

    /** The drawing's weight of a row's name: 800 for the lit prayer, else 600. */
    private fun nameWeight(row: RowModel): Int = if (row.status == RowStatus.CURRENT) 800 else 600

    /** The row's pip: the current prayer's halo and disc, a passed one's dot, an upcoming one's ring; Sunrise none. */
    private fun pip(row: RowModel, u: WatchUnits): LayoutElement? {
        if (row.small) return null
        return when (row.status) {
            RowStatus.CURRENT -> Box.Builder()
                .setWidth(dp(u.x(HALO_DIAMETER).value))
                .setHeight(dp(u.x(HALO_DIAMETER).value))
                .setModifiers(round(u, HALO_DIAMETER, HALO))
                .addContent(disc(u, DISC_DIAMETER, ACCENT))
                .build()
            RowStatus.PASSED -> disc(u, PASSED_DIAMETER, SLATE)
            RowStatus.UPCOMING -> Box.Builder()
                .setWidth(dp(u.x(UPCOMING_DIAMETER).value))
                .setHeight(dp(u.x(UPCOMING_DIAMETER).value))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(background(u, UPCOMING_DIAMETER, BLACK))
                        .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(u.x(UPCOMING_RING).value)).setColor(argb(HAIRLINE)).build())
                        .build(),
                )
                .build()
        }
    }

    private fun disc(u: WatchUnits, diameter: Float, color: Int): LayoutElement =
        Box.Builder().setWidth(dp(u.x(diameter).value)).setHeight(dp(u.x(diameter).value)).setModifiers(round(u, diameter, color)).build()

    private fun round(u: WatchUnits, diameter: Float, color: Int): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder().setBackground(background(u, diameter, color)).build()

    private fun background(u: WatchUnits, diameter: Float, color: Int): ModifiersBuilders.Background =
        ModifiersBuilders.Background.Builder()
            .setColor(argb(color))
            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(u.x(diameter / 2).value)).build())
            .build()

    /**
     * The city, whole, on one line inside the round screen's chord at its [baseline], 184, cut with
     * an ellipsis where it runs on, and read after the rows as it is written (it has its own speech,
     * or a screen reader would pass it by).
     */
    private fun city(city: String, screen: TileScreen, baseline: Float, measure: TileMeasure): LayoutElement {
        val u = screen.units
        val size = TileType.city.range(screen.fontScale).endInclusive
        val slot = Slot.at(baseline, measure.extent(city, size, 600))
        val text = Text.Builder()
            .setText(city)
            .setFontStyle(style(size, 600, MIST, screen))
            .setMaxLines(TileType.city.maxLines)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .build()
        val line = centredLine(u, slot.bottom - slot.top, WatchUnits.chordUnits(farEdge(baseline, centred = false, line = TileType.city, size = size), screen.round), text)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(city).build()).build())
            .build()
        return placed(u, slot.top, slot.bottom - slot.top, line)
    }

    /**
     * "Update Taqwa on your watch" in the city's place, in the accent (call 5): one line near the
     * city's, or two below Isha's row, at the size and baselines [fitUpdate] finds for them on
     * [measure]'s widths and line rooms, each line centred inside the round screen's chord at its
     * baseline and cut with an ellipsis where it still runs on. It is read whole, after the rows.
     */
    private fun update(update: String, screen: TileScreen, measure: TileMeasure): LayoutElement {
        val u = screen.units
        val fit = fitUpdate(
            update,
            TileType.update.range(screen.fontScale),
            screen.round,
            width = { text, size -> measure.width(text, size, 600, tabular = false) },
            extent = { text, size -> measure.extent(text, size, 600) },
        )
        val slots = fit.lines.indices.map { Slot.at(fit.baselines[it], measure.extent(fit.lines[it], fit.size, 600)) }
        val top = slots.minOf { it.top }
        val bottom = slots.maxOf { it.bottom }
        val lines = Box.Builder()
            .setWidth(dp(u.x(WatchUnits.ACROSS).value))
            .setHeight(dp(u.x(bottom - top).value))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(update).build()).build())
        for ((i, line) in fit.lines.withIndex()) {
            val slot = slots[i]
            val text = Text.Builder()
                .setText(line)
                .setFontStyle(style(fit.size, 600, ACCENT, screen))
                .setMaxLines(1)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                .build()
            val width = WatchUnits.chordUnits(farEdge(fit.baselines[i], centred = false, line = TileType.update, size = fit.size), screen.round)
            lines.addContent(placed(u, slot.top - top, slot.bottom - slot.top, centredLine(u, slot.bottom - slot.top, width, text).build(), stack = bottom - top))
        }
        return placed(u, top, bottom - top, lines.build())
    }

    /** A box across the screen [height] units tall, holding [text] centred in a box [width] units wide. */
    private fun centredLine(u: WatchUnits, height: Float, width: Float, text: LayoutElement): Box.Builder =
        Box.Builder()
            .setWidth(dp(u.x(WatchUnits.ACROSS).value))
            .setHeight(dp(u.x(height).value))
            .addContent(Box.Builder().setWidth(dp(u.x(width).value)).addContent(text).build())

    /**
     * A style: [units] of the drawing as sp through the screen's own density, so it is drawn
     * at that many units at every font scale (`WatchUnits.sp`); [weight] as the drawings' (800,
     * 700, 600), as the system font's own weight axis and, for a renderer without it, as
     * ProtoLayout's nearest stable weight ([weightOf]); [tracking] in ems; [tabular] figures for
     * counts and times.
     */
    private fun style(units: Float, weight: Int, color: Int, screen: TileScreen, tracking: Float = 0f, tabular: Boolean = false): FontStyle {
        val settings = buildList {
            add(FontSetting.weight(weight))
            if (tabular) add(FontSetting.tabularNum())
        }
        val builder = FontStyle.Builder()
            .setSize(DimensionBuilders.sp(screen.units.sp(units, Density(screen.density, screen.fontScale)).value))
            .setColor(argb(color))
            .setWeight(weightOf(weight))
            .setSettings(*settings.toTypedArray())
        if (tracking != 0f) builder.setLetterSpacing(DimensionBuilders.em(tracking))
        return builder.build()
    }

    /**
     * ProtoLayout's stable weight nearest a drawing's: bold from 700, else normal. Its medium is
     * still experimental, and the drawings' 600 is drawn by the font's own weight axis wherever the
     * renderer has it ([style]'s `FontSetting.weight`).
     */
    private fun weightOf(drawn: Int): Int = if (drawn >= 700) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL

    /**
     * Where a line sits, in units: its box from [top] to [bottom], whose middle, [centre], puts its
     * baseline where the drawing has it. A text view is as tall as its line, and its baseline lies
     * its fonts' ascent below its top; centred in its box, its baseline falls half its ascent less
     * its descent below the middle. The box keeps [LINE_ROOM] beyond the tallest the line may be
     * drawn, each way.
     */
    private data class Slot(val centre: Float, val top: Float, val bottom: Float) {
        companion object {
            fun at(baseline: Float, extent: TextExtent): Slot {
                val centre = baseline - (extent.above - extent.below) / 2
                val half = maxOf(extent.tall, extent.above + extent.below) / 2 + LINE_ROOM
                return Slot(centre, centre - half, centre + half)
            }
        }
    }

    /**
     * [content] in a box [width] wide from [top] to [bottom], padded so that its middle is at
     * [centre] ([centring]), aligned [align] across: one part of a row whose parts are each set at
     * their own middle.
     */
    private fun slotted(
        u: WatchUnits,
        top: Float,
        bottom: Float,
        centre: Float,
        content: LayoutElement?,
        width: DimensionBuilders.ContainerDimension,
        align: Int = LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER,
    ): LayoutElement {
        val box = Box.Builder().setWidth(width).setHeight(dp(u.x(bottom - top).value))
        if (content == null) return box.build()
        if (align != LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER) box.setHorizontalAlignment(align)
        return box.setModifiers(centred(top, bottom, centre, u)).addContent(content).build()
    }

    /** [centring] as an element's modifiers: a Text in a Row is set at its own middle this way, with no box of its own. */
    private fun centred(top: Float, bottom: Float, centre: Float, u: WatchUnits): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder().setPadding(centring(top, bottom, centre, u)).build()

    /**
     * [content], [height] units tall, [top] units below the top of its stack, [stack] units tall: a
     * child of a stack that sets its children at its bottom, which may reach into its neighbours'
     * room. The box holding it runs across the screen from [top] to the stack's bottom with
     * [content] at its top (what of [content] would run past the stack's bottom, the screen's edge,
     * is cut there), so the boxes' tops follow the drawing's order: Android hands a screen reader a
     * view group's children sorted by place, by their left edges first where they overlap (in a
     * left-to-right host) and then by their tops, and boxes that all began at the stack's top were
     * taken tallest first, the city before the line, and a narrower one last (review fix round 1,
     * TalkBack on emulator-5558). The box says nothing, so a screen reader's bounds are the
     * content's own.
     */
    private fun placed(u: WatchUnits, top: Float, height: Float, content: LayoutElement, stack: Float = WatchUnits.ACROSS): LayoutElement {
        val from = top.coerceAtLeast(0f)
        return Box.Builder()
            .setWidth(dp(u.x(WatchUnits.ACROSS).value))
            .setHeight(dp(u.x(stack - from).value))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .addContent(content)
            .build()
    }

    /**
     * The padding that puts the middle of a box from [top] to [bottom] at [centre]: the room above
     * or below, whichever moves it there.
     */
    private fun centring(top: Float, bottom: Float, centre: Float, u: WatchUnits): ModifiersBuilders.Padding {
        val shift = 2 * (centre - top) - (bottom - top)
        val padding = ModifiersBuilders.Padding.Builder()
        if (shift > 0f) padding.setTop(dp(u.x(shift).value))
        if (shift < 0f) padding.setBottom(dp(u.x(-shift).value))
        return padding.build()
    }

    /** [left] and [right], in units, on those sides whatever the watch's language (its start is the left). */
    private fun sides(u: WatchUnits, left: Float, right: Float): ModifiersBuilders.Padding {
        val padding = ModifiersBuilders.Padding.Builder().setRtlAware(false)
        if (left > 0f) padding.setStart(dp(u.x(left).value))
        if (right > 0f) padding.setEnd(dp(u.x(right).value))
        return padding.build()
    }

    private fun spacer(u: WatchUnits, width: Float, height: Float = 0f): LayoutElement =
        Spacer.Builder().setWidth(dp(u.x(width).value)).setHeight(dp(u.x(height).value)).build()

    private val MAIN_ACTIVITY: String = WearMainActivity::class.java.name
    private const val OPEN_ID = "open_today"
}
