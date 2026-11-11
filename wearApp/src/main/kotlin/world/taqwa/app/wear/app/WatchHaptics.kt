package world.taqwa.app.wear.app

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** What the wrist feels: the tasbeeh counter's four moments and the Qibla's alignment (Tasks 6 and 7). */
internal interface Feel {
    fun count()
    fun partComplete()
    fun setComplete()
    fun aligned()
}

/**
 * The phone's buzzes (`Haptics.android.kt` in `:shared`, which `HapticPatternsTest` reads), pure so
 * that the JVM tests reach them, and the waveforms' lengths: the windows in which Task 6 sends no
 * tick, because a new vibration cancels the one still playing (call 14).
 */
internal object HapticPatterns {
    /** A part's end: two short pulses. */
    val PART = longArrayOf(0, 40, 60, 40)

    /** The set's end: two pulses and a long one. */
    val SET = longArrayOf(0, 60, 70, 60, 70, 140)

    const val PART_MS = 140L
    const val SET_MS = 400L

    /** The Qibla's alignment: the phone's `tick`, one shot. */
    const val ALIGNED_MS = 30L
}

/**
 * [Feel] on the watch's vibrator, with the phone's four effects (its count, part, set and tick).
 * Each is sent under `USAGE_TOUCH` from API 33, so that the notification slider never scales a
 * count's tick, and inside `runCatching`: a haptic is a confirmation, never the feature, and a
 * restricted vibrator must not take a screen down.
 */
internal class WatchHaptics(context: Context) : Feel {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") // VibratorManager is API 31; the watch's minSdk is 30.
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /**
     * `EFFECT_TICK`, the OEM's own lightest primitive, tuned per device. The phone falls back to a
     * 20 ms one-shot below API 29; the watch's minSdk is 30, so it never needs to.
     */
    override fun count() {
        buzz(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    override fun partComplete() {
        buzz(VibrationEffect.createWaveform(HapticPatterns.PART, -1))
    }

    override fun setComplete() {
        buzz(VibrationEffect.createWaveform(HapticPatterns.SET, -1))
    }

    override fun aligned() {
        buzz(VibrationEffect.createOneShot(HapticPatterns.ALIGNED_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

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
