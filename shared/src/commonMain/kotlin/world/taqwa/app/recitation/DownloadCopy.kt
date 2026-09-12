package world.taqwa.app.recitation

/**
 * The words a download notification is made of, in the two languages a notification can be baked
 * for, and nothing else.
 *
 * Same reasoning as `LocalizedNotificationCopy` for prayers: no platform code ever localises a
 * notification. A download worker can be started by WorkManager into a process with no Activity,
 * no composition and therefore no Compose resource lookup, so the language is decided while the
 * app is still running — at enqueue time — and travels with the work.
 */
object DownloadCopy {

    /** The low-importance channel every download notification is posted into. */
    fun channelName(arabic: Boolean): String = if (arabic) "التنزيلات" else "Downloads"

    /** "Al-Baqarah · 9.3 of 58.2 MB", "البقرة · ٩٫٣ من ٥٨٫٢ م.ب". */
    fun progress(surahName: String, bytes: Long, total: Long, arabic: Boolean): String {
        val done = megabytes(bytes, arabic)
        val whole = megabytes(total, arabic)
        val unit = if (arabic) "م.ب" else "MB"
        val of = if (arabic) "من" else "of"
        return "$surahName · $done $of $whole $unit"
    }

    /** "Mishary Rashid Alafasy · 12 of 114 surahs", "مشاري العفاسي · ١٢ من ١١٤ سورة". */
    fun batch(reciterName: String, done: Int, total: Int, arabic: Boolean): String {
        val d = digits(done.toString(), arabic)
        val t = digits(total.toString(), arabic)
        return if (arabic) "$reciterName · $d من $t سورة" else "$reciterName · $d of $t surahs"
    }

    /**
     * Megabytes to one decimal, without the unit — the unit is printed once for the pair, so the
     * line reads "9.3 of 58.2 MB" rather than "9.3 MB of 58.2 MB".
     *
     * Rounded rather than truncated, and no platform number formatter: this runs in a worker that
     * may have no locale services worth the call, and the shape is fixed anyway.
     */
    fun megabytes(bytes: Long, arabic: Boolean): String {
        val tenths = (bytes * 10 + MEGABYTE / 2) / MEGABYTE
        val plain = "${tenths / 10}.${tenths % 10}"
        return digits(plain, arabic)
    }

    /** Arabic-Indic digits and the Arabic decimal separator, or the string unchanged. */
    private fun digits(text: String, arabic: Boolean): String {
        if (!arabic) return text
        return buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        in '0'..'9' -> ARABIC_ZERO + (c - '0')
                        '.' -> ARABIC_DECIMAL
                        else -> c
                    }
                )
            }
        }
    }

    private const val MEGABYTE = 1024L * 1024L
    private const val ARABIC_ZERO = '٠'
    private const val ARABIC_DECIMAL = '٫'
}
