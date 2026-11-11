package world.taqwa.app.watch

import world.taqwa.app.watch.WatchPrayer.ASR
import world.taqwa.app.watch.WatchPrayer.DHUHR
import world.taqwa.app.watch.WatchPrayer.FAJR
import world.taqwa.app.watch.WatchPrayer.ISHA
import world.taqwa.app.watch.WatchPrayer.MAGHRIB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules over the Cape Town day of the mockups (`Fixtures.capeTown`), unless a test says
 * otherwise: Fajr 04:33, Sunrise 06:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24, with
 * Fajr ending at 06:01 and Asr at 19:02. Every `now` is a Cape Town time on a day of that feed.
 */
class WatchRulesTest {

    private val feed = Fixtures.capeTown()

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long = Fixtures.at(day, hour, minute, second)

    // ---- Next, previous and progress

    @Test
    fun atTwoFiftyEightTheNextIsAsr() {
        val moment = assertNotNull(WatchRules.moment(feed, at(0, 14, 58)))

        assertEquals(WatchStart(ASR, at(0, 16, 10), 0), moment.next)
        assertEquals(WatchStart(DHUHR, at(0, 12, 33), 0), moment.previous)
        assertEquals(4320L, moment.secondsLeft)
        // 2 h 25 min into the 3 h 37 min from Dhuhr to Asr.
        assertEquals(145f / 217f, moment.progress, 1e-4f)
    }

    @Test
    fun afterIshaThePeriodRunsToTomorrowsFajr() {
        val moment = assertNotNull(WatchRules.moment(feed, at(0, 22, 30)))

        assertEquals(WatchStart(FAJR, at(1, 4, 33), 1), moment.next)
        assertEquals(WatchStart(ISHA, at(0, 20, 24), 0), moment.previous)
        assertEquals(at(1, 4, 33) - at(0, 22, 30), moment.secondsLeft)
        // 2 h 6 min into the 8 h 9 min from Isha to the next Fajr.
        assertEquals(126f / 489f, moment.progress, 1e-4f)
    }

    @Test
    fun beforeFajrThePeriodStartsAtYesterdaysIsha() {
        val moment = assertNotNull(WatchRules.moment(feed, at(1, 4, 10)))

        assertEquals(WatchStart(ISHA, at(0, 20, 24), 0), moment.previous)
        assertEquals(WatchStart(FAJR, at(1, 4, 33), 1), moment.next)
        assertEquals(23L * 60, moment.secondsLeft)
        // 7 h 46 min into the same 8 h 9 min.
        assertEquals(466f / 489f, moment.progress, 1e-4f)
    }

    @Test
    fun aStartBeginsThePeriodItOpens() {
        // At 16:10:00 Asr has arrived: it is the previous start, and the ring is empty.
        val moment = assertNotNull(WatchRules.moment(feed, at(0, 16, 10)))

        assertEquals(WatchStart(ASR, at(0, 16, 10), 0), moment.previous)
        assertEquals(WatchStart(MAGHRIB, at(0, 19, 4), 0), moment.next)
        assertEquals(2L * 3600 + 54 * 60, moment.secondsLeft)
        assertEquals(0f, moment.progress)

        // A second earlier, Asr is still ahead.
        val before = assertNotNull(WatchRules.moment(feed, at(0, 16, 9, 59)))
        assertEquals(ASR, before.next.prayer)
        assertEquals(1L, before.secondsLeft)
    }

    @Test
    fun theMomentIsNullPastTheLastStart() {
        val twoDays = Fixtures.capeTown(days = 2)

        // A second before the last start, that start is still ahead.
        val last = assertNotNull(WatchRules.moment(twoDays, at(1, 20, 23, 59)))
        assertEquals(WatchStart(ISHA, at(1, 20, 24), 1), last.next)
        assertEquals(1L, last.secondsLeft)

        // From it on there is nothing to count to.
        assertNull(WatchRules.moment(twoDays, at(1, 20, 24)))
        assertNull(WatchRules.moment(twoDays, at(1, 22, 30)))
        assertNull(WatchRules.moment(twoDays, at(40, 12, 0)))
        assertNull(WatchRules.moment(feed.copy(days = emptyList()), at(0, 12, 0)))
    }

    @Test
    fun theMomentIsNullBeforeTheFeedsFirstStart() {
        // A feed that begins at the day itself, as `capeTown()` does, holds no yesterday, so before
        // day 0's Fajr no start has arrived to measure the period from, and the rules do not invent
        // one. (The phone's own feed begins at yesterday, and has one: `capeTownSample()`.)
        assertNull(WatchRules.moment(feed, at(0, 4, 10)))
        assertNull(WatchRules.moment(feed, at(0, 4, 32, 59)))
        assertNull(WatchRules.moment(feed, at(-1, 22, 30)))

        // At Fajr the first period begins.
        val first = assertNotNull(WatchRules.moment(feed, at(0, 4, 33)))
        assertEquals(WatchStart(FAJR, at(0, 4, 33), 0), first.previous)
        assertEquals(WatchStart(DHUHR, at(0, 12, 33), 0), first.next)
        assertEquals(0f, first.progress)
    }

    @Test
    fun aLateIshaAfterTheNextFajrIsNotAStart() {
        val tromso = Fixtures.tromsoMay()
        // The premise: day 0's Isha is at 00:41 on the 18th, three minutes after day 1's Fajr, and
        // day 1's Isha is later than day 2's Fajr in the same way.
        assertEquals(Fixtures.atTromso(1, 0, 41), tromso.days[0].times.isha)
        assertEquals(Fixtures.atTromso(1, 0, 38), tromso.days[1].times.fajr)
        assertTrue(tromso.days[1].times.isha > tromso.days[2].times.fajr)

        // The flat list leaves such a start out for good, since it has no date to turn.
        val starts = WatchRules.starts(tromso)

        assertTrue(starts.none { it.prayer == ISHA && it.dayIndex == 0 })
        assertTrue(starts.none { it.prayer == ISHA && it.dayIndex == 1 })
        // Day 0's Maghrib, at 00:13, comes before day 1's Fajr, so it stays. The last day has no
        // later Fajr to drop its Isha.
        assertTrue(WatchStart(MAGHRIB, Fixtures.atTromso(1, 0, 13), 0) in starts)
        assertTrue(WatchStart(ISHA, Fixtures.atTromso(3, 0, 47), 2) in starts)
        // What remains is in time order.
        assertEquals(starts.sortedBy { it.at }, starts)

        // The phone does the same once the 18th has begun (`yesterdaysStarts` counts yesterday's
        // starts only before today's Fajr), and so does `moment`. After Maghrib the night's next
        // start is Fajr, three minutes before the dropped Isha.
        val night = assertNotNull(WatchRules.moment(tromso, Fixtures.atTromso(1, 0, 20)))
        assertEquals(WatchStart(MAGHRIB, Fixtures.atTromso(1, 0, 13), 0), night.previous)
        assertEquals(WatchStart(FAJR, Fixtures.atTromso(1, 0, 38), 1), night.next)
        // And once Fajr is in, the dropped Isha is not what comes next: Dhuhr is.
        val morning = assertNotNull(WatchRules.moment(tromso, Fixtures.atTromso(1, 0, 40)))
        assertEquals(WatchStart(FAJR, Fixtures.atTromso(1, 0, 38), 1), morning.previous)
        assertEquals(WatchStart(DHUHR, Fixtures.atTromso(1, 12, 46), 1), morning.next)
        // And when the dropped Isha's own time has passed, 00:41, it is still not the previous start.
        val later = assertNotNull(WatchRules.moment(tromso, Fixtures.atTromso(1, 0, 50)))
        assertEquals(WatchStart(FAJR, Fixtures.atTromso(1, 0, 38), 1), later.previous)
        assertEquals(WatchStart(DHUHR, Fixtures.atTromso(1, 12, 46), 1), later.next)
    }

    @Test
    fun aLateMaghribCountsUntilMidnightThenNot() {
        val night = Fixtures.lateMaghribNight()
        val asr = WatchStart(ASR, Fixtures.atTromso(1, 17, 9), 1)
        val maghrib = WatchStart(MAGHRIB, Fixtures.atTromso(2, 0, 45), 1)
        val fajr = WatchStart(FAJR, Fixtures.atTromso(2, 0, 38), 2)

        // The premise: the 18th's Maghrib falls at 00:45 on the 19th, after the 19th's Fajr at 00:38.
        assertEquals(maghrib.at, night.days[1].times.maghrib)
        assertEquals(fajr.at, night.days[2].times.fajr)

        // The phone's day holds all five of its starts, whatever hour they fall at. From Asr until
        // midnight it counts to that Maghrib, though Fajr comes first: so does the watch.
        listOf(
            Fixtures.atTromso(1, 17, 10),
            Fixtures.atTromso(1, 20, 0),
            Fixtures.atTromso(1, 23, 59, 59),
        ).forEach { now ->
            val moment = assertNotNull(WatchRules.moment(night, now), "at $now")
            assertEquals(maghrib, moment.next, "next at $now")
            assertEquals(asr, moment.previous, "previous at $now")
            assertEquals(maghrib.at - now, moment.secondsLeft, "left at $now")
        }
        val evening = assertNotNull(WatchRules.moment(night, Fixtures.atTromso(1, 20, 0)))
        assertEquals("in 4 h 45 min", WatchRules.countdown(night, evening.secondsLeft))

        // Once the 19th has begun, that Maghrib is one of yesterday's starts after today's Fajr: it is
        // dropped, and the next start is the Fajr at 00:38.
        val midnight = assertNotNull(WatchRules.moment(night, Fixtures.atTromso(2, 0, 0)))
        assertEquals(fajr, midnight.next)
        assertEquals(asr, midnight.previous)
        // Nor is it the previous start when its own time has passed, 00:45: Fajr is.
        val morning = assertNotNull(WatchRules.moment(night, Fixtures.atTromso(2, 0, 50)))
        assertEquals(fajr, morning.previous)
        assertEquals(WatchStart(DHUHR, Fixtures.atTromso(2, 12, 46), 2), morning.next)

        // The flat list, which has no date to turn, never lists it.
        assertTrue(maghrib !in WatchRules.starts(night))
    }

    @Test
    fun aStartAtTheNextFajrIsNotAStartEither() {
        // "At or after": an Isha that falls on the very instant of the next day's Fajr is dropped.
        val two = Fixtures.capeTown(days = 2)
        val meeting = two.copy(
            days = listOf(
                two.days[0].copy(times = two.days[0].times.copy(isha = two.days[1].times.fajr)),
                two.days[1],
            ),
        )

        val starts = WatchRules.starts(meeting)

        assertTrue(starts.none { it.prayer == ISHA && it.dayIndex == 0 })
        assertEquals(9, starts.size)
        // A second earlier it would have stayed.
        val justBefore = meeting.copy(
            days = listOf(
                meeting.days[0].copy(times = meeting.days[0].times.copy(isha = two.days[1].times.fajr - 1)),
                meeting.days[1],
            ),
        )
        assertTrue(WatchRules.starts(justBefore).any { it.prayer == ISHA && it.dayIndex == 0 })

        // The phone's `yesterdaysStarts` says "before today's Fajr" just as strictly: with the Isha
        // on the Fajr's instant, the next start a second earlier is the Fajr, and a second before
        // the Fajr the Isha stays.
        val atFajr = assertNotNull(WatchRules.moment(meeting, at(1, 4, 32, 59)))
        assertEquals(WatchStart(FAJR, at(1, 4, 33), 1), atFajr.next)
        val beforeIsha = assertNotNull(WatchRules.moment(justBefore, at(1, 4, 32, 58)))
        assertEquals(WatchStart(ISHA, at(1, 4, 32, 59), 0), beforeIsha.next)
    }

    @Test
    fun aNextStartThatHasPassedIsNoAnswer() {
        // Ruling 11. Engine data never has this shape: at 23:00 on day 0 every start of day 0 has
        // passed, and day 1's times here are moved two days back, so none of them is ahead either.
        // The phone answers day 1's Fajr there, a time already gone; the watch answers nothing, and
        // its surfaces show their empty form.
        val two = Fixtures.capeTown(days = 2)
        val earlier = Fixtures.shifted(two.days[1], -2 * 86_400L).copy(midnight = two.days[1].midnight)
        val odd = two.copy(days = listOf(two.days[0], earlier))

        assertNull(WatchRules.moment(odd, at(0, 23, 0)))
        // With day 1 where it belongs, the same instant counts to its Fajr.
        val moment = assertNotNull(WatchRules.moment(two, at(0, 23, 0)))
        assertEquals(WatchStart(FAJR, two.days[1].times.fajr, 1), moment.next)
    }

    @Test
    fun previousFallsBackToYesterdaysIshaWhenNothingHasArrived() {
        // Engine data never has this shape; `TimelineBuilder` has the fallback all the same, and the
        // watch restates it. Day 0's times here are moved two days on, to a day after day 1's, so
        // none of its starts is before day 1's Fajr, and at 01:00 on day 1 nothing has arrived at all.
        val two = Fixtures.capeTown(days = 2)
        val later = Fixtures.shifted(two.days[0], 2 * 86_400L).copy(midnight = two.days[0].midnight)
        val odd = two.copy(days = listOf(later, two.days[1]))

        val moment = assertNotNull(WatchRules.moment(odd, at(1, 1, 0)))

        assertEquals(WatchStart(ISHA, at(2, 20, 24), 0), moment.previous)
        assertEquals(WatchStart(FAJR, at(1, 4, 33), 1), moment.next)
        assertEquals(at(1, 4, 33) - at(1, 1, 0), moment.secondsLeft)
        // The period began after the moment it is measured at, so the ring is empty, not negative.
        assertEquals(0f, moment.progress)
    }

    @Test
    fun aCombinedMaghribAndIshaLightsMaghribOnTheListAndIshaOnTheGlance() {
        // R72: a combined polar Maghrib/Isha is one instant. The Prayer screen lights the first of
        // two prayers at the latest instant, the widgets the last prayer to have begun, and the
        // period that opens there began at the Isha.
        val combined = Fixtures.combinedMaghribIsha()
        assertEquals(combined.days[1].times.maghrib, combined.days[1].times.isha)

        assertEquals(MAGHRIB, WatchRules.litOnList(combined, at(1, 19, 4)))
        assertEquals(ISHA, WatchRules.litOnGlance(combined, at(1, 19, 4)))
        assertEquals(MAGHRIB, WatchRules.litOnList(combined, at(1, 22, 0)))
        assertEquals(ISHA, WatchRules.litOnGlance(combined, at(1, 22, 0)))

        // Before it the next start is the Maghrib, the first of the two.
        val before = assertNotNull(WatchRules.moment(combined, at(1, 19, 0)))
        assertEquals(WatchStart(MAGHRIB, at(1, 19, 4), 1), before.next)
        assertEquals(WatchStart(ASR, at(1, 16, 10), 1), before.previous)

        // From it on, the previous start is the Isha, the later of the two, and the period runs on to
        // tomorrow's Fajr.
        val after = assertNotNull(WatchRules.moment(combined, at(1, 22, 0)))
        assertEquals(WatchStart(ISHA, at(1, 19, 4), 1), after.previous)
        assertEquals(WatchStart(FAJR, at(2, 4, 33), 2), after.next)
    }

    @Test
    fun startsAreTheFivePrayersOfEachDayAndNeverSunrise() {
        val starts = WatchRules.starts(Fixtures.capeTown(days = 2))

        assertEquals(
            listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA, FAJR, DHUHR, ASR, MAGHRIB, ISHA),
            starts.map { it.prayer },
        )
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1), starts.map { it.dayIndex })
        assertEquals(starts.map { it.at }.sorted(), starts.map { it.at })
        assertEquals(WatchStart(DHUHR, at(1, 12, 33), 1), starts[6])
        assertTrue(WatchRules.starts(feed.copy(days = emptyList())).isEmpty())
    }

    // ---- The countdown and its digits

    @Test
    fun countdownFloorsToTheMinute() {
        assertEquals("in 1 h 12 min", WatchRules.countdown(feed, 4320))
        assertEquals("in 1 h 11 min", WatchRules.countdown(feed, 4319))
        assertEquals("in 1 h", WatchRules.countdown(feed, 3600))
        assertEquals("in 12 min", WatchRules.countdown(feed, 720))
        assertEquals("in under a minute", WatchRules.countdown(feed, 59))
    }

    @Test
    fun countdownAtTheEdgesOfEachForm() {
        // A whole minute is the first minute, and a second short of two is still one.
        assertEquals("in 1 min", WatchRules.countdown(feed, 60))
        assertEquals("in 1 min", WatchRules.countdown(feed, 119))
        assertEquals("in 59 min", WatchRules.countdown(feed, 3599))
        assertEquals("in 1 h 1 min", WatchRules.countdown(feed, 3660))
        assertEquals("in 1 h 59 min", WatchRules.countdown(feed, 7199))
        // More than a day is still hours: there is no days form.
        assertEquals("in 25 h 1 min", WatchRules.countdown(feed, 90_060))
        // Nothing left, or less than nothing, is under a minute too, and never a negative number.
        assertEquals("in under a minute", WatchRules.countdown(feed, 0))
        assertEquals("in under a minute", WatchRules.countdown(feed, -30))
    }

    @Test
    fun arabicLibyaCountdown() {
        val libya = Fixtures.capeTown(ui = Fixtures.arabicLibya())

        assertEquals("بعد 1 س 12 د", WatchRules.countdown(libya, 4320))
        assertEquals("بعد 1 س", WatchRules.countdown(libya, 3600))
        assertEquals("بعد 12 د", WatchRules.countdown(libya, 720))
        assertEquals("بعد أقل من دقيقة", WatchRules.countdown(libya, 59))
    }

    @Test
    fun arabicIndicDigitsInTheCountdown() {
        val saudi = Fixtures.capeTown(ui = Fixtures.arabicSaudi())

        assertEquals("بعد ١ س ١٢ د", WatchRules.countdown(saudi, 4320))
        assertEquals("بعد ١ س", WatchRules.countdown(saudi, 3600))
        assertEquals("بعد ١٢ د", WatchRules.countdown(saudi, 720))
        // Under a minute has no number to write.
        assertEquals("بعد أقل من دقيقة", WatchRules.countdown(saudi, 59))
    }

    // ---- The Galaxy Tile's compact countdown (Mohamed's report of 9 November 2026)

    /** [feed] with the compact forms given, each blank one left to the full form. */
    private fun compact(feed: WatchFeed, hoursMinutes: String, hours: String, minutes: String): WatchFeed =
        feed.copy(ui = feed.ui.copy(countdown = feed.ui.countdown.copy(compactHoursMinutes = hoursMinutes, compactHours = hours, compactMinutes = minutes)))

    @Test
    fun theCompactCountdownWritesTheCompactFormsAtTheEdgesOfEachForm() {
        val english = compact(feed, "in %1\$sh %2\$sm", "in %1\$sh", "in %1\$sm")

        assertEquals("in 1h 12m", WatchRules.compactCountdown(english, 4320))
        assertEquals("in 1h 11m", WatchRules.compactCountdown(english, 4319))
        assertEquals("in 1m", WatchRules.compactCountdown(english, 60))
        assertEquals("in 1m", WatchRules.compactCountdown(english, 119))
        assertEquals("in 59m", WatchRules.compactCountdown(english, 3599))
        assertEquals("in 1h", WatchRules.compactCountdown(english, 3600))
        assertEquals("in 1h 1m", WatchRules.compactCountdown(english, 3660))
        assertEquals("in 1h 59m", WatchRules.compactCountdown(english, 7199))
        assertEquals("in 25h 1m", WatchRules.compactCountdown(english, 90_060))
        assertEquals("in 99h 59m", WatchRules.compactCountdown(english, 99 * 3600L + 59 * 60L))
        // Under a minute has the one form, and nothing left, or less, is under a minute too.
        assertEquals("in under a minute", WatchRules.compactCountdown(english, 59))
        assertEquals("in under a minute", WatchRules.compactCountdown(english, 0))
        assertEquals("in under a minute", WatchRules.compactCountdown(english, -30))
    }

    @Test
    fun theCompactCountdownIsWrittenInTheFeedsDigits() {
        // Arabic sends none and keeps its spaces (Mohamed's call of 9 November 2026), in its digits.
        val saudi = Fixtures.capeTown(days = 1, ui = Fixtures.arabicSaudi())
        assertEquals("بعد ١ س ١٢ د", WatchRules.compactCountdown(saudi, 4320))
        assertEquals("بعد ١ س", WatchRules.compactCountdown(saudi, 3600))
        assertEquals("بعد ١٢ د", WatchRules.compactCountdown(saudi, 720))
        assertEquals("بعد أقل من دقيقة", WatchRules.compactCountdown(saudi, 59))
        val libya = Fixtures.capeTown(days = 1, ui = Fixtures.arabicLibya())
        assertEquals("بعد 1 س 12 د", WatchRules.compactCountdown(libya, 4320))
        val bengali = compact(Fixtures.capeTown(days = 1, ui = Fixtures.bengali()), "%1\$sঘঃ %2\$sমিঃ পরে", "%1\$sঘঃ পরে", "%1\$sমিঃ পরে")
        assertEquals("১ঘঃ ১২মিঃ পরে", WatchRules.compactCountdown(bengali, 4320))
        assertEquals("১০ঘঃ পরে", WatchRules.compactCountdown(bengali, 36_000))
        val urdu = compact(Fixtures.capeTown(days = 1, ui = Fixtures.urdu()), "in %1\$sh %2\$sm", "in %1\$sh", "in %1\$sm")
        assertEquals("in ۱h ۱۲m", WatchRules.compactCountdown(urdu, 4320))
    }

    @Test
    fun aFeedWithoutCompactFormsHasTheFullOnes() {
        // A feed written before them, a 1.6.0 (44) phone's, has none: the Tile writes the full forms.
        val older = compact(feed, "", "", "")
        for (seconds in listOf(4320L, 4319, 3600, 3660, 720, 60, 59, 0, -30)) {
            assertEquals(WatchRules.countdown(older, seconds), WatchRules.compactCountdown(older, seconds), "at $seconds s")
        }
        assertEquals("in 1 h 12 min", WatchRules.compactCountdown(older, 4320))
        // And a form left blank is its full one, whatever the other two are.
        val minutesOnly = compact(feed, "", " ", "in %1\$sm")
        assertEquals("in 1 h 12 min", WatchRules.compactCountdown(minutesOnly, 4320))
        assertEquals("in 1 h", WatchRules.compactCountdown(minutesOnly, 3600))
        assertEquals("in 12m", WatchRules.compactCountdown(minutesOnly, 720))
        // The full countdown never takes them: every other surface writes the full forms.
        assertEquals("in 1 h 12 min", WatchRules.countdown(compact(feed, "in %1\$sh %2\$sm", "in %1\$sh", "in %1\$sm"), 4320))
    }

    // ---- The countdown as a screen reader is told it (plan 1, Task 12)

    @Test
    fun theSpokenCountdownIsThePhonesSentenceInFullWords() {
        // The phone's prayer_a11y_countdown with its a11y_hours and a11y_minutes, never "h" and "min".
        fun said(secondsLeft: Long) = WatchRules.spokenCountdown(feed, "Asr", "16:10", secondsLeft)
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", said(4320))
        assertEquals("Asr in 1 hour 11 minutes, at 16:10", said(4319))
        assertEquals("Asr in 2 hours 1 minute, at 16:10", said(7260))
        assertEquals("Asr in 1 hour, at 16:10", said(3600))
        assertEquals("Asr in 3 hours, at 16:10", said(3 * 3600))
        assertEquals("Asr in 1 minute, at 16:10", said(119))
        assertEquals("Asr in 59 minutes, at 16:10", said(3599))
        assertEquals("Asr in under a minute, at 16:10", said(59))
        assertEquals("Asr in under a minute, at 16:10", said(-30))
    }

    @Test
    fun theSpokenCountdownInArabicTakesTheArabicPlurals() {
        val libya = Fixtures.capeTown(ui = Fixtures.arabicLibya())
        fun said(secondsLeft: Long) = WatchRules.spokenCountdown(libya, "العصر", "16:10", secondsLeft)
        assertEquals("العصر بعد 1 ساعة و12 دقيقة، الساعة 16:10", said(4320))
        assertEquals("العصر بعد 2 ساعتين و2 دقيقتين، الساعة 16:10", said(2 * 3600 + 120))
        assertEquals("العصر بعد 3 ساعات و10 دقائق، الساعة 16:10", said(3 * 3600 + 600))
        assertEquals("العصر بعد 11 دقيقة، الساعة 16:10", said(660))
        assertEquals("العصر بعد 8 ساعات، الساعة 16:10", said(8 * 3600))
        assertEquals("العصر بعد أقل من دقيقة، الساعة 16:10", said(59))
        // Saudi Arabia: the same words, every number in the phone's Arabic-Indic digits.
        val saudi = Fixtures.capeTown(ui = Fixtures.arabicSaudi())
        assertEquals("العصر بعد ١ ساعة و١٢ دقيقة، الساعة ١٦:١٠", WatchRules.spokenCountdown(saudi, "العصر", "١٦:١٠", 4320))
    }

    @Test
    fun aPhraseIsChosenByTheNumberAloneAndTheLastTakesAnyBeyond() {
        val minutes = assertNotNull(Fixtures.arabicLibya().spoken).minutes
        val libya = Fixtures.capeTown(days = 1, ui = Fixtures.arabicLibya())
        assertEquals(
            listOf("0 دقيقة", "1 دقيقة", "2 دقيقتين", "3 دقائق", "10 دقائق", "11 دقيقة", "99 دقيقة", "150 دقيقة"),
            listOf(0L, 1, 2, 3, 10, 11, 99, 150).map { WatchRules.counted(libya, minutes, it) },
        )
    }

    @Test
    fun aFeedWithoutSpokenWordsHasNoSpokenCountdown() {
        // A feed written before them, or one whose lists are empty: the watch says the screen's words.
        val before = feed.copy(ui = feed.ui.copy(spoken = null))
        assertNull(WatchRules.spokenCountdown(before, "Asr", "16:10", 4320))
        val empty = feed.copy(ui = feed.ui.copy(spoken = assertNotNull(feed.ui.spoken).copy(minutes = emptyList())))
        assertNull(WatchRules.spokenCountdown(empty, "Asr", "16:10", 4320))
    }

    @Test
    fun digitsFollowTheFeedsDigitSet() {
        val saudi = Fixtures.capeTown(days = 1, ui = Fixtures.arabicSaudi())

        assertEquals("1234567890", WatchRules.digits(feed, 1_234_567_890))
        assertEquals("١٢٣٤٥٦٧٨٩٠", WatchRules.digits(saudi, 1_234_567_890))
        assertEquals("٠", WatchRules.digits(saudi, 0))
        // Arabic text with Western digits is its own setting: Libya's.
        assertEquals("12", WatchRules.digits(Fixtures.capeTown(days = 1, ui = Fixtures.arabicLibya()), 12))

        // Urdu and Persian write the extended Arabic-Indic set, a block of its own: the zero says which.
        val urdu = saudi.copy(ui = saudi.ui.copy(language = "ur", digitZero = '۰'))
        assertEquals("۱۲۳۴۵۶۷۸۹۰", WatchRules.digits(urdu, 1_234_567_890))
        // And Bengali the Bengali one.
        val bengali = Fixtures.capeTown(days = 1, ui = Fixtures.bengali())
        assertEquals("১২৩৪৫৬৭৮৯০", WatchRules.digits(bengali, 1_234_567_890))
        assertEquals("০", WatchRules.digits(bengali, 0))
    }

    @Test
    fun bengaliDigitsInTheCountdown() {
        // The phrases are the English pattern here, the Bengali ones being unwritten (spec 1.3); the
        // digits are the phone's own, the zero it sent.
        val bengali = Fixtures.capeTown(days = 1, ui = Fixtures.bengali())

        assertEquals("in ১ h ১২ min", WatchRules.countdown(bengali, 4320))
        assertEquals("in ১ h", WatchRules.countdown(bengali, 3600))
        assertEquals("in ১২ min", WatchRules.countdown(bengali, 720))
        assertEquals("in under a minute", WatchRules.countdown(bengali, 59))
        assertEquals("১", WatchRules.digits(bengali, 1))
        assertEquals("১২", WatchRules.digits(bengali, 12))
    }

    // ---- The clock as the app writes it, and the helpers the screens write their words with

    @Test
    fun aTwelveHourTimeIsDrawnWithItsDayPeriod() {
        val twelve = Fixtures.capeTown(ui = Fixtures.english12()).days[0]

        assertEquals("4:10${NBSP}PM", WatchRules.clockText(twelve, WatchTime.ASR))
        assertEquals("16:10", WatchRules.clockText(feed.days[0], WatchTime.ASR))
    }

    @Test
    fun aTwelveHourTimeIsSpokenWhole() {
        // Isolated, so that no sentence around it can reorder its pieces: an Urdu "6:32 PM" is
        // never said "PM 6:32" (the phone's isolatedClock).
        val twelve = Fixtures.capeTown(ui = Fixtures.english12()).days[0]
        assertEquals(FSI + "4:10${NBSP}PM" + PDI, WatchRules.spokenClock(twelve, WatchTime.ASR))

        // The sample's day 0 is its second day: its first is the day before.
        val libya = Fixtures.capeTownSamples().getValue("ar-ly12").days[1]
        assertEquals("2026-10-18", libya.date)
        assertEquals(FSI + "4:10${NBSP}م" + PDI, WatchRules.spokenClock(libya, WatchTime.ASR))
    }

    @Test
    fun aTwentyFourHourTimeIsSpokenBare() {
        // Digits and a colon, in any digit set, are spliced as they are, as every 24-hour line on
        // the watch has always been: byte for byte what it was.
        assertEquals("16:10", WatchRules.spokenClock(feed.days[0], WatchTime.ASR))
        assertEquals("١٦:١٠", WatchRules.spokenClock(Fixtures.capeTown(ui = Fixtures.arabicSaudi()).days[0], WatchTime.ASR))
        assertEquals("১৬:১০", WatchRules.spokenClock(Fixtures.capeTown(days = 1, ui = Fixtures.bengali()).days[0], WatchTime.ASR))
        assertEquals("۱۶:۱۰", WatchRules.spokenClock(Fixtures.capeTown(days = 1, ui = Fixtures.urdu()).days[0], WatchTime.ASR))
    }

    @Test
    fun aTwelveHourSunriseCarriesItsDayPeriod() {
        val twelve = Fixtures.capeTown(ui = Fixtures.english12()).days[0]

        assertEquals("6:01${NBSP}AM", WatchRules.clockText(twelve, WatchTime.SUNRISE))
        assertEquals(FSI + "6:01${NBSP}AM" + PDI, WatchRules.spokenClock(twelve, WatchTime.SUNRISE))
        assertEquals("6:01", WatchRules.clockText(feed.days[0], WatchTime.SUNRISE))
        assertEquals("6:01", WatchRules.spokenClock(feed.days[0], WatchTime.SUNRISE))
    }

    @Test
    fun everyTimeIsDrawnFromItsOwnSlot() {
        val twelve = Fixtures.capeTown(days = 1, ui = Fixtures.english12()).days[0]

        assertEquals(
            listOf("4:33${NBSP}AM", "6:01${NBSP}AM", "12:33${NBSP}PM", "4:10${NBSP}PM", "7:04${NBSP}PM", "8:24${NBSP}PM"),
            WatchTime.entries.map { WatchRules.clockText(twelve, it) },
        )
        assertEquals(
            listOf("4:33", "6:01", "12:33", "16:10", "19:04", "20:24"),
            WatchTime.entries.map { WatchRules.spokenClock(feed.days[0], it) },
        )
    }

    @Test
    fun aDayWithoutItsClockTimesDrawsItsClock() {
        // A 12-hour phone from before the field sends none, and neither does a 24-hour phone: the
        // watch draws and says the clock string as it is, as it did before.
        val older = Fixtures.capeTown(days = 1, ui = Fixtures.english12()).days[0].copy(clockTime = null)

        assertEquals("4:10", WatchRules.clockText(older, WatchTime.ASR))
        assertEquals("4:10", WatchRules.spokenClock(older, WatchTime.ASR))
    }

    @Test
    fun aMarkerBeforeTheTimeIsSpokenWholeToo() {
        // Turkish writes its day period first, as the app does (TwelveHourClock.render): "ÖS 4:10".
        val turkish = Fixtures.capeTown(days = 1, ui = Fixtures.english12().copy(language = "tr", am = "ÖÖ", pm = "ÖS")).days[0]

        assertEquals("ÖS${NBSP}4:10", WatchRules.clockText(turkish, WatchTime.ASR))
        assertEquals(FSI + "ÖS${NBSP}4:10" + PDI, WatchRules.spokenClock(turkish, WatchTime.ASR))
    }

    @Test
    fun everyTimeHasItsOwnSlot() {
        val six = WatchSix("f", "s", "d", "a", "m", "i")

        assertEquals(listOf("f", "s", "d", "a", "m", "i"), WatchTime.entries.map { six.of(it) })
        assertEquals(listOf("FAJR", "SUNRISE", "DHUHR", "ASR", "MAGHRIB", "ISHA"), WatchTime.entries.map { it.name })
        WatchPrayer.entries.forEach { prayer -> assertEquals(prayer.name, WatchTime.of(prayer).name) }
    }

    @Test
    fun filledWritesEachPartInItsPlace() {
        assertEquals("13 of 100", WatchRules.filled("%1\$s of %2\$s", "13", "100"))
        // A language may put the second place first, as round 2's Bengali count does.
        assertEquals("১০০-এর মধ্যে ১৩", WatchRules.filled("%2\$s-এর মধ্যে %1\$s", "১৩", "১০০"))
        // A part is written as it is, never filled in turn: a phrase of his own may hold "%2$s".
        assertEquals("Say %2\$s, round 3", WatchRules.filled("%1\$s, round %2\$s", "Say %2\$s", "3"))
        // A place may come twice, and a place with no part stays as it is.
        assertEquals("a and a", WatchRules.filled("%1\$s and %1\$s", "a"))
        assertEquals("13 of %2\$s", WatchRules.filled("%1\$s of %2\$s", "13"))
        assertEquals("Today", WatchRules.filled("Today"))
        assertEquals("100%", WatchRules.filled("%1\$s%", "100"))
    }

    @Test
    fun inDigitsWritesTheWesternDigitsFromTheZero() {
        assertEquals("1:02:03", WatchRules.inDigits("1:02:03", '0'))
        assertEquals("١:٠٢:٠٣", WatchRules.inDigits("1:02:03", '٠'))
        assertEquals("১২:৩৪", WatchRules.inDigits("12:34", '০'))
        assertEquals("۱۲۳۴۵۶۷۸۹۰", WatchRules.inDigits("1234567890", '۰'))
        // Only 0 to 9 move: the words, and the digits of another set, stay as they are.
        assertEquals("بعد ٣ س ٤ د", WatchRules.inDigits("بعد ٣ س 4 د", '٠'))
        assertEquals("", WatchRules.inDigits("", '٠'))
    }

    // ---- Which row is lit

    @Test
    fun litOnListDropsFajrAtSunrise() {
        assertEquals(FAJR, WatchRules.litOnList(feed, at(0, 5, 0)))
        assertEquals(FAJR, WatchRules.litOnList(feed, at(0, 6, 0, 59)))
        // From sunrise a Fajr prayed now is late, so nothing is lit until Dhuhr.
        assertNull(WatchRules.litOnList(feed, at(0, 6, 1)))
        assertNull(WatchRules.litOnList(feed, at(0, 6, 40)))
        assertNull(WatchRules.litOnList(feed, at(0, 12, 32, 59)))
        assertEquals(DHUHR, WatchRules.litOnList(feed, at(0, 12, 33)))
    }

    @Test
    fun litOnListDropsAsrAtSunset() {
        assertEquals(ASR, WatchRules.litOnList(feed, at(0, 16, 10)))
        assertEquals(ASR, WatchRules.litOnList(feed, at(0, 19, 1, 59)))
        // Sunset is 19:02, two minutes before Maghrib: nothing is lit in between.
        assertNull(WatchRules.litOnList(feed, at(0, 19, 2)))
        assertNull(WatchRules.litOnList(feed, at(0, 19, 3)))
        assertEquals(MAGHRIB, WatchRules.litOnList(feed, at(0, 19, 4)))
    }

    @Test
    fun litOnListKeepsDhuhrMaghribAndIshaUntilTheNext() {
        // Their ends mark a preferred time, not a lapse the screen shows.
        assertEquals(DHUHR, WatchRules.litOnList(feed, at(0, 14, 58)))
        assertEquals(MAGHRIB, WatchRules.litOnList(feed, at(0, 20, 23, 59)))
        assertEquals(ISHA, WatchRules.litOnList(feed, at(0, 20, 24)))
        assertEquals(ISHA, WatchRules.litOnList(feed, at(0, 22, 30)))
        assertEquals(ISHA, WatchRules.litOnList(feed, at(0, 23, 59, 59)))
    }

    @Test
    fun litOnListBeforeFajrIsNull() {
        assertNull(WatchRules.litOnList(feed, at(0, 4, 10)))
        // The next morning, yesterday's Isha has begun but lights no row.
        assertNull(WatchRules.litOnList(feed, at(1, 4, 10)))
        assertNull(WatchRules.litOnList(feed, at(1, 2, 0)))
        assertEquals(FAJR, WatchRules.litOnList(feed, at(1, 4, 33)))
    }

    @Test
    fun litOnGlanceKeepsFajrUntilDhuhr() {
        assertEquals(FAJR, WatchRules.litOnGlance(feed, at(0, 5, 0)))
        assertEquals(FAJR, WatchRules.litOnGlance(feed, at(0, 6, 40)))
        assertEquals(FAJR, WatchRules.litOnGlance(feed, at(0, 12, 32, 59)))
        assertEquals(DHUHR, WatchRules.litOnGlance(feed, at(0, 12, 33)))
    }

    @Test
    fun litOnGlanceKeepsAsrUntilMaghrib() {
        // Where the list is dark from sunset, the glance holds the latest prayer to begin.
        assertEquals(ASR, WatchRules.litOnGlance(feed, at(0, 19, 3)))
        assertNull(WatchRules.litOnList(feed, at(0, 19, 3)))
        assertEquals(MAGHRIB, WatchRules.litOnGlance(feed, at(0, 19, 4)))
        assertEquals(ISHA, WatchRules.litOnGlance(feed, at(0, 22, 30)))
        assertEquals(ISHA, WatchRules.litOnGlance(feed, at(0, 23, 59, 59)))
    }

    @Test
    fun litOnGlanceBeforeFajrIsNull() {
        assertNull(WatchRules.litOnGlance(feed, at(0, 4, 10)))
        // The next morning too: yesterday's Isha is not today's latest prayer.
        assertNull(WatchRules.litOnGlance(feed, at(1, 4, 10)))
        assertEquals(FAJR, WatchRules.litOnGlance(feed, at(1, 4, 33)))
    }

    @Test
    fun aPolarDayWithoutEndsKeepsThePlainRule() {
        val polar = feed.withEnds(fajrEnd = false, asrEnd = false)
        assertNull(polar.days[0].times.fajrEnd)
        assertNull(polar.days[0].times.asrEnd)

        // With no sunrise or sunset to end them, Fajr and Asr stay lit until the next prayer.
        assertEquals(FAJR, WatchRules.litOnList(polar, at(0, 6, 40)))
        assertEquals(FAJR, WatchRules.litOnList(polar, at(0, 12, 32, 59)))
        assertEquals(ASR, WatchRules.litOnList(polar, at(0, 19, 3)))
        assertEquals(FAJR, WatchRules.litOnGlance(polar, at(0, 6, 40)))
        // The same feed with its ends in does not.
        assertNull(WatchRules.litOnList(feed, at(0, 6, 40)))
    }

    @Test
    fun anEndMissingOnItsOwnLeavesTheOtherInForce() {
        val noFajrEnd = feed.withEnds(fajrEnd = false, asrEnd = true)
        assertEquals(FAJR, WatchRules.litOnList(noFajrEnd, at(0, 6, 40)))
        assertNull(WatchRules.litOnList(noFajrEnd, at(0, 19, 3)))

        val noAsrEnd = feed.withEnds(fajrEnd = true, asrEnd = false)
        assertNull(WatchRules.litOnList(noAsrEnd, at(0, 6, 40)))
        assertEquals(ASR, WatchRules.litOnList(noAsrEnd, at(0, 19, 3)))
    }

    @Test
    fun theLitRowFollowsTheDayNowIsOn() {
        // Friday, day 5, and the last day of the feed.
        assertEquals(DHUHR, WatchRules.litOnList(feed, at(5, 14, 58)))
        assertEquals(ASR, WatchRules.litOnGlance(feed, at(29, 17, 0)))
        assertNull(WatchRules.litOnList(feed, at(29, 6, 40)))
    }

    @Test
    fun aStartOfYesterdaysAfterTodaysLatestLightsNothing() {
        // `TimelineBuilder.build`'s guard: today's prayer is current only when nothing of
        // yesterday's began after it. Engine data never has this shape, since a day's Fajr comes
        // first; here day 1 has an Asr at 00:10 and day 0 an Isha at 00:20, before day 1's Fajr.
        val odd = Fixtures.oddNight()

        assertNull(WatchRules.litOnList(odd, at(1, 0, 40)))
        // The widgets' rule looks at today alone, and lights the Asr.
        assertEquals(ASR, WatchRules.litOnGlance(odd, at(1, 0, 40)))
        // The ring is the phone's too: the latest start to have arrived is yesterday's Isha, at 00:20.
        val moment = assertNotNull(WatchRules.moment(odd, at(1, 0, 40)))
        assertEquals(WatchStart(ISHA, at(1, 0, 20), 0), moment.previous)
        assertEquals(WatchStart(FAJR, at(1, 4, 33), 1), moment.next)
        // Without the Isha ahead of it the same Asr is lit.
        val ordinary = odd.copy(days = listOf(odd.days[0].copy(times = odd.days[0].times.copy(isha = at(0, 20, 24))), odd.days[1]))
        assertEquals(ASR, WatchRules.litOnList(ordinary, at(1, 0, 40)))
    }

    @Test
    fun theDayTurnsOverAtLocalMidnight() {
        // The feed carries each date's local midnight, so the watch turns the day over where the
        // Prayer screen does, at 00:00 and not at the 00:33 where the sun is lowest. At 00:10 on
        // day 1 today is day 1, which has no prayer yet, and yesterday's Isha lights no row.
        assertNull(WatchRules.litOnList(feed, at(1, 0, 10)))
        assertNull(WatchRules.litOnGlance(feed, at(1, 0, 10)))

        // The second before midnight is day 0's, and the instant of midnight is day 1's.
        assertEquals(ISHA, WatchRules.litOnList(feed, at(0, 23, 59, 59)))
        assertEquals(ISHA, WatchRules.litOnGlance(feed, at(0, 23, 59, 59)))
        assertNull(WatchRules.litOnList(feed, at(1, 0, 0)))
        assertNull(WatchRules.litOnGlance(feed, at(1, 0, 0)))
        // Which a prayer at 00:00 sharp shows: it is the new day's, and already in.
        val two = Fixtures.capeTown(days = 2)
        val fajrAtMidnight = two.copy(
            days = listOf(two.days[0], two.days[1].let { it.copy(times = it.times.copy(fajr = it.midnight)) }),
        )
        assertEquals(ISHA, WatchRules.litOnGlance(fajrAtMidnight, at(0, 23, 59, 59)))
        assertEquals(FAJR, WatchRules.litOnGlance(fajrAtMidnight, at(1, 0, 0)))

        // Tromsø: Maghrib (00:13) and Isha (00:41) of the 17th fall on the 18th, whose own Fajr is
        // at 00:38. At 00:20 today is the 18th and nothing of it has begun; at 00:40 its Fajr has.
        val tromso = Fixtures.tromsoMay()
        assertEquals(ASR, WatchRules.litOnList(tromso, Fixtures.atTromso(0, 23, 59, 59)))
        assertEquals(ASR, WatchRules.litOnGlance(tromso, Fixtures.atTromso(0, 23, 59, 59)))
        assertNull(WatchRules.litOnList(tromso, Fixtures.atTromso(1, 0, 20)))
        assertNull(WatchRules.litOnGlance(tromso, Fixtures.atTromso(1, 0, 20)))
        assertEquals(FAJR, WatchRules.litOnList(tromso, Fixtures.atTromso(1, 0, 40)))
        assertEquals(FAJR, WatchRules.litOnGlance(tromso, Fixtures.atTromso(1, 0, 40)))
    }

    @Test
    fun aDayOfTwentyFiveHoursEndsAtItsOwnMidnight() {
        // Where the clocks go back an hour a date is 25 hours long, and where they go forward 23: the
        // next date's midnight is not 24 hours on, and the feed says where it is.
        val two = Fixtures.capeTown(days = 2)

        val longDay = two.copy(days = listOf(two.days[0], Fixtures.shifted(two.days[1], 3_600)))
        assertEquals(two.days[0].midnight + 25 * 3_600, longDay.days[1].midnight)
        assertEquals(ISHA, WatchRules.litOnList(longDay, longDay.days[1].midnight - 1))
        assertNull(WatchRules.litOnList(longDay, longDay.days[1].midnight))

        val shortDay = two.copy(days = listOf(two.days[0], Fixtures.shifted(two.days[1], -3_600)))
        assertEquals(two.days[0].midnight + 23 * 3_600, shortDay.days[1].midnight)
        assertEquals(ISHA, WatchRules.litOnList(shortDay, shortDay.days[1].midnight - 1))
        assertNull(WatchRules.litOnList(shortDay, shortDay.days[1].midnight))
    }

    @Test
    fun aDayWithoutMidnightKeepsTheFallback() {
        // A midnight of 0 is the phone saying it cannot say. The day then turns over at solar
        // midnight, halfway between one Dhuhr and the next, 00:33 here: Isha stays lit a little into
        // the night, and then nothing is.
        val unknown = Fixtures.withoutMidnights(feed)
        assertEquals(ISHA, WatchRules.litOnList(unknown, at(1, 0, 10)))
        assertEquals(ISHA, WatchRules.litOnGlance(unknown, at(1, 0, 32, 59)))
        assertEquals(ISHA, WatchRules.litOnList(unknown, at(1, 0, 32, 59)))
        assertNull(WatchRules.litOnList(unknown, at(1, 0, 33)))
        assertNull(WatchRules.litOnGlance(unknown, at(1, 0, 33)))

        // Halfway is between the two Dhuhrs, which drift from one day to the next: with day 1's ten
        // minutes later, the turn-over moves five minutes later, to 00:38.
        val pair = Fixtures.capeTown(days = 2)
        val drifting = Fixtures.withoutMidnights(
            pair.copy(days = listOf(pair.days[0], Fixtures.shifted(pair.days[1], 600))),
        )
        assertEquals(ISHA, WatchRules.litOnGlance(drifting, at(1, 0, 37, 59)))
        assertNull(WatchRules.litOnGlance(drifting, at(1, 0, 38)))

        // The instant of the turn-over belongs to the new day. In Tromsø, Fajr (00:38) is already in
        // by solar midnight (00:46), so the new day has something to light at once.
        val tromso = Fixtures.withoutMidnights(Fixtures.tromsoMay())
        assertEquals(ISHA, WatchRules.litOnGlance(tromso, Fixtures.atTromso(1, 0, 45, 59)))
        assertEquals(FAJR, WatchRules.litOnGlance(tromso, Fixtures.atTromso(1, 0, 46)))

        // At the end of the feed, the last day takes half a day from its own Dhuhr, and the feed is
        // silent beyond.
        val twoDays = Fixtures.withoutMidnights(Fixtures.capeTown(days = 2))
        assertEquals(ISHA, WatchRules.litOnGlance(twoDays, at(2, 0, 32, 59)))
        assertNull(WatchRules.litOnGlance(twoDays, at(2, 0, 33)))
        assertNull(WatchRules.litOnList(twoDays, at(2, 14, 58)))
        assertNull(WatchRules.litOnGlance(twoDays, at(2, 14, 58)))

        // A feed that lacks one midnight falls back at that boundary alone: day 1 here begins at
        // 00:33, where day 0 and day 2 begin at their midnights.
        val three = Fixtures.capeTown(days = 3)
        val mixed = three.copy(days = listOf(three.days[0], three.days[1].copy(midnight = 0L), three.days[2]))
        assertEquals(ISHA, WatchRules.litOnGlance(mixed, at(1, 0, 10)))
        assertNull(WatchRules.litOnGlance(mixed, at(1, 0, 33)))
        assertEquals(ISHA, WatchRules.litOnGlance(mixed, at(1, 23, 59, 59)))
        assertNull(WatchRules.litOnGlance(mixed, at(2, 0, 0)))
    }

    @Test
    fun aDayStartsWhereTheDateTurnsOver() {
        // dayStart is the rules' own turn-over, with a midnight and without one: the day is today
        // from that second, and the day before until the second before it.
        val pair = Fixtures.capeTown(days = 2)
        val drifting = pair.copy(days = listOf(pair.days[0], Fixtures.shifted(pair.days[1], 600)))
        val three = Fixtures.capeTown(days = 3)
        val mixed = three.copy(days = listOf(three.days[0], three.days[1].copy(midnight = 0L), three.days[2]))
        for ((what, subject) in listOf(
            "with midnights" to feed,
            "without them" to Fixtures.withoutMidnights(feed),
            "drifting Dhuhrs" to Fixtures.withoutMidnights(drifting),
            "one midnight missing" to mixed,
            "Tromsø without them" to Fixtures.withoutMidnights(Fixtures.tromsoMay()),
        )) {
            for (index in subject.days.indices) {
                val start = WatchRules.dayStart(subject, index)
                assertEquals(index, WatchRules.today(subject, start), "$what: day $index from its start")
                if (index > 0) {
                    assertEquals(index - 1, WatchRules.today(subject, start - 1), "$what: day ${index - 1} until then")
                }
            }
        }
        // The day's own midnight where the phone gave one; halfway between the Dhuhrs where it did not.
        assertEquals(feed.days[1].midnight, WatchRules.dayStart(feed, 1))
        val unknown = Fixtures.withoutMidnights(feed)
        assertEquals((feed.days[0].times.dhuhr + feed.days[1].times.dhuhr) / 2, WatchRules.dayStart(unknown, 1))
        assertEquals(at(1, 0, 33), WatchRules.dayStart(unknown, 1))
    }

    @Test
    fun noRowIsLitWhereTheFeedHasNoDay() {
        val twoDays = Fixtures.capeTown(days = 2)

        // The evening before the feed begins, and the evenings after it ends: the feed is silent.
        assertNull(WatchRules.litOnList(twoDays, at(-1, 22, 30)))
        assertNull(WatchRules.litOnGlance(twoDays, at(-1, 22, 30)))
        assertEquals(ISHA, WatchRules.litOnGlance(twoDays, at(1, 22, 30)))
        // The last day runs a day from its own midnight; the next date, which the feed does not
        // hold, begins at 00:00.
        assertEquals(ISHA, WatchRules.litOnGlance(twoDays, at(1, 23, 59, 59)))
        assertNull(WatchRules.litOnGlance(twoDays, at(2, 0, 0)))
        assertNull(WatchRules.litOnList(twoDays, at(2, 14, 58)))
        assertNull(WatchRules.litOnGlance(twoDays, at(2, 14, 58)))
        assertNull(WatchRules.litOnList(feed.copy(days = emptyList()), at(0, 14, 58)))
        assertNull(WatchRules.litOnGlance(feed.copy(days = emptyList()), at(0, 14, 58)))
    }

    // ---- Which day it is

    @Test
    fun todayIsThePlaceOfTheDayNowIsOn() {
        assertEquals(0, WatchRules.today(feed, at(0, 14, 58)))
        // Friday 23 October, the sixth date of the feed's thirty.
        assertEquals(5, WatchRules.today(feed, at(5, 0, 0)))
        assertEquals(5, WatchRules.today(feed, at(5, 23, 59, 59)))
        assertEquals(6, WatchRules.today(feed, at(6, 0, 0)))
        // It is the day the rules work on, so it turns over where they do: at the day's own
        // midnight, the instant of it belonging to the new day.
        assertEquals(4, WatchRules.today(feed, at(5, 0, 0) - 1))
        // The last day runs a day from its own midnight, and the feed is silent on either side.
        assertEquals(29, WatchRules.today(feed, at(29, 23, 59, 59)))
        assertNull(WatchRules.today(feed, at(30, 0, 0)))
        assertNull(WatchRules.today(feed, at(-1, 23, 59, 59)))
        assertNull(WatchRules.today(feed.copy(days = emptyList()), at(0, 12, 0)))
    }

    @Test
    fun todayKeepsTheDaysOfTwentyFiveHoursAndTheSolarFallback() {
        val two = Fixtures.capeTown(days = 2)
        val longDay = two.copy(days = listOf(two.days[0], Fixtures.shifted(two.days[1], 3_600)))
        assertEquals(0, WatchRules.today(longDay, longDay.days[1].midnight - 1))
        assertEquals(1, WatchRules.today(longDay, longDay.days[1].midnight))

        // A midnight of 0 turns the day over at solar midnight, 00:33 here: the same instant that
        // `litOnList` and `litOnGlance` turn over at, because all three take the day from one rule.
        val unknown = Fixtures.withoutMidnights(feed)
        assertEquals(0, WatchRules.today(unknown, at(1, 0, 32, 59)))
        assertEquals(1, WatchRules.today(unknown, at(1, 0, 33)))
    }

    // ---- When the watch asks the phone for a new feed

    @Test
    fun needsRefreshThreeDaysBeforeTheEnd() {
        val lastStart = at(29, 20, 24)
        val threeDays = 3 * 86_400L

        // Written the moment it is read, so that only the end of the feed is in question.
        fun needsRefresh(now: Long) = WatchRules.needsRefresh(feed.copy(writtenAt = now), now)

        assertFalse(needsRefresh(at(0, 14, 58)))
        assertFalse(needsRefresh(lastStart - threeDays - 1))
        // Exactly three days away is not less than three.
        assertFalse(needsRefresh(lastStart - threeDays))
        assertTrue(needsRefresh(lastStart - threeDays + 1))
        assertTrue(needsRefresh(lastStart))
        assertTrue(needsRefresh(lastStart + 3 * 3600))
        // No start at all is nothing to rely on.
        assertTrue(WatchRules.needsRefresh(feed.copy(days = emptyList()), at(0, 14, 58)))
    }

    @Test
    fun needsRefreshAWeekAfterWriting() {
        val week = 7 * 86_400L
        val written = feed.writtenAt

        assertFalse(WatchRules.needsRefresh(feed, written))
        assertFalse(WatchRules.needsRefresh(feed, written + week - 1))
        // Exactly a week old is not more than a week.
        assertFalse(WatchRules.needsRefresh(feed, written + week))
        assertTrue(WatchRules.needsRefresh(feed, written + week + 1))
        // A watch clock a little behind the phone's is not a stale feed.
        assertFalse(WatchRules.needsRefresh(feed, written - 3600))
    }

    // ---- The fixtures themselves, which the rules and the Wear app's sample feed lean on

    @Test
    fun theCapeTownFixtureIsTheMockupsDay() {
        val first = feed.days[0]

        assertEquals(30, feed.days.size)
        assertEquals(WatchFeedJson.VERSION, feed.version)
        assertEquals("2026-10-18", first.date)
        assertEquals(at(0, 4, 33), first.times.fajr)
        assertEquals(at(0, 6, 1), first.times.sunrise)
        assertEquals(at(0, 12, 33), first.times.dhuhr)
        assertEquals(at(0, 16, 10), first.times.asr)
        assertEquals(at(0, 19, 4), first.times.maghrib)
        assertEquals(at(0, 20, 24), first.times.isha)
        assertEquals(at(0, 6, 1), first.times.fajrEnd)
        assertEquals(at(0, 19, 2), first.times.asrEnd)
        assertEquals(at(0, 0, 0), first.midnight)
        assertEquals(WatchSix("4:33", "6:01", "12:33", "16:10", "19:04", "20:24"), first.clock)
        assertEquals(WatchSix("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"), first.names)
        assertEquals("6 Jumada al-Ula 1448", first.hijri)
        // The face's form, the day and the month with no year (Ruling 24), as the mockups write it.
        assertEquals("6 Jumada al-Ula", first.hijriDayMonth)
        assertEquals("SUN 18 OCT", first.gregorian)
        assertEquals("SUN", first.weekday)
        assertEquals("18", first.dayNumber)
        assertEquals("Cape Town", feed.place.city)
        assertEquals("6,600", feed.place.qiblaKm)

        // Later days repeat the clock times on their own dates.
        feed.days.forEachIndexed { index, day ->
            assertEquals(at(index, 12, 33), day.times.dhuhr)
            assertEquals(at(index, 0, 0), day.midnight)
            assertEquals(first.clock, day.clock)
        }
        assertEquals("2026-10-19", feed.days[1].date)
        assertEquals("7 Jumada al-Ula 1448", feed.days[1].hijri)
        assertEquals("MON 19 OCT", feed.days[1].gregorian)
        assertEquals("2026-11-01", feed.days[14].date)
        assertEquals("SUN 1 NOV", feed.days[14].gregorian)
        assertEquals("2026-11-16", feed.days[29].date)
        // The month turns after 30 Jumada al-Ula, on 12 November.
        assertEquals("30 Jumada al-Ula 1448", feed.days[24].hijri)
        assertEquals("1 Jumada al-Akhirah 1448", feed.days[25].hijri)
        assertEquals("1 Jumada al-Akhirah", feed.days[25].hijriDayMonth)
    }

    @Test
    fun theSampleFeedBeginsAtYesterday() {
        val sample = Fixtures.capeTownSample()

        // Yesterday and thirty days, 31 in all, from Saturday 17 October: the mockups' Sunday has its
        // yesterday.
        assertEquals(31, sample.days.size)
        assertEquals("2026-10-17", sample.days[0].date)
        assertEquals("SAT 17 OCT", sample.days[0].gregorian)
        assertEquals("5 Jumada al-Ula 1448", sample.days[0].hijri)
        assertEquals(at(-1, 0, 0), sample.days[0].midnight)
        assertEquals("2026-11-16", sample.days[30].date)
        // From the second day on it is capeTown()'s thirty days, written on the mockups' day.
        assertEquals(feed.days, sample.days.drop(1))
        assertEquals(feed.writtenAt, sample.writtenAt)
        assertEquals(feed.place, sample.place)
        assertEquals(feed.ui, sample.ui)

        // So the night before Fajr on the mockups' day has its previous start: yesterday's Isha.
        val night = assertNotNull(WatchRules.moment(sample, at(0, 4, 10)))
        assertEquals(WatchStart(ISHA, at(-1, 20, 24), 0), night.previous)
        assertEquals(WatchStart(FAJR, at(0, 4, 33), 1), night.next)

        // And two phrases of his own: one in Arabic of 36 characters, which takes two lines on the
        // counter, and one in Latin letters with a target of its own.
        assertEquals(
            listOf(
                WatchPhrase("custom_1730000000000", "سبحان الله وبحمده، سبحان الله العظيم", 100),
                WatchPhrase("custom_1730000000001", "Astaghfirullah wa atubu ilayh", 33),
            ),
            sample.tasbeeh,
        )
        assertEquals(36, sample.tasbeeh[0].phrase.length)
    }

    @Test
    fun everyFridayNamesDhuhrJumuah() {
        val fridays = feed.days.indices.filter { feed.days[it].weekday == "FRI" }

        assertEquals(listOf(5, 12, 19, 26), fridays)
        assertEquals("Jumuʿah", feed.days[5].names.dhuhr)
        assertEquals("FRI 23 OCT", feed.days[5].gregorian)
        feed.days.forEachIndexed { index, day ->
            assertEquals(if (index in fridays) "Jumuʿah" else "Dhuhr", day.names.dhuhr, "day $index")
            assertEquals("Fajr", day.names.fajr)
            assertEquals("Isha", day.names.isha)
        }
    }

    @Test
    fun eachFixtureLanguageWritesItsClockAndDigits() {
        val twelve = Fixtures.capeTown(days = 1, ui = Fixtures.english12()).days[0]
        assertEquals(WatchSix("4:33", "6:01", "12:33", "4:10", "7:04", "8:24"), twelve.clock)
        // The phone's own 12-hour times beside them, as TwelveHourClock.render writes them: the day
        // period after a no-break space.
        assertEquals(
            WatchSix("4:33${NBSP}AM", "6:01${NBSP}AM", "12:33${NBSP}PM", "4:10${NBSP}PM", "7:04${NBSP}PM", "8:24${NBSP}PM"),
            twelve.clockTime,
        )
        // On every day of the 12-hour feeds, each day's times being the mockups', and on none of a
        // 24-hour one.
        val twelveFeeds = listOf(Fixtures.capeTown(ui = Fixtures.english12())) +
            Fixtures.capeTownSamples().filterKeys { it.endsWith("12") }.values
        twelveFeeds.forEach { twelveFeed ->
            val first = assertNotNull(twelveFeed.days[0].clockTime, twelveFeed.ui.language)
            twelveFeed.days.forEach { day -> assertEquals(first, day.clockTime, "${twelveFeed.ui.language} ${day.date}") }
        }
        assertTrue(feed.days.all { it.clockTime == null })
        assertTrue(Fixtures.capeTownSamples().getValue("ar-sa").days.all { it.clockTime == null })
        val libyaTwelve = Fixtures.capeTownSamples().getValue("ar-ly12").days[1]
        assertEquals("4:10${NBSP}م", libyaTwelve.clockTime?.asr)
        assertEquals("4:33${NBSP}ص", libyaTwelve.clockTime?.fajr)

        // The interface's script: transliterations show under a Latin one alone.
        assertTrue(Fixtures.english24().latinScript)
        assertTrue(Fixtures.english12().latinScript)
        assertFalse(Fixtures.arabicLibya().latinScript)
        assertFalse(Fixtures.arabicSaudi().latinScript)
        // The words: English for an English phone, the phone's Arabic for an Arabic one.
        assertEquals(WatchScreenWords.ENGLISH, Fixtures.english24().screens)
        assertEquals(Fixtures.ARABIC_SCREENS, Fixtures.arabicLibya().screens)
        assertEquals(Fixtures.ARABIC_SCREENS, Fixtures.arabicSaudi().screens)
        // The ring's digits: a Saudi phone's ring counts down in Western digits
        // (CountdownDigits.westernFallback), its clock strings in Arabic-Indic ones.
        assertEquals('0', Fixtures.arabicSaudi().ringDigitZero)
        assertEquals('٠', Fixtures.arabicSaudi().digitZero)
        assertNull(Fixtures.arabicLibya().ringDigitZero)
        assertNull(Fixtures.english24().ringDigitZero)

        val libya = Fixtures.capeTown(days = 1, ui = Fixtures.arabicLibya())
        assertEquals(WatchSix("4:33", "6:01", "12:33", "16:10", "19:04", "20:24"), libya.days[0].clock)
        assertEquals(WatchSix("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"), libya.days[0].names)
        assertEquals("6 جمادى الأولى 1448", libya.days[0].hijri)
        assertEquals("6 جمادى الأولى", libya.days[0].hijriDayMonth)
        assertEquals("الأحد 18 أكتوبر", libya.days[0].gregorian)
        assertEquals("الأحد", libya.days[0].weekday)
        assertEquals("18", libya.days[0].dayNumber)
        assertEquals("كيب تاون", libya.place.city)

        val saudi = Fixtures.capeTown(days = 1, ui = Fixtures.arabicSaudi())
        assertEquals(WatchSix("٤:٣٣", "٦:٠١", "١٢:٣٣", "١٦:١٠", "١٩:٠٤", "٢٠:٢٤"), saudi.days[0].clock)
        assertEquals("٦ جمادى الأولى ١٤٤٨", saudi.days[0].hijri)
        assertEquals("٦ جمادى الأولى", saudi.days[0].hijriDayMonth)
        assertEquals("الأحد ١٨ أكتوبر", saudi.days[0].gregorian)
        assertEquals("١٨", saudi.days[0].dayNumber)
        assertEquals("٦٬٦٠٠", saudi.place.qiblaKm)
        assertEquals("الجمعة", Fixtures.capeTown(ui = Fixtures.arabicSaudi()).days[5].names.dhuhr)

        // Bengali is digits alone: the words stay English.
        val bengali = Fixtures.capeTown(days = 1, ui = Fixtures.bengali())
        assertEquals(
            WatchSix("৪:৩৩", "৬:০১", "১২:৩৩", "১৬:১০", "১৯:০৪", "২০:২৪"),
            bengali.days[0].clock,
        )
        assertEquals("SUN ১৮ OCT", bengali.days[0].gregorian)
        assertEquals("৬ Jumada al-Ula ১৪৪৮", bengali.days[0].hijri)
        assertEquals("৬,৬০০", bengali.place.qiblaKm)
    }

    @Test
    fun everyFixtureFeedIsOneTheCodecReadsBack() {
        listOf(
            Fixtures.capeTown(),
            Fixtures.capeTown(ui = Fixtures.english12()),
            Fixtures.capeTown(ui = Fixtures.arabicLibya()),
            Fixtures.capeTown(ui = Fixtures.arabicSaudi()),
            Fixtures.capeTown(ui = Fixtures.bengali()),
            Fixtures.capeTownSample(),
            Fixtures.tromsoMay(),
        ).plus(Fixtures.capeTownSamples().values).forEach { fixture ->
            assertEquals(fixture, WatchFeedJson.decode(WatchFeedJson.encode(fixture)), fixture.ui.language)
        }
    }

    /** This feed with the day ends of every day kept or taken out, as a polar day lacks them. */
    private fun WatchFeed.withEnds(fajrEnd: Boolean, asrEnd: Boolean): WatchFeed = copy(
        days = days.map { day ->
            day.copy(
                times = day.times.copy(
                    fajrEnd = day.times.fajrEnd.takeIf { fajrEnd },
                    asrEnd = day.times.asrEnd.takeIf { asrEnd },
                ),
            )
        },
    )

    private companion object {
        /** U+2068 FIRST STRONG ISOLATE and U+2069 POP DIRECTIONAL ISOLATE, which keep a time whole in a sentence. */
        val FSI = Char(0x2068).toString()
        val PDI = Char(0x2069).toString()

        /** U+00A0 NO-BREAK SPACE, which the app writes between a 12-hour time and its day period. */
        val NBSP = Char(0x00A0).toString()
    }
}
