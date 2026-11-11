package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.TestFeeds.at
import world.taqwa.app.wear.tile.PhoneFeeds

/**
 * What the sources give a screen reader (Task 12), on the sample's Sunday 18 October 2026 in Cape
 * Town and the debug build's other samples, each in the phone's own words: the words they build, and
 * the moving part read back as the system works it out ([DynamicEvaluator]).
 *
 * On the Taqwa face a screen reader reads the watch's time (the runtime's own item), then slot 0,
 * the next prayer's ring ([nextPrayerSaid], the phone's spoken sentence), then slot 2, the dates
 * ([ComplicationFields.datesSpoken]), then the wearer's slots; the face's other slots have no room
 * and are not read (`SourceManifestTest`).
 */
class SpokenTest {

    private val english = TestFeeds.sample
    private val english12 = TestFeeds.other("en12")
    private val libya = TestFeeds.other("ar-ly12")
    private val saudi = TestFeeds.other("ar-sa")

    /** What the ring and the long text say for the period [now] is in, as the system works it out [later] seconds on. */
    private fun prayerSays(feed: WatchFeed, now: Long, later: Long = 0): String {
        val fields = assertNotNull(ComplicationFields.nextPrayer(feed, now))
        val said = nextPrayerSaid(fields, feed, fields.endAt - now, sdk = 33)
        return DynamicEvaluator(now + later).string(said.expression().toDynamicStringProto())
    }

    private fun datesSay(feed: WatchFeed, now: Long): String =
        ComplicationFields.datesSpoken(assertNotNull(ComplicationFields.dates(feed, now)), feed.ui)

    /** [feed] as a phone sent it before the spoken words: none for the countdown, none for the date. */
    private fun beforeSpoken(feed: WatchFeed): WatchFeed =
        feed.copy(ui = feed.ui.copy(spoken = null), days = feed.days.map { it.copy(gregorianSpoken = "") })

    // ---- The next prayer: the phone's spoken sentence

    @Test
    fun thePrayerTheTimeLeftAndItsTimeInEnglishWords() {
        // The phone's prayer_a11y_countdown, with its a11y_hours and a11y_minutes: never "h" and "min".
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", prayerSays(english, at(0, 14, 58)))
        // Before the system has worked the countdown out, the reader is given the same words.
        val fields = assertNotNull(ComplicationFields.nextPrayer(english, at(0, 14, 58)))
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", nextPrayerSaid(fields, english, fields.endAt - at(0, 14, 58), sdk = 33).fallback())
    }

    @Test
    fun theTimeLeftMovesAMinuteAtATimeInThePluralItTakes() {
        val now = at(0, 14, 58)
        assertEquals("Asr in 1 hour 11 minutes, at 16:10", prayerSays(english, now, later = 60))
        assertEquals("Asr in 1 hour 1 minute, at 16:10", prayerSays(english, now, later = 11 * 60))
        assertEquals("Asr in 1 hour, at 16:10", prayerSays(english, now, later = 12 * 60))
        assertEquals("Asr in 40 minutes, at 16:10", prayerSays(english, now, later = 32 * 60))
        assertEquals("Asr in 1 minute, at 16:10", prayerSays(english, now, later = 71 * 60))
        assertEquals("Asr in under a minute, at 16:10", prayerSays(english, now, later = 71 * 60 + 30))
    }

    @Test
    fun theLineSaysWhatTheRulesSayAtEveryStepOfTwoDays() {
        // Every step of the plan from the night before on, at its start and a few minutes into it, in
        // the four samples: the sentence is the rules' own, and so are the screen's words of a feed
        // written before the spoken ones.
        for (sample in listOf(english, english12, libya, saudi)) {
            for (feed in listOf(sample, beforeSpoken(sample))) {
                val separator = ComplicationFields.spokenSeparator(feed.ui)
                for (step in ComplicationTimelines.nextPrayer(feed, at(0, 0, 30))) {
                    val fields = step.fields ?: continue
                    val said = nextPrayerSaid(fields, feed, fields.endAt - step.from, sdk = 33).expression().toDynamicStringProto()
                    for (into in listOf(0L, 7 * 60L, 61 * 60L)) {
                        val moment = step.from + into
                        if (moment >= step.until) continue
                        val expected = WatchRules.spokenCountdown(feed, fields.name, fields.spokenTime, fields.endAt - moment)
                            ?: (ComplicationFields.prayerSpoken(fields) + separator + WatchRules.countdown(feed, fields.endAt - moment))
                        assertEquals(expected, DynamicEvaluator(moment).string(said), "${feed.ui.language} at $moment")
                    }
                }
            }
        }
    }

    @Test
    fun everyMinuteOfTheLongestPeriodTakesThePhrasesTheRulesChoose() {
        // The night, Isha to Fajr, 8 h 9 min: every number of hours from 8 down and of minutes from
        // 59 down is said, in English and in Arabic, whose two, few (3 to 10) and many (11 and on)
        // each take their own words.
        for (feed in listOf(english, saudi)) {
            val now = at(0, 20, 30)
            val fields = assertNotNull(ComplicationFields.nextPrayer(feed, now))
            val said = nextPrayerSaid(fields, feed, fields.endAt - now, sdk = 33).expression().toDynamicStringProto()
            var moment = now
            while (moment < fields.endAt) {
                assertEquals(
                    WatchRules.spokenCountdown(feed, fields.name, fields.time, fields.endAt - moment),
                    DynamicEvaluator(moment).string(said),
                    "${feed.ui.language} at $moment",
                )
                moment += 60
            }
        }
    }

    @Test
    fun theNamesAreThePhonesAndNeverTheFacesCapitals() {
        // A speech engine may spell DHUHR out letter by letter; the phone wrote Dhuhr.
        assertEquals("Dhuhr in 2 hours 33 minutes, at 12:33", prayerSays(english, at(0, 10, 0)))
        assertEquals("Jumuʿah in 2 hours 33 minutes, at 12:33", prayerSays(english, at(5, 10, 0)))
        assertEquals("Fajr in 7 hours 33 minutes, at 4:33", prayerSays(english, at(0, 21, 0)))
        val fields = assertNotNull(ComplicationFields.nextPrayer(english, at(0, 14, 58)))
        assertEquals("ASR", fields.label)
        assertEquals("Asr, 16:10", ComplicationFields.prayerSpoken(fields))
    }

    @Test
    fun onTwelveHoursTheTimeIsThePhones() {
        assertEquals("Asr in 1 hour 12 minutes, at " + FSI + "4:10${NBSP}PM" + PDI, prayerSays(english12, at(0, 14, 58)))
    }

    @Test
    fun inArabicThePhonesWordsAndDigitsAndNoLetterAlone() {
        // The bare س and د of the screen would be read as the letters' names.
        // Libya: Western digits, on twelve hours.
        assertEquals("العصر بعد 1 ساعة و12 دقيقة، الساعة " + FSI + "4:10${NBSP}م" + PDI, prayerSays(libya, at(0, 14, 58)))
        // Saudi Arabia: the phone's Arabic-Indic digits, every one of them, so that the reader says
        // the numbers the phone wrote and no digit is another set's.
        assertEquals("العصر بعد ١ ساعة و١٢ دقيقة، الساعة ١٦:١٠", prayerSays(saudi, at(0, 14, 58)))
        assertEquals("العصر بعد ٤٠ دقيقة، الساعة ١٦:١٠", prayerSays(saudi, at(0, 14, 58), later = 32 * 60))
        assertEquals("العصر بعد ٢ دقيقتين، الساعة ١٦:١٠", prayerSays(saudi, at(0, 14, 58), later = 70 * 60))
        assertEquals("العصر بعد أقل من دقيقة، الساعة ١٦:١٠", prayerSays(saudi, at(0, 14, 58), later = 71 * 60 + 30))
        for (said in listOf(prayerSays(libya, at(0, 14, 58)), prayerSays(saudi, at(0, 14, 58)))) {
            assertTrue(said.split(' ', '،').none { it == "س" || it == "د" }, said)
        }
    }

    @Test
    fun aFeedWrittenBeforeTheSpokenWordsSaysTheScreensWords() {
        assertEquals("Asr, 16:10, in 1 h 12 min", prayerSays(beforeSpoken(english), at(0, 14, 58)))
        assertEquals("العصر، ١٦:١٠، بعد ١ س ١٢ د", prayerSays(beforeSpoken(saudi), at(0, 14, 58)))
    }

    @Test
    fun thePrayerAndItsTimeAreJoinedByThePhonesCommaInEveryLanguage() {
        // Ruling 74 item 1: what the complications say of the prayer and its time joins as the Tile
        // and Today do, the name, the phone's comma and the time: the Arabic comma for the two
        // languages written right to left, Arabic and Urdu, and ", " for the five others, on both
        // clocks. The literal lines are `ClockFormTest`'s and `ComplicationFieldsTest`'s.
        for (phone in PhoneFeeds.ALL) {
            val feed = PhoneFeeds.localised(english, phone)
            val rightToLeft = phone.tag.substringBefore('-') in setOf("ar", "ur")
            assertEquals(rightToLeft, feed.ui.rtl, "$phone")
            val comma = if (rightToLeft) "، " else ", "
            for (moment in listOf(at(0, 3, 0), at(0, 10, 0), at(0, 14, 58), at(0, 19, 30), at(5, 10, 0))) {
                val fields = assertNotNull(ComplicationFields.nextPrayer(feed, moment), "$phone at $moment")
                val said = ComplicationFields.prayerSpoken(fields)
                assertEquals(fields.name + comma + fields.spokenTime, said, "$phone at $moment")
                // Below API 33 it is all the ring and the long text say; the short text says it at
                // every level.
                assertEquals(said, nextPrayerSaid(fields, feed, fields.endAt - moment, sdk = 30).fallback(), "$phone at $moment")
            }
        }
    }

    @Test
    fun theSpokenSentenceIsSmallEnoughToSendTenAtATime() {
        // The night's countdown, from 20:30, is about 4.5 KB of protobuf, which the ring and the long
        // text carry twice, in the text or value and in the description. The sentence's words for
        // each number add to the second copy about a tenth in English (4.9 KB) and a quarter in
        // Arabic (5.6 KB), and a timeline of ten periods stays a few tens of kilobytes, far inside
        // what the system is sent.
        for (feed in listOf(english, saudi)) {
            val now = at(0, 20, 30)
            val fields = assertNotNull(ComplicationFields.nextPrayer(feed, now))
            val countdown = CountdownExpression.countdown(feed.ui.countdown, feed.ui.digitZero, fields.endAt, fields.endAt - now)
                .toDynamicStringProto().serializedSize
            val said = nextPrayerSaid(fields, feed, fields.endAt - now, sdk = 33).expression().toDynamicStringProto().serializedSize
            assertTrue(said < countdown * 3 / 2, "${feed.ui.language}: the spoken sentence is $said bytes beside the countdown's $countdown")
            assertTrue(said < 8_000, "${feed.ui.language}: the spoken sentence is $said bytes")
        }
    }

    // ---- The dates

    @Test
    fun theDatesAreTheFullHijriDateAndTheGregorianInWords() {
        // The face draws the Hijri day and month and SUN 18 OCT; the reader is told the Prayer
        // screen's full Hijri date and the Gregorian in words, never in the face's capitals.
        assertEquals("6 Jumada al-Ula 1448, Sunday 18 October", datesSay(english, at(0, 14, 58)))
        assertEquals("6 جمادى الأولى 1448، الأحد 18 أكتوبر", datesSay(libya, at(0, 14, 58)))
        assertEquals("٦ جمادى الأولى ١٤٤٨، الأحد ١٨ أكتوبر", datesSay(saudi, at(0, 14, 58)))
        // A feed written before the spoken date says the face's line as it is.
        assertEquals("6 Jumada al-Ula 1448, SUN 18 OCT", datesSay(beforeSpoken(english), at(0, 14, 58)))
    }

    @Test
    fun theDatesTurnOverAtMidnight() {
        assertEquals("6 Jumada al-Ula 1448, Sunday 18 October", datesSay(english, at(0, 23, 59)))
        assertEquals("7 Jumada al-Ula 1448, Monday 19 October", datesSay(english, at(1, 0, 0)))
    }

    @Test
    fun aSpentFeedHasNothingToSay() {
        val past = english.days.last().times.isha + 1
        kotlin.test.assertNull(ComplicationFields.nextPrayer(english, past))
        kotlin.test.assertNull(ComplicationFields.dates(english, past))
    }

    // ---- The day dial, on a runtime that gives a reader every slot

    @Test
    fun theDayDialSaysTheDatesSixTimesInThePhonesWords() {
        assertEquals(
            "Fajr 4:33, Sunrise 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04, Isha 20:24",
            ComplicationFields.dialSpoken(english, at(0, 14, 58)),
        )
        assertEquals(
            "الفجر ٤:٣٣، الشروق ٦:٠١، الظهر ١٢:٣٣، العصر ١٦:١٠، المغرب ١٩:٠٤، العشاء ٢٠:٢٤",
            ComplicationFields.dialSpoken(saudi, at(0, 14, 58)),
        )
    }

    private companion object {
        val NBSP = Char(0xA0)
        val FSI = Char(0x2068)
        val PDI = Char(0x2069)
    }
}
