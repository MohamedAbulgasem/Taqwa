package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.i18n.PlatformFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private class FakeKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String): String? = map[key]
}

private class FakePlatformFormat : PlatformFormat {
    override fun languageTag() = "en-US"
    override fun localizedDigits(number: Int) = number.toString()
    override fun clockTime(hour: Int, minute: Int) =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

class WidgetMirrorWriterTest {

    private val today = TodayState(
        rows = listOf(
            TimelineRow(Prayer.FAJR, Instant.fromEpochSeconds(0), PrayerStatus.PASSED),
            TimelineRow(Prayer.DHUHR, Instant.fromEpochSeconds(3600 * 7), PrayerStatus.CURRENT),
            TimelineRow(Prayer.ASR, Instant.fromEpochSeconds(3600 * 10), PrayerStatus.UPCOMING),
            TimelineRow(Prayer.MAGHRIB, Instant.fromEpochSeconds(3600 * 18), PrayerStatus.UPCOMING),
            TimelineRow(Prayer.ISHA, Instant.fromEpochSeconds(3600 * 20), PrayerStatus.UPCOMING),
        ),
        next = PrayerTime(Prayer.ASR, Instant.fromEpochSeconds(3600 * 10)),
        countdown = 90.minutes,
        ringProgress = 0.3f,
    )

    @Test
    fun writingThenReadingReproducesTheCurrentAndNextPrayer() {
        val store = FakeKeyValueStore()
        WidgetMirrorWriter.write(store, today, "UTC", FakePlatformFormat())
        val snapshot = WidgetMirrorWriter.read(store)!!
        assertEquals(Prayer.ASR, snapshot.nextPrayer)
        assertEquals(Prayer.DHUHR, snapshot.currentPrayer)
        assertEquals(90L, snapshot.countdownMinutes)
        assertEquals(0.3f, snapshot.ringProgress)
    }

    @Test
    fun readingBeforeAnyWriteReturnsNullRatherThanCrashing() {
        assertEquals(null, WidgetMirrorWriter.read(FakeKeyValueStore()))
    }
}
