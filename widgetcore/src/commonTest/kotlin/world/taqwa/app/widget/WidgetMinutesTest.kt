package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B36. The iOS timeline dated its entries `Date() + k·60`, on whatever second the reload landed,
 * and worked each one out at its own date. An entry dated 14:59:37 then said "Asr in 0:00" until
 * 15:00:37, well after Asr had begun, and every entry showed a digit one too high for the part of
 * the next minute it was still on screen. Dating entries on whole minutes alone does not cure the
 * second: an entry worked out on its own whole minute is one too high for all of it.
 */
class WidgetMinutesTest {

    private val hour = 3_600L
    private val midnight = 1_757_116_800L // an arbitrary local midnight, in epoch seconds
    private val now = midnight + 14 * hour + 58 * 60 + 37 // 14:58:37, a ragged second
    private val asr = midnight + 15 * hour

    private fun day(index: Int) = listOf(
        ScheduledPrayer(Prayer.FAJR, midnight + index * 24 * hour + 5 * hour, "05:00", index),
        ScheduledPrayer(Prayer.DHUHR, midnight + index * 24 * hour + 12 * hour, "12:00", index),
        ScheduledPrayer(Prayer.ASR, midnight + index * 24 * hour + 15 * hour, "15:00", index),
        ScheduledPrayer(Prayer.MAGHRIB, midnight + index * 24 * hour + 18 * hour, "18:00", index),
        ScheduledPrayer(Prayer.ISHA, midnight + index * 24 * hour + 20 * hour, "20:00", index),
    )

    /** Written at 13:00 on day 0, when Asr was next. */
    private val snapshot = WidgetSnapshot(
        nextPrayer = Prayer.ASR,
        countdownMinutes = 120,
        nextClockTime = "15:00",
        allClockTimes = day(0).associate { it.prayer to it.clockTime },
        currentPrayer = Prayer.DHUHR,
        languageTag = "en-US",
        ringProgress = 0.33f,
        countdownLabel = "Asr in",
        nextPrayerEpochSeconds = asr,
        previousPrayerEpochSeconds = midnight + 12 * hour,
        schedule = day(0) + day(1),
    )

    private fun label(at: Long) = WidgetContentBuilder.build(snapshot, at).countdownLabel

    @Test
    fun entriesStartOnTheMinuteThatHoldsNowAndStepByWholeMinutes() {
        val minutes = WidgetMinutes.timeline(now, 60)
        assertEquals(60, minutes.size)
        assertEquals(midnight + 14 * hour + 58 * 60, minutes.first().showAtEpochSeconds)
        minutes.zipWithNext().forEach { (a, b) -> assertEquals(60L, b.showAtEpochSeconds - a.showAtEpochSeconds) }
    }

    @Test
    fun eachEntryIsWorkedOutHalfWayThroughItsMinute() {
        WidgetMinutes.timeline(now, 60).forEach { assertEquals(it.showAtEpochSeconds + 30, it.evaluateAtEpochSeconds) }
    }

    // The "one too high for part of every minute" symptom: whatever second of its minute the
    // widget is looked at, the entry on screen reads what the Prayer screen would at that second.
    @Test
    fun everySecondOfAnEntrysMinuteReadsWhatTheEntryShows() {
        WidgetMinutes.timeline(now, 60).forEach { entry ->
            val shown = WidgetCountdown.remainingMinutesAt(snapshot, entry.evaluateAtEpochSeconds)
            val shownLabel = label(entry.evaluateAtEpochSeconds)
            for (s in 1 until 60) {
                val at = entry.showAtEpochSeconds + s
                assertEquals(WidgetCountdown.remainingMinutesAt(snapshot, at), shown, "entry ${entry.showAtEpochSeconds} at +$s")
                assertEquals(label(at), shownLabel, "entry ${entry.showAtEpochSeconds} at +$s")
            }
        }
    }

    // The "X in 0:00 after the prayer" symptom: the entry on screen when Asr begins already counts
    // to Maghrib, and the last minute before it is the only one reading "Asr in 0:00".
    @Test
    fun noEntryStillCountsToAPrayerThatHasBegun() {
        val minutes = WidgetMinutes.timeline(now, 60)
        val atAsr = minutes.single { it.showAtEpochSeconds == asr }
        assertEquals("Maghrib in", label(atAsr.evaluateAtEpochSeconds))
        val asrInZero = minutes.filter {
            label(it.evaluateAtEpochSeconds) == "Asr in" && WidgetCountdown.remainingMinutesAt(snapshot, it.evaluateAtEpochSeconds) == 0L
        }
        assertEquals(listOf(asr - 60), asrInZero.map { it.showAtEpochSeconds })
        // And in general: the prayer an entry counts to is still ahead at the last second it shows.
        minutes.forEach { entry ->
            val countedTo = snapshot.schedule.filter { it.epochSeconds > entry.evaluateAtEpochSeconds }.minOf { it.epochSeconds }
            assertTrue(countedTo >= entry.showAtEpochSeconds + 60, "entry ${entry.showAtEpochSeconds}")
        }
    }

    // The Android widget draws when an alarm fires, and both its alarms land on a whole minute:
    // the rolling refresh on a five-minute boundary, the prayer alarm on the prayer's own minute.
    // Worked out on that instant, the draw would read one too high until the next draw.
    @Test
    fun aDrawOnTheMinuteIsWorkedOutForTheRestOfThatMinute() {
        assertEquals(179L, WidgetCountdown.remainingMinutesAt(snapshot, WidgetMinutes.evaluationInstant(asr)))
        assertEquals("Maghrib in", label(WidgetMinutes.evaluationInstant(asr)))
        assertEquals(WidgetMinutes.evaluationInstant(asr), WidgetMinutes.evaluationInstant(asr + 59))
    }

    @Test
    fun aClockBeforeNineteenSeventyFloorsToItsOwnMinute() {
        assertEquals(-60L, WidgetMinutes.startOfMinute(-1))
        assertEquals(0L, WidgetMinutes.startOfMinute(59))
    }
}
