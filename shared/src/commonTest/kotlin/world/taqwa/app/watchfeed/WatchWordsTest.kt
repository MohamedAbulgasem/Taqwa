package world.taqwa.app.watchfeed

import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchCountdownPhrases
import world.taqwa.app.watch.WatchFaceWords
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchPlace
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The countdown the watch writes, in each interface language: English and Arabic are the spec's (1.3). */
class WatchWordsTest {

    @Test
    fun englishCountdownIsTheSpecs() {
        assertEquals(
            WatchCountdownPhrases(
                hoursMinutes = "in %1\$s h %2\$s min",
                hours = "in %1\$s h",
                minutes = "in %1\$s min",
                underAMinute = "in under a minute",
                // The Tile's line: Mohamed's own form (his report of 9 November 2026).
                compactHoursMinutes = "in %1\$sh %2\$sm",
                compactHours = "in %1\$sh",
                compactMinutes = "in %1\$sm",
            ),
            WatchWords.countdown(UiLanguage.ENGLISH),
        )
    }

    @Test
    fun arabicCountdownIsTheSpecs() {
        assertEquals(
            WatchCountdownPhrases(
                hoursMinutes = "بعد %1\$s س %2\$s د",
                hours = "بعد %1\$s س",
                minutes = "بعد %1\$s د",
                underAMinute = "بعد أقل من دقيقة",
                // No compact forms: the Tile keeps these spaced ones (Mohamed's call of 9 November
                // 2026, Wear OS's own Arabic is spaced too).
            ),
            WatchWords.countdown(UiLanguage.ARABIC),
        )
    }

    /**
     * Ruling 66 item 1, held by its shape (Ruling 67 item 2): the Qibla dial's short forms are the
     * feed's figures and the unit alone, in seven languages (as their native review left them: "km"
     * in every language but Arabic's كم). Each has exactly the long phrase's places, in order; no
     * digit of its own, since the watch writes digits only where it fills the feed's figures; the
     * bearing in degrees on the dimmed dial and no degree sign on the turning one; and one word,
     * the unit, the same on both dials, and no other. No test pins a form itself: a reviewer's form
     * (another order, a direction mark, a no-break space, another unit) passes here, and ships only
     * if `QiblaFitTest`, which reads the forms from `WatchWords.kt`, still passes (errata p5 #22).
     * In French, Turkish, Indonesian and Bengali it changes `WatchWords.kt` alone (a new unit in
     * Bengali or Arabic letters also reruns `qibla-ink.py`); Urdu's also regenerates its phone
     * golden, Arabic's also changes `:watchcore`'s hand copy (`Fixtures.ARABIC_SCREENS`) and
     * regenerates the vectors, and English's is also the watch's own fallback, in Kotlin and Swift.
     * Plan 3's Ruling 67 note lists the files, language by language.
     */
    @Test
    fun theDialsShortFormsAreTheFiguresAndTheUnitInEveryLanguage() {
        val problems = UiLanguage.entries.flatMap { language ->
            val words = WatchWords.screens(language)
            shortFormProblems(language.code, words.distanceOnDial, words.qiblaDetailOnDial)
        }
        assertEquals(emptyList(), problems)
    }

    /** What is wrong with a language's two short forms, [turning] (the distance) and [dimmed] (the bearing and the distance), by their shape. */
    private fun shortFormProblems(code: String, turning: String, dimmed: String): List<String> {
        val problems = mutableListOf<String>()
        val units = mutableListOf<String>()
        for ((form, places) in listOf(turning to listOf("%1\$s"), dimmed to listOf("%1\$s", "%2\$s"))) {
            if (PLACE.findAll(form).map { it.value }.toList() != places) problems += "$code: '$form' has not the places $places"
            val rest = PLACE.replace(form, " ")
            if (rest.any { it.isDigit() }) problems += "$code: '$form' has a digit of its own"
            val inWords = words(rest)
            if (inWords.size != 1) problems += "$code: '$form' has ${inWords.size} words, where the unit alone is one"
            units += inWords
        }
        if (units.size == 2 && units[0] != units[1]) problems += "$code: the turning dial's unit '${units[0]}' is not the dimmed one's '${units[1]}'"
        if ('°' in turning) problems += "$code: '$turning' has a degree sign, with no bearing"
        if (!dimmed.contains("%1\$s°") || dimmed.count { it == '°' } != 1) problems += "$code: '$dimmed' has not the bearing in degrees"
        return problems
    }

    /** [text]'s words: its runs of letters, each with the marks written on them. */
    private fun words(text: String): List<String> {
        val out = mutableListOf<String>()
        val word = StringBuilder()
        for (c in text) {
            if (c.isLetter() || c.category == CharCategory.NON_SPACING_MARK || c.category == CharCategory.COMBINING_SPACING_MARK) {
                word.append(c)
            } else if (word.isNotEmpty()) {
                out += word.toString()
                word.clear()
            }
        }
        if (word.isNotEmpty()) out += word.toString()
        return out
    }

    @Test
    fun everyLanguageHasEveryFormWithItsPlaces() {
        for (language in UiLanguage.entries) {
            val c = WatchWords.countdown(language)
            val code = language.code
            val first = c.hoursMinutes.indexOf("%1\$s")
            val second = c.hoursMinutes.indexOf("%2\$s")
            assertTrue(first >= 0 && second > first, "$code hoursMinutes: ${c.hoursMinutes}")
            for ((name, form) in listOf("hours" to c.hours, "minutes" to c.minutes)) {
                assertTrue(form.contains("%1\$s"), "$code $name: $form")
                assertFalse(form.contains("%2\$s"), "$code $name: $form")
            }
            assertFalse(c.underAMinute.contains("%1\$s") || c.underAMinute.contains("%2\$s"), "$code underAMinute: ${c.underAMinute}")
            for (form in listOf(c.hoursMinutes, c.hours, c.minutes, c.underAMinute)) {
                assertTrue(form.isNotBlank(), "$code has a blank form")
            }
        }
    }

    /**
     * The Tile's compact countdown as it is written in each language and digit set, at an hour and
     * twelve minutes, a whole hour, twelve minutes, one minute, the most the rules write and under
     * a minute (Mohamed's report of 9 November 2026). English is his own form; French is CLDR's
     * narrow units; Turkish closes up its own abbreviations; Indonesian takes Wear OS's short
     * units, j and m, closed up, in place of its full forms' j and mnt; Arabic keeps its spaces
     * (Mohamed's call of the same day), and Urdu and Bengali their full forms.
     */
    @Test
    fun theTilesCompactCountdownInEveryLanguage() {
        val seconds = listOf(4320L, 3600L, 720L, 60L, 99 * 3600L + 59 * 60L, 59L)
        val expected = listOf(
            Triple(UiLanguage.ENGLISH, '0', listOf("in 1h 12m", "in 1h", "in 12m", "in 1m", "in 99h 59m", "in under a minute")),
            Triple(UiLanguage.ARABIC, '٠', listOf("بعد ١ س ١٢ د", "بعد ١ س", "بعد ١٢ د", "بعد ١ د", "بعد ٩٩ س ٥٩ د", "بعد أقل من دقيقة")),
            Triple(UiLanguage.ARABIC, '0', listOf("بعد 1 س 12 د", "بعد 1 س", "بعد 12 د", "بعد 1 د", "بعد 99 س 59 د", "بعد أقل من دقيقة")),
            Triple(UiLanguage.FRENCH, '0', listOf("dans 1h 12min", "dans 1h", "dans 12min", "dans 1min", "dans 99h 59min", "d’ici une minute")),
            Triple(UiLanguage.TURKISH, '0', listOf("1sa 12dk sonra", "1sa sonra", "12dk sonra", "1dk sonra", "99sa 59dk sonra", "bir dakika içinde")),
            Triple(UiLanguage.INDONESIAN, '0', listOf("1j 12m lagi", "1j lagi", "12m lagi", "1m lagi", "99j 59m lagi", "kurang dari semenit")),
            Triple(UiLanguage.URDU, '۰', listOf("۱ گھنٹے ۱۲ منٹ میں", "۱ گھنٹے میں", "۱۲ منٹ میں", "۱ منٹ میں", "۹۹ گھنٹے ۵۹ منٹ میں", "ایک منٹ سے کم میں")),
            Triple(UiLanguage.BENGALI, '০', listOf("১ঘঃ ১২মিঃ পরে", "১ঘঃ পরে", "১২মিঃ পরে", "১মিঃ পরে", "৯৯ঘঃ ৫৯মিঃ পরে", "এক মিনিটের কম সময়ে")),
        )
        assertEquals(UiLanguage.entries.toSet(), expected.map { it.first }.toSet())
        for ((language, zero, words) in expected) {
            val feed = feedIn(language, zero)
            assertEquals(words, seconds.map { WatchRules.compactCountdown(feed, it) }, "${language.code} in $zero")
        }
    }

    /**
     * Each language's compact forms are its full ones made shorter: all three or none (Arabic,
     * Urdu and Bengali keep theirs), with exactly the full form's places, in order, no digit of their own,
     * and, filled with the widest numbers the rules write, shorter than the full form.
     */
    @Test
    fun theCompactFormsAreTheFullFormsMadeShorter() {
        val problems = mutableListOf<String>()
        for (language in UiLanguage.entries) {
            val c = WatchWords.countdown(language)
            val code = language.code
            val pairs = listOf(
                Triple("hoursMinutes", c.hoursMinutes, c.compactHoursMinutes),
                Triple("hours", c.hours, c.compactHours),
                Triple("minutes", c.minutes, c.compactMinutes),
            )
            if (pairs.all { it.third.isEmpty() }) {
                if (language !in setOf(UiLanguage.ARABIC, UiLanguage.URDU, UiLanguage.BENGALI)) problems += "$code has no compact forms"
                continue
            }
            for ((name, full, compact) in pairs) {
                if (compact.isBlank()) problems += "$code $name: no compact form beside the others"
                if (PLACE.findAll(compact).map { it.value }.toList() != PLACE.findAll(full).map { it.value }.toList()) problems += "$code $name: '$compact' has not the places of '$full'"
                if (PLACE.replace(compact, "").any { it.isDigit() }) problems += "$code $name: '$compact' has a digit of its own"
                val filled = WatchRules.filled(compact, "99", "59")
                if (filled.length >= WatchRules.filled(full, "99", "59").length) problems += "$code $name: '$filled' is no shorter than the full form"
            }
        }
        assertEquals(emptyList(), problems)
    }

    /** A feed of no days in [language]'s words, written in the digits from [zero]: what the countdown rules read. */
    private fun feedIn(language: UiLanguage, zero: Char) = WatchFeed(
        version = WatchFeedJson.VERSION,
        writtenAt = 0L,
        days = emptyList(),
        place = WatchPlace(city = "", qiblaBearing = 0.0, qiblaKm = "", declination = 0.0, southern = false),
        ui = WatchUi(
            language = language.code,
            rtl = language.rtl,
            digitZero = zero,
            twelveHour = false,
            am = "AM",
            pm = "PM",
            countdown = WatchWords.countdown(language),
            nextIn = "%1\$s in",
        ),
    )

    /**
     * Ruling 60 item 4, settled by the native review (plan 5 Task 11): the Galaxy's bezel hint and
     * the Apple Watch's crown hint differ only in what is turned, so both watches name the tap with
     * one word, the phone's own (`tasbeeh_hint`): Urdu's ٹیپ کریں and Bengali's চাপ দিন. This holds
     * the two hints alike; `WatchWordsResourcesTest.theWatchesTapIsThePhonesOwnWord` holds their
     * word to the phone's resources, which only the host tests read.
     */
    @Test
    fun theTwoWatchesHintsNameTheTapAlike() {
        val or = mapOf(
            UiLanguage.ENGLISH to " or ", UiLanguage.ARABIC to " أو ", UiLanguage.FRENCH to " ou ",
            UiLanguage.TURKISH to " veya ", UiLanguage.INDONESIAN to " atau ", UiLanguage.URDU to " یا ",
            UiLanguage.BENGALI to " বা ",
        )
        val problems = UiLanguage.entries.mapNotNull { language ->
            val words = WatchWords.screens(language)
            val word = or.getValue(language)
            val bezelTap = words.bezelHint.substringAfterLast(word, missingDelimiterValue = "")
            val crownTap = words.crownHint.substringAfterLast(word, missingDelimiterValue = "")
            if (bezelTap.isEmpty() || bezelTap != crownTap) {
                "${language.code}: '${words.bezelHint}' and '${words.crownHint}' name the tap apart"
            } else {
                null
            }
        }
        assertEquals(emptyList(), problems)
    }

    /** The face screen's five phrases (plan 5 Task 5): the plan's English, and every language whole. */
    @Test
    fun theFaceWordsAreThePlansInEnglishAndWholeInEveryLanguage() {
        assertEquals(
            WatchFaceWords(
                add = "Add the Taqwa face",
                about = "The next prayer and its countdown on your watch face.",
                active = "Taqwa is your watch face.",
                chooseIt = "Touch and hold your watch face, then choose Taqwa.",
                failed = "Couldn’t add the face. Try again later.",
            ),
            WatchWords.face(UiLanguage.ENGLISH),
        )
        for (language in UiLanguage.entries) {
            val face = WatchWords.face(language)
            for ((name, phrase) in listOf("add" to face.add, "about" to face.about, "active" to face.active, "chooseIt" to face.chooseIt, "failed" to face.failed)) {
                assertTrue(phrase.isNotBlank(), "${language.code} $name is blank")
                assertFalse(PLACE.containsMatchIn(phrase) || phrase.any { it.isDigit() }, "${language.code} $name: '$phrase' has a place or a digit")
                assertFalse('\'' in phrase, "${language.code} $name: write ’, not a straight apostrophe")
            }
        }
    }

    private companion object {
        val PLACE = Regex("""%\d+\$[sd]""")
    }
}
