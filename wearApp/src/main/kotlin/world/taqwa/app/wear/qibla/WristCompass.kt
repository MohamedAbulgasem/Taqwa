package world.taqwa.app.wear.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.CompassReading
import world.taqwa.app.qibla.TrueNorth

/** One sample of the wrist's compass: the reading, true north already, and how far the screen tilts from level ([Tilt.degrees]; not a number when unknown). */
internal data class WristReading(val reading: CompassReading, val tiltDegrees: Double)

/**
 * The watch's own compass: the phone's `AndroidCompassSource` on the wrist, its decisions in
 * [WristCompassPolicy], with these differences:
 * - the [declination] is the feed's, the phone's figure for the place, so no sample is ever low for
 *   want of one, and the watch is never told where it is (Ruling 39, call 9);
 * - each sample carries the screen's tilt;
 * - the magnetometer stays registered beside a trusted rotation vector for as long as [readings] is
 *   collected (the Qibla page active, call 13). Its samples go to the field check alone, never to
 *   the heading, and every sample's low reason is [WristCompassPolicy.lowReason]: a rotation vector
 *   that says it is confident in a 179 µT field (B6) is INTERFERENCE, and the dial shows no needle.
 *   The accelerometer is heard only while the raw pair gives the heading;
 * - each sensor has a listener, and so a connection, of its own, so that letting one go never
 *   silences another (`tap`, below);
 * - it logs `TaqwaWearQibla` once a second in a debug build, and in a store build once the tag is
 *   enabled on the watch ([qiblaLogOn], [qiblaLogLine]), for plan 5's Galaxy pass on the candidate.
 *
 * What it keeps of the phone's: the accuracy is read from every sample (`onAccuracyChanged` only
 * feeds the log), and the heading is remapped for the display's rotation, read once and then on
 * each change, exactly as the phone's `azimuthDegrees` ([remapFor]): a watch on the right wrist,
 * its display turned 180°, reads its heading turned back.
 */
internal class WristCompass(context: Context, private val declination: Double) {

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val displayManager: DisplayManager? = context.getSystemService(DisplayManager::class.java)

    /** A rotation vector, or an accelerometer and a magnetometer: a heading can be had. */
    val hasSensor: Boolean = rotationSensor != null || (accelerometer != null && magnetometer != null)

    /**
     * The samples, true north, at `SENSOR_DELAY_GAME`, delivered on the main looper (the
     * three-argument `registerListener`), so the state below needs no lock. The sensors are
     * registered while this is collected and unregistered when the collection ends.
     */
    val readings: Flow<WristReading> = callbackFlow {
        val manager = sensorManager
        val rotation = rotationSensor
        val accel = accelerometer
        val magnet = magnetometer
        if (manager == null || !hasSensor) {
            close()
            return@callbackFlow
        }
        val policy = WristCompassPolicy(rawPairAvailable = accel != null && magnet != null)
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        var displayRotation = display?.rotation ?: Surface.ROTATION_0
        var accelRegistered = false
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveGeomagnetic = false
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        // Read once for each collection, so a setprop to DEBUG takes hold only at the next fresh open
        // of Qibla; a lower level (INFO) stops the line at once, since the system drops a line below
        // its tag's level as it is written.
        val log = QiblaLogLine(on = qiblaLogOn(QiblaLog.IN_DEBUG_BUILD, Log.isLoggable(QIBLA_TAG, Log.DEBUG)))

        /** The magnetic heading of [m], remapped for the display as the phone's `azimuthDegrees` is. */
        fun azimuthDegrees(m: FloatArray): Double {
            val axes = remapFor(displayRotation)
            val oriented = axes != null && SensorManager.remapCoordinateSystem(m, axes.first, axes.second, remapped)
            SensorManager.getOrientation(if (oriented) remapped else m, orientation)
            return (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
        }

        /** One sample from [m], read at its source's own [accuracy]. */
        fun emit(now: Long, m: FloatArray, accuracy: Int) {
            val magnetic = azimuthDegrees(m)
            val tilt = Tilt.degrees(m)
            val reason = policy.lowReason(now, accuracy)
            val reading = CompassReading(TrueNorth.correct(magnetic, declination), now, isLowAccuracy = reason != null, lowReason = reason)
            log.heard(magnetic, tilt)
            trySend(WristReading(reading, tilt))
        }

        /**
         * A listener for one sensor: its samples and its accuracy callbacks, the log line after
         * each sample. Each sensor has its own, and so its own connection to the sensor service
         * (SensorManager opens a queue for each listener). Unregistering a sensor turns off what its
         * connection asked for, and where the rotation vector is Android's own fusion (AOSP's, as on
         * the emulator), the fusion asks for its accelerometer under the vector's connection, and
         * emits on that accelerometer's samples. So an accelerometer dropped from a listener the
         * vector shared silenced the vector with it: on emulator-5558 the vector's last event came
         * the moment the trusted vector let the accelerometer go, and "Active sensors" listed the
         * magnetometer alone. The phone's source shares one listener the same way.
         */
        fun tap(onSample: (SensorEvent, Long) -> Unit) = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                when (sensor?.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> log.vectorAccuracy = accuracy
                    Sensor.TYPE_MAGNETIC_FIELD -> log.fieldAccuracy = accuracy
                }
            }

            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                onSample(event, now)
                log.everySecond(now, policy, displayRotation)
            }
        }

        val accelTap = tap { event, _ ->
            System.arraycopy(event.values, 0, gravity, 0, 3)
            haveGravity = true
        }

        /**
         * The accelerometer is registered while the raw pair gives the heading and only then.
         * Unregistered, its last value is forgotten, so a pair heard again after a distrust waits
         * for a fresh one rather than fusing a stale gravity.
         */
        fun hearTheAccelerometer(wanted: Boolean) {
            if (accel == null || wanted == accelRegistered) return
            if (wanted) {
                manager.registerListener(accelTap, accel, SensorManager.SENSOR_DELAY_GAME)
            } else {
                manager.unregisterListener(accelTap)
                haveGravity = false
            }
            accelRegistered = wanted
        }

        val magnetTap = tap { event, now ->
            val values = event.values
            log.fieldAccuracy = event.accuracy
            policy.onField(now, values[0], values[1], values[2])
            System.arraycopy(values, 0, geomagnetic, 0, 3)
            haveGeomagnetic = true
            // The magnetometer is the slower and the decisive sensor of the pair, so the pair is
            // fused on its samples alone, as on the phone.
            if (policy.rawPairActive && haveGravity && haveGeomagnetic &&
                SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)
            ) {
                emit(now, matrix, event.accuracy)
            }
        }

        lateinit var vectorTap: SensorEventListener
        vectorTap = tap { event, now ->
            val values = event.values
            log.vectorAccuracy = event.accuracy
            when (policy.onRotationVector(now, values[0], values[1], values[2])) {
                RotationVerdict.USE -> {
                    SensorManager.getRotationMatrixFromVector(matrix, values)
                    emit(now, matrix, event.accuracy)
                }
                RotationVerdict.DROP -> manager.unregisterListener(vectorTap)
                RotationVerdict.NOTICE -> trySend(
                    WristReading(
                        CompassReading(TrueNorth.correct(0.0, declination), now, isLowAccuracy = true, lowReason = CompassLowReason.CALIBRATION),
                        tiltDegrees = Double.NaN,
                    ),
                )
                // DISTRUST has the accelerometer heard again, below; PROBATION leaves the heading to
                // the raw pair while it is active.
                RotationVerdict.DISTRUST, RotationVerdict.PROBATION -> Unit
            }
            hearTheAccelerometer(policy.rawPairActive)
        }

        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) displayRotation = display?.rotation ?: Surface.ROTATION_0
            }
        }
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        rotation?.let { manager.registerListener(vectorTap, it, SensorManager.SENSOR_DELAY_GAME) }
        magnet?.let { manager.registerListener(magnetTap, it, SensorManager.SENSOR_DELAY_GAME) }
        hearTheAccelerometer(policy.rawPairActive)

        awaitClose {
            manager.unregisterListener(vectorTap)
            manager.unregisterListener(accelTap)
            manager.unregisterListener(magnetTap)
            displayManager?.unregisterDisplayListener(displayListener)
        }
    }
}

/**
 * The compass's line once a second while the compass is heard, when [on] ([qiblaLogOn]): the
 * rotation vector's and the magnetometer's accuracy, the field, the trust, the tilt and the
 * magnetic heading ([qiblaLogLine]). Off, it builds no line.
 */
private class QiblaLogLine(private val on: Boolean) {
    var vectorAccuracy: Int? = null
    var fieldAccuracy: Int? = null
    private var magnetic: Double? = null
    private var tilt = Double.NaN
    private var loggedAt: Long? = null

    fun heard(magnetic: Double, tilt: Double) {
        this.magnetic = magnetic
        this.tilt = tilt
    }

    fun everySecond(now: Long, policy: WristCompassPolicy, displayRotation: Int) {
        if (!on) return
        val last = loggedAt
        if (last != null && now - last < EVERY_MILLIS) return
        loggedAt = now
        Log.d(
            QIBLA_TAG,
            qiblaLogLine(
                vectorAccuracy,
                fieldAccuracy,
                policy.fieldMicroTesla,
                trusted = policy.vectorTrusted,
                rawPairActive = policy.rawPairActive,
                tiltDegrees = tilt,
                displayRotation = displayRotation,
                headingDegrees = magnetic,
            ),
        )
    }

    private companion object {
        const val EVERY_MILLIS = 1_000L
    }
}

/** The tag the compass logs under, in every build (plan 5 Task 12 Step 0). */
internal const val QIBLA_TAG = "TaqwaWearQibla"

/**
 * Whether the compass logs its line once a second: always in a debug build, and in any other only
 * while its tag is enabled on the watch, `adb -s <watch> shell setprop log.tag.TaqwaWearQibla DEBUG`
 * ([tagEnabled], `Log.isLoggable(QIBLA_TAG, Log.DEBUG)`), which `setprop log.tag.TaqwaWearQibla
 * INFO` or a reboot ends. So the Galaxy's pass reads the signed candidate's own compass (plan 5
 * Task 13), and a wearer's watch logs nothing of it. [WristCompass] reads it once each time its
 * readings start being collected, so only the setprop to DEBUG waits for a fresh open of Qibla. A
 * lower level stops the line at once, in every build, a debug build's too: the system drops a line
 * below its tag's level as it is written, whatever this says.
 */
internal fun qiblaLogOn(debugBuild: Boolean, tagEnabled: Boolean): Boolean = debugBuild || tagEnabled

/**
 * The compass's line (plan 5 Task 12 Step 0), for example
 * `rv 3, mag 2, field 46.1 µT, trusted, raw pair off, tilt 12°, rotation 0, heading 231°`: the
 * rotation vector's and the magnetometer's accuracy ([rvAccuracy], [magAccuracy]), the field's
 * strength, whether the rotation vector is [trusted], whether the raw pair gives the heading
 * ([rawPairActive]), how far the screen tilts from level, the display's rotation (a `Surface`
 * constant: 2 is the right wrist's half turn) and [headingDegrees], the magnetic heading as the
 * display's rotation remaps it, before the feed's declination is added. A value not yet heard is
 * `none`. Degrees are whole and every digit Western, whatever the watch's language, so the log
 * reads the same everywhere.
 *
 * It takes no Qibla bearing, no declination and no place, so the log says nothing of where the
 * wearer is (Ruling 39): a heading and a field strength describe the watch, not where it is.
 */
internal fun qiblaLogLine(
    rvAccuracy: Int?,
    magAccuracy: Int?,
    fieldMicroTesla: Double?,
    trusted: Boolean,
    rawPairActive: Boolean,
    tiltDegrees: Double,
    displayRotation: Int,
    headingDegrees: Double?,
): String {
    val field = fieldMicroTesla?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT, "%.1f µT", it) } ?: "none"
    val tilt = wholeDegrees(tiltDegrees)?.let { "$it°" } ?: "none"
    val heading = wholeDegrees(headingDegrees)?.let { "${Math.floorMod(it, 360)}°" } ?: "none"
    return "rv ${rvAccuracy ?: "none"}, mag ${magAccuracy ?: "none"}, field $field, " +
        "${if (trusted) "trusted" else "not trusted"}, raw pair ${if (rawPairActive) "on" else "off"}, " +
        "tilt $tilt, rotation $displayRotation, heading $heading"
}

/** [degrees] to the nearest whole degree, or null when it is not a number. */
private fun wholeDegrees(degrees: Double?): Int? = degrees?.takeIf { it.isFinite() }?.roundToInt()
