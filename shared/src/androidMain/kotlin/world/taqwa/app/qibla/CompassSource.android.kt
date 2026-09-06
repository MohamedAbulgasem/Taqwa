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
 * `TYPE_ROTATION_VECTOR` gives orientation without the classic accelerometer+magnetometer
 * jitter; `GeomagneticField` then corrects magnetic heading to true north using whatever
 * location was last supplied via [updateLocation]. Falls back to fusing the raw accelerometer
 * and magnetometer via `getRotationMatrix`/`getOrientation` when the fused rotation vector
 * sensor is absent; if there is no magnetometer at all, [hasSensor] reports false and the screen
 * falls back to the numeric bearing.
 */
class AndroidCompassSource : CompassSource {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile private var location: GeoLocation? = null

    override fun hasSensor(): Boolean =
        rotationSensor != null || (accelerometer != null && magnetometer != null)

    override fun updateLocation(location: GeoLocation?) { this.location = location }

    private fun trueHeadingFrom(magneticHeading: Double): Double {
        val loc = location ?: return magneticHeading
        val declination = GeomagneticField(
            loc.latitude.toFloat(), loc.longitude.toFloat(), 0f, System.currentTimeMillis(),
        ).declination.toDouble()
        return TrueNorth.correct(magneticHeading, declination)
    }

    override val readings: Flow<CompassReading> = callbackFlow {
        val rotation = rotationSensor
        if (rotation != null) {
            var lowAccuracy = false
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(changedSensor: Sensor?, accuracy: Int) {
                    lowAccuracy = CompassAccuracyRules.androidAccuracyIsLow(accuracy)
                }

                override fun onSensorChanged(event: SensorEvent) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val magneticHeading = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                    trySend(CompassReading(trueHeadingFrom(magneticHeading), lowAccuracy))
                }
            }
            sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sensorManager.unregisterListener(listener) }
        } else if (accelerometer != null && magnetometer != null) {
            var lowAccuracy = false
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var haveGravity = false
            var haveGeomagnetic = false

            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(changedSensor: Sensor?, accuracy: Int) {
                    if (changedSensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        lowAccuracy = CompassAccuracyRules.androidAccuracyIsLow(accuracy)
                    }
                }

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, gravity, 0, 3)
                            haveGravity = true
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                            haveGeomagnetic = true
                        }
                    }
                    if (!haveGravity || !haveGeomagnetic) return
                    val rotationMatrix = FloatArray(9)
                    val ok = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                    if (!ok) return
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val magneticHeading = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                    trySend(CompassReading(trueHeadingFrom(magneticHeading), lowAccuracy))
                }
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sensorManager.unregisterListener(listener) }
        } else {
            close()
        }
    }
}

actual fun createCompassSource(): CompassSource = AndroidCompassSource()
