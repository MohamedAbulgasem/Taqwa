package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.isolatedClock

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
        /**
         * The reminder with one minute left, where the language's noun takes the singular. A
         * reminder clamped to the prayer before it (the planner's own rule) can say any count
         * under its lead, one included; the leads themselves (5, 10, 15, 30) never needed it.
         */
        val reminderBodyOne: String = reminderBody,
        /** The night prayer's own name, and what its notification says: `{prayer}` is Fajr. */
        val tahajjud: String,
        val tahajjudBody: String,
        /** The adhkar reminder's titles, each language's `adhkar_morning` / `adhkar_evening`. */
        val adhkarMorning: String,
        val adhkarEvening: String,
        /** `{time}` is where the window ends: sunrise for the morning, Maghrib for the evening. */
        val adhkarMorningBody: String,
        val adhkarEveningBody: String,
        /** The one channel both adhkar kinds post into, named as `notifications_adhkar` is. */
        val adhkarChannel: String,
        /** Duha's own name, which titles both its notifications and names its channel. */
        val duha: String,
        /** `{time}` is where Duha ends, ten minutes before noon. */
        val duhaBestBody: String,
        val duhaStartBody: String,
        /** `{minutes}` is the gap since the adhan; Arabic counts it in code instead (see [iqama]). */
        val iqamaBody: String,
        /** The gap of one minute, where the language's noun takes the singular; no gap on offer is one. */
        val iqamaBodyOne: String = iqamaBody,
        /** The one channel all five iqamas post into. */
        val iqamaChannel: String,
        val silent: String,
        val notification: String,
        val takbir: String,
        val adhan: String,
    )

    private val language = UiLanguage.of(format.languageTag())

    private val words: Words = WORDS.getValue(language)

    private fun name(prayer: Prayer) =
        PrayerNaming.display(prayer, format.languageTag(), PrayerNaming.name(prayer, format.languageTag()))

    override fun title(prayer: Prayer, kind: NotificationKind): String = when (kind) {
        NotificationKind.TAHAJJUD -> words.tahajjud
        NotificationKind.ADHKAR_MORNING -> words.adhkarMorning
        NotificationKind.ADHKAR_EVENING -> words.adhkarEvening
        NotificationKind.DUHA_BEST, NotificationKind.DUHA_START -> words.duha
        // The prayer's name with its usual pairing, as the adhan's own title is.
        NotificationKind.PRAYER, NotificationKind.REMINDER, NotificationKind.IQAMA -> name(prayer)
    }

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
                .replace("{time}", isolatedClock(clockTime))
            NotificationKind.REMINDER -> reminder(n, minutesBefore, clockTime)
            // Fajr by its one name in this language, not the paired «Fajr · الفجر» a title
            // wears: inside a sentence that already has a separator the pair reads as a list.
            NotificationKind.TAHAJJUD -> words.tahajjudBody
                .replace("{prayer}", PrayerNaming.name(prayer, format.languageTag()))
                .replace("{time}", isolatedClock(clockTime))
            NotificationKind.ADHKAR_MORNING -> words.adhkarMorningBody.replace("{time}", isolatedClock(clockTime))
            NotificationKind.ADHKAR_EVENING -> words.adhkarEveningBody.replace("{time}", isolatedClock(clockTime))
            NotificationKind.DUHA_BEST -> words.duhaBestBody.replace("{time}", isolatedClock(clockTime))
            NotificationKind.DUHA_START -> words.duhaStartBody.replace("{time}", isolatedClock(clockTime))
            NotificationKind.IQAMA -> iqama(minutesBefore)
        }
    }

    /** How long ago the adhan was, in [minutes]: the iqama reminder's body, which quotes no clock time. */
    private fun iqama(minutes: Int): String {
        val body = if (language == UiLanguage.ARABIC) {
            // "مضت ١٠ دقائق على الأذان": as the before-prayer reminder counts, 3–10 take the
            // plural and 11 and up the singular; one and two are said without a numeral.
            when (minutes) {
                1 -> "تذكير الإقامة · مضت دقيقة على الأذان"
                2 -> "تذكير الإقامة · مضت دقيقتان على الأذان"
                else -> "تذكير الإقامة · مضت {minutes} ${if (minutes in 3..10) "دقائق" else "دقيقة"} على الأذان"
            }
        } else if (minutes == 1) {
            words.iqamaBodyOne
        } else {
            words.iqamaBody
        }
        return body.replace("{minutes}", format.localizedDigits(minutes))
    }

    private fun reminder(prayerName: String, minutesBefore: Int, clockTime: String): String {
        val minutes = format.localizedDigits(minutesBefore)
        val body = if (language == UiLanguage.ARABIC) {
            // "بعد ١٠ دقائق" — Arabic counts 3–10 with the plural, 11+ with the singular; one and
            // two are said without a numeral, the singular and the dual.
            when (minutesBefore) {
                1 -> "{prayer} بعد دقيقة · {time}"
                2 -> "{prayer} بعد دقيقتين · {time}"
                else -> "{prayer} بعد {minutes} ${if (minutesBefore in 3..10) "دقائق" else "دقيقة"} · {time}"
            }
        } else if (minutesBefore == 1) {
            words.reminderBodyOne
        } else {
            words.reminderBody
        }
        return body.replace("{prayer}", prayerName).replace("{minutes}", minutes).replace("{time}", isolatedClock(clockTime))
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
        // Both adhkar kinds post into one channel (NotificationChannels), so it is named for both:
        // "Morning adhkar · Notification" over the evening's notifications would be wrong half the day.
        // All five iqamas share one channel too, so it is named for the reminder, not for a prayer.
        val channel = when {
            kind.isAdhkar -> words.adhkarChannel
            kind == NotificationKind.IQAMA -> words.iqamaChannel
            else -> title(prayer, kind)
        }
        return "$channel · $soundName"
    }

    private companion object {
        /** Kept in step with each language's `sound_*` strings by hand. */
        val WORDS: Map<UiLanguage, Words> = mapOf(
            UiLanguage.ENGLISH to Words(
                prayerBody = "It is time for {prayer} · {time}",
                reminderBody = "{prayer} in {minutes} minutes · {time}",
                reminderBodyOne = "{prayer} in {minutes} minute · {time}",
                tahajjud = "Tahajjud",
                tahajjudBody = "The last third of the night has begun · {prayer} at {time}",
                adhkarMorning = "Morning adhkar",
                adhkarEvening = "Evening adhkar",
                adhkarMorningBody = "Until sunrise · {time}",
                adhkarEveningBody = "Until Maghrib · {time}",
                adhkarChannel = "Morning and evening adhkar",
                duha = "Duha",
                duhaBestBody = "The best time for Duha · until {time}",
                duhaStartBody = "Duha time is in · until {time}",
                iqamaBody = "Iqama reminder · {minutes} minutes after the adhan",
                iqamaBodyOne = "Iqama reminder · {minutes} minute after the adhan",
                iqamaChannel = "Iqama reminder",
                silent = "Silent", notification = "Notification", takbir = "Takbir", adhan = "Adhan",
            ),
            UiLanguage.ARABIC to Words(
                prayerBody = "حان الآن وقت صلاة {prayer} · {time}",
                reminderBody = "{prayer} بعد {minutes} دقيقة · {time}",
                tahajjud = "التهجد",
                tahajjudBody = "بدأ الثلث الأخير من الليل · {prayer} {time}",
                adhkarMorning = "أذكار الصباح",
                adhkarEvening = "أذكار المساء",
                adhkarMorningBody = "حتى الشروق · {time}",
                adhkarEveningBody = "حتى المغرب · {time}",
                adhkarChannel = "أذكار الصباح والمساء",
                duha = "الضحى",
                duhaBestBody = "أفضل وقت لصلاة الضحى · حتى {time}",
                duhaStartBody = "دخل وقت الضحى · حتى {time}",
                // Counted in code (iqama), as the reminder is: this is the 3–10 form.
                iqamaBody = "تذكير الإقامة · مضت {minutes} دقائق على الأذان",
                iqamaChannel = "تذكير الإقامة",
                silent = "صامت", notification = "نغمة التنبيه", takbir = "تكبير", adhan = "أذان",
            ),
            UiLanguage.FRENCH to Words(
                prayerBody = "C’est l’heure de la prière : {prayer} · {time}",
                reminderBody = "{prayer} dans {minutes} minutes · {time}",
                reminderBodyOne = "{prayer} dans {minutes} minute · {time}",
                tahajjud = "Tahajjud",
                tahajjudBody = "Le dernier tiers de la nuit a commencé · {prayer} à {time}",
                adhkarMorning = "Adhkar du matin",
                adhkarEvening = "Adhkar du soir",
                adhkarMorningBody = "Jusqu’au lever du soleil · {time}",
                adhkarEveningBody = "Jusqu’au Maghrib · {time}",
                adhkarChannel = "Adhkar du matin et du soir",
                duha = "Douha",
                duhaBestBody = "Le meilleur moment pour la Douha · jusqu’à {time}",
                duhaStartBody = "Le temps de la Douha est arrivé · jusqu’à {time}",
                iqamaBody = "Rappel de l’iqama · {minutes} minutes après l’adhan",
                iqamaBodyOne = "Rappel de l’iqama · {minutes} minute après l’adhan",
                iqamaChannel = "Rappel de l’iqama",
                silent = "Silencieux", notification = "Tonalité", takbir = "Takbir", adhan = "Adhan",
            ),
            UiLanguage.TURKISH to Words(
                prayerBody = "{prayer} vakti girdi · {time}",
                reminderBody = "{prayer} vaktine {minutes} dakika · {time}",
                tahajjud = "Teheccüd",
                tahajjudBody = "Gecenin son üçte biri başladı · {prayer} {time}",
                adhkarMorning = "Sabah zikirleri",
                adhkarEvening = "Akşam zikirleri",
                adhkarMorningBody = "Güneş doğana kadar · {time}",
                adhkarEveningBody = "Akşama kadar · {time}",
                adhkarChannel = "Sabah ve akşam zikirleri",
                duha = "Kuşluk",
                duhaBestBody = "Kuşluk namazı için en faziletli vakit · bitiş {time}",
                duhaStartBody = "Kuşluk vakti girdi · bitiş {time}",
                iqamaBody = "Kamet hatırlatması · ezandan {minutes} dakika sonra",
                iqamaChannel = "Kamet hatırlatması",
                silent = "Sessiz", notification = "Bildirim", takbir = "Tekbir", adhan = "Ezan",
            ),
            UiLanguage.INDONESIAN to Words(
                prayerBody = "Telah masuk waktu {prayer} · {time}",
                reminderBody = "{prayer} {minutes} menit lagi · {time}",
                tahajjud = "Tahajud",
                tahajjudBody = "Sepertiga malam terakhir telah tiba · {prayer} pukul {time}",
                adhkarMorning = "Zikir pagi",
                adhkarEvening = "Zikir petang",
                adhkarMorningBody = "Hingga matahari terbit · {time}",
                adhkarEveningBody = "Hingga Maghrib · {time}",
                adhkarChannel = "Zikir pagi dan petang",
                duha = "Dhuha",
                duhaBestBody = "Waktu terbaik untuk salat Dhuha · hingga pukul {time}",
                duhaStartBody = "Telah masuk waktu Dhuha · hingga pukul {time}",
                iqamaBody = "Pengingat iqamah · {minutes} menit setelah azan",
                iqamaChannel = "Pengingat iqamah",
                silent = "Senyap", notification = "Nada notifikasi", takbir = "Takbir", adhan = "Azan",
            ),
            UiLanguage.URDU to Words(
                prayerBody = "{prayer} کا وقت ہو گیا · {time}",
                reminderBody = "{prayer} میں {minutes} منٹ باقی · {time}",
                tahajjud = "تہجد",
                tahajjudBody = "رات کا آخری تہائی حصہ شروع ہو گیا · {prayer} {time}",
                adhkarMorning = "صبح کے اذکار",
                adhkarEvening = "شام کے اذکار",
                adhkarMorningBody = "طلوعِ آفتاب تک · {time}",
                adhkarEveningBody = "مغرب تک · {time}",
                adhkarChannel = "صبح و شام کے اذکار",
                duha = "چاشت",
                duhaBestBody = "نمازِ چاشت کا افضل وقت · {time} تک",
                duhaStartBody = "چاشت کا وقت شروع ہو گیا · {time} تک",
                iqamaBody = "اقامت کی یاد دہانی · اذان کے {minutes} منٹ بعد",
                iqamaChannel = "اقامت کی یاد دہانی",
                silent = "خاموش", notification = "اطلاعی ٹون", takbir = "تکبیر", adhan = "اذان",
            ),
            UiLanguage.BENGALI to Words(
                prayerBody = "{prayer} নামাজের সময় হয়েছে · {time}",
                reminderBody = "{minutes} মিনিট পর {prayer} · {time}",
                tahajjud = "তাহাজ্জুদ",
                tahajjudBody = "রাতের শেষ তৃতীয়াংশ শুরু হয়েছে · {prayer} {time}",
                adhkarMorning = "সকালের আযকার",
                adhkarEvening = "সন্ধ্যার আযকার",
                adhkarMorningBody = "সূর্যোদয় পর্যন্ত · {time}",
                adhkarEveningBody = "মাগরিব পর্যন্ত · {time}",
                adhkarChannel = "সকাল ও সন্ধ্যার আযকার",
                duha = "চাশত",
                duhaBestBody = "চাশতের নামাজের উত্তম সময় · {time} পর্যন্ত",
                duhaStartBody = "চাশতের সময় শুরু হয়েছে · {time} পর্যন্ত",
                iqamaBody = "ইকামতের রিমাইন্ডার · আজানের {minutes} মিনিট পর",
                iqamaChannel = "ইকামতের রিমাইন্ডার",
                silent = "নীরব", notification = "নোটিফিকেশন সুর", takbir = "তাকবীর", adhan = "আযান",
            ),
        )
    }
}
