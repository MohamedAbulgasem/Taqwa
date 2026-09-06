package world.taqwa.app.feature.qibla

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.qibla.CompassSource
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.qibla.HeadingFilter
import world.taqwa.app.qibla.QiblaMath

sealed interface QiblaUiState {
    data object NoSensor : QiblaUiState
    data class Searching(val headingDegrees: Double, val bearingDegrees: Double, val distanceKm: Double) : QiblaUiState
    data class Aligned(val headingDegrees: Double, val bearingDegrees: Double, val distanceKm: Double) : QiblaUiState
    data class LowAccuracy(val bearingDegrees: Double, val distanceKm: Double) : QiblaUiState
}

class QiblaViewModel(
    location: GeoLocation,
    private val compassSource: CompassSource,
    private val haptics: Haptics,
    private val filter: HeadingFilter = HeadingFilter(),
) {
    private val bearing = QiblaMath.bearing(location)
    private val distance = QiblaMath.distanceKm(location)

    private val _state = MutableStateFlow<QiblaUiState>(
        if (compassSource.hasSensor()) QiblaUiState.Searching(0.0, bearing, distance) else QiblaUiState.NoSensor,
    )
    val state: StateFlow<QiblaUiState> = _state.asStateFlow()

    private var wasAligned = false

    init {
        if (compassSource.hasSensor()) compassSource.updateLocation(location)
    }

    fun start(scope: CoroutineScope) {
        if (!compassSource.hasSensor()) return
        // UNDISPATCHED: the collector subscribes synchronously, before this call returns, rather
        // than on the next dispatcher tick. Sensor registration on Android/iOS starts the moment
        // the screen appears, and a hot fake source in tests can't emit into a subscriber that
        // hasn't attached yet — a not-yet-collecting Flow.first() would otherwise never see it.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            compassSource.readings.collect { reading ->
                val smoothed = filter.update(reading.trueHeadingDegrees)
                _state.value = when {
                    reading.isLowAccuracy -> {
                        wasAligned = false
                        QiblaUiState.LowAccuracy(bearing, distance)
                    }
                    QiblaMath.isAligned(smoothed, bearing) -> {
                        // One tick on entry, never once per frame while already aligned.
                        if (!wasAligned) haptics.tick()
                        wasAligned = true
                        QiblaUiState.Aligned(smoothed, bearing, distance)
                    }
                    else -> {
                        wasAligned = false
                        QiblaUiState.Searching(smoothed, bearing, distance)
                    }
                }
            }
        }
    }
}
