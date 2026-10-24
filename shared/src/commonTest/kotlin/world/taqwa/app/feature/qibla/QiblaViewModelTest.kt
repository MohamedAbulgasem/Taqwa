package world.taqwa.app.feature.qibla

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.CompassReading
import world.taqwa.app.qibla.CompassSource
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.qibla.HeadingFilter
import world.taqwa.app.qibla.QiblaMath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeCompassSource(private val sensorPresent: Boolean = true) : CompassSource {
    val readingsFlow = MutableSharedFlow<CompassReading>(replay = 0, extraBufferCapacity = 8)
    override val readings = readingsFlow
    override fun hasSensor() = sensorPresent
    var lastLocation: GeoLocation? = null
    override fun updateLocation(location: GeoLocation?) { lastLocation = location }
}

private class FakeHaptics : Haptics {
    var tickCount = 0
    override fun tick() { tickCount++ }
    // The compass uses none of the tasbeeh's three; they are here only to satisfy the interface.
    override fun count() = Unit
    override fun partComplete() = Unit
    override fun setComplete() = Unit
}

class QiblaViewModelTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val bearing = QiblaMath.bearing(london)

    /**
     * UNDISPATCHED: the collector subscribes synchronously, before this returns, rather than on
     * the next dispatcher tick. A hot fake source cannot emit into a subscriber that has not
     * attached yet, and the real one registers its sensors the moment the screen appears.
     */
    private fun CoroutineScope.startCollecting(vm: QiblaViewModel) =
        launch(start = CoroutineStart.UNDISPATCHED) { vm.collectWhileActive() }

    /** A good reading at [at] ms on the platform's monotonic clock. */
    private fun good(heading: Double, at: Long) =
        CompassReading(heading, timestampMillis = at, isLowAccuracy = false)

    private fun low(at: Long, reason: CompassLowReason) =
        CompassReading(0.0, timestampMillis = at, isLowAccuracy = true, lowReason = reason)

    /** A good reading [offset] degrees clockwise of the qibla (negative: anticlockwise). */
    private fun goodAt(offset: Double, at: Long) = good((bearing + offset + 360.0) % 360.0, at)

    /**
     * The pointing state for the reading [offset] degrees off the qibla. The heading identifies
     * the reading, so this can neither pass on the initial `Searching(0.0)` or on the reading
     * before, nor hang waiting for a state type that has already come and gone. Needs a filter
     * that does not smooth, so the heading on screen is the one emitted.
     */
    private suspend fun QiblaViewModel.stateAt(offset: Double): QiblaUiState {
        val heading = (bearing + offset + 360.0) % 360.0
        return state.first {
            val shown = when (it) {
                is QiblaUiState.Aligned -> it.headingDegrees
                is QiblaUiState.Searching -> it.headingDegrees
                else -> return@first false
            }
            abs(QiblaMath.angleDelta(shown, heading)) < 1e-6
        }
    }

    @Test
    fun withNoSensorTheStateIsNoSensorAndNothingIsCollected() = runTest {
        val source = FakeCompassSource(sensorPresent = false)
        val vm = QiblaViewModel(london, source, FakeHaptics())
        // The screen tells the reader to use "the bearing below", so the state has to carry it:
        // both numbers are arithmetic on the location and are as right on a phone with no
        // magnetometer as on any other.
        val state = vm.state.first() as QiblaUiState.NoSensor
        assertEquals(bearing, state.bearingDegrees, absoluteTolerance = 1e-9)
        assertEquals(QiblaMath.distanceKm(london), state.distanceKm, absoluteTolerance = 1e-9)
        // Nothing is asked of a sensor that is not there.
        assertEquals(null, source.lastLocation)
    }

    @Test
    fun aFarOffHeadingIsSearching() = runTest {
        val source = FakeCompassSource()
        val vm = QiblaViewModel(london, source, FakeHaptics())
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(good(0.0, at = 0))
        assertTrue(vm.state.first { it is QiblaUiState.Searching } is QiblaUiState.Searching)
    }

    @Test
    fun aHeadingWithinFiveDegreesIsAlignedAndTicksExactlyOnce() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(good(bearing, at = 0))
        vm.state.first { it is QiblaUiState.Aligned }
        source.readingsFlow.emit(good(bearing, at = 20))
        vm.state.first { it is QiblaUiState.Aligned }
        assertEquals(1, haptics.tickCount)
    }

    @Test
    fun leavingAlignmentAndReturningTicksAgain() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(good(bearing, at = 0))
        vm.state.first { it is QiblaUiState.Aligned }
        source.readingsFlow.emit(good((bearing + 90.0) % 360.0, at = 20))
        vm.state.first { it is QiblaUiState.Searching }
        source.readingsFlow.emit(good(bearing, at = 40))
        vm.state.first { it is QiblaUiState.Aligned }
        assertEquals(2, haptics.tickCount)
    }

    // --- Hysteresis: aligned is entered within 5 degrees and left only beyond 7, so a phone
    // resting near the edge neither flickers the rim nor buzzes on every wobble of the sensor.

    @Test
    fun aHeadingRestingOnTheFiveDegreeEdgeTicksOnceAndStaysAligned() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        var at = 0L
        source.readingsFlow.emit(goodAt(4.0, at))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(4.0))
        repeat(10) {
            listOf(5.5, 4.5).forEach { offset ->
                at += 20
                source.readingsFlow.emit(goodAt(offset, at))
                assertIs<QiblaUiState.Aligned>(vm.stateAt(offset), "at $offset degrees, $at ms")
            }
        }
        at += 20
        source.readingsFlow.emit(goodAt(6.5, at))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(6.5))
        assertEquals(1, haptics.tickCount)
    }

    @Test
    fun anAlignedHeadingLeavesOnlyBeyondSevenDegreesAndReturnsOnlyInsideFive() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(goodAt(-4.0, at = 0))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(-4.0))
        assertEquals(1, haptics.tickCount)
        source.readingsFlow.emit(goodAt(-6.9, at = 20))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(-6.9))
        source.readingsFlow.emit(goodAt(-7.5, at = 40))
        assertIs<QiblaUiState.Searching>(vm.stateAt(-7.5))
        // Out again, so it takes 5 degrees to come back in, not 7.
        source.readingsFlow.emit(goodAt(-6.0, at = 60))
        assertIs<QiblaUiState.Searching>(vm.stateAt(-6.0))
        source.readingsFlow.emit(goodAt(-4.5, at = 80))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(-4.5))
        assertEquals(2, haptics.tickCount)
    }

    @Test
    fun aSpellOfLowAccuracyForgetsTheAlignment() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(goodAt(4.0, at = 0))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(4.0))
        source.readingsFlow.emit(goodAt(6.0, at = 20))
        assertIs<QiblaUiState.Aligned>(vm.stateAt(6.0))
        source.readingsFlow.emit(low(at = 40, reason = CompassLowReason.CALIBRATION))
        source.readingsFlow.emit(low(at = 1_040, reason = CompassLowReason.CALIBRATION))
        vm.state.first { it is QiblaUiState.LowAccuracy }
        // The gate needs 1 500 ms of good samples before it trusts the compass again; the first
        // of these two is still LowAccuracy, the second is the dial's first reading back.
        source.readingsFlow.emit(goodAt(6.0, at = 1_100))
        source.readingsFlow.emit(goodAt(6.0, at = 2_600))
        // 6 degrees would hold an aligned dial, but the dial came back from not knowing, so it
        // has to come within 5 again, and tick again when it does.
        assertIs<QiblaUiState.Searching>(vm.stateAt(6.0))
        assertEquals(1, haptics.tickCount)
    }

    // --- The display. A figure of eight is seconds without a touch, and best effort comes 20 s
    // in, past a 15 s screen timeout; a compass that points is glanced at.

    @Test
    fun onlyACompassBeingCalibratedOrGivenUpOnHoldsTheScreenOn() {
        val d = QiblaMath.distanceKm(london)
        assertFalse(QiblaUiState.NoSensor(bearing, d).keepsScreenOn)
        assertFalse(QiblaUiState.Searching(0.0, bearing, d).keepsScreenOn)
        assertFalse(QiblaUiState.Aligned(bearing, bearing, d).keepsScreenOn)
        CompassLowReason.entries.forEach { reason ->
            assertTrue(QiblaUiState.LowAccuracy(bearing, d, reason).keepsScreenOn, "low, $reason")
            assertTrue(QiblaUiState.BestEffort(bearing, d, reason).keepsScreenOn, "best effort, $reason")
        }
    }

    // --- The gate, as the screen sees it. One untrustworthy sample used to flip the whole screen;
    // at SENSOR_DELAY_GAME that is up to a hundred flips a second.

    @Test
    fun oneLowSampleDoesNotTakeTheNeedleAwayFromAnAlignedScreen() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(good(bearing, at = 0))
        vm.state.first { it is QiblaUiState.Aligned }
        source.readingsFlow.emit(low(at = 20, reason = CompassLowReason.CALIBRATION))
        source.readingsFlow.emit(good(bearing, at = 40))
        assertTrue(vm.state.first { it is QiblaUiState.Aligned } is QiblaUiState.Aligned)
    }

    @Test
    fun aSecondOfLowSamplesAsksForTheFigureOfEightAndNeverTicks() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(low(at = 0, reason = CompassLowReason.CALIBRATION))
        source.readingsFlow.emit(low(at = 1_000, reason = CompassLowReason.CALIBRATION))
        val state = vm.state.first { it is QiblaUiState.LowAccuracy } as QiblaUiState.LowAccuracy
        assertEquals(CompassLowReason.CALIBRATION, state.reason)
        assertEquals(bearing, state.bearingDegrees, absoluteTolerance = 1e-9)
        assertEquals(0, haptics.tickCount)
    }

    @Test
    fun anImplausibleFieldAsksForSomethingElseEntirely() = runTest {
        val source = FakeCompassSource()
        val vm = QiblaViewModel(london, source, FakeHaptics())
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(low(at = 0, reason = CompassLowReason.INTERFERENCE))
        source.readingsFlow.emit(low(at = 1_000, reason = CompassLowReason.INTERFERENCE))
        val state = vm.state.first { it is QiblaUiState.LowAccuracy } as QiblaUiState.LowAccuracy
        assertEquals(CompassLowReason.INTERFERENCE, state.reason)
    }

    @Test
    fun twentySecondsOfALowCompassFallsThroughToTheNumbersThatNeedNoSensor() = runTest {
        val source = FakeCompassSource()
        val vm = QiblaViewModel(london, source, FakeHaptics())
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(low(at = 0, reason = CompassLowReason.INTERFERENCE))
        source.readingsFlow.emit(low(at = 20_000, reason = CompassLowReason.INTERFERENCE))
        val state = vm.state.first { it is QiblaUiState.BestEffort } as QiblaUiState.BestEffort
        assertEquals(CompassLowReason.INTERFERENCE, state.reason)
        // The two things that are still exactly right, and the reason this state exists.
        assertEquals(bearing, state.bearingDegrees, absoluteTolerance = 1e-9)
        assertEquals(QiblaMath.distanceKm(london), state.distanceKm, absoluteTolerance = 1e-9)
    }

    @Test
    fun aCompassThatComesGoodAgainGetsItsNeedleBack() = runTest {
        val source = FakeCompassSource()
        val vm = QiblaViewModel(london, source, FakeHaptics(), filter = HeadingFilter(smoothing = 1.0))
        backgroundScope.startCollecting(vm)
        source.readingsFlow.emit(low(at = 0, reason = CompassLowReason.CALIBRATION))
        source.readingsFlow.emit(low(at = 20_000, reason = CompassLowReason.CALIBRATION))
        vm.state.first { it is QiblaUiState.BestEffort }
        source.readingsFlow.emit(good(bearing, at = 21_000))
        source.readingsFlow.emit(good(bearing, at = 22_600))
        assertTrue(vm.state.first { it is QiblaUiState.Aligned } is QiblaUiState.Aligned)
    }
}
