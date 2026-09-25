package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import world.taqwa.app.di.AppContainer
import world.taqwa.app.feature.recitation.RecitationController
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.nav.LaunchRequests
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.nav.Tab
import world.taqwa.app.nav.ayahWidgetTarget
import world.taqwa.app.nav.openReading
import world.taqwa.app.recitation.foregroundReturnsToRecitation
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.AyahPoolMirrorWriter
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.refreshWidgets

@Composable
internal fun PlayingLaunchRequests(
    settings: SettingsRepository,
    openPlaying: () -> Unit,
) {
    LaunchedEffect(Unit) {
        settings.onboardingComplete.filter { it }.first()
        LaunchRequests.pendingPlaying.collect { asked ->
            if (asked) {
                openPlaying()
                LaunchRequests.consumePlaying()
            }
        }
    }
}

@Composable
internal fun DebugScreenRequests(
    settings: SettingsRepository,
    navigator: Navigator,
) {
    // The debug harnesses' "open this screen" (see LaunchRequests.openScreen): a tab root, or a
    // sub-screen on top of its own tab, exactly as a finger would reach it.
    LaunchedEffect(Unit) {
        settings.onboardingComplete.filter { it }.first()
        LaunchRequests.pendingScreen.collect { name ->
            name ?: return@collect
            when (name) {
                "prayer" -> navigator.selectTab(Tab.PRAYER)
                "quran" -> navigator.selectTab(Tab.QURAN)
                "settings" -> navigator.selectTab(Tab.SETTINGS)
                "notifications" -> { navigator.selectTab(Tab.SETTINGS); navigator.push(Screen.NotificationSettings) }
                "appearance" -> { navigator.selectTab(Tab.SETTINGS); navigator.push(Screen.Appearance) }
                "qibla" -> { navigator.selectTab(Tab.PRAYER); navigator.push(Screen.Qibla) }
                "tasbeeh" -> { navigator.selectTab(Tab.PRAYER); navigator.push(Screen.Tasbeeh) }
            }
            LaunchRequests.consumeScreen()
        }
    }
}

@Composable
internal fun ForegroundEffect(
    refreshPermissions: suspend () -> Unit,
    recitation: RecitationController,
    openPlaying: () -> Unit,
) {
    val appLifecycle = LocalLifecycleOwner.current
    LaunchedEffect(appLifecycle) {
        appLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshPermissions()
            if (foregroundReturnsToRecitation && recitation.state.value.bar?.playing == true) openPlaying()
        }
    }
}

@Composable
internal fun RecitationLibraryStartUp(
    container: AppContainer,
) {
    // Recitation library reconciliation (spec 3a §7). The registry in DataStore is what every
    // recitation screen reads, and it can fall out of step with the disk without the app being
    // involved at all — the system clearing app storage, a commit that did not survive the process
    // being killed, a reinstall over files that were left behind. One pass at start puts the two
    // back in agreement, in both directions, before anything can be tapped.
    //
    // Off the main thread because it stats every downloaded surah, and swallowed on failure for
    // the same reason the mirror write is: a library that could not be scanned is a stale registry,
    // which the next start fixes, and not a reason to fail a launch.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { container.recitationLibrary.reconcile() }
            // ── Recitation catalogue refresh (spec 3a §4, slice 3a task 2) ──────────
            // After the reconciliation and on the same background pass: at most one fetch of
            // manifest.json a day, silent about every way it can fail. A reader who is offline
            // keeps yesterday's catalogue, or the one bundled with the build. And nothing at all
            // until the reader has used recitation — the refresher checks that itself (privacy
            // spec §2), which is why this line can stay unconditional.
            runCatching { container.manifestRefresher.refreshIfStale() }
            // ── end recitation catalogue refresh ────────────────────────────────────
        }
    }
}

@Composable
internal fun LanguageChangeReschedule(
    uiLanguage: String,
    container: AppContainer,
    settings: SettingsRepository,
    platformFormat: PlatformFormat,
) {
    // The other two things written in the interface language outside the composition: the
    // notifications already armed (title, body and channel names are baked at schedule time)
    // and the prayer widgets' mirror (written by Today's view model, which only exists while the
    // Prayer tab is on screen). Both would otherwise keep the previous language after an in-place
    // switch — the normal case on Android 13+, where the per-app language page returns to a
    // process that is still alive — until the next cold start or the twice-daily top-up. Skipped
    // on the first composition: the cold-start path already reschedules and Today writes its
    // own mirror.
    val languageAtStart = remember { uiLanguage }
    var lastLanguageApplied by remember { mutableStateOf(languageAtStart) }
    LaunchedEffect(uiLanguage) {
        if (uiLanguage == lastLanguageApplied) return@LaunchedEffect
        lastLanguageApplied = uiLanguage
        container.notificationCoordinator.reschedule(world.taqwa.app.notifications.RescheduleTrigger.SETTINGS_CHANGED)
        world.taqwa.app.widget.WidgetMirrorRefresher.refresh(settings, container.prayerTimesEngine, format = platformFormat)
        refreshWidgets()
    }
}

@Composable
internal fun AyahPoolMirror(
    uiLanguage: String,
    platformFormat: PlatformFormat,
    settings: SettingsRepository,
    container: AppContainer,
) {
    // Ayah widget pool mirror (design spec §4): fills the widget KeyValueStore from the Quran
    // database once on start, again whenever the reading translation changes, and again whenever
    // the UI language does, so neither widget process ever has to open the database itself. The
    // database work runs off the main thread, and a failure here (a locked store, a database that
    // failed to open) is swallowed rather than crashing the app — the mirror simply stays stale
    // until the next successful write.
    //
    // Keyed on the *resolved* `ui_language` string rather than on `Unit`, which is what spec §4's
    // "whenever the UI language changes" needs on Android: MainActivity declares `configChanges`
    // for locale, so nothing recreates when the user switches the app's language — the composition
    // simply re-resolves its strings. `ui_language` is the same string `isRtlLocale()` reads, so
    // this effect restarts on exactly the changes that flip the card's script. Restarting writes
    // the mirror once and then re-collects; the write ends in `refreshWidgets()`, which touches
    // nothing this key reads, so there is no loop.
    LaunchedEffect(uiLanguage) {
        // `platformFormat` is itself keyed on `uiLanguage`, so by the time this effect restarts
        // it already reports the new language. It is kept, not just asked for its tag, because
        // the mirror also records the digit set this same format renders (D2).
        val format = platformFormat
        val languageTag = format.languageTag()
        val store = createWidgetKeyValueStore()
        suspend fun writeMirror() {
            runCatching {
                val reading = settings.readingSettings(languageTag).first()
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        AyahPoolMirrorWriter.write(store, container.quranRepository, reading, languageTag, format)
                    }
                }
                result.onSuccess { refreshWidgets() }
            }
        }
        writeMirror()
        runCatching {
            settings.readingSettings(languageTag)
                .map { it.translationId }
                .distinctUntilChanged()
                .drop(1)
                .collect { writeMirror() }
        }
    }
}

@Composable
internal fun WidgetLaunchRequests(
    settings: SettingsRepository,
    platformFormat: PlatformFormat,
    container: AppContainer,
    navigator: Navigator,
) {
    // Widget tap launch requests (design spec §8). Waits for onboarding to be known complete before
    // collecting: on a cold start the back stack still shows Screen.Today until the onboarding
    // effect above has read DataStore and (if needed) replaced it with Screen.Onboarding, so acting
    // on a pending request before that point would push a screen only to have replaceAll wipe it out
    // right after. Once onboarding is known complete it can never become incomplete again in this
    // session, so a plain collect on the pending flow is enough — a warm tap, where onboarding is
    // already behind the user, is simply the first emission this effect ever sees.
    LaunchedEffect(Unit) {
        settings.onboardingComplete.filter { it }.first()
        LaunchRequests.pendingAyah.collect { pending ->
            val (surah, ayah) = pending ?: return@collect
            runCatching {
                // The reader the user actually reads in, with the tapped ayah already picked out:
                // selected and showing its actions in translation mode, highlighted with its
                // reference bar in Mushaf mode. Resolved before the navigator is touched at all,
                // because `pageOf` is itself a database read that can fail and a failure must
                // leave the back stack exactly as it found it rather than half-applying a push.
                val reading = settings.readingSettings(platformFormat.languageTag()).first()
                val target = ayahWidgetTarget(reading.mode, surah, ayah) { s, a ->
                    container.quranRepository.pageOf(s, a)
                }
                // On the Quran tab, with the Quran root at the bottom of the stack: a tap from
                // the Prayer tab used to push the root and the reader on top of Prayer, which
                // kept the tab current at Prayer and so hid the player bar (spec §5.3 draws it
                // only on the Quran tab) until the tab was left and re-entered. `openReading`
                // also keeps the D3 rule: one Back from the reader reaches the surah list.
                navigator.openReading(target)
            }
            // Consumed unconditionally: a bad request (e.g. a database failure resolving the page)
            // must not be retried forever on every future emission.
            LaunchRequests.consume()
        }
    }
}
