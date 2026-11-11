package world.taqwa.app.wear.qibla

import android.hardware.SensorManager
import android.view.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.wear.qibla.RotationVerdict.DISTRUST
import world.taqwa.app.wear.qibla.RotationVerdict.DROP
import world.taqwa.app.wear.qibla.RotationVerdict.NOTICE
import world.taqwa.app.wear.qibla.RotationVerdict.PROBATION
import world.taqwa.app.wear.qibla.RotationVerdict.USE

/**
 * The wrist's compass (plan 3 Task 7): the phone's `AndroidCompassSource` decisions, restated as a
 * pure policy over timed samples, and the watch's own field check beside them (call 13). Time is
 * the sensors' monotonic clock in milliseconds, from 0 here: the policy keeps "not yet" apart from
 * a clock that reads 0.
 */
class WristCompassPolicyTest {

    /** A rotation vector that encodes a real rotation. */
    private fun WristCompassPolicy.real(at: Long) = onRotationVector(at, 0.1f, 0.2f, 0.3f)

    /** A stubbed HAL's identity quaternion: no rotation at all (`androidRotationVectorIsDegenerate`). */
    private fun WristCompassPolicy.lie(at: Long) = onRotationVector(at, 0f, 0f, 0f)

    /** A policy whose rotation vector has just earned trust, at 500 ms, after half a second of real samples from 0. */
    private fun trusted(rawPairAvailable: Boolean = true): WristCompassPolicy =
        WristCompassPolicy(rawPairAvailable).apply {
            for (t in 0L until 500L step 20) real(t)
            assertEquals(USE, real(500))
        }

    @Test
    fun aHealthyRotationVectorIsTrustedAfterHalfASecond() {
        val policy = WristCompassPolicy(rawPairAvailable = true)
        assertTrue(policy.rawPairActive, "the raw pair is the heading's source until the vector is trusted")

        for (t in 0L until 499L step 20) assertEquals(PROBATION, policy.real(t), "at $t ms")
        assertEquals(PROBATION, policy.real(499))
        assertTrue(policy.rawPairActive)

        assertEquals(USE, policy.real(500))
        assertFalse(policy.rawPairActive, "trusted: the raw pair is no longer the heading's source")
        assertEquals(USE, policy.real(520))
    }

    @Test
    fun oneGoodSampleAmongLiesNeverEarnsTrust() {
        val policy = WristCompassPolicy(rawPairAvailable = true)

        // A real sample every 400 ms among identity quaternions: each lie breaks the run the dwell
        // times, so no run ever lasts 500 ms; and each real sample breaks the run of lies, so the
        // vector is never given up on either.
        for (t in 0L..5_000L step 20) {
            val verdict = if (t % 400L == 0L) policy.real(t) else policy.lie(t)
            assertEquals(PROBATION, verdict, "at $t ms")
        }
        assertTrue(policy.rawPairActive)
    }

    @Test
    fun aDeadRotationVectorLeavesTheRawPairInCharge() {
        val policy = WristCompassPolicy(rawPairAvailable = true)

        for (t in 0L until 2_000L step 20) assertEquals(PROBATION, policy.lie(t), "at $t ms")
        assertEquals(DROP, policy.lie(2_000), "two seconds of lies: unregister the vector")
        assertTrue(policy.rawPairActive)

        // A sample still in flight after the drop changes nothing more.
        assertEquals(PROBATION, policy.lie(2_020))
        assertTrue(policy.rawPairActive)
    }

    @Test
    fun aTrustedVectorThatStartsLyingIsDistrustedAfterTwoSeconds() {
        val policy = trusted()

        for (t in 520L until 2_520L step 20) assertEquals(PROBATION, policy.lie(t), "at $t ms")
        assertFalse(policy.rawPairActive, "still trusted inside the two seconds")

        assertEquals(DISTRUST, policy.lie(2_520))
        assertTrue(policy.rawPairActive, "trust is lost: the raw pair is the source again")

        // Back on probation exactly as it started: two more seconds of lies drop it ...
        for (t in 2_540L until 4_520L step 20) assertEquals(PROBATION, policy.lie(t), "at $t ms")
        assertEquals(DROP, policy.lie(4_520))
    }

    @Test
    fun aDistrustedVectorEarnsTrustAgainByTheSameDwell() {
        val policy = trusted()
        for (t in 520L..2_520L step 20) policy.lie(t)
        assertTrue(policy.rawPairActive)

        for (t in 2_540L until 3_040L step 20) assertEquals(PROBATION, policy.real(t), "at $t ms")
        assertEquals(USE, policy.real(3_040))
        assertFalse(policy.rawPairActive)
    }

    @Test
    fun noRawPairAndALyingVectorSaysSoEveryTwoHundredMs() {
        val policy = WristCompassPolicy(rawPairAvailable = false)
        assertFalse(policy.rawPairActive, "there is no raw pair to be the source")

        val verdicts = (0L..3_000L step 20).associateWith { policy.lie(it) }

        // The first at once, then one every 200 ms, past the two seconds too: with no raw pair the
        // vector is never dropped, there being nothing else to hear.
        assertEquals((0L..3_000L step 200).toList(), verdicts.filterValues { it == NOTICE }.keys.toList())
        assertEquals(setOf(NOTICE, PROBATION), verdicts.values.toSet())
        assertFalse(policy.rawPairActive)
    }

    @Test
    fun aTrustedVectorWithNoRawPairIsDistrustedAndThenSaysSo() {
        val policy = trusted(rawPairAvailable = false)

        for (t in 520L until 2_520L step 20) assertEquals(PROBATION, policy.lie(t), "at $t ms")
        assertEquals(DISTRUST, policy.lie(2_520))
        assertFalse(policy.rawPairActive, "no raw pair to hand the heading to")
        assertEquals(NOTICE, policy.lie(2_540))
    }

    @Test
    fun aTrustedVectorStillHearsAnImplausibleField() {
        val policy = trusted()
        assertFalse(policy.rawPairActive)

        // The magnetometer stays registered beside the trusted vector, for this check alone.
        policy.onField(600, 600f, 0f, 0f)
        assertEquals(CompassLowReason.INTERFERENCE, policy.lowReason(600, ACCURACY_HIGH))

        policy.onField(700, 22f, 0f, -40f) // 45.7 µT, the Earth's.
        assertNull(policy.lowReason(700, ACCURACY_HIGH))
    }

    @Test
    fun aConfidentVectorInAStrongFieldIsInterference() {
        // B6: the S23 showed a confident needle in a 179 µT field, its rotation vector at HIGH.
        val policy = trusted()
        policy.onField(600, 0f, 179f, 0f)

        assertEquals(CompassLowReason.INTERFERENCE, policy.lowReason(620, ACCURACY_HIGH))
    }

    @Test
    fun interferenceWinsOverCalibration() {
        val policy = WristCompassPolicy(rawPairAvailable = true)

        policy.onField(0, 600f, 0f, 0f)
        assertEquals(CompassLowReason.INTERFERENCE, policy.lowReason(0, ACCURACY_LOW))

        policy.onField(20, 22f, 0f, -40f)
        assertEquals(CompassLowReason.CALIBRATION, policy.lowReason(20, ACCURACY_LOW))
        assertEquals(CompassLowReason.CALIBRATION, policy.lowReason(20, 0), "UNRELIABLE")
        assertEquals(CompassLowReason.CALIBRATION, policy.lowReason(20, -1), "NO_CONTACT")
        assertNull(policy.lowReason(20, 2), "MEDIUM is good enough, as on the phone")
    }

    @Test
    fun withNoFieldHeardOnlyTheAccuracyCounts() {
        val policy = WristCompassPolicy(rawPairAvailable = false)

        assertNull(policy.lowReason(0, ACCURACY_HIGH))
        assertEquals(CompassLowReason.CALIBRATION, policy.lowReason(0, ACCURACY_LOW))
    }

    @Test
    fun aFieldOlderThanASecondIsNotHeld() {
        val policy = WristCompassPolicy(rawPairAvailable = true)
        policy.onField(0, 600f, 0f, 0f)

        assertEquals(CompassLowReason.INTERFERENCE, policy.lowReason(1_000, ACCURACY_HIGH), "a second old is still heard")
        assertNull(policy.lowReason(1_001, ACCURACY_HIGH))
    }

    @Test
    fun theFieldIsKeptForTheLog() {
        val policy = WristCompassPolicy(rawPairAvailable = true)
        assertNull(policy.fieldMicroTesla, "no field heard yet")

        policy.onField(0, 600f, 0f, 0f)
        assertEquals(600.0, policy.fieldMicroTesla)

        policy.onField(20, 22f, 0f, -40f)
        assertEquals(45.65, assertNotNull(policy.fieldMicroTesla), 0.01)
    }

    @Test
    fun aDisplayTurnedHalfAroundTurnsTheHeadingBack() {
        // A watch on the right wrist turns its display 180°: the phone's remap for ROTATION_180,
        // which the shell hands to SensorManager.remapCoordinateSystem.
        assertEquals(SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y, remapFor(Surface.ROTATION_180))

        // And the phone's other two, with none at the natural rotation.
        assertNull(remapFor(Surface.ROTATION_0))
        assertEquals(SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X, remapFor(Surface.ROTATION_90))
        assertEquals(SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X, remapFor(Surface.ROTATION_270))
        assertNotEquals(remapFor(Surface.ROTATION_90), remapFor(Surface.ROTATION_270))
    }

    private companion object {
        /** `SensorManager.SENSOR_STATUS_ACCURACY_HIGH` and `_LOW`. */
        const val ACCURACY_HIGH = 3
        const val ACCURACY_LOW = 1
    }
}
