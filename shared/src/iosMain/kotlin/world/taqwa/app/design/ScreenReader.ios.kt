package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification

@Composable
internal actual fun platformScreenReaderRunning(): Boolean = rememberVoiceOverRunning().value

/** True while VoiceOver is on, kept current by the system's own status notification. */
@Composable
internal fun rememberVoiceOverRunning(): State<Boolean> {
    val running = remember { mutableStateOf(UIAccessibilityIsVoiceOverRunning()) }
    DisposableEffect(Unit) {
        val centre = NSNotificationCenter.defaultCenter
        val observer = centre.addObserverForName(
            name = UIAccessibilityVoiceOverStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? -> running.value = UIAccessibilityIsVoiceOverRunning() }
        // Read again once the observer is in place: a toggle between the first read and this
        // effect would otherwise go unseen until the next one.
        running.value = UIAccessibilityIsVoiceOverRunning()
        onDispose { centre.removeObserver(observer) }
    }
    return running
}
