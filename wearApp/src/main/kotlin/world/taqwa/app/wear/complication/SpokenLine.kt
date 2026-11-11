package world.taqwa.app.wear.complication

import android.os.Build
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.DynamicComplicationText

/**
 * A line made of words that stay and parts that move with the clock (the countdown), joined by
 * [separator]: what a screen reader is told, and the next prayer's long text. From Wear OS 4 (API
 * 33) it is a dynamic value, which the system works out a minute at a time as it does the
 * countdown, so the reader is never told a minute that has gone. Each moving part comes with its
 * words at the entry's start, which are what the system shows before it has worked the dynamic one
 * out. Below API 33 nothing is worked out, so a line made for it holds no moving part: the next
 * prayer's lines are then its clock form (Ruling 72 item 1, [nextPrayerSaid], [nextPrayerLongText]).
 */
internal class SpokenLine(private val separator: String) {
    private var dynamic: DynamicString? = null
    private val words = StringBuilder()

    /** Adds [part] as it is. */
    fun words(part: String): SpokenLine = add(DynamicString.constant(part), part)

    /** Adds [part], which moves with the clock, and [now], its words at the entry's start. */
    fun moving(part: DynamicString, now: String): SpokenLine = add(part, now)

    private fun add(part: DynamicString, now: String): SpokenLine {
        val before = dynamic
        dynamic = before?.concat(DynamicString.constant(separator))?.concat(part) ?: part
        if (before != null) words.append(separator)
        words.append(now)
        return this
    }

    /** The words at the entry's start, all of them: the reader's fallback. */
    fun fallback(): String = words.toString()

    /** The dynamic value, for the tests' evaluator. */
    fun expression(): DynamicString = dynamic ?: DynamicString.constant("")

    /**
     * The line as complication text: dynamic where the system evaluates it, its words below. The
     * check is `Build.VERSION.SDK_INT`'s own, which lint reads; a line made for a level below 33
     * holds words alone, so its text is its words either way.
     */
    fun complicationText(): ComplicationText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        DynamicComplicationText(expression(), fallback())
    } else {
        text(fallback())
    }
}

/**
 * Whether the watch's system evaluates dynamic values at API level [sdk] (`Build.VERSION.SDK_INT`):
 * from Wear OS 4, API 33. Wear OS 3 (API 30) shows a dynamic value's fallback words for as long as
 * its entry lasts (P5.12 Step 1, emulator-5568).
 */
internal fun evaluatesDynamicValues(sdk: Int): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU
