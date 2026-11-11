package world.taqwa.app.watchfeed

import kotlinx.coroutines.runBlocking
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.DigitGrouping
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.uppercaseIn
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.watch.WatchDay
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watch.WatchPhrase
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchSpoken
import world.taqwa.app.watch.WatchUi
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The feed the phone builds on the real Android formats, against the samples `:watchcore` commits
 * (`watchcore/vectors/sample-feed-capetown*.json`, which the Wear app's debug build starts with and
 * the face's pass is shot on). Every string the phone's own functions write wins: CLDR's words or
 * ours, the digits, the grouping, the bundle's city name and the place as Ruling 39 rounds it. A
 * sample that disagrees has its fixture changed (`Fixtures.kt`), never the builder.
 *
 * The other cases hold the rules that the tag never decides the language, the case or the digits
 * the face cannot draw, and the feed's size in the worst case.
 */
class WatchFeedSampleTest {

    private fun sample(name: String): WatchFeed = CapeTownSample.read(name)

    private val english: WatchFeed get() = CapeTownSample.english

    private val cities by lazy { BundledCities.repository() }

    private fun format(tag: String, clock: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR): PlatformFormat =
        AndroidPlatformFormat(Locale.forLanguageTag(tag), clock)

    private fun build(
        format: PlatformFormat,
        language: UiLanguage,
        clock: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR,
        city: String? = null,
        spoken: WatchSpoken? = BundledSpoken.of(language),
        screens: WatchScreenWords = BundledScreenStrings.words(language),
        phrases: List<TasbeehPreset> = emptyList(),
        budgetBytes: Int = WatchLinkContract.FEED_BUDGET_BYTES,
    ): WatchFeed = assertNotNull(
        WatchFeedBuilder.build(
            WatchFeedInputs(
                location = CapeTownSample.LOCATION,
                city = city ?: runBlocking { watchCityName(CapeTownSample.LOCATION, format, cities) },
                language = language,
                spoken = spoken,
                screens = screens,
                // The samples are a phone with Show sunrise on, as the mockups draw the day's six rows.
                prayerSettings = PrayerSettings(showSunrise = true),
                format = format,
                clockFormat = clock,
                phrases = phrases,
                now = CapeTownSample.now,
                timesFor = CapeTownSample.times,
            ),
            budgetBytes,
        ),
        format.languageTag(),
    )

    @Test
    fun capeTownIsTheSampleFeed() {
        val cases = listOf(
            Triple("en", ClockFormat.TWENTY_FOUR_HOUR, "sample-feed-capetown.json"),
            Triple("en", ClockFormat.TWELVE_HOUR, "sample-feed-capetown-en12.json"),
            Triple("ar-LY", ClockFormat.TWELVE_HOUR, "sample-feed-capetown-ar-ly12.json"),
            Triple("ar-SA", ClockFormat.TWENTY_FOUR_HOUR, "sample-feed-capetown-ar-sa.json"),
        )
        val problems = mutableListOf<String>()
        for ((tag, clock, file) in cases) {
            val expected = sample(file)
            // His own phrases as the phone stores them, so the feed carries them as the sample does.
            val phrases = expected.tasbeeh.map { TasbeehPresets.custom(it.id, it.phrase, it.target) }
            val feed = build(format(tag, clock), UiLanguage.of(tag), clock, phrases = phrases)
            problems += differences("$tag/${if (clock == ClockFormat.TWELVE_HOUR) 12 else 24} ($file)", expected, feed)
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun bundledSpokenIsTheSamples() {
        assertEquals(english.ui.spoken, BundledSpoken.of(UiLanguage.ENGLISH))
        assertEquals(sample("sample-feed-capetown-ar-sa.json").ui.spoken, BundledSpoken.of(UiLanguage.ARABIC))
    }

    @Test
    fun theLanguageIsTheAppsNotTheTags() {
        val arabicSample = sample("sample-feed-capetown-ar-ly12.json")

        // An American phone with the app set to Arabic.
        val arabic = build(format("en-US"), UiLanguage.ARABIC)
        assertEquals(arabicSample.days.map { it.names }, arabic.days.map { it.names })
        assertEquals(WatchWords.countdown(UiLanguage.ARABIC), arabic.ui.countdown)
        assertEquals("متبقٍّ على %1\$s", arabic.ui.nextIn)
        // The Libyan sample is Arabic in Western digits: the same words as an American phone in Arabic.
        assertEquals(arabicSample.days.map { it.hijri }, arabic.days.map { it.hijri })
        assertEquals(arabicSample.days.map { it.weekday }, arabic.days.map { it.weekday })
        assertEquals("الأحد", arabic.days.single { it.date == "2026-10-18" }.weekday)
        assertEquals(true, arabic.ui.rtl)
        assertEquals("ص" to "م", arabic.ui.am to arabic.ui.pm)
        assertEquals('0', arabic.ui.digitZero)
        assertEquals("ar", arabic.ui.language)
        assertOnlyDigitsFrom('0', arabic)

        // A Saudi phone with the app set to English: Arabic-Indic digits with English are no pair
        // the face draws, so every digit is Western.
        val englishOnSaudi = build(format("ar-SA"), UiLanguage.ENGLISH)
        assertEquals(english.days.map { it.names }, englishOnSaudi.days.map { it.names })
        assertEquals(english.days.map { it.gregorian }, englishOnSaudi.days.map { it.gregorian })
        assertEquals(english.days.map { it.hijri }, englishOnSaudi.days.map { it.hijri })
        assertEquals(WatchWords.countdown(UiLanguage.ENGLISH), englishOnSaudi.ui.countdown)
        assertEquals(false, englishOnSaudi.ui.rtl)
        assertEquals("AM" to "PM", englishOnSaudi.ui.am to englishOnSaudi.ui.pm)
        assertEquals('0', englishOnSaudi.ui.digitZero)
        assertEquals("en", englishOnSaudi.ui.language)
        assertOnlyDigitsFrom('0', englishOnSaudi)
    }

    @Test
    fun theFaceWordsAreTheInterfaceLanguages() {
        // The face screen's words (plan 5 Task 5) follow the app's language, never the device's.
        val arabic = build(format("en-US"), UiLanguage.ARABIC)
        assertEquals(WatchWords.face(UiLanguage.ARABIC), arabic.ui.face)
        assertEquals(WatchWords.face(UiLanguage.ENGLISH), build(format("ar-SA"), UiLanguage.ENGLISH).ui.face)
        assertEquals("Add the Taqwa face", english.ui.face.add)
    }

    @Test
    fun theWatchsCaseIsTheAppsCase() {
        val cases = listOf(
            "az-AZ" to UiLanguage.ENGLISH,
            "lt-LT" to UiLanguage.ENGLISH,
            "he-IL" to UiLanguage.ENGLISH,
            "tr-TR" to UiLanguage.TURKISH,
            "en-US" to UiLanguage.TURKISH,
        )
        for ((tag, language) in cases) {
            val feed = build(format(tag), language)
            val watchLocale = Locale.forLanguageTag(feed.ui.language)
            for (day in feed.days) {
                for (name in day.names.all()) {
                    // Plan 1's slot A label (ComplicationFields.kt), against the app's own capitals.
                    assertEquals(name.uppercaseIn(language), name.uppercase(watchLocale), "$tag with ${language.code}: $name")
                }
            }
        }
        assertEquals("MAGHRIB", build(format("az-AZ"), UiLanguage.ENGLISH).let { it.days[0].names.maghrib.uppercase(Locale.forLanguageTag(it.ui.language)) })
        assertEquals("İKİNDİ", build(format("tr-TR"), UiLanguage.TURKISH).let { it.days[0].names.asr.uppercase(Locale.forLanguageTag(it.ui.language)) })
    }

    @Test
    fun theDigitZeroIsTheFeedsOwnZero() {
        for (tag in listOf("en", "ar-SA", "ar-LY", "ar-LY-u-nu-arab", "ar-EG", "bn-BD", "ur-PK", "ur-PK-u-nu-arabext", "fr-FR")) {
            val format = format(tag)
            val feed = build(format, UiLanguage.of(tag))
            assertEquals(format.localizedDigits(0), feed.ui.digitZero.toString(), tag)
            assertOnlyDigitsFrom(feed.ui.digitZero, feed)
            // The words locale's tag is the phone's own, its numbering extension kept (plan 2's errata).
            assertEquals(tag, feed.ui.language, tag)
        }
    }

    @Test
    fun aSetTheFaceWasNotBuiltForIsWesternThroughout() {
        for (tag in listOf("mr-IN", "ne-NP", "my-MM", "fa-IR")) {
            val feed = build(format(tag), UiLanguage.of(tag))
            assertEquals('0', feed.ui.digitZero, tag)
            val json = WatchFeedJson.encode(feed)
            val foreign = json.filter { Character.getType(it) == Character.DECIMAL_DIGIT_NUMBER.toInt() && it !in '0'..'9' }
            assertTrue(foreign.isEmpty(), "$tag's feed holds the digits $foreign")
            assertOnlyDigitsFrom('0', feed)
        }
    }

    @Test
    fun aDistanceInWesternDigitsTakesTheInterfaceLanguagesMark() {
        val narrowSpace = DigitGrouping.NARROW_NO_BREAK_SPACE
        // Pairs the face was not built for, on the real formats: Western throughout, the mark too.
        val western = listOf(
            Triple("ar-SA", UiLanguage.ENGLISH, "6,600"),
            Triple("fa-IR", UiLanguage.ENGLISH, "6,600"),
            Triple("fa-AF", UiLanguage.ENGLISH, "6,600"),
            Triple("ur-PK-u-nu-arabext", UiLanguage.ENGLISH, "6,600"),
            Triple("bn-BD", UiLanguage.ENGLISH, "6,600"),
            Triple("ar-SA", UiLanguage.FRENCH, "6${narrowSpace}600"),
            Triple("ar-EG", UiLanguage.TURKISH, "6.600"),
        )
        for ((tag, language, km) in western) {
            val feed = build(format(tag), language)
            assertEquals('0', feed.ui.digitZero, "$tag with ${language.code}")
            assertEquals(km, feed.place.qiblaKm, "$tag with ${language.code}")
        }
        // Pairs the face draws: the phone's own Qibla card string.
        assertEquals("٦٬٦٠٠", build(format("ar-EG"), UiLanguage.ARABIC).place.qiblaKm)
        assertEquals("6${narrowSpace}600", build(format("fr-FR"), UiLanguage.ARABIC).place.qiblaKm)
    }

    @Test
    fun aPhoneOutsideTheSevenGetsTheAppsOwnFallback() {
        val en = build(format("en"), UiLanguage.ENGLISH)
        for (tag in listOf("hi-IN", "he-IL", "fa-IR", "mr-IN", "zh-CN", "az-AZ")) {
            val feed = build(format(tag), UiLanguage.ENGLISH)
            assertEquals(en.days.map { it.names }, feed.days.map { it.names }, tag)
            assertEquals(en.ui.countdown, feed.ui.countdown, tag)
            assertEquals(en.ui.nextIn, feed.ui.nextIn, tag)
            assertEquals(en.ui.spoken, feed.ui.spoken, tag)
            assertEquals(en.days.map { it.weekday }, feed.days.map { it.weekday }, tag)
            assertEquals(en.days.map { it.gregorianSpoken }, feed.days.map { it.gregorianSpoken }, tag)
            assertEquals(en.days.map { it.gregorian }, feed.days.map { it.gregorian }, tag)
            assertEquals("AM" to "PM", feed.ui.am to feed.ui.pm, tag)
            assertEquals(false, feed.ui.rtl, tag)
            assertEquals('0', feed.ui.digitZero, tag)
            assertEquals("en", feed.ui.language, tag)
        }
    }

    /**
     * On 12 hours, outside the seven, the words stay the app's fallback but each `clockTime` is
     * what the phone's Prayer screen writes, its own locale's day period and all (plan 3's call 9,
     * "as the app writes it", and plan 2's hand-off, "its own clockTime, marker included"). The
     * markers beside it, `ui.am` and `ui.pm`, stay the words locale's (plan 2), so the two differ
     * here, as they do where a phone's locale is one of the seven's but not its interface's.
     */
    @Test
    fun aTwelveHourPhoneOutsideTheSevenWritesItsTimesAsItsPrayerScreenDoes() {
        val twelve = ClockFormat.TWELVE_HOUR
        // Today's Asr and Sunrise, Western digits by the Lead's call 3, the marker after them.
        val cases = listOf(
            Triple("hi-IN", "4:10${NBSP}pm", "6:01${NBSP}am"),
            Triple("he-IL", "4:10${NBSP}אחה״צ", "6:01${NBSP}לפנה״צ"),
            Triple("fa-IR", "4:10${NBSP}بعدازظهر", "6:01${NBSP}قبل${ZWNJ}ازظهر"),
            Triple("mr-IN", "4:10${NBSP}PM", "6:01${NBSP}AM"),
            Triple("zh-CN", "4:10${NBSP}下午", "6:01${NBSP}上午"),
            Triple("az-AZ", "4:10${NBSP}PM", "6:01${NBSP}AM"),
            Triple("ko-KR", "4:10${NBSP}오후", "6:01${NBSP}오전"),
            Triple("my-MM", "4:10${NBSP}ညနေ", "6:01${NBSP}နံနက်"),
            Triple("am-ET", "4:10${NBSP}ከሰዓት", "6:01${NBSP}ጥዋት"),
        )
        for ((tag, asr, sunrise) in cases) {
            val feed = build(format(tag, twelve), UiLanguage.ENGLISH, twelve)
            assertEquals("AM" to "PM", feed.ui.am to feed.ui.pm, tag)
            assertEquals("en", feed.ui.language, tag)
            val today = assertNotNull(feed.days.single { it.date == TODAY }.clockTime, tag)
            assertEquals(asr, today.asr, tag)
            assertEquals(sunrise, today.sunrise, tag)
            for (day in feed.days) {
                val times = assertNotNull(day.clockTime, "$tag ${day.date}")
                // The figures are the feed's bare clock, Western, and the marker follows them.
                day.clock.all().zip(times.all()).forEach { (clock, time) ->
                    assertTrue(time.startsWith("$clock$NBSP"), "$tag ${day.date}: \"$time\" against \"$clock\"")
                }
            }
        }

        // Within the seven too: each clockTime is its Prayer screen's, whatever the interface.
        val englishOnTurkish = build(format("tr-TR", twelve), UiLanguage.ENGLISH, twelve)
        assertEquals("AM" to "PM", englishOnTurkish.ui.am to englishOnTurkish.ui.pm)
        assertEquals("ÖS${NBSP}4:10", englishOnTurkish.days.single { it.date == TODAY }.clockTime?.asr)
        val englishOnSaudi = build(format("ar-SA", twelve), UiLanguage.ENGLISH, twelve)
        assertEquals("AM" to "PM", englishOnSaudi.ui.am to englishOnSaudi.ui.pm)
        assertEquals("4:10${NBSP}م", englishOnSaudi.days.single { it.date == TODAY }.clockTime?.asr)
        val turkishOnAmerican = build(format("en-US", twelve), UiLanguage.TURKISH, twelve)
        assertEquals("ÖÖ" to "ÖS", turkishOnAmerican.ui.am to turkishOnAmerican.ui.pm)
        assertEquals("4:10${NBSP}PM", turkishOnAmerican.days.single { it.date == TODAY }.clockTime?.asr)
    }

    @Test
    fun theLargestFeedStaysWithinItsBudget() {
        val cases = listOf("ar-SA", "bn-BD", "ur-PK-u-nu-arabext")
        var largest = 0 to ""
        for (tag in cases) {
            for (kind in PhraseKind.entries) {
                val presets = WorstFeeds.presets(kind)
                val feed = CapeTownSample.largestFeed(tag, kind)
                val json = WatchFeedJson.encode(feed)
                val size = WatchFeedBuilder.sizeOf(json)
                println("[measure] largest feed $tag/12 ${kind.name}: ${feed.tasbeeh.size} phrases, $size UTF-8 bytes, ${json.length * 2} UTF-16 bytes")
                assertTrue(size <= WatchLinkContract.FEED_BUDGET_BYTES, "$tag $kind: $size bytes")
                if (size > largest.first) largest = size to "$tag/12 ${kind.name}"
                assertEquals(31, feed.days.size, "$tag $kind")
                assertEquals(presets.take(feed.tasbeeh.size).map { it.id }, feed.tasbeeh.map { it.id }, "$tag $kind")
                val all = feed.tasbeeh.size == presets.size
                // Plan 2 Task 3's rule (the Lead's ruling of 9 November 2026: it stands, and the
                // budget does not move): an ar-SA phone's 100 phrases of Arabic or of emoji all go.
                // The Tile's compact countdown took them to 60,033 bytes, so Arabic keeps its spaced
                // forms (Mohamed's call of the same day) and an empty compact form is not written.
                if (tag == "ar-SA" && kind != PhraseKind.CONTROL) assertTrue(all, "$tag $kind: ${feed.tasbeeh.size} of 100")
                if (tag in setOf("ar-SA", "bn-BD") && kind == PhraseKind.CONTROL) {
                    assertTrue(!all, "$tag $kind: all 100 went")
                }
                if (!all) {
                    val oneMore = feed.copy(tasbeeh = phrasesOf(presets.take(feed.tasbeeh.size + 1)))
                    assertTrue(
                        WatchFeedBuilder.sizeOf(WatchFeedJson.encode(oneMore)) > WatchLinkContract.FEED_BUDGET_BYTES,
                        "$tag $kind: one more phrase would have fitted",
                    )
                }
            }
        }
        println("[measure] largest feed, words in: ${largest.second}, ${largest.first} UTF-8 bytes of ${WatchLinkContract.FEED_BUDGET_BYTES}")
    }

    @Test
    fun theTimesAloneLeaveRoomForThePhrases() {
        var largest = 0 to ""
        for (tag in PhoneTags.TAGS) {
            val language = UiLanguage.of(tag)
            val sets = PhoneTags.digitSets(tag) { format(it, ClockFormat.TWELVE_HOUR) }
            for (set in sets) {
                val feed = build(format(set.formatTag, ClockFormat.TWELVE_HOUR), language, ClockFormat.TWELVE_HOUR, city = WorstFeeds.CITY)
                val size = WatchFeedBuilder.sizeOf(WatchFeedJson.encode(feed))
                assertTrue(size <= WatchFeedBuilder.TIMES_ALONE_MAX_BYTES, "${set.formatTag}: $size bytes")
                if (size > largest.first) largest = size to set.formatTag
            }
        }
        println("[measure] largest times-alone feed: ${largest.second}/12, ${largest.first} UTF-8 bytes of ${WatchFeedBuilder.TIMES_ALONE_MAX_BYTES}")
    }

    @Test
    fun theWorstCityIsLongerThanAnyTheBundleNames() {
        val worst = WatchFeedBuilder.sizeOf(WorstFeeds.CITY)
        assertEquals(300, worst)
        val names = BundledCities.allNames()
        assertTrue(names.size > 30_000, "${names.size} names")
        val longest = names.maxBy { WatchFeedBuilder.sizeOf(it) }
        println("[measure] longest bundled city name: ${WatchFeedBuilder.sizeOf(longest)} UTF-8 bytes")
        for (name in names) {
            assertTrue(WatchFeedBuilder.sizeOf(name) < worst, "$name takes ${WatchFeedBuilder.sizeOf(name)} bytes")
        }
    }

    // ---- Helpers

    /**
     * Every digit of every string that holds one is in [zero]'s set, and Western digits stand beside
     * no Arabic number mark (U+066B, U+066C): those go with Arabic-Indic digits alone.
     */
    private fun assertOnlyDigitsFrom(zero: Char, feed: WatchFeed) {
        val texts = feed.days.flatMap { day ->
            day.clock.all() + listOf(day.hijri, day.hijriDayMonth, day.gregorian, day.gregorianSpoken, day.dayNumber)
        } + feed.place.qiblaKm
        for (text in texts) {
            for (c in text) {
                if (Character.isDigit(c)) assertTrue(c in zero..zero + 9, "'$c' in \"$text\" is not of the set from '$zero'")
                if (zero == '0') assertTrue(c != ARABIC_DECIMAL_SEPARATOR && c != DigitGrouping.ARABIC_THOUSANDS_SEPARATOR, "an Arabic mark in \"$text\"")
            }
        }
    }

    private fun WatchSix<String>.all(): List<String> = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)

    private fun phrasesOf(presets: List<TasbeehPreset>): List<WatchPhrase> =
        presets.map { WatchPhrase(it.id, it.parts.first().dhikr.arabic, it.total) }

    /** Each field of [actual] that is not [expected]'s, one line each: what the builder writes against the sample. */
    private fun differences(label: String, expected: WatchFeed, actual: WatchFeed): List<String> {
        val out = mutableListOf<String>()
        fun check(field: String, want: Any?, got: Any?) {
            if (want != got) out += "$label $field: sample $want, phone $got"
        }
        check("days.size", expected.days.size, actual.days.size)
        expected.days.zip(actual.days).forEach { (want, got) -> dayDifferences(want, got).forEach { (field, pair) -> check("${want.date} $field", pair.first, pair.second) } }
        uiDifferences(expected.ui, actual.ui).forEach { (field, pair) -> check("ui.$field", pair.first, pair.second) }
        check("place.city", expected.place.city, actual.place.city)
        check("place.qiblaBearing", expected.place.qiblaBearing, actual.place.qiblaBearing)
        check("place.qiblaKm", expected.place.qiblaKm, actual.place.qiblaKm)
        check("place.declination", expected.place.declination, actual.place.declination)
        check("place.southern", expected.place.southern, actual.place.southern)
        check("tasbeeh", expected.tasbeeh, actual.tasbeeh)
        return out
    }

    private fun dayDifferences(want: WatchDay, got: WatchDay): List<Pair<String, Pair<Any?, Any?>>> = listOf(
        "date" to (want.date to got.date),
        "times" to (want.times to got.times),
        "clock" to (want.clock to got.clock),
        "names" to (want.names to got.names),
        "hijri" to (want.hijri to got.hijri),
        "hijriDayMonth" to (want.hijriDayMonth to got.hijriDayMonth),
        "gregorian" to (want.gregorian to got.gregorian),
        "weekday" to (want.weekday to got.weekday),
        "dayNumber" to (want.dayNumber to got.dayNumber),
        "gregorianSpoken" to (want.gregorianSpoken to got.gregorianSpoken),
        "midnight" to (want.midnight to got.midnight),
        "clockTime" to (want.clockTime to got.clockTime),
    )

    private fun uiDifferences(want: WatchUi, got: WatchUi): List<Pair<String, Pair<Any?, Any?>>> = listOf(
        "language" to (want.language to got.language),
        "rtl" to (want.rtl to got.rtl),
        "digitZero" to (want.digitZero to got.digitZero),
        "twelveHour" to (want.twelveHour to got.twelveHour),
        "am" to (want.am to got.am),
        "pm" to (want.pm to got.pm),
        "countdown" to (want.countdown to got.countdown),
        "nextIn" to (want.nextIn to got.nextIn),
        "spoken" to (want.spoken to got.spoken),
        "screens" to (want.screens to got.screens),
        "ringDigitZero" to (want.ringDigitZero to got.ringDigitZero),
        "latinScript" to (want.latinScript to got.latinScript),
        "face" to (want.face to got.face),
        "showSunrise" to (want.showSunrise to got.showSunrise),
    )

    private companion object {
        /** U+066B ARABIC DECIMAL SEPARATOR. */
        val ARABIC_DECIMAL_SEPARATOR: Char = Char(0x066B)

        /** U+00A0, which the app's 12-hour clock puts between the figures and the day period. */
        val NBSP: Char = Char(0x00A0)

        /** U+200C ZERO WIDTH NON-JOINER, inside Persian's morning marker. */
        val ZWNJ: Char = Char(0x200C)

        /** The samples' today at the place, the day of [CapeTownSample.now]. */
        const val TODAY = "2026-10-18"
    }
}
