package world.taqwa.app.wear.face

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the Taqwa face stands on this watch, for the face screen, Today's chip and the phone's row:
 * - [UNSUPPORTED]: no Watch Face Push here (Wear OS 3 to 5), or a Push service that answers an
 *   error. No row anywhere; the app, the Tile and the complications work as ever.
 * - [NOT_BUNDLED]: this build carries no face (a CI or a no-face release build).
 * - [NOT_INSTALLED]: the slot holds no Taqwa face: the wearer removed it.
 * - [INSTALLED]: the face is in the picker and not the watch's face, with the activation unspent.
 * - [ACTIVE]: it is the watch's face.
 * - [CHOOSE_IT_YOURSELF]: it is in the picker, not the watch's face, and the one activation (or the
 *   one refusal of its permission) is used: the wearer chooses it himself.
 * - [FAILED]: adding, updating or making it active failed for a passing reason; a tap tries again.
 */
internal enum class FaceState { UNSUPPORTED, NOT_BUNDLED, NOT_INSTALLED, INSTALLED, ACTIVE, CHOOSE_IT_YOURSELF, FAILED }

/**
 * How long after this process updated the face or made it active [FacePush.state] answers that
 * call's result rather than ask the system. Google: right after an update, isWatchFaceActive "can
 * lead to unexpected results. It can take a little time for the watch face swap to finish."
 */
internal val UPDATE_SETTLE: Duration = 30.seconds

/**
 * The Taqwa face through Watch Face Push (spec 3.1, plan 5 Task 5): the face the app bundles kept
 * current in Taqwa's one slot, and made the watch's face once, on the wearer's tap.
 *
 * The rules it keeps, Google's ("Watch Face Push: Wear OS app configuration"):
 * - **One call at a time.** Every public call holds one [Mutex] and reads the slot afresh inside it
 *   (a slot id is never kept). So when the app's start and MY_PACKAGE_REPLACED both run
 *   [ensureCurrent], the second finds the face current and updates nothing. [FacePushes.of] makes
 *   one per process.
 * - **No activity check right after a change.** [ensureCurrent] asks whether the face is active
 *   before an update, never after: "If updateWatchFace() completes successfully, you can assume
 *   that your app still has control of the active watch face". For [UPDATE_SETTLE] after this
 *   process's last update or activation, [state] answers that call's result.
 * - **One activation, and one refusal.** [FaceMemory] keeps either for good, across processes.
 *
 * It never removes the face, which would lose the wearer's style, colour and slots. [log] is given
 * one line a port call, `TaqwaFace <call> <version code or slot> <result>`, never the token.
 */
internal class FacePush(
    private val port: FacePushPort,
    private val bundled: suspend () -> BundledFace?,
    private val memory: FaceMemory,
    private val elapsed: () -> Duration,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    /** The last update's or activation's answer, and when it was given. */
    private var settled: Pair<Duration, FaceState>? = null

    /** Whether the one activation, or the one refusal of its permission, is used. */
    val spent: Boolean get() = memory.spent

    /** Where the face stands, from the system, or from this process's last change within [UPDATE_SETTLE]. */
    suspend fun state(): FaceState = mutex.withLock {
        recentChange()?.let { return it }
        val list = installedOrNull() ?: return FaceState.UNSUPPORTED
        if (bundled() == null) return FaceState.NOT_BUNDLED
        val ours = list.ours() ?: return FaceState.NOT_INSTALLED
        currentState(ours)
    }

    /**
     * Updates an older Taqwa face in its slot to the bundled one, after an app update: never adds
     * it and never makes it active. It answers ACTIVE when the face was active before the update,
     * and otherwise what [state] would.
     */
    suspend fun ensureCurrent(): FaceState = mutex.withLock {
        val list = installedOrNull() ?: return FaceState.UNSUPPORTED
        val face = bundled() ?: return FaceState.NOT_BUNDLED
        val ours = list.ours() ?: return FaceState.NOT_INSTALLED
        if (ours.versionCode >= face.versionCode) return recentChange() ?: currentState(ours)

        val wasActive = activeOrNull() ?: return FaceState.UNSUPPORTED
        if (!updated(ours, face)) return FaceState.FAILED
        memory.seen = FaceSeen(face.versionCode, wasActive)
        settle(if (wasActive) FaceState.ACTIVE else resting(active = false))
    }

    /**
     * The face screen's button: adds the face if the wearer removed it (FAILED when no slot is free),
     * updates it if it is older, then makes it the watch's face if [permissionGranted] and the one
     * activation is unspent. Otherwise, or when the system refuses, the answer is
     * CHOOSE_IT_YOURSELF and the activation is spent for good, so neither row asks again.
     */
    suspend fun addAndActivate(permissionGranted: Boolean): FaceState = mutex.withLock {
        val list = installedOrNull() ?: return FaceState.UNSUPPORTED
        val face = bundled() ?: return FaceState.NOT_BUNDLED
        val ours = list.ours()
        var changed = false
        when {
            ours == null -> {
                val free = guarded("remainingSlots", "-") { port.remainingSlots() } ?: return FaceState.FAILED
                if (free <= 0) return FaceState.FAILED
                val added = guarded("add", face.versionCode.toString()) { port.add(face.apk(), face.token) } ?: return FaceState.FAILED
                memory.seen = FaceSeen(added.versionCode, active = false)
            }
            ours.versionCode < face.versionCode -> {
                if (!updated(ours, face)) return FaceState.FAILED
                memory.seen = FaceSeen(face.versionCode, memory.seen?.active ?: false)
                changed = true
            }
        }

        if (!permissionGranted || memory.spent) {
            memory.spent = true
            return FaceState.CHOOSE_IT_YOURSELF.also { if (changed) settle(it) }
        }
        val slot = installedOrNull()?.ours()?.slotId ?: return FaceState.FAILED
        try {
            port.setActive(slot)
            log("TaqwaFace setActive $slot ok")
        } catch (e: CancellationException) {
            throw e
        } catch (e: FaceRefused) {
            log("TaqwaFace setActive $slot refused")
            memory.spent = true
            return FaceState.CHOOSE_IT_YOURSELF
        } catch (e: Exception) {
            // A passing failure (erratum p5: ERROR_UNKNOWN, a slot changed under the call): the
            // activation is kept for the next tap.
            log("TaqwaFace setActive $slot failed ${e.javaClass.simpleName}")
            return FaceState.FAILED
        } catch (e: LinkageError) {
            log("TaqwaFace setActive $slot unlinked ${e.javaClass.simpleName}")
            return FaceState.FAILED
        }
        memory.spent = true
        memory.seen = FaceSeen(face.versionCode, active = true)
        settle(FaceState.ACTIVE)
    }

    private fun List<InstalledFace>.ours(): InstalledFace? = firstOrNull { it.packageName == FACE_PACKAGE }

    /**
     * The state of [ours], installed and current. A version this process did not make (the system
     * updated it with the app) is a change just made: the system is not asked whether it is active
     * right after it (Google's warning, met on emulator-5558), the activity last seen holds, as
     * Google says an app keeps the active face through an update, for [UPDATE_SETTLE]. Otherwise
     * the system is asked, and its answer kept.
     */
    private suspend fun currentState(ours: InstalledFace): FaceState {
        recentChange()?.let { return it }
        val seen = memory.seen
        if (seen != null && seen.versionCode != ours.versionCode) {
            log("TaqwaFace changed ${seen.versionCode}->${ours.versionCode} ${if (seen.active) "active" else "inactive"} (not asked)")
            memory.seen = FaceSeen(ours.versionCode, seen.active)
            return settle(resting(seen.active))
        }
        val active = activeOrNull() ?: return FaceState.UNSUPPORTED
        memory.seen = FaceSeen(ours.versionCode, active)
        return resting(active)
    }

    private fun resting(active: Boolean): FaceState = when {
        active -> FaceState.ACTIVE
        memory.spent -> FaceState.CHOOSE_IT_YOURSELF
        else -> FaceState.INSTALLED
    }

    private fun recentChange(): FaceState? =
        settled?.takeIf { (at, _) -> elapsed() - at < UPDATE_SETTLE }?.second

    private fun settle(state: FaceState): FaceState {
        settled = elapsed() to state
        return state
    }

    private suspend fun updated(ours: InstalledFace, face: BundledFace): Boolean =
        guarded("update", "${ours.slotId} ${ours.versionCode}->${face.versionCode}") { port.update(ours.slotId, face.apk(), face.token) } != null

    /** The slot as the system lists it now, or null for no Watch Face Push or a service in error. */
    private suspend fun installedOrNull(): List<InstalledFace>? =
        guarded("installed", "-") { port.installed() }

    private suspend fun activeOrNull(): Boolean? = guarded("isActive", FACE_PACKAGE) { port.isActive(FACE_PACKAGE) }

    /**
     * [call]'s answer, or null when it throws anything but a cancellation: an exception of the
     * system's or its service's, or a LinkageError on a watch whose system lacks the library's
     * wear-sdk (erratum p5), which an Error would otherwise carry past every catch.
     */
    private suspend fun <T> guarded(name: String, what: String, call: suspend () -> T): T? = try {
        call().also { log("TaqwaFace $name $what ${describe(it)}") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("TaqwaFace $name $what failed ${e.javaClass.simpleName}")
        null
    } catch (e: LinkageError) {
        log("TaqwaFace $name $what unlinked ${e.javaClass.simpleName}")
        null
    }

    private fun describe(answer: Any?): String = when (answer) {
        null -> "none"
        is InstalledFace -> "${answer.slotId} ${answer.versionCode}"
        is List<*> -> answer.filterIsInstance<InstalledFace>().joinToString(",", "[", "]") { "${it.slotId} ${it.packageName} ${it.versionCode}" }
        else -> answer.toString()
    }
}

/**
 * What FaceUpdateReceiver and the app's start run after an app update (Google: a bundled face is
 * not updated with the app): [FacePush.ensureCurrent], then [publish] of the state it answered and
 * whether the activation is spent. It never asks [FacePush.state], which would ask whether the
 * face is active right after updating it.
 */
internal suspend fun refreshAfterUpdate(push: FacePush, publish: suspend (FaceState, Boolean) -> Unit): FaceState {
    val state = push.ensureCurrent()
    publish(state, push.spent)
    return state
}

/**
 * What the face screen runs when it opens: [FacePush.state] within [boundMs], [show]n as it
 * answers, FAILED when it does not answer in time; then [publish] of the state read and whether the
 * activation is spent, as Today's chip and the daily nudge publish theirs. The phone's row opens
 * the screen, so a face chosen in the watch's own picker takes the row away once its tap opens it,
 * not at the next publish. A state not read in time is shown but never published: it says nothing
 * of the face, and the capability stays as the last publish left it.
 */
internal suspend fun readAndPublish(push: FacePush, boundMs: Long, show: (FaceState) -> Unit, publish: suspend (FaceState, Boolean) -> Unit) {
    val state = withTimeoutOrNull(boundMs) { push.state() }
    show(state ?: FaceState.FAILED)
    if (state != null) publish(state, push.spent)
}
