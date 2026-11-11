package world.taqwa.app.wear.complication

import android.os.Build
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TaqwaWearApp

/**
 * The next prayer (spec 2.3), for the Taqwa face's slot A and for any face that takes a ring, a
 * short text or a long one:
 *
 * - **RANGED_VALUE**: the ring, from the previous start to the next, with the prayer in the title
 *   (`ASR`) and its time in the text (`16:10`), which is what a round slot has room for;
 * - **SHORT_TEXT**: the same two words;
 * - **LONG_TEXT**: the prayer in the title and the countdown in the text (`in 1 h 12 min`), which
 *   needs a slot as wide as a pill; below Wear OS 4 its time (`16:10`), as said below.
 *
 * The ring and the countdown are dynamic values that the system evaluates itself, a minute at a
 * time, while this app is not running; a timeline of ten prayer periods carries them, and the next
 * period takes over at its start without the app too (`ComplicationTimelines`, `CountdownExpression`).
 * What a screen reader says (Task 12) is the prayer and its time in the name's own case, `Dhuhr,
 * 12:33` (`ComplicationFields.prayerSpoken`), and for the ring and the long text the phone's spoken
 * countdown, `Dhuhr in 7 hours 10 minutes, at 12:33` ([nextPrayerSaid]): the ring is how far the
 * period has got, and the countdown is what is left of it. The countdown in a description is a dynamic value
 * too, which the system works out as it does the text, and the face runtime gives the reader the
 * new words each minute (on the Wear OS 6 emulator). On the Taqwa face the ring's description is
 * the item a screen reader reads after the time.
 * The app runs when a face first takes the source, when the system asks again (the manifest's
 * update period wishes for every six hours, which the system need not keep) and when a feed is
 * stored. The title and the time are static words, because a period fixes them.
 *
 * Below Wear OS 4 (API 33) the system cannot evaluate dynamic values ([evaluatesDynamicValues]):
 * a dynamic value's words at the entry's start stay for the whole period, a countdown frozen for
 * hours (P5.12 Step 1 on Wear OS 3). So there the long text and every line a screen reader is told
 * take the clock form, the prayer and its time with no countdown, `ASR` over `16:10`, said `Asr,
 * 16:10`, which is true however long the entry is shown, as the Tile draws on a renderer below
 * schema 1.2 (Ruling 72 item 1, plan 5's call 6 (b); Ruling 65 item 2). The ring alone stays at its
 * value at the entry's start: moving it in steps would take entries of its own, and the timeline's
 * budget is 2 to 16 entries (`ComplicationTimelinesTest.aTimelineIsAFewEntriesAndNotAMonthsWorth`),
 * of which this plan already takes 13 (ten periods, the night's midnight steps and the empty one),
 * so one more step a period (23) would pass it. The budget is there because from API 33 each entry
 * carries its own copy of the dynamic countdown, a few kilobytes of protobuf in the text or value
 * and again in the description (`SpokenTest.theSpokenSentenceIsSmallEnoughToSendTenAtATime`), and
 * one plan serves every type (Ruling 28 of the close). The Taqwa face needs Wear OS 6 and is not
 * affected.
 */
class NextPrayerSource : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? =
        answerOffMain("next prayer") { answer(request) }

    private fun answer(request: ComplicationRequest): ComplicationDataTimeline? {
        val type = request.complicationType
        if (type !in SUPPORTED) return null
        val feed = (application as TaqwaWearApp).feedStore.changes.value ?: return nothingToSay()
        val now = System.currentTimeMillis() / 1000

        val plan = ComplicationTimelines.nextPrayer(feed, now)
        logTimeline("next prayer", type, plan)
        val sdk = Build.VERSION.SDK_INT
        return when (type) {
            ComplicationType.RANGED_VALUE -> plan.toTimeline { step, fields -> ranged(fields, fields.endAt - step.from, feed, sdk) }
            ComplicationType.SHORT_TEXT -> plan.toTimeline { _, fields -> shortText(fields) }
            else -> plan.toTimeline { step, fields -> longText(fields, feed, secondsLeft = fields.endAt - step.from, sdk) }
        }
    }

    /**
     * The ring's range is the period itself, in minutes from the date's Dhuhr (`dialStart` to
     * `dialEnd`), and its value the moment: any face draws the share between them, which is the
     * period's progress, and the Taqwa face's Sun style reads the same numbers as places on its day
     * dial, where Dhuhr is twelve o'clock. Below API 33 the value is the entry's start's, and stays.
     */
    private fun ranged(fields: NextPrayerFields, secondsLeft: Long, feed: WatchFeed, sdk: Int): ComplicationData {
        val description = nextPrayerSaid(fields, feed, secondsLeft, sdk).complicationText()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RangedValueComplicationData.Builder(
                CountdownExpression.ring(fields.startAt, fields.endAt, fields.dhuhrAt),
                fields.dialNow,
                fields.dialStart,
                fields.dialEnd,
                description,
            )
        } else {
            RangedValueComplicationData.Builder(fields.dialNow, fields.dialStart, fields.dialEnd, description)
        }
        return builder.setTitle(text(fields.label)).setText(text(fields.time)).build()
    }

    private fun shortText(fields: NextPrayerFields): ComplicationData =
        ShortTextComplicationData.Builder(text(fields.time), text(ComplicationFields.prayerSpoken(fields)))
            .setTitle(text(fields.label))
            .build()

    /**
     * The prayer in the title and, in the text, the countdown from Wear OS 4 (API 33), a dynamic
     * value, or the prayer's time below it ([nextPrayerLongText]); a screen reader says the prayer,
     * its time and the countdown moving as the text does, or below API 33 the prayer and its time.
     */
    private fun longText(fields: NextPrayerFields, feed: WatchFeed, secondsLeft: Long, sdk: Int): ComplicationData =
        LongTextComplicationData.Builder(
            nextPrayerLongText(fields, feed, secondsLeft, sdk).complicationText(),
            nextPrayerSaid(fields, feed, secondsLeft, sdk).complicationText(),
        )
            .setTitle(text(fields.label))
            .build()

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val label = getString(R.string.preview_prayer_label)
        val time = getString(R.string.preview_prayer_time)
        val preview = nextPrayerPreview(
            getString(R.string.preview_prayer_name),
            time,
            getString(R.string.preview_countdown),
            Build.VERSION.SDK_INT,
            rtl = previewRightToLeft(resources.configuration.locales[0].language),
        )
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(PREVIEW_NOW, PREVIEW_START, PREVIEW_END, text(preview.said))
                    .setTitle(text(label))
                    .setText(text(time))
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(time), text(preview.prayerSaid)).setTitle(text(label)).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(preview.longText), text(preview.said))
                    .setTitle(text(label))
                    .build()
            else -> null
        }
    }

    private companion object {
        val SUPPORTED = setOf(ComplicationType.RANGED_VALUE, ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT)

        /** 145 of the 217 minutes from Dhuhr to Asr, in minutes from Dhuhr: the preview is the mockups' 14:58. */
        const val PREVIEW_START = 0f
        const val PREVIEW_END = 217f
        const val PREVIEW_NOW = 145f
    }
}

/**
 * What a screen reader says for [NextPrayerSource]'s ring and long text, [secondsLeft] being the
 * seconds left at the entry's start: the phone's spoken sentence of the prayer, the time left and
 * its clock time, `Dhuhr in 7 hours 10 minutes, at 12:33`, hours and minutes in full words and the
 * plural each number takes (`WatchUi.spoken`, `WatchRules.spokenCountdown`), the time left moving as
 * the text does. The clock time is [NextPrayerFields.spokenTime], so a twelve-hour phone's is said
 * with its day period, `at 12:33 PM`. A feed written before the phone sent those words gives the
 * screen's own, the prayer and its time and then the countdown, `Dhuhr, 12:33, in 7 h 10 min`,
 * joined by the phone's comma.
 *
 * At an API level [sdk] below 33, where nothing moves ([evaluatesDynamicValues]), it is the clock
 * form, the prayer and its time as the short text says them (`ComplicationFields.prayerSpoken`),
 * `Dhuhr, 12:33`, true for the whole period (Ruling 72 item 1).
 */
internal fun nextPrayerSaid(fields: NextPrayerFields, feed: WatchFeed, secondsLeft: Long, sdk: Int): SpokenLine {
    val ui = feed.ui
    val line = SpokenLine(ComplicationFields.spokenSeparator(ui))
    if (!evaluatesDynamicValues(sdk)) return line.words(ComplicationFields.prayerSpoken(fields))
    val spoken = ui.spoken
    val words = WatchRules.spokenCountdown(feed, fields.name, fields.spokenTime, secondsLeft)
    if (spoken != null && words != null) {
        return line.moving(CountdownExpression.spoken(spoken, ui.digitZero, fields.name, fields.spokenTime, fields.endAt, secondsLeft), words)
    }
    return line
        .words(ComplicationFields.prayerSpoken(fields))
        .moving(CountdownExpression.countdown(ui.countdown, ui.digitZero, fields.endAt, secondsLeft), fields.countdown)
}

/**
 * The text of [NextPrayerSource]'s long text, [secondsLeft] being the seconds left at the entry's
 * start: the countdown, `in 1 h 12 min`, a dynamic value from API 33 ([sdk]); below it, where the
 * countdown would keep its first words for the whole period, the prayer's time, `16:10`, beside
 * the title's `ASR`: the clock form (Ruling 72 item 1).
 */
internal fun nextPrayerLongText(fields: NextPrayerFields, feed: WatchFeed, secondsLeft: Long, sdk: Int): SpokenLine {
    val line = SpokenLine(separator = "")
    if (!evaluatesDynamicValues(sdk)) return line.words(fields.time)
    val ui = feed.ui
    return line.moving(CountdownExpression.countdown(ui.countdown, ui.digitZero, fields.endAt, secondsLeft), fields.countdown)
}

/**
 * What the picker's preview of the next prayer shows in a long text, what its ring and long text
 * say, and what its short text says ([prayerSaid], the prayer and its time at every level).
 */
internal data class NextPrayerPreview(val longText: String, val said: String, val prayerSaid: String)

/**
 * Whether the preview's words, the app's own resources in the watch's [language] (a `Locale`'s
 * language code), are written right to left: of the seven languages they ship in, Arabic and
 * Urdu. A watch in any other language reads the English or its own folder's, left to right.
 */
internal fun previewRightToLeft(language: String): Boolean = language == "ar" || language == "ur"

/**
 * The preview's words from the source's resources, [name] at [time] with [countdown] left: as the
 * slot will show and say them at the API level [sdk], the countdown from API 33 and the clock form
 * below it ([nextPrayerLongText], [nextPrayerSaid]), joined by the comma of the resources'
 * direction ([rtl], [previewRightToLeft]) as the slot joins the phone's
 * (`ComplicationFields.prayerSpoken`).
 */
internal fun nextPrayerPreview(name: String, time: String, countdown: String, sdk: Int, rtl: Boolean): NextPrayerPreview {
    val separator = ComplicationFields.spokenSeparator(rtl)
    val prayer = name + separator + time
    return if (evaluatesDynamicValues(sdk)) {
        NextPrayerPreview(longText = countdown, said = prayer + separator + countdown, prayerSaid = prayer)
    } else {
        NextPrayerPreview(longText = time, said = prayer, prayerSaid = prayer)
    }
}
