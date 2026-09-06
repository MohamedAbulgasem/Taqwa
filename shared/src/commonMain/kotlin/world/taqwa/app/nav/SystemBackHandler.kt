package world.taqwa.app.nav

import androidx.compose.runtime.Composable

/**
 * Lets the platform's own "go back" gesture walk [Navigator]'s stack.
 *
 * Compose Multiplatform 1.12 does expose a common `BackHandler`, but only on the non-Android
 * targets — `androidx.compose.ui.backhandler` is absent from the Android artifact, where the
 * hardware button belongs to `androidx.activity` instead. So this is one more small expect/actual
 * seam rather than a shared call.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
