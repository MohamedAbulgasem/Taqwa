package world.taqwa.app.wear.face

import java.io.File
import kotlinx.coroutines.CompletableDeferred

/**
 * Watch Face Push as the tests play it: Taqwa's one slot, the faces in it, whether the system says
 * Taqwa's face is active, and a log of every call in the order made, so that a test can say what
 * was asked and in what order. A face it adds or updates to is [bundledVersion]'s, as the system
 * installs the APK it is given. Each call can be made to throw, and [installedGate] and
 * [updateGate] hold a listing and an update until a test releases them.
 */
internal class FakeFacePushPort(
    faces: List<InstalledFace> = emptyList(),
    var free: Int = 1,
    var active: Boolean = false,
    var supported: Boolean = true,
    private val bundledVersion: Long = 40,
) : FacePushPort {

    val faces: MutableList<InstalledFace> = faces.toMutableList()
    val calls: MutableList<String> = mutableListOf()

    var installedThrows: Throwable? = null
    var addThrows: Throwable? = null
    var updateThrows: Throwable? = null
    var setActiveThrows: Throwable? = null
    var installedGate: CompletableDeferred<Unit>? = null
    var updateGate: CompletableDeferred<Unit>? = null

    private var nextSlot = 1

    override suspend fun installed(): List<InstalledFace>? {
        calls += "installed"
        installedGate?.await()
        installedThrows?.let { throw it }
        return if (supported) faces.toList() else null
    }

    override suspend fun remainingSlots(): Int {
        calls += "remainingSlots"
        return free
    }

    override suspend fun add(apk: File, token: String): InstalledFace {
        calls += "add $token"
        addThrows?.let { throw it }
        check(free > 0) { "no slot" }
        val face = InstalledFace("new-${nextSlot++}", FACE_PACKAGE, bundledVersion)
        faces += face
        free--
        return face
    }

    override suspend fun update(slotId: String, apk: File, token: String): InstalledFace {
        calls += "update $slotId $token"
        updateGate?.await()
        updateThrows?.let { throw it }
        val at = faces.indexOfFirst { it.slotId == slotId }
        check(at >= 0) { "no slot $slotId" }
        val face = faces[at].copy(versionCode = bundledVersion)
        faces[at] = face
        return face
    }

    override suspend fun isActive(packageName: String): Boolean {
        calls += "isActive $packageName"
        return active && faces.any { it.packageName == packageName }
    }

    override suspend fun setActive(slotId: String) {
        calls += "setActive $slotId"
        setActiveThrows?.let { throw it }
        active = true
    }

    /** The calls made, by their names alone: `update`, never `update slot-a token-40`. */
    fun names(): List<String> = calls.map { it.substringBefore(' ') }
}
