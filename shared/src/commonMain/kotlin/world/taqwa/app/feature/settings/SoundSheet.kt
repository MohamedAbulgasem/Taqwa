package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import world.taqwa.app.audio.notificationSoundsMuted
import world.taqwa.app.resources.sound_sheet_muted_hint
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.soundDisplayName
import world.taqwa.app.notifications.SoundAssets
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.sound_adhan_detail
import world.taqwa.app.resources.sound_notification_detail
import world.taqwa.app.resources.sound_sheet_footnote
import world.taqwa.app.resources.sound_sheet_title
import world.taqwa.app.resources.sound_silent_detail
import world.taqwa.app.resources.sound_takbir_detail

/** How often the open sheet asks whether the phone is silenced. */
private const val MUTED_POLL_MS = 1_500L

/** The platform cap on a notification sound, which the footnote quotes. */
private const val SOUND_CAP_SECONDS = 30

/**
 * Every length comes from [SoundAssets] and rounds to whole seconds for display — the spec's
 * original "~6s" assumed a plainly recited takbir; the original recording's first complete
 * "Allahu akbar, Allahu akbar" pair actually runs 15.8s, so the subtitle says ~16s rather than
 * repeat that assumption. Reading the table rather than a constant is also what lets the subtitle
 * follow the chosen voice, whose clips are cut to their own lengths. The numbers themselves go
 * through the locale's digits, like every other numeral in the app.
 */
@Composable
private fun subtitle(sound: PrayerSound, voice: AdhanVoice): String {
    val format = LocalPlatformFormat.current
    val seconds = SoundAssets.duration(sound, voice)?.let { format.localizedSeconds(it) }
    return when (sound) {
        PrayerSound.SILENT -> stringResource(Res.string.sound_silent_detail)
        PrayerSound.NOTIFICATION -> stringResource(Res.string.sound_notification_detail, seconds!!)
        PrayerSound.TAKBIR -> stringResource(Res.string.sound_takbir_detail, seconds!!)
        PrayerSound.ADHAN -> stringResource(Res.string.sound_adhan_detail, seconds!!)
    }
}

/**
 * Four levels, each auditionable before committing. Silent has nothing to preview, so it is the
 * one row with no play button. [voice] is the adhan the user has chosen: the two levels that are
 * a recording preview *that* recording and quote *its* length, so the sheet never auditions a
 * voice the notification would not actually play.
 */
@Composable
fun SoundSheet(
    current: PrayerSound,
    voice: AdhanVoice,
    onPick: (PrayerSound) -> Unit,
    onPreview: (PrayerSound) -> Unit,
    /** The levels on offer: all four for a prayer; Tahajjud leaves the adhan out (spec §17.6). */
    options: List<PrayerSound> = PrayerSound.entries,
) {
    val colors = LocalTaqwaColors.current
    // Asked while the sheet is open rather than once: the reader who hears nothing reaches for
    // the volume keys, and the hint should go the moment the phone can be heard again.
    var muted by remember { mutableStateOf(notificationSoundsMuted()) }
    LaunchedEffect(Unit) {
        while (true) {
            muted = notificationSoundsMuted()
            delay(MUTED_POLL_MS)
        }
    }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            stringResource(Res.string.sound_sheet_title),
            style = TaqwaText.screenTitle,
            color = colors.textPrimary,
        )
        TaqwaCard(Modifier.padding(top = 16.dp)) {
            options.forEachIndexed { i, sound ->
                if (i > 0) CardDivider()
                AudioOptionRow(
                    label = soundDisplayName(sound),
                    caption = subtitle(sound, voice),
                    selected = sound == current,
                    onSelect = { onPick(sound) },
                    onPreview = if (sound == PrayerSound.SILENT) null else ({ onPreview(sound) }),
                )
            }
        }
        if (muted) {
            Text(
                stringResource(Res.string.sound_sheet_muted_hint),
                style = TaqwaText.caption,
                color = colors.accent,
                modifier = Modifier.padding(top = 16.dp),
            )
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
