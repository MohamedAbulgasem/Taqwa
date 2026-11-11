package world.taqwa.app.wear.link

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * How the watch reads Play services' answers (plan 2 Task 6): the watch's copy of the phone's rules
 * (Task 5, `:shared`'s `WearCodes.kt`), since `:wearApp` never links `:shared`. Each answer is read
 * in its own code space, by the library's own constants, and never mixed:
 * `ConnectionResult.API_UNAVAILABLE` and `CommonStatusCodes.CANCELED` are both 16, and only the
 * first is "no phone".
 */

/**
 * Whether [availability], `GoogleApiAvailability.isGooglePlayServicesAvailable`'s answer (a
 * `ConnectionResult` code), means there is no usable Play services on the watch: anything but
 * `SUCCESS`. Then no Wearable client is ever asked, and nothing is shown.
 */
internal fun noPlayServices(availability: Int): Boolean = availability != ConnectionResult.SUCCESS

/**
 * Whether [statusCode], an `ApiException.statusCode` from a Wearable call (a `CommonStatusCodes`
 * code, which `WearableStatusCodes` extends), means there is no Wear API to ask: `API_NOT_CONNECTED`
 * (17), "an API that failed to connect". Every other status is a failure, logged and tried again at
 * the next chance.
 */
internal fun noWearApi(statusCode: Int): Boolean = statusCode == CommonStatusCodes.API_NOT_CONNECTED

/**
 * A Play services task that was cancelled: an ordinary failure, never a `CancellationException`,
 * which would read as the caller's own cancellation.
 */
internal class TaskCancelled : Exception("the Play services task was cancelled")

/**
 * A task's outcome, read in this order: cancelled is [TaskCancelled], failed is its own
 * [exception], and only otherwise [result]. The result is read lazily because Play services'
 * `getResult()` throws for a task that did not succeed: `RuntimeExecutionException` around the real
 * error of a failed one, which would hide the `ApiException` 17 from [noWearApi], and
 * `java.util.concurrent.CancellationException` for a cancelled one.
 */
internal fun <T> outcome(isCanceled: Boolean, exception: Exception?, result: () -> T): T {
    if (isCanceled) throw TaskCancelled()
    if (exception != null) throw exception
    return result()
}

/** Runs a task's listener on the thread that completes it: no main looper, no executor of ours. */
private val directly = Executor { it.run() }

/**
 * Awaits this task to its result, its own exception or [TaskCancelled] ([outcome]), without the
 * `kotlinx-coroutines-play-services` dependency (plan 2, Global Constraints, "The library").
 *
 * Cancellable, so the watch's bounds cut a `getCapability` or `sendMessage` that never answers
 * (Play services updating, say): the acknowledgement's 5 s, the handshake's and the daily nudge's
 * 10 s. A Play services task has no way to remove a listener: a completion after the cut reaches a
 * cancelled continuation, which ignores it, so nothing resumes and nothing throws.
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(directly) { task ->
        continuation.resumeWith(runCatching { outcome(task.isCanceled, task.exception) { task.result } })
    }
}
