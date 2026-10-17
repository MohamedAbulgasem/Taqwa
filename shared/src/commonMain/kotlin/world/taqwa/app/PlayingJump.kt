package world.taqwa.app

import world.taqwa.app.nav.Screen

/**
 * "Show me the ayah being recited" (spec §15.5): from the bar's words, the media notification's
 * tap, or, on iOS, the app in front again with a voice going ([eachTimeInFront]). A screen already
 * showing it is asked to [scroll]; any other is replaced or pushed with the reader the user reads
 * in, at the ayah ([target], then [open]).
 *
 * A return to the front passes [yieldToRequestsSince], the request count from when the app left;
 * the bar and the media tap pass null and never yield. If a request has been counted since (a
 * widget tap or an adhkar reminder that brought the app back), nothing moves. It is checked right
 * before the scroll and again after [target] has loaded the reading settings, so a request that
 * lands at any point before the screen changes wins.
 */
internal suspend fun jumpToPlaying(
    playing: Pair<Int, Int>?,
    yieldToRequestsSince: Long?,
    requestsMade: () -> Long,
    showsSurah: (surah: Int) -> Boolean,
    scroll: () -> Unit,
    target: suspend (surah: Int, ayah: Int) -> Screen,
    open: (Screen) -> Unit,
) {
    fun yielded() = yieldToRequestsSince != null && requestsMade() != yieldToRequestsSince
    val (surah, ayah) = playing ?: return
    if (yielded()) return
    if (showsSurah(surah)) {
        scroll()
        return
    }
    val screen = runCatching { target(surah, ayah) }.getOrNull() ?: return
    if (!yielded()) open(screen)
}
