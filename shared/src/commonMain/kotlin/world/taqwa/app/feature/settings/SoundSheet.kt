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
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.RadioMark
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.domain.PrayerSound

private fun displayName(sound: PrayerSound): String = when (sound) {
    PrayerSound.SILENT -> "Silent"
    PrayerSound.NOTIFICATION -> "Notification"
    PrayerSound.TAKBIR -> "Takbir"
    PrayerSound.ADHAN -> "Adhan"
}

/**
 * The measured duration (`SoundAssets`) rounds to whole seconds for display — the spec's original
 * "~6s" assumed a plainly recited takbir; this recording's first complete "Allahu akbar, Allahu
 * akbar" pair actually runs 15.8s, so the subtitle says ~16s rather than repeat that assumption.
 */
private fun subtitle(sound: PrayerSound): String = when (sound) {
    PrayerSound.SILENT -> "Banner only, no sound"
    PrayerSound.NOTIFICATION -> "Your phone's default tone"
    PrayerSound.TAKBIR -> "Allahu akbar, Allahu akbar — ~16s"
    PrayerSound.ADHAN -> "Full call, 30 seconds"
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
        Text("Notification sound", style = TaqwaText.screenTitle, color = colors.textPrimary)
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
                        Text(displayName(sound), style = TaqwaText.rowLabel, color = colors.textPrimary)
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
            "Notification sounds are capped at 30 seconds on both platforms. The complete " +
                "adhan can be played inside the app.",
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}
