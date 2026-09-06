package world.taqwa.app.i18n

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

@OptIn(ExperimentalForeignApi::class)
private class IosPlatformFormat : PlatformFormat {
    private val locale = NSLocale.currentLocale
    private val twoDigitFormatter = NSNumberFormatter().apply {
        locale = this@IosPlatformFormat.locale
        minimumIntegerDigits = 2UL
    }
    private val plainFormatter = NSNumberFormatter().apply { locale = this@IosPlatformFormat.locale }

    override fun languageTag(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

    override fun localizedDigits(number: Int): String =
        plainFormatter.stringFromNumber(NSNumber(int = number)) ?: number.toString()

    override fun clockTime(hour: Int, minute: Int): String {
        val h = twoDigitFormatter.stringFromNumber(NSNumber(int = hour)) ?: hour.toString()
        val m = twoDigitFormatter.stringFromNumber(NSNumber(int = minute)) ?: minute.toString()
        return "$h:$m"
    }
}

actual fun createPlatformFormat(): PlatformFormat = IosPlatformFormat()
