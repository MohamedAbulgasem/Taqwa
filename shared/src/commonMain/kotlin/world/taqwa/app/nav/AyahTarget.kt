package world.taqwa.app.nav

import world.taqwa.app.quran.ReadingMode

/**
 * The screen a tap on the ayah widget opens: the reader the user actually reads in, with the
 * tapped ayah already picked out rather than merely scrolled past.
 *
 * In translation mode that is the ayah's own card, selected — tinted, with its bookmark, copy and
 * share row showing — which is the state a tap on the card itself would leave it in, and so the
 * state the widget's own card leads the user to expect. In Mushaf mode it is the page holding the
 * ayah, with that ayah highlighted and its reference bar up: the same reading, on the page.
 *
 * [pageOf] is only called on the Mushaf branch, so translation-mode readers pay nothing for a
 * database lookup they will not use. It lives here, out of the composable, so both branches are
 * reachable from a test — the mode that decides between them is persisted, and flipping it on a
 * device means driving the reading-settings sheet by hand.
 */
suspend fun ayahWidgetTarget(
    mode: ReadingMode,
    surah: Int,
    ayah: Int,
    pageOf: suspend (Int, Int) -> Int,
): Screen = if (mode == ReadingMode.MUSHAF) {
    Screen.Mushaf(page = pageOf(surah, ayah), highlightSurah = surah, highlightAyah = ayah)
} else {
    Screen.Reader(surah, ayah, selectAyah = true)
}

/**
 * The screen the player bar and the media notification open for the ayah being recited (spec
 * §15.5): the same reader or page as [ayahWidgetTarget], but with nothing *selected* — the
 * recitation's own highlight already marks the ayah, and it moves on with the voice, which a
 * selected card would only argue with.
 */
suspend fun recitationTarget(
    mode: ReadingMode,
    surah: Int,
    ayah: Int,
    pageOf: suspend (Int, Int) -> Int,
): Screen = if (mode == ReadingMode.MUSHAF) Screen.Mushaf(page = pageOf(surah, ayah)) else Screen.Reader(surah, ayah)
