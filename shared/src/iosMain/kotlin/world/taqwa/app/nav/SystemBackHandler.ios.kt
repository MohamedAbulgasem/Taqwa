package world.taqwa.app.nav

import androidx.compose.runtime.Composable

/**
 * iOS has no system back button, and the app is a single Compose view controller rather than a
 * UINavigationController, so there is no interactive pop gesture to intercept either. Every
 * screen carries its own chevron; this stays empty until a UIKit navigation host exists.
 */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
