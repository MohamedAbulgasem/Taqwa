package world.taqwa.app.i18n

import world.taqwa.app.domain.HighLatitudePreference

/**
 * Today's high-latitude note, in every interface language.
 *
 * It lives in Kotlin rather than `strings.xml` for the same reason [world.taqwa.app.notifications.LocalizedNotificationCopy]
 * does: the sentence is assembled by a plain, non-composable view model — which is also what
 * makes it testable without a resource loader — and the polar case splices a rule name into the
 * middle of it, which a flat resource string cannot do without a second parallel template per
 * rule. Every string a *composable* renders is in the resources.
 *
 * The seasonal wording is deliberate. The one-seventh and twilight-angle rules bind in **summer**,
 * when a short night would otherwise put Fajr absurdly early — not in winter. The polar sentence
 * leads with the fact that EVERY time on screen came from a different latitude, because adhan2
 * could not compute the day at all, and only then names the rule that produced Fajr and Isha.
 */
object HighLatitudeCopy {

    private class Words(
        val seventh: String,
        val twilight: String,
        val middle: String,
        /** No full stop: [polarRule] may follow it, and the stop is added after. */
        val polarLead: String,
        /** With `{rule}` where the rule's name goes. */
        val polarRule: String,
        val ruleSeventh: String,
        val ruleTwilight: String,
        val ruleMiddle: String,
    )

    private val WORDS: Map<UiLanguage, Words> = mapOf(
        UiLanguage.ENGLISH to Words(
            seventh = "Nights are short here at this time of year. Fajr and Isha use the one-seventh rule.",
            twilight = "Nights are short here at this time of year. Fajr and Isha use the twilight angle rule.",
            middle = "Fajr and Isha use the middle of the night rule at this latitude.",
            polarLead = "The sun does not rise or set here today. All times are calculated for the nearest latitude where it does",
            polarRule = ", with Fajr and Isha using {rule}",
            ruleSeventh = "the one-seventh rule",
            ruleTwilight = "the twilight angle rule",
            ruleMiddle = "the middle of the night rule",
        ),
        UiLanguage.ARABIC to Words(
            seventh = "الليل قصير هنا في هذا الوقت من السنة. يُحسب الفجر والعشاء بقاعدة سُبع الليل.",
            twilight = "الليل قصير هنا في هذا الوقت من السنة. يُحسب الفجر والعشاء بقاعدة زاوية الشفق.",
            middle = "يُحسب الفجر والعشاء بقاعدة منتصف الليل عند خط العرض هذا.",
            polarLead = "لا تشرق الشمس ولا تغرب هنا اليوم. حُسبت كل المواقيت لأقرب خط عرض تشرق فيه وتغرب",
            polarRule = "، مع حساب الفجر والعشاء بـ{rule}",
            ruleSeventh = "قاعدة سُبع الليل",
            ruleTwilight = "قاعدة زاوية الشفق",
            ruleMiddle = "قاعدة منتصف الليل",
        ),
        UiLanguage.FRENCH to Words(
            seventh = "Les nuits sont courtes ici à cette période de l’année. Le Fajr et l’Isha suivent la règle du septième de la nuit.",
            twilight = "Les nuits sont courtes ici à cette période de l’année. Le Fajr et l’Isha suivent la règle de l’angle du crépuscule.",
            middle = "À cette latitude, le Fajr et l’Isha suivent la règle du milieu de la nuit.",
            polarLead = "Le soleil ne se lève ni ne se couche ici aujourd’hui. Tous les horaires sont calculés pour la latitude la plus proche où il le fait",
            polarRule = ", le Fajr et l’Isha suivant {rule}",
            ruleSeventh = "la règle du septième de la nuit",
            ruleTwilight = "la règle de l’angle du crépuscule",
            ruleMiddle = "la règle du milieu de la nuit",
        ),
        UiLanguage.TURKISH to Words(
            seventh = "Bu mevsimde geceler burada kısa. Sabah ve Yatsı gecenin yedide biri kuralıyla hesaplanır.",
            twilight = "Bu mevsimde geceler burada kısa. Sabah ve Yatsı tan açısı kuralıyla hesaplanır.",
            middle = "Bu enlemde Sabah ve Yatsı gece yarısı kuralıyla hesaplanır.",
            polarLead = "Güneş bugün burada doğmuyor ve batmıyor. Tüm vakitler, güneşin doğup battığı en yakın enleme göre hesaplandı",
            polarRule = "; Sabah ve Yatsı için {rule} kullanıldı",
            ruleSeventh = "gecenin yedide biri kuralı",
            ruleTwilight = "tan açısı kuralı",
            ruleMiddle = "gece yarısı kuralı",
        ),
        UiLanguage.INDONESIAN to Words(
            seventh = "Malam di sini pendek pada waktu ini dalam setahun. Subuh dan Isya memakai aturan sepertujuh malam.",
            twilight = "Malam di sini pendek pada waktu ini dalam setahun. Subuh dan Isya memakai aturan sudut senja.",
            middle = "Subuh dan Isya memakai aturan pertengahan malam pada lintang ini.",
            polarLead = "Matahari tidak terbit atau terbenam di sini hari ini. Semua waktu dihitung untuk lintang terdekat yang mengalaminya",
            polarRule = ", dengan Subuh dan Isya memakai {rule}",
            ruleSeventh = "aturan sepertujuh malam",
            ruleTwilight = "aturan sudut senja",
            ruleMiddle = "aturan pertengahan malam",
        ),
        UiLanguage.URDU to Words(
            seventh = "سال کے اس حصے میں یہاں راتیں چھوٹی ہوتی ہیں۔ فجر اور عشاء کے لیے رات کے ساتویں حصے کا قاعدہ استعمال ہوتا ہے۔",
            twilight = "سال کے اس حصے میں یہاں راتیں چھوٹی ہوتی ہیں۔ فجر اور عشاء کے لیے شفق کے زاویے کا قاعدہ استعمال ہوتا ہے۔",
            middle = "اس عرض بلد پر فجر اور عشاء کے لیے نصف شب کا قاعدہ استعمال ہوتا ہے۔",
            polarLead = "آج یہاں سورج نہ طلوع ہوتا ہے نہ غروب۔ سب اوقات قریب ترین اُس عرض بلد کے لیے نکالے گئے ہیں جہاں ایسا ہوتا ہے",
            polarRule = "، اور فجر اور عشاء کے لیے {rule} استعمال ہوتا ہے",
            ruleSeventh = "رات کے ساتویں حصے کا قاعدہ",
            ruleTwilight = "شفق کے زاویے کا قاعدہ",
            ruleMiddle = "نصف شب کا قاعدہ",
        ),
        UiLanguage.BENGALI to Words(
            seventh = "বছরের এই সময়ে এখানে রাত ছোট। ফজর ও এশা রাতের এক-সপ্তমাংশের নিয়মে হিসাব করা হয়।",
            twilight = "বছরের এই সময়ে এখানে রাত ছোট। ফজর ও এশা গোধূলি কোণের নিয়মে হিসাব করা হয়।",
            middle = "এই অক্ষাংশে ফজর ও এশা রাতের মধ্যভাগের নিয়মে হিসাব করা হয়।",
            polarLead = "আজ এখানে সূর্য ওঠেও না, অস্তও যায় না। সব সময় নিকটতম সেই অক্ষাংশ ধরে হিসাব করা হয়েছে যেখানে সূর্য ওঠে ও অস্ত যায়",
            polarRule = ", আর ফজর ও এশায় {rule} ব্যবহার করা হয়েছে",
            ruleSeventh = "রাতের এক-সপ্তমাংশের নিয়ম",
            ruleTwilight = "গোধূলি কোণের নিয়ম",
            ruleMiddle = "রাতের মধ্যভাগের নিয়ম",
        ),
    )

    fun note(
        languageTag: String,
        polarFallback: Boolean,
        rule: HighLatitudePreference?,
    ): String? {
        val words = WORDS.getValue(UiLanguage.of(languageTag))
        return when {
            polarFallback -> polar(words, rule)
            rule == HighLatitudePreference.SEVENTH_OF_NIGHT -> words.seventh
            rule == HighLatitudePreference.TWILIGHT_ANGLE -> words.twilight
            rule != null -> words.middle
            else -> null
        }
    }

    private fun polar(words: Words, rule: HighLatitudePreference?): String = buildString {
        append(words.polarLead)
        ruleName(words, rule)?.let { append(words.polarRule.replace("{rule}", it)) }
        append(if (words === WORDS.getValue(UiLanguage.URDU)) "۔" else if (words === WORDS.getValue(UiLanguage.BENGALI)) "।" else ".")
    }

    private fun ruleName(words: Words, rule: HighLatitudePreference?): String? = when (rule) {
        HighLatitudePreference.SEVENTH_OF_NIGHT -> words.ruleSeventh
        HighLatitudePreference.TWILIGHT_ANGLE -> words.ruleTwilight
        HighLatitudePreference.MIDDLE_OF_NIGHT -> words.ruleMiddle
        else -> null
    }
}
