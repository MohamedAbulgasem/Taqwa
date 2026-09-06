package world.taqwa.app.qibla

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import world.taqwa.app.settings.appContext

private class AndroidHaptics : Haptics {
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // A haptic is a confirmation, never the feature itself: if an OEM restricts the vibrator, or
    // the permission is somehow absent, alignment must still be reported visually rather than
    // taking the app down from inside the compass collector, which has no catch on its path.
    override fun tick() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

actual fun createHaptics(): Haptics = AndroidHaptics()
