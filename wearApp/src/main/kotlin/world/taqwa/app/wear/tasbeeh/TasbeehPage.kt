package world.taqwa.app.wear.tasbeeh

import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import world.taqwa.app.tasbeeh.TapEvent
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.LineText
import world.taqwa.app.wear.app.UnitsBox
import world.taqwa.app.wear.app.WatchColors
import world.taqwa.app.wear.app.WatchFonts
import world.taqwa.app.wear.app.WatchUnits

/**
 * Tasbeeh (spec 2.5): the counter, and a swipe up from it the phrase list (`gTasbeeh`,
 * `gTasbeehList`), as a vertical pager of the two. The bezel belongs to the counter: the pager is
 * given no rotary behaviour (call 16), since Wear foundation's default would turn to the phrases on
 * a click of the bezel; the phrase list scrolls by the bezel itself.
 *
 * [tasbeeh] is held by the activity, above the pager, so that a swipe to another page, which
 * disposes this one, loses neither the last counts nor a set's hold. Whenever the counter stops
 * being in front and active (a swipe away, the phrase list, the activity paused), its count is
 * written at once ([WatchTasbeeh.flush], erratum p3 #8).
 *
 * For a screen reader: the counter is one node, its name and its count (round 2's rules,
 * [TasbeehSpeech]), with "Count" as its click and "Take one away" and "Choose a dhikr" as its
 * actions, which is how TalkBack reaches the phrase list, since one-finger swipes and the bezel are
 * then TalkBack's. The pager keeps its own scroll semantics: nothing above it clears them.
 */
@Composable
internal fun TasbeehPage(feed: WatchFeed, active: Boolean, tasbeeh: WatchTasbeeh) {
    val words = feed.words()
    val ui by tasbeeh.ui.collectAsStateWithLifecycle()
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val phrases = remember(feed) { PhraseList.of(feed) }
    val counting = active && pager.settledPage == COUNTER
    LaunchedEffect(counting) { if (!counting) tasbeeh.flush() }
    DisposableEffect(tasbeeh) { onDispose { tasbeeh.flush() } }

    Box(Modifier.fillMaxSize().semantics { paneTitle = words.tasbeeh }) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), rotaryScrollableBehavior = null) { page ->
            if (page == COUNTER) {
                Counter(feed, ui, words, tasbeeh, onPhrases = { scope.launch { pager.animateScrollToPage(PHRASES) } })
            } else {
                PhrasesPage(
                    feed = feed,
                    ui = ui,
                    phrases = phrases,
                    onChoose = { id ->
                        tasbeeh.select(id)
                        scope.launch { pager.animateScrollToPage(COUNTER) }
                    },
                    onReset = {
                        tasbeeh.reset()
                        scope.launch { pager.animateScrollToPage(COUNTER) }
                    },
                )
            }
        }
    }
}

/**
 * The counter (`gTasbeeh`): the ring round the screen, clockwise in every language, notched at the
 * parts' ends; the dhikr, its transliteration, the round, the count, its target, the parts' dots
 * and, at the very start, the hint. A tap anywhere counts, and so does each click of the bezel
 * clockwise; each click back takes one away ([RotaryCounter]). The counter takes the bezel whenever
 * it is the page in front (`requestFocusOnHierarchyActive`), and answers every rotary event, a
 * remainder of no whole click included, so that nothing above it ever takes the bezel.
 */
@Composable
private fun Counter(feed: WatchFeed, ui: TasbeehUi, words: WatchScreenWords, tasbeeh: WatchTasbeeh, onPhrases: () -> Unit) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val rotary = remember(context) { RotaryCounter(ViewConfiguration.get(context).scaledVerticalScrollFactor) }
    val digits = remember(feed) { { n: Int -> WatchRules.digits(feed, n.toLong()) } }
    val lines = counterLines(feed, ui)
    val name = TasbeehSpeech.name(ui, words, digits)
    val state = TasbeehSpeech.state(ui, words, digits)
    val actions = TasbeehSpeech.actions(words).map { action ->
        CustomAccessibilityAction(action.label) {
            when (action.does) {
                CounterDoes.UNDO -> tasbeeh.undo()
                CounterDoes.PHRASES -> onPhrases()
            }
            true
        }
    }

    // The flash of a part's or the set's end, kept for its whole length whatever is counted after it.
    var halo by remember { mutableStateOf<TapEvent?>(null) }
    LaunchedEffect(ui) { ui.flash?.let { halo = it } }
    LaunchedEffect(halo) {
        val shown = halo ?: return@LaunchedEffect
        delay(if (shown == TapEvent.SetComplete) SET_FLASH_MS else PART_FLASH_MS)
        halo = null
    }

    UnitsBox(
        Modifier
            .onRotaryScrollEvent { event ->
                val detents = rotary.onScroll(event.verticalScrollPixels, event.uptimeMillis)
                if (detents != 0) tasbeeh.turn(detents)
                true
            }
            .requestFocusOnHierarchyActive()
            .focusable()
            .clickable(interactionSource = null, indication = null, onClickLabel = words.countAction) { tasbeeh.count() }
            .clearAndSetSemantics {
                contentDescription = name
                stateDescription = state
                customActions = actions
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCounterRing(ui, halo)
            drawPartDots(ui, rtl = layoutDirection == LayoutDirection.Rtl)
        }
        // The dhikr in the system font, as the phone draws it (TasbeehScreen.DhikrBlock), ending
        // above "ROUND n" whatever its script's line height.
        LineText(
            lines.dhikr, lines.dhikrLine, y = TasbeehType.DHIKR_Y, color = WatchColors.ink, weight = FontWeight.Bold, family = FontFamily.Default,
            bottom = TasbeehType.dhikrBottom(transliterated = lines.transliteration != null, fontScale),
        )
        lines.transliteration?.let {
            LineText(it, TasbeehType.transliteration, y = TasbeehType.TRANSLITERATION_Y, color = WatchColors.mist, weight = WatchFonts.weight(600))
        }
        LineText(lines.round, TasbeehType.round, y = TasbeehType.ROUND_Y, color = WatchColors.accent, weight = WatchFonts.weight(800), tracking = 1.3f)
        LineText(lines.count, TasbeehType.count, y = TasbeehType.COUNT_Y, color = WatchColors.ink, weight = WatchFonts.weight(300), tracking = -0.8f, tabular = true)
        LineText(lines.of, TasbeehType.of, y = TasbeehType.OF_Y, color = WatchColors.mist, weight = WatchFonts.weight(600))
        lines.hint?.let {
            LineText(it, TasbeehType.hint, y = TasbeehType.HINT_Y, color = WatchColors.mist, weight = WatchFonts.weight(600), inset = TasbeehType.HINT_INSET)
        }
    }
}

/**
 * The ring (`appRing(100, 100, 92, …, 5.6)`): the hairline track, the accent arc from the top,
 * clockwise in every language, with round ends; the notches at the parts' ends in the track's
 * colour, which show once the arc has passed them; and over it all, the flash of a part's end
 * (10 units wide) or the set's (14), in the accent at 0.22.
 */
private fun DrawScope.drawCounterRing(ui: TasbeehUi, halo: TapEvent?) {
    val side = min(size.width, size.height)
    val unit = side / WatchUnits.ACROSS
    val centre = Offset(size.width / 2, size.height / 2)
    val radius = RING_RADIUS * unit
    val topLeft = Offset(centre.x - radius, centre.y - radius)
    val box = Size(2 * radius, 2 * radius)
    drawCircle(WatchColors.hairline, radius = radius, center = centre, style = Stroke(width = RING_WIDTH * unit))
    val sweep = 360f * ui.progress.coerceIn(0f, 1f)
    // As faces.js's arc: nothing for a sliver, and the whole ring once it is all but full.
    if (sweep >= 359.9f) {
        drawCircle(WatchColors.accent, radius = radius, center = centre, style = Stroke(width = RING_WIDTH * unit))
    } else if (sweep >= 0.05f) {
        drawArc(
            WatchColors.accent, startAngle = -90f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = box,
            style = Stroke(width = RING_WIDTH * unit, cap = StrokeCap.Round),
        )
    }
    val inner = (RING_RADIUS - RING_WIDTH / 2 - NOTCH_OVERHANG) * unit
    val outer = (RING_RADIUS + RING_WIDTH / 2 + NOTCH_OVERHANG) * unit
    for (fraction in ui.ticks) {
        val angle = Math.toRadians(360.0 * fraction - 90.0)
        val (dx, dy) = cos(angle).toFloat() to sin(angle).toFloat()
        drawLine(
            WatchColors.hairline,
            start = Offset(centre.x + inner * dx, centre.y + inner * dy),
            end = Offset(centre.x + outer * dx, centre.y + outer * dy),
            strokeWidth = NOTCH_WIDTH * unit,
            cap = StrokeCap.Butt,
        )
    }
    if (halo != null) {
        val width = if (halo == TapEvent.SetComplete) SET_FLASH_WIDTH else PART_FLASH_WIDTH
        drawCircle(WatchColors.accent.copy(alpha = FLASH_ALPHA), radius = radius, center = centre, style = Stroke(width = width * unit))
    }
}

/**
 * A set's parts as dots at y 168, 13 units apart (`gTasbeeh`): the part done and the one being said
 * filled, slate and accent, the ones to come hollow. In the phone's reading order, as its part
 * reminder row lays them out: the first part on the right for a right-to-left phone. A single
 * phrase has none.
 */
private fun DrawScope.drawPartDots(ui: TasbeehUi, rtl: Boolean) {
    val parts = ui.preset.parts.size
    if (parts < 2) return
    val side = min(size.width, size.height)
    val unit = side / WatchUnits.ACROSS
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    for (i in 0 until parts) {
        val place = if (rtl) parts - 1 - i else i
        val centre = Offset(left + (WatchUnits.ACROSS / 2 + (place - (parts - 1) / 2f) * DOT_PITCH) * unit, top + TasbeehType.DOTS_Y * unit)
        when {
            i == ui.currentPart -> drawCircle(WatchColors.accent, radius = DOT_RADIUS * unit, center = centre)
            i < ui.currentPart -> drawCircle(WatchColors.slate, radius = DOT_RADIUS * unit, center = centre)
            else -> {
                drawCircle(WatchColors.black, radius = HOLLOW_RADIUS * unit, center = centre)
                drawCircle(WatchColors.slate, radius = HOLLOW_RADIUS * unit, center = centre, style = Stroke(width = HOLLOW_STROKE * unit))
            }
        }
    }
}

/** The vertical pager's two pages. */
private const val PAGES = 2
private const val COUNTER = 0
private const val PHRASES = 1

/** The ring: radius, width, and its notches 0.3 units past each edge, 1.1 wide. */
private const val RING_RADIUS = 92f
private const val RING_WIDTH = 5.6f
private const val NOTCH_OVERHANG = 0.3f
private const val NOTCH_WIDTH = 1.1f

/** The flash: a part's end 10 units wide for 240 ms, the set's 14 for 520 ms, in the accent at 0.22. */
private const val PART_FLASH_WIDTH = 10f
private const val SET_FLASH_WIDTH = 14f
private const val FLASH_ALPHA = 0.22f
private const val PART_FLASH_MS = 240L
private const val SET_FLASH_MS = 520L

/** The parts' dots: 13 units apart, filled at r 2.6, hollow at r 2.2 with a 1-unit edge. */
private const val DOT_PITCH = 13f
private const val DOT_RADIUS = 2.6f
private const val HOLLOW_RADIUS = 2.2f
private const val HOLLOW_STROKE = 1f
