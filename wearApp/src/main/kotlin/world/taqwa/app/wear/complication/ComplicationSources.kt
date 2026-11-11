package world.taqwa.app.wear.complication

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The tag the complication sources log under. */
internal const val COMPLICATION_TAG = "TaqwaComplications"

/**
 * The five sources, as the rest of the app knows them. The face's fixed slots name them in its
 * watch face XML, so their class names are part of the face's contract
 * (`world.taqwa.app/world.taqwa.app.wear.complication.NextPrayerSource` and so on): renaming one
 * is a change to the face too.
 */
internal object ComplicationSources {

    private val all = listOf<Class<*>>(
        NextPrayerSource::class.java,
        DaySource::class.java,
        DayDialSource::class.java,
        DatesSource::class.java,
        MarkSource::class.java,
    )

    /**
     * Asks the system to request every source again for every complication that shows it: called
     * whenever a feed is stored, because the timelines a source served were drawn from the feed
     * that was there, and cut off two days on from the time they were asked. Nothing happens for a
     * source no face is showing. A request that cannot be made is logged and goes no further, since
     * the feed that prompted it is stored and the system asks again by itself, as often as it
     * pleases (the sources' update period asks for every six hours).
     *
     * [why] names the caller in the log (`app start`, `tile`, `daily`, `feed`): once every source
     * has been asked, it logs `asked again (<why>)` at `Log.i`, which the 12-hour observation reads
     * to tell the safety net's requests from the system's own (plan 5 Task 12), and answers true;
     * false when a source could not be asked (`SurfaceMemory` then keeps no stamp).
     */
    fun requestUpdates(context: Context, why: String = "unknown"): Boolean {
        var failed = false
        for (source in all) {
            try {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, source))
                    .requestUpdateAll()
            } catch (e: RuntimeException) {
                failed = true
                Log.w(COMPLICATION_TAG, "could not ask for ${source.simpleName} to be requested again", e)
            }
        }
        if (!failed) Log.i(COMPLICATION_TAG, "asked again ($why)")
        return !failed
    }
}

/** A [ComplicationText] of plain words: every static string a source shows is one of these. */
internal fun text(words: CharSequence): ComplicationText = PlainComplicationText.Builder(words).build()

/**
 * The timeline the library takes for a [plan] (`ComplicationTimelines`), as [shape] lays it out,
 * made of complication data: [typed] makes the data of a step whose fields it has, in the type
 * that was asked for, and [empty], when it is given, what an empty step says instead of no data.
 */
internal fun <F : Any> List<Step<F>>.toTimeline(
    empty: (() -> ComplicationData)? = null,
    typed: (step: Step<F>, fields: F) -> ComplicationData,
): ComplicationDataTimeline {
    val shape = shape(noData = { NoDataComplicationData() }, empty = empty, typed = typed)
    return ComplicationDataTimeline(
        shape.default,
        shape.entries.map { entry ->
            val during = TimeInterval(Instant.ofEpochSecond(entry.from), Instant.ofEpochSecond(entry.until))
            TimelineEntry(during, entry.data)
        },
    )
}

/** A timeline as [shape] lays it out: the [default], and the [entries] that follow it. */
internal data class TimelineShape<out D : Any>(val default: D, val entries: List<ShapedEntry<D>>)

/** One entry of a [TimelineShape]: [data] from [from] until [until], in epoch seconds. */
internal data class ShapedEntry<out D : Any>(val from: Long, val until: Long, val data: D)

/**
 * How a plan becomes a timeline, apart from the library's types, so that a unit test can read it
 * (`TimelineShapeTest`): the first step, the stretch that is under way, is the default; the rest,
 * the empty step at the end included, are entries, each its step's from and until. [typed] makes
 * what a step with fields says, [noData] what says nothing, and [empty], when it is given, what an
 * empty step says instead of [noData].
 *
 * A plan that is only the empty step is the one case where the default says no data, and then it
 * stands alone. The library refuses typed entries under such a default, and refuses them by
 * throwing inside its update path, where nothing catches it and the app's process ends.
 *
 * [empty] is typed data that holds whatever the moment, as the day dial's settings do
 * (`DayDialSource`, ledger Ruling 29). A plan that is only the empty step is then that data
 * alone, as the default, and the plan's last entry is that data and not [noData].
 */
internal fun <F : Any, D : Any> List<Step<F>>.shape(
    noData: () -> D,
    empty: (() -> D)?,
    typed: (step: Step<F>, fields: F) -> D,
): TimelineShape<D> {
    val first = first()
    val firstFields = first.fields ?: return TimelineShape(empty?.invoke() ?: noData(), emptyList())
    val entries = drop(1).map { step ->
        val fields = step.fields
        ShapedEntry(step.from, step.until, if (fields == null) empty?.invoke() ?: noData() else typed(step, fields))
    }
    return TimelineShape(typed(first, firstFields), entries)
}

/** The timeline of a source with nothing to say: no feed, or one that has run out. */
internal fun nothingToSay(): ComplicationDataTimeline = ComplicationDataTimeline(NoDataComplicationData(), emptyList())

/**
 * What [answer] says, or [nothingToSay] when working it out throws: every source that reads the
 * feed answers through this. The answer is worked out from a stored feed, and one the rules were
 * never shown (a day out of order, a field the phone wrote wrong) could throw anywhere in that
 * work. Uncaught, the throw would end the app's process inside the library's request, and the face
 * would go on showing what it was last given, a period that has gone by. Said with no data, the
 * face shows its empty form until the next request or the next feed, and the throw is logged under
 * [source]'s name. A cancellation is let through: it is the system calling the request off.
 */
internal inline fun answerOrNothing(source: String, answer: () -> ComplicationDataTimeline?): ComplicationDataTimeline? =
    orOnFailure(answer, fallback = { nothingToSay() }) { e ->
        Log.w(COMPLICATION_TAG, "$source could not answer, so it says nothing", e)
    }

/**
 * [answerOrNothing], worked out on [Dispatchers.Default]: every source that reads the feed answers
 * through this. The library starts each request on the main thread
 * (`SuspendingTimelineComplicationDataSourceService` launches it on `Dispatchers.Main.immediate`),
 * and an answer is ten prayer periods of plans and complication data, the moon's picture for each
 * date, and, in a process the request started, the store's first read of the feed's file. None of
 * it needs the main thread: the app's store (made under its `lazy`'s lock) and its `StateFlow`, the
 * app's resources, the library's data builders, the dynamic values and a `Bitmap` may all be used
 * from any thread, and the plans keep nothing between calls, so sources asked at the same moment
 * may work side by side. The answer goes back to the library on the main thread, as before.
 */
internal suspend fun answerOffMain(source: String, answer: () -> ComplicationDataTimeline?): ComplicationDataTimeline? =
    withContext(Dispatchers.Default) { answerOrNothing(source, answer) }

/**
 * [compute]'s value, or [fallback]'s when [compute] throws, after [report] is told why. A
 * cancellation is thrown on and never reported. Kept apart from the library's types so that a unit
 * test can hold it (`ComplicationSourcesTest`).
 */
internal inline fun <T> orOnFailure(compute: () -> T, fallback: () -> T, report: (Exception) -> Unit): T =
    try {
        compute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        report(e)
        fallback()
    }

/**
 * Logs what a source served, at `Log.d` under [COMPLICATION_TAG], only while that tag is enabled
 * on the watch ([timelineLog]): `adb -s <watch> shell setprop log.tag.TaqwaComplications DEBUG`,
 * which `setprop log.tag.TaqwaComplications INFO` or a reboot ends. It is read at every answer, so
 * the setprop takes hold at the next request. Every source that reads the feed logs through this,
 * and nothing else writes the line.
 *
 * [tagEnabled] reads the tag (`Log.isLoggable(COMPLICATION_TAG, Log.DEBUG)`) and [write] writes
 * the line (`Log.d`): the sources take both as they are, and a host test passes its own, so that
 * the gate is held at this call too (`TimelineLogTest`, Ruling 74 item 3).
 */
internal fun logTimeline(
    source: String,
    type: ComplicationType?,
    plan: List<Step<*>>,
    tagEnabled: () -> Boolean = { Log.isLoggable(COMPLICATION_TAG, Log.DEBUG) },
    write: (String) -> Unit = { Log.d(COMPLICATION_TAG, it) },
) {
    timelineLog(tagEnabled(), source, type, plan.size, plan.saysUntil())?.let(write)
}

/**
 * What a source logs of each answer (plan 5 Task 12 Step 0), or null while [tagEnabled] is false,
 * which is every build's default, a debug build's too (Ruling 72 item 2): the [source], the [type]
 * it was asked for when it serves more than one, how many [steps] its plan holds, the empty one
 * included, and [lastUntil], where the timeline stops saying anything ([saysUntil]), in UTC, or
 * `none` for a plan that is only the empty step. For example
 * `next prayer, RANGED_VALUE: 13 steps, last until 2026-11-02T03:58:00Z`. The 12-hour observation
 * (Ruling 22) reads from these lines alone when the face would run out, and whether a later request
 * reached further, with the tag set first (Task 12 Step 2).
 *
 * The time is a prayer's start, and a prayer's start with its date says roughly where on Earth the
 * wearer is. Anything that reads the watch's log could read it: a bug report taken from the watch
 * carries the log whole. So it is written only on request, as the compass's line is in a store
 * build (`qiblaLogOn`), and the promise holds that only the wearer's own devices see where they are
 * (Ruling 39). A line with no instant (a request asked again, a source that could not answer) is
 * written as before.
 */
internal fun timelineLog(tagEnabled: Boolean, source: String, type: ComplicationType?, steps: Int, lastUntil: Long?): String? =
    if (tagEnabled) timelineLogLine(source, type, steps, lastUntil) else null

/** [timelineLog]'s line, whole. */
private fun timelineLogLine(source: String, type: ComplicationType?, steps: Int, lastUntil: Long?): String {
    val asked = if (type == null) source else "$source, ${type.name}"
    val until = lastUntil?.let { Instant.ofEpochSecond(it).toString() } ?: "none"
    return "$asked: $steps ${if (steps == 1) "step" else "steps"}, last until $until"
}

/**
 * Where a plan stops saying anything: the end of its last step with fields, which is where the
 * empty step at the end begins (its own end is a month on, and never drawn); null for a plan that
 * is only the empty step.
 */
internal fun List<Step<*>>.saysUntil(): Long? = lastOrNull { it.fields != null }?.until
