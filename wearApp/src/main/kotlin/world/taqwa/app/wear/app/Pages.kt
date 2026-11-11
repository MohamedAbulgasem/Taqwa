package world.taqwa.app.wear.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/** The app's three pages, in the order the pager shows them, left to right in every language (call 4). */
internal enum class Page { TODAY, TASBEEH, QIBLA }

/** The extra that opens the app on a page: the page's name (`TASBEEH`); none, or a name that is no page, is Today. */
const val EXTRA_PAGE = "world.taqwa.app.wear.PAGE"

/** The page [name] names ([EXTRA_PAGE]), or Today. */
internal fun pageOf(name: String?): Page = Page.entries.firstOrNull { it.name == name } ?: Page.TODAY

/**
 * A page that has only its [title] and, under it, what has to be said ([notice]), centred and in
 * the accent: Today once its feed is spent ("Open Taqwa on your phone", or "Update Taqwa on your
 * watch" when a newer format has been seen; `world.taqwa.app.wear.today.TodayPage`). The notice is
 * one node, read as it is written.
 */
@Composable
internal fun NoticePage(title: String, notice: String) {
    FramePage(title = title) {
        LineText(notice, FrameType.notice, y = NOTICE_Y, color = WatchColors.accent, weight = WatchFonts.weight(600), centred = true)
    }
}

/**
 * Every page of a watch with no feed ([AppState.NoFeed]): the watch's own line ([noFeedLine]),
 * centred, in the watch's language and direction. It is one node, read as it is written.
 */
@Composable
internal fun NoFeedPage(state: AppState.NoFeed) {
    UnitsBox {
        LineText(stringResource(noFeedLine(state.update)), FrameType.notice, y = 100f, color = WatchColors.ink, weight = WatchFonts.weight(600), centred = true)
    }
}

/**
 * A page with its [title], which is also its pane title, so that a screen reader says it as the
 * page comes in. Only semantics are added: nothing drawn changes for them.
 */
@Composable
private fun FramePage(title: String, content: @Composable () -> Unit) {
    UnitsBox(Modifier.semantics { paneTitle = title }) {
        LineText(title, FrameType.title, y = TITLE_Y, color = WatchColors.ink, weight = WatchFonts.weight(800), modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * The watch's clock, in epoch seconds: read when the page is drawn, and again at each [everyMillis]
 * on the clock (each minute by default, each second for Today's ring) while [active]. It gives `now`
 * and nothing more: every instant is the feed's.
 */
@Composable
internal fun rememberNow(active: Boolean, everyMillis: Long = MINUTE_MS): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(active, everyMillis) {
        while (active) {
            now = System.currentTimeMillis() / 1000
            delay(everyMillis - System.currentTimeMillis() % everyMillis)
        }
    }
    return now
}

private const val MINUTE_MS = 60_000L

/** The frame's lines, in the drawing's units: the title, and under it the notice, centred. */
private const val TITLE_Y = 94f
private const val NOTICE_Y = 128f
