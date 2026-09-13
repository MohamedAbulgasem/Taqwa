package world.taqwa.app.feature.recitation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.feature.settings.TaqwaToggle
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.recitation_auto_download
import world.taqwa.app.resources.recitation_cancel
import world.taqwa.app.resources.recitation_download
import world.taqwa.app.resources.recitation_downloading
import world.taqwa.app.resources.recitation_keep_reading
import world.taqwa.app.resources.recitation_over_wifi
import world.taqwa.app.resources.recitation_retry
import world.taqwa.app.resources.recitation_settings_whole_quran
import world.taqwa.app.resources.recitation_switch_ready
import world.taqwa.app.resources.recitation_switch_running
import world.taqwa.app.resources.recitation_use_mobile_once
import world.taqwa.app.resources.recitation_waiting_connection

/**
 * The download sheet (spec §5.4). One surah, one voice, one price, and — while it runs — one
 * progress bar with a plain sentence saying the reader need not sit and watch it.
 *
 * [surahName] is resolved by the caller, as everywhere else in this feature: the sheet has a
 * surah number and no database.
 *
 * The Wi-Fi note is two sentences and only the second is a control: "Over Wi-Fi." states the
 * policy, "Use mobile data this once" overrides it for this download alone and is never written
 * back to Settings — allowing this surah onto mobile data is not a decision about the next one.
 */
@Composable
fun DownloadSheet(
    sheet: DownloadSheetState,
    surahName: String,
    wholeQuran: WholeQuran?,
    onConfirm: (allowMobileOnce: Boolean, autoDownload: Boolean) -> Unit,
    onWholeQuran: (allowMobileOnce: Boolean) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onChangeReciter: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    // Which of the two downloads the last tap asked for, so "Use mobile data this once" on the
    // failure face repeats *that* one. Without it a refused whole-Quran batch offered an override
    // that quietly fetched a single surah instead — the same words meaning a different thing
    // depending on a button the reader can no longer see.
    var lastWasWholeQuran by remember(sheet.surah, sheet.reciter.id) { mutableStateOf(false) }
    // The switch's position (spec §15.3): ticked until the reader has confirmed a sheet once,
    // then whatever they last chose. Written only when a download is actually asked for.
    var autoDownload by remember(sheet.surah, sheet.reciter.id) { mutableStateOf(sheet.autoDownloadDefault) }
    Column(Modifier.padding(horizontal = 24.dp)) {
        if (arabic) {
            Text(surahName, fontFamily = mushafFamily(), fontSize = 26.sp, color = colors.textPrimary, maxLines = 1)
        } else {
            Text(surahName, style = TaqwaText.screenTitle, color = colors.textPrimary, maxLines = 1)
        }
        TaqwaCard(Modifier.padding(top = 16.dp)) {
            ReciterRow(sheet, onChangeReciter)
        }
        Spacer(Modifier.height(16.dp))
        when (val phase = sheet.phase) {
            is SheetPhase.Ready -> Ready(
                sheet = sheet,
                phase = phase,
                wholeQuran = wholeQuran,
                autoDownload = autoDownload,
                onAutoDownload = { autoDownload = it },
                onConfirm = { allow -> lastWasWholeQuran = false; onConfirm(allow, autoDownload) },
                onWholeQuran = { allow -> lastWasWholeQuran = true; onWholeQuran(allow) },
            )
            is SheetPhase.Downloading -> Running(sheet, phase, onCancel)
            is SheetPhase.Failed -> Failed(
                phase = phase,
                onRetry = onRetry,
                onOverride = { if (lastWasWholeQuran) onWholeQuran(true) else onConfirm(true, autoDownload) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReciterRow(sheet: DownloadSheetState, onChangeReciter: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onChangeReciter,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReciterMonogram(sheet.reciter, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(reciterName(sheet.reciter), style = TaqwaText.rowLabel, color = colors.textPrimary, maxLines = 1)
            Text(
                reciterStyleAndBitrate(sheet.reciter),
                style = TaqwaText.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        Canvas(Modifier.size(18.dp)) { drawChevron(colors.textTertiary, pointsForward = forward) }
    }
}

@Composable
private fun Ready(
    sheet: DownloadSheetState,
    phase: SheetPhase.Ready,
    wholeQuran: WholeQuran?,
    autoDownload: Boolean,
    onAutoDownload: (Boolean) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onWholeQuran: (Boolean) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    TaqwaPrimaryButton(
        text = stringResource(Res.string.recitation_download, megabytes(sheet.bytes)),
        onClick = { onConfirm(false) },
    )
    if (phase.needsWifiNote) {
        // Two sentences in one paragraph, the second of them a control. Splitting them into a label
        // and a button would put a line break where the eye expects a sentence to carry on, so the
        // whole line is one Text and the tap is on the row — the accent on the second half is what
        // says which half is the control.
        val accented = buildAnnotatedString {
            append(stringResource(Res.string.recitation_over_wifi))
            append(" ")
            withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) {
                append(stringResource(Res.string.recitation_use_mobile_once))
            }
        }
        Text(
            accented,
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onConfirm(true) },
                )
                .padding(top = 14.dp),
        )
    }
    // "Download future surahs without asking" (spec §15.3): a switch on the sheet itself, where
    // the reader is being asked, rather than a setting they would have to go and find. Ticked
    // the first time, since a sheet on every surah is the thing most people will not want.
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onAutoDownload(!autoDownload) },
            )
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.recitation_auto_download),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        TaqwaToggle(autoDownload, onAutoDownload)
    }
    // The reader picked this voice from the bar while another was reciting the surah (spec
    // §14.3): say what happens in the meantime, because nothing else on the sheet does.
    sheet.playingMeanwhile?.let { meanwhile ->
        Text(
            stringResource(Res.string.recitation_switch_ready, reciterName(meanwhile)),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
    // The quieter second button (spec §5.4): the same voice, all 114. Only ever the Offer face —
    // a batch already running has this surah in it, so the sheet is showing its progress bar
    // instead, and a reciter whose Quran is complete has nothing to offer.
    val offer = wholeQuran as? WholeQuran.Offer ?: return
    Spacer(Modifier.height(if (phase.needsWifiNote) 4.dp else 10.dp))
    QuietAction(
        stringResource(Res.string.recitation_settings_whole_quran, dataSize(offer.bytes)),
        onClick = { onWholeQuran(false) },
    )
}

@Composable
private fun Running(sheet: DownloadSheetState, phase: SheetPhase.Downloading, onCancel: () -> Unit) {
    val colors = LocalTaqwaColors.current
    // A download that has been accepted and has not moved a byte in twenty seconds is a download
    // waiting for something — a network, most of the time — and saying "playback starts as soon
    // as it lands" over an empty bar is the sentence the flight-mode round found indistinguishable
    // from progress. The clock runs only while the state is actually Queued: the first byte, or a
    // failure, ends it.
    var waiting by remember(phase.queued) { mutableStateOf(false) }
    if (phase.queued) {
        LaunchedEffect(Unit) {
            delay(WAITING_MS)
            waiting = true
        }
    }
    val target = if (phase.total <= 0L) 0f else (phase.bytes.toFloat() / phase.total).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, tween(400), label = "downloadProgress")
    // The button's own shape and height, filled from the start: the primary button *becomes* the
    // progress bar (spec §5.4) rather than being replaced by a thinner thing that leaves the
    // sheet jumping by twenty pixels.
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.hairline),
        contentAlignment = Alignment.Center,
    ) {
        // Start-aligned inside a centred Box: the bar fills from the reading edge, which under
        // an Arabic UI is the right one, and `align` is what stops it growing from the middle.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(48.dp)
                .background(colors.accent.copy(alpha = 0.30f)),
        )
        Text(
            stringResource(Res.string.recitation_downloading, megabytesBare(phase.bytes), megabytes(phase.total)),
            style = TaqwaText.caption.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
            color = colors.textPrimary,
        )
    }
    val meanwhile = sheet.playingMeanwhile
    Text(
        when {
            waiting -> stringResource(Res.string.recitation_waiting_connection)
            // A switch (spec §14.3): the old voice is still going, and the sentence says which
            // voice takes over rather than promising a start "from the ayah you chose".
            meanwhile != null -> stringResource(
                Res.string.recitation_switch_running,
                reciterName(meanwhile),
                reciterName(sheet.reciter),
            )
            else -> stringResource(Res.string.recitation_keep_reading)
        },
        style = TaqwaText.caption,
        color = colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    )
    QuietAction(stringResource(Res.string.recitation_cancel), onCancel)
}

@Composable
private fun Failed(phase: SheetPhase.Failed, onRetry: () -> Unit, onOverride: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Text(
        downloadFailureSentence(phase.reason),
        style = TaqwaText.caption,
        color = colors.textPrimary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(14.dp))
    TaqwaPrimaryButton(text = stringResource(Res.string.recitation_retry), onClick = onRetry)
    // Wi-Fi is the one refusal a Retry cannot answer: the same tap on the same network refuses
    // again. So the sentence that names the policy is followed by the tap that suspends it, the
    // same override the Ready face offers — otherwise the reader on mobile data is told what is
    // wrong and given the one button that cannot put it right.
    if (phase.reason == DownloadFailure.NEEDS_WIFI) UseMobileOnce(onOverride)
}

/** "Use mobile data this once" (spec §5.4), on both the faces that can act on it. */
@Composable
private fun UseMobileOnce(onOverride: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Text(
        stringResource(Res.string.recitation_use_mobile_once),
        style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOverride,
            )
            .padding(top = 14.dp),
    )
}

/** A text-weight action under a primary one: Cancel, which must be reachable and must not
 * compete with the thing it cancels. */
@Composable
private fun QuietAction(text: String, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
    }
}
