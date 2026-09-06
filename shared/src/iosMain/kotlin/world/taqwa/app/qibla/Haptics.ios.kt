package world.taqwa.app.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@OptIn(ExperimentalForeignApi::class)
private class IosHaptics : Haptics {
    private val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)

    override fun tick() {
        generator.prepare()
        generator.impactOccurred()
    }
}

actual fun createHaptics(): Haptics = IosHaptics()
