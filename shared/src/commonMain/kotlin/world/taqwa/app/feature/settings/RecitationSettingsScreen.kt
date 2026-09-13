package world.taqwa.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.feature.recitation.ReciterMonogram
import world.taqwa.app.feature.recitation.RecitationState
import world.taqwa.app.feature.recitation.RecitationStorage
import world.taqwa.app.feature.recitation.WholeQuran
import world.taqwa.app.feature.recitation.dataSize
import world.taqwa.app.feature.recitation.downloadFailureSentence
import world.taqwa.app.feature.recitation.reciterName
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.recitation_cancel
import world.taqwa.app.resources.recitation_reciter
import world.taqwa.app.resources.recitation_settings_batch
import world.taqwa.app.resources.recitation_settings_credit
import world.taqwa.app.resources.recitation_settings_downloads
import world.taqwa.app.resources.recitation_settings_mobile_data
import world.taqwa.app.resources.recitation_settings_surahs
import world.taqwa.app.resources.recitation_settings_surahs_one
import world.taqwa.app.resources.recitation_settings_whole_quran
import world.taqwa.app.resources.settings_recitation

/**
 * Settings › Quran › **Recitation** (spec §5.6): the voice, whether a download may leave Wi-Fi,
 * what is on the phone and what the rest of the Quran would cost.
 *
 * It owns nothing. Every value comes from [RecitationState], which is the same value the reader's
 * header button and the player bar are drawn from, so this screen can never disagree with them
 * about which voice is chosen or what is downloading; [storage] is the one thing state cannot
 * carry, because it is measured off the disk and the caller refreshes it when the registry moves.
 */
@Composable
fun RecitationSettingsScreen(
    state: RecitationState,
    storage: RecitationStorage,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    onSetMobileData: (Boolean) -> Unit,
    onOpenDownloads: (String) -> Unit,
    onDownloadWholeQuran: () -> Unit,
    onCancelWholeQuran: () -> Unit,
) {
    SettingsScaffold(stringResource(Res.string.settings_recitation), onBack) {
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.recitation_reciter),
                // Blank rather than a placeholder before the catalogue has loaded: it fills in a
                // frame later, and "Not set" would be a lie about a setting that always has a value.
                value = state.reciter?.let { reciterName(it) }.orEmpty(),
                onClick = onOpenPicker,
            )
            CardDivider()
            TaqwaRow(
                stringResource(Res.string.recitation_settings_mobile_data),
                onClick = { onSetMobileData(!state.downloadOnMobileData) },
                ripple = false,
                trailing = { TaqwaToggle(state.downloadOnMobileData, onSetMobileData) },
            )
        }

        val withDownloads = state.reciterDownloads
        if (withDownloads.isNotEmpty() && storage.total > 0L) {
            Spacer(Modifier.height(28.dp))
            // The heading carries the total, so the one number a reader came here for is the
            // first thing on the screen rather than a sum they have to do across the rows.
            SectionLabel(stringResource(Res.string.recitation_settings_downloads, dataSize(storage.total)))
            SettingsCard {
                withDownloads.forEachIndexed { index, reciter ->
                    if (index > 0) CardDivider()
                    ReciterStorageRow(
                        reciter = reciter,
                        surahs = state.downloadedByReciter[reciter.id].orEmpty().size,
                        bytes = storage.of(reciter.id),
                        onClick = { onOpenDownloads(reciter.id) },
                    )
                }
            }
        }

        val whole = state.wholeQuran
        if (whole != null) {
            Spacer(Modifier.height(if (withDownloads.isEmpty()) 28.dp else 14.dp))
            WholeQuranAction(whole, onDownloadWholeQuran, onCancelWholeQuran)
        }

        Spacer(Modifier.height(20.dp))
        // A sentence, not a link: the credits row is one tap away in the same tab, and a second
        // way into the same screen would be two answers to one question.
        SettingsNote(stringResource(Res.string.recitation_settings_credit))
    }
}

/** One reciter with something on the phone: monogram, name, "12 surahs · 298 MB", chevron. */
@Composable
private fun ReciterStorageRow(reciter: Reciter, surahs: Int, bytes: Long, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    val caption = stringResource(
        if (surahs == 1) Res.string.recitation_settings_surahs_one else Res.string.recitation_settings_surahs,
        format.localizedDigits(surahs),
        dataSize(bytes),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 32 dp, not the picker's 44: this is a list of storage, and a disc the size of the one
        // that auditions a voice would make it read as a second place to choose one.
        ReciterMonogram(reciter, 32.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(reciterName(reciter), style = TaqwaText.rowLabel, color = colors.textPrimary, maxLines = 1)
            Text(caption, style = TaqwaText.caption, color = colors.textSecondary, maxLines = 1)
        }
        Canvas(Modifier.size(14.dp)) { drawChevron(colors.textTertiary, pointsForward = forward) }
    }
}

/**
 * "Download the whole Quran · 0.87 GB" (spec §12.8), and what it becomes once it is running: the
 * count, a thin line under it and a Cancel.
 *
 * Quiet on purpose — accent text on the card's own surface rather than a filled button. It is an
 * 0.9 GB commitment offered on a settings screen nobody came to to spend a gigabyte, and it must
 * not out-shout the reciter row above it.
 */
@Composable
private fun WholeQuranAction(whole: WholeQuran, onDownload: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    SettingsCard {
        when (whole) {
            is WholeQuran.Offer -> QuietRow(
                text = stringResource(Res.string.recitation_settings_whole_quran, dataSize(whole.bytes)),
                tint = colors.accent,
                onClick = onDownload,
            )
            is WholeQuran.Running -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        stringResource(
                            Res.string.recitation_settings_batch,
                            format.localizedDigits(whole.done),
                            format.localizedDigits(whole.total),
                        ),
                        style = TaqwaText.rowLabel,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    BatchProgressLine(whole.fraction)
                }
                CardDivider()
                QuietRow(stringResource(Res.string.recitation_cancel), colors.textSecondary, onCancel)
            }
            is WholeQuran.Failed -> {
                Text(
                    downloadFailureSentence(whole.reason),
                    style = TaqwaText.caption,
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                )
                CardDivider()
                // The offer again rather than a "Retry": the reader is being asked for the same
                // gigabyte, and the price is the part of that sentence that matters.
                QuietRow(
                    text = stringResource(Res.string.recitation_settings_whole_quran, dataSize(whole.bytes)),
                    tint = colors.accent,
                    onClick = onDownload,
                )
            }
        }
    }
}

/**
 * The batch's progress line: 3 dp, the accent over the hairline, filling from the reading edge —
 * which under an Arabic UI is the right-hand one, hence the start alignment rather than a plain
 * `fillMaxWidth(fraction)` that would grow from the left in both languages.
 */
@Composable
private fun BatchProgressLine(fraction: Float) {
    val colors = LocalTaqwaColors.current
    val width by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "batchProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.hairline),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(width).height(3.dp).background(colors.accent))
    }
}

/** A row of the card whose whole content is one coloured word or phrase: an action, not a value. */
@Composable
private fun QuietRow(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.SemiBold), color = tint, maxLines = 2)
    }
}
