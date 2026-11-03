package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.notifications.currentCustomAdhan
import world.taqwa.app.notifications.customAdhanSupported
import world.taqwa.app.notifications.rememberCustomAdhanPicker
import world.taqwa.app.notifications.removeCustomAdhan
import kotlin.time.Duration.Companion.milliseconds
import world.taqwa.app.resources.adhan_voice_taqwa_label
import world.taqwa.app.resources.adhan_voice_phone_label
import world.taqwa.app.resources.adhan_voice_choose
import world.taqwa.app.resources.adhan_voice_choose_detail
import world.taqwa.app.resources.adhan_voice_custom
import world.taqwa.app.resources.adhan_voice_custom_detail
import world.taqwa.app.resources.adhan_voice_choose_another
import world.taqwa.app.resources.adhan_voice_remove
import world.taqwa.app.resources.adhan_voice_pick_failed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.adhanVoiceDisplayName
import world.taqwa.app.notifications.SoundAssets
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhan_voice_azeez_detail
import world.taqwa.app.resources.adhan_voice_azemi_detail
import world.taqwa.app.resources.adhan_voice_madinah_detail
import world.taqwa.app.resources.adhan_voice_makkah_detail
import world.taqwa.app.resources.adhan_voice_original_detail
import world.taqwa.app.resources.adhan_voice_sheet_title
import world.taqwa.app.resources.sound_a11y_preview

/**
 * Who the recording is and how long it runs — the two things a listener needs before pressing
 * play. The length is the *complete* adhan, because that is what the play button here auditions;
 * the 30-second clip a notification actually carries is the sound sheet's business.
 */
@Composable
private fun caption(voice: AdhanVoice): String {
    val length = LocalPlatformFormat.current.localizedClipLength(SoundAssets.fullAdhanDuration(voice))
    return when (voice) {
        AdhanVoice.ORIGINAL -> stringResource(Res.string.adhan_voice_original_detail, length)
        AdhanVoice.AZEEZ -> stringResource(Res.string.adhan_voice_azeez_detail, length)
        AdhanVoice.AZEMI -> stringResource(Res.string.adhan_voice_azemi_detail, length)
        AdhanVoice.MAKKAH -> stringResource(Res.string.adhan_voice_makkah_detail, length)
        AdhanVoice.MADINAH -> stringResource(Res.string.adhan_voice_madinah_detail, length)
        AdhanVoice.CUSTOM -> ""
    }
}

/**
 * One voice for all five prayers. The same row idiom as the sound sheet — and literally the same
 * composable — because the two sheets are the same question asked twice, and a listener moving
 * between them should not have to learn a second layout.
 *
 * The play button plays the **complete** adhan rather than the notification clip: a voice is
 * chosen by how it sounds over a couple of minutes, not by its first thirty seconds.
 */
@Composable
fun AdhanVoiceSheet(
    current: AdhanVoice,
    onPick: (AdhanVoice) -> Unit,
    onPreview: (AdhanVoice) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Text(
            stringResource(Res.string.adhan_voice_sheet_title),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        if (customAdhanSupported) {
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(Res.string.adhan_voice_taqwa_label), gutter = 0.dp)
        }
        TaqwaCard(Modifier.padding(top = if (customAdhanSupported) 0.dp else 16.dp, bottom = 24.dp).selectableGroup()) {
            AdhanVoice.entries.filter { it != AdhanVoice.CUSTOM }.forEachIndexed { i, voice ->
                if (i > 0) CardDivider()
                AudioOptionRow(
                    label = adhanVoiceDisplayName(voice),
                    caption = caption(voice),
                    selected = voice == current,
                    onSelect = { onPick(voice) },
                    onPreview = { onPreview(voice) },
                    previewDescription = stringResource(Res.string.sound_a11y_preview, adhanVoiceDisplayName(voice)),
                )
            }
        }
        if (customAdhanSupported) {
            // "From your phone", last (the owner's design of 3 Nov 2026): Taqwa's own adhans stay
            // the default, and a recording chosen here plays only on this phone.
            var custom by remember { mutableStateOf(currentCustomAdhan()) }
            var failed by remember { mutableStateOf(false) }
            val pick = rememberCustomAdhanPicker { info ->
                failed = info == null
                if (info != null) {
                    custom = info
                    onPick(AdhanVoice.CUSTOM)
                }
            }
            SectionLabel(stringResource(Res.string.adhan_voice_phone_label), gutter = 0.dp)
            TaqwaCard(Modifier.selectableGroup()) {
                val chosen = custom
                if (chosen == null) {
                    TaqwaRow(
                        stringResource(Res.string.adhan_voice_choose),
                        subtitle = stringResource(Res.string.adhan_voice_choose_detail),
                        onClick = pick,
                    )
                } else {
                    AudioOptionRow(
                        label = stringResource(Res.string.adhan_voice_custom),
                        caption = stringResource(
                            Res.string.adhan_voice_custom_detail,
                            chosen.name,
                            format.localizedClipLength(chosen.durationMs.milliseconds),
                        ),
                        selected = current == AdhanVoice.CUSTOM,
                        onSelect = { onPick(AdhanVoice.CUSTOM) },
                        onPreview = { onPreview(AdhanVoice.CUSTOM) },
                        previewDescription = stringResource(Res.string.sound_a11y_preview, stringResource(Res.string.adhan_voice_custom)),
                    )
                    CardDivider()
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = pick, modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.adhan_voice_choose_another), color = colors.accent)
                        }
                        TextButton(
                            onClick = {
                                removeCustomAdhan()
                                custom = null
                                if (current == AdhanVoice.CUSTOM) onPick(AdhanVoice.ORIGINAL)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(Res.string.adhan_voice_remove), color = colors.textSecondary)
                        }
                    }
                }
            }
            if (failed) {
                Spacer(Modifier.height(10.dp))
                SettingsNote(stringResource(Res.string.adhan_voice_pick_failed), gutter = 0.dp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
