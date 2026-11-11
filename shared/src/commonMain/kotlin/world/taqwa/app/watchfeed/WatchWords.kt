package world.taqwa.app.watchfeed

import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchCountdownPhrases
import world.taqwa.app.watch.WatchFaceWords
import world.taqwa.app.watch.WatchScreenWords

/**
 * The watch's own words that the phone has no string for, and the ones it must name outside a
 * composition: in plain Kotlin, like `PrayerNaming`, because a boot receiver that reschedules and
 * asks the watch has no composition to read a resource in. Jumuʿah is kept in step with each
 * language's `prayer_jumuah` by `WatchWordsResourcesTest`; the countdown has no phone string.
 *
 * The countdown is the face's short form, which fits its curves and windows: English and Arabic are
 * the spec's (1.3), and Mohamed read the Arabic at M8; French, Turkish, Indonesian, Urdu and
 * Bengali were read by native readers (plan 5 Task 11). The French, Indonesian and Bengali
 * under-a-minute forms are the shorter ones the Lead's call 7 chose, since the first drafts were
 * wider than the Moon's countdown curve. None holds a digit: the watch writes digits only where it
 * fills a number, in the feed's digits.
 *
 * The Galaxy watch's Tile writes its line in compact forms (Mohamed's report of 9 November 2026,
 * "in 6h 20m"), each unit a letter or so with no space before it. English is his own form, which
 * he approved. French is CLDR's narrow units ("dans 6h 20min", as ICU's narrow durations write
 * it), not "6h20", whose minutes take two digits ("6h05") and which reads as a time of day.
 * Turkish closes up its own abbreviations ("6sa 20dk"; CLDR's narrow Turkish is "6s 20d").
 * Indonesian takes Wear OS's own short units, j and m (androidx watchface-complications-data's
 * values-in, "%d j" and "%d m", spaced), closed up ("6j 20m"): its full forms' "mnt" is CLDR's
 * narrowest ("6 j, 20 mnt"), and "m" is the unit changed, not only the space. Arabic keeps its
 * spaces, "بعد ٦ س ٢٠ د" (Mohamed's call of the same day: Wear OS's own Arabic is spaced too),
 * Urdu its words, which have no short forms in common use, and Bengali its own, which already
 * are; their compact forms are left empty, so the Tile writes the full ones, and an empty one is
 * not sent ([WatchCountdownPhrases.compactHoursMinutes]). French, Turkish and Indonesian, and
 * Urdu and Bengali kept, wait for their native readers. Under a minute has no number, and keeps
 * its one form.
 *
 * The screens' words that are not read from the phone's resources ([screens]) are here too, for
 * `watchScreenWords` to join with the ones that are.
 */
internal object WatchWords {

    fun countdown(language: UiLanguage): WatchCountdownPhrases = COUNTDOWN.getValue(language)

    /** Friday's Dhuhr, as each language's `prayer_jumuah` names it on the Prayer screen. */
    fun jumuah(language: UiLanguage): String = JUMUAH.getValue(language)

    /**
     * The words of the watch's screens that the phone's Compose resources do not hold, in
     * [language], with every other field left blank for `watchScreenWords` to fill from them:
     *
     * - `openOnPhone` and `updateOnWatch`, the Wear app's own two lines (`open_taqwa_on_phone`,
     *   `update_taqwa_on_watch`), so the watch says them alike with a feed and without one;
     * - `bezelHint`, `undoAction`, `phrasesAction` and `holdLevel`, new phrases for the watch, and
     *   `crownHint`, the Apple Watch's own (plan 4 Task 3): English, and the six other languages as
     *   their native review left them (plan 5 Task 11). The Turkish names Apple's Digital Crown,
     *   with the typographic apostrophe the resources use (Compose prints a backslashed one as it
     *   is, `tools/i18n-check.py`);
     * - `distanceOnDial` and `qiblaDetailOnDial`, the Qibla dial's short forms (Ruling 66 item 1):
     *   the figures and the unit alone, "km" in every language but Arabic's كم (Bengali's কিমি
     *   would fade the dial's north letter), as their native review left them; a reviewer's form
     *   ships only if the Wear Qibla page's fit tests (`QiblaFitTest`) still pass. The phone's long
     *   phrases (`qibla_distance`, `prayer_qibla_detail`) stay what a screen reader hears;
     * - the tasbeeh's six spoken phrases and the turns, accessibility round 2's own string table
     *   (`tasbeeh_a11y_*` and the plurals `qibla_a11y_turn_*`), until round 2 puts them in the
     *   resources. A turn is that plural asked at 15, 30, … 180 degrees, kept as runs: Arabic's
     *   درجة takes every one but 105, CLDR's "few", which takes درجات.
     */
    fun screens(language: UiLanguage): WatchScreenWords = SCREENS.getValue(language)

    /**
     * The Galaxy watch's face screen and Today's face chip (plan 5 Task 5): "Add the Taqwa face" and
     * the four lines of how it stands. New phrases for the watch: the English is plan 5's, and the
     * rest are as their native review left them (plan 5 Task 11), in the words the phone's "Add to
     * Apple Watch" line uses for a watch face. The Wear app's own `face_*` resources are the same
     * phrases, for a watch before any feed (`FaceWordsResourcesTest` in :wearApp holds them to the
     * samples).
     */
    fun face(language: UiLanguage): WatchFaceWords = FACE.getValue(language)

    private val COUNTDOWN: Map<UiLanguage, WatchCountdownPhrases> = mapOf(
        UiLanguage.ENGLISH to WatchCountdownPhrases(
            hoursMinutes = "in %1\$s h %2\$s min",
            hours = "in %1\$s h",
            minutes = "in %1\$s min",
            underAMinute = "in under a minute",
            compactHoursMinutes = "in %1\$sh %2\$sm",
            compactHours = "in %1\$sh",
            compactMinutes = "in %1\$sm",
        ),
        UiLanguage.ARABIC to WatchCountdownPhrases(
            hoursMinutes = "بعد %1\$s س %2\$s د",
            hours = "بعد %1\$s س",
            minutes = "بعد %1\$s د",
            underAMinute = "بعد أقل من دقيقة",
        ),
        UiLanguage.FRENCH to WatchCountdownPhrases(
            hoursMinutes = "dans %1\$s h %2\$s min",
            hours = "dans %1\$s h",
            minutes = "dans %1\$s min",
            underAMinute = "d’ici une minute",
            compactHoursMinutes = "dans %1\$sh %2\$smin",
            compactHours = "dans %1\$sh",
            compactMinutes = "dans %1\$smin",
        ),
        UiLanguage.TURKISH to WatchCountdownPhrases(
            hoursMinutes = "%1\$s sa %2\$s dk sonra",
            hours = "%1\$s sa sonra",
            minutes = "%1\$s dk sonra",
            underAMinute = "bir dakika içinde",
            compactHoursMinutes = "%1\$ssa %2\$sdk sonra",
            compactHours = "%1\$ssa sonra",
            compactMinutes = "%1\$sdk sonra",
        ),
        UiLanguage.INDONESIAN to WatchCountdownPhrases(
            hoursMinutes = "%1\$s j %2\$s mnt lagi",
            hours = "%1\$s j lagi",
            minutes = "%1\$s mnt lagi",
            underAMinute = "kurang dari semenit",
            compactHoursMinutes = "%1\$sj %2\$sm lagi",
            compactHours = "%1\$sj lagi",
            compactMinutes = "%1\$sm lagi",
        ),
        UiLanguage.URDU to WatchCountdownPhrases(
            hoursMinutes = "%1\$s گھنٹے %2\$s منٹ میں",
            hours = "%1\$s گھنٹے میں",
            minutes = "%1\$s منٹ میں",
            underAMinute = "ایک منٹ سے کم میں",
        ),
        UiLanguage.BENGALI to WatchCountdownPhrases(
            hoursMinutes = "%1\$sঘঃ %2\$sমিঃ পরে",
            hours = "%1\$sঘঃ পরে",
            minutes = "%1\$sমিঃ পরে",
            underAMinute = "এক মিনিটের কম সময়ে",
        ),
    )

    private val FACE: Map<UiLanguage, WatchFaceWords> = mapOf(
        UiLanguage.ENGLISH to WatchFaceWords(
            add = "Add the Taqwa face",
            about = "The next prayer and its countdown on your watch face.",
            active = "Taqwa is your watch face.",
            chooseIt = "Touch and hold your watch face, then choose Taqwa.",
            failed = "Couldn’t add the face. Try again later.",
        ),
        UiLanguage.ARABIC to WatchFaceWords(
            add = "أضِف واجهة تقوى",
            about = "الصلاة القادمة وعدّها التنازلي على واجهة ساعتك.",
            active = "تقوى هي واجهة ساعتك.",
            chooseIt = "المس واجهة ساعتك مطوّلًا، ثم اختر تقوى.",
            failed = "تعذّرت إضافة الواجهة. حاول مجددًا لاحقًا.",
        ),
        UiLanguage.FRENCH to WatchFaceWords(
            add = "Ajouter le cadran Taqwa",
            about = "La prochaine prière et son compte à rebours sur votre cadran.",
            active = "Taqwa est votre cadran.",
            chooseIt = "Appuyez longuement sur votre cadran, puis choisissez Taqwa.",
            failed = "Impossible d’ajouter le cadran. Réessayez plus tard.",
        ),
        UiLanguage.TURKISH to WatchFaceWords(
            add = "Taqwa saat yüzünü ekle",
            about = "Sonraki namaz ve geri sayımı saat yüzünüzde.",
            active = "Saat yüzünüz Taqwa.",
            chooseIt = "Saat yüzünüze dokunup basılı tutun, ardından Taqwa’yı seçin.",
            failed = "Saat yüzü eklenemedi. Daha sonra yeniden deneyin.",
        ),
        UiLanguage.INDONESIAN to WatchFaceWords(
            add = "Tambahkan tampilan jam Taqwa",
            about = "Salat berikutnya dan hitung mundurnya di tampilan jam Anda.",
            active = "Taqwa adalah tampilan jam Anda.",
            chooseIt = "Sentuh lama tampilan jam Anda, lalu pilih Taqwa.",
            failed = "Tampilan jam tidak dapat ditambahkan. Coba lagi nanti.",
        ),
        UiLanguage.URDU to WatchFaceWords(
            add = "تقویٰ واچ فیس شامل کریں",
            about = "اگلی نماز اور اس کی الٹی گنتی آپ کے واچ فیس پر۔",
            active = "تقویٰ آپ کا واچ فیس ہے۔",
            chooseIt = "اپنے واچ فیس کو دبا کر رکھیں، پھر تقویٰ منتخب کریں۔",
            failed = "واچ فیس شامل نہیں ہو سکا۔ بعد میں دوبارہ کوشش کریں۔",
        ),
        UiLanguage.BENGALI to WatchFaceWords(
            add = "তাকওয়া ওয়াচ ফেস যোগ করুন",
            about = "আপনার ওয়াচ ফেসে পরবর্তী নামাজ ও তার কাউন্টডাউন।",
            active = "তাকওয়া আপনার ওয়াচ ফেস।",
            chooseIt = "আপনার ওয়াচ ফেস চেপে ধরে রাখুন, তারপর তাকওয়া বেছে নিন।",
            failed = "ওয়াচ ফেস যোগ করা যায়নি। পরে আবার চেষ্টা করুন।",
        ),
    )

    private val SCREENS: Map<UiLanguage, WatchScreenWords> = mapOf(
        UiLanguage.ENGLISH to WatchScreenWords(
            openOnPhone = "Open Taqwa on your phone",
            updateOnWatch = "Update Taqwa on your watch",
            bezelHint = "Turn the bezel or tap",
            crownHint = "Turn the crown or tap",
            undoAction = "Take one away",
            phrasesAction = "Choose a dhikr",
            holdLevel = "Hold your wrist level",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            countSpoken = "%1\$s of %2\$s",
            nextSpoken = "%1\$s. Next, %2\$s",
            setCompleteSpoken = "%1\$s. Round complete",
            roundSpoken = "Round complete. %1\$s, round %2\$s",
            nameRoundSpoken = "%1\$s, round %2\$s",
            countAction = "Count",
            turnRight = listOf(WatchCount(180, "Turn right about %1\$s degrees")),
            turnLeft = listOf(WatchCount(180, "Turn left about %1\$s degrees")),
        ),
        UiLanguage.ARABIC to WatchScreenWords(
            // The new phrases, and the dial's short forms (Ruling 66).
            openOnPhone = "افتح تقوى على هاتفك",
            updateOnWatch = "حدّث تقوى على ساعتك",
            bezelHint = "أدِر الإطار أو اضغط",
            crownHint = "أدِر التاج أو اضغط",
            undoAction = "أنقِص واحدة",
            phrasesAction = "اختر ذكرًا",
            holdLevel = "أبقِ معصمك مستويًا",
            distanceOnDial = "%1\$s كم",
            qiblaDetailOnDial = "%1\$s° · %2\$s كم",
            countSpoken = "%1\$s من %2\$s",
            nextSpoken = "%1\$s. التالي: %2\$s",
            setCompleteSpoken = "%1\$s. اكتملت الجولة",
            roundSpoken = "اكتملت الجولة. %1\$s، الجولة %2\$s",
            nameRoundSpoken = "%1\$s، الجولة %2\$s",
            countAction = "عُدّ",
            turnRight = listOf(
                WatchCount(90, "استدر يمينًا حوالي %1\$s درجة"),
                WatchCount(105, "استدر يمينًا حوالي %1\$s درجات"),
                WatchCount(180, "استدر يمينًا حوالي %1\$s درجة"),
            ),
            turnLeft = listOf(
                WatchCount(90, "استدر يسارًا حوالي %1\$s درجة"),
                WatchCount(105, "استدر يسارًا حوالي %1\$s درجات"),
                WatchCount(180, "استدر يسارًا حوالي %1\$s درجة"),
            ),
        ),
        UiLanguage.FRENCH to WatchScreenWords(
            // The new phrases, and the dial's short forms (Ruling 66).
            openOnPhone = "Ouvrez Taqwa sur votre téléphone",
            updateOnWatch = "Mettez à jour Taqwa sur votre montre",
            bezelHint = "Tournez la lunette ou touchez",
            crownHint = "Tournez la Digital Crown ou touchez",
            undoAction = "Retirer un",
            phrasesAction = "Choisir un dhikr",
            holdLevel = "Gardez le poignet à plat",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            countSpoken = "%1\$s sur %2\$s",
            nextSpoken = "%1\$s. Ensuite, %2\$s",
            setCompleteSpoken = "%1\$s. Série terminée",
            roundSpoken = "Série terminée. %1\$s, série %2\$s",
            nameRoundSpoken = "%1\$s, série %2\$s",
            countAction = "Compter",
            turnRight = listOf(WatchCount(180, "Tournez à droite d’environ %1\$s degrés")),
            turnLeft = listOf(WatchCount(180, "Tournez à gauche d’environ %1\$s degrés")),
        ),
        UiLanguage.TURKISH to WatchScreenWords(
            // The new phrases, and the dial's short forms (Ruling 66).
            openOnPhone = "Telefonunuzda Taqwa’yı açın",
            updateOnWatch = "Saatinizde Taqwa’yı güncelleyin",
            bezelHint = "Çerçeveyi çevirin veya dokunun",
            crownHint = "Digital Crown’u çevirin veya dokunun",
            undoAction = "Bir eksilt",
            phrasesAction = "Zikir seç",
            holdLevel = "Saatinizi düz tutun",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            countSpoken = "%1\$s, toplam %2\$s",
            nextSpoken = "%1\$s. Sıradaki: %2\$s",
            setCompleteSpoken = "%1\$s. Tur tamamlandı",
            roundSpoken = "Tur tamamlandı. %1\$s, %2\$s. tur",
            nameRoundSpoken = "%1\$s, %2\$s. tur",
            countAction = "Say",
            turnRight = listOf(WatchCount(180, "Yaklaşık %1\$s derece sağa dönün")),
            turnLeft = listOf(WatchCount(180, "Yaklaşık %1\$s derece sola dönün")),
        ),
        UiLanguage.INDONESIAN to WatchScreenWords(
            // The new phrases, and the dial's short forms (Ruling 66).
            openOnPhone = "Buka Taqwa di ponsel Anda",
            updateOnWatch = "Perbarui Taqwa di jam tangan Anda",
            bezelHint = "Putar bezel atau ketuk",
            crownHint = "Putar Digital Crown atau ketuk",
            undoAction = "Kurangi satu",
            phrasesAction = "Pilih zikir",
            holdLevel = "Datarkan jam tangan",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            countSpoken = "%1\$s dari %2\$s",
            nextSpoken = "%1\$s. Berikutnya, %2\$s",
            setCompleteSpoken = "%1\$s. Putaran selesai",
            roundSpoken = "Putaran selesai. %1\$s, putaran %2\$s",
            nameRoundSpoken = "%1\$s, putaran %2\$s",
            countAction = "Hitung",
            turnRight = listOf(WatchCount(180, "Putar badan ke kanan sekitar %1\$s derajat")),
            turnLeft = listOf(WatchCount(180, "Putar badan ke kiri sekitar %1\$s derajat")),
        ),
        UiLanguage.URDU to WatchScreenWords(
            // The new phrases, and the dial's short forms (Ruling 66).
            openOnPhone = "اپنے فون پر تقویٰ کھولیں",
            updateOnWatch = "اپنی گھڑی پر تقویٰ اپڈیٹ کریں",
            bezelHint = "بیزل گھمائیں یا ٹیپ کریں",
            crownHint = "کراؤن گھمائیں یا ٹیپ کریں",
            undoAction = "ایک کم کریں",
            phrasesAction = "ذکر منتخب کریں",
            holdLevel = "اپنی کلائی افقی رکھیں",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            countSpoken = "%2\$s میں سے %1\$s",
            nextSpoken = "%1\$s۔ اگلا: %2\$s",
            setCompleteSpoken = "%1\$s۔ چکر مکمل",
            roundSpoken = "چکر مکمل۔ %1\$s، چکر %2\$s",
            nameRoundSpoken = "%1\$s، چکر %2\$s",
            countAction = "گنیں",
            turnRight = listOf(WatchCount(180, "تقریباً %1\$s درجے دائیں مڑیں")),
            turnLeft = listOf(WatchCount(180, "تقریباً %1\$s درجے بائیں مڑیں")),
        ),
        UiLanguage.BENGALI to WatchScreenWords(
            // The new phrases, and the dial's short forms (Ruling 66).
            openOnPhone = "আপনার ফোনে তাকওয়া খুলুন",
            updateOnWatch = "আপনার ঘড়িতে তাকওয়া আপডেট করুন",
            bezelHint = "বেজেল ঘোরান বা চাপ দিন",
            crownHint = "ক্রাউন ঘোরান বা চাপ দিন",
            undoAction = "একটি কমান",
            phrasesAction = "একটি জিকির বেছে নিন",
            holdLevel = "কব্জি সমান করে রাখুন",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            countSpoken = "%2\$s-এর মধ্যে %1\$s",
            nextSpoken = "%1\$s। এরপর %2\$s",
            setCompleteSpoken = "%1\$s। রাউন্ড সম্পূর্ণ",
            roundSpoken = "রাউন্ড সম্পূর্ণ। %1\$s, রাউন্ড %2\$s",
            nameRoundSpoken = "%1\$s, রাউন্ড %2\$s",
            countAction = "গণনা",
            turnRight = listOf(WatchCount(180, "ডানে প্রায় %1\$s ডিগ্রি ঘুরুন")),
            turnLeft = listOf(WatchCount(180, "বাঁয়ে প্রায় %1\$s ডিগ্রি ঘুরুন")),
        ),
    )

    private val JUMUAH: Map<UiLanguage, String> = mapOf(
        UiLanguage.ENGLISH to "Jumuʿah",
        UiLanguage.ARABIC to "الجمعة",
        UiLanguage.FRENCH to "Joumouʿa",
        UiLanguage.TURKISH to "Cuma",
        UiLanguage.INDONESIAN to "Jumat",
        UiLanguage.URDU to "جمعہ",
        UiLanguage.BENGALI to "জুমা",
    )
}
