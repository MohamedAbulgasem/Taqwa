package world.taqwa.app.widget

import org.jetbrains.compose.resources.StringResource
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.widget_background_frosted_subtitle
import world.taqwa.app.resources.widget_background_frosted_title
import world.taqwa.app.resources.widget_background_translucent_subtitle
import world.taqwa.app.resources.widget_background_translucent_title

/**
 * The spec's fourth widget background option is labelled per platform because each platform
 * describes what it actually does — neither user reads a caveat about the other's phone.
 * Translucency is genuine on Android (Glance renders a real semi-transparent surface over the
 * wallpaper); iOS cannot sample the wallpaper for its own blur, so it names the system material
 * it actually draws instead.
 *
 * The decision itself is a pure function of a platform flag, so it is unit-testable without a
 * platform-specific test source set — only [isIosPlatform] is `expect`/`actual`, and it is a
 * one-line fact no test would meaningfully strengthen.
 */
fun translucentOrFrostedTitleKey(isIos: Boolean): StringResource =
    if (isIos) Res.string.widget_background_frosted_title else Res.string.widget_background_translucent_title

/** The one-line description shown beneath the title — split out so it can sit on its own row
 * line instead of running into the check mark (Task: widget background picker layout). */
fun translucentOrFrostedSubtitleKey(isIos: Boolean): StringResource =
    if (isIos) Res.string.widget_background_frosted_subtitle else Res.string.widget_background_translucent_subtitle

/** True on iOS, false on Android. */
expect val isIosPlatform: Boolean

/**
 * How a widget gets onto this platform's home screen, which decides the last onboarding
 * screen's copy and button. iOS changed the gesture in iOS 18: the "+" in the top corner became an
 * Edit menu, and holding the app's own icon started offering its widget sizes directly, which is
 * the shortest path and the one the copy gives. Older iOS still has the "+".
 */
enum class WidgetAddPath { ANDROID_PIN, IOS_HOLD_ICON, IOS_PLUS_BUTTON }

expect val widgetAddPath: WidgetAddPath
