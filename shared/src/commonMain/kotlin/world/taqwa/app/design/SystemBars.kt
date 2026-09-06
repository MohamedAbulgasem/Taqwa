package world.taqwa.app.design

import androidx.compose.runtime.Composable

/**
 * Tells the platform which way the status and navigation bar glyphs should face.
 *
 * Without this both platforms decide from the *system* appearance, so an app forced to Light on
 * a phone set to Dark draws white clock digits over its own near-white background and the status
 * bar simply vanishes. [dark] is the theme actually painted; [mode] is the user's choice, which
 * iOS needs separately because it can only be told "follow the system" or "override it".
 */
@Composable
expect fun SystemBarsAppearance(mode: ThemeMode, dark: Boolean)
