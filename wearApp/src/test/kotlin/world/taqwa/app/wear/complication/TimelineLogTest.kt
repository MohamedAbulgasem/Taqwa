package world.taqwa.app.wear.complication

import androidx.wear.watchface.complications.data.ComplicationType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The sources' log line (plan 5 Task 12 Step 0): each answer names how many steps it served and
 * where its timeline stops saying anything, so that the 12-hour observation (Ruling 22) can read
 * from the log alone when the face would have run out, and whether a later request reached further.
 * That end is a prayer's start, which says roughly where the wearer is, so the line is written
 * only while its tag is enabled on the watch, in every build (Ruling 72 item 2).
 */
class TimelineLogTest {

    private val end = Instant.parse("2026-11-02T03:58:00Z").epochSecond

    @Test
    fun theLogLineNamesTheTimelinesEnd() {
        assertEquals(
            "next prayer, RANGED_VALUE: 10 steps, last until 2026-11-02T03:58:00Z",
            timelineLog(tagEnabled = true, "next prayer", ComplicationType.RANGED_VALUE, steps = 10, lastUntil = end),
        )
        // A source of one type names none, as the day's and the day dial's lines always have.
        assertEquals("day: 13 steps, last until 2026-11-02T03:58:00Z", timelineLog(tagEnabled = true, "day", null, steps = 13, lastUntil = end))
        // A plan that is only the empty step says nothing at all: no feed, or one that has run out.
        assertEquals(
            "dates, LONG_TEXT: 1 step, last until none",
            timelineLog(tagEnabled = true, "dates", ComplicationType.LONG_TEXT, steps = 1, lastUntil = null),
        )
    }

    @Test
    fun theLineIsWrittenOnlyWhileItsTagIsEnabled() {
        // `Log.isLoggable("TaqwaComplications", Log.DEBUG)`, false until
        // `adb shell setprop log.tag.TaqwaComplications DEBUG`, in a debug build as in a store build:
        // with the tag off a source writes nothing of its answer, its timeline's end least of all.
        assertNull(timelineLog(tagEnabled = false, "next prayer", ComplicationType.RANGED_VALUE, steps = 10, lastUntil = end))
        assertNull(timelineLog(tagEnabled = false, "day", null, steps = 13, lastUntil = end))
        assertNull(timelineLog(tagEnabled = false, "dates", ComplicationType.LONG_TEXT, steps = 1, lastUntil = null))
    }

    @Test
    fun aSourceWritesItsLineOnlyWhenTheTagReadsEnabledAtThatAnswer() {
        // logTimeline, which every source that reads the feed answers through, asks the reader
        // (Log.isLoggable on the watch) at each answer and writes nothing while it says no: the
        // gate is the call's own, not only timelineLog's (Ruling 74 item 3).
        val plan = listOf(Step(100, 200, "Dhuhr"), Step(200, 300, "Asr"), Step(300, 300 + 30L * 86_400L, null))
        val written = mutableListOf<String>()
        var asked = 0
        logTimeline("next prayer", ComplicationType.RANGED_VALUE, plan, tagEnabled = { asked++; false }, write = written::add)
        logTimeline("day", null, plan, tagEnabled = { asked++; false }, write = written::add)
        assertEquals(emptyList(), written)
        assertEquals(2, asked)
        // The setprop takes hold at the next answer, with no restart.
        logTimeline("next prayer", ComplicationType.RANGED_VALUE, plan, tagEnabled = { asked++; true }, write = written::add)
        assertEquals(listOf("next prayer, RANGED_VALUE: 3 steps, last until 1970-01-01T00:05:00Z"), written)
        assertEquals(3, asked)
    }

    @Test
    fun theTimelinesEndIsWhereItsLastStepThatSaysSomethingEnds() {
        // The empty step at the end is a month long, so its own end is not when the face runs out:
        // its start is.
        val plan = listOf(Step(100, 200, "Dhuhr"), Step(200, 300, "Asr"), Step(300, 300 + 30L * 86_400L, null))
        assertEquals(300L, plan.saysUntil())

        assertNull(listOf(Step<String>(100, 100 + 30L * 86_400L, null)).saysUntil())
    }
}
