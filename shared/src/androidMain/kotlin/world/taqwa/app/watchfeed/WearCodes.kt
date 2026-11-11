package world.taqwa.app.watchfeed

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableStatusCodes
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor

/*
 * How the Android sender reads Play services' answers (plan 2 Task 5, Global Constraints,
 * "Transport, Android"). Each answer is read in its own code space, by the library's own
 * constants, and never mixed: `ConnectionResult.API_UNAVAILABLE` and `CommonStatusCodes.CANCELED`
 * are both 16, and only the first is "no watch".
 */

/**
 * Whether [availability], `GoogleApiAvailability.isGooglePlayServicesAvailable`'s answer (a
 * `ConnectionResult` code), means there is no usable Play services: anything but `SUCCESS`
 * (missing, updating, disabled, too old, invalid). Then no Wearable client is ever made.
 */
internal fun noPlayServices(availability: Int): Boolean = availability != ConnectionResult.SUCCESS

/**
 * Whether [statusCode], an `ApiException.statusCode` from a Wearable call (a `CommonStatusCodes`
 * code, which `WearableStatusCodes` extends), means there is no Wear API on this phone:
 * `API_NOT_CONNECTED` (17), "an API that failed to connect", as on a phone without the Wear OS app.
 * Every other status is a failure. The `ConnectionResult` inside a 17 is logged, never compared.
 */
internal fun noWearApi(statusCode: Int): Boolean = statusCode == CommonStatusCodes.API_NOT_CONNECTED

/**
 * Whether [statusCode], `sendMessage`'s `ApiException.statusCode`, means the node went away
 * between the capability query and the send: `WearableStatusCodes.TARGET_NODE_NOT_CONNECTED` (4000).
 */
internal fun nodeGone(statusCode: Int): Boolean = statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED

/**
 * A Play services task that was cancelled: an ordinary failure, never a `CancellationException`,
 * which would read as the sync's own cancellation and could end its worker.
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
 * Cancellable, so a budget around it cuts a `getCapability` or `sendMessage` that never answers
 * (Play services updating, say), and the sync's worker serves the next ask. A Play services task
 * has no way to remove a listener: a completion after the cut reaches a cancelled continuation,
 * which ignores it, so nothing resumes and nothing throws.
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(directly) { task ->
        continuation.resumeWith(runCatching { outcome(task.isCanceled, task.exception) { task.result } })
    }
}
