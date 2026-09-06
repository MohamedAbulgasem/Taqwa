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
    )

    @Test
    fun aSnapshotRoundTripsThroughSerialization() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot))
        assertEquals(snapshot, restored)
    }

    @Test
    fun aNullCurrentPrayerRoundTripsAsNullRatherThanAStrayValue() {
        val restored = WidgetInputsMirror.deserialize(WidgetInputsMirror.serialize(snapshot.copy(currentPrayer = null)))
        assertEquals(null, restored?.currentPrayer)
    }

    @Test
    fun malformedInputDeserializesToNullRatherThanCrashing() {
        assertNull(WidgetInputsMirror.deserialize("not a valid snapshot"))
        assertNull(WidgetInputsMirror.deserialize(""))
        assertNull(WidgetInputsMirror.deserialize("TOO|FEW|FIELDS"))
    }
}
