package world.taqwa.app.i18n

import java.text.NumberFormat
import java.util.Locale

private class AndroidPlatformFormat : PlatformFormat {
    private val locale: Locale = Locale.getDefault()
    private val twoDigitFormat = NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = 2
        isGroupingUsed = false
    }

    override fun languageTag(): String = locale.toLanguageTag()

    override fun localizedDigits(number: Int): String =
        NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }.format(number)

    override fun clockTime(hour: Int, minute: Int): String =
        "${twoDigitFormat.format(hour)}:${twoDigitFormat.format(minute)}"
}

actual fun createPlatformFormat(): PlatformFormat = AndroidPlatformFormat()
