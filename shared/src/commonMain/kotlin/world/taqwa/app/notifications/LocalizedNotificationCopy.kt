package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming

/**
 * The localised replacement for [EnglishNotificationCopy]. No platform code ever localises a
 * notification: this bakes the correct language, and the Arabic-alone naming rule, in at
 * schedule time — which is the only moment the app is guaranteed to be running.
 */
class LocalizedNotificationCopy(private val format: PlatformFormat) : NotificationCopy {

    private val isArabic = PrayerNaming.isArabicLanguage(format.languageTag())

    private fun name(prayer: Prayer) =
        PrayerNaming.display(prayer, format.languageTag(), PrayerNaming.englishName(prayer))

    override fun title(prayer: Prayer, kind: NotificationKind): String = name(prayer)

    override fun body(
        prayer: Prayer,
        kind: NotificationKind,
        clockTime: String,
        minutesBefore: Int,
    ): String {
        val n = name(prayer)
        return if (isArabic) {
            when (kind) {
                NotificationKind.PRAYER -> "حان الآن وقت صلاة $n · $clockTime"
                // "بعد ١٠ دقائق" — Arabic counts 3–10 with the plural, 11+ with the singular.
                NotificationKind.REMINDER ->
                    "$n بعد ${format.localizedDigits(minutesBefore)} " +
                        "${if (minutesBefore in 3..10) "دقائق" else "دقيقة"} · $clockTime"
            }
        } else {
            when (kind) {
                NotificationKind.PRAYER -> "It is time for $n · $clockTime"
                NotificationKind.REMINDER -> "$n in $minutesBefore minutes · $clockTime"
            }
        }
    }
}
