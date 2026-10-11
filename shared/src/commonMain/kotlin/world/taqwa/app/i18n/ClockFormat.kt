package world.taqwa.app.i18n

/** How clock times are written. 24-hour is the default and what the site prints (spec §1.1). */
enum class ClockFormat { TWENTY_FOUR_HOUR, TWELVE_HOUR }

/** The 12-hour dial's hour for a 0..23 hour, and whether it is after noon: 0 → 12 am, 12 → 12 pm. */
fun twelveHourParts(hour: Int): Pair<Int, Boolean> {
    val h = hour % 12
    return (if (h == 0) 12 else h) to (hour >= 12)
}

/**
 * The pure half of 12-hour rendering (spec §1.2). The digits and the day-period marker come
 * from the platform; this decides only how they are joined: U+00A0 rather than CLDR's U+202F,
 * which Manrope has no glyph for, and the marker before the digits only for Turkish, the one
 * interface language whose CLDR pattern is "a h:mm".
 */
object TwelveHourClock {
    private const val NO_BREAK_SPACE = "\u00A0"

    fun markerFirst(languageTag: String): Boolean =
        languageTag.substringBefore('-').substringBefore('_').lowercase() == "tr"

    fun render(hourText: String, minuteText: String, marker: String, markerFirst: Boolean): String =
        if (markerFirst) "$marker$NO_BREAK_SPACE$hourText:$minuteText" else "$hourText:$minuteText$NO_BREAK_SPACE$marker"
}
