package world.taqwa.app.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
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
 * streaming two hundred useless samples a second for the life of the screen. That trust is
 * revocable in the other direction too — a rotation vector that worked and then went back to
 * identity quaternions for the same two seconds is distrusted again and the raw pair registered
 * again, so a HAL that gives up mid-session does not leave the needle pinned to north with the
 * fallback already switched off.
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
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val display: Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

    /**
     * `Display.getRotation()` is a binder call into the window manager. Reading it inside
     * `azimuthDegrees` meant one per sensor sample — up to two hundred a second, to learn a value
     * that changes when the user turns the phone over, which is to say a handful of times a
     * session. It is read once per registration and then only when the display says it changed.
     * Written on the main looper by the listener below and read there by every sensor callback,
     * but volatile anyway: nothing here should depend on that staying true.
     */
    @Volatile private var displayRotation: Int = display?.rotation ?: Surface.ROTATION_0

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
        // Start of the current unbroken run of non-degenerate samples; 0L when no such run is
        // in progress. Reset on every degenerate sample so a HAL that alternates between real
        // and identity samples never accumulates a run long enough to (re-)earn trust.
        var healthySince = 0L
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
            val oriented = when (displayRotation) {
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
                    val now = SystemClock.elapsedRealtime()
                    if (healthySince == 0L) healthySince = now
                    // A single real sample used to flip trust immediately, which is exactly what
                    // let an unstable HAL emitting one real sample every couple of seconds cycle
                    // trust/distrust and re-register/unregister the raw pair on every sample.
                    // Trust now needs RETRUST_DWELL_MILLIS of unbroken non-degenerate samples
                    // first; the raw pair keeps emitting while this dwell runs, so nothing is
                    // lost by waiting, including on the very first trust of a healthy device.
                    if (now - healthySince < RETRUST_DWELL_MILLIS) return
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
                // Any degenerate sample breaks the non-degenerate run the dwell above is timing.
                healthySince = 0L
                val now = SystemClock.elapsedRealtime()
                if (degenerateSince == 0L) degenerateSince = now
                if (rotationVectorTrusted) {
                    // Trust was one-way until this: a sensor that produced one real sample kept
                    // the dial for the life of the screen, however many identity quaternions it
                    // streamed afterwards, and the raw pair it had unregistered was not coming
                    // back. A HAL that stops mid-session (it happens after a suspend, and after
                    // the sensor service restarts) therefore pinned the needle to north with no
                    // way out. The mirror of the promotion above: after the same two seconds of
                    // nothing but identity quaternions, the rotation vector is distrusted, the
                    // raw pair is registered again, and the sensor goes back on probation in
                    // exactly the state it started in — one real sample re-earns it, and another
                    // two seconds of lies unregisters it below.
                    if (now - degenerateSince < DEGENERATE_GIVE_UP_MILLIS) return
                    rotationVectorTrusted = false
                    degenerateSince = now
                    if (rawPairAvailable && !magnetometerRegistered) {
                        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
                        sensorManager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_GAME)
                        magnetometerRegistered = true
                        haveField = false
                        fieldImplausible = false
                    }
                    return
                }
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

        // The rotation as it is right now, then one callback per actual change. Registered on
        // the main looper because that is where the sensor callbacks that read it arrive.
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    displayRotation = display?.rotation ?: Surface.ROTATION_0
                }
            }
        }
        displayRotation = display?.rotation ?: Surface.ROTATION_0
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        if (rotation != null) {
            sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        }
        if (rawPairAvailable) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_GAME)
        }
        awaitClose {
            sensorManager.unregisterListener(listener)
            displayManager?.unregisterDisplayListener(displayListener)
        }
    }

    private companion object {
        /** Two seconds of nothing but identity quaternions is a HAL that is never going to work. */
        const val DEGENERATE_GIVE_UP_MILLIS = 2_000L

        /** How long a run of non-degenerate samples must last before the rotation vector is
         * (re-)trusted, so one flaky-good sample amid mostly-identity output cannot flap trust. */
        const val RETRUST_DWELL_MILLIS = 500L

        /** How often to say "no heading here" when there is no raw pair to fall back to. */
        const val DEGENERATE_NOTICE_INTERVAL_MILLIS = 200L
    }
}

actual fun createCompassSource(): CompassSource = AndroidCompassSource()
