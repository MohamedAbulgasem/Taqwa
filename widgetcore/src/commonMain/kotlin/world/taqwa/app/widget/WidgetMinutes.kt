package world.taqwa.app.widget

/**
 * One minute of a widget's life: from [showAtEpochSeconds], a whole minute, until the next one,
 * the widget shows what it reads at [evaluateAtEpochSeconds], the middle of that minute.
 */
data class WidgetMinute(val showAtEpochSeconds: Long, val evaluateAtEpochSeconds: Long)

/**
 * When a widget draws a minute, and at which moment it works that minute out (B36).
 *
 * Every instant the engine produces is a whole minute (`PrayerDay`), so nothing a widget shows
 * changes inside a minute: whichever second of it the widget is looked at, the next prayer, the
 * highlighted row and the countdown truncated as the Prayer screen truncates it are the same. The
 * middle of the minute reads exactly that, and is the one moment that does so whatever its
 * neighbours do. The two shapes it replaces each got it wrong:
 *
 *  - an entry dated on a ragged second (`now + k·60`) is still on screen for part of the next
 *    minute, showing the old digit, and when a prayer falls inside it the entry goes on saying
 *    "Asr in 0:00" for up to a minute after Asr has begun;
 *  - an entry worked out on its own whole minute reads one minute too many for all of it,
 *    because at 14:58:00 Asr at 15:00 is still two whole minutes away and at 14:58:01 it is not.
 */
object WidgetMinutes {

    /** The whole minute holding [epochSeconds]; floored, so a clock before 1970 is no exception. */
    fun startOfMinute(epochSeconds: Long): Long = epochSeconds.floorDiv(60L) * 60L

    /**
     * The moment to work out a single draw at [epochSeconds]: the middle of its minute. The
     * Android widget draws when an alarm fires, and its alarms aim at whole minutes: the prayer
     * alarm is exact on the prayer's own minute, and the rolling refresh is often delivered in
     * the first second of a five-minute boundary. Worked out on that instant, the draw would read
     * one too high until the next one.
     */
    fun evaluationInstant(epochSeconds: Long): Long = startOfMinute(epochSeconds) + 30L

    /**
     * [count] minutes for a timeline that starts now: the first is the minute holding
     * [nowEpochSeconds], already under way, so the widget is right from the reload on, and each
     * after it is the next whole minute.
     */
    fun timeline(nowEpochSeconds: Long, count: Int): List<WidgetMinute> {
        val first = startOfMinute(nowEpochSeconds)
        return (0 until count).map { k ->
            val showAt = first + k * 60L
            WidgetMinute(showAtEpochSeconds = showAt, evaluateAtEpochSeconds = evaluationInstant(showAt))
        }
    }
}
