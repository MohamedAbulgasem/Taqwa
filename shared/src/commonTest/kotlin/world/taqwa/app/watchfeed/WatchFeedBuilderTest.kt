package world.taqwa.app.watchfeed

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.feature.qibla.localizedGroupedKm
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.DigitGrouping
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.TwelveHourClock
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.twelveHourParts
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.qibla.QiblaMath
import world.taqwa.app.qibla.WorldMagneticModel
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchSpoken
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The feed the phone builds, over fixed instants and the locale-free English format: its dates,
 * its days, the place as Ruling 39 allows it, and the phrases within the byte budget. The real
 * formats and the real engine are the host tests' (`WatchFeedSampleTest`, `WatchFeedAgreesTest`).
 */
class WatchFeedBuilderTest {

    @Test
    fun theFeedRunsFromYesterdayForThirtyOneDays() {
        val feed = assertNotNull(WatchFeedBuilder.build(inputs()))
        assertEquals(WatchFeedBuilder.DAYS, feed.days.size)
        assertEquals(31, feed.days.size)
        val first = LocalDate(2026, 10, 17)
        assertEquals((0 until 31).map { first.plus(it, DateTimeUnit.DAY).toString() }, feed.days.map { it.date })
        assertEquals("2026-11-16", feed.days.last().date)
        assertEquals(WatchFeedJson.VERSION, feed.version)
        assertEquals(NOW.epochSeconds, feed.writtenAt)
    }

    @Test
    fun aDayTheEngineCannotGiveEndsTheFeed() {
        val feed = assertNotNull(
            WatchFeedBuilder.build(inputs(timesFor = timesAt(CAPE_TOWN_ZONE, throwOn = setOf(LocalDate(2026, 10, 25))))),
        )
        assertEquals(8, feed.days.size)
        assertEquals("2026-10-24", feed.days.last().date)
    }

    @Test
    fun withoutTodayThereIsNoFeed() {
        assertNull(WatchFeedBuilder.build(inputs(timesFor = timesAt(CAPE_TOWN_ZONE, throwOn = setOf(LocalDate(2026, 10, 18))))))
        // Nor without yesterday: the night before today's Fajr would have no previous start.
        assertNull(WatchFeedBuilder.build(inputs(timesFor = timesAt(CAPE_TOWN_ZONE, throwOn = setOf(LocalDate(2026, 10, 17))))))
    }

    @Test
    fun aPolarDayKeepsItsEndsMissing() {
        val polar = assertNotNull(WatchFeedBuilder.build(inputs(timesFor = timesAt(CAPE_TOWN_ZONE, ends = false))))
        for (day in polar.days) {
            assertNull(day.times.fajrEnd, day.date)
            assertNull(day.times.asrEnd, day.date)
        }
        val ordinary = assertNotNull(WatchFeedBuilder.build(inputs()))
        val sunday = ordinary.days.single { it.date == "2026-10-18" }
        val midnight = LocalDate(2026, 10, 18).atStartOfDayIn(CAPE_TOWN_ZONE).epochSeconds
        assertEquals(midnight + (6 * 60 + 1) * 60, sunday.times.fajrEnd)
        assertEquals(midnight + (19 * 60 + 2) * 60, sunday.times.asrEnd)
        assertEquals(midnight + (4 * 60 + 33) * 60, sunday.times.fajr)
        assertEquals(midnight + (20 * 60 + 24) * 60, sunday.times.isha)
        assertEquals("16:10", sunday.clock.asr)
    }

    @Test
    fun fridaysDhuhrIsJumuah() {
        val feed = assertNotNull(WatchFeedBuilder.build(inputs()))
        assertEquals("Jumuʿah", feed.days.single { it.date == "2026-10-23" }.names.dhuhr)
        assertEquals("Dhuhr", feed.days.single { it.date == "2026-10-22" }.names.dhuhr)
        assertEquals("Asr", feed.days.single { it.date == "2026-10-23" }.names.asr)
    }

    @Test
    fun theScreenReadersWordsAreSent() {
        val spoken = WatchSpoken(
            countdown = "%1\$s in %2\$s, at %3\$s",
            countdownSoon = "%1\$s in under a minute, at %2\$s",
            hoursMinutes = "%1\$s %2\$s",
            hours = listOf(WatchCount(0, "%1\$s hours"), WatchCount(1, "%1\$s hour"), WatchCount(99, "%1\$s hours")),
            minutes = listOf(WatchCount(0, "%1\$s minutes"), WatchCount(1, "%1\$s minute"), WatchCount(99, "%1\$s minutes")),
        )
        val feed = assertNotNull(WatchFeedBuilder.build(inputs(spoken = spoken)))
        assertEquals(spoken, feed.ui.spoken)
        assertNull(assertNotNull(WatchFeedBuilder.build(inputs(spoken = null))).ui.spoken)
        for (day in feed.days) {
            val date = LocalDate.parse(day.date)
            assertEquals(WatchDateLine.of(date, EnglishPlatformFormat, UiLanguage.ENGLISH).spoken, day.gregorianSpoken, day.date)
        }
        assertEquals("Sunday 18 October", feed.days.single { it.date == "2026-10-18" }.gregorianSpoken)
        assertEquals("SUN 18 OCT", feed.days.single { it.date == "2026-10-18" }.gregorian)
    }

    @Test
    fun aClockChangeInTheFeedKeepsEachDaysOwnMidnight() {
        val zone = TimeZone.of("Europe/London")
        val feed = assertNotNull(
            WatchFeedBuilder.build(inputs(location = LONDON, city = "London", timesFor = timesAt(zone))),
        )
        val the25th = feed.days.single { it.date == "2026-10-25" }
        val the26th = feed.days.single { it.date == "2026-10-26" }
        // The clocks go back at 02:00 on 25 October: that date is 25 hours long.
        assertEquals(90_000L, the26th.midnight - the25th.midnight)
        for (day in feed.days) {
            assertEquals(LocalDate.parse(day.date).atStartOfDayIn(zone).epochSeconds, day.midnight, day.date)
        }
    }

    @Test
    fun theFirstHundredPhrasesGo() {
        val presets = List(101) { TasbeehPresets.custom("custom_$it", "phrase $it", it + 1) }
        val feed = assertNotNull(WatchFeedBuilder.build(inputs(phrases = presets)))
        assertEquals(WatchFeedBuilder.MAX_PHRASES, feed.tasbeeh.size)
        feed.tasbeeh.forEachIndexed { index, phrase ->
            assertEquals("custom_$index", phrase.id)
            assertEquals("phrase $index", phrase.phrase)
            assertEquals(index + 1, phrase.target)
        }
    }

    @Test
    fun phrasesGoWhileTheyFit() {
        val presets = WorstFeeds.presets(PhraseKind.CONTROL)
        val bare = WatchFeedBuilder.sizeOf(WatchFeedJson.encode(assertNotNull(WatchFeedBuilder.build(inputs()))))
        val each = WatchFeedBuilder.sizeOf(
            WatchFeedJson.encode(assertNotNull(WatchFeedBuilder.build(inputs(phrases = presets.take(1))))),
        ) - bare

        // Ten phrases and half of one more: exactly the first ten go.
        val tenAndAHalf = bare + 10 * each + each / 2
        val ten = assertNotNull(WatchFeedBuilder.build(inputs(phrases = presets), budgetBytes = tenAndAHalf))
        assertEquals(presets.take(10).map { it.id }, ten.tasbeeh.map { it.id })
        assertTrue(WatchFeedBuilder.sizeOf(WatchFeedJson.encode(ten)) <= tenAndAHalf)
        assertTrue(WatchFeedBuilder.sizeOf(WatchFeedJson.encode(ten.withPhrasesOf(presets.take(11)))) > tenAndAHalf)

        // A budget below the times alone: the times still go, all of them, and no phrase.
        val none = assertNotNull(WatchFeedBuilder.build(inputs(phrases = presets), budgetBytes = bare - 1))
        assertEquals(31, none.days.size)
        assertTrue(none.tasbeeh.isEmpty())

        // The real budget: a prefix of the phrases, within it, and the next would not have fitted.
        val real = assertNotNull(WatchFeedBuilder.build(inputs(phrases = presets)))
        val sent = real.tasbeeh.map { it.id }
        assertEquals(presets.take(sent.size).map { it.id }, sent)
        assertTrue(WatchFeedBuilder.sizeOf(WatchFeedJson.encode(real)) <= WatchLinkContract.FEED_BUDGET_BYTES)
        assertTrue(
            sent.size == presets.size ||
                WatchFeedBuilder.sizeOf(WatchFeedJson.encode(real.withPhrasesOf(presets.take(sent.size + 1)))) >
                WatchLinkContract.FEED_BUDGET_BYTES,
        )
    }

    @Test
    fun thePlaceIsWhatRuling39Allows() {
        // Each place, its name, the Qibla's distance as the watch is to write it, and the latitude
        // and longitude to two decimals, both cut and rounded, which the feed must never hold.
        val places = listOf(
            Place("Cape Town", GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg"), "6,600", listOf("-33.92", "18.42")),
            Place("London", LONDON, "4,800", listOf("51.50", "51.51", "-0.12", "-0.13")),
            Place("Tripoli", GeoLocation(32.8872, 13.1913, "Africa/Tripoli"), "2,900", listOf("32.88", "32.89", "13.19")),
            Place("Cairo", GeoLocation(30.0444, 31.2357, "Africa/Cairo"), "1,300", listOf("30.04", "31.23", "31.24")),
            Place("Riyadh", GeoLocation(24.7136, 46.6753, "Asia/Riyadh"), "790", listOf("24.71", "46.67", "46.68")),
            Place("Madinah", GeoLocation(24.4672, 39.6111, "Asia/Riyadh"), "340", listOf("24.46", "24.47", "39.61")),
            Place("Jeddah", GeoLocation(21.5433, 39.1728, "Asia/Riyadh"), "69", listOf("21.54", "39.17")),
        )
        val today = LocalDate(2026, 10, 18)
        for (place in places) {
            val fix = place.location
            val feed = assertNotNull(
                WatchFeedBuilder.build(
                    inputs(location = fix, city = place.name, timesFor = timesAt(TimeZone.of(fix.timeZoneId))),
                ),
                place.name,
            )
            assertEquals(place.name, feed.place.city)
            assertEquals(QiblaMath.bearing(fix).toInt().toDouble(), feed.place.qiblaBearing, place.name)
            assertEquals(
                WorldMagneticModel.declinationDegrees(fix.latitude, fix.longitude, 0.0, WorldMagneticModel.decimalYear(today))
                    .roundToInt().toDouble(),
                feed.place.declination,
                place.name,
            )
            assertEquals(place.km, feed.place.qiblaKm, place.name)
            assertEquals(fix.latitude < 0, feed.place.southern, place.name)
            assertEquals(floor(feed.place.qiblaBearing), feed.place.qiblaBearing, place.name)
            assertEquals(floor(feed.place.declination), feed.place.declination, place.name)
            val json = WatchFeedJson.encode(feed)
            for (coordinate in place.coordinates) {
                assertFalse(json.contains(coordinate), "${place.name}'s feed holds $coordinate")
            }
        }
    }

    @Test
    fun theDistanceIsRoundedToTwoFigures() {
        val cases = listOf(
            6_558.3 to 6_600.0, 4_793.8 to 4_800.0, 790.3 to 790.0, 339.3 to 340.0, 68.9 to 69.0,
            99.6 to 100.0, 995.0 to 1_000.0, 12_345.0 to 12_000.0, 9.7 to 9.7, 0.4 to 0.4,
        )
        for ((km, rounded) in cases) {
            assertEquals(rounded, WatchFeedBuilder.roundedKm(km), "$km")
        }
        // Under 10 km the phone's own whole kilometre, as the Qibla card truncates it.
        assertEquals("9", localizedGroupedKm(WatchFeedBuilder.roundedKm(9.7), EnglishPlatformFormat))
        assertEquals("0", localizedGroupedKm(WatchFeedBuilder.roundedKm(0.4), EnglishPlatformFormat))
        assertEquals("6,600", localizedGroupedKm(WatchFeedBuilder.roundedKm(6_558.3), EnglishPlatformFormat))
    }

    @Test
    fun aDistanceInWesternDigitsTakesTheInterfaceLanguagesMark() {
        val narrowSpace = DigitGrouping.NARROW_NO_BREAK_SPACE
        // A pair the face was not built for is Western throughout (the Lead's call 3), its mark
        // included: the one DigitGrouping gives Western digits in the interface language, never
        // U+066C, the mark of the Arabic-Indic digits the feed no longer holds.
        val western = listOf(
            Triple(DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-SA"), UiLanguage.ENGLISH, "6,600"),
            Triple(DigitSetFormat(DigitSets.EXTENDED_ARABIC_INDIC, "fa-IR"), UiLanguage.ENGLISH, "6,600"),
            Triple(DigitSetFormat(DigitSets.EXTENDED_ARABIC_INDIC, "ur-PK-u-nu-arabext"), UiLanguage.ENGLISH, "6,600"),
            Triple(DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-SA"), UiLanguage.FRENCH, "6${narrowSpace}600"),
            Triple(DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-EG"), UiLanguage.TURKISH, "6.600"),
            Triple(DigitSetFormat(DigitSets.DEVANAGARI, "mr-IN"), UiLanguage.ENGLISH, "6,600"),
            Triple(DigitSetFormat(DigitSets.BENGALI, "bn-BD"), UiLanguage.ENGLISH, "6,600"),
        )
        for ((format, language, km) in western) {
            val feed = assertNotNull(WatchFeedBuilder.build(inputs(language = language, format = format)))
            assertEquals('0', feed.ui.digitZero, "${format.languageTag()} with ${language.code}")
            assertEquals(km, feed.place.qiblaKm, "${format.languageTag()} with ${language.code}")
        }
        // A pair the face draws keeps the phone's own Qibla card string, its mark included.
        val kept = listOf(
            DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-EG") to UiLanguage.ARABIC,
            DigitSetFormat(DigitSets.EXTENDED_ARABIC_INDIC, "ur-PK-u-nu-arabext") to UiLanguage.URDU,
            DigitSetFormat(DigitSets.BENGALI, "bn-BD") to UiLanguage.BENGALI,
            DigitSetFormat(DigitSets.WESTERN, "fr-FR") to UiLanguage.ARABIC,
            DigitSetFormat(DigitSets.WESTERN, "tr-TR") to UiLanguage.ENGLISH,
        )
        for ((format, language) in kept) {
            val feed = assertNotNull(WatchFeedBuilder.build(inputs(language = language, format = format)))
            assertEquals(localizedGroupedKm(6_600.0, format), feed.place.qiblaKm, "${format.languageTag()} with ${language.code}")
        }
        assertEquals(
            "${DigitSets.ARABIC_INDIC[6]}${DigitGrouping.ARABIC_THOUSANDS_SEPARATOR}${DigitSets.ARABIC_INDIC[6]}${DigitSets.ARABIC_INDIC[0]}${DigitSets.ARABIC_INDIC[0]}",
            localizedGroupedKm(6_600.0, DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-EG")),
        )
        assertEquals("6${narrowSpace}600", localizedGroupedKm(6_600.0, DigitSetFormat(DigitSets.WESTERN, "fr-FR")))
    }

    @Test
    fun theWatchShowsSunriseOnlyWhenThePhoneDoes() {
        // Show sunrise is off until the user turns it on (PrayerSettings.showSunrise), and both
        // watches follow it (the owner's decision of 9 November 2026).
        val off = assertNotNull(WatchFeedBuilder.build(inputs()))
        assertFalse(off.ui.showSunrise)
        val on = assertNotNull(WatchFeedBuilder.build(inputs(prayerSettings = PrayerSettings(showSunrise = true))))
        assertTrue(on.ui.showSunrise)
        // Nothing else moves: every day keeps its Sunrise, its name, its clock and its end of Fajr.
        assertEquals("6:01", on.days[1].clock.sunrise)
        assertEquals("Sunrise", on.days[1].names.sunrise)
        assertEquals(on.copy(ui = on.ui.copy(showSunrise = false)), off)
        // So the two feeds have two names, and turning it on or off sends the watch the feed again
        // (the sync compares the names, WatchFeedSync step 4).
        assertNotEquals(watchFeedFingerprint(on), watchFeedFingerprint(off))
    }

    @Test
    fun theScreensWordsGoAsTheyAre() {
        val words = WatchScreenWords(round = "TOUR %1\$s", bezelHint = "Tournez la lunette")
        assertEquals(words, assertNotNull(WatchFeedBuilder.build(inputs(screens = words))).ui.screens)
        assertEquals(WatchScreenWords.ENGLISH, assertNotNull(WatchFeedBuilder.build(inputs())).ui.screens)
    }

    @Test
    fun aTwelveHourPhoneSendsItsOwnClockTimes() {
        val twelve = ClockFormat.TWELVE_HOUR
        val english = assertNotNull(
            WatchFeedBuilder.build(inputs(format = TwelveHourFormat(DigitSets.WESTERN, "en", "AM", "PM"), clockFormat = twelve)),
        )
        for (day in english.days) {
            // The app's own clockTime, day period and all, for all six times: Sunrise's too.
            assertEquals(
                WatchSix("4:33${NBSP}AM", "6:01${NBSP}AM", "12:33${NBSP}PM", "4:10${NBSP}PM", "7:04${NBSP}PM", "8:24${NBSP}PM"),
                day.clockTime,
                day.date,
            )
            // The face's bare form beside it, unchanged.
            assertEquals(WatchSix("4:33", "6:01", "12:33", "4:10", "7:04", "8:24"), day.clock, day.date)
        }

        // Turkish puts its marker first, as the app writes it.
        val turkish = assertNotNull(
            WatchFeedBuilder.build(
                inputs(
                    language = UiLanguage.TURKISH,
                    format = TwelveHourFormat(DigitSets.WESTERN, "tr-TR", "ÖÖ", "ÖS"),
                    clockFormat = twelve,
                ),
            ),
        )
        assertEquals("ÖS${NBSP}4:10", turkish.days[1].clockTime?.asr)
        assertEquals("ÖÖ${NBSP}6:01", turkish.days[1].clockTime?.sunrise)

        // The feed's digit rule: a Saudi phone's own digits in Arabic, Western ones in English.
        val saudi = TwelveHourFormat(DigitSets.ARABIC_INDIC, "ar-SA", "ص", "م")
        val arabic = assertNotNull(WatchFeedBuilder.build(inputs(language = UiLanguage.ARABIC, format = saudi, clockFormat = twelve)))
        assertEquals("٤:١٠${NBSP}م", arabic.days[1].clockTime?.asr)
        assertEquals("١٢:٣٣${NBSP}م", arabic.days[1].clockTime?.dhuhr)
        val englishOnSaudi = assertNotNull(WatchFeedBuilder.build(inputs(format = saudi, clockFormat = twelve)))
        assertEquals("4:10${NBSP}م", englishOnSaudi.days[1].clockTime?.asr)
    }

    @Test
    fun aTwentyFourHourPhoneSendsNone() {
        val feed = assertNotNull(WatchFeedBuilder.build(inputs()))
        assertEquals(31, feed.days.size)
        for (day in feed.days) assertNull(day.clockTime, day.date)
        val arabic = assertNotNull(
            WatchFeedBuilder.build(inputs(language = UiLanguage.ARABIC, format = DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-SA"))),
        )
        for (day in arabic.days) assertNull(day.clockTime, day.date)
    }

    @Test
    fun aSaudiPhonesRingIsWestern() {
        // The phone's ring keeps Western digits where its clock strings do not
        // (CountdownDigits.westernFallback): ar-SA, ar-EG and Bengali.
        val western = listOf(
            Triple(DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-SA"), UiLanguage.ARABIC, Char(0x0660)),
            Triple(DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-EG"), UiLanguage.ARABIC, Char(0x0660)),
            Triple(DigitSetFormat(DigitSets.BENGALI, "bn-BD"), UiLanguage.BENGALI, Char(0x09E6)),
        )
        for ((format, language, zero) in western) {
            val feed = assertNotNull(WatchFeedBuilder.build(inputs(language = language, format = format)))
            assertEquals(zero, feed.ui.digitZero, format.languageTag())
            assertEquals('0', feed.ui.ringDigitZero, format.languageTag())
        }
        // Null wherever the ring writes the feed's own digits (Ruling 47): an English or Libyan
        // phone, a Libyan phone set to Arabic-Indic digits (its ring keeps them), an Urdu phone in
        // its own digits, and an English interface on a Saudi phone, whose feed is Western already.
        val same = listOf(
            EnglishPlatformFormat to UiLanguage.ENGLISH,
            DigitSetFormat(DigitSets.WESTERN, "ar-LY") to UiLanguage.ARABIC,
            DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-LY-u-nu-arab") to UiLanguage.ARABIC,
            DigitSetFormat(DigitSets.EXTENDED_ARABIC_INDIC, "ur-PK-u-nu-arabext") to UiLanguage.URDU,
            DigitSetFormat(DigitSets.ARABIC_INDIC, "ar-SA") to UiLanguage.ENGLISH,
            DigitSetFormat(DigitSets.BENGALI, "bn-BD") to UiLanguage.ENGLISH,
        )
        for ((format, language) in same) {
            val feed = assertNotNull(WatchFeedBuilder.build(inputs(language = language, format = format)))
            assertNull(feed.ui.ringDigitZero, "${format.languageTag()} with ${language.code}")
        }
    }

    @Test
    fun aBengaliPhoneHidesTheTransliteration() {
        // Transliterations, and capitals, only under a Latin-script interface.
        val latin = mapOf(
            UiLanguage.ENGLISH to true,
            UiLanguage.FRENCH to true,
            UiLanguage.TURKISH to true,
            UiLanguage.INDONESIAN to true,
            UiLanguage.ARABIC to false,
            UiLanguage.URDU to false,
            UiLanguage.BENGALI to false,
        )
        assertEquals(UiLanguage.entries.toSet(), latin.keys)
        for ((language, expected) in latin) {
            val feed = assertNotNull(WatchFeedBuilder.build(inputs(language = language)))
            assertEquals(expected, feed.ui.latinScript, language.code)
        }
        val bengaliPhone = DigitSetFormat(DigitSets.BENGALI, "bn-BD")
        assertFalse(assertNotNull(WatchFeedBuilder.build(inputs(language = UiLanguage.BENGALI, format = bengaliPhone))).ui.latinScript)
    }

    @Test
    fun theSizeIsTheUtf8Bytes() {
        assertEquals(3, WatchFeedBuilder.sizeOf("abc"))
        assertEquals(2, WatchFeedBuilder.sizeOf("ا"))
        assertEquals(3, WatchFeedBuilder.sizeOf("ক"))
        assertEquals(4, WatchFeedBuilder.sizeOf(charArrayOf(Char(0xD83D), Char(0xDCFF)).concatToString()))
    }

    private class Place(val name: String, val location: GeoLocation, val km: String, val coordinates: List<String>)

    /**
     * A 12-hour format as the app's own are made: its numbers in [digits], each time joined by
     * `TwelveHourClock.render` with its [am] or [pm] marker (marker first for Turkish), and the
     * bare figures for the face.
     */
    private class TwelveHourFormat(
        digits: List<String>,
        private val tag: String,
        private val am: String,
        private val pm: String,
    ) : PlatformFormat {
        private val numbers = DigitSetFormat(digits, tag)
        override fun languageTag(): String = tag
        override fun localizedDigits(number: Int): String = numbers.localizedDigits(number)
        override fun clockTime(hour: Int, minute: Int): String {
            val (h, afternoon) = twelveHourParts(hour)
            return TwelveHourClock.render(
                localizedDigits(h),
                localizedDigits(minute / 10) + localizedDigits(minute % 10),
                if (afternoon) pm else am,
                TwelveHourClock.markerFirst(tag),
            )
        }
        override fun clockFigures(hour: Int, minute: Int): String {
            val h = twelveHourParts(hour).first
            return "${localizedDigits(h)}:${localizedDigits(minute / 10)}${localizedDigits(minute % 10)}"
        }
        override fun dayPeriods(language: UiLanguage): Pair<String, String> = am to pm
    }

    private fun WatchFeed.withPhrasesOf(presets: List<TasbeehPreset>): WatchFeed =
        copy(tasbeeh = presets.map { world.taqwa.app.watch.WatchPhrase(it.id, it.parts.first().dhikr.arabic, it.total) })

    private companion object {
        /** U+00A0, which the app writes between a 12-hour time and its day period. */
        val NBSP: Char = Char(0x00A0)

        /** Sunday 18 October 2026, 08:00 in Cape Town: the samples' `writtenAt`. */
        val NOW: Instant = Instant.fromEpochSeconds(1_792_303_200L)

        val CAPE_TOWN_ZONE: TimeZone = TimeZone.of("Africa/Johannesburg")
        val CAPE_TOWN = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", cityName = "Cape Town", cityId = 3369157)
        val LONDON = GeoLocation(51.5074, -0.1278, "Europe/London")

        fun inputs(
            location: GeoLocation = CAPE_TOWN,
            city: String = "Cape Town",
            language: UiLanguage = UiLanguage.ENGLISH,
            spoken: WatchSpoken? = null,
            screens: WatchScreenWords = WatchScreenWords.ENGLISH,
            format: PlatformFormat = EnglishPlatformFormat,
            clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR,
            phrases: List<TasbeehPreset> = emptyList(),
            now: Instant = NOW,
            timesFor: (LocalDate) -> DayPrayerTimes = timesAt(CAPE_TOWN_ZONE),
            prayerSettings: PrayerSettings = PrayerSettings(),
        ) = WatchFeedInputs(
            location = location,
            city = city,
            language = language,
            spoken = spoken,
            screens = screens,
            prayerSettings = prayerSettings,
            format = format,
            clockFormat = clockFormat,
            phrases = phrases,
            now = now,
            timesFor = timesFor,
        )

        /**
         * The mockups' day on every date, at the place's own midnight in [zone]: Fajr 04:33, Sunrise
         * 06:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04 and Isha 20:24, Fajr ending at sunrise and
         * Asr at 19:02 (unless the day has no [ends], as a polar day may not). A date in [throwOn]
         * is one the engine cannot give.
         */
        fun timesAt(
            zone: TimeZone,
            ends: Boolean = true,
            throwOn: Set<LocalDate> = emptySet(),
        ): (LocalDate) -> DayPrayerTimes = { date ->
            if (date in throwOn) throw IllegalStateException("no times for $date")
            val midnight = date.atStartOfDayIn(zone)
            fun at(hour: Int, minute: Int): Instant = midnight + (hour * 60 + minute).minutes
            DayPrayerTimes(
                date = date,
                times = listOf(
                    PrayerTime(Prayer.FAJR, at(4, 33)),
                    PrayerTime(Prayer.SUNRISE, at(6, 1)),
                    PrayerTime(Prayer.DHUHR, at(12, 33)),
                    PrayerTime(Prayer.ASR, at(16, 10)),
                    PrayerTime(Prayer.MAGHRIB, at(19, 4)),
                    PrayerTime(Prayer.ISHA, at(20, 24)),
                ),
                highLatitudeRuleApplied = null,
                asrOther = at(17, 0),
                sunset = at(19, 2),
                endOfEating = at(4, 33),
                ends = if (ends) mapOf(Prayer.FAJR to at(6, 1), Prayer.ASR to at(19, 2)) else emptyMap(),
                sourceEntryId = "test",
                entryClass = EntryClass.D_NONE,
            )
        }
    }
}
