package world.taqwa.app.feature.qibla

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.qibla.CompassAccuracyGate
import world.taqwa.app.qibla.CompassAccuracyState
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.CompassSource
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.qibla.HeadingFilter
import world.taqwa.app.qibla.QiblaMath

sealed interface QiblaUiState {
    data object NoSensor : QiblaUiState
    data class Searching(val headingDegrees: Double, val bearingDegrees: Double, val distanceKm: Double) : QiblaUiState
    data class Aligned(val headingDegrees: Double, val bearingDegrees: Double, val distanceKm: Double) : QiblaUiState

    /** The compass can probably be fixed: ask for the fix that suits [reason]. */
    data class LowAccuracy(
        val bearingDegrees: Double,
        val distanceKm: Double,
        val reason: CompassLowReason,
    ) : QiblaUiState

    /**
     * The compass has been untrustworthy long enough that asking again would be a lie. The
     * bearing and the distance are still exactly right — they are computed from the location, not
     * measured — so they are shown, under a caveat, with no needle.
     */
    data class BestEffort(
        val bearingDegrees: Double,
        val distanceKm: Double,
        val reason: CompassLowReason,
    ) : QiblaUiState
}

class QiblaViewModel(
    location: GeoLocation,
    private val compassSource: CompassSource,
    private val haptics: Haptics,
    private val filter: HeadingFilter = HeadingFilter(),
    private val gate: CompassAccuracyGate = CompassAccuracyGate(),
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

    /**
     * Collects headings until the calling coroutine is cancelled. No owned scope and no `launch`
     * of its own: the caller (`repeatOnLifecycle(STARTED) { … }` in `App.kt`) is what ties the
     * sensors to the screen actually being on screen, not merely composed — a plain
     * `LaunchedEffect` kept the magnetometer registered at `SENSOR_DELAY_GAME` behind the lock
     * screen, because Android stops an activity without destroying it and the composable stayed
     * composed. The same reasoning, and the same shape, as `TodayViewModel.tickWhileActive`.
     *
     * Both the filter and the gate are reset on every (re)entry: after a spell in the background
     * the last heading is stale, and a device that was mid-calibration should be judged on what
     * it does now, not on what it was doing when the screen went away.
     */
    suspend fun collectWhileActive() {
        if (!compassSource.hasSensor()) return
        filter.reset()
        gate.reset()
        wasAligned = false
        compassSource.readings.collect { reading ->
            val accuracy = gate.update(reading.timestampMillis, reading.isLowAccuracy, reading.lowReason)
            // Smoothed regardless: a filter fed only the samples we trust would jump when the
            // gate reopens, having missed everything in between.
            val smoothed = filter.update(reading.trueHeadingDegrees)
            _state.value = when (accuracy) {
                is CompassAccuracyState.BestEffort -> {
                    wasAligned = false
                    QiblaUiState.BestEffort(bearing, distance, accuracy.reason)
                }

                is CompassAccuracyState.Low -> {
                    wasAligned = false
                    QiblaUiState.LowAccuracy(bearing, distance, accuracy.reason)
                }

                CompassAccuracyState.Good ->
                    if (QiblaMath.isAligned(smoothed, bearing)) {
                        // One tick on entry, never once per frame while already aligned.
                        if (!wasAligned) haptics.tick()
                        wasAligned = true
                        QiblaUiState.Aligned(smoothed, bearing, distance)
                    } else {
                        wasAligned = false
                        QiblaUiState.Searching(smoothed, bearing, distance)
                    }
            }
        }
    }
}
