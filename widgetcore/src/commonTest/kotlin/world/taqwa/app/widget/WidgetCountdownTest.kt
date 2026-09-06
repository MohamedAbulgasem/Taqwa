package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Finding I8: the Android widget rendered `countdownMinutes` verbatim, and the mirror is only ever
 * written while the Today screen is open. The half-hourly `APPWIDGET_UPDATE` then re-rendered the
 * same frozen number, so a widget could sit on the home screen all afternoon insisting a prayer
 * was forty minutes away.
 *
 * Every case here writes the mirror at [writtenAt] and reads it back at some later moment, which
 * is exactly the shape of the bug.
 */
class WidgetCountdownTest {

    private val writtenAt = 1_757_170_800L // an arbitrary fixed T, in epoch seconds
    private val fortyMinutes = 40 * 60L

    /** What `WidgetMirrorWriter` would have stored at T, with Asr forty minutes out. */
    private val writtenAtT = WidgetSnapshot(
        nextPrayer = Prayer.ASR,
        countdownMinutes = 40,
        nextClockTime = "15:47",
        allClockTimes = mapOf(Prayer.ASR to "15:47"),
        currentPrayer = Prayer.DHUHR,
        languageTag = "en-US",
        ringProgress = 0.42f,
        countdownLabel = "Asr in",
        nextPrayerEpochSeconds = writtenAt + fortyMinutes,
        previousPrayerEpochSeconds = writtenAt - 2 * 60 * 60L,
    )

    // The headline case from the review: open the app at 14:00 with Asr forty minutes away, close
    // it, and look at the home screen at 17:00. The old code said "0:40". Nothing here may.
    @Test
    fun aSnapshotRenderedThreeHoursLateDoesNotStillClaimFortyMinutes() {
        val threeHoursLater = writtenAt + 3 * 60 * 60L
        val remaining = WidgetCountdown.remainingMinutesAt(writtenAtT, threeHoursLater)
        assertNotEquals(40L, remaining)
        assertNull(remaining, "a prayer two hours in the past has no honest countdown")
    }

    @Test
    fun theCountdownIsDerivedFromTheRenderClockNotTheWriteClock() {
        assertEquals(40L, WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt))
        assertEquals(30L, WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt + 10 * 60L))
        assertEquals(1L, WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt + 39 * 60L))
    }

    // Truncation, matching `TodayState.countdown.inWholeMinutes`, so the widget and the Today
    // screen never disagree about the same moment by a minute.
    @Test
    fun partMinutesTruncateTheSameWayTheTodayScreenTruncates() {
        assertEquals(39L, WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt + 59L))
        assertEquals(0L, WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt + fortyMinutes - 1L))
        assertEquals(0L, WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt + fortyMinutes))
    }

    // One second past the prayer, the widget already knows it no longer has an answer — it does not
    // wander into negative numbers or clamp to a fake zero.
    @Test
    fun theFirstSecondAfterThePrayerAlreadyYieldsNoCountdown() {
        assertNull(WidgetCountdown.remainingMinutesAt(writtenAtT, writtenAt + fortyMinutes + 1L))
    }

    // A mirror left behind by a build from before this field existed deserialises with the `0`
    // sentinel. There is genuinely nothing to count towards, so there is no countdown to show.
    @Test
    fun aMirrorWithNoRecordedInstantYieldsNoCountdownRatherThanTheFrozenOne() {
        val legacy = writtenAtT.copy(nextPrayerEpochSeconds = 0L)
        assertNull(WidgetCountdown.remainingMinutesAt(legacy, writtenAt))
    }
}
