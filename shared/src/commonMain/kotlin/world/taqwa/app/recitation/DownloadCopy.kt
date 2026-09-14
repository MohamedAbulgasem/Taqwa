package world.taqwa.app.recitation

import world.taqwa.app.i18n.UiLanguage

/**
 * How a number is written: the digit set and the decimal separator, which the platform decides
 * per locale (Egyptian Arabic ٩٫٣, Libyan Arabic 9.3, Bangladeshi Bengali ৯.৩, French 9,3).
 *
 * Chosen by the caller from the platform's own formatter — `localizedDigits(0)` says which digits
 * the rest of the app is already drawing — and the language, never from the text direction: that
 * put «٠٫٢» on a Libyan sheet directly above «64 ك.ب/ث».
 */
enum class NumberStyle(private val zero: Char, private val decimal: Char) {
    WESTERN('0', '.'),
    WESTERN_COMMA('0', ','),
    ARABIC_INDIC('٠', '٫'),
    BENGALI('০', '.'),
    ;

    fun apply(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '0'..'9' -> zero + (c - '0')
                    '.' -> decimal
                    else -> c
                },
            )
        }
    }

    companion object {
        /** [platformZero] is the platform's rendering of the digit zero for the current locale. */
        fun of(languageTag: String, platformZero: String): NumberStyle = when {
            platformZero == "٠" -> ARABIC_INDIC
            platformZero == "০" -> BENGALI
            UiLanguage.of(languageTag) in COMMA_LANGUAGES -> WESTERN_COMMA
            else -> WESTERN
        }

        private val COMMA_LANGUAGES = setOf(UiLanguage.FRENCH, UiLanguage.TURKISH, UiLanguage.INDONESIAN)
    }
}

/**
 * The words a download notification is made of, in every interface language, and nothing else.
 *
 * Same reasoning as `LocalizedNotificationCopy` for prayers: no platform code ever localises a
 * notification. A download worker can be started by WorkManager into a process with no Activity,
 * no composition and therefore no Compose resource lookup, so the language is decided while the
 * app is still running — at enqueue time — and travels with the work.
 */
object DownloadCopy {

    private class Words(
        val channelName: String,
        /** `{name}`, `{done}`, `{total}` and `{unit}`: "Al-Baqarah · 9.3 of 58.2 MB". */
        val progress: String,
        /** `{name}`, `{done}`, `{total}`: "Mishary Rashid Alafasy · 12 of 114 surahs". */
        val batch: String,
        val megabyteUnit: String,
    )

    private val WORDS: Map<UiLanguage, Words> = mapOf(
        UiLanguage.ENGLISH to Words("Downloads", "{name} · {done} of {total} {unit}", "{name} · {done} of {total} surahs", "MB"),
        UiLanguage.ARABIC to Words("التنزيلات", "{name} · {done} من {total} {unit}", "{name} · {done} من {total} سورة", "م.ب"),
        UiLanguage.FRENCH to Words("Téléchargements", "{name} · {done} sur {total} {unit}", "{name} · {done} sur {total} sourates", "Mo"),
        UiLanguage.TURKISH to Words("İndirmeler", "{name} · {done} / {total} {unit}", "{name} · {total} sureden {done}", "MB"),
        UiLanguage.INDONESIAN to Words("Unduhan", "{name} · {done} dari {total} {unit}", "{name} · {done} dari {total} surah", "MB"),
        UiLanguage.URDU to Words("ڈاؤن لوڈز", "{name} · {total} {unit} میں سے {done}", "{name} · {total} میں سے {done} سورتیں", "ایم بی"),
        UiLanguage.BENGALI to Words("ডাউনলোড", "{name} · {total} {unit}-এর মধ্যে {done}", "{name} · {total}টির মধ্যে {done}টি সূরা", "এমবি"),
    )

    private fun words(languageTag: String) = WORDS.getValue(UiLanguage.of(languageTag))

    /** The low-importance channel every download notification is posted into. */
    fun channelName(languageTag: String): String = words(languageTag).channelName

    /** "Al-Baqarah · 9.3 of 58.2 MB", "البقرة · ٩٫٣ من ٥٨٫٢ م.ب". */
    fun progress(surahName: String, bytes: Long, total: Long, languageTag: String, style: NumberStyle): String {
        val w = words(languageTag)
        return w.progress
            .replace("{name}", surahName)
            .replace("{done}", megabytes(bytes, style))
            .replace("{total}", megabytes(total, style))
            .replace("{unit}", w.megabyteUnit)
    }

    /** "Mishary Rashid Alafasy · 12 of 114 surahs", "مشاري العفاسي · ١٢ من ١١٤ سورة". */
    fun batch(reciterName: String, done: Int, total: Int, languageTag: String, style: NumberStyle): String =
        words(languageTag).batch
            .replace("{name}", reciterName)
            .replace("{done}", style.apply(done.toString()))
            .replace("{total}", style.apply(total.toString()))

    /**
     * Megabytes to one decimal, without the unit — the unit is printed once for the pair, so the
     * line reads "9.3 of 58.2 MB" rather than "9.3 MB of 58.2 MB".
     *
     * Rounded rather than truncated, and no platform number formatter: this runs in a worker that
     * may have no locale services worth the call, and the shape is fixed anyway.
     */
    fun megabytes(bytes: Long, style: NumberStyle): String {
        val tenths = megabyteTenths(bytes)
        return style.apply("${tenths / 10}.${tenths % 10}")
    }

    /**
     * Gigabytes to one decimal, without the unit. The whole Quran for one reciter is 0.6 to 1.6 GB
     * (spec §8) and a settings row that priced it at "1625.4 MB" would be asking the reader to
     * count digits.
     */
    fun gigabytes(bytes: Long, style: NumberStyle): String {
        val tenths = (bytes * 10 + GIGABYTE / 2) / GIGABYTE
        return style.apply("${tenths / 10}.${tenths % 10}")
    }

    /**
     * Which of the two units a size is printed in: gigabytes from **1000.0 MB up**, megabytes
     * below it.
     *
     * The threshold is asked of the *rounded* megabyte figure, not of the raw byte count, so the
     * unit and the number can never disagree: 1023.97 MB rounds to 1024.0, and a threshold on the
     * bytes alone would have printed "1024.0 MB" for it. 1000 rather than 1024 because the point
     * of switching is that four digits before the decimal point are unreadable, and that happens
     * at a thousand whichever way the unit is defined.
     */
    fun useGigabytes(bytes: Long): Boolean = megabyteTenths(bytes) >= 10_000L

    private fun megabyteTenths(bytes: Long): Long = (bytes * 10 + MEGABYTE / 2) / MEGABYTE

    private const val MEGABYTE = 1024L * 1024L
    private const val GIGABYTE = 1024L * 1024L * 1024L
}
