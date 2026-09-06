package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.RadioMark
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.soundDisplayName
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.sound_adhan_detail
import world.taqwa.app.resources.sound_notification_detail
import world.taqwa.app.resources.sound_sheet_footnote
import world.taqwa.app.resources.sound_sheet_title
import world.taqwa.app.resources.sound_silent_detail
import world.taqwa.app.resources.sound_takbir_detail

/** The takbir's measured length, and the platform notification-sound cap, in whole seconds. */
private const val TAKBIR_SECONDS = 16
private const val SOUND_CAP_SECONDS = 30

/**
 * The measured duration (`SoundAssets`) rounds to whole seconds for display — the spec's original
 * "~6s" assumed a plainly recited takbir; this recording's first complete "Allahu akbar, Allahu
 * akbar" pair actually runs 15.8s, so the subtitle says ~16s rather than repeat that assumption.
 * The number itself goes through the locale's digits, like every other numeral in the app.
 */
@Composable
private fun subtitle(sound: PrayerSound): String {
    val format = LocalPlatformFormat.current
    return when (sound) {
        PrayerSound.SILENT -> stringResource(Res.string.sound_silent_detail)
        PrayerSound.NOTIFICATION -> stringResource(Res.string.sound_notification_detail)
        PrayerSound.TAKBIR ->
            stringResource(Res.string.sound_takbir_detail, format.localizedDigits(TAKBIR_SECONDS))
        PrayerSound.ADHAN ->
            stringResource(Res.string.sound_adhan_detail, format.localizedDigits(SOUND_CAP_SECONDS))
    }
}

/** A drawn triangle, matching `CheckMark`'s "never a text glyph for a control" rule. */
@Composable
private fun PlayTriangle(modifier: Modifier = Modifier) {
    val color = LocalTaqwaColors.current.textSecondary
    Canvas(modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            lineTo(size.width * 0.86f, size.height * 0.5f)
            close()
        }
        drawPath(path, color = color)
    }
}

/**
 * Four levels, each auditionable before committing. Silent has nothing to preview, so it is the
 * one row with no play button. Selection uses a radio rather than [world.taqwa.app.design.components.CheckMark]:
 * seeing the three unselected targets — not just the chosen one — helps a listener compare them.
 */
@Composable
fun SoundSheet(
    current: PrayerSound,
    onPick: (PrayerSound) -> Unit,
    onPreview: (PrayerSound) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            stringResource(Res.string.sound_sheet_title),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        TaqwaCard(Modifier.padding(top = 16.dp)) {
            PrayerSound.entries.forEachIndexed { i, sound ->
                if (i > 0) CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(sound) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(soundDisplayName(sound), style = TaqwaText.rowLabel, color = colors.textPrimary)
                        Text(subtitle(sound), style = TaqwaText.caption, color = colors.textSecondary)
                    }
                    if (sound != PrayerSound.SILENT) {
                        PlayTriangle(
                            Modifier
                                .clickable { onPreview(sound) }
                                .padding(10.dp),
                        )
                    }
                    RadioMark(selected = sound == current, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Text(
            stringResource(
                Res.string.sound_sheet_footnote,
                LocalPlatformFormat.current.localizedDigits(SOUND_CAP_SECONDS),
            ),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}
