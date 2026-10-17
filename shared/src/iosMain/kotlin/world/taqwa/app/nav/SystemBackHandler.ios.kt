package world.taqwa.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification

/**
 * iOS has no back button, but it has a back *gesture* for VoiceOver users: the two-finger scrub,
 * which UIKit delivers as `accessibilityPerformEscape`. Compose Multiplatform 1.12 turns that into
 * an Escape key event and hands it to the innermost enabled [BackHandler]
 * (`ComposeSceneMediator.performEscape` → `BackNavigationEventInput`), so the app's one handler in
 * App.kt pops the stack, or returns to the Prayer tab, exactly as the Android back button does.
 * Before this the actual was empty and the scrub did nothing (audit sr-back).
 *
 * Enabled only while VoiceOver is running, and not for everyone: CMP 1.12 also installs a
 * screen-edge swipe-back whenever any back handler is enabled (`UIKitNavigationEventInput`; only
 * the end edge is configurable), and a sighted reader must not gain a gesture from an
 * accessibility round — on the Mushaf a page swipe that starts at the edge would leave the screen
 * instead of turning the page. VoiceOver owns single-finger touches while it runs, so the edge
 * gesture never reaches a sighted hand. Switch Control is not in the gate: it leaves direct touch
 * working, so its users would meet the swipe. A hardware keyboard's Escape reaches the handler
 * only under the same gate, so keyboard users keep what they have today. The on-screen chevron is
 * unchanged.
 *
 * `BackHandler` is deprecated in favour of `NavigationEventHandler`; it works in 1.12 and is a
 * one-line seam to swap once the replacement's API settles.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val voiceOver by rememberVoiceOverRunning()
    BackHandler(enabled = enabled && voiceOver, onBack = onBack)
}

/** True while VoiceOver is on, kept current by the system's own status notification. */
@Composable
private fun rememberVoiceOverRunning(): State<Boolean> {
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
