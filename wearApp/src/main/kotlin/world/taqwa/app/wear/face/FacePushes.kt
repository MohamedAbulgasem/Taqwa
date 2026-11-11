package world.taqwa.app.wear.face

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * The process's one [FacePush], which every caller uses (the app's start, FaceUpdateReceiver, Today,
 * the face screen and the daily nudge), so that its one lock keeps two updates from running at once.
 * The bundled face is looked up once per process; it is copied out of the app and read once per
 * install of the app ([FaceStage]), and its file is touched only by an add or an update.
 */
internal object FacePushes {

    @Volatile
    private var push: FacePush? = null

    fun of(context: Context): FacePush = push ?: synchronized(this) {
        push ?: create(context.applicationContext).also { push = it }
    }

    private fun create(app: Context): FacePush {
        var bundled: BundledFace? = null
        var read = false
        return FacePush(
            port = AndroidFacePushPort(app),
            // Called under FacePush's lock, so read once without a lock of its own.
            bundled = {
                if (!read) {
                    bundled = BundledFace.load(app)
                    read = true
                }
                bundled
            },
            memory = FaceMemory(File(app.filesDir, MEMORY_FILE)),
            elapsed = { SystemClock.elapsedRealtime().milliseconds },
            log = { Log.i(TAG, it) },
        )
    }

    private const val MEMORY_FILE = "face-activation-spent"
    private const val TAG = "TaqwaFace"
}
