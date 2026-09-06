package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetInputsMirrorTest {

    private val snapshot = WidgetSnapshot(
        nextPrayer = Prayer.ASR,
        countdownMinutes = 42,
        nextClockTime = "15:47",
        allClockTimes = mapOf(
            Prayer.FAJR to "05:12", Prayer.DHUHR to "12:34", Prayer.ASR to "15:47",
            Prayer.MAGHRIB to "18:20", Prayer.ISHA to "19:50",
        ),
        currentPrayer = Prayer.DHUHR,
        languageTag = "en-US",
        ringProgress = 0.42f,
        countdownLabel = "Asr in",
        nextPrayerEpochSeconds = 1_757_173_200L,
        previousPrayerEpochSeconds = 1_757_163_600L,
    )

    @Test
    fun aSnapshotRoundTripsThroughSerialization() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot))
        assertEquals(snapshot, restored)
    }

    @Test
    fun countdownLabelRoundTripsThroughSerialization() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot))
        assertEquals("Asr in", restored?.countdownLabel)
    }

    @Test
    fun aPreCountdownLabelSnapshotDeserializesWithTheFallbackRatherThanThrowing() {
        // The exact seven-field shape `serialize` produced before `countdownLabel` existed.
        val oldFormat = "ASR|42|15:47|FAJR=05:12;DHUHR=12:34;ASR=15:47;MAGHRIB=18:20;ISHA=19:50|DHUHR|en-US|0.42"
        val restored = WidgetInputsMirror.deserialize(oldFormat)
        assertEquals(Prayer.ASR, restored?.nextPrayer)
        assertEquals("", restored?.countdownLabel)
    }

    // The eight-field shape, i.e. a mirror left behind by the build immediately before I8. It must
    // still render — but with the `0` sentinel, so `WidgetCountdown` knows it has no absolute
    // instant to count towards and the widget shows the prayer name without a stale number.
    @Test
    fun aPreEpochSnapshotDeserializesWithTheNotRecordedSentinelRatherThanThrowing() {
        val eightFields = "ASR|42|15:47|FAJR=05:12;DHUHR=12:34;ASR=15:47;MAGHRIB=18:20;ISHA=19:50|DHUHR|en-US|0.42|Asr in"
        val restored = WidgetInputsMirror.deserialize(eightFields)
        assertEquals(Prayer.ASR, restored?.nextPrayer)
        assertEquals("Asr in", restored?.countdownLabel)
        assertEquals(0L, restored?.nextPrayerEpochSeconds)
        assertEquals(0L, restored?.previousPrayerEpochSeconds)
        assertNull(WidgetCountdown.remainingMinutesAt(restored!!, 1_757_170_800L))
    }

    @Test
    fun theAbsolutePrayerInstantsRoundTripThroughSerialization() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot))
        assertEquals(1_757_173_200L, restored?.nextPrayerEpochSeconds)
        assertEquals(1_757_163_600L, restored?.previousPrayerEpochSeconds)
    }

    // A non-numeric epoch is corruption, not version skew: it degrades to the sentinel — no
    // countdown — rather than throwing NumberFormatException past the widget process.
    @Test
    fun aCorruptEpochFieldDegradesToTheSentinelRatherThanThrowing() {
        val corrupt = "ASR|42|15:47|ASR=15:47|DHUHR|en-US|0.42|Asr in|not-a-number|also-not"
        assertEquals(0L, WidgetInputsMirror.deserialize(corrupt)?.nextPrayerEpochSeconds)
    }

    @Test
    fun aNullCurrentPrayerRoundTripsAsNullRatherThanAStrayValue() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot.copy(currentPrayer = null)))
        assertEquals(null, restored?.currentPrayer)
    }

    // M3: `val (name, time) = pair.split("=")` threw IndexOutOfBoundsException on a pair with no
    // `=`, and IndexOutOfBoundsException is not among the caught exceptions — so a single corrupt
    // byte in the mirror crashed the widget process rather than falling back to the placeholder.
    // Both shapes below reach the parser; neither may throw.
    @Test
    fun aClockTimePairWithNoSeparatorDoesNotCrashTheParser() {
        val noSeparator = "ASR|42|15:47|FAJR;DHUHR=12:34|DHUHR|en-US|0.42|Asr in"
        val restored = WidgetInputsMirror.deserialize(noSeparator)
        assertEquals("", restored?.allClockTimes?.get(Prayer.FAJR))
        assertEquals("12:34", restored?.allClockTimes?.get(Prayer.DHUHR))
    }

    @Test
    fun aClockTimePairWhoseNameIsNotAPrayerYieldsNullRatherThanThrowing() {
        assertNull(WidgetInputsMirror.deserialize("ASR|42|15:47|GARBAGE|DHUHR|en-US|0.42|Asr in"))
    }

    @Test
    fun malformedInputDeserializesToNullRatherThanCrashing() {
        assertNull(WidgetInputsMirror.deserialize("not a valid snapshot"))
        assertNull(WidgetInputsMirror.deserialize(""))
        assertNull(WidgetInputsMirror.deserialize("TOO|FEW|FIELDS"))
    }
}
