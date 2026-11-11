package world.taqwa.app.watchfeed

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Whether the Kotlin framework is a debug binary: Xcode's Debug configuration builds one
 * (`embedAndSignAppleFrameworkForXcode` follows `CONFIGURATION`), its Release, an archive's, never.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun isDebugBuild(): Boolean = Platform.isDebugBinary

/** The iPhone has no log tag to set, so its step line stays a debug binary's. */
internal actual fun stepsTagEnabled(): Boolean = false
