package world.taqwa.app.wear.tile

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchTime
import world.taqwa.app.wear.LongestCity
import world.taqwa.app.wear.R
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.today.LitRule
import world.taqwa.app.wear.today.RowStatus
import world.taqwa.app.wear.today.dayRows

/**
 * The Tile's plan (plan 3 Task 8): its timeline as plain entries, each what the Tile draws from
 * its `from` to its `until`, on the committed samples (`TestFeeds`): Cape Town, whose every day is
 * Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04 and Isha 20:24. `newerVersionSeen`
 * is false unless a test names it.
 */
class TilePlanTest {

    private val sample = TestFeeds.sample
    private val en12 = TestFeeds.other("en12")
    private val at1458 = TestFeeds.at(0, 14, 58)

    private fun plan(feed: WatchFeed = sample, now: Long = at1458, newer: Boolean = false) = TilePlan.entries(feed, newer, now)

    @Test
    fun atTwoFiftyEightDhuhrIsLitAndAsrIsNext() {
        val first = plan().first()

        assertEquals(at1458, first.from)
        val next = assertNotNull(first.next)
        assertEquals("ASR", next.label)
        assertEquals("Asr", next.name)
        assertEquals("16:10", next.clock)
        assertEquals(TestFeeds.at(0, 12, 33), next.startAt)
        assertEquals(TestFeeds.at(0, 16, 10), next.endAt)
        assertEquals("in 1h 12m", next.minutes.last().text)
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", next.minutes.last().spoken)
        assertEquals("Asr, 16:10", next.clockSentence)
        assertEquals(RowStatus.CURRENT, first.rows[2].status)
        assertEquals("Dhuhr", first.rows[2].name)
        assertNull(first.notice)
        assertNull(first.update)
        assertEquals("Cape Town", first.city)
    }

    @Test
    fun theLineWritesTheCompactFormsAndItsSentenceTheFullWords() {
        // Mohamed's report of 9 November 2026: the Tile's countdown in one-letter units, "in 6h
        // 20m", in the feed's compact forms, and what a screen reader is told still the phone's
        // full words. Every minute of the first entries, in English, Saudi and Libyan Arabic and
        // on 12 hours. Arabic sends no compact forms and keeps its spaces (Mohamed's call of the
        // same day).
        for (feed in listOf(sample, en12, TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"))) {
            for (entry in plan(feed).take(20)) {
                val next = entry.next ?: continue
                for (step in next.minutes) {
                    if (step.text == next.clock) continue
                    assertEquals(WatchRules.compactCountdown(feed, step.minutesLeft * 60L), step.text, "${feed.ui.language} at ${step.minutesLeft}")
                    assertEquals(WatchRules.spokenCountdown(feed, next.name, next.spokenClock, step.minutesLeft * 60L), step.spoken, "${feed.ui.language} at ${step.minutesLeft}")
                }
            }
        }
        val first = assertNotNull(plan(TestFeeds.other("ar-sa")).first().next)
        assertEquals("بعد ١ س ١٢ د", first.minutes.last().text)
        assertEquals("العصر بعد ١ ساعة و١٢ دقيقة، الساعة ١٦:١٠", first.minutes.last().spoken)
    }

    @Test
    fun aFeedWithoutCompactFormsCountsDownInTheFullOnes() {
        // A 1.6.0 (44) phone's feed has none: the Tile writes what it wrote before.
        val older = sample.copy(ui = sample.ui.copy(countdown = sample.ui.countdown.copy(compactHoursMinutes = "", compactHours = "", compactMinutes = "")))
        val next = assertNotNull(plan(older).first().next)

        assertEquals("in 1 h 12 min", next.minutes.last().text)
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", next.minutes.last().spoken)
    }

    @Test
    fun aPeriodIsCutShorterTowardsEachChange() {
        // Erratum p3 #6: the countdown is a chain of the rules' own words, one for each minute an
        // entry can be shown at, so an entry of the moving hours is five minutes at most (review
        // round 4; six before, which left no room in the quota for the start's time past them).
        // Review round 3: the renderer changes entries late, so the entries before each change
        // shorten towards it, 2 and 4 minutes before it, then every five, so that none but the
        // last can still be shown when it comes. Asr at 16:10 is 72 minutes away at 14:58.
        val asr = plan().takeWhile { it.next?.endAt == TestFeeds.at(0, 16, 10) }

        assertEquals(
            listOf(at1458) + (0..12).map { TestFeeds.at(0, 15, 1) + it * 300L } + listOf(TestFeeds.at(0, 16, 6), TestFeeds.at(0, 16, 8)),
            asr.map { it.from },
        )
        assertEquals(TestFeeds.at(0, 16, 10), asr.last().until)
        for (entry in asr) {
            val until = assertNotNull(entry.until)
            assertTrue(until - entry.from <= TilePlan.STEP_SECONDS, "${entry.from}..$until")
        }
    }

    @Test
    fun noEntryButTheLastBeforeAChangeCanStillBeShownWhenItComes() {
        // Each change (a start, a time of the day, a turn of the date) and the moving entries
        // before it: every one but the last ends early enough that the renderer, however late,
        // has moved on from it by then; the last carries what comes after the change.
        val tromso = TestFeeds.phone("tromso-may-en")
        val plans = listOf(sample, en12, TestFeeds.other("ar-sa"), TestFeeds.phone("capetown-ur24")).flatMap { feed ->
            listOf(at1458, TestFeeds.at(0, 18, 50), TestFeeds.at(0, 4, 0), TestFeeds.at(0, 23, 30)).map { plan(feed, now = it) }
        } + (0 until 4).map { TilePlan.entries(tromso, false, tromso.days[it].midnight + 22 * 3_600L) }
        for (entries in plans) {
            for ((i, entry) in entries.withIndex()) {
                val next = entry.next ?: continue
                if (next.minutes.isEmpty()) continue
                val until = assertNotNull(entry.until)
                val shown = assertNotNull(entry.shownUntil)
                assertEquals(TilePlan.shownUntil(entry.from, until), shown)
                val change = entries.drop(i + 1).firstOrNull { it.rows != entry.rows || it.next?.endAt != next.endAt || it.notice != null }?.from ?: continue
                if (entry.after == null) {
                    assertTrue(shown < change, "the entry from ${entry.from} may still be shown at $shown, the change at $change")
                } else {
                    assertEquals(change, until, "the entry that carries what comes after ends at the change")
                }
            }
        }
    }

    @Test
    fun theEntryBeforeAStartCarriesWhatComesAfterIt() {
        val last = plan().first { it.until == TestFeeds.at(0, 16, 10) }
        val after = assertNotNull(last.after)
        val next = assertNotNull(after.next)

        assertEquals(TestFeeds.at(0, 16, 10), after.from)
        assertEquals(last.shownUntil, after.until)
        assertEquals("MAGHRIB", next.label)
        assertEquals(dayRows(sample, TestFeeds.at(0, 16, 10), LitRule.GLANCE), after.rows)
        assertEquals(RowStatus.CURRENT, after.rows[3].status)
        // It counts down to Maghrib for as long as the renderer may still show the entry: from
        // 2 h 54 min at 16:10 to 2 h 52 min at 16:11:55.
        assertEquals(listOf("in 2h 52m", "in 2h 53m", "in 2h 54m"), next.minutes.map { it.text })
        assertEquals(TestFeeds.at(0, 16, 11, 55), last.shownUntil)
        // The last before the feed's last start carries the notice.
        val toTheEnd = plan(now = TestFeeds.at(29, 20, 0))
        assertEquals("Open Taqwa on your phone", assertNotNull(toTheEnd.dropLast(1).last().after).notice)
    }

    @Test
    fun theLatenessCoversWhatEmulator5558Showed() {
        // dumpsys alarm on emulator-5558 (fix round 2 and review round 3): SysUI's
        // ProtoTilesTimelineMgr set the change for an entry's end with a window three quarters of
        // the time from its first showing, and the change came at the window's end. Each latest
        // change seen falls within what the plan allows for.
        val seen = listOf(
            // Shown from 15:05:03, to end at 15:10:00: window +3m44.8s.
            Triple(TestFeeds.at(0, 15, 5, 3), TestFeeds.at(0, 15, 10), TestFeeds.at(0, 15, 13, 45)),
            // Asked for at 19:09:00, to end at 19:14:00: window +3m44.7s, still pending at 19:17:44.
            Triple(TestFeeds.at(0, 19, 9), TestFeeds.at(0, 19, 14), TestFeeds.at(0, 19, 17, 45)),
            // Asked for at 19:02:05, to end at 19:04:00: window +1m14.8s, changed by 19:05:15.
            Triple(TestFeeds.at(0, 19, 2, 5), TestFeeds.at(0, 19, 4), TestFeeds.at(0, 19, 5, 15)),
        )
        for ((from, until, latest) in seen) assertTrue(TilePlan.shownUntil(from, until) >= latest, "$from..$until")
        // An entry first shown in its last seconds moves on no sooner than a minute later, with
        // three quarters of that minute again.
        assertEquals(TestFeeds.at(0, 16, 10) + 115L, TilePlan.shownUntil(TestFeeds.at(0, 16, 9, 59), TestFeeds.at(0, 16, 10)))
    }

    @Test
    fun everyEntryCountsDownInTheRulesWordsAtEveryMinuteItCanBeShown() {
        for (feed in listOf(sample, en12, TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"))) {
            for (entry in plan(feed)) {
                val next = entry.next ?: continue
                if (next.minutes.isEmpty()) continue
                val shown = assertNotNull(entry.shownUntil)
                // From the margin before it to the last second the renderer may still show it.
                val earliest = (entry.from - TilePlan.MARGIN_SECONDS).coerceAtLeast(next.startAt)
                for (s in (earliest..shown step 30L) + shown) {
                    val left = next.endAt - s
                    val step = TilePlan.stepAt(next, left)
                    // Under a minute is the last entry's alone; an earlier one shown late, and any
                    // from the start on, says the start's time.
                    val clock = left <= 0 || (left < 60 && entry.until != next.endAt)
                    val text = if (clock) next.clock else WatchRules.compactCountdown(feed, left)
                    val spoken = if (clock) next.clockSentence else WatchRules.spokenCountdown(feed, next.name, next.spokenClock, left)
                    assertEquals(text, step.text, "${feed.ui.language} at $s")
                    assertEquals(spoken, step.spoken, "${feed.ui.language} at $s")
                }
            }
        }
    }

    @Test
    fun theLastMinuteHasAnEntryOfItsOwn() {
        // Review round 2: an entry's chain reaches half a minute past its end, so the last ten
        // minutes before a start held "in under a minute", the widest of their words, and the line
        // was fitted to it for all ten. The last minute and the margin before it are an entry of
        // their own, from the whole minute before them, so only that entry says under a minute, and
        // the eight minutes before it say "in 10m" at most.
        // Since review round 3 the entry before it may be shown in the last minute too, late, and
        // then says the start's time; the last says it from the start on.
        val asr = plan().filter { it.next?.endAt == TestFeeds.at(0, 16, 10) }.takeLast(2)
        assertEquals(listOf(TestFeeds.at(0, 16, 6), TestFeeds.at(0, 16, 8)), asr.map { it.from })
        assertEquals(listOf("16:10") + (1..4).map { "in ${it}m" }, asr[0].next!!.minutes.map { it.text })
        assertEquals(listOf("in under a minute", "in 1m", "in 2m"), asr[1].next!!.minutes.map { it.text })
        // From the start on, both say the start's time (TileCountdown).
        for (entry in asr) assertEquals("16:10", TilePlan.stepAt(entry.next!!, 0L).text)
        assertEquals(TilePlan.LAST_MINUTE_SECONDS, TestFeeds.at(0, 16, 10) - asr[1].from)

        val tromso = TestFeeds.phone("tromso-may-en")
        val plans = listOf(sample, en12, TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24")).flatMap { feed ->
            listOf(at1458, TestFeeds.at(0, 18, 55), TestFeeds.at(0, 4, 0)).map { feed to plan(feed, now = it) }
        } + (0 until 4).map { tromso to TilePlan.entries(tromso, false, tromso.days[it].midnight + 20 * 3_600L) }
        for ((feed, entries) in plans) {
            val underAMinute = WatchRules.countdown(feed, 0L)
            for (entry in entries) {
                val next = entry.next ?: continue
                if (next.minutes.none { it.text == underAMinute }) continue
                assertTrue(next.endAt - entry.from <= TilePlan.LAST_MINUTE_SECONDS, "${feed.ui.language}: the entry from ${entry.from} says '$underAMinute'")
            }
        }
    }

    @Test
    fun theTileKeepsFajrLitUntilDhuhr() {
        // The widgets' rule (spec 1.4); Today's list differs there (Task 5).
        val first = plan(now = TestFeeds.at(0, 6, 40)).first()

        assertEquals(RowStatus.CURRENT, first.rows[0].status)
        assertEquals("Current prayer", first.rows[0].state)
        assertEquals("DHUHR", first.next?.label)
    }

    @Test
    fun entriesAreContiguousAndNeverOverlap() {
        val nows = listOf(at1458, TestFeeds.at(0, 6, 40), TestFeeds.at(0, 23, 59, 30), TestFeeds.at(1, 0, 0), TestFeeds.at(0, 4, 33))
        for (now in nows) {
            val entries = plan(now = now)
            assertEquals(now, entries.first().from)
            for ((a, b) in entries.zipWithNext()) {
                val until = assertNotNull(a.until, "only the last entry runs on")
                assertTrue(a.from < until, "an entry from ${a.from} lasts")
                assertEquals(until, b.from, "no gap and no overlap at $until")
            }
            assertNull(entries.last().until)
        }
    }

    @Test
    fun theRowsOfAnEntryHoldForItsWholeTime() {
        for (entry in plan()) {
            val until = entry.until ?: continue
            for (s in listOf(entry.from, (entry.from + until) / 2, until - 1)) {
                assertEquals(dayRows(sample, s, LitRule.GLANCE), entry.rows, "the rows at $s")
            }
        }
    }

    @Test
    fun entriesBreakAtEveryTimeOfTheDayAndAtMidnight() {
        val boundaries = plan().map { it.from }.toSet()
        // Monday 19 October, the day after the mock Sunday, inside the plan: each of its six
        // times, and its own midnight.
        val monday = sample.days[2]
        assertTrue(monday.midnight in boundaries, "midnight")
        for (time in WatchTime.entries) {
            val at = when (time) {
                WatchTime.FAJR -> monday.times.fajr
                WatchTime.SUNRISE -> monday.times.sunrise
                WatchTime.DHUHR -> monday.times.dhuhr
                WatchTime.ASR -> monday.times.asr
                WatchTime.MAGHRIB -> monday.times.maghrib
                WatchTime.ISHA -> monday.times.isha
            }
            if (at < plan().last().from) assertTrue(at in boundaries, "$time")
        }
    }

    @Test
    fun withoutSunriseTheTileHasTheFivePrayersAndNoCutAtSunrise() {
        // The phone's Show sunrise off (the owner's decision of 9 November 2026): every entry's rows
        // are the five prayers, and Sunrise, which then changes nothing the Tile draws, cuts no
        // entry. Every other change still cuts one, and the countdown counts to the same starts.
        val hidden = TestFeeds.withoutSunrise(sample)
        val now = TestFeeds.at(0, 23, 0)
        val entries = plan(hidden, now = now)
        val shown = plan(now = now)
        val monday = sample.days[2]
        assertTrue(monday.times.dhuhr < entries.last().from, "Monday's morning lies inside the plan")

        assertTrue(entries.dropLast(1).all { entry -> entry.rows.map { it.name } == listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha") })
        for (entry in entries) {
            val until = entry.until ?: continue
            for (s in listOf(entry.from, (entry.from + until) / 2, until - 1)) {
                assertEquals(dayRows(hidden, s, LitRule.GLANCE), entry.rows, "the rows at $s")
            }
        }
        val boundaries = entries.map { it.from }.toSet()
        assertFalse(monday.times.sunrise in boundaries, "no cut at Sunrise")
        assertTrue(monday.times.sunrise in shown.map { it.from }, "with Sunrise shown, its cut")
        for (at in listOf(monday.midnight, monday.times.fajr, monday.times.dhuhr, monday.times.asr, monday.times.maghrib, monday.times.isha)) {
            if (at < entries.last().from) assertTrue(at in boundaries, "a cut at $at")
        }
        assertEquals(shown.mapNotNull { it.next?.endAt }.distinct(), entries.mapNotNull { it.next?.endAt }.distinct())
    }

    @Test
    fun theTimelineEndsInTheNotice() {
        val entries = plan()
        val last = entries.last()

        assertEquals("Open Taqwa on your phone", last.notice)
        assertNull(last.until)
        assertNull(last.next)
        assertTrue(last.rows.isEmpty())
        assertTrue(entries.dropLast(1).all { it.notice == null && it.next != null && it.rows.size == 6 })
    }

    @Test
    fun theTimelineReachesTenPeriods() {
        val entries = plan()
        val periodsEnded = entries.dropLast(1).count { it.until == it.next?.endAt }

        assertEquals(TilePlan.PERIODS, periodsEnded)
        // The tenth start after 14:58 on the mock Sunday is Dhuhr on Tuesday 20 October.
        assertEquals(TestFeeds.at(2, 12, 33), entries.last().from)
    }

    @Test
    fun theCountdownMovesForTheFirstEightyMinutesAndThenTheLineSaysTheTime() {
        val entries = plan().dropLast(1)
        val (moving, still) = entries.partition { it.from < at1458 + TilePlan.MOVING_SECONDS }

        assertTrue(moving.all { it.next!!.minutes.isNotEmpty() })
        assertTrue(still.isNotEmpty() && still.all { it.next!!.minutes.isEmpty() })
        // Past the moving hours an entry lasts until the rows or the next start change, not five
        // minutes, and carries nothing after it.
        assertTrue(still.none { e -> e.until!! - e.from == TilePlan.STEP_SECONDS && e.until != e.next!!.endAt })
        assertTrue(still.all { it.after == null })
    }

    @Test
    fun aSpentFeedIsTheNoticeAlone() {
        // The sample's last start is Isha at 20:24 on Monday 16 November.
        val now = TestFeeds.at(29, 21, 0)
        val entries = plan(now = now)

        assertEquals(1, entries.size)
        assertEquals(now, entries.single().from)
        assertEquals("Open Taqwa on your phone", entries.single().notice)
        assertNull(entries.single().next)
    }

    @Test
    fun aNewerVersionAsksForTheUpdateInTheCitysPlace() {
        val entries = plan(newer = true)

        assertTrue(entries.all { it.update == "Update Taqwa on your watch" })
        assertTrue(entries.dropLast(1).all { it.rows.size == 6 && it.next != null }, "the rows are still drawn")
        assertEquals("Update Taqwa on your watch", entries.last().notice)

        // Spent as well, the Tile says the update, not "Open Taqwa on your phone".
        assertEquals("Update Taqwa on your watch", plan(now = TestFeeds.at(29, 21, 0), newer = true).single().notice)
    }

    @Test
    fun withNoFeedTheTileSaysWhatThePagesSay() {
        assertTrue(TilePlan.entries(null, false, at1458).isEmpty())
        assertTrue(TilePlan.entries(null, true, at1458).isEmpty())
        assertEquals(R.string.open_taqwa_on_phone, TilePlan.emptyLine(false))
        assertEquals(R.string.update_taqwa_on_watch, TilePlan.emptyLine(true))
    }

    @Test
    fun aTwelveHourTileSaysItsDayPeriod() {
        val first = plan(en12).first()
        val next = assertNotNull(first.next)

        assertEquals("4:10${NBSP}PM", next.clock)
        assertEquals("${FSI}4:10${NBSP}PM$PDI", next.spokenClock)
        assertEquals("Asr in 1 hour 12 minutes, at ${FSI}4:10${NBSP}PM$PDI", next.minutes.last().spoken)
        assertEquals("Asr, ${FSI}4:10${NBSP}PM$PDI", next.clockSentence)
        assertEquals("6:01${NBSP}AM", first.rows[1].time)
        assertEquals("Sunrise, ${FSI}6:01${NBSP}AM$PDI", first.rows[1].spoken)
    }

    @Test
    fun anArabicTileKeepsTheNameAsItIs() {
        val next = assertNotNull(plan(TestFeeds.other("ar-sa")).first().next)

        assertEquals("العصر", next.label)
        assertEquals("١٦:١٠", next.clock)
        assertEquals("بعد ١ س ١٢ د", next.minutes.last().text)
    }

    /**
     * M8 point A5 (6 November 2026): the Tile's line, where it says the start's time, and its rows
     * join a name and its time with the Arabic comma "، " in Arabic and Urdu (Ruling 64 item 6).
     */
    @Test
    fun anArabicOrUrduTileJoinsTheNameAndTimeWithTheArabicComma() {
        val arabic = plan(TestFeeds.other("ar-sa")).first()
        val urdu = plan(TestFeeds.phone("capetown-ur24")).first()

        assertEquals("العصر، ١٦:١٠", assertNotNull(arabic.next).clockSentence)
        assertEquals("العصر، ١٦:١٠", TilePlan.stepAt(arabic.next!!, 0L).spoken)
        assertEquals("الشروق، ٦:٠١", arabic.rows[1].spoken)
        assertEquals("عصر، ۱۶:۱۰", assertNotNull(urdu.next).clockSentence)
        assertEquals("عصر، ۱۶:۱۰", urdu.rows[3].spoken)
    }

    @Test
    fun theLabelIsCasedInThePhonesLanguage() {
        val turkish = sample.copy(ui = sample.ui.copy(language = "tr"))
        val friday = plan(turkish, now = TestFeeds.at(5, 11, 0)).first()

        assertEquals("JUMUʿAH", friday.next?.label)
        assertEquals("MAGHRİB", plan(turkish, now = TestFeeds.at(0, 16, 30)).first().next?.label)
    }

    @Test
    fun theLongestCityIsCarriedWhole() {
        val name = LongestCity.name()
        val far = sample.copy(place = sample.place.copy(city = name))

        assertTrue(plan(far).all { it.city == name })
        assertEquals(1, TileType.city.maxLines)
    }

    @Test
    fun theTileGrowsLittleBecauseItCannotScroll() {
        assertEquals(12f, TileType.rowName.range(1.3f).start)
        assertEquals(13.2f, TileType.rowName.range(1.3f).endInclusive, 1e-4f)
        assertEquals(12f, TileType.rowName.range(1.0f).endInclusive)
        for (line in TileType.all) assertTrue(line.cap <= 1.1f, "$line")
    }

    @Test
    fun aTileLineIsDrawnAtItsUnits() {
        val units = WatchUnits.of(227, 227)
        for (scale in listOf(1.0f, 1.1f, 1.3f)) {
            val density = Density(2f, scale)
            for (line in TileType.all) {
                val size = line.range(scale).endInclusive
                val drawn = with(density) { units.sp(size, density).toDp() }
                assertEquals(units.x(size).value, drawn.value, 0.01f, "$line at a font scale of $scale")
            }
        }
    }

    @Test
    fun aFarNorthNightHasNoEmptyEntry() {
        // Tromsø in May: Maghrib and Isha after midnight, Sunrise a minute after Fajr.
        val tromso = TestFeeds.phone("tromso-may-en")
        for (day in 0 until 4) {
            val now = tromso.days[day].midnight + 20 * 3_600L
            val entries = TilePlan.entries(tromso, false, now)
            assertTrue(entries.size > 1, "a plan from day $day")
            for (entry in entries) entry.until?.let { assertTrue(entry.from < it, "day $day: ${entry.from}") }
            for ((a, b) in entries.zipWithNext()) assertEquals(a.until, b.from)
        }
    }

    @Test
    fun freshnessIsAtMostThreeQuartersOfAnHour() {
        // Within Ruling 22's six hours, and short of the countdown's 80 minutes even if the
        // system's own request comes as late as an inexact alarm may (three quarters again), so
        // that it asks again while the countdown still moves.
        assertEquals(45 * 60_000L, TilePlan.freshnessMillis(plan(), at1458))
        assertTrue(TilePlan.MAX_FRESH_MILLIS * 7 / 4 < TilePlan.MOVING_SECONDS * 1_000L)

        // Half an hour before the feed's last start, the Tile is fresh until the notice.
        val near = TestFeeds.at(29, 19, 54)
        assertEquals((TestFeeds.at(29, 20, 24) - near) * 1_000L, TilePlan.freshnessMillis(plan(now = near), near))

        // Spent, the notice is already showing: nothing to refresh for but a new feed.
        val spent = TestFeeds.at(29, 21, 0)
        assertEquals(0L, TilePlan.freshnessMillis(plan(now = spent), spent))
        assertEquals(0L, TilePlan.freshnessMillis(emptyList(), spent))
    }

    @Test
    fun aMovingPartEndedEarlyIsAskedForAgainWhileItStillMoves() {
        // Where the Tile's weight ends the moving part early (TileLayout.fitted), the system is
        // asked back within four sevenths of it, so that a request as late as an inexact alarm
        // may come (three quarters again) still comes while the countdown moves.
        for (minutes in listOf(70L, 56L, 30L, 6L)) {
            val entries = TilePlan.entries(sample, false, at1458, movingUntil = at1458 + minutes * 60L)
            val still = entries.first { it.next != null && !it.moves }.from
            val fresh = TilePlan.freshnessMillis(entries, at1458)
            assertEquals(minOf(TilePlan.MAX_FRESH_MILLIS, (still - at1458) * 1_000L * 4 / 7), fresh, "$minutes min")
            assertTrue(fresh * 7 / 4 <= (still - at1458) * 1_000L, "$minutes min")
        }
        // With nothing left to move, as long as the Tile is ever fresh for; and the plan's whole
        // moving part, as before.
        assertEquals(TilePlan.MAX_FRESH_MILLIS, TilePlan.freshnessMillis(TilePlan.entries(sample, false, at1458, movingUntil = at1458), at1458))
        assertEquals(TilePlan.MAX_FRESH_MILLIS, TilePlan.freshnessMillis(TilePlan.entries(sample, false, at1458), at1458))
    }

    @Test
    fun theTileIsNeverAskedForMoreThanOnceAMinute() {
        // The Tiles reference: freshness "should not be used to update your tile more frequently
        // than once a minute", and the system may throttle a Tile that asks sooner (Ruling 68
        // item 2 (a)). A moving part the Tile's weight ends two seconds on (at 16:07:58 its first
        // entry ends at 16:08:00; review round 6 found it asked back in 1,142 ms), and the notice
        // twenty seconds on, are each asked for again a minute on. The notice already showing is
        // still never asked for (0), as before.
        val beforeTheCut = TestFeeds.at(0, 16, 7, 58)
        val short = TilePlan.entries(sample, false, beforeTheCut, movingUntil = TestFeeds.at(0, 16, 8))
        assertTrue(short.first().moves && short[1].next != null && !short[1].moves)
        assertEquals(60_000L, TilePlan.freshnessMillis(short, beforeTheCut))
        val nearTheNotice = TestFeeds.at(29, 20, 23, 40)
        assertEquals(60_000L, TilePlan.freshnessMillis(plan(now = nearTheNotice), nearTheNotice))

        // Every 17 minutes through a day with the moving part ended at each of its entries, and
        // every 7 s across the feed's last start: a minute at least while anything is left to
        // show, and 0 once the notice shows.
        val start = sample.days[1].midnight
        val nows = (start until start + 86_400L step 17 * 60L) + (TestFeeds.at(29, 20, 20) until TestFeeds.at(29, 20, 30) step 7L)
        for (now in nows) {
            val whole = plan(now = now)
            for (until in listOf(now + TilePlan.MOVING_SECONDS) + whole.filter { it.moves }.map { it.from }) {
                val entries = TilePlan.entries(sample, false, now, movingUntil = until)
                val fresh = TilePlan.freshnessMillis(entries, now)
                if (entries.first().notice != null) assertEquals(0L, fresh, "at $now") else assertTrue(fresh >= 60_000L, "at $now, moving until $until: $fresh ms")
            }
        }
    }

    @Test
    fun anEntryMovesWhenItBeginsBeforeTheMovingPartEnds() {
        val ends = at1458 + 34 * 60L
        val entries = TilePlan.entries(sample, false, at1458, movingUntil = ends)
        assertTrue(entries.any { it.moves } && entries.any { it.next != null && !it.moves })
        for (entry in entries) assertEquals(entry.next != null && entry.from < ends, entry.moves, "from ${entry.from}")
        // From the moving part's end, an entry runs to the change after it, as past the moving hours.
        val first = entries.first { it.next != null && !it.moves }
        assertEquals(TestFeeds.at(0, 16, 10), first.until)
        assertEquals(entries.takeWhile { it.moves }, TilePlan.entries(sample, false, at1458).takeWhile { it.from < first.from })
    }

    @Test
    fun theTileIsLaidOutInThePhonesOrder() {
        val forward = listOf(RowPart.PIP, RowPart.NAME, RowPart.TIME)
        val back = forward.reversed()

        // An English feed on an English watch, and an Arabic feed on an Arabic watch, whose renderer
        // mirrors the row into the Arabic order itself.
        assertEquals(forward, rowOrder(rtl = false, hostRtl = false))
        assertEquals(forward, rowOrder(rtl = true, hostRtl = true))
        assertEquals(back, rowOrder(rtl = true, hostRtl = false))
        assertEquals(back, rowOrder(rtl = false, hostRtl = true))

        val line = listOf("label", "countdown")
        assertEquals(line, inTileOrder(line, rtl = false, hostRtl = false))
        assertEquals(line, inTileOrder(line, rtl = true, hostRtl = true))
        assertEquals(line.reversed(), inTileOrder(line, rtl = true, hostRtl = false))
        assertEquals(line.reversed(), inTileOrder(line, rtl = false, hostRtl = true))
    }

    private companion object {
        val NBSP = Char(0xA0)
        val FSI = Char(0x2068)
        val PDI = Char(0x2069)
    }
}
