package world.taqwa.app.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSThread
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * One generator per style, kept as a field and prepared before each use: preparing is what warms
 * the Taptic engine, and a generator created per tap would miss its own first pulse.
 *
 * Every generator call goes through [onMain]. `UIFeedbackGenerator` is UIKit, and UIKit is
 * main-thread-only: a tap handed to [count] from a background dispatcher is not an error UIKit
 * reports, it is a pulse that silently never fires — or a main-thread checker assertion in a
 * debug build. Only the delayed second pulses were on the main queue before, because
 * `dispatch_after` put them there by accident of how the delay was written.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosHaptics : Haptics {
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    override fun tick() = onMain {
        medium.prepare()
        medium.impactOccurred()
    }

    override fun count() = onMain {
        light.prepare()
        light.impactOccurred()
    }

    /** iOS has no waveform API, so the two pulses are two impacts and a delay between them. */
    override fun partComplete() {
        onMain {
            medium.prepare()
            medium.impactOccurred()
        }
        afterMillis(70) {
            medium.prepare()
            medium.impactOccurred()
        }
    }

    /** Success is already the three-pulse pattern iOS readers know; the heavy impact after it is
     * what makes finishing a set unmistakably heavier than finishing a part. */
    override fun setComplete() {
        onMain {
            notification.prepare()
            notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
        afterMillis(120) {
            heavy.prepare()
            heavy.impactOccurred()
        }
    }

    /**
     * Straight through when already on the main thread — a hop would cost this pulse the frame it
     * belongs to, and the count is felt against the tap that caused it.
     */
    private fun onMain(block: () -> Unit) {
        if (NSThread.isMainThread()) block() else dispatch_async(dispatch_get_main_queue(), block)
    }

    /** `dispatch_after` on the main queue, which is both the delay and the thread these need. */
    private fun afterMillis(millis: Long, block: () -> Unit) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, millis * 1_000_000L), dispatch_get_main_queue(), block)
    }
}

actual fun createHaptics(): Haptics = IosHaptics()
