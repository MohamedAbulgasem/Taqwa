package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.drawBook
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_reader_aa
import world.taqwa.app.resources.quran_reader_mushaf

/** The 36 dp round icon buttons' size (spec §2.2). */
private val IconButtonSize = 36.dp

/**
 * The reader's shared header (spec §2.2): back chevron, title and caption, then the mode-toggle
 * book button and the "Aa" settings button. Used by both [ReaderScreen] (translation mode, this
 * task) and the Mushaf screen (task 8) — [title] and [caption] are already resolved by the
 * caller, since translation mode names the surah while Mushaf mode names the current page's first
 * line's surah, and only the caller knows which.
 *
 * Under an Arabic UI the title is drawn in [mushafFamily] at 20 sp, matching the surah name
 * everywhere else in the app (spec §5.1); otherwise it is [TaqwaText.rowLabel] at extra-bold
 * weight. Callers pick the string to match — [Surah.nameArabic] under Arabic, [Surah.nameLatin]
 * otherwise — since only they know which surah is showing.
 */
@Composable
fun ReaderHeader(
    title: String,
    caption: String,
    mushafSelected: Boolean,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackChevron(onBack)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            if (arabic) {
                Text(title, style = TaqwaText.quran(20).copy(fontFamily = mushafFamily()), color = colors.textPrimary, maxLines = 1)
            } else {
                Text(
                    title,
                    style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
            }
            Text(caption, style = TaqwaText.caption.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderIconButton(
                selected = mushafSelected,
                description = stringResource(Res.string.quran_reader_mushaf),
                onClick = onToggleMode,
            ) { tint -> Canvas(Modifier.size(18.dp)) { drawBook(tint) } }
            HeaderIconButton(
                selected = false,
                description = stringResource(Res.string.quran_reader_aa),
                onClick = onOpenSheet,
            ) { tint ->
                Text(
                    "Aa",
                    style = TaqwaText.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                    color = tint,
                )
            }
        }
    }
}

/** One 36 dp round header button (spec §2.2): plain for "Aa", accent-haloed when [selected] (the
 * book button in Mushaf mode). No ripple — the halo itself is the selected state's feedback. */
@Composable
private fun HeaderIconButton(
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val tint = if (selected) colors.accent else colors.textSecondary
    Column(
        Modifier
            .size(IconButtonSize)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.18f), CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content(tint)
    }
}
