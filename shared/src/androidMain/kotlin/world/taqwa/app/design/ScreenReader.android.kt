package world.taqwa.app.design

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Touch exploration is what TalkBack turns on, and what a screen reader on Android has in common:
 * Switch Access and Voice Access leave it off, since their users still touch the screen directly.
 */
@Composable
internal actual fun platformScreenReaderRunning(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var running by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { running = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        // Read again once the listener is in place: a toggle between the first read and this
        // effect would otherwise go unseen until the next one.
        running = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return running
}
