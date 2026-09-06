package world.taqwa.app.i18n

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

private class IosPlatformFormat : PlatformFormat {

    private val currentLocale = NSLocale.currentLocale

    private val plainFormatter = NSNumberFormatter().apply {
        setLocale(currentLocale)
        setNumberStyle(NSNumberFormatterDecimalStyle)
        setUsesGroupingSeparator(false)
    }

    private val twoDigitFormatter = NSNumberFormatter().apply {
        setLocale(currentLocale)
        setNumberStyle(NSNumberFormatterDecimalStyle)
        setUsesGroupingSeparator(false)
        setMinimumIntegerDigits(2u)
    }

    /**
     * The UI language comes from `preferredLanguages` — that is what decides which `values-ar`
     * strings Compose loads and therefore whether the layout mirrors. But a user whose language
     * list is a bare "ar" still has a region, and the region is what decides the digit set, so
     * the region from `currentLocale` is folded in when the preferred tag carries none. Without
     * this, launching the simulator with `-AppleLanguages "(ar)" -AppleLocale "ar_EG"` would
     * report "ar" and miss the Arabic-Indic default that same device actually formats with.
     */
    private val tag: String = run {
        val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
        if (preferred.contains('-')) {
            preferred
        } else {
            val region = currentLocale.objectForKey(NSLocaleCountryCode) as? String
            if (region.isNullOrBlank()) preferred else "$preferred-$region"
        }
    }

    override fun languageTag(): String = tag

    override fun localizedDigits(number: Int): String =
        plainFormatter.stringFromNumber(NSNumber(int = number)) ?: number.toString()

    override fun clockTime(hour: Int, minute: Int): String {
        val h = plainFormatter.stringFromNumber(NSNumber(int = hour)) ?: hour.toString()
        val m = twoDigitFormatter.stringFromNumber(NSNumber(int = minute))
            ?: minute.toString().padStart(2, '0')
        return "$h:$m"
    }
}

actual fun createPlatformFormat(): PlatformFormat = IosPlatformFormat()
