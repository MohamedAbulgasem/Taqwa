package world.taqwa.app.wear.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import world.taqwa.app.wear.FeedStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import world.taqwa.app.wear.TaqwaWearApp
import world.taqwa.app.wear.tasbeeh.PhraseList
import world.taqwa.app.wear.tasbeeh.PrefsTasbeehMemory
import world.taqwa.app.wear.tasbeeh.TasbeehPage
import world.taqwa.app.wear.tasbeeh.WatchTasbeeh
import world.taqwa.app.wear.today.TodayPage
import world.taqwa.app.wear.qibla.QiblaPage

/**
 * The watch app: one activity, a pager of its three pages (spec 2.5), Today, then Tasbeeh, then
 * Qibla. It opens on the page [EXTRA_PAGE] names, or Today.
 *
 * Each time it comes to the front it calls [TaqwaWearApp.onAppOpened]: Ruling 22's app-start net,
 * when something the complications and the Tile show may have changed, and the phone asked for a
 * feed when the one held needs it.
 *
 * It holds the tasbeeh counter ([WatchTasbeeh]) above the pager, on its own lifecycle's scope: the
 * pager composes only the page in front, and a page's own scope would end, with the counter's last
 * writes and a set's hold, at the first swipe away. The counter is given his phrases from each feed
 * stored, before the first frame, and its count is written whenever the activity pauses (the page
 * writes it too as soon as it stops being in front: erratum p3 #8).
 *
 * It starts on the launch screen, the launcher icon itself on black ([installSplashScreen], before
 * anything else), which leaves as soon as the first frame is ready.
 *
 * It is a single task (the manifest), so the Tile's tap reaches this one through [onNewIntent],
 * which turns the pager to the page [EXTRA_PAGE] names; an intent that names none, the launcher's,
 * leaves the page as it was.
 */
class WearMainActivity : ComponentActivity() {

    private val tasbeeh by lazy { WatchTasbeeh(PrefsTasbeehMemory(this), WatchHaptics(this), lifecycleScope) }

    /** The pages later intents ask for (the Tile's tap), for the pager to turn to. */
    private val pageRequests = MutableSharedFlow<Page>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super: the launch screen (Theme.Taqwa.Starting) hands to the device's theme here,
        // and on Wear OS 3, which has no system splash screen, it is drawn by this.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val store = (application as TaqwaWearApp).feedStore
        val start = pageOf(intent?.getStringExtra(EXTRA_PAGE))
        // On the main thread at once: the feed held is the counter's before anything is drawn.
        lifecycleScope.launch { store.changes.collect { feed -> feed?.let { tasbeeh.setPhrases(PhraseList.of(it)) } } }
        setContent { WatchFrame(store, start, tasbeeh, pageRequests) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_PAGE)?.let { pageRequests.tryEmit(pageOf(it)) }
    }

    override fun onResume() {
        super.onResume()
        (application as TaqwaWearApp).onAppOpened()
    }

    override fun onPause() {
        tasbeeh.flush()
        super.onPause()
    }
}

/**
 * The frame. The pager runs left to right in every language (call 4): a Wear app closes with a
 * swipe to the right, so a mirrored pager would make that same swipe mean "next page" on Today.
 * Compose lays a pager out in reverse under right to left, so the scaffold and the pager are
 * composed under [LayoutDirection.Ltr], and each page's content under [WatchTheme], which gives it
 * the phone's direction.
 *
 * Nothing the drawings lack (call 16): no time at the top (`AppScaffold`'s default is the watch's
 * own `TimeText`, in its own 12/24 hours and digits, not the phone's), no page dots, no scroll bar.
 * The pager's rotary behaviour is left at its default, none, so the bezel never turns a page, and
 * it keeps its own scroll semantics, so a screen reader can turn its pages.
 */
@Composable
internal fun WatchFrame(store: FeedStore, start: Page, tasbeeh: WatchTasbeeh, pageRequests: Flow<Page> = emptyFlow()) {
    val feed by store.changes.collectAsStateWithLifecycle()
    val newerVersionSeen by store.newerVersionSeen.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val pagerState = rememberPagerState(initialPage = start.ordinal) { Page.entries.size }
    LaunchedEffect(pagerState, pageRequests) { pageRequests.collect { pagerState.scrollToPage(it.ordinal) } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AppScaffold(timeText = {}) {
            HorizontalPagerScaffold(pagerState = pagerState, pageIndicator = null) {
                HorizontalPager(state = pagerState) { index ->
                    val page = Page.entries[index]
                    val active = resumed && pagerState.settledPage == index
                    // The branch alone is taken from the state here: whether the feed is spent is
                    // Today's to see, on its own clock (TodayPage).
                    val state = appState(feed, newerVersionSeen, now = System.currentTimeMillis() / 1000)
                    WatchTheme(rtl = (state as? AppState.Ready)?.feed?.ui?.rtl) {
                        when (state) {
                            is AppState.NoFeed -> NoFeedPage(state)
                            is AppState.Ready -> when (page) {
                                Page.TODAY -> TodayPage(state.feed, active, state.update)
                                Page.TASBEEH -> TasbeehPage(state.feed, active, tasbeeh)
                                Page.QIBLA -> QiblaPage(state.feed, active)
                            }
                        }
                    }
                }
            }
        }
    }
}
