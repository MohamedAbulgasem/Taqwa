package world.taqwa.app.i18n

import java.text.NumberFormat
import java.util.Locale

private class AndroidPlatformFormat : PlatformFormat {
    private val locale: Locale = Locale.getDefault()

    // NumberFormat is not thread-safe, but every call here is on the UI thread and one instance
    // avoids re-resolving the locale's numbering system on every tick of the countdown.
    private val plainFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    private val twoDigitFormat = NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = 2
        isGroupingUsed = false
    }

    override fun languageTag(): String = locale.toLanguageTag()

    override fun localizedDigits(number: Int): String = plainFormat.format(number)

    override fun clockTime(hour: Int, minute: Int): String =
        "${plainFormat.format(hour)}:${twoDigitFormat.format(minute)}"
}

actual fun createPlatformFormat(): PlatformFormat = AndroidPlatformFormat()
