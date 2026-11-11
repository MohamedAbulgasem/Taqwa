package world.taqwa.app.wear.tile

import androidx.wear.protolayout.proto.LayoutElementProto
import androidx.wear.protolayout.proto.TimelineProto
import androidx.wear.protolayout.proto.TypesProto
import world.taqwa.app.wear.complication.DynamicEvaluator

/**
 * The Tile renderer as it changes entries on emulator-5558 (review round 3): asked at [firstShown]
 * (epoch milliseconds), it shows the entry that holds the moment ([shownAt]), and moves on with an
 * inexact alarm set for that entry's end, never sooner than a minute after it last moved
 * (androidx's `TilesTimelineManagerInternal`, `MIN_TILE_UPDATE_DELAY_MILLIS`, as SysUI's
 * `ProtoTilesTimelineMgr` did). An inexact alarm may come as late as three quarters of the time it
 * was set for again, an hour at most (`AlarmManager.set`'s window), and when [late] it comes then,
 * as it did on the emulator ("window +3m44s736ms", and the change at its end); else on the dot.
 * [beyondMillis] more is a renderer later than its window: deep idle held one on emulator-5558
 * until the watch woke, 2 min 16 s past the last second the plan allows for (review round 4).
 *
 * Returns each change up to [horizon]: when it came and which entry it showed, in order.
 */
internal fun changes(timeline: TimelineProto.Timeline, firstShown: Long, horizon: Long, late: Boolean, beyondMillis: Long = 0L): List<Pair<Long, Int>> {
    val out = ArrayList<Pair<Long, Int>>()
    var at = firstShown
    var shown = shownAt(timeline, at) ?: return out
    out += at to shown
    while (at < horizon) {
        val end = timeline.timelineEntriesList[shown].validity.endMillis
        if (end == Long.MAX_VALUE) break
        val asked = maxOf(end, at + 60_000L)
        val window = if (late) minOf((asked - at) * 3 / 4, 3_600_000L) else 0L
        at = asked + window + beyondMillis
        shown = shownAt(timeline, at) ?: break
        out += at to shown
    }
    return out
}

/** Which entry the renderer of [changes] shows at [millis]. */
internal fun showing(changes: List<Pair<Long, Int>>, millis: Long): Int = changes.last { it.first <= millis }.second

/**
 * What a layout shows at [millis] by the platform's clock, as the renderer draws it: the words of
 * every Text not hidden (`Modifiers.visible`, with an element hidden whose box is), each dynamic one
 * as its value then reads, and every element's speech and state that is read then. A renderer that
 * cannot hide an element ([hides] false) shows all of them, as a renderer before schema 1.3 would.
 * One that does not [bind] the dynamic values (over its quota, on an evaluation error, or in the
 * frame before it binds them) shows each one's static value, and hides by the static values too.
 * Each text is what is seen of it ([visible]).
 */
internal class Seen(root: LayoutElementProto.LayoutElement, millis: Long, private val hides: Boolean = true, private val binds: Boolean = true) {
    private val evaluator = DynamicEvaluator(millis / 1_000L, (millis % 1_000L).toInt())

    val texts = ArrayList<String>()
    val said = ArrayList<String>()
    val states = ArrayList<String>()

    init {
        walk(root)
    }

    private fun value(prop: TypesProto.StringProp): String = if (binds && prop.hasDynamicValue()) evaluator.string(prop.dynamicValue) else prop.value

    private fun walk(element: LayoutElementProto.LayoutElement) {
        val (modifiers, children) = when {
            element.hasBox() -> element.box.modifiers.takeIf { element.box.hasModifiers() } to element.box.contentsList
            element.hasRow() -> element.row.modifiers.takeIf { element.row.hasModifiers() } to element.row.contentsList
            element.hasColumn() -> element.column.modifiers.takeIf { element.column.hasModifiers() } to element.column.contentsList
            element.hasText() -> element.text.modifiers.takeIf { element.text.hasModifiers() } to emptyList()
            element.hasSpacer() -> element.spacer.modifiers.takeIf { element.spacer.hasModifiers() } to emptyList()
            else -> throw UnsupportedOperationException("Seen does not know ${element.innerCase}: teach it before using it")
        }
        if (modifiers != null && modifiers.hasVisible() && hides) {
            val visible = modifiers.visible
            if (!(if (binds && visible.hasDynamicValue()) evaluator.bool(visible.dynamicValue) else visible.value)) return
        }
        if (modifiers != null && modifiers.hasSemantics()) {
            if (modifiers.semantics.hasContentDescription()) said += value(modifiers.semantics.contentDescription)
            if (modifiers.semantics.hasStateDescription()) states += value(modifiers.semantics.stateDescription)
        }
        if (element.hasText()) texts += visible(value(element.text.text))
        for (child in children) walk(child)
    }
}

/**
 * What is seen of [text] once drawn: without the direction marks, which have no ink, and without
 * the figure and no-break spaces that hold the countdown's static value wide after the start's time
 * (`heldClock`). A clock's own no-break space, before its day period, is inside it and stays.
 */
internal fun visible(text: String): String =
    text.filterNot { it == '\u200E' || it == '\u200F' }.trimEnd { it == '\u2007' || it == '\u00A0' }
