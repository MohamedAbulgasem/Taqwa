package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.TestFeeds.at

/**
 * The timelines the sources serve, as the plain lists they are planned in. The library checks a
 * timeline only on the watch, inside its update path, where a timeline that breaks its rules ends
 * the app's process (the spike's report, evidence 4): so every rule it enforces, and the ones this
 * app adds to them, is held here, on a list, before any complication data is built.
 */
class ComplicationTimelinesTest {

    private val feed = TestFeeds.sample
    private val now = at(0, 14, 58)

    private val thirtyDays = 30L * 86_400L

    /** The rules every plan holds, whichever source it is for. */
    private fun <F : Any> assertRulesHold(plan: List<Step<F>>, from: Long, what: String) {
        assertTrue(plan.isNotEmpty(), "$what: a plan is never empty")
        assertEquals(from, plan.first().from, "$what: it begins where it was asked from")
        plan.forEachIndexed { index, step ->
            assertTrue(step.from < step.until, "$what, step $index: a step ends after it begins")
            if (index > 0) {
                // No overlap and no gap: the library picks the shortest of overlapping entries, and
                // a gap shows the default, which is a stale period.
                assertEquals(plan[index - 1].until, step.from, "$what, step $index: it follows the one before")
            }
        }
        // The runtime ignores validTimeRange, so what follows the last start is an entry that says
        // no data, and only that one; a default that says it would be refused with the others.
        assertEquals(listOf(plan.lastIndex), plan.indices.filter { plan[it].fields == null }, "$what: only the last step is empty")
        assertEquals(thirtyDays, plan.last().until - plan.last().from, "$what: and it lasts a month")
    }

    private fun <F : Any> stepAt(plan: List<Step<F>>, t: Long): Step<F> =
        plan.first { t >= it.from && t < it.until }

    // ---- The next prayer

    @Test
    fun nextPrayerHoldsTenPeriodsAndEndsEmpty() {
        val plan = ComplicationTimelines.nextPrayer(feed, now)

        assertRulesHold(plan, now, "next prayer")
        // Ten periods, the two nights each split at their midnight (the Sun's dial turns to the new
        // date there), then the empty entry.
        assertEquals(13, plan.size, "ten periods, two midnights, then the empty entry")
        assertEquals(
            listOf("ASR", "MAGHRIB", "ISHA", "FAJR", "FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA", "FAJR", "FAJR", "DHUHR"),
            plan.dropLast(1).map { it.fields!!.label },
        )
        assertEquals(10, plan.dropLast(1).count { it.fields!!.endAt == it.until }, "and ten of them end a period")
        assertEquals(at(0, 16, 10), plan.first().until, "the first period ends at Asr")
        assertEquals(at(2, 12, 33), plan.last().from, "and the tenth, two days on, at Dhuhr")
    }

    @Test
    fun eachPeriodIsTheOneTheRulesHaveAtItsFirstSecondAndLastSecond() {
        val plan = ComplicationTimelines.nextPrayer(feed, now)

        for (step in plan.dropLast(1)) {
            val fields = step.fields!!
            assertEquals(ComplicationFields.nextPrayer(feed, step.from), fields, "at ${step.from}")
            assertEquals(ComplicationFields.nextPrayer(feed, step.until - 1)?.stable(), fields.stable(), "at ${step.until - 1}")
            assertTrue(fields.startAt <= step.from && step.until <= fields.endAt, "a step never outlives its period")
        }
    }

    @Test
    fun nextPrayerTimelineSaysWhatTheRulesSayAtEveryInstant() {
        val plan = ComplicationTimelines.nextPrayer(feed, now)

        var t = now
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.nextPrayer(feed, t)?.stable(), stepAt(plan, t).fields?.stable(), "at $t")
            t += 61
        }
        for (step in plan) {
            for (edge in listOf(step.from - 1, step.from).filter { it >= now && it < plan.last().from }) {
                assertEquals(ComplicationFields.nextPrayer(feed, edge)?.stable(), stepAt(plan, edge).fields?.stable(), "at the edge $edge")
            }
        }
    }

    @Test
    fun theTimelineBeginsWhereItIsAskedFromAndRunsOnFromThere() {
        for (from in listOf(at(0, 3, 0), at(0, 4, 33), at(1, 0, 0), at(3, 20, 24), at(5, 12, 0, 1))) {
            val plan = ComplicationTimelines.nextPrayer(feed, from)

            assertRulesHold(plan, from, "from $from")
            assertEquals(ComplicationFields.nextPrayer(feed, from), plan.first().fields, "from $from")
            assertEquals(10, plan.dropLast(1).count { it.fields!!.endAt == it.until }, "from $from")
        }
    }

    @Test
    fun aLaterRequestReachesFurther() {
        // The system asks again every few hours, and each answer reaches ten periods on from where
        // it was asked: that is what keeps the timeline ahead of the clock.
        val first = ComplicationTimelines.nextPrayer(feed, now)
        val later = ComplicationTimelines.nextPrayer(feed, now + 6 * 3_600)

        assertTrue(later.last().from > first.last().from)
    }

    @Test
    fun aFeedThatEndsSoonEndsTheTimelineAtItsLastStart() {
        // 22:00 on 15 November; the sample's last start is Isha on the 16th at 20:24.
        val from = at(28, 22, 0)
        val plan = ComplicationTimelines.nextPrayer(feed, from)

        assertRulesHold(plan, from, "near the end")
        assertEquals(listOf("FAJR", "FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA"), plan.dropLast(1).map { it.fields!!.label })
        assertEquals(at(29, 20, 24), plan.last().from, "the empty entry begins at the last start")
    }

    @Test
    fun aSpentFeedIsOnlyTheEmptyEntry() {
        for (spent in listOf(at(29, 20, 24), at(29, 21, 0), at(31, 12, 0))) {
            for (plan in listOf(
                ComplicationTimelines.nextPrayer(feed, spent),
                ComplicationTimelines.day(feed, spent),
                ComplicationTimelines.dial(feed, spent),
                ComplicationTimelines.dates(feed, spent),
            )) {
                assertEquals(1, plan.size)
                assertRulesHold(plan, spent, "spent at $spent")
                assertNull(plan.single().fields)
            }
        }
        // And a feed with nothing in it.
        val nothing = feed.copy(days = emptyList())
        assertEquals(1, ComplicationTimelines.nextPrayer(nothing, now).size)
        assertEquals(1, ComplicationTimelines.day(nothing, now).size)
        assertEquals(1, ComplicationTimelines.dial(nothing, now).size)
        assertEquals(1, ComplicationTimelines.dates(nothing, now).size)
    }

    // ---- A high-latitude night

    @Test
    fun aDateTurningOverInsideAPeriodSplitsItWhereTheAnswerChanges() {
        // Tuesday 20 October's Maghrib falls at 00:45 on the 21st, after that day's Fajr (00:38):
        // the phone counts to it until midnight and drops it once the date has turned, so from
        // 00:00 the next prayer is Fajr, not Maghrib. One period, two answers.
        val night = TestFeeds.lateMaghribNight()
        val from = at(2, 12, 0)
        val plan = ComplicationTimelines.nextPrayer(night, from)
        val midnight = at(3, 0, 0)

        assertRulesHold(plan, from, "a late Maghrib")
        assertEquals("MAGHRIB", ComplicationFields.nextPrayer(night, midnight - 1)!!.label)
        assertEquals("FAJR", ComplicationFields.nextPrayer(night, midnight)!!.label)
        assertEquals("MAGHRIB", stepAt(plan, midnight - 1).fields!!.label)
        assertEquals("FAJR", stepAt(plan, midnight).fields!!.label)
        assertTrue(plan.any { it.from == midnight }, "a step begins at the turn of the date")

        // Everywhere in between the plan says what the rules say, and it still holds ten periods.
        var t = from
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.nextPrayer(night, t)?.stable(), stepAt(plan, t).fields?.stable(), "at $t")
            t += 53
        }
        assertEquals(10, plan.dropLast(1).count { it.fields!!.endAt == it.until }, "the split is not a period of its own")
    }

    @Test
    fun everyMidnightSplitsTheNightForTheDial() {
        val plan = ComplicationTimelines.nextPrayer(feed, now)

        // The period from Isha to Fajr is one period in two steps: the ring and the countdown run on
        // unchanged, and the Sun's dial turns to the new date's Dhuhr at midnight.
        val before = plan.single { it.from == at(0, 20, 24) }
        val after = plan.single { it.from == at(1, 0, 0) }
        assertEquals(at(1, 0, 0), before.until)
        assertEquals(at(1, 4, 33), after.until)
        assertEquals(before.fields!!.copy(dhuhrAt = 0L).stable(), after.fields!!.copy(dhuhrAt = 0L).stable(), "the same period")
        assertEquals(at(0, 12, 33), before.fields.dhuhrAt)
        assertEquals(at(1, 12, 33), after.fields.dhuhrAt)
    }

    // ---- The day

    @Test
    fun dayTimelineSaysWhatTheRulesSayAtEveryInstant() {
        val plan = ComplicationTimelines.day(feed, now)

        assertRulesHold(plan, now, "day")
        var t = now
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.day(feed, t), stepAt(plan, t).fields, "at $t")
            t += 67
        }
    }

    @Test
    fun dayTimelineBreaksAtEveryPrayerAndEveryMidnight() {
        val plan = ComplicationTimelines.day(feed, now)
        val breaks = plan.map { it.from }.toSet()

        // The lit stretches change with the period and the stretches themselves with the date.
        for (expected in listOf(
            at(0, 16, 10), at(0, 19, 4), at(0, 20, 24), at(1, 0, 0), at(1, 4, 33), at(1, 12, 33),
            at(1, 16, 10), at(1, 19, 4), at(1, 20, 24), at(2, 0, 0), at(2, 4, 33),
        )) {
            assertTrue(expected in breaks, "a step begins at $expected")
        }
    }

    @Test
    fun dayTimelineAcrossAPolarNightStillSaysWhatTheRulesSay() {
        val night = TestFeeds.lateMaghribNight()
        val from = at(2, 12, 0)
        val plan = ComplicationTimelines.day(night, from)

        assertRulesHold(plan, from, "day, late Maghrib")
        var t = from
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.day(night, t), stepAt(plan, t).fields, "at $t")
            t += 59
        }
    }

    // ---- The day dial's geometry

    @Test
    fun dialTimelineSaysWhatTheRulesSayAtEveryInstant() {
        val plan = ComplicationTimelines.dial(feed, now)

        assertRulesHold(plan, now, "dial")
        var t = now
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.dial(feed, t), stepAt(plan, t).fields, "at $t")
            t += 67
        }
        for (step in plan.dropLast(1)) {
            assertEquals(ComplicationFields.dial(feed, step.until - 1), step.fields, "at the end of the step from ${step.from}")
        }
    }

    @Test
    fun dialTimelineAcrossAPolarNightStillSaysWhatTheRulesSay() {
        val night = TestFeeds.lateMaghribNight()
        val from = at(2, 12, 0)
        val plan = ComplicationTimelines.dial(night, from)

        assertRulesHold(plan, from, "dial, late Maghrib")
        var t = from
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.dial(night, t), stepAt(plan, t).fields, "at $t")
            t += 59
        }
    }

    // ---- The dates

    @Test
    fun datesTimelineChangesOnlyAtMidnight() {
        val plan = ComplicationTimelines.dates(feed, now)

        assertRulesHold(plan, now, "dates")
        assertEquals(listOf(now, at(1, 0, 0), at(2, 0, 0)), plan.dropLast(1).map { it.from })
        assertEquals(
            listOf("SUN 18 OCT", "MON 19 OCT", "TUE 20 OCT"),
            plan.dropLast(1).map { it.fields!!.gregorian },
        )
    }

    @Test
    fun datesTimelineSaysWhatTheRulesSayAtEveryInstant() {
        val plan = ComplicationTimelines.dates(feed, now)

        var t = now
        while (t < plan.last().from) {
            assertEquals(ComplicationFields.dates(feed, t), stepAt(plan, t).fields, "at $t")
            t += 71
        }
    }

    // ---- Together

    @Test
    fun theThreeTimelinesEndTogether() {
        // Whatever stops the answers stops all three at once: a face never shows a countdown
        // beside a date that is gone, or the other way about.
        val ends = listOf(
            ComplicationTimelines.nextPrayer(feed, now).last().from,
            ComplicationTimelines.day(feed, now).last().from,
            ComplicationTimelines.dial(feed, now).last().from,
            ComplicationTimelines.dates(feed, now).last().from,
        )
        assertEquals(1, ends.toSet().size, "$ends")

        val nearTheEnd = at(28, 22, 0)
        val endsAtTheEnd = listOf(
            ComplicationTimelines.nextPrayer(feed, nearTheEnd).last().from,
            ComplicationTimelines.day(feed, nearTheEnd).last().from,
            ComplicationTimelines.dial(feed, nearTheEnd).last().from,
            ComplicationTimelines.dates(feed, nearTheEnd).last().from,
        )
        assertEquals(setOf(at(29, 20, 24)), endsAtTheEnd.toSet())
    }

    @Test
    fun aTimelineIsAFewEntriesAndNotAMonthsWorth() {
        // Ten periods, their midnights and the empty entry: a dozen or so, never a month's worth
        // (each entry carries its own copy of the countdown, a few kilobytes).
        for (plan in listOf(
            ComplicationTimelines.nextPrayer(feed, now),
            ComplicationTimelines.day(feed, now),
            ComplicationTimelines.dial(feed, now),
            ComplicationTimelines.dates(feed, now),
        )) {
            assertTrue(plan.size in 2..16, "${plan.size} entries")
        }
    }

    /** The parts of a period's fields that the system moves on its own: the ring and the countdown. */
    private fun NextPrayerFields.stable(): NextPrayerFields = copy(countdown = "", progress = 0f)
}
