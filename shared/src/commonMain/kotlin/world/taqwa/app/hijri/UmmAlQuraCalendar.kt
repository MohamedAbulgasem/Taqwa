package world.taqwa.app.hijri

import kotlinx.datetime.LocalDate

data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Tabular civil ("Kuwaiti") Hijri calendar. An arithmetic approximation of Umm al-Qura that can
 * differ from local moonsighting by a day, which is why the user is given a plus or minus one
 * day offset in settings.
 */
object UmmAlQuraCalendar {

    private fun gregorianToJulianDay(y: Int, m: Int, d: Int): Long {
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return (d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045)
    }

    fun fromGregorian(date: LocalDate): HijriDate {
        val jd = gregorianToJulianDay(date.year, date.monthNumber, date.dayOfMonth)
        val l0 = jd - 1948440L + 10632L
        val n = (l0 - 1) / 10631L
        var l = l0 - 10631L * n + 354L
        val j = ((10985L - l) / 5316L) * ((50L * l) / 17719L) + (l / 5670L) * ((43L * l) / 15238L)
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val month = ((24L * l) / 709L).toInt()
        val day = (l - (709L * month) / 24L).toInt()
        val year = (30L * n + j - 30L).toInt()
        return HijriDate(year = year, month = month, day = day)
    }
}
