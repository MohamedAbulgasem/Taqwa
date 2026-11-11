package world.taqwa.app.wear.link

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.RuntimeExecutionException
import com.google.android.gms.wearable.WearableStatusCodes
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How the watch reads Play services' answers (plan 2 Task 6): its own copy of the phone's rules
 * (Task 5, `:shared`'s `WearCodes.kt`), since `:wearApp` has no `:shared`. Each code is read in its
 * own code space by the library's own constants, never a literal; a task is awaited to its own error,
 * never to the wrapper an eager `getResult()` throws nor to a `CancellationException`; and the
 * await is cancellable, so the watch's 5 s and 10 s bounds cut a call that never answers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearCodesTest {

    @Test
    fun theWatchReadsEachCodeInItsOwnSpace() {
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
        for (code in listOf(
            CommonStatusCodes.CANCELED,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.ERROR,
            WearableStatusCodes.TARGET_NODE_NOT_CONNECTED,
        )) {
            assertFalse(noWearApi(code), "status $code")
        }

        // Why the spaces are never mixed: ConnectionResult's API_UNAVAILABLE (no Wear OS app) is the
        // same 16 as CommonStatusCodes' CANCELED, which is a failure.
        assertEquals(ConnectionResult.API_UNAVAILABLE, CommonStatusCodes.CANCELED)
        assertFalse(noWearApi(CommonStatusCodes.CANCELED))
    }

    /**
     * Ruling 54 item 1, on the watch as on the phone: the link's classes, as compiled, ask Play
     * services through its public reference alone (`GoogleApiAvailability.getInstance()
     * .isGooglePlayServicesAvailable(context)`, never `GoogleApiAvailabilityLight`), and none of the
     * calls that show a dialog or a notification, or ask the user to update Play services.
     */
    @Test
    fun playServicesIsAskedThroughItsPublicApiAndNothingCanShow() {
        val references = ClassReferences.inPackageOf(PlayPhoneLink::class.java)
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
            "startResolutionForResult",
        )
        assertEquals(emptyList(), references.filter { it.owner.startsWith("com/google/android/gms/") && it.name in shows })
    }

    @Test
    fun theWatchsAwaitReadsTheResultOnlyOnSuccess() = runTest {
        // outcome: cancelled is TaskCancelled, never a CancellationException; failed is its own error.
        val cancelled = assertFailsWith<TaskCancelled> {
            outcome(isCanceled = true, exception = null) { throw java.util.concurrent.CancellationException() }
        }
        assertFalse((cancelled as Throwable) is CancellationException, "never a CancellationException")
        val error = IllegalStateException("the call failed")
        assertSame(error, assertFailsWith<IllegalStateException> { outcome(isCanceled = false, exception = error) { throw RuntimeExecutionException(error) } })
        assertEquals(1, outcome(isCanceled = false, exception = null) { 1 })

        // And through the real await(), over tasks that answer as Play services' do.
        val apiError = ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
        val thrown = assertFailsWith<ApiException> { FakeTask.failed<Int>(apiError).await() }
        assertSame(apiError, thrown, "never a RuntimeExecutionException")
        assertTrue(noWearApi(thrown.statusCode))
        assertFailsWith<TaskCancelled> { FakeTask.cancelled<Int>().await() }
        assertEquals(42, FakeTask.succeeded(42).await())
    }

    @Test
    fun theWatchsAwaitIsCutByItsBound() = runTest {
        // The acknowledgement's bound, then the handshake's and the daily nudge's.
        assertEquals(5.seconds, FeedReceiver.ACK_BOUND)
        assertEquals(10.seconds, PhoneAsk.BOUND)
        for (bound in listOf(FeedReceiver.ACK_BOUND, PhoneAsk.BOUND)) {
            val task = FakeTask.never<Int>()
            val start = currentTime
            assertNull(withTimeoutOrNull(bound) { task.await() }, "$bound")
            assertEquals(start + bound.inWholeMilliseconds, currentTime)
            task.complete(1) // the kept listener reaches a cancelled continuation: nothing resumes or throws
            assertTrue(task.isComplete)
        }
    }
}
