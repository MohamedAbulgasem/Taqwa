package world.taqwa.app.design

import androidx.compose.runtime.Composable

/**
 * Holds the display awake for as long as the calling composition is on screen, and hands the
 * timer back on the way out. Called only where the screen is looked at for minutes without a
 * touch: the tasbeeh, counted in a pocket-free hand (the count blanking out mid-set is the
 * failure this prevents), the adhkar session, and the Qibla screen while its compass is being
 * calibrated or has been given up on. No screen that does not call this is affected.
 */
@Composable
expect fun KeepScreenOn()
