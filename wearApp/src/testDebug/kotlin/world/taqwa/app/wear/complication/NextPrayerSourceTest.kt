package world.taqwa.app.wear.complication

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.DynamicComplicationText
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.wear.FeedWriteResult
import world.taqwa.app.wear.SampleFeed
import world.taqwa.app.wear.TaqwaWearApp
import world.taqwa.app.wear.TestFeeds

/**
 * [NextPrayerSource] itself, run on Robolectric's Android at the two API levels it branches on:
 * Wear OS 3's 30, where the system evaluates no dynamic value, and Wear OS 4's 33, where it does
 * (Ruling 74 item 3). `ClockFormTest` holds the pure functions at both levels with the level
 * passed in; this holds the source's own reading of `Build.VERSION.SDK_INT`, in the request's
 * ring and long text and in the picker's preview, so a source that read any other level (33 always,
 * say) fails here and not only on an old watch. It holds the short text's request too, which takes
 * no level and answers alike at both (Ruling 76 item 3).
 *
 * The source answers from the app's feed store, which holds the debug build's sample for today
 * ([SampleFeed.install], the debug build's own), so the answer is a real timeline of ten periods
 * from the clock's now. Each entry is checked against the rules at its own start, the default
 * at the moment before the first entry begins, so no prayer passing during the test can move it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = NextPrayerSourceTest.StoreOnlyApp::class)
class NextPrayerSourceTest {

    /**
     * The app with its store and none of its start's work (the daily nudge, the phone's handshake,
     * the face's refresh), which needs Play services and the watch's system.
     */
    class StoreOnlyApp : TaqwaWearApp() {
        override fun onCreate() {}
    }

    private val app: TaqwaWearApp get() = RuntimeEnvironment.getApplication() as TaqwaWearApp

    /** The source, with the sample called [sample] (`TestFeeds.other`; "" the first) for today. */
    private fun sourceWith(sample: String = ""): Pair<NextPrayerSource, WatchFeed> {
        val json = if (sample.isEmpty()) TestFeeds.sampleJson else TestFeeds.json(TestFeeds.other(sample))
        assertEquals(FeedWriteResult.ACCEPTED, SampleFeed.install(app.filesDir, app.feedStore, System.currentTimeMillis() / 1000, json))
        return Robolectric.buildService(NextPrayerSource::class.java).create().get() to assertNotNull(app.feedStore.changes.value)
    }

    private fun NextPrayerSource.ask(type: ComplicationType): ComplicationDataTimeline =
        assertNotNull(runBlocking { onComplicationRequest(ComplicationRequest(1, type, false)) }, "$type")

    /**
     * Each typed answer of [timeline] with the moment it is checked at: the default at the second
     * before the first entry, the entries at their starts. The empty step's no data is left out.
     */
    private fun answers(timeline: ComplicationDataTimeline): List<Pair<Long, ComplicationData>> {
        val entries = timeline.timelineEntries.toList()
        assertTrue(entries.size >= 10, "${entries.size} entries")
        val all = listOf(entries.first().validity.start.epochSecond - 1 to timeline.defaultComplicationData) +
            entries.map { it.validity.start.epochSecond to it.complicationData }
        return all.filter { (_, data) -> data.type != ComplicationType.NO_DATA }.also { assertTrue(it.size >= 10) }
    }

    /** What [text] shows before the system works it out: its words, or a dynamic one's fallback. */
    private fun words(text: ComplicationText?): String = when (text) {
        null -> error("no text")
        is DynamicComplicationText -> text.fallbackValue.toString()
        else -> text.getTextAt(app.resources, Instant.EPOCH).toString()
    }

    /** What [text] says at [moment] as the system works it out. */
    private fun shown(text: ComplicationText?, moment: Long): String =
        DynamicEvaluator(moment).string(assertIs<DynamicComplicationText>(text).dynamicValue.toDynamicStringProto())

    private fun fieldsAt(feed: WatchFeed, moment: Long): NextPrayerFields =
        assertNotNull(ComplicationFields.nextPrayer(feed, moment), "at $moment")

    // ---- Wear OS 3: the clock form

    @Test
    @Config(sdk = [30])
    fun onWearOsThreeTheRingAndTheLongTextSayThePrayerAndItsTime() {
        for (sample in listOf("", "ar-sa")) {
            val (source, feed) = sourceWith(sample)
            for ((moment, data) in answers(source.ask(ComplicationType.RANGED_VALUE))) {
                val fields = fieldsAt(feed, moment)
                val ring = assertIs<RangedValueComplicationData>(data)
                assertNull(ring.dynamicValue, "a ring below API 33 has no dynamic value, at $moment")
                assertEquals(fields.label, words(ring.title))
                assertEquals(fields.time, words(ring.text))
                assertFalse(ring.contentDescription is DynamicComplicationText, "at $moment")
                assertEquals(ComplicationFields.prayerSpoken(fields), words(ring.contentDescription), "${feed.ui.language} at $moment")
            }
            for ((moment, data) in answers(source.ask(ComplicationType.LONG_TEXT))) {
                val fields = fieldsAt(feed, moment)
                val long = assertIs<LongTextComplicationData>(data)
                assertEquals(fields.label, words(long.title))
                assertFalse(long.text is DynamicComplicationText, "at $moment")
                assertEquals(fields.time, words(long.text), "${feed.ui.language} at $moment")
                assertEquals(ComplicationFields.prayerSpoken(fields), words(long.contentDescription), "${feed.ui.language} at $moment")
            }
        }
    }

    @Test
    @Config(sdk = [30])
    fun onWearOsThreeThePickersPreviewShowsAndSaysTheClockForm() {
        val (source, _) = sourceWith()
        val ring = assertIs<RangedValueComplicationData>(source.getPreviewData(ComplicationType.RANGED_VALUE))
        assertEquals("Asr, 16:10", words(ring.contentDescription))
        val long = assertIs<LongTextComplicationData>(source.getPreviewData(ComplicationType.LONG_TEXT))
        assertEquals("16:10", words(long.text))
        assertEquals("Asr, 16:10", words(long.contentDescription))
        val short = assertIs<ShortTextComplicationData>(source.getPreviewData(ComplicationType.SHORT_TEXT))
        assertEquals("Asr, 16:10", words(short.contentDescription))
    }

    @Test
    @Config(sdk = [30], qualifiers = "ar")
    fun anArabicWatchsPreviewIsJoinedByTheArabicComma() {
        val (source, _) = sourceWith()
        val long = assertIs<LongTextComplicationData>(source.getPreviewData(ComplicationType.LONG_TEXT))
        assertEquals("العصر، 16:10", words(long.contentDescription))
        val short = assertIs<ShortTextComplicationData>(source.getPreviewData(ComplicationType.SHORT_TEXT))
        assertEquals("العصر، 16:10", words(short.contentDescription))
    }

    // ---- Wear OS 4: the countdown, as before

    @Test
    @Config(sdk = [33])
    fun onWearOsFourTheRingAndTheLongTextCountDown() {
        for (sample in listOf("", "ar-sa")) {
            val (source, feed) = sourceWith(sample)
            for ((moment, data) in answers(source.ask(ComplicationType.RANGED_VALUE))) {
                val fields = fieldsAt(feed, moment)
                val ring = assertIs<RangedValueComplicationData>(data)
                assertNotNull(ring.dynamicValue, "a ring from API 33 moves by itself, at $moment")
                assertEquals(fields.time, words(ring.text))
                assertEquals(
                    WatchRules.spokenCountdown(feed, fields.name, fields.spokenTime, fields.endAt - moment),
                    shown(ring.contentDescription, moment),
                    "${feed.ui.language} at $moment",
                )
            }
            for ((moment, data) in answers(source.ask(ComplicationType.LONG_TEXT))) {
                val fields = fieldsAt(feed, moment)
                val long = assertIs<LongTextComplicationData>(data)
                assertEquals(WatchRules.countdown(feed, fields.endAt - moment), shown(long.text, moment), "${feed.ui.language} at $moment")
                assertEquals(
                    WatchRules.spokenCountdown(feed, fields.name, fields.spokenTime, fields.endAt - moment),
                    shown(long.contentDescription, moment),
                )
            }
        }
    }

    @Test
    @Config(sdk = [33])
    fun onWearOsFourThePickersPreviewCountsDown() {
        val (source, _) = sourceWith()
        val ring = assertIs<RangedValueComplicationData>(source.getPreviewData(ComplicationType.RANGED_VALUE))
        assertEquals("Asr, 16:10, in 1 h 12 min", words(ring.contentDescription))
        val long = assertIs<LongTextComplicationData>(source.getPreviewData(ComplicationType.LONG_TEXT))
        assertEquals("in 1 h 12 min", words(long.text))
        assertEquals("Asr, 16:10, in 1 h 12 min", words(long.contentDescription))
        val short = assertIs<ShortTextComplicationData>(source.getPreviewData(ComplicationType.SHORT_TEXT))
        assertEquals("Asr, 16:10", words(short.contentDescription))
    }

    // ---- The short text, the same two words and the same line at both levels (Ruling 76 item 3)

    @Test
    @Config(sdk = [30])
    fun onWearOsThreeTheShortTextSaysThePrayerAndItsTime() = assertShortTextSaysThePrayerAndItsTime()

    @Test
    @Config(sdk = [33])
    fun onWearOsFourTheShortTextSaysThePrayerAndItsTime() = assertShortTextSaysThePrayerAndItsTime()

    private fun assertShortTextSaysThePrayerAndItsTime() {
        for (sample in listOf("", "ar-sa")) {
            val (source, feed) = sourceWith(sample)
            for ((moment, data) in answers(source.ask(ComplicationType.SHORT_TEXT))) {
                val fields = fieldsAt(feed, moment)
                val short = assertIs<ShortTextComplicationData>(data)
                assertEquals(fields.label, words(short.title))
                assertEquals(fields.time, words(short.text))
                assertFalse(short.contentDescription is DynamicComplicationText, "at $moment")
                assertEquals(ComplicationFields.prayerSpoken(fields), words(short.contentDescription), "${feed.ui.language} at $moment")
            }
        }
    }

    // ---- The sources' log line, at the source's own call (Ruling 72 item 2)

    @Test
    @Config(sdk = [30])
    fun theSourceLogsWhereItsTimelineEndsOnlyWhileItsTagIsEnabled() {
        val (source, _) = sourceWith()
        fun lines() = ShadowLog.getLogsForTag(COMPLICATION_TAG).filter { "last until" in it.msg }
        // As on a watch where nothing was set: Log.isLoggable(tag, DEBUG) is false.
        assertFalse(Log.isLoggable(COMPLICATION_TAG, Log.DEBUG))
        source.ask(ComplicationType.RANGED_VALUE)
        assertEquals(emptyList(), lines().map { it.msg })
        // setprop log.tag.TaqwaComplications DEBUG: the next answer writes its line, at Log.d.
        ShadowLog.setLoggable(COMPLICATION_TAG, Log.DEBUG)
        source.ask(ComplicationType.RANGED_VALUE)
        val line = lines().single()
        assertEquals(Log.DEBUG, line.type)
        assertTrue(line.msg.startsWith("next prayer, RANGED_VALUE: "), line.msg)
    }
}
