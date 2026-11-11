package world.taqwa.app.wear.qibla

import android.hardware.SensorManager
import android.view.Surface
import world.taqwa.app.qibla.CompassAccuracyRules
import world.taqwa.app.qibla.CompassLowReason

/** What the wrist's compass does with one rotation-vector sample ([WristCompassPolicy.onRotationVector]). */
internal enum class RotationVerdict {
    /** The vector is trusted: the heading comes from this sample. */
    USE,

    /** Not this sample: the vector is still earning trust, or is lying inside its two seconds. The raw pair, while it is active, gives the heading. */
    PROBATION,

    /** Trust is lost: register the raw pair again, which gives the heading from now on. */
    DISTRUST,

    /** Two seconds of lies with a raw pair present: unregister the rotation vector. */
    DROP,

    /** No raw pair, and the vector lies: emit one low CALIBRATION sample, so the dial says it cannot point. */
    NOTICE,
}

/**
 * The wrist's compass decisions, pure, so that the JVM tests reach them: the phone's
 * `AndroidCompassSource` logic with its constants, which [WristCompass] runs on the sensors.
 *
 * - **Trust.** A rotation vector is believed only after [RETRUST_DWELL_MILLIS] of unbroken real
 *   samples (a degenerate one, `CompassAccuracyRules.androidRotationVectorIsDegenerate`, breaks the
 *   run). Until then the accelerometer and the magnetometer give the heading ([rawPairActive]).
 * - **Losing it.** A trusted vector that lies for [DEGENERATE_GIVE_UP_MILLIS] is distrusted, and the
 *   raw pair takes over again; an untrusted one that lies that long beside a raw pair is dropped.
 *   With no raw pair there is nothing else to hear, so it is kept, and says so every
 *   [DEGENERATE_NOTICE_INTERVAL_MILLIS].
 * - **The field (call 13, the watch's departure from the phone).** The phone stops hearing its
 *   magnetometer once its rotation vector is trusted, so it can never say "Magnetic interference"
 *   beside a working vector (B6: the S23 showed a needle in a 179 µT field). The watch keeps hearing
 *   it ([onField]), for this check and for the log, never for the heading: a field heard within
 *   [FIELD_FRESH_MILLIS] that cannot be the Earth's makes every sample INTERFERENCE ([lowReason]).
 *
 * Time is the sensors' monotonic clock, in milliseconds. "Not yet" is null and never 0, so a clock
 * that reads 0, as a test's does, is a time like any other.
 */
internal class WristCompassPolicy(private val rawPairAvailable: Boolean) {

    private var trusted = false
    private var vectorRegistered = true
    private var healthySince: Long? = null
    private var degenerateSince: Long? = null
    private var lastNoticeAt: Long? = null
    private var fieldAt: Long? = null

    /** Whether the accelerometer and the magnetometer give the heading: until the vector is trusted, and again once it is not. */
    val rawPairActive: Boolean get() = rawPairAvailable && !trusted

    /** Whether the rotation vector gives the heading, for the log. */
    val vectorTrusted: Boolean get() = trusted

    /** The magnetometer's last |B| in µT ([CompassAccuracyRules.fieldMagnitude]), for the log; null before any. */
    var fieldMicroTesla: Double? = null
        private set

    fun onRotationVector(nowMillis: Long, x: Float, y: Float, z: Float): RotationVerdict {
        if (CompassAccuracyRules.androidRotationVectorIsDegenerate(x, y, z)) return onDegenerate(nowMillis)
        degenerateSince = null
        if (!trusted) {
            val since = healthySince ?: nowMillis.also { healthySince = it }
            if (nowMillis - since < RETRUST_DWELL_MILLIS) return RotationVerdict.PROBATION
            trusted = true
        }
        return RotationVerdict.USE
    }

    private fun onDegenerate(nowMillis: Long): RotationVerdict {
        healthySince = null
        val since = degenerateSince ?: nowMillis.also { degenerateSince = it }
        val lying = nowMillis - since >= DEGENERATE_GIVE_UP_MILLIS
        return when {
            trusted -> {
                if (!lying) return RotationVerdict.PROBATION
                trusted = false
                degenerateSince = nowMillis
                RotationVerdict.DISTRUST
            }
            rawPairAvailable -> {
                if (!vectorRegistered || !lying) return RotationVerdict.PROBATION
                vectorRegistered = false
                RotationVerdict.DROP
            }
            else -> {
                val last = lastNoticeAt
                if (last != null && nowMillis - last < DEGENERATE_NOTICE_INTERVAL_MILLIS) return RotationVerdict.PROBATION
                lastNoticeAt = nowMillis
                RotationVerdict.NOTICE
            }
        }
    }

    /** A magnetometer sample: its |B| and when it came, whatever gives the heading. */
    fun onField(nowMillis: Long, x: Float, y: Float, z: Float) {
        fieldMicroTesla = CompassAccuracyRules.fieldMagnitude(x, y, z)
        fieldAt = nowMillis
    }

    /**
     * Why a sample from either source, read at [accuracy] (its own `SensorEvent.accuracy`), is not
     * to be pointed with: INTERFERENCE while a field heard in the last [FIELD_FRESH_MILLIS] cannot
     * be the Earth's, whatever the source and its trust; otherwise CALIBRATION at LOW accuracy or
     * worse; otherwise null. INTERFERENCE wins: no figure of eight fixes a field that is not the
     * Earth's, and moving away does.
     */
    fun lowReason(nowMillis: Long, accuracy: Int): CompassLowReason? {
        val field = fieldMicroTesla
        val heardAt = fieldAt
        val implausible = field != null && heardAt != null && nowMillis - heardAt <= FIELD_FRESH_MILLIS &&
            CompassAccuracyRules.magneticFieldIsImplausible(field)
        return when {
            implausible -> CompassLowReason.INTERFERENCE
            CompassAccuracyRules.androidAccuracyIsLow(accuracy) -> CompassLowReason.CALIBRATION
            else -> null
        }
    }

    companion object {
        /** Two seconds of nothing but identity quaternions is a HAL that is never going to work (the phone's). */
        const val DEGENERATE_GIVE_UP_MILLIS = 2_000L

        /** How long a run of real samples must last before the vector is (re-)trusted (the phone's). */
        const val RETRUST_DWELL_MILLIS = 500L

        /** How often to say "no heading here" when there is no raw pair (the phone's). */
        const val DEGENERATE_NOTICE_INTERVAL_MILLIS = 200L

        /** How long a magnetometer sample is held for the field check. */
        const val FIELD_FRESH_MILLIS = 1_000L
    }
}

/**
 * The axes `SensorManager.remapCoordinateSystem` is given for the display's [rotation], or null at
 * its natural rotation: the phone's `azimuthDegrees`, exactly. A watch worn on the right wrist with
 * its display turned 180° is ROTATION_180, whose heading this turns back, so the dial points true
 * for the screen as it is worn.
 */
internal fun remapFor(rotation: Int): Pair<Int, Int>? = when (rotation) {
    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
    else -> null
}
