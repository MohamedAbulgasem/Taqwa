package world.taqwa.app.i18n

/**
 * NOTE (Task 23): see the note on `PrayerNaming.kt` — Task 22 owns this interface's real home;
 * this is a verbatim stand-in copied from Task 22's brief so Task 23's widget mirror writer could
 * compile and be tested. Expect a merge conflict against Task 22's own commit.
 */
interface PlatformFormat {
    fun languageTag(): String
    /** Locale-correct digits for a plain non-negative integer, e.g. "42" or "٤٢". */
    fun localizedDigits(number: Int): String
    /** A 24-hour clock time using the platform's own locale formatting and digit set. */
    fun clockTime(hour: Int, minute: Int): String
}

expect fun createPlatformFormat(): PlatformFormat
