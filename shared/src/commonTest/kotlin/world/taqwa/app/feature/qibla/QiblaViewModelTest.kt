package world.taqwa.app.feature.qibla

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import world.taqwa.app.domain.GeoLocation
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
}

class QiblaViewModelTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val bearing = QiblaMath.bearing(london)

    @Test
    fun withNoSensorTheStateIsNoSensorAndNothingIsCollected() = runTest {
        val vm = QiblaViewModel(london, FakeCompassSource(sensorPresent = false), FakeHaptics())
        assertEquals(QiblaUiState.NoSensor, vm.state.first())
    }

    @Test
    fun aFarOffHeadingIsSearching() = runTest {
        val source = FakeCompassSource()
        val vm = QiblaViewModel(london, source, FakeHaptics())
        vm.start(backgroundScope)
        source.readingsFlow.emit(CompassReading(trueHeadingDegrees = 0.0, isLowAccuracy = false))
        assertTrue(vm.state.first { it is QiblaUiState.Searching } is QiblaUiState.Searching)
    }

    @Test
    fun aHeadingWithinFiveDegreesIsAlignedAndTicksExactlyOnce() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        vm.start(backgroundScope)
        source.readingsFlow.emit(CompassReading(bearing, isLowAccuracy = false))
        vm.state.first { it is QiblaUiState.Aligned }
        source.readingsFlow.emit(CompassReading(bearing, isLowAccuracy = false))
        vm.state.first { it is QiblaUiState.Aligned }
        assertEquals(1, haptics.tickCount)
    }

    @Test
    fun leavingAlignmentAndReturningTicksAgain() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        vm.start(backgroundScope)
        source.readingsFlow.emit(CompassReading(bearing, false))
        vm.state.first { it is QiblaUiState.Aligned }
        source.readingsFlow.emit(CompassReading((bearing + 90.0) % 360.0, false))
        vm.state.first { it is QiblaUiState.Searching }
        source.readingsFlow.emit(CompassReading(bearing, false))
        vm.state.first { it is QiblaUiState.Aligned }
        assertEquals(2, haptics.tickCount)
    }

    @Test
    fun lowAccuracyOverridesAlignmentDimsRatherThanPointsAndNeverTicks() = runTest {
        val source = FakeCompassSource()
        val haptics = FakeHaptics()
        val vm = QiblaViewModel(london, source, haptics, filter = HeadingFilter(smoothing = 1.0))
        vm.start(backgroundScope)
        source.readingsFlow.emit(CompassReading(bearing, isLowAccuracy = true))
        assertTrue(vm.state.first { it is QiblaUiState.LowAccuracy } is QiblaUiState.LowAccuracy)
        assertEquals(0, haptics.tickCount)
    }
}
