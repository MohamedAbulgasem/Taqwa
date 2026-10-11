package world.taqwa.app.feature.adhkar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_evening
import world.taqwa.app.resources.adhkar_morning

/**
 * The adhkar screen's shell (adhkar spec §3): the Tasbeeh header row, a way back and the set's
 * name, and nothing else yet. The pager, the progress row and the foot arrive with the screen
 * itself; until then this is what the horizon button opens, so the route, the back stack and the
 * debug harness's `adhkar` request can all be exercised.
 *
 * [set] is the set to open on; null stands for "the one the day is in", which the view model will
 * resolve. Until it does, a null set shows the morning title.
 */
@Composable
fun AdhkarScreen(set: AdhkarSet?, onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val title = stringResource(
        if (set == AdhkarSet.EVENING) Res.string.adhkar_evening else Res.string.adhkar_morning,
    )
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .contentWidth(),
        ) {
            // The Tasbeeh header idiom: the chevron, then the screen's name in the reader header's
            // words and weight (a 24 sp title would compete with the page beneath it).
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BackChevron(onBack)
                Text(
                    title,
                    style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
            }
        }
    }
}
