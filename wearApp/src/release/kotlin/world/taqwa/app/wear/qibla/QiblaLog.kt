package world.taqwa.app.wear.qibla

/**
 * The release build's side of the debug build's layout log (`src/debug`): it writes nothing, and,
 * being inline, never builds the line it is handed, since the page's lines name the detail it draws
 * (the bearing and the distance). The wrist compass's own line holds no place and logs here too
 * once its tag is enabled ([qiblaLogOn]).
 */
internal object QiblaLog {
    /** Not a debug build: the compass logs its line only with the tag enabled ([qiblaLogOn]). */
    const val IN_DEBUG_BUILD = false

    @Suppress("NOTHING_TO_INLINE", "UNUSED_PARAMETER")
    inline fun line(text: () -> String) = Unit
}
