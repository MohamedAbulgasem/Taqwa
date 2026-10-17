package world.taqwa.app.nav

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * The reader's reading-settings sheet, held by the app rather than by the reader so it stays open
 * when the voice moving on to the next surah replaces the reader under the reader's finger, as
 * the Mushaf, which no surah change replaces, keeps its own. It comes back once the next surah has
 * loaded: the sheet is drawn with the surah's content, which a reader still loading does not have.
 *
 * A reading screen opened on purpose (a widget tap, the bar, the media notification) opens without
 * it, and any top screen that is not a reader closes it, so it never comes back by itself over a
 * later reader, one pushed from the surah list included.
 */
class ReaderSheet {

    val open: MutableState<Boolean> = mutableStateOf(false)

    /** Opens [target] on purpose, without the sheet. */
    fun openReading(navigator: Navigator, target: Screen) {
        open.value = false
        navigator.openReading(target)
    }

    /** Called with each new top screen: anything but a reader closes the sheet. */
    fun onTop(screen: Screen) {
        if (screen !is Screen.Reader) open.value = false
    }
}
