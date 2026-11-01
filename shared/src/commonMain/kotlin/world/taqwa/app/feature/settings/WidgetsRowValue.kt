package world.taqwa.app.feature.settings

import world.taqwa.app.widget.WidgetPlacement

/**
 * What the Widgets row on the Settings root says beside its label: how many of the two home-screen
 * widgets (prayer and ayah) are on a home screen. Information, not a call to act, so it carries no
 * badge or accent. The iPhone's Lock Screen widget is not counted, although placement reports it:
 * the row counts what is on the home screen, and a Lock Screen widget alone must never read as the
 * prayer widget added there. The Lock Screen card on the Widgets screen shows its own check.
 */
internal sealed interface WidgetsRowValue {
    /** Placement has not been read yet, or the platform cannot tell: no value at all, rather
     * than a guess that flips a frame later, or one that says a widget is there when nobody knows. */
    data object Hidden : WidgetsRowValue

    data object AllAdded : WidgetsRowValue

    data object NoneAdded : WidgetsRowValue

    data class Some(val placed: Int, val total: Int) : WidgetsRowValue
}

/**
 * [placement] is null until a read has answered: before the first read, and while every read so
 * far could not tell. A read that cannot tell after one that answered keeps that answer
 * ([world.taqwa.app.widget.reread]), so the value does not flicker away on a resume.
 */
internal fun widgetsRowValue(placement: WidgetPlacement?): WidgetsRowValue {
    placement ?: return WidgetsRowValue.Hidden
    val placed = listOf(placement.prayer, placement.ayah)
    val count = placed.count { it }
    return when (count) {
        placed.size -> WidgetsRowValue.AllAdded
        0 -> WidgetsRowValue.NoneAdded
        else -> WidgetsRowValue.Some(count, placed.size)
    }
}
