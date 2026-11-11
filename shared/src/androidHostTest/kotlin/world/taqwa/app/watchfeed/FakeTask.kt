package world.taqwa.app.watchfeed

import android.app.Activity
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.RuntimeExecutionException
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor

/**
 * A Play services [Task] for the host tests, which answers as the library's own does (plan 2 Task
 * 5): [getResult] throws `RuntimeExecutionException` around the error of a failed task and
 * `java.util.concurrent.CancellationException` for a cancelled one, so a test sees what an eager
 * `getResult()` would do to the sync. Only `addOnCompleteListener(Executor, …)`, the one call our
 * `await()` makes, is supported: a completed task calls its listener at once, and one made with
 * [never] keeps it until [complete], so a budget around it is cut on virtual time and nowhere else.
 */
internal class FakeTask<T>(
    private var canceled: Boolean,
    private var exception: Exception?,
    private var result: T?,
) : Task<T>() {

    private var complete = true
    private var kept: Pair<Executor, OnCompleteListener<T>>? = null

    /** Makes a task [never] made successful with [value], and calls the listener it kept. */
    fun complete(value: T) {
        check(!complete) { "already complete" }
        result = value
        complete = true
        kept?.let { (executor, listener) -> executor.execute { listener.onComplete(this) } }
    }

    override fun isComplete(): Boolean = complete

    override fun isCanceled(): Boolean = complete && canceled

    override fun isSuccessful(): Boolean = complete && !canceled && exception == null

    override fun getException(): Exception? = if (complete) exception else null

    override fun getResult(): T {
        check(complete) { "Task is not yet complete" }
        if (canceled) throw java.util.concurrent.CancellationException("Task is already canceled.")
        exception?.let { throw RuntimeExecutionException(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    override fun <X : Throwable?> getResult(type: Class<X>): T {
        val error = exception
        if (error != null && type.isInstance(error)) throw type.cast(error)!!
        return getResult()
    }

    override fun addOnCompleteListener(executor: Executor, listener: OnCompleteListener<T>): Task<T> {
        if (complete) executor.execute { listener.onComplete(this) } else kept = executor to listener
        return this
    }

    override fun addOnFailureListener(listener: OnFailureListener): Task<T> = unsupported()

    override fun addOnFailureListener(activity: Activity, listener: OnFailureListener): Task<T> = unsupported()

    override fun addOnFailureListener(executor: Executor, listener: OnFailureListener): Task<T> = unsupported()

    override fun addOnSuccessListener(listener: OnSuccessListener<in T>): Task<T> = unsupported()

    override fun addOnSuccessListener(activity: Activity, listener: OnSuccessListener<in T>): Task<T> = unsupported()

    override fun addOnSuccessListener(executor: Executor, listener: OnSuccessListener<in T>): Task<T> = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("only addOnCompleteListener(Executor, …) is faked")

    companion object {
        fun <T> succeeded(value: T): FakeTask<T> = FakeTask(canceled = false, exception = null, result = value)

        fun <T> failed(error: Exception): FakeTask<T> = FakeTask(canceled = false, exception = error, result = null)

        fun <T> cancelled(): FakeTask<T> = FakeTask(canceled = true, exception = null, result = null)

        /** A task that never completes until [complete]: `isComplete` false, its listener kept. */
        fun <T> never(): FakeTask<T> = FakeTask<T>(canceled = false, exception = null, result = null).apply { complete = false }
    }
}
