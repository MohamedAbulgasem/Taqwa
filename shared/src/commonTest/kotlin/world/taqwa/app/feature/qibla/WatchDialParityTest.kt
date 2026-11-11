package world.taqwa.app.feature.qibla

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.qibla.CompassAccuracyRules
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.CompassReading
import world.taqwa.app.qibla.CompassSource
import world.taqwa.app.qibla.DialState
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.qibla.IphoneCapeTownSession
import world.taqwa.app.qibla.QiblaMath
import world.taqwa.app.qibla.WatchQibla
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard that says when the phone's Qibla and the watch's part. Each sequence goes through the
 * phone's [QiblaViewModel], as Dev 1's B20, B40 and B41 left it, and through [WatchQibla] on the
 * phone's own bearing, both with their default filter and gate. After every reading the two show
 * the same kind of state with the same heading (the view model's LowAccuracy is the dial's Low),
 * the same reason when the compass is not trusted, and the view model has ticked once for every
 * buzz of the dial.
 */
class WatchDialParityTest {

    private val capeTown = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", "Cape Town", "ZA")
    private val bearing = QiblaMath.bearing(capeTown)

    private class FakeCompassSource : CompassSource {
        val readingsFlow = MutableSharedFlow<CompassReading>(replay = 0, extraBufferCapacity = 8)
        override val readings = readingsFlow
        override fun hasSensor() = true
        override fun updateLocation(location: GeoLocation?) = Unit
    }

    private class FakeHaptics : Haptics {
        var tickCount = 0
        override fun tick() { tickCount++ }
        override fun count() = Unit
        override fun partComplete() = Unit
        override fun setComplete() = Unit
    }

    /** What a dial shows, in terms both sides have: the kind, the heading, and the reason. */
    private data class Shown(val kind: String, val heading: Double? = null, val reason: CompassLowReason? = null)

    private fun QiblaUiState.shown(): Shown = when (this) {
        is QiblaUiState.NoSensor -> Shown("NoSensor")
        is QiblaUiState.Searching -> Shown("Searching", heading = headingDegrees)
        is QiblaUiState.Aligned -> Shown("Aligned", heading = headingDegrees)
        is QiblaUiState.LowAccuracy -> Shown("Low", reason = reason)
        is QiblaUiState.BestEffort -> Shown("BestEffort", reason = reason)
    }

    private fun DialState.shown(): Shown = when (this) {
        DialState.NoSensor -> Shown("NoSensor")
        is DialState.Searching -> Shown("Searching", heading = heading)
        is DialState.Aligned -> Shown("Aligned", heading = heading)
        is DialState.Low -> Shown("Low", reason = reason)
        is DialState.BestEffort -> Shown("BestEffort", reason = reason)
    }

    /** Feeds [readings] to both, one at a time, holds them equal after each, and answers what was
     * shown and how many times the dial buzzed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.replay(readings: List<CompassReading>): Pair<List<Shown>, Int> {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(capeTown, source, haptics)
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.collectWhileActive() }
        val dial = WatchQibla(bearing)
        var buzzes = 0
        val shown = readings.mapIndexed { i, reading ->
            source.readingsFlow.emit(reading)
            runCurrent()
            val step = dial.onReading(reading)
            if (step.buzz) buzzes++
            val phone = vm.state.value.shown()
            assertEquals(phone, step.state.shown(), "reading $i at ${reading.timestampMillis} ms")
            assertEquals(haptics.tickCount, buzzes, "ticks after reading $i at ${reading.timestampMillis} ms")
            phone
        }
        return shown to buzzes
    }

    @Test
    fun aRecordedIphoneSessionShowsTheSameOnBoth() = runTest {
        // Turned into readings as IphoneSessionReplayTest turns them, and as the iPhone's source does.
        val readings = IphoneCapeTownSession.samples.map { (timestamp, accuracy, heading) ->
            CompassReading(
                trueHeadingDegrees = heading,
                timestampMillis = timestamp,
                isLowAccuracy = CompassAccuracyRules.iosSampleIsLow(heading, accuracy),
                lowReason = CompassLowReason.CALIBRATION,
            )
        }
        val (shown, buzzes) = replay(readings)
        // Not a vacuous agreement: the session turns through the bearing and away from it again.
        val kinds = shown.map { it.kind }.toSet()
        assertTrue("Aligned" in kinds && "Searching" in kinds, "kinds $kinds")
        assertTrue(buzzes >= 1)
    }

    @Test
    fun aScriptedRunThroughEveryStateShowsTheSameOnBoth() = runTest {
        var at = 0L
        fun run(millis: Long, reading: (Long) -> CompassReading) =
            List((millis / 50).toInt()) { reading(at).also { at += 50 } }
        fun good(heading: Double) = { t: Long -> CompassReading(heading, t, isLowAccuracy = false) }
        fun low(reason: CompassLowReason) =
            { t: Long -> CompassReading(bearing, t, isLowAccuracy = true, lowReason = reason) }

        val readings = run(1_000, good(200.0)) + // searching
            run(2_000, good(bearing)) + // aligned, the first buzz
            run(2_000, good(300.0)) + // searching again
            run(2_000, good(bearing)) + // aligned again, the second
            run(1_200, low(CompassLowReason.CALIBRATION)) + // low after a second
            run(2_000, good(bearing)) + // trusted after 1.5 s, aligned, the third
            run(21_000, low(CompassLowReason.INTERFERENCE)) + // low, then best effort at 20 s
            run(3_000, good(bearing)) // good again after 1.5 s, the fourth
        val (shown, buzzes) = replay(readings)
        assertEquals(setOf("Searching", "Aligned", "Low", "BestEffort"), shown.map { it.kind }.toSet())
        assertTrue(Shown("Low", reason = CompassLowReason.CALIBRATION) in shown)
        assertTrue(Shown("BestEffort", reason = CompassLowReason.INTERFERENCE) in shown)
        assertEquals(4, buzzes)
    }

    @Test
    fun aHeadingThatIsNotANumberShowsTheSameOnBoth() = runTest {
        // One NaN from a sensor that misbehaves, between two seconds facing the bearing. Both
        // filters keep it, so both search at NaN from then on, and neither throws.
        val readings = List(41) { i ->
            CompassReading(if (i == 20) Double.NaN else bearing, i * 50L, isLowAccuracy = false)
        }
        val (shown, buzzes) = replay(readings)
        assertEquals("Aligned", shown[19].kind)
        assertEquals(Shown("Searching", heading = Double.NaN), shown.last())
        assertEquals(1, buzzes)
    }
}
