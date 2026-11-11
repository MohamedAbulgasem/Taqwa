package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.TestFeeds.at

/**
 * How a plan becomes the timeline the library is given (`shape`, which `toTimeline` makes of
 * complication data): what is the default, what are the entries, and what an empty step says.
 * The library's own types need the watch, so the shape is read here with words standing for the
 * data: [NO_DATA] for an entry that says no data, [SETTINGS] for the day dial's settings alone,
 * and the step's own fields for typed data.
 */
class TimelineShapeTest {

    private val feed = TestFeeds.sample
    private val now = at(0, 14, 58)

    /** A plan's shape as a source without an empty form of its own makes it. */
    private fun <F : Any> List<Step<F>>.plain(): TimelineShape<String> =
        shape(noData = { NO_DATA }, empty = null) { _, fields -> fields.toString() }

    /** A plan's shape as the day dial makes it: an empty step is the face's settings alone. */
    private fun <F : Any> List<Step<F>>.dialLike(): TimelineShape<String> =
        shape(noData = { NO_DATA }, empty = { SETTINGS }) { _, fields -> fields.toString() }

    /** What the library refuses, and what a face left on a stale entry would show. */
    private fun assertTheLibraryTakesIt(shape: TimelineShape<String>, what: String) {
        if (shape.default == NO_DATA || shape.default == SETTINGS) {
            assertTrue(shape.entries.isEmpty(), "$what: an empty default stands alone")
        }
        shape.entries.zipWithNext().forEach { (one, next) ->
            assertEquals(one.until, next.from, "$what: entries follow one another")
        }
        shape.entries.forEach { assertTrue(it.from < it.until, "$what: an entry ends after it begins") }
    }

    @Test
    fun theStretchUnderWayIsTheDefaultAndTheRestAreEntries() {
        val plan = ComplicationTimelines.nextPrayer(feed, now)
        val shape = plan.plain()

        assertEquals(plan.first().fields.toString(), shape.default)
        assertEquals(plan.drop(1).map { it.from to it.until }, shape.entries.map { it.from to it.until })
        assertEquals(plan.drop(1).dropLast(1).map { it.fields.toString() }, shape.entries.dropLast(1).map { it.data })
        assertTheLibraryTakesIt(shape, "next prayer")
    }

    @Test
    fun everySourceButTheDialEndsInAnEntryThatSaysNoData() {
        for ((what, plan) in listOf(
            "next prayer" to ComplicationTimelines.nextPrayer(feed, now),
            "day" to ComplicationTimelines.day(feed, now),
            "dates" to ComplicationTimelines.dates(feed, now),
        )) {
            val shape = plan.plain()
            assertEquals(NO_DATA, shape.entries.last().data, "$what: the last entry blanks the face")
            val noData = shape.entries.indices.filter { shape.entries[it].data == NO_DATA }
            assertEquals(listOf(shape.entries.lastIndex), noData, "$what: only the last entry says no data")
            assertTrue(shape.default != NO_DATA, "$what: the default is the stretch under way")
            assertTheLibraryTakesIt(shape, what)
        }
    }

    @Test
    fun theDialEndsInItsSettingsAloneAndNeverInNoData() {
        val plan = ComplicationTimelines.dial(feed, now)
        val shape = plan.dialLike()

        // The last entry is the settings with a dial of nothing, from where the plan's answers
        // stop, for the month the empty step lasts: the face draws its empty frame from it and
        // keeps the phone's clock and direction.
        assertEquals(SETTINGS, shape.entries.last().data)
        assertEquals(plan.last().from to plan.last().until, shape.entries.last().from to shape.entries.last().until)
        assertTrue(shape.entries.none { it.data == NO_DATA }, "the dial never says no data while a feed is stored")
        assertEquals(1, shape.entries.count { it.data == SETTINGS }, "and is its settings alone only at the end")
        assertTheLibraryTakesIt(shape, "dial")
    }

    @Test
    fun aSpentFeedIsTheDialsSettingsAloneAndNoDataForTheOthers() {
        for (spent in listOf(at(29, 20, 24), at(31, 12, 0))) {
            val dial = ComplicationTimelines.dial(feed, spent).dialLike()
            assertEquals(TimelineShape(SETTINGS, emptyList()), dial, "dial spent at $spent")

            for (plan in listOf(
                ComplicationTimelines.nextPrayer(feed, spent),
                ComplicationTimelines.day(feed, spent),
                ComplicationTimelines.dates(feed, spent),
            )) {
                assertEquals(TimelineShape(NO_DATA, emptyList()), plan.plain(), "spent at $spent")
            }
        }
        // A feed with no days in it is the same as one that has run out.
        val nothing = feed.copy(days = emptyList())
        assertEquals(TimelineShape(SETTINGS, emptyList()), ComplicationTimelines.dial(nothing, now).dialLike())
        assertEquals(TimelineShape(NO_DATA, emptyList()), ComplicationTimelines.nextPrayer(nothing, now).plain())
    }

    @Test
    fun theDialsSettingsAreTheFeedsOwnWhateverTheDialSays() {
        // DayDialSource gives every step, the empty one too, the settings of the feed it was asked
        // with: the face's clock and direction are the phone's for as long as a feed is stored.
        val english = ComplicationFields.faceSettings(feed.ui)
        val saudi = ComplicationFields.faceSettings(TestFeeds.arabicSaudi().ui)
        assertTrue(english != saudi, "two phones, two sets of settings")
        for (ui in listOf(feed.ui, TestFeeds.arabicSaudi().ui)) {
            val settings = ComplicationFields.faceSettings(ui)
            val shape = ComplicationTimelines.dial(feed, now)
                .shape(noData = { NO_DATA }, empty = { "settings $settings" }) { _, fields -> "$fields $settings" }
            assertTrue(shape.default.endsWith(settings.toString()))
            assertTrue(shape.entries.all { it.data.endsWith(settings.toString()) }, "every entry carries $settings")
        }
    }

    private companion object {
        const val NO_DATA = "no data"
        const val SETTINGS = "settings alone"
    }
}
