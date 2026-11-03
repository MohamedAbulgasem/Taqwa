package world.taqwa.app.notifications

import androidx.compose.runtime.Composable

/**
 * A recording of the adhan the user chose from their own phone ("From your phone", the owner's
 * design of 3 Nov 2026): its file name as the phone shows it, and its length. The recording is
 * copied into the app's own storage once, plays only on this phone, and is never uploaded.
 *
 * Android only for now: iOS also needs the file trimmed to its 30-second notification cap.
 */
data class CustomAdhanInfo(val name: String, val durationMs: Long)

/** Whether this platform offers "From your phone" yet. */
expect val customAdhanSupported: Boolean

/** The recording copied in, or null when there is none. */
expect fun currentCustomAdhan(): CustomAdhanInfo?

/** Deletes the copied recording. Whoever calls it moves the voice back to a bundled one. */
expect fun removeCustomAdhan()

/**
 * Opens the system's file picker for audio. [onPicked] gets the recording once it is copied in,
 * or null when nothing was chosen or the file could not be read as audio.
 */
@Composable
expect fun rememberCustomAdhanPicker(onPicked: (CustomAdhanInfo?) -> Unit): () -> Unit
