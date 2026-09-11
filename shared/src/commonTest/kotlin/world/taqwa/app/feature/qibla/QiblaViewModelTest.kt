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
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun withNoSensorTheStateIsNoSensorAndNothingIsCollected() = runTest {
        val vm = QiblaViewModel(london, FakeCompassSource(sensorPresent = false), FakeHaptics())
        assertEquals(QiblaUiState.NoSensor, vm.state.first())
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
