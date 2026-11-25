package world.taqwa.app.debug

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlin.time.Instant
import world.taqwa.app.design.TaqwaTheme
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.design.components.TaqwaTabScaffold
import world.taqwa.app.di.appContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.feature.prayer.OneTimeCardSession
import world.taqwa.app.feature.prayer.PrayerScreen
import world.taqwa.app.feature.prayer.PrayerUiState
import world.taqwa.app.feature.prayer.PrayerViewModel
import world.taqwa.app.feature.prayer.WatchCardAnswer
import world.taqwa.app.feature.prayer.WatchCardStep
import world.taqwa.app.feature.prayer.WatchCardUi
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.nav.Tab
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.KeyValueStore

/**
 * Debug-only gallery of Home's watch card (1.7.0) in every state, on the real Prayer screen, for
 * screenshots: the release build carries no fake state, so the states are drawn here. Never
 * referenced from a main source set: it exists in the debug variant alone, and it uses only public
 * API, the Prayer screen and its view model with the app container's engine and city list, as
 * `WatchHarnessReceiver` does.
 *
 * ```
 * adb -s emulator-5556 shell am start -W -S -n world.taqwa.app/world.taqwa.app.debug.WatchCardGalleryActivity \
 *   --es card install [--es theme dark] [--ef fontScale 1.3] [--ez landscape true]
 * ```
 *
 * - `card`: `none`, `install`, `install-long` (a name too long for one line), `face`, `asking`,
 *   `continue`, `continue-face`, `unreachable` (the face's), `dismissed`;
 * - `theme`: `light` (the default) or `dark`;
 * - `fontScale`: the reader's text size, 1 by default;
 * - `landscape`: held sideways.
 *
 * The screen is Tripoli's at a fixed instant (20 November 2026, 14:58 there), in the language the
 * app is set to (`cmd locale set-app-locales`), with no one-time card: the gallery is about the
 * watch card. Its view model keeps its settings in a store of its own, in the cache, and writes no
 * widget: nothing the app itself has stored is read or changed.
 */
class WatchCardGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("landscape", false)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val card = cardOf(intent.getStringExtra("card"))
        val mode = if (intent.getStringExtra("theme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT
        val fontScale = intent.getFloatExtra("fontScale", 1f)
        val settings = SettingsRepository(store(cacheDir))
        setContent {
            val format = remember { createPlatformFormat() }
            val viewModel = remember {
                PrayerViewModel(
                    engine = appContainer.prayerTimesEngine,
                    settings = settings,
                    locationOf = { TRIPOLI },
                    now = { NOW },
                    cityRepository = appContainer.cityRepository,
                    format = format,
                    widgetStore = { NoWidgets },
                    onWidgetsChanged = {},
                    cardSession = OneTimeCardSession(),
                )
            }
            LaunchedEffect(viewModel) { viewModel.refresh() }
            val state by viewModel.state.collectAsState()
            val shown = (state as? PrayerUiState.Ready)?.copy(oneTimeCard = null, oneTimeCardThisLaunch = false) ?: state
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides if (isRtlLocale()) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalPlatformFormat provides format,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                TaqwaTheme(mode) {
                    TaqwaTabScaffold(Tab.PRAYER, onSelect = {}) {
                        PrayerScreen(
                            state = shown,
                            onChooseCity = {},
                            onOpenLocationSettings = {},
                            onOpenQibla = {},
                            onOpenAdhkar = {},
                            onOpenTasbeeh = {},
                            watchCard = card,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val TRIPOLI = GeoLocation(32.8872, 13.1913, "Africa/Tripoli", "Tripoli", "LY", cityId = 2210247)
        val NOW: Instant = Instant.parse("2026-11-20T12:58:00Z")
        const val WATCH = "Galaxy Watch7"
        const val LONG_WATCH = "Galaxy Watch8 Classic (5F2A)"

        /** One store for the process: DataStore allows a single one per file. */
        private var opened: DataStore<Preferences>? = null

        fun store(cache: File): DataStore<Preferences> = opened ?: PreferenceDataStoreFactory
            .create { File(cache, "watch-card-gallery.preferences_pb") }
            .also { opened = it }

        fun cardOf(name: String?): WatchCardUi = when (name) {
            "install" -> WatchCardUi.Offer(WatchCardStep.INSTALL, WATCH)
            "install-long" -> WatchCardUi.Offer(WatchCardStep.INSTALL, LONG_WATCH)
            "face" -> WatchCardUi.Offer(WatchCardStep.ADD_FACE, WATCH)
            "asking" -> WatchCardUi.Offer(WatchCardStep.INSTALL, WATCH, WatchCardAnswer.ASKING)
            "continue" -> WatchCardUi.Offer(WatchCardStep.INSTALL, WATCH, WatchCardAnswer.CONTINUE)
            "continue-face" -> WatchCardUi.Offer(WatchCardStep.ADD_FACE, WATCH, WatchCardAnswer.CONTINUE)
            "unreachable" -> WatchCardUi.Offer(WatchCardStep.ADD_FACE, WATCH, WatchCardAnswer.UNREACHABLE)
            "dismissed" -> WatchCardUi.Dismissed
            else -> WatchCardUi.Hidden
        }
    }

    /** No widget mirror: the gallery's Tripoli never reaches the app's widgets. */
    private object NoWidgets : KeyValueStore {
        override fun putString(key: String, value: String) = Unit
        override fun getString(key: String): String? = null
    }
}
