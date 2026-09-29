package world.taqwa.timetables

import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod

/**
 * "The way {authority} calculates" in the app's own words: `AboutTimesScreen.methodDescription`
 * without Compose — the same parts, the same strings, the same order, joined with the language's
 * list comma. Generated from the method, never hand-written per authority. If the app's function
 * and this one ever differ, the app is right.
 */
object MethodWords {
    /** [TimetableMethod.horizonDeg]'s own default (the plain sea-level horizon). */
    private const val DEFAULT_HORIZON_DEG = -0.8333

    /** The list comma of the language's script: "، " under Arabic and Urdu, ", " otherwise. */
    fun listComma(language: String): String = if (UiLanguage.of(language).arabicScript) "، " else ", "

    fun describe(strings: AppStrings, language: String, method: TimetableMethod, f: Formats): String {
        val comma = listComma(language)
        val parts = mutableListOf<String>()

        // Diyanet, Oman and Jamiatul Ulama take the sun's position once a day, not at each event.
        if (method.sunModel == SunModel.DAILY_0H_UT) parts += strings.get(language, "about_method_daily_sun")

        // Fajr: a fixed angle, or a curve by day of year (ruling R28), read honestly off the method.
        val fajr = method.fajrAngleByDayOfYear?.let { it.min() to it.max() } ?: (method.fajrAngle to method.fajrAngle)
        parts += if (fajr.first != fajr.second) {
            strings.format(language, "about_method_fajr_curve", angle(fajr.first, f), angle(fajr.second, f))
        } else {
            strings.format(language, "about_method_fajr_angle", angle(fajr.first, f))
        }

        parts += when (val isha = method.isha) {
            is IshaRule.Angle -> {
                val range = method.ishaAngleByDayOfYear?.let { it.min() to it.max() } ?: (isha.degrees to isha.degrees)
                if (range.first != range.second) {
                    strings.format(language, "about_method_isha_curve", angle(range.first, f), angle(range.second, f))
                } else {
                    strings.format(language, "about_method_isha_angle", angle(isha.degrees, f))
                }
            }
            is IshaRule.AfterMaghrib -> strings.format(language, "about_method_isha_after_maghrib", f.digits(isha.minutes))
        }

        // A horizon deeper than sea level, or depth added to the twilight angles (INM, Habous, JAKIM's highlands, Kemenag, Jordan).
        if (method.horizonDeg != DEFAULT_HORIZON_DEG || method.twilightDipDeg != 0.0) {
            parts += strings.get(language, "about_method_horizon_dip")
        }

        val minutes = Prayer.entries.mapNotNull { prayer ->
            method.authorityMinutes[prayer].takeIf { it != 0 }?.let { "${strings.prayer(language, prayer)} ${signed(it, f)}" }
        }
        if (minutes.isNotEmpty()) parts += strings.format(language, "about_method_minutes_note", minutes.joinToString(comma))

        parts += strings.get(language, "about_method_rounding_note")
        return parts.joinToString(comma)
    }

    private fun signed(minutes: Int, f: Formats): String {
        val digits = f.digits(if (minutes < 0) -minutes else minutes)
        return if (minutes < 0) "−$digits" else "+$digits"
    }

    /**
     * An angle in whole degrees where it is one (18°), else one decimal (19.5°), never rounded
     * away; the digits localized, the point the literal "." (the app's `formatAngle`).
     */
    private fun angle(value: Double, f: Formats): String {
        val sign = if (value < 0) "−" else ""
        val tenths = kotlin.math.round(kotlin.math.abs(value) * 10).toInt()
        val whole = tenths / 10
        val fraction = tenths % 10
        val digits = if (fraction == 0) f.digits(whole) else "${f.digits(whole)}.${f.digits(fraction)}"
        return "$sign$digits"
    }
}
