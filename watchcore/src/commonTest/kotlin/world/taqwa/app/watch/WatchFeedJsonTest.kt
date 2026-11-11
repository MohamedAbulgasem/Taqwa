package world.taqwa.app.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchFeedJsonTest {

    @Test
    fun roundTripKeepsEveryField() {
        val withoutPhrases = english.copy(tasbeeh = emptyList())
        val worded = english.copy(ui = english.ui.copy(screens = WatchScreenWords.ENGLISH))
        val noSunrise = english.copy(ui = english.ui.copy(showSunrise = false))

        mapOf(
            "English" to english,
            "English with its words" to worded,
            "English without Sunrise" to noSunrise,
            "Arabic" to arabic,
            "No phrases" to withoutPhrases,
        ).forEach { (which, feed) ->
            val json = WatchFeedJson.encode(feed)
            val decoded = assertNotNull(WatchFeedJson.decode(json), which)

            assertEquals(feed, decoded, which)
            // And back to the same bytes. The phone decides whether a feed has changed, and so
            // whether to send it, by comparing encodings (spec 1.2), which needs one feed to
            // always encode one way.
            assertEquals(json, WatchFeedJson.encode(decoded), which)
        }

        // A key left at its default is still written, so the Swift reader never has to guess
        // which keys a given phone leaves out.
        assertTrue("\"tasbeeh\":[]" in WatchFeedJson.encode(withoutPhrases), "an empty list of phrases is written")
    }

    @Test
    fun unknownKeysAreIgnored() {
        // A later build adds a key at the top and one to each day; this build reads the rest.
        val feed = WatchFeedJson.decode(fridayDocument(futureKeys = true))

        assertEquals(englishFriday, feed)
    }

    @Test
    fun unknownKeysAreIgnoredAtEveryDepth() {
        // And one inside the times, the clock, the names, the place, the ui and its countdown, and
        // a phrase, each an object of its own: this build reads the rest of every one of them.
        val feed = WatchFeedJson.decode(fridayDocument(futureKeys = true, deepFutureKeys = true))

        assertEquals(englishFriday, feed)
    }

    @Test
    fun aFeedWithoutPhrasesReadsAsNone() {
        // A phone with no phrases of its own may leave the key out: no phrases, not a broken feed.
        val feed = assertNotNull(WatchFeedJson.decode(fridayDocument(tasbeeh = false)))

        assertEquals(englishFriday.copy(tasbeeh = emptyList()), feed)
    }

    @Test
    fun aNewerVersionDecodesToNull() {
        // The same document reads at this build's version, so it is the version that stops it.
        assertNotNull(WatchFeedJson.decode(fridayDocument(version = 1)))
        assertNull(WatchFeedJson.decode(fridayDocument(version = 2)))
        assertNull(WatchFeedJson.decode(fridayDocument(version = 12)))
        // A newer feed need not even have this shape. That is the same answer, not a crash.
        assertNull(WatchFeedJson.decode("""{"version": 2, "layout": "something else"}"""))
    }

    @Test
    fun aVersionBelowOneDecodesToNull() {
        // No phone writes a version below 1, so such a document is broken, not an older feed: the
        // watch keeps what it had and says nothing (versionOf gives 0 or less, not a newer version).
        assertNull(WatchFeedJson.decode(fridayDocument(version = 0)), "version 0")
        assertNull(WatchFeedJson.decode(fridayDocument(version = -1)), "version -1")
        assertEquals(0, WatchFeedJson.versionOf(fridayDocument(version = 0)))
    }

    @Test
    fun malformedJsonDecodesToNull() {
        val whole = WatchFeedJson.encode(english)
        mapOf(
            "" to "nothing at all",
            "not json" to "not JSON",
            whole.dropLast(1) to "cut short by its last character",
            whole.take(whole.length / 2) to "cut in half",
            whole + "}" to "a stray character after the feed",
            "[]" to "JSON, but not an object",
            "null" to "JSON null",
            "{}" to "an object with none of the feed's fields",
            """{"version": 1}""" to "a version and nothing else",
            """{"version": 1, "writtenAt": 0, "days": "none", "place": {}, "ui": {}}""" to "days that are not a list",
        ).forEach { (text, what) ->
            assertNull(WatchFeedJson.decode(text), what)
        }
    }

    @Test
    fun missingEndsDecodeAsNull() {
        // A polar day: the sun neither rises nor sets, so there is no sunrise to end Fajr and no
        // sunset to end Asr, and the feed has no end to give.
        val polarTimes = friday.times.copy(fajrEnd = null, asrEnd = null)

        // Left out, or written as null, which is what the encoder does.
        listOf("", ", \"fajrEnd\": null, \"asrEnd\": null").forEach { ends ->
            val feed = assertNotNull(WatchFeedJson.decode(fridayDocument(ends = ends)), ends)

            assertEquals(polarTimes, feed.days.single().times, ends)
        }

        // Either may be missing without the other (spec 1.1).
        val onlyFajrEnd = assertNotNull(WatchFeedJson.decode(fridayDocument(ends = ", \"fajrEnd\": 1792727820")))
        assertEquals(friday.times.copy(asrEnd = null), onlyFajrEnd.days.single().times)
        val onlyAsrEnd = assertNotNull(WatchFeedJson.decode(fridayDocument(ends = ", \"asrEnd\": 1792775160")))
        assertEquals(friday.times.copy(fajrEnd = null), onlyAsrEnd.days.single().times)

        // And the encoder's own output for such a day reads back the same, with the ends written
        // as null rather than left out.
        val polar = english.copy(days = listOf(friday.copy(times = polarTimes)))
        val written = WatchFeedJson.encode(polar)
        assertTrue("\"fajrEnd\":null,\"asrEnd\":null" in written, written)
        assertEquals(polar, WatchFeedJson.decode(written))
    }

    @Test
    fun aDayWithoutMidnightReadsAsUnknown() {
        // A feed written before the key, or by a phone that could not say, has no midnight: the day
        // reads it as 0, which the rules take to mean unknown.
        val unsaid = assertNotNull(WatchFeedJson.decode(fridayDocument(midnight = "")))
        assertEquals(0L, unsaid.days.single().midnight)
        assertEquals(friday.copy(midnight = 0L), unsaid.days.single())

        // Written, it is the value as it stands: the date's local midnight, here Friday 23 October
        // at UTC+2.
        val said = assertNotNull(WatchFeedJson.decode(fridayDocument()))
        assertEquals(1792706400L, said.days.single().midnight)

        // And a day left at 0 is still written, as 0, never left out.
        val written = WatchFeedJson.encode(english.copy(days = listOf(friday.copy(midnight = 0L))))
        assertTrue("\"midnight\":0," in written, written)
        assertTrue("\"midnight\":1792620000," in WatchFeedJson.encode(english), "Thursday's midnight")
    }

    @Test
    fun aDayWithoutHijriDayMonthReadsAsEmpty() {
        // Ruling 24's yearless form came after the first feeds: a day written before the key reads
        // it as empty, and the watch then shows the full Hijri date as it is.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument(hijriDayMonth = "")))
        assertEquals("", before.days.single().hijriDayMonth)
        assertEquals(friday.copy(hijriDayMonth = ""), before.days.single())

        // Written, it is the day and the month as the phone wrote them, and it is always on the
        // wire, empty or not.
        assertEquals("11 Jumada al-Ula", assertNotNull(WatchFeedJson.decode(fridayDocument())).days.single().hijriDayMonth)
        val empty = WatchFeedJson.encode(english.copy(days = listOf(friday.copy(hijriDayMonth = ""))))
        assertTrue("\"hijriDayMonth\":\"\"," in empty, empty)
    }

    @Test
    fun aDayWithoutItsSpokenDateReadsAsEmpty() {
        // The spoken Gregorian came with Task 12 of plan 1: a day written before the key reads it as
        // empty, and the watch then says the face's line as it is.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument(gregorianSpoken = "")))
        assertEquals(friday.copy(gregorianSpoken = ""), before.days.single())
        assertEquals("Friday 23 October", assertNotNull(WatchFeedJson.decode(fridayDocument())).days.single().gregorianSpoken)
        val empty = WatchFeedJson.encode(english.copy(days = listOf(friday.copy(gregorianSpoken = ""))))
        assertTrue("\"gregorianSpoken\":\"\"," in empty, empty)
    }

    @Test
    fun aFeedWithoutSpokenWordsReadsAsNone() {
        // A feed written before the spoken words has none: the watch then says the screen's words.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument(spoken = "")))
        assertEquals(englishFriday.copy(ui = english.ui.copy(spoken = null)), before)
        // Written, they are the phone's phrases and the runs of numbers each takes, in order.
        val said = assertNotNull(assertNotNull(WatchFeedJson.decode(fridayDocument())).ui.spoken)
        assertEquals(listOf(0, 1, 99), said.hours.map { it.upTo })
        assertEquals("%1\$s hour", said.hours[1].phrase)
        // And none is still written, as null, never left out.
        val none = WatchFeedJson.encode(english.copy(ui = english.ui.copy(spoken = null)))
        assertTrue("\"spoken\":null" in none, none)
    }

    @Test
    fun aFeedFromBeforeTheScreensStillReads() {
        // A feed written before the screens' words, the ring's digits, the script and the 12-hour
        // times reads them at their defaults: no words (the watch then speaks English), the ring in
        // the clock's digits, a Latin interface, and each day's bare clock.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument()))

        assertEquals(WatchScreenWords(), before.ui.screens)
        assertNull(before.ui.ringDigitZero)
        assertTrue(before.ui.latinScript)
        assertNull(before.days.single().clockTime)
        assertEquals(englishFriday, before)
        assertEquals(WatchScreenWords.ENGLISH, before.words())

        // And at their defaults they are still written, never left out.
        val written = WatchFeedJson.encode(english)
        listOf("\"screens\":{\"today\":\"\",", "\"ringDigitZero\":null", "\"latinScript\":true", "\"clockTime\":null").forEach { key ->
            assertTrue(key in written, "$key in $written")
        }
    }

    @Test
    fun theNewKeysAreReadByTheirNames() {
        // Every key of the screens' words, the ring's zero, the script and the 12-hour times, as the
        // spec names them for both watches, written out by hand.
        val feed = assertNotNull(WatchFeedJson.decode(fridayDocument(clockTime = CLOCK_TIME_DOCUMENT, newUi = NEW_UI_DOCUMENT)))

        assertEquals(WatchScreenWords.ENGLISH, feed.ui.screens)
        assertEquals('0', feed.ui.ringDigitZero)
        assertFalse(feed.ui.latinScript)
        assertEquals(
            WatchSix("4:28${NBSP}AM", "5:57${NBSP}AM", "12:33${NBSP}PM", "4:10${NBSP}PM", "7:08${NBSP}PM", "8:28${NBSP}PM"),
            feed.days.single().clockTime,
        )
        // A later build's word, in the words or in a turn's phrase, is ignored as any other key is.
        val later = NEW_UI_DOCUMENT.replace("\"today\":", "\"future\": 1, \"today\":")
            .replace("{\"upTo\": 180,", "{\"future\": 1, \"upTo\": 180,")
        assertEquals(feed, WatchFeedJson.decode(fridayDocument(clockTime = CLOCK_TIME_DOCUMENT, newUi = later)))
    }

    @Test
    fun theFaceWordsAreReadByTheirNamesAndAFeedWithoutThemHasNone() {
        // Plan 5 Task 5: the face screen's five phrases ride under ui.face. A feed written before
        // them reads none, so the watch says its own; the format's version does not move.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument()))
        assertEquals(WatchFaceWords(), before.ui.face)
        assertEquals(1, WatchFeedJson.VERSION)

        val face = ", \"face\": {\"add\": \"Add the Taqwa face\", \"about\": \"The next prayer and its countdown on your watch face.\", " +
            "\"active\": \"Taqwa is your watch face.\", \"chooseIt\": \"Touch and hold your watch face, then choose Taqwa.\", " +
            "\"failed\": \"Couldn’t add the face. Try again later.\", \"future\": 1}"
        val read = assertNotNull(WatchFeedJson.decode(fridayDocument(newUi = face))).ui.face
        assertEquals(
            WatchFaceWords(
                add = "Add the Taqwa face",
                about = "The next prayer and its countdown on your watch face.",
                active = "Taqwa is your watch face.",
                chooseIt = "Touch and hold your watch face, then choose Taqwa.",
                failed = "Couldn’t add the face. Try again later.",
            ),
            read,
        )
        assertTrue("\"face\":{\"add\":\"\"," in WatchFeedJson.encode(english), "written at its default too")
    }

    @Test
    fun theSunriseSettingIsReadByItsNameAndAFeedWithoutItShowsSunrise() {
        // The owner's decision of 9 November 2026: the phone's Show sunrise rides as
        // ui.showSunrise. A feed written before it, a 1.6.0 (43) phone's, shows Sunrise, as every
        // watch did; the format's version does not move.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument()))
        assertTrue(before.ui.showSunrise)
        assertEquals(englishFriday, before)
        assertEquals(1, WatchFeedJson.VERSION)

        assertFalse(assertNotNull(WatchFeedJson.decode(fridayDocument(newUi = ", \"showSunrise\": false"))).ui.showSunrise)
        assertTrue(assertNotNull(WatchFeedJson.decode(fridayDocument(newUi = ", \"showSunrise\": true"))).ui.showSunrise)
        // A null is no answer, as at every key whose default is not null.
        assertNull(WatchFeedJson.decode(fridayDocument(newUi = ", \"showSunrise\": null")))

        // Written either way, its default too, and read back as it was written.
        val hidden = english.copy(ui = english.ui.copy(showSunrise = false))
        val json = WatchFeedJson.encode(hidden)
        assertTrue("\"showSunrise\":false" in json, json)
        assertEquals(hidden, WatchFeedJson.decode(json))
        assertEquals(json, WatchFeedJson.encode(assertNotNull(WatchFeedJson.decode(json))))
        assertTrue("\"showSunrise\":true" in WatchFeedJson.encode(english), "written at its default too")
    }

    @Test
    fun theCompactCountdownIsReadByItsNamesAndAFeedWithoutItHasNone() {
        // Mohamed's report of 9 November 2026: the Galaxy Tile writes the countdown in compact
        // forms, "in 6h 20m", carried in ui.countdown beside its four. A feed written before them,
        // a 1.6.0 (44) phone's, has none, and the Tile writes the full forms; the format's version
        // does not move.
        val before = assertNotNull(WatchFeedJson.decode(fridayDocument()))
        assertEquals(englishFriday, before)
        with(before.ui.countdown) { assertEquals(listOf("", "", ""), listOf(compactHoursMinutes, compactHours, compactMinutes)) }
        assertEquals(1, WatchFeedJson.VERSION)

        val compact = ", \"compactHoursMinutes\": \"in %1${'$'}sh %2${'$'}sm\", \"compactHours\": \"in %1${'$'}sh\", \"compactMinutes\": \"in %1${'$'}sm\""
        val read = assertNotNull(WatchFeedJson.decode(fridayDocument(countdown = compact))).ui.countdown
        assertEquals(listOf("in %1\$sh %2\$sm", "in %1\$sh", "in %1\$sm"), listOf(read.compactHoursMinutes, read.compactHours, read.compactMinutes))
        assertEquals(english.ui.countdown.copy(compactHoursMinutes = "", compactHours = "", compactMinutes = ""), read.copy(compactHoursMinutes = "", compactHours = "", compactMinutes = ""))
        // A null is no answer, as at every key whose default is not null.
        assertNull(WatchFeedJson.decode(fridayDocument(countdown = ", \"compactHours\": null")))

        // Written where they hold a form, and read back as they were written.
        val worded = english.copy(ui = english.ui.copy(countdown = read))
        val json = WatchFeedJson.encode(worded)
        assertTrue("\"underAMinute\":\"in under a minute\",\"compactHoursMinutes\":\"in %1\$sh %2\$sm\",\"compactHours\":\"in %1\$sh\",\"compactMinutes\":\"in %1\$sm\"}" in json, json)
        assertEquals(worded, WatchFeedJson.decode(json))
        assertEquals(json, WatchFeedJson.encode(assertNotNull(WatchFeedJson.decode(json))))
    }

    @Test
    fun anEmptyCompactFormIsNotWritten() {
        // The Lead's ruling of 9 November 2026: a language that keeps its full forms, as Arabic,
        // Urdu and Bengali do, costs the feed nothing for them, so that plan 2 Task 3's worst case
        // keeps all 100 phrases within the budget. Each form is left out on its own when empty,
        // and every other key is written as before, at its default too.
        val none = english
        with(none.ui.countdown) { assertEquals(listOf("", "", ""), listOf(compactHoursMinutes, compactHours, compactMinutes)) }
        val json = WatchFeedJson.encode(none)
        assertTrue("\"underAMinute\":\"in under a minute\"}" in json, json)
        assertTrue("compact" !in json, json)
        assertEquals(none, WatchFeedJson.decode(json))
        assertEquals(json, WatchFeedJson.encode(assertNotNull(WatchFeedJson.decode(json))))
        // Every other default is still on the wire: the Show sunrise, the script, the screens'
        // empty words.
        assertTrue("\"showSunrise\":true" in json, json)
        assertTrue("\"latinScript\":true" in json, json)
        assertTrue("\"screens\":{\"today\":\"\"," in json, json)

        val minutesOnly = none.copy(ui = none.ui.copy(countdown = none.ui.countdown.copy(compactMinutes = "in %1\$sm")))
        val one = WatchFeedJson.encode(minutesOnly)
        assertTrue("\"underAMinute\":\"in under a minute\",\"compactMinutes\":\"in %1\$sm\"}" in one, one)
        assertEquals(minutesOnly, WatchFeedJson.decode(one))

        // The feed with them costs exactly their keys and words more, and nothing else moves.
        val compact = WatchCountdownPhrases(
            hoursMinutes = none.ui.countdown.hoursMinutes,
            hours = none.ui.countdown.hours,
            minutes = none.ui.countdown.minutes,
            underAMinute = none.ui.countdown.underAMinute,
            compactHoursMinutes = "in %1\$sh %2\$sm",
            compactHours = "in %1\$sh",
            compactMinutes = "in %1\$sm",
        )
        val worded = WatchFeedJson.encode(none.copy(ui = none.ui.copy(countdown = compact)))
        assertEquals(json, worded.replace(",\"compactHoursMinutes\":\"in %1\$sh %2\$sm\",\"compactHours\":\"in %1\$sh\",\"compactMinutes\":\"in %1\$sm\"", ""))
    }

    @Test
    fun theRingsZeroIsOneCharacterOrNone() {
        // Written as digitZero is, a string of one character, and null for none.
        assertEquals('0', assertNotNull(WatchFeedJson.decode(fridayDocument(newUi = ", \"ringDigitZero\": \"0\""))).ui.ringDigitZero)
        assertNull(assertNotNull(WatchFeedJson.decode(fridayDocument(newUi = ", \"ringDigitZero\": null"))).ui.ringDigitZero)
        val saudi = WatchFeedJson.encode(english.copy(ui = english.ui.copy(digitZero = '٠', ringDigitZero = '0')))
        assertTrue("\"digitZero\":\"٠\"" in saudi && "\"ringDigitZero\":\"0\"" in saudi, saudi)

        // Anything else is not a feed.
        mapOf(
            "\"\"" to "no character",
            "\"00\"" to "two characters",
            "[\"0\"]" to "a list",
        ).forEach { (value, what) ->
            assertNull(WatchFeedJson.decode(fridayDocument(newUi = ", \"ringDigitZero\": $value")), what)
        }
    }

    @Test
    fun digitZeroIsOneCharacterOnTheWire() {
        // The zero of the phone's digit set, as a string of one character: Western by default.
        val western = WatchFeedJson.encode(english)
        assertTrue("\"digitZero\":\"0\"" in western, western)
        val arabicIndic = WatchFeedJson.encode(arabic)
        assertTrue("\"digitZero\":\"٠\"" in arabicIndic, arabicIndic)
        assertEquals('٠', assertNotNull(WatchFeedJson.decode(arabicIndic)).ui.digitZero)

        // A feed written before the key reads in Western digits.
        val withoutKey = fridayDocument().replace(", \"digitZero\": \"0\"", "")
        assertFalse("digitZero" in withoutKey)
        assertEquals('0', assertNotNull(WatchFeedJson.decode(withoutKey)).ui.digitZero)

        // A zero is one character. Anything else is not a feed.
        mapOf(
            "\"\"" to "no character",
            "\"٠٠\"" to "two characters",
            "null" to "null",
            "[\"0\"]" to "a list",
        ).forEach { (value, what) ->
            assertNull(WatchFeedJson.decode(fridayDocument(digitZero = value)), what)
        }
    }

    @Test
    fun versionOfReadsANewerFeed() {
        // `decode` answers null for a feed it cannot read; the version is how a caller tells a newer
        // one from a broken one (spec 2.6: "Update Taqwa on your watch", and the last feed is kept).
        assertEquals(2, WatchFeedJson.versionOf("""{"version": 2, "layout": "something else"}"""))
        assertEquals(12, WatchFeedJson.versionOf(fridayDocument(version = 12)))
        // Only the version is read, so a document with nothing else in it still has one.
        assertEquals(3, WatchFeedJson.versionOf("""{"version":3}"""))
        // And a feed this build reads has the version this build writes.
        assertEquals(WatchFeedJson.VERSION, WatchFeedJson.versionOf(WatchFeedJson.encode(english)))
        assertEquals(1, WatchFeedJson.versionOf(fridayDocument(version = 1)))
    }

    @Test
    fun versionOfMalformedIsNull() {
        mapOf(
            "" to "nothing at all",
            "not json" to "not JSON",
            "[]" to "JSON, but not an object",
            "[1, 2]" to "an array of numbers",
            "null" to "JSON null",
            "7" to "a bare number",
            "{}" to "an object with no version",
            """{"writtenAt": 1792631529}""" to "a document with other keys but no version",
            """{"version": null}""" to "a null version",
            """{"version": "2"}""" to "a version written as text",
            """{"version": 2.5}""" to "a version that is not a whole number",
            """{"version": true}""" to "a version that is a boolean",
            """{"version": {"major": 2}}""" to "a version that is an object",
            """{"version": 2""" to "cut short in the version's own object",
            WatchFeedJson.encode(english).take(40) to "a feed cut short after its first keys",
        ).forEach { (text, what) ->
            assertNull(WatchFeedJson.versionOf(text), what)
        }
    }

    /**
     * Friday's feed written by hand rather than by the encoder, so the names the watches read are
     * pinned here as the spec gives them: a property renamed in Kotlin would still round-trip
     * through the encoder, and break every watch holding a feed written under the old name.
     * [futureKeys] adds a key a later build might send, at the top and in the day. [ends] is what
     * follows Isha in `times`, which a polar day does without, and [midnight] is what follows the
     * day's last string, which a feed written before the key does without. [digitZero] is the JSON
     * value, quotes and all, of the ui's `digitZero`. [hijriDayMonth] follows the midnight, and a
     * feed written before that key does without it too, as it does without [gregorianSpoken],
     * which follows it, and the ui's [spoken] words, which follow `nextIn`. [clockTime] follows the
     * day's spoken date and [newUi] the spoken words: a feed written before them does without both.
     * [countdown] follows the countdown's under-a-minute form, where its compact forms go.
     */
    private fun fridayDocument(
        version: Int = 1,
        futureKeys: Boolean = false,
        ends: String = ", \"fajrEnd\": 1792727820, \"asrEnd\": 1792775160",
        midnight: String = ", \"midnight\": 1792706400",
        digitZero: String = "\"0\"",
        hijriDayMonth: String = ", \"hijriDayMonth\": \"11 Jumada al-Ula\"",
        gregorianSpoken: String = ", \"gregorianSpoken\": \"Friday 23 October\"",
        spoken: String = SPOKEN_DOCUMENT,
        deepFutureKeys: Boolean = false,
        tasbeeh: Boolean = true,
        clockTime: String = "",
        newUi: String = "",
        countdown: String = "",
    ): String {
        val future = if (futureKeys) "\"future\": 1," else ""
        // A later build's key inside every object below the day, with a value of a shape this build
        // has no field for.
        val deep = if (deepFutureKeys) "\"future\": {\"nested\": [1, \"two\", null]}, " else ""
        val phrases = if (!tasbeeh) "" else """,
              "tasbeeh": [
                {$deep"id": "custom-1", "phrase": "Subhan Allah wa bihamdihi", "target": 100},
                {"id": "custom-2", "phrase": "Astaghfirullah", "target": 33}
              ]"""
        return """
            {
              $future
              "version": $version,
              "writtenAt": 1792631529,
              "days": [
                {
                  $future
                  "date": "2026-10-23",
                  "times": {$deep"fajr": 1792722480, "sunrise": 1792727820, "dhuhr": 1792751580, "asr": 1792764600, "maghrib": 1792775280, "isha": 1792780080$ends},
                  "clock": {$deep"fajr": "4:28", "sunrise": "5:57", "dhuhr": "12:33", "asr": "16:10", "maghrib": "19:08", "isha": "20:28"},
                  "names": {$deep"fajr": "Fajr", "sunrise": "Sunrise", "dhuhr": "Jumuʿah", "asr": "Asr", "maghrib": "Maghrib", "isha": "Isha"},
                  "hijri": "11 Jumada al-Ula 1448",
                  "gregorian": "FRI 23 OCT",
                  "weekday": "FRI",
                  "dayNumber": "23"$midnight$hijriDayMonth$gregorianSpoken$clockTime
                }
              ],
              "place": {$deep"city": "Cape Town", "qiblaBearing": 23.352542268995705, "qiblaKm": "6,558", "declination": -25.411843829384712, "southern": true},
              "ui": {
                $deep"language": "en", "rtl": false, "digitZero": $digitZero, "twelveHour": false, "am": "AM", "pm": "PM",
                "countdown": {$deep"hoursMinutes": "in %1${'$'}s h %2${'$'}s min", "hours": "in %1${'$'}s h", "minutes": "in %1${'$'}s min", "underAMinute": "in under a minute"$countdown},
                "nextIn": "%1${'$'}s in"$spoken$newUi
              }$phrases
            }
        """.trimIndent()
    }

    // Cape Town, UTC+2. Fajr ends at sunrise; Asr ends at sunset, a little before Maghrib.
    private val thursday = WatchDay(
        date = "2026-10-22",
        times = WatchTimes(
            fajr = 1792636140,
            sunrise = 1792641480,
            dhuhr = 1792665180,
            asr = 1792678200,
            maghrib = 1792688820,
            isha = 1792693620,
            fajrEnd = 1792641480,
            asrEnd = 1792688700,
        ),
        clock = WatchSix("4:29", "5:58", "12:33", "16:10", "19:07", "20:27"),
        names = WatchSix("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
        hijri = "10 Jumada al-Ula 1448",
        gregorian = "THU 22 OCT",
        weekday = "THU",
        dayNumber = "22",
        midnight = 1792620000,
        hijriDayMonth = "10 Jumada al-Ula",
        gregorianSpoken = "Thursday 22 October",
    )

    private val friday = WatchDay(
        date = "2026-10-23",
        times = WatchTimes(
            fajr = 1792722480,
            sunrise = 1792727820,
            dhuhr = 1792751580,
            asr = 1792764600,
            maghrib = 1792775280,
            isha = 1792780080,
            fajrEnd = 1792727820,
            asrEnd = 1792775160,
        ),
        clock = WatchSix("4:28", "5:57", "12:33", "16:10", "19:08", "20:28"),
        names = WatchSix("Fajr", "Sunrise", "Jumuʿah", "Asr", "Maghrib", "Isha"),
        hijri = "11 Jumada al-Ula 1448",
        gregorian = "FRI 23 OCT",
        weekday = "FRI",
        dayNumber = "23",
        midnight = 1792706400,
        hijriDayMonth = "11 Jumada al-Ula",
        gregorianSpoken = "Friday 23 October",
    )

    private val english = WatchFeed(
        version = WatchFeedJson.VERSION,
        writtenAt = 1792631529,
        days = listOf(thursday, friday),
        place = WatchPlace(
            city = "Cape Town",
            qiblaBearing = 23.352542268995705,
            qiblaKm = "6,558",
            declination = -25.411843829384712,
            southern = true,
        ),
        ui = WatchUi(
            language = "en",
            rtl = false,
            digitZero = '0',
            twelveHour = false,
            am = "AM",
            pm = "PM",
            countdown = WatchCountdownPhrases(
                hoursMinutes = "in %1\$s h %2\$s min",
                hours = "in %1\$s h",
                minutes = "in %1\$s min",
                underAMinute = "in under a minute",
            ),
            nextIn = "%1\$s in",
            spoken = WatchSpoken(
                countdown = "%1\$s in %2\$s, at %3\$s",
                countdownSoon = "%1\$s in under a minute, at %2\$s",
                hoursMinutes = "%1\$s %2\$s",
                hours = listOf(WatchCount(0, "%1\$s hours"), WatchCount(1, "%1\$s hour"), WatchCount(99, "%1\$s hours")),
                minutes = listOf(WatchCount(0, "%1\$s minutes"), WatchCount(1, "%1\$s minute"), WatchCount(99, "%1\$s minutes")),
            ),
        ),
        tasbeeh = listOf(
            WatchPhrase(id = "custom-1", phrase = "Subhan Allah wa bihamdihi", target = 100),
            WatchPhrase(id = "custom-2", phrase = "Astaghfirullah", target = 33),
        ),
    )

    /** [english] with the one day [fridayDocument] holds. */
    private val englishFriday = english.copy(days = listOf(friday))

    /** [english] the other way round in each of its language flags, with its text in Arabic: right
     * to left, Arabic-Indic digits, twelve hours with their day periods, the ring in Western digits
     * as a Saudi phone's is, a script that is not Latin, and the words of the screens. */
    private val arabic = english.copy(
        days = listOf(
            friday.copy(
                clock = WatchSix("٤:٢٨", "٥:٥٧", "١٢:٣٣", "٤:١٠", "٧:٠٨", "٨:٢٨"),
                names = WatchSix("الفجر", "الشروق", "الجمعة", "العصر", "المغرب", "العشاء"),
                hijri = "١١ جمادى الأولى ١٤٤٨",
                hijriDayMonth = "١١ جمادى الأولى",
                gregorian = "الجمعة ٢٣ أكتوبر",
                weekday = "الجمعة",
                dayNumber = "٢٣",
                gregorianSpoken = "الجمعة ٢٣ أكتوبر",
                clockTime = WatchSix("٤:٢٨${NBSP}ص", "٥:٥٧${NBSP}ص", "١٢:٣٣${NBSP}م", "٤:١٠${NBSP}م", "٧:٠٨${NBSP}م", "٨:٢٨${NBSP}م"),
            ),
        ),
        place = english.place.copy(city = "كيب تاون", qiblaKm = "٦٥٥٨"),
        ui = WatchUi(
            language = "ar-SA",
            rtl = true,
            digitZero = '٠',
            twelveHour = true,
            am = "ص",
            pm = "م",
            countdown = WatchCountdownPhrases(
                hoursMinutes = "بعد %1\$s س %2\$s د",
                hours = "بعد %1\$s س",
                minutes = "بعد %1\$s د",
                underAMinute = "بعد أقل من دقيقة",
            ),
            nextIn = "متبقٍّ على %1\$s",
            spoken = WatchSpoken(
                countdown = "%1\$s بعد %2\$s، الساعة %3\$s",
                countdownSoon = "%1\$s بعد أقل من دقيقة، الساعة %2\$s",
                hoursMinutes = "%1\$s و%2\$s",
                hours = listOf(WatchCount(1, "%1\$s ساعة"), WatchCount(2, "%1\$s ساعتين"), WatchCount(10, "%1\$s ساعات"), WatchCount(99, "%1\$s ساعة")),
                minutes = listOf(WatchCount(1, "%1\$s دقيقة"), WatchCount(2, "%1\$s دقيقتين"), WatchCount(10, "%1\$s دقائق"), WatchCount(99, "%1\$s دقيقة")),
            ),
            screens = Fixtures.ARABIC_SCREENS,
            ringDigitZero = '0',
            latinScript = false,
        ),
        tasbeeh = listOf(WatchPhrase(id = "custom-1", phrase = "سبحان الله وبحمده", target = 100)),
    )

    private companion object {
        /** U+00A0 NO-BREAK SPACE, which the app writes between a 12-hour time and its day period. */
        val NBSP = Char(0x00A0).toString()

        /** Friday's 12-hour times as [fridayDocument] writes them by hand, after the spoken date. */
        val CLOCK_TIME_DOCUMENT = """,
                  "clockTime": {"fajr": "4:28${NBSP}AM", "sunrise": "5:57${NBSP}AM", "dhuhr": "12:33${NBSP}PM", "asr": "4:10${NBSP}PM", "maghrib": "7:08${NBSP}PM", "isha": "8:28${NBSP}PM"}"""

        /** The screens' English words, a Saudi ring's zero and a script that is not Latin, by hand,
         * after the spoken words: every key the two watches read. The dial's two short forms are
         * written from [WatchScreenWords.ENGLISH], since a reviewer may change them
         * (Ruling 67 item 2); their keys are by hand as every other. */
        val NEW_UI_DOCUMENT = """,
                "screens": {
                  "today": "Today", "tasbeeh": "Tasbeeh", "qibla": "Qibla", "appName": "Taqwa",
                  "openOnPhone": "Open Taqwa on your phone", "updateOnWatch": "Update Taqwa on your watch",
                  "rowCurrent": "Current prayer", "rowPassed": "Passed", "rowNext": "Next prayer",
                  "round": "ROUND %1${'$'}s", "of": "of %1${'$'}s", "bezelHint": "Turn the bezel or tap",
                  "crownHint": "Turn the crown or tap",
                  "reset": "Reset", "resetConfirm": "Reset the count for this dhikr?",
                  "presets": {
                    "after_prayer": "After prayer", "subhanallah": "SubhanAllah", "alhamdulillah": "Alhamdulillah",
                    "allahu_akbar": "Allahu Akbar", "astaghfirullah": "Astaghfirullah",
                    "la_ilaha_illallah": "La ilaha illallah", "subhanallahi_wa_bihamdihi": "SubhanAllahi wa bihamdihi"
                  },
                  "countSpoken": "%1${'$'}s of %2${'$'}s", "nextSpoken": "%1${'$'}s. Next, %2${'$'}s",
                  "setCompleteSpoken": "%1${'$'}s. Round complete", "roundSpoken": "Round complete. %1${'$'}s, round %2${'$'}s",
                  "nameRoundSpoken": "%1${'$'}s, round %2${'$'}s",
                  "countAction": "Count", "undoAction": "Take one away", "phrasesAction": "Choose a dhikr",
                  "facingQibla": "Facing qibla", "distance": "Makkah · %1${'$'}s km away", "bearing": "%1${'$'}s°",
                  "qiblaDetail": "%1${'$'}s° · %2${'$'}s km to Makkah",
                  "distanceOnDial": "${WatchScreenWords.ENGLISH.distanceOnDial}",
                  "qiblaDetailOnDial": "${WatchScreenWords.ENGLISH.qiblaDetailOnDial}",
                  "cardinals": ["N", "E", "S", "W"],
                  "holdLevel": "Hold your wrist level", "needsCalibrating": "Compass needs calibrating",
                  "interference": "Magnetic interference", "bestEffort": "Compass unavailable", "noSensor": "No compass sensor",
                  "turnRight": [{"upTo": 180, "phrase": "Turn right about %1${'$'}s degrees"}],
                  "turnLeft": [{"upTo": 180, "phrase": "Turn left about %1${'$'}s degrees"}]
                },
                "ringDigitZero": "0",
                "latinScript": false"""

        /** The ui's spoken words as [fridayDocument] writes them by hand, after `nextIn`. */
        val SPOKEN_DOCUMENT = """,
                "spoken": {
                  "countdown": "%1${'$'}s in %2${'$'}s, at %3${'$'}s",
                  "countdownSoon": "%1${'$'}s in under a minute, at %2${'$'}s",
                  "hoursMinutes": "%1${'$'}s %2${'$'}s",
                  "hours": [{"upTo": 0, "phrase": "%1${'$'}s hours"}, {"upTo": 1, "phrase": "%1${'$'}s hour"}, {"upTo": 99, "phrase": "%1${'$'}s hours"}],
                  "minutes": [{"upTo": 0, "phrase": "%1${'$'}s minutes"}, {"upTo": 1, "phrase": "%1${'$'}s minute"}, {"upTo": 99, "phrase": "%1${'$'}s minutes"}]
                }"""
    }
}
