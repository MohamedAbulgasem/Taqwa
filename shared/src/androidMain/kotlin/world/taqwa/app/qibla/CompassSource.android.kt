package world.taqwa.app.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * [CompassAccuracyRules.androidRotationVectorIsDegenerate].
 *
 * If there is no magnetometer at all and no usable rotation vector, [hasSensor] reports false and
 * the screen falls back to the numeric bearing.
 */
class AndroidCompassSource : CompassSource {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile private var location: GeoLocation? = null

    // Declination varies over kilometres and months, never between two sensor events, so it is
    // computed once per location rather than rebuilding a GeomagneticField fifty times a second.
    @Volatile private var declinationFor: GeoLocation? = null
    @Volatile private var declination: Double = 0.0

    override fun hasSensor(): Boolean =
        rotationSensor != null || (accelerometer != null && magnetometer != null)

    override fun updateLocation(location: GeoLocation?) { this.location = location }

    private fun trueHeadingFrom(magneticHeading: Double): Double {
        val loc = location ?: return magneticHeading
        if (loc != declinationFor) {
            declination = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(), 0f, System.currentTimeMillis(),
            ).declination.toDouble()
            declinationFor = loc
        }
        return TrueNorth.correct(magneticHeading, declination)
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
        var rotationAccuracyLow = false
        var magnetometerAccuracyLow = false
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveGeomagnetic = false
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        fun emit(azimuthRadians: Float, lowAccuracy: Boolean) {
            val magneticHeading = (Math.toDegrees(azimuthRadians.toDouble()) + 360.0) % 360.0
            trySend(CompassReading(trueHeadingFrom(magneticHeading), lowAccuracy))
        }

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(changedSensor: Sensor?, accuracy: Int) {
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
                        fuseRawPair()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        haveGeomagnetic = true
                        fuseRawPair()
                    }
                }
            }

            private fun onRotationVector(event: SensorEvent) {
                val values = event.values
                // A stubbed HAL: no rotation encoded, so nothing to report. Dropping the sample
                // leaves the raw accelerometer+magnetometer pair driving the dial.
                val degenerate = CompassAccuracyRules.androidRotationVectorIsDegenerate(
                    values[0], values[1], values[2],
                )
                if (degenerate) return

                if (!rotationVectorTrusted) {
                    rotationVectorTrusted = true
                    // The fused sensor works, so the raw pair is redundant — and leaving a
                    // magnetometer streaming at SENSOR_DELAY_GAME for nothing costs power.
                    if (accel != null) sensorManager.unregisterListener(this, accel)
                    if (magnet != null) sensorManager.unregisterListener(this, magnet)
                }

                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                emit(orientation[0], rotationAccuracyLow)
            }

            private fun fuseRawPair() {
                if (rotationVectorTrusted || !haveGravity || !haveGeomagnetic) return
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
                SensorManager.getOrientation(rotationMatrix, orientation)
                // Only the magnetometer's accuracy matters here: the accelerometer is not what
                // goes stale, and it is the magnetometer the figure-of-eight prompt recalibrates.
                emit(orientation[0], magnetometerAccuracyLow)
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
}

actual fun createCompassSource(): CompassSource = AndroidCompassSource()
