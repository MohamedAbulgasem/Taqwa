package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mirror is only written while the app is open. Before the schedule existed, the moment a
 * prayer passed the widget lost its countdown until the next launch, and after Isha it sat on
 * "Isha · 19:50" all night. Every case here renders the same snapshot at a later moment than it
 * was written and expects the widget to have moved on by itself.
 */
class WidgetScheduleTest {

    private val hour = 3_600L
    private val midnight = 1_757_116_800L // an arbitrary local midnight, in epoch seconds

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
        nextPrayerEpochSeconds = midnight + 15 * hour,
        previousPrayerEpochSeconds = midnight + 12 * hour,
        schedule = day(0) + day(1),
    )

    @Test
    fun theScheduleRoundTripsThroughTheWireFormat() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot))
        assertEquals(snapshot, restored)
        assertEquals(10, restored!!.schedule.size)
    }

    @Test
    fun aMirrorFromBeforeTheScheduleExistedStillReads() {
        val tenFields = WidgetInputsMirror.serialize(snapshot).split("|").take(10).joinToString("|")
        val restored = WidgetInputsMirror.deserialize(tenFields)!!
        assertTrue(restored.schedule.isEmpty())
        // And its render falls back to the write-time fields rather than inventing a next prayer.
        assertEquals("Asr in", WidgetContentBuilder.build(restored, midnight + 19 * hour).countdownLabel)
    }

    @Test
    fun betweenDhuhrAndAsrTheWidgetReadsAsWritten() {
        val at1400 = midnight + 14 * hour
        val content = WidgetContentBuilder.build(snapshot, at1400)
        assertEquals("Asr in", content.countdownLabel)
        assertEquals(60L, content.countdownMinutes)
        assertEquals(60L, WidgetCountdown.remainingMinutesAt(snapshot, at1400))
        assertEquals(Prayer.DHUHR, content.rows.single { it.isCurrent }.prayer)
        // Two of the three hours between Dhuhr and Asr have passed.
        assertEquals(0.667f, content.ringProgress, 0.01f)
    }

    // The headline case: the app was last opened at 13:00, and it is now 19:00. The old render
    // still said "Asr in" with no number; the widget has to have moved to Isha on its own.
    @Test
    fun afterAsrAndMaghribHavePassedTheWidgetCountsToIsha() {
        val at1900 = midnight + 19 * hour
        val content = WidgetContentBuilder.build(snapshot, at1900)
        assertEquals("Isha in", content.countdownLabel)
        assertEquals("20:00", content.nextClockTime)
        assertEquals(60L, WidgetCountdown.remainingMinutesAt(snapshot, at1900))
        assertEquals(Prayer.MAGHRIB, content.rows.single { it.isCurrent }.prayer)
        assertEquals(5, content.rows.size)
    }

    @Test
    fun afterIshaTheWidgetCountsToTomorrowsFajrWithTodaysRowsStillShowing() {
        val at2230 = midnight + 22 * hour + 30 * 60
        val content = WidgetContentBuilder.build(snapshot, at2230)
        assertEquals("Fajr in", content.countdownLabel)
        assertEquals(6 * hour / 60 + 30, WidgetCountdown.remainingMinutesAt(snapshot, at2230))
        assertEquals(Prayer.ISHA, content.rows.single { it.isCurrent }.prayer)
        assertTrue(content.rows.all { it.clockTime == day(0).first { d -> d.prayer == it.prayer }.clockTime })
    }

    @Test
    fun onTheSecondDayTheRowsAreTheSecondDays() {
        val day1At1300 = midnight + 24 * hour + 13 * hour
        val content = WidgetContentBuilder.build(snapshot, day1At1300)
        assertEquals("Asr in", content.countdownLabel)
        assertEquals(Prayer.DHUHR, content.rows.single { it.isCurrent }.prayer)
        assertEquals(120L, content.countdownMinutes)
    }

    @Test
    fun beforeTheFirstPrayerOfTheHorizonNothingIsCurrent() {
        val day0At0300 = midnight + 3 * hour
        val content = WidgetContentBuilder.build(snapshot, day0At0300)
        assertEquals("Fajr in", content.countdownLabel)
        assertFalse(content.rows.any { it.isCurrent })
        assertEquals(0f, content.ringProgress)
    }

    @Test
    fun pastTheWholeHorizonThereIsNoHonestCountdown() {
        val day3 = midnight + 3 * 24 * hour
        assertNull(WidgetCountdown.remainingMinutesAt(snapshot, day3))
    }

    @Test
    fun theLabelFollowsTheMirrorsLanguage() {
        val arabic = snapshot.copy(languageTag = "ar")
        val content = WidgetContentBuilder.build(arabic, midnight + 19 * hour)
        assertEquals("متبقٍ على العشاء", content.countdownLabel)
        assertEquals("العشاء", content.nextPrayerDisplayName)
    }
}
