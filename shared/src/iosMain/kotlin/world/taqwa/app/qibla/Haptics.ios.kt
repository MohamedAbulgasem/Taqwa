package world.taqwa.app.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * One generator per style, kept as a field and prepared before each use: preparing is what warms
 * the Taptic engine, and a generator created per tap would miss its own first pulse.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosHaptics : Haptics {
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    override fun tick() {
        medium.prepare()
        medium.impactOccurred()
    }

    override fun count() {
        light.prepare()
        light.impactOccurred()
    }

    /** iOS has no waveform API, so the two pulses are two impacts and a delay between them. */
    override fun partComplete() {
        medium.prepare()
        medium.impactOccurred()
        afterMillis(70) {
            medium.prepare()
            medium.impactOccurred()
        }
    }

    /** Success is already the three-pulse pattern iOS readers know; the heavy impact after it is
     * what makes finishing a set unmistakably heavier than finishing a part. */
    override fun setComplete() {
        notification.prepare()
        notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        afterMillis(120) {
            heavy.prepare()
            heavy.impactOccurred()
        }
    }

    private fun afterMillis(millis: Long, block: () -> Unit) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, millis * 1_000_000L), dispatch_get_main_queue(), block)
    }
}

actual fun createHaptics(): Haptics = IosHaptics()
