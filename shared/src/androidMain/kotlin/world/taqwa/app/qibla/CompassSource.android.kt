package world.taqwa.app.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.settings.appContext

/**
 * `TYPE_ROTATION_VECTOR` gives orientation without the classic accelerometer+magnetometer jitter,
 * so it is preferred whenever it actually works; `GeomagneticField` then corrects the magnetic
 * heading to true north using whatever location was last supplied via [updateLocation].
 *
 * "Whenever it actually works" is load-bearing. `getDefaultSensor(TYPE_ROTATION_VECTOR)` returning
 * non-null only proves the device *declares* the sensor. Vendor HALs on some devices declare it
 * and then stream the identity quaternion `(0, 0, 0, 1)` forever, reporting
 * `SENSOR_STATUS_ACCURACY_HIGH` while doing it — every sample decodes to azimuth 0, so the dial
 * pins to north and never moves no matter how the phone is turned. This class therefore subscribes
 * to the raw accelerometer and magnetometer *alongside* the rotation vector and fuses those until
 * the rotation vector proves, by producing one sample that encodes a real rotation, that it is
 * worth listening to; only then does it drop the raw pair. See
 * [CompassAccuracyRules.androidRotationVectorIsDegenerate]. A rotation vector that has produced
 * nothing but degenerate samples for [DEGENERATE_GIVE_UP_MILLIS] is unregistered outright: it was
 * streaming two hundred useless samples a second for the life of the screen.
 *
 * Accuracy is read from `event.accuracy` on every sample, not only from `onAccuracyChanged`. That
 * callback is not a contract: one device sent exactly one magnetometer accuracy callback, at
 * registration, and nothing for the next ninety seconds — so a calibration prompt raised from it
 * could never clear, and no figure of eight could help, because nothing was listening for the
 * result. Another never sent a magnetometer callback at all, so the app showed a confident needle
 * on an accuracy it had never read. `onAccuracyChanged` is kept as a secondary input — it writes
 * the same last-known-accuracy the samples write, so it still counts on a device that only speaks
 * through the callback, but on a device that speaks through both, fifty samples a second overrule
 * a callback that fired once at registration.
 *
 * If there is no magnetometer at all and no usable rotation vector, [hasSensor] reports false and
 * the screen falls back to the numeric bearing.
 */
class AndroidCompassSource : CompassSource {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /**
     * `getOrientation` reads the matrix in the device's *natural* orientation, so on a phone held
     * sideways every heading is 90° out — and this screen deliberately supports a sideways phone.
     * The display is fetched from `DisplayManager` rather than `Context.getDisplay()` because the
     * only context here is the application context, which is not a visual context and throws.
     */
    private val display: Display? = (appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
        ?.getDisplay(Display.DEFAULT_DISPLAY)

    @Volatile private var location: GeoLocation? = null

    // Declination varies over kilometres and months, never between two sensor events, so it is
    // computed once per location rather than rebuilding a GeomagneticField fifty times a second.
    @Volatile private var declinationFor: GeoLocation? = null
    @Volatile private var declination: Double = 0.0

    override fun hasSensor(): Boolean =
        rotationSensor != null || (accelerometer != null && magnetometer != null)

    override fun updateLocation(location: GeoLocation?) { this.location = location }

    private fun declinationOrNull(): Double? {
        val loc = location ?: return null
        if (loc != declinationFor) {
            declination = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(), 0f, System.currentTimeMillis(),
            ).declination.toDouble()
            declinationFor = loc
        }
        return declination
    }

    override val readings: Flow<CompassReading> = callbackFlow {
        val rotation = rotationSensor
        val accel = accelerometer
        val magnet = magnetometer
        val rawPairAvailable = accel != null && magnet != null
        if (rotation == null && !rawPairAvailable) {
            close()
            return@callbackFlow
        }

        // Every callback below is delivered on the main looper (the three-argument
        // registerListener overload uses it), so this state needs no synchronisation.
        var rotationVectorTrusted = false
        var rotationVectorRegistered = rotation != null
        var degenerateSince = 0L
        var lastDegenerateNoticeAt = 0L
        // The latest accuracy known for each sensor, written by every sample of that sensor and
        // by onAccuracyChanged alike — an assignment, never an accumulation. A flag that could
        // only be set is what latched the calibration prompt on the device this was found on.
        var rotationAccuracyLow = false
        var magnetometerAccuracyLow = false
        var magnetometerRegistered = rawPairAvailable
        var fieldImplausible = false
        var haveField = false
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveGeomagnetic = false
        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)

        /**
         * Turns a rotation matrix into a heading. The remap is what makes a sideways phone
         * correct; without it `getOrientation` answers for the device's natural orientation.
         */
        fun azimuthDegrees(matrix: FloatArray): Double {
            val oriented = when (display?.rotation ?: Surface.ROTATION_0) {
                Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                    matrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapped,
                )
                Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                    matrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remapped,
                )
                Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                    matrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remapped,
                )
                else -> false
            }
            SensorManager.getOrientation(if (oriented) remapped else matrix, orientation)
            return (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
        }

        /**
         * Emits one sample. With no location there is no declination, so the heading is magnetic
         * north, not true north — up to 26° out where this was tested. It is emitted as a low
         * sample rather than presented as a direction to pray in.
         */
        fun emit(magneticHeading: Double, low: Boolean, reason: CompassLowReason?) {
            val declination = declinationOrNull()
            val heading = if (declination == null) magneticHeading else TrueNorth.correct(magneticHeading, declination)
            trySend(
                CompassReading(
                    trueHeadingDegrees = heading,
                    timestampMillis = SystemClock.elapsedRealtime(),
                    isLowAccuracy = low || declination == null,
                    lowReason = reason ?: if (declination == null) CompassLowReason.CALIBRATION else null,
                ),
            )
        }

        /** INTERFERENCE beats CALIBRATION: a field that cannot be the Earth's makes the accuracy
         * flag meaningless, and waving the phone about will not change it. */
        fun reasonFor(accuracyLow: Boolean, implausible: Boolean): CompassLowReason? = when {
            implausible -> CompassLowReason.INTERFERENCE
            accuracyLow -> CompassLowReason.CALIBRATION
            else -> null
        }

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(changedSensor: Sensor?, accuracy: Int) {
                // Secondary: this is the whole accuracy signal on a device whose samples carry a
                // stale one, and it is overwritten by the next sample on a device whose samples
                // are current. It is never the only thing that has ever been read.
                when (changedSensor?.type) {
                    Sensor.TYPE_ROTATION_VECTOR ->
                        rotationAccuracyLow = CompassAccuracyRules.androidAccuracyIsLow(accuracy)
                    Sensor.TYPE_MAGNETIC_FIELD ->
                        magnetometerAccuracyLow = CompassAccuracyRules.androidAccuracyIsLow(accuracy)
                }
            }

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> onRotationVector(event)
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        haveGravity = true
                        // Deliberately no fuse here: fusing on both branches emitted twice per
                        // fused reading — 100 Hz of StateFlow writes and recompositions on the
                        // device this was measured on, for a filter that needs nothing like it.
                        // The magnetometer is the slower and the decisive sensor of the pair.
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        haveGeomagnetic = true
                        val magnitude = CompassAccuracyRules.fieldMagnitude(
                            event.values[0], event.values[1], event.values[2],
                        )
                        fieldImplausible = CompassAccuracyRules.magneticFieldIsImplausible(magnitude)
                        haveField = true
                        magnetometerAccuracyLow = CompassAccuracyRules.androidAccuracyIsLow(event.accuracy)
                        fuseRawPair()
                    }
                }
            }

            private fun onRotationVector(event: SensorEvent) {
                val values = event.values
                // A stubbed HAL: no rotation encoded, so nothing to report.
                val degenerate = CompassAccuracyRules.androidRotationVectorIsDegenerate(
                    values[0], values[1], values[2],
                )
                if (degenerate) {
                    onDegenerateRotationVector()
                    return
                }
                degenerateSince = 0L

                if (!rotationVectorTrusted) {
                    rotationVectorTrusted = true
                    // The fused sensor works, so the raw pair is redundant — and leaving a
                    // magnetometer streaming at SENSOR_DELAY_GAME for nothing costs power.
                    if (accel != null) sensorManager.unregisterListener(this, accel)
                    if (magnet != null) sensorManager.unregisterListener(this, magnet)
                    magnetometerRegistered = false
                }

                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                // The field check applies only while a magnetometer sample is still arriving —
                // once the raw pair is dropped there is no field to check, and re-registering a
                // magnetometer to sanity-check a sensor that works would cost more than it saves.
                rotationAccuracyLow = CompassAccuracyRules.androidAccuracyIsLow(event.accuracy)
                val accuracyLow = rotationAccuracyLow
                val implausible = magnetometerRegistered && haveField && fieldImplausible
                emit(azimuthDegrees(rotationMatrix), accuracyLow || implausible, reasonFor(accuracyLow, implausible))
            }

            /**
             * The rotation vector exists and lies. If there is a raw pair to fall back to, stop
             * listening to it: it was delivering two hundred identity quaternions a second. If
             * there is not, there is no heading to be had on this device, so say so at a slow
             * pace rather than streaming silence — the gate turns that into the best-effort
             * screen, which shows the bearing and the distance and no needle.
             */
            private fun onDegenerateRotationVector() {
                if (rotationVectorTrusted) return
                val now = SystemClock.elapsedRealtime()
                if (degenerateSince == 0L) degenerateSince = now
                if (rawPairAvailable) {
                    if (rotationVectorRegistered && now - degenerateSince >= DEGENERATE_GIVE_UP_MILLIS) {
                        rotation?.let { sensorManager.unregisterListener(this, it) }
                        rotationVectorRegistered = false
                    }
                    return
                }
                if (now - lastDegenerateNoticeAt < DEGENERATE_NOTICE_INTERVAL_MILLIS) return
                lastDegenerateNoticeAt = now
                emit(0.0, low = true, reason = CompassLowReason.CALIBRATION)
            }

            private fun fuseRawPair() {
                if (rotationVectorTrusted || !haveGravity || !haveGeomagnetic) return
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
                // Only the magnetometer's accuracy matters here: the accelerometer is not what
                // goes stale, and it is the magnetometer the figure-of-eight prompt recalibrates.
                val accuracyLow = magnetometerAccuracyLow
                val implausible = fieldImplausible
                emit(azimuthDegrees(rotationMatrix), accuracyLow || implausible, reasonFor(accuracyLow, implausible))
            }
        }

        if (rotation != null) {
            sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        }
        if (rawPairAvailable) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_GAME)
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        /** Two seconds of nothing but identity quaternions is a HAL that is never going to work. */
        const val DEGENERATE_GIVE_UP_MILLIS = 2_000L

        /** How often to say "no heading here" when there is no raw pair to fall back to. */
        const val DEGENERATE_NOTICE_INTERVAL_MILLIS = 200L
    }
}

actual fun createCompassSource(): CompassSource = AndroidCompassSource()
