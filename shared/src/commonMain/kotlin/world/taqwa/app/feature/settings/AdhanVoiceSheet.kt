package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import world.taqwa.app.resources.adhan_voice_original_detail
import world.taqwa.app.resources.adhan_voice_sheet_title

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
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            stringResource(Res.string.adhan_voice_sheet_title),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        TaqwaCard(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
            AdhanVoice.entries.forEachIndexed { i, voice ->
                if (i > 0) CardDivider()
                AudioOptionRow(
                    label = adhanVoiceDisplayName(voice),
                    caption = caption(voice),
                    selected = voice == current,
                    onSelect = { onPick(voice) },
                    onPreview = { onPreview(voice) },
                )
            }
        }
    }
}
