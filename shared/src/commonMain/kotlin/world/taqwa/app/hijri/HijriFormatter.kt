package world.taqwa.app.hijri

private val MONTHS = listOf(
    "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
    "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
    "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
)

object HijriFormatter {
    fun monthNameEnglish(month: Int): String =
        MONTHS.getOrNull(month - 1) ?: error("Hijri month out of range: $month")

    fun format(date: HijriDate): String =
        "${date.day} ${monthNameEnglish(date.month)} ${date.year}"
}
