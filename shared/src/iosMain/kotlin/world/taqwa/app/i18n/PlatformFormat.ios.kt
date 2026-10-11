package world.taqwa.app.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.preferredLanguages

private class IosPlatformFormat(private val clockFormat: ClockFormat) : PlatformFormat {

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

    // The day-period symbols of the *language* locale, not `currentLocale`: the marker is a
    // language question ("pm" in British English, "PM" in American), while the digits above
    // are a region question (spec §1.2).
    private val amPm: Pair<String, String> = NSDateFormatter().run {
        setLocale(NSLocale(localeIdentifier = tag))
        (AMSymbol ?: "AM") to (PMSymbol ?: "PM")
    }
    private val markerFirst = TwelveHourClock.markerFirst(tag)

    // Explicitly Gregorian on both the components and the formatter: a user who has switched
    // their iPhone to the Umm al-Qura calendar would otherwise get a Hijri date here, next to the
    // Hijri date the app already prints.
    private val gregorian = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)
    private val longDateFormatter = NSDateFormatter().apply {
        setLocale(currentLocale)
        gregorian?.let { setCalendar(it) }
        setLocalizedDateFormatFromTemplate("dMMMMy")
    }

    override fun languageTag(): String = tag

    override fun longDate(date: LocalDate): String {
        val components = NSDateComponents().apply {
            setYear(date.year.toLong())
            setMonth(date.month.number.toLong())
            setDay(date.day.toLong())
        }
        val nsDate = gregorian?.dateFromComponents(components)
            ?: return EnglishPlatformFormat.longDate(date)
        return longDateFormatter.stringFromDate(nsDate)
    }

    override fun localizedDigits(number: Int): String =
        plainFormatter.stringFromNumber(NSNumber(int = number)) ?: number.toString()

    // NSLocale returns the name exactly as that locale writes it — lower case in French ("anglais"),
    // for one — rather than titlecasing it for a list row the way Android's own lookup has to. That
    // is also why this file overrides only languageName: the interface's default languageNameInSentence
    // (= languageName(code)) already reads correctly inside a sentence, with no fix-up to make. A
    // code Foundation does not know returns null or the code itself, which hands the seven bundled
    // languages to the English map rather than showing a bare "bn".
    override fun languageName(code: String): String {
        val name = currentLocale.localizedStringForLanguageCode(code)
        if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) {
            return EnglishPlatformFormat.languageName(code)
        }
        return name
    }

    override fun clockTime(hour: Int, minute: Int): String {
        val m = twoDigitFormatter.stringFromNumber(NSNumber(int = minute)) ?: minute.toString().padStart(2, '0')
        if (clockFormat == ClockFormat.TWENTY_FOUR_HOUR) {
            val h = plainFormatter.stringFromNumber(NSNumber(int = hour)) ?: hour.toString()
            return "$h:$m"
        }
        val (hour12, pm) = twelveHourParts(hour)
        val h = plainFormatter.stringFromNumber(NSNumber(int = hour12)) ?: hour12.toString()
        return TwelveHourClock.render(h, m, if (pm) amPm.second else amPm.first, markerFirst)
    }
}

actual fun createPlatformFormat(clockFormat: ClockFormat): PlatformFormat = IosPlatformFormat(clockFormat)
