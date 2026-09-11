package world.taqwa.app.design

import androidx.compose.runtime.Composable

/**
 * Holds the display awake for as long as the calling composition is on screen, and hands the
 * timer back on the way out. The tasbeeh is the one screen that is looked at for minutes while
 * being tapped in a pocket-free hand — the count blanking out mid-set is the failure this
 * prevents — and no screen that does not call this is affected.
 */
@Composable
expect fun KeepScreenOn()
