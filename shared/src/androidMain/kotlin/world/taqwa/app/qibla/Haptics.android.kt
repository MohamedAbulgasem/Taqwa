package world.taqwa.app.qibla

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import world.taqwa.app.settings.appContext

private class AndroidHaptics : Haptics {
    /** `as?`: an unchecked cast would be a NullPointerException at *construction* on a device
     * with no vibrator service, before [buzz]'s runCatching could help. */
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun tick() {
        buzz(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** `EFFECT_TICK` is the OEM's own lightest primitive and is tuned per device; below API 29
     * the shortest one-shot that is still felt stands in for it. */
    override fun count() {
        buzz(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
            },
        )
    }

    override fun partComplete() {
        buzz(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
    }

    override fun setComplete() {
        buzz(VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60, 70, 140), -1))
    }

    /**
     * Every pulse this class fires answers a finger, so from API 33 it is sent under
     * `USAGE_TOUCH`. Without an attribute the framework files a bare `vibrate` under the
     * notification usage, which on Samsung and others means these are scaled by the notification
     * slider — a reader who turned notification vibration down loses the count's tick, and one
     * who turned it up gets a hundred notification-strength buzzes in a row. Below 33
     * `createForUsage` does not exist and the calls stay as they were.
     *
     * A haptic is a confirmation, never the feature itself: if an OEM restricts the vibrator, or
     * the permission is somehow absent, alignment must still be reported visually rather than
     * taking the app down from inside the compass collector, which has no catch on its path.
     */
    private fun buzz(effect: VibrationEffect) {
        val vibrator = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        }
    }
}

actual fun createHaptics(): Haptics = AndroidHaptics()
