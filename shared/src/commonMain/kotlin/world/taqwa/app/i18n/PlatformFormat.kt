package world.taqwa.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

interface PlatformFormat {
    fun languageTag(): String

    /** Locale-correct digits for a plain non-negative integer, e.g. "42" or "٤٢". */
    fun localizedDigits(number: Int): String

    /**
     * A 24-hour clock time using the platform's own locale formatting and digit set.
     *
     * The hour is *not* zero-padded and the minute always is — "3:42", "٣:٤٢", "15:07". That is
     * the shape the spec quotes for a Libyan user and the shape Today already showed before this
     * task, so migrating to CLDR digits does not silently reformat every English screen. (The
     * brief's sketch padded both fields; padding the hour would have turned every existing
     * "3:42" into "03:42".)
     */
    fun clockTime(hour: Int, minute: Int): String
}

expect fun createPlatformFormat(): PlatformFormat

/**
 * Built once per process rather than per composable: constructing a locale-aware number
 * formatter is not free, and every screen that shows a time needs one. Provided explicitly in
 * `App`, but the default keeps previews and any stray call site correct.
 */
val LocalPlatformFormat = staticCompositionLocalOf { createPlatformFormat() }

/**
 * A locale-free [PlatformFormat]: British English wording, Western digits. It is the default for
 * the pure view models so their tests read the same on any machine, and the fallback wherever a
 * device format is genuinely unavailable. The app always passes the real one.
 */
object EnglishPlatformFormat : PlatformFormat {
    override fun languageTag(): String = "en"
    override fun localizedDigits(number: Int): String = number.toString()
    override fun clockTime(hour: Int, minute: Int): String =
        "$hour:${minute.toString().padStart(2, '0')}"
}
