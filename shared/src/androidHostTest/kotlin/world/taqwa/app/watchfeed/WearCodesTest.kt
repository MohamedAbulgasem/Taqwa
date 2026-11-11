package world.taqwa.app.watchfeed

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.RuntimeExecutionException
import com.google.android.gms.wearable.WearableStatusCodes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * How the Android sender reads each Play services answer (plan 2 Task 5, Global Constraints,
 * "Transport, Android"): every code in its own code space, by the library's own constants, never a
 * literal; and a [com.google.android.gms.tasks.Task] awaited to its own error, never to the
 * wrapper an eager `getResult()` throws, and never to a `CancellationException` that would end the
 * sync's worker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearCodesTest {

    @Test
    fun eachCallIsReadInItsOwnCodeSpace() {
        // GoogleApiAvailability.isGooglePlayServicesAvailable answers a ConnectionResult code.
        assertFalse(noPlayServices(ConnectionResult.SUCCESS))
        for (code in listOf(
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_DISABLED,
            ConnectionResult.SERVICE_INVALID,
            ConnectionResult.SERVICE_UPDATING,
        )) {
            assertTrue(noPlayServices(code), "ConnectionResult $code")
        }

        // ApiException.statusCode is a CommonStatusCodes code: 17 alone is no Wear API.
        assertTrue(noWearApi(CommonStatusCodes.API_NOT_CONNECTED))
        val failures = listOf(
            CommonStatusCodes.CANCELED,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.ERROR,
        )
        for (code in failures + WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
            assertFalse(noWearApi(code), "status $code")
        }

        // From sendMessage, WearableStatusCodes' 4000 alone is the node gone.
        assertTrue(nodeGone(WearableStatusCodes.TARGET_NODE_NOT_CONNECTED))
        for (code in failures + CommonStatusCodes.API_NOT_CONNECTED + WearableStatusCodes.INVALID_TARGET_NODE) {
            assertFalse(nodeGone(code), "status $code")
        }

        // Why the spaces are never mixed: ConnectionResult's API_UNAVAILABLE, the phone without the
        // Wear OS app, is the same 16 as CommonStatusCodes' CANCELED, which is a failure.
        assertEquals(ConnectionResult.API_UNAVAILABLE, CommonStatusCodes.CANCELED)
        assertFalse(noWearApi(CommonStatusCodes.CANCELED))
    }

    /**
     * Ruling 54 item 1: Play services is asked only through its public reference, and nothing that
     * could show anything is ever called. The watch side's classes, as compiled, ask
     * `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)`, never
     * `GoogleApiAvailabilityLight` (`@ShowFirstParty`, `@KeepForSdk`, outside the public
     * reference), and none of the calls that show a dialog or a notification, or ask the user to
     * update Play services.
     */
    @Test
    fun playServicesIsAskedThroughItsPublicApiAndNothingCanShow() {
        val references = ClassReferences.inPackageOf(PlayWatchNodes::class.java)
        val public = "com/google/android/gms/common/GoogleApiAvailability"
        assertTrue(MethodReference(public, "getInstance") in references, "GoogleApiAvailability.getInstance()")
        assertTrue(MethodReference(public, "isGooglePlayServicesAvailable") in references, "its isGooglePlayServicesAvailable(context)")

        val light = "com/google/android/gms/common/GoogleApiAvailabilityLight"
        assertEquals(emptyList(), references.filter { it.owner == light }, "nothing of GoogleApiAvailabilityLight")

        val shows = setOf(
            "getErrorDialog",
            "showErrorDialogFragment",
            "showErrorNotification",
            "makeGooglePlayServicesAvailable",
            "getErrorResolutionIntent",
            "getErrorResolutionPendingIntent",
            "setDefaultNotificationChannelId",
            "startResolutionForResult", // ConnectionResult's, Status's or ResolvableApiException's: a resolution screen
        )
        assertEquals(emptyList(), references.filter { it.owner.startsWith("com/google/android/gms/") && it.name in shows })
    }

    @Test
    fun aCancelledTaskIsAnErrorNotACancellation() {
        val cancelled = assertFailsWith<TaskCancelled> {
            outcome(isCanceled = true, exception = null) { throw java.util.concurrent.CancellationException() }
        }
        assertFalse((cancelled as Throwable) is CancellationException, "never a CancellationException")

        val error = IllegalStateException("the call failed")
        val thrown = assertFailsWith<IllegalStateException> {
            outcome(isCanceled = false, exception = error) { throw RuntimeExecutionException(error) }
        }
        assertSame(error, thrown, "the task's own error, not the getter's wrapper")

        assertEquals(1, outcome(isCanceled = false, exception = null) { 1 })
    }

    @Test
    fun aFailedTaskAwaitsToItsOwnException() = runTest {
        val error = ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
        val thrown = assertFailsWith<ApiException> { FakeTask.failed<Int>(error).await() }
        assertSame(error, thrown, "never a RuntimeExecutionException")
        assertTrue(noWearApi(thrown.statusCode))
    }

    @Test
    fun aCancelledTaskAwaitsToTaskCancelled() = runTest {
        assertFailsWith<TaskCancelled> { FakeTask.cancelled<Int>().await() }
    }

    @Test
    fun aSuccessfulTaskAwaitsToItsResult() = runTest {
        assertEquals(42, FakeTask.succeeded(42).await())
    }

    @Test
    fun aTaskThatNeverCompletesIsCutByTheBudget() = runTest {
        val task = FakeTask.never<Int>()
        val start = currentTime
        assertNull(withTimeoutOrNull(5.seconds) { task.await() })
        assertEquals(start + 5_000, currentTime)
        task.complete(1) // the kept listener reaches a cancelled continuation: nothing resumes or throws
        assertTrue(task.isComplete)
    }
}
