package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.TestFeeds.at

/**
 * The countdown and the ring as the watch's system evaluates them, read back with
 * [DynamicEvaluator]. The countdown is the one place the complications word a number themselves,
 * and the phone's widgets, the face and the vectors must say the same thing at the same second
 * (Review Focus 2): so every one of these compares it with `WatchRules.countdown`, which is the
 * one place the wording lives.
 */
class CountdownExpressionTest {

    private val feed = TestFeeds.sample

    private fun countdownAt(feed: WatchFeed, endAt: Long, longest: Long, now: Long, millis: Int = 0): String {
        val ui = feed.ui
        val expression = CountdownExpression.countdown(ui.countdown, ui.digitZero, endAt, longest)
        return DynamicEvaluator(now, millis).string(expression.toDynamicStringProto())
    }

    /**
     * Checks the countdown against the rules for a period of [periodSeconds]: at every second of
     * the last two minutes and of the first, and at the seconds either side of each minute's edge
     * all the way through (an exhaustive sweep when [everySecond]). Each is read at the start of
     * the second, a hair into it, in the middle of it and a millisecond before it ends: the
     * platform's clock has milliseconds, and the rules count in the whole second it is in.
     */
    private fun assertReadsAsTheRulesDo(
        feed: WatchFeed,
        periodSeconds: Long,
        what: String,
        everySecond: Boolean = false,
    ) {
        val endAt = at(0, 12, 0)
        for (left in 0L..periodSeconds) {
            val edge = left < 130 || periodSeconds - left < 130 || left % 60 in setOf(0L, 1L, 59L)
            if (!everySecond && !edge) continue
            for (millis in listOf(0, 1, 500, 999)) {
                assertEquals(
                    WatchRules.countdown(feed, left),
                    countdownAt(feed, endAt, periodSeconds, endAt - left, millis),
                    "$what, $left s left, $millis ms in",
                )
            }
        }
    }

    private fun minutes(m: Int, s: Int = 0) = m * 60L + s

    @Test
    fun theCountdownReadsAsTheRulesDoInEnglish() {
        // The five periods of the sample's day: Dhuhr to Asr is 3 h 37 min, Asr to Maghrib 2 h 54
        // min, Maghrib to Isha 1 h 20 min, Isha to the next Fajr 8 h 9 min and Fajr to Dhuhr 8 h.
        // The shortest is swept second by second, the others at every minute's edge.
        assertReadsAsTheRulesDo(feed, minutes(80), "Maghrib to Isha", everySecond = true)
        assertReadsAsTheRulesDo(feed, minutes(217), "Dhuhr to Asr")
        assertReadsAsTheRulesDo(feed, minutes(174), "Asr to Maghrib")
        assertReadsAsTheRulesDo(feed, minutes(489), "Isha to Fajr")
        assertReadsAsTheRulesDo(feed, minutes(480), "Fajr to Dhuhr")
    }

    @Test
    fun theCountdownReadsAsTheRulesDoInTheOtherDigitSets() {
        val arabicIndic = TestFeeds.arabicSaudi()
        val bengali = feed.copy(ui = feed.ui.copy(digitZero = '০'))
        val extendedArabicIndic = feed.copy(ui = feed.ui.copy(language = "ur", rtl = true, digitZero = '۰'))

        assertReadsAsTheRulesDo(arabicIndic, minutes(489), "Arabic-Indic")
        assertReadsAsTheRulesDo(bengali, minutes(489), "Bengali")
        assertReadsAsTheRulesDo(extendedArabicIndic, minutes(489), "extended Arabic-Indic")
        assertReadsAsTheRulesDo(arabicIndic, minutes(80), "Arabic-Indic, the short one", everySecond = true)
    }

    @Test
    fun theCountdownNamesTheFeedsDigitsAndNeverTheWatchs() {
        // 72 minutes left, which is "in 1 h 12 min" in Western digits and ١ and ١٢ in Arabic-Indic: the
        // numbers are written out of the feed's own digit zero and not by the watch's language.
        val arabic = TestFeeds.arabicSaudi()

        assertEquals("in 1 h 12 min", countdownAt(feed, at(0, 16, 10), minutes(217), at(0, 14, 58)))
        assertEquals("بعد ١ س ١٢ د", countdownAt(arabic, at(0, 16, 10), minutes(217), at(0, 14, 58)))
    }

    @Test
    fun theMinuteBoundaryIsTheRulesOwn() {
        val end = at(0, 16, 10)
        val longest = minutes(217)

        // Exactly 72 minutes left reads 1 h 12 min, and 71 minutes 59 seconds reads 1 h 11 min.
        assertEquals("in 1 h 12 min", countdownAt(feed, end, longest, end - minutes(72)))
        assertEquals("in 1 h 11 min", countdownAt(feed, end, longest, end - minutes(71, 59)))
        // A whole number of hours and a whole number of minutes drop the part that is nothing.
        assertEquals("in 2 h", countdownAt(feed, end, longest, end - minutes(120)))
        assertEquals("in 59 min", countdownAt(feed, end, longest, end - minutes(59, 30)))
        assertEquals("in 1 min", countdownAt(feed, end, longest, end - 60))
        assertEquals("in under a minute", countdownAt(feed, end, longest, end - 59))
        assertEquals("in under a minute", countdownAt(feed, end, longest, end - 1))
    }

    @Test
    fun theClockKeepsItsMillisecondsAndTheCountdownReadsTheWholeSecondItIsIn() {
        // `platformTimeWithSecondsPrecision` is read once a second and is not rounded: at 14:58:00.2
        // the time left to 16:10:00 is 71 min 59.8 s, and a countdown that floored that would read
        // 1 h 11 min at once. The rules count in whole seconds, and 14:58:00 is 72 minutes before.
        val end = at(0, 16, 10)
        val longest = minutes(217)

        assertEquals("in 1 h 12 min", countdownAt(feed, end, longest, at(0, 14, 58), millis = 0))
        assertEquals("in 1 h 12 min", countdownAt(feed, end, longest, at(0, 14, 58), millis = 200))
        assertEquals("in 1 h 12 min", countdownAt(feed, end, longest, at(0, 14, 58), millis = 999))
        assertEquals("in 1 h 11 min", countdownAt(feed, end, longest, at(0, 14, 58, 1), millis = 0))
        // The same where the minutes give way to "under a minute", and where a countdown runs out.
        assertEquals("in 1 min", countdownAt(feed, end, longest, end - 60, millis = 999))
        assertEquals("in under a minute", countdownAt(feed, end, longest, end - 59, millis = 0))
        assertEquals("in under a minute", countdownAt(feed, end, longest, end, millis = 500))
        assertEquals("in under a minute", countdownAt(feed, end, longest, end + 1, millis = 500))
    }

    @Test
    fun aCountdownThatHasRunOutIsUnderAMinuteAndNeverNegative() {
        val end = at(0, 16, 10)

        assertEquals("in under a minute", countdownAt(feed, end, minutes(217), end))
        assertEquals("in under a minute", countdownAt(feed, end, minutes(217), end + 1))
        assertEquals("in under a minute", countdownAt(feed, end, minutes(217), end + 86_400))
    }

    @Test
    fun aPeriodOfTenHoursOrMoreHasTwoDigitHours() {
        // A winter night in the far north can be longer than ten hours between two prayers.
        val longest = 13 * 3_600L + 7 * 60
        val end = at(0, 12, 0)

        for (left in listOf(longest, longest - 1, 12 * 3_600L, 10 * 3_600L + 59 * 60 + 59, 10 * 3_600L, 9 * 3_600L + 59 * 60)) {
            assertEquals(
                WatchRules.countdown(feed, left),
                countdownAt(feed, end, longest, end - left),
                "$left s left",
            )
        }
        assertReadsAsTheRulesDo(feed, longest, "13 h 7 min")
    }

    @Test
    fun aPeriodOfAHundredHoursOrMoreIsHeldToNinetyNine() {
        // No period between two starts is that long, but a broken feed could say so, and a number
        // has two digits at most: past 99 hours the countdown says 99, and below them the rules.
        val longest = 150 * 3_600L + 7 * 60
        val end = at(0, 12, 0)

        for ((left, shown) in listOf(
            longest to 99 * 3_600L + 7 * 60,
            120 * 3_600L to 99 * 3_600L,
            100 * 3_600L + 5 * 60 to 99 * 3_600L + 5 * 60,
            99 * 3_600L + 59 * 60 to 99 * 3_600L + 59 * 60,
            98 * 3_600L to 98 * 3_600L,
            10 * 3_600L + 1 to 10 * 3_600L,
            59L to 59L,
        )) {
            assertEquals(WatchRules.countdown(feed, shown), countdownAt(feed, end, longest, end - left), "$left s left")
        }
        // In another digit set too: the held hours are the feed's digits, never a character past them.
        val saudi = TestFeeds.arabicSaudi()
        assertEquals(WatchRules.countdown(saudi, 99 * 3_600L), countdownAt(saudi, end, longest, end - 120 * 3_600L))
    }

    @Test
    fun aClockAMinuteEarlyStillReadsRightAtTheStartOfAPeriod() {
        // The entry is shown from the period's first second, but a system that evaluates it a
        // little before (a minute's slack) must not show a number the countdown cannot make. This
        // period is 30 seconds short of three hours, so that the minute before it begins is where
        // the hours would run out: 3 h 0 min 20 s left is "3 h" and not "2 h".
        val longest = 3 * 3_600L - 30
        val end = at(0, 12, 0)

        for (extra in 0L..60L) {
            assertEquals(
                WatchRules.countdown(feed, longest + extra),
                countdownAt(feed, end, longest, end - longest - extra),
                "$extra s before the start",
            )
        }
    }

    @Test
    fun theRingRunsFromItsStartToItsEndInMinutesFromDhuhr() {
        // Dhuhr to Asr on the Sunday: the ring is the minutes from the dial's Dhuhr, 0 to 217.
        val dhuhr = at(0, 12, 33)
        val start = dhuhr
        val end = at(0, 16, 10)
        val ring = CountdownExpression.ring(start, end, dhuhr).toDynamicFloatProto()

        fun ringAt(now: Long, millis: Int = 0) = DynamicEvaluator(now, millis).float(ring)

        assertEquals(0f, ringAt(start), 0f)
        assertEquals(1f / 60f, ringAt(start + 1), 1e-6f)
        // Counted in whole seconds, as the rules do, whatever the milliseconds of the clock.
        assertEquals(100f / 60f, ringAt(start + 100, millis = 999), 1e-5f)
        assertEquals(0f, ringAt(start, millis = 999), 0f)
        assertEquals(145f, ringAt(at(0, 14, 58)), 1e-4f)
        assertEquals(217f, ringAt(end), 0f)
        // Held to the period on either side, so a stale or an early evaluation draws a whole ring
        // and an empty one, and never a value outside the complication's range.
        assertEquals(217f, ringAt(end + 3_600), 0f)
        assertEquals(0f, ringAt(start - 3_600), 0f)
    }

    @Test
    fun theNightsRingIsMeasuredFromTheDialsDhuhrOnEitherSideOfMidnight() {
        // Isha to Fajr: before midnight from Sunday's Dhuhr, after it from Monday's.
        val isha = at(0, 20, 24)
        val fajr = at(1, 4, 33)
        val beforeMidnight = CountdownExpression.ring(isha, fajr, at(0, 12, 33)).toDynamicFloatProto()
        val afterMidnight = CountdownExpression.ring(isha, fajr, at(1, 12, 33)).toDynamicFloatProto()

        assertEquals(471f + 126f, DynamicEvaluator(at(0, 22, 30)).float(beforeMidnight), 1e-3f)
        assertEquals(-503f, DynamicEvaluator(at(1, 4, 10)).float(afterMidnight), 1e-3f)
        assertEquals(-480f, DynamicEvaluator(fajr).float(afterMidnight), 0f)
    }

    @Test
    fun theRingIsTheRulesOwnProgressAtTheSameMoment() {
        for (now in listOf(at(0, 14, 58), at(0, 22, 30), at(1, 4, 10), at(2, 9, 0, 7))) {
            val next = checkNotNull(ComplicationFields.nextPrayer(feed, now))
            val ring = CountdownExpression.ring(next.startAt, next.endAt, next.dhuhrAt).toDynamicFloatProto()
            val value = DynamicEvaluator(now).float(ring)

            // The value is the moment on the dial, and as a share of the period it is the progress.
            assertEquals(next.dialNow, value, 1e-3f, "at $now")
            assertEquals(next.progress, (value - next.dialStart) / (next.dialEnd - next.dialStart), 1e-5f, "at $now")
            // At the period's end it reaches the range's own end exactly, never a hair past it.
            assertEquals(next.dialEnd, DynamicEvaluator(next.endAt).float(ring), 0f, "at the end of the period at $now")
        }
    }

    @Test
    fun anEntryIsSmallEnoughToSendTenAtATime() {
        // Each entry carries its own copy of the expression (it has no way to share a part), and a
        // timeline holds ten of them, so a countdown that doubled in size by accident would be
        // sent twice as heavy ten times over. The longest period of a day is the night, 8 h 9 min,
        // whose countdown is about 3.4 KB as the protobuf the system is sent.
        val ui = feed.ui
        val night = CountdownExpression.countdown(ui.countdown, ui.digitZero, at(1, 4, 33), minutes(489))
        val arabic = TestFeeds.arabicSaudi().ui
        val arabicNight = CountdownExpression.countdown(arabic.countdown, arabic.digitZero, at(1, 4, 33), minutes(489))

        val englishBytes = night.toDynamicStringProto().serializedSize
        val arabicBytes = arabicNight.toDynamicStringProto().serializedSize
        assertTrue(englishBytes < 8_000, "an English entry's countdown is $englishBytes bytes")
        assertTrue(arabicBytes < 8_000, "an Arabic entry's countdown is $arabicBytes bytes")
        assertTrue(CountdownExpression.ring(0, 1, 0).toDynamicFloatProto().serializedSize < 600)
    }
}
