package world.taqwa.app.feature.prayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.watchfeed.WatchTarget
import world.taqwa.app.watchfeed.WatchTargets
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Home, the Prayer screen, as the watch card's suites on Home compose it (WatchCardOnHomeTest).

/** A watch without Taqwa: Settings' install row, and Home's install card. */
internal fun installOn(t: WatchTarget = galaxy) = WatchTargets(install = t, addFace = null)

/** A ready Home with fixed instants (SourceLabelTest's Cape Town): nothing here reads the engine. */
internal fun readyHome(polar: Boolean = false, oneTimeCard: OneTimeCard? = null, oneTimeCardThisLaunch: Boolean = false): PrayerUiState.Ready {
    val fajr = Instant.parse("2026-10-22T02:36:00Z")
    val times = listOf(
        Prayer.FAJR to fajr,
        Prayer.DHUHR to fajr + 480.minutes,
        Prayer.ASR to fajr + 695.minutes,
        Prayer.MAGHRIB to fajr + 873.minutes,
        Prayer.ISHA to fajr + 953.minutes,
    )
    val rows = times.map { (prayer, instant) ->
        TimelineRow(prayer, instant, if (prayer == Prayer.FAJR) PrayerStatus.PASSED else PrayerStatus.UPCOMING)
    }
    return PrayerUiState.Ready(
        location = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", "Cape Town", "ZA"),
        cityDisplayName = "Cape Town",
        hijri = "10 Jumada al-Ula 1448",
        gregorian = "22 October 2026",
        today = TodayState(rows, PrayerTime(Prayer.DHUHR, fajr + 480.minutes), 150.minutes, ringProgress = 0.69f),
        whose = WhoseTimes("authority_diyanet", EntryClass.A),
        polar = polar,
        oneTimeCard = oneTimeCard,
        oneTimeCardThisLaunch = oneTimeCardThisLaunch,
        qiblaBearingDegrees = 23.0,
        qiblaDistanceKm = 6559.0,
    )
}

/**
 * Home, the Prayer screen, on a phone [width] by [height], with the watch card [watchCard]; each of
 * the card's callbacks, every touch and every press and lift counted in [taps] unless the test binds
 * them to a holder.
 */
@Composable
internal fun Home(
    state: PrayerUiState,
    watchCard: WatchCardUi,
    taps: CardTaps = CardTaps(),
    width: Dp = 384.dp,
    height: Dp = 760.dp,
    onOpen: () -> Unit = { taps.open++ },
    onDismiss: (Duration) -> Unit = {
        taps.dismiss++
        taps.window = it
    },
    onUndo: () -> Unit = { taps.undo++ },
    onTouched: () -> Unit = { taps.touched++ },
    onPressed: (Boolean) -> Unit = { taps.pressed += it },
) {
    CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
        Box(Modifier.size(width, height)) {
            PrayerScreen(
                state = state,
                onChooseCity = {},
                onOpenLocationSettings = {},
                onOpenQibla = {},
                onOpenAdhkar = {},
                onOpenTasbeeh = {},
                watchCard = watchCard,
                onOpenWatchCard = onOpen,
                onDismissWatchCard = onDismiss,
                onUndoWatchCard = onUndo,
                onHomeTouched = onTouched,
                onHomePressed = onPressed,
            )
        }
    }
}
