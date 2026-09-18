package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.UiLanguage

/**
 * The localised replacement for [EnglishNotificationCopy]. No platform code ever localises a
 * notification: this bakes the right language, and the naming rule that goes with it, in at
 * schedule time — the only moment the app is guaranteed to be running.
 *
 * Templates rather than resources for the reason the class exists at all: a boot receiver has no
 * composition to look a string up in. `{prayer}`, `{time}` and `{minutes}` are the only holes.
 */
class LocalizedNotificationCopy(private val format: PlatformFormat) : NotificationCopy {

    private class Words(
        val prayerBody: String,
        val reminderBody: String,
        /** The night prayer's own name, and what its notification says: `{prayer}` is Fajr. */
        val tahajjud: String,
        val tahajjudBody: String,
        val silent: String,
        val notification: String,
        val takbir: String,
        val adhan: String,
    )

    private val language = UiLanguage.of(format.languageTag())

    private val words: Words = WORDS.getValue(language)

    private fun name(prayer: Prayer) =
        PrayerNaming.display(prayer, format.languageTag(), PrayerNaming.name(prayer, format.languageTag()))

    override fun title(prayer: Prayer, kind: NotificationKind): String =
        if (kind == NotificationKind.TAHAJJUD) words.tahajjud else name(prayer)

    override fun body(
        prayer: Prayer,
        kind: NotificationKind,
        clockTime: String,
        minutesBefore: Int,
    ): String {
        val n = name(prayer)
        return when (kind) {
            NotificationKind.PRAYER -> words.prayerBody
                .replace("{prayer}", n)
                .replace("{time}", clockTime)
            NotificationKind.REMINDER -> reminder(n, minutesBefore, clockTime)
            // Fajr by its one name in this language, not the paired «Fajr · الفجر» a title
            // wears: inside a sentence that already has a separator the pair reads as a list.
            NotificationKind.TAHAJJUD -> words.tahajjudBody
                .replace("{prayer}", PrayerNaming.name(prayer, format.languageTag()))
                .replace("{time}", clockTime)
        }
    }

    private fun reminder(prayerName: String, minutesBefore: Int, clockTime: String): String {
        val minutes = format.localizedDigits(minutesBefore)
        val body = if (language == UiLanguage.ARABIC) {
            // "بعد ١٠ دقائق" — Arabic counts 3–10 with the plural, 11+ with the singular.
            "{prayer} بعد {minutes} ${if (minutesBefore in 3..10) "دقائق" else "دقيقة"} · {time}"
        } else {
            words.reminderBody
        }
        return body.replace("{prayer}", prayerName).replace("{minutes}", minutes).replace("{time}", clockTime)
    }

    // Not read from string resources: a channel is created from the Android scheduler, which can
    // be running inside a boot receiver where no Compose resource lookup exists — the same reason
    // title and body are baked in here.
    override fun channelName(prayer: Prayer, sound: PrayerSound, kind: NotificationKind): String {
        val soundName = when (sound) {
            PrayerSound.SILENT -> words.silent
            PrayerSound.NOTIFICATION -> words.notification
            PrayerSound.TAKBIR -> words.takbir
            PrayerSound.ADHAN -> words.adhan
        }
        return "${title(prayer, kind)} · $soundName"
    }

    private companion object {
        /** Kept in step with each language's `sound_*` strings by hand. */
        val WORDS: Map<UiLanguage, Words> = mapOf(
            UiLanguage.ENGLISH to Words(
                prayerBody = "It is time for {prayer} · {time}",
                reminderBody = "{prayer} in {minutes} minutes · {time}",
                tahajjud = "Tahajjud",
                tahajjudBody = "The last third of the night has begun · {prayer} at {time}",
                silent = "Silent", notification = "Notification", takbir = "Takbir", adhan = "Adhan",
            ),
            UiLanguage.ARABIC to Words(
                prayerBody = "حان الآن وقت صلاة {prayer} · {time}",
                reminderBody = "{prayer} بعد {minutes} دقيقة · {time}",
                tahajjud = "التهجد",
                tahajjudBody = "بدأ الثلث الأخير من الليل · {prayer} {time}",
                silent = "صامت", notification = "نغمة التنبيه", takbir = "تكبير", adhan = "أذان",
            ),
            UiLanguage.FRENCH to Words(
                prayerBody = "C’est l’heure de la prière : {prayer} · {time}",
                reminderBody = "{prayer} dans {minutes} minutes · {time}",
                tahajjud = "Tahajjud",
                tahajjudBody = "Le dernier tiers de la nuit a commencé · {prayer} à {time}",
                silent = "Silencieux", notification = "Tonalité", takbir = "Takbir", adhan = "Adhan",
            ),
            UiLanguage.TURKISH to Words(
                prayerBody = "{prayer} vakti girdi · {time}",
                reminderBody = "{prayer} vaktine {minutes} dakika · {time}",
                tahajjud = "Teheccüd",
                tahajjudBody = "Gecenin son üçte biri başladı · {prayer} {time}",
                silent = "Sessiz", notification = "Bildirim", takbir = "Tekbir", adhan = "Ezan",
            ),
            UiLanguage.INDONESIAN to Words(
                prayerBody = "Telah masuk waktu {prayer} · {time}",
                reminderBody = "{prayer} {minutes} menit lagi · {time}",
                tahajjud = "Tahajud",
                tahajjudBody = "Sepertiga malam terakhir telah tiba · {prayer} pukul {time}",
                silent = "Senyap", notification = "Nada notifikasi", takbir = "Takbir", adhan = "Azan",
            ),
            UiLanguage.URDU to Words(
                prayerBody = "{prayer} کا وقت ہو گیا · {time}",
                reminderBody = "{prayer} میں {minutes} منٹ باقی · {time}",
                tahajjud = "تہجد",
                tahajjudBody = "رات کا آخری تہائی حصہ شروع ہو گیا · {prayer} {time}",
                silent = "خاموش", notification = "اطلاعی ٹون", takbir = "تکبیر", adhan = "اذان",
            ),
            UiLanguage.BENGALI to Words(
                prayerBody = "{prayer} নামাজের সময় হয়েছে · {time}",
                reminderBody = "{minutes} মিনিট পর {prayer} · {time}",
                tahajjud = "তাহাজ্জুদ",
                tahajjudBody = "রাতের শেষ তৃতীয়াংশ শুরু হয়েছে · {prayer} {time}",
                silent = "নীরব", notification = "নোটিফিকেশন সুর", takbir = "তাকবীর", adhan = "আযান",
            ),
        )
    }
}
