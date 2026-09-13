package world.taqwa.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.feature.recitation.dataSize
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.recitation_cancel
import world.taqwa.app.resources.recitation_downloads_confirm
import world.taqwa.app.resources.recitation_downloads_delete
import world.taqwa.app.resources.recitation_downloads_delete_all
import world.taqwa.app.resources.recitation_downloads_empty

/**
 * One downloaded surah, already resolved: the screen holds no database and no manifest, exactly
 * as the player bar and the download sheet do not.
 */
data class DownloadedSurah(val number: Int, val name: String, val bytes: Long)

/**
 * Settings › Recitation › **Downloads**, for one reciter (spec §5.6): what of this voice is on
 * the phone, one row per surah, with a delete on each and one for the lot.
 *
 * **Delete is a text button and a confirm sheet**, not a swipe and not a long-press. The app's two
 * existing deletes are a bookmark (a filled glyph you tap to un-keep, instant and re-doable in one
 * tap) and a dhikr of your own (long-pressed open, because there is nowhere else to edit it from).
 * Neither fits: 58 MB fetched over a phone connection is not undone in one tap, and a list nobody
 * has been taught to long-press would hide the only thing this screen exists to do. So the row
 * says the word, and the sheet says the size — which is the fact the reader is actually deciding on.
 */
@Composable
fun RecitationDownloadsScreen(
    reciterName: String,
    surahs: List<DownloadedSurah>,
    totalBytes: Long,
    onBack: () -> Unit,
    onDelete: (Int) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    SettingsScaffold(reciterName, onBack) {
        if (surahs.isEmpty()) {
            SettingsNote(stringResource(Res.string.recitation_downloads_empty))
            return@SettingsScaffold
        }
        SettingsCard {
            surahs.forEachIndexed { index, surah ->
                if (index > 0) CardDivider()
                SurahRow(surah) { confirm = Confirm.One(surah) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { confirm = Confirm.All },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stringResource(Res.string.recitation_downloads_delete_all),
                    style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
            }
        }
    }

    // Outside the scaffold, so the sheet is not inside the scroller it is covering.
    val open = confirm
    if (open != null) {
        val bytes = when (open) {
            is Confirm.One -> open.surah.bytes
            Confirm.All -> totalBytes
        }
        DeleteConfirmSheet(
            bytes = bytes,
            onDismiss = { confirm = null },
            onConfirm = {
                when (open) {
                    is Confirm.One -> onDelete(open.surah.number)
                    Confirm.All -> onDeleteAll()
                }
                confirm = null
            },
        )
    }
}

/** Which delete is being confirmed. */
private sealed interface Confirm {
    data class One(val surah: DownloadedSurah) : Confirm
    data object All : Confirm
}

/** "36  Ya-Sin        6.1 MB   Delete" — number, name, size, and the word. */
@Composable
private fun SurahRow(surah: DownloadedSurah, onDelete: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            format.localizedDigits(surah.number),
            style = TaqwaText.caption,
            color = colors.textTertiary,
            maxLines = 1,
            textAlign = TextAlign.Start,
            // A fixed column, so 114 names start on one line rather than a ragged edge that
            // moves in and out by a digit — wide enough for three, with a gap after it, or
            // «112» sits against the L of Al-Ikhlas.
            modifier = Modifier.width(40.dp).padding(end = 6.dp),
        )
        Text(
            surah.name,
            style = TaqwaText.rowLabel,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            dataSize(surah.bytes),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(
            Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.recitation_downloads_delete),
                style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** "Delete 298 MB?" — the size is the whole question, so it is the whole heading. */
@Composable
private fun DeleteConfirmSheet(bytes: Long, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.recitation_downloads_confirm, dataSize(bytes)),
                style = TaqwaText.screenTitle.copy(fontSize = 20.sp),
                color = colors.textPrimary,
            )
            TaqwaPrimaryButton(stringResource(Res.string.recitation_downloads_delete), onConfirm)
            TaqwaTextLink(stringResource(Res.string.recitation_cancel), onDismiss)
        }
    }
}
