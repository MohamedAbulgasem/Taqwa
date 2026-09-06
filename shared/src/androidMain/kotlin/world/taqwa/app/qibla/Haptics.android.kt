package world.taqwa.app.qibla

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import world.taqwa.app.settings.appContext

private class AndroidHaptics : Haptics {
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    override fun tick() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

actual fun createHaptics(): Haptics = AndroidHaptics()
