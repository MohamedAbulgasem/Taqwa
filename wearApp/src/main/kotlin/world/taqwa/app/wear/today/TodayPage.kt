package world.taqwa.app.wear.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import kotlin.math.min
import kotlin.math.roundToInt
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.LineText
import world.taqwa.app.wear.app.LocalRoundScreen
import world.taqwa.app.wear.app.LocalWatchUnits
import world.taqwa.app.wear.app.NoticePage
import world.taqwa.app.wear.app.UnitsBox
import world.taqwa.app.wear.app.WatchColors
import world.taqwa.app.wear.app.WatchFonts
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.app.notice
import world.taqwa.app.wear.app.ready
import world.taqwa.app.wear.app.rememberNow
import world.taqwa.app.wear.face.FaceRow
import world.taqwa.app.wear.face.rememberFaceChip

/**
 * Today (spec 2.5): the phone's countdown ring around the screen, and below it, a swipe or a turn
 * of the bezel away, the day's six times (five where the phone hides Sunrise) and the city, as the
 * Tile draws them (`gToday`, `gTile`).
 * [active] is "the current page, and the activity resumed": only then does the clock tick, once a
 * second on the second, for the ring's H:MM:SS. [update] says a newer format has been seen: the ring
 * says so in the time's place.
 *
 * Spent ([today] is null), the page is the frame's notice under its title (Task 4): the update
 * when a newer format has been seen, else "Open Taqwa on your phone".
 *
 * For a screen reader: the page's title is its pane title, so that a page change is announced; the
 * ring is one node that says its sentence, which changes once a minute; each row is one node, said
 * as the phone's Prayer screen says its rows, with its state; the city is read as it is written;
 * the chevron is not read.
 *
 * While the Taqwa face is not the watch's face and its one activation is unspent, the list ends in
 * the face chip ([FaceRow], plan 5 Task 5), after the city.
 */
@Composable
internal fun TodayPage(feed: WatchFeed, active: Boolean, update: Boolean) {
    val words = feed.words()
    val now = rememberNow(active, everyMillis = SECOND_MS)
    val model = today(feed, now, update)
    if (model == null) {
        NoticePage(title = words.today, notice = ready(feed, update, now).notice(words) ?: words.openOnPhone)
        return
    }
    val faceChip = rememberFaceChip(active)
    TodayColumn(model, title = words.today, face = if (faceChip) ({ FaceRow(feed) }) else null)
}

/**
 * The ring, a screen tall, then the list: its room above, the day's rows and the city, a second
 * screen at the drawing's size, with Sunrise or without ([TodayLayout.list]). Each row grows with
 * its name's size as drawn, which the row reports; every item's edges are the list's, rounded to
 * pixels from the list's top ([TodayLayout.px]), so that scrolled to its end the list is the Tile's
 * to the pixel.
 */
@Composable
private fun TodayColumn(model: TodayModel, title: String, face: (@Composable () -> Unit)? = null) {
    val state = rememberTransformingLazyColumnState()
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val unit = units.dp * density.density
    val small = model.rows.map { it.small }
    // Each row's name as drawn, over its line's size: the largest its range allows until the row
    // has been laid out and says.
    val grow = remember(small, density.fontScale) {
        mutableStateListOf(*small.map { nameLineOf(it).range(density.fontScale).endInclusive / nameLineOf(it).units }.toTypedArray())
    }
    val cityGrow = TodayType.city.range(density.fontScale).endInclusive / TodayType.city.units
    val list = TodayLayout.list(small, grow.toList(), cityGrow)
    // The one edge the times end at, each name and time measured at the largest size its line may
    // take, and held at each row's round edge where the drawing has the rows; the list's cap keeps
    // that edge on the screen where the list's end puts them ([TodayLayout.maxEnds]).
    val drawing = TodayLayout.list(small)
    val round = LocalRoundScreen.current
    val measurer = rememberTextMeasurer()
    val timeEnd = remember(model.rows, density, units, round) {
        fun widthOf(text: String, line: Line, style: TextStyle) = measurer.measure(
            text, style.copy(fontSize = units.sp(line.range(density.fontScale).endInclusive, density)), maxLines = 1, softWrap = false, density = density,
        ).size.width / unit
        TodayLayout.timeEnd(
            nameWidths = model.rows.map { widthOf(it.name, nameLineOf(it.small), rowLook(it).nameStyle) },
            timeWidths = model.rows.map { widthOf(it.time, timeLineOf(it.small), rowLook(it).timeStyle) },
            maxEnds = TodayLayout.maxEnds(small, round),
        )
    }
    fun px(u: Float) = TodayLayout.px(u, unit)
    fun dp(pixels: Int) = with(density) { pixels.toDp() }

    BoxWithConstraints(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        val screen = maxHeight
        TransformingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            item(key = "ring") { RingItem(model.ring, Modifier.fillMaxWidth().height(screen)) }
            item(key = "above") { Spacer(Modifier.fillMaxWidth().height(dp(px(list.tops.first())))) }
            items(model.rows.size, key = { "row$it" }) { i ->
                val top = px(list.tops[i])
                val bottom = px(list.tops.getOrElse(i + 1) { list.cityTop })
                RowItem(
                    row = model.rows[i],
                    grow = grow[i],
                    height = bottom - top,
                    baseline = px(list.baselines[i]) - top,
                    pipY = (list.baselines[i] - PIP_RISE * grow[i]) * unit - top,
                    timeEnd = timeEnd,
                    // Written only when it changes: a snapshot list's write recomposes its readers.
                    onDrawn = { size -> (size / nameLineOf(small[i]).units).let { if (grow[i] != it) grow[i] = it } },
                )
            }
            item(key = "city") {
                CityItem(model.city, y = drawing.city, height = px(list.height) - px(list.cityTop), baseline = px(list.city) - px(list.cityTop))
            }
            // Plan 5 Task 5: "Add the Taqwa face", while the face is not the watch's and a tap can help.
            face?.let { item(key = "face") { it() } }
        }
    }
}

private fun nameLineOf(small: Boolean): Line = if (small) TodayType.sunrise else TodayType.rowName

private fun timeLineOf(small: Boolean): Line = if (small) TodayType.sunrise else TodayType.rowTime

/** How a row's name and time are drawn (`gTile`): the lit name ExtraBold in the accent, a passed or small row in slate, the rest in ink. */
private class RowLook(val nameStyle: TextStyle, val timeStyle: TextStyle)

private fun rowLook(row: RowModel): RowLook {
    val nameColor = when {
        row.small || row.status == RowStatus.PASSED -> WatchColors.slate
        row.status == RowStatus.CURRENT -> WatchColors.accent
        else -> WatchColors.ink
    }
    val timeColor = if (row.small || row.status == RowStatus.PASSED) WatchColors.slate else WatchColors.ink
    val nameWeight = WatchFonts.weight(if (row.status == RowStatus.CURRENT && !row.small) 800 else 600)
    return RowLook(rowStyle(nameColor, nameWeight, tabular = false), rowStyle(timeColor, WatchFonts.weight(600), tabular = true))
}

/** A row's text in Manrope, its size left to the line's range; counts and times tabular. */
private fun rowStyle(color: Color, weight: FontWeight, tabular: Boolean) = TextStyle(
    color = color,
    fontFamily = WatchFonts.manrope,
    fontWeight = weight,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** The ring (`gToday`, `appRing`): the track and the arc at r 90, the label, the countdown, the time or the notice, and the chevron. */
@Composable
private fun RingItem(ring: RingModel, modifier: Modifier) {
    UnitsBox(modifier.clearAndSetSemantics { contentDescription = ring.spoken }) {
        Canvas(Modifier.fillMaxSize()) { drawRing(ring.progress, ring.rtl) }
        LineText(ring.label, TodayType.label, y = 78f, color = WatchColors.accent, weight = WatchFonts.weight(800), tracking = 1.6f)
        LineText(ring.countdown, TodayType.countdown, y = 117f, color = WatchColors.ink, weight = WatchFonts.weight(300), tracking = -0.6f, tabular = true)
        if (ring.notice) {
            LineText(ring.time, TodayType.notice, y = 140f, color = WatchColors.accent, weight = WatchFonts.weight(600), centred = true)
        } else {
            // Proportional figures, as the phone's ring writes its clock time (`TaqwaText.caption`) and gToday draws it.
            LineText(ring.time, TodayType.time, y = 137f, color = WatchColors.mist, weight = WatchFonts.weight(600))
        }
    }
}

/**
 * The ring as the phone's `RingArc` draws it, in the drawing's units across the screen's square:
 * the hairline track, and the accent arc from the top, clockwise, or anticlockwise for a
 * right-to-left phone, with round ends; then the chevron that says the list is below.
 */
private fun DrawScope.drawRing(progress: Float, rtl: Boolean) {
    val side = min(size.width, size.height)
    val unit = side / WatchUnits.ACROSS
    val centre = Offset(size.width / 2, size.height / 2)
    val radius = RING_RADIUS * unit
    val stroke = Stroke(width = RING_WIDTH * unit, cap = StrokeCap.Round)
    val topLeft = Offset(centre.x - radius, centre.y - radius)
    val box = Size(2 * radius, 2 * radius)
    drawArc(WatchColors.hairline, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = box, style = stroke)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    // As faces.js's arc: nothing for a sliver, and the whole ring once it is all but full.
    if (sweep >= 0.05f) {
        drawArc(WatchColors.accent, startAngle = -90f, sweepAngle = if (rtl) -sweep else sweep, useCenter = false, topLeft = topLeft, size = box, style = stroke)
    }

    val square = Offset(centre.x - side / 2, centre.y - side / 2)
    fun at(x: Float, y: Float) = Offset(square.x + x * unit, square.y + y * unit)
    val chevron = Path().apply {
        val (a, b, c) = listOf(at(93f, 160f), at(100f, 166f), at(107f, 160f))
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
    }
    drawPath(chevron, WatchColors.slate, style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * One of the day's rows, as `gTile` draws it: the pip at 46 (none for Sunrise), the name from 56
 * and the time ending at the list's one edge, [timeEnd] (150 as the drawing has it, or towards the
 * round edge when a name needs the room, [TodayLayout.timeEnd]), mirrored for a right-to-left
 * phone, on the row's [baseline]. The row is [height] pixels tall, its pitch, which grows with its
 * name's size as drawn ([grow], call 15), and it tells the column that size through [onDrawn]. A
 * name with too little room shrinks to its line's size and is then cut with an ellipsis; the time
 * always keeps its line, as on the phone.
 */
@Composable
private fun RowItem(row: RowModel, grow: Float, height: Int, baseline: Int, pipY: Float, timeEnd: Float, onDrawn: (Float) -> Unit) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val look = rowLook(row)
    val pip = if (row.small) null else row.status
    val unit = units.dp * density.density

    Layout(
        content = {
            RowText(row.name, nameLineOf(row.small), look.nameStyle, drawn = onDrawn)
            RowText(row.time, timeLineOf(row.small), look.timeStyle)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { height.toDp() })
            .clearAndSetSemantics {
                contentDescription = row.spoken
                row.state?.let { stateDescription = it }
            }
            .drawBehind { if (pip != null) drawPip(pip, grow, unit, pipY) },
    ) { measurables, constraints ->
        val left = (constraints.maxWidth - WatchUnits.ACROSS * unit) / 2
        val time = measurables[1].measure(Constraints())
        val room = TodayLayout.nameRoom(timeEnd, time.width / unit)
        val name = measurables[0].measure(Constraints(maxWidth = (room * unit).roundToInt()))
        layout(constraints.maxWidth, height) {
            name.placeRelative((left + TodayLayout.NAME_START * unit).roundToInt(), baseline - name[FirstBaseline])
            time.placeRelative((left + timeEnd * unit).roundToInt() - time.width, baseline - time[FirstBaseline])
        }
    }
}

/**
 * A row's name or time: one line in Manrope, sized by its [line]'s range at the wearer's font size
 * (`TextAutoSize`, as `LineText` sizes a line), cut with an ellipsis. [drawn] hears the size it was
 * drawn at, in units, which the row's pitch follows.
 */
@Composable
private fun RowText(text: String, line: Line, style: TextStyle, drawn: ((Float) -> Unit)? = null) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val sizes = line.range(density.fontScale)
    BasicText(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = units.sp(sizes.start, density),
            maxFontSize = units.sp(sizes.endInclusive, density),
            stepSize = units.sp(STEP, density),
        ),
        onTextLayout = drawn?.let { hear ->
            { result: TextLayoutResult ->
                val size = result.layoutInput.style.fontSize
                if (size.isSp) hear(with(density) { size.toDp() }.value / units.dp)
            }
        },
    )
}

/** A row's pip (`pip()`): the current prayer's accent disc in its halo, a passed one's slate dot, an upcoming one's hollow ring. */
private fun DrawScope.drawPip(status: RowStatus, grow: Float, unit: Float, y: Float) {
    val left = (size.width - WatchUnits.ACROSS * unit) / 2
    val x = if (layoutDirection == LayoutDirection.Rtl) WatchUnits.ACROSS - TodayLayout.PIP_X else TodayLayout.PIP_X
    val centre = Offset(left + x * unit, y)
    when (status) {
        RowStatus.CURRENT -> {
            drawCircle(WatchColors.accent.copy(alpha = 0.2f), radius = 5.6f * grow * unit, center = centre)
            drawCircle(WatchColors.accent, radius = 3.3f * grow * unit, center = centre)
        }
        RowStatus.PASSED -> drawCircle(WatchColors.slate, radius = 2.4f * grow * unit, center = centre)
        RowStatus.UPCOMING -> {
            drawCircle(WatchColors.black, radius = 2.3f * grow * unit, center = centre)
            drawCircle(WatchColors.hairline, radius = 2.3f * grow * unit, center = centre, style = Stroke(width = 1.3f * grow * unit))
        }
    }
}

/**
 * The city the list ends in, as `gTile` writes it under the rows: one line inside the round screen's
 * chord at its place in the drawing ([y]), cut with an ellipsis, on [baseline] in a room [height]
 * pixels tall.
 */
@Composable
private fun CityItem(city: String, y: Float, height: Int, baseline: Int) {
    val density = LocalDensity.current
    Layout(
        content = { LineText(city, TodayType.city, y = y, color = WatchColors.mist, weight = WatchFonts.weight(600)) },
        modifier = Modifier.fillMaxWidth().height(with(density) { height.toDp() }),
    ) { measurables, constraints ->
        val text = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, height) {
            text.place((constraints.maxWidth - text.width) / 2, baseline - text[FirstBaseline])
        }
    }
}

private const val SECOND_MS = 1_000L

/** The ring's radius and width in the drawing (`gToday`: `appRing(100, 100, 90, …, 7.4)`). */
private const val RING_RADIUS = 90f
private const val RING_WIDTH = 7.4f

/** How far above its baseline a row's pip sits (`pip(46, y - 4, …)`), at the drawing's size. */
private const val PIP_RISE = 4f

/** How finely a row's size is searched, in units, as `LineText` searches. */
private const val STEP = 0.5f
