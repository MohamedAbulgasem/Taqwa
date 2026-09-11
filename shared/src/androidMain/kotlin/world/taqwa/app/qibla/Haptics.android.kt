package world.taqwa.app.qibla

import android.content.Context
import android.os.Build
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

    /** `EFFECT_TICK` is the OEM's own lightest primitive and is tuned per device; below API 29
     * the shortest one-shot that is still felt stands in for it. */
    override fun count() {
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        }
    }

    override fun partComplete() {
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1)) }
    }

    override fun setComplete() {
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60, 70, 140), -1)) }
    }
}

actual fun createHaptics(): Haptics = AndroidHaptics()
