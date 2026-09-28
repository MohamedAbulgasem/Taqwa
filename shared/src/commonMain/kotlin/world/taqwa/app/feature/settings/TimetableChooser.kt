package world.taqwa.app.feature.settings

import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A timetable choice on its way to being stored (spec §2.2, ruling R52): [choose] checks it
 * against Automatic over the next 12 months, then [apply]s it confirmed when it is never earlier,
 * or holds it as [warning] for "Follow it" ([follow]) or the button that keeps what is followed
 * now ([keep]). A choice [needsCheck] turns down is applied at once, unconfirmed: Automatic for a
 * timetable, and for a high-latitude rule anything but a confirmed Other method's.
 *
 * The check runs in [checkScope], the screen's own, so leaving the screen stops it. A new choice
 * cancels the one being checked, and only the latest choice's check may ever apply or warn: a
 * check that is cancelled, or replaced, never stores anything, even one that finishes regardless.
 * The screen remembers a chooser per location and settings; when they change (a settings write
 * during a check), the old chooser is forgotten ([RememberObserver]) and its check [cancel]led, so
 * a check made under the old settings can never apply (review M11).
 *
 * One thread: [choose], [follow], [keep] and [cancel] are called on the main thread, and
 * [checkScope] runs there. The check reports its progress from wherever it computes (a background
 * dispatcher); each report is posted to [checkScope] before it touches [checking], so the Compose
 * state, [generation] and [job] are only ever read and written on that one thread.
 */
@Stable
internal class TimetableChooser(
    private val checkScope: CoroutineScope,
    private val check: suspend (id: String, onProgress: (Float) -> Unit) -> TimetableCheck,
    private val nameKeyOf: (String) -> String,
    private val apply: (id: String, confirmed: Boolean) -> Unit,
    private val needsCheck: (id: String) -> Boolean = { it != AUTOMATIC_TIMETABLE },
) : RememberObserver {
    var checking by mutableStateOf<ChoiceInProgress?>(null)
        private set
    var warning by mutableStateOf<PendingWarning?>(null)
        private set
    private var job: Job? = null

    /** The latest choice's number; a check whose number is no longer this one is stale. */
    private var generation = 0

    /** The number of the check still running, 0 when none: progress lands only while it runs. */
    private var running = 0

    fun choose(id: String) {
        job?.cancel()
        warning = null
        checking = null
        val mine = ++generation
        if (!needsCheck(id)) {
            apply(id, false)
            return
        }
        job = checkScope.launch {
            running = mine
            checking = ChoiceInProgress(id, 0f)
            val result = try {
                check(id) { progress ->
                    // Posted to the screen's thread: never a state write from the check's own.
                    checkScope.launch { if (running == mine && generation == mine) checking = ChoiceInProgress(id, progress) }
                }
            } finally {
                if (running == mine) running = 0
                if (generation == mine) checking = null
            }
            // A newer choice, or a cancelled one, has the last word; this check's result is stale.
            if (generation != mine || !isActive) return@launch
            when (result) {
                TimetableCheck.NeverEarlier -> apply(id, true)
                is TimetableCheck.Earlier -> warning = PendingWarning(id, nameKeyOf(id), result.finding)
                TimetableCheck.NotHere -> Unit
            }
        }
    }

    fun follow() {
        val pending = warning ?: return
        warning = null
        apply(pending.id, true)
    }

    fun keep() {
        warning = null
    }

    /** Stops the check in progress, if any: it never applies or warns, and nothing is pending. */
    fun cancel() {
        job?.cancel()
        job = null
        generation++
        running = 0
        checking = null
        warning = null
    }

    override fun onRemembered() = Unit

    /** The screen's settings or location changed, or it left: the old choice's check must not land. */
    override fun onForgotten() = cancel()

    override fun onAbandoned() = cancel()
}
