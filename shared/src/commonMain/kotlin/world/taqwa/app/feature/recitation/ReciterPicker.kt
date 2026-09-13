package world.taqwa.app.feature.recitation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.feature.settings.AudioOptionRow
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.recitation_a11y_preview
import world.taqwa.app.resources.recitation_reciter

/**
 * The reciter picker (spec §5.5), in the adhan sheet's idiom exactly: one card of option rows,
 * each a monogram, a name, a caption, a play triangle that auditions the bundled clip without
 * downloading anything, and a radio. It is the same [AudioOptionRow] the two prayer-sound sheets
 * use, with a monogram put in front of it.
 *
 * A reciter this build has no preview clip for simply has no triangle — the pipeline publishes
 * the other nine later, and a triangle over a missing file would be a button that does nothing.
 *
 * The list scrolls. Ten rows do not fit a phone below the fold the way seven would have, and a
 * sheet that clipped its last two voices would hide the two the reader had to scroll for.
 */
@Composable
fun ReciterPicker(
    reciters: List<Reciter>,
    currentId: String?,
    downloadedCounts: Map<String, Int>,
    previewable: Set<String>,
    onPick: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            stringResource(Res.string.recitation_reciter),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        Column(
            Modifier
                .padding(top = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TaqwaCard {
                reciters.forEachIndexed { index, reciter ->
                    if (index > 0) CardDivider()
                    AudioOptionRow(
                        label = reciterName(reciter),
                        caption = reciterPickerCaption(reciter, downloadedCounts[reciter.id] ?: 0),
                        selected = reciter.id == currentId,
                        onSelect = { onPick(reciter.id) },
                        onPreview = if (reciter.id in previewable) ({ onPreview(reciter.id) }) else null,
                        previewDescription = stringResource(Res.string.recitation_a11y_preview),
                        leading = { ReciterMonogram(reciter, 44.dp) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
