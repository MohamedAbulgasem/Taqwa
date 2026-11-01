package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether a screen reader is reading the app now, kept current as it is turned on or off:
 * TalkBack (any service that explores by touch) on Android, VoiceOver on iPhone. It gates the few
 * things only a screen reader needs that a sighted reader must not meet — moving focus, which
 * scrolls what it lands on into view — under the owner's rule that accessibility work leaves the
 * screen as it was at the phone's default settings.
 */
@Composable
fun screenReaderRunning(): Boolean {
    val platform = platformScreenReaderRunning()
    return LocalScreenReaderOverride.current ?: platform
}

/** A test's word on whether a screen reader is running; null in the app, which asks the platform. */
internal val LocalScreenReaderOverride = staticCompositionLocalOf<Boolean?> { null }

@Composable
internal expect fun platformScreenReaderRunning(): Boolean
