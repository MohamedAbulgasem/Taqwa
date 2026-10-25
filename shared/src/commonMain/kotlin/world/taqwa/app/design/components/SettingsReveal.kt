package world.taqwa.app.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** How long a row that follows a switch takes to slide and fade in or out. */
private const val REVEAL_MS = 220

/**
 * Whether the screen's reveals stand still. The screen reads [world.taqwa.app.design.reduceMotion]
 * once and provides it here; unprovided, the reveals move.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

internal fun revealEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None else expandVertically(tween(REVEAL_MS)) + fadeIn(tween(REVEAL_MS))

internal fun revealExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None else shrinkVertically(tween(REVEAL_MS)) + fadeOut(tween(REVEAL_MS))

/**
 * Every row, subtitle, line and note that depends on a switch comes and goes through this (Duha
 * and Iqama spec §4.3), so the four reminder cards in Settings › Notifications move alike. Hidden,
 * the content leaves the semantics tree too, so a screen reader meets nothing a switch has put away.
 */
@Composable
fun SettingsReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val reduced = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = revealEnter(reduced),
        exit = revealExit(reduced),
        content = content,
    )
}
