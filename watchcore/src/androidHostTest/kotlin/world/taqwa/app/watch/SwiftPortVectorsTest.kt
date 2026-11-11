package world.taqwa.app.watch

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import world.taqwa.app.tasbeeh.TasbeehPresets
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Keeps `watchcore/vectors/feed-vectors.json` in step with the Kotlin it records: what the watchOS
 * app's Swift port reads beyond plan 1's `watch-rules-vectors.json` (plan 4, Task 1). Its sections:
 *
 * - `decode`: texts a watch may be handed, and what [WatchFeedJson.decode] and
 *   [WatchFeedJson.versionOf] make of them (`feed`, `newer` or `malformed`), with what a feed holds;
 *   `swift` where the port answers otherwise, which is only ever a refusal (the Lead's call 8),
 *   and each case's `class` (Ruling 56): ENCODER, the encoder's own text, and ONE_CHANGE, one
 *   such text with one value changed, which hold the port exactly, and FOREIGN, a text no encoder
 *   writes, which holds it only not to crash and to read a feed's values exactly, Kotlin's answer
 *   there being information. Among them: newer feeds of a shape this build cannot read, and the
 *   number and text forms that Foundation and kotlinx read differently (a key written twice
 *   among them), each with Kotlin's answer;
 * - `spoken`: [WatchRules.spokenCountdown] on day 0's Asr;
 * - `labels`: a prayer's name in capitals, as `ComplicationFields.nextPrayer` writes it;
 * - `days`: [WatchRules.today] and [WatchRules.dayStart] on both sides of each turn of the date,
 *   with midnights and without;
 * - `clock`: [WatchRules.clockText] and [WatchRules.spokenClock] for each of a day's six times,
 *   keyed by [WatchTime], Sunrise among them;
 * - `words`: [WatchFeed.words] on the samples, an older phone's feed and `blankWords` (plan 4
 *   Task 3), so the port's `orEnglish` is held to Kotlin's, with the presets' ids in Kotlin's order
 *   (`presetIds`, Ruling 60 item 1e);
 * - `moments`, `starts`, `filled` and `inDigits` (Ruling 57 item 5): [WatchRules.moment] and the
 *   rest of a rules case, and [WatchRules.starts], on feeds where a start falls exactly on the next
 *   day's Fajr and where no start has arrived when the date turns; [WatchRules.filled] on places
 *   with no part; and [WatchRules.inDigits] past the end of 16 bits;
 * - `contract`: [WatchLinkContract]'s keys and sizes, and the receivers' rules of Ruling 50.
 *
 * Every case of `spoken`, `labels`, `days`, `clock`, `words`, `moments` and `starts` carries the class of
 * the feed it runs on, always ENCODER. The cases of `filled`, `inDigits` and the contract are no
 * feeds and carry none.
 *
 * Every answer is Kotlin's own, worked out here from the decoded text the port reads too. The test
 * fails when the file is missing or differs. After a change that is meant, rewrite it with
 * `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors` (the build file passes the flag
 * on), read the diff, and commit it; the Swift port then fails `swift test` until it follows.
 */
class SwiftPortVectorsTest {

    @Test
    fun swiftPortVectorsAreUpToDate() {
        val directory = if (File("src/androidHostTest").isDirectory) File("vectors") else File("watchcore/vectors")
        val file = File(directory, FILE)
        if (System.getProperty("taqwa.updateVectors") != null) {
            directory.mkdirs()
            file.writeText(vectorsText)
            return
        }
        if (!file.exists()) fail("${file.path} is missing. $HOW_TO_WRITE")
        val actual = file.readText()
        if (actual == vectorsText) return
        val found = actual.lines()
        val wanted = vectorsText.lines()
        val line = (0 until maxOf(found.size, wanted.size)).firstOrNull { found.getOrNull(it) != wanted.getOrNull(it) }
            ?: fail("${file.path} has the lines Kotlin produces, but ends them differently (CRLF for LF, say). $HOW_TO_WRITE")
        fail(
            "${file.path} is not what Kotlin answers. First difference at line ${line + 1}:\n" +
                "    file:   ${found.getOrNull(line)?.take(160) ?: "(no such line)"}\n" +
                "    Kotlin: ${wanted.getOrNull(line)?.take(160) ?: "(no such line)"}\n$HOW_TO_WRITE",
        )
    }

    /**
     * Each case's `result` is what the decoder answers for its text, read the way a watch reads it
     * (`FeedStore`): a feed when [WatchFeedJson.decode] gives one, newer when it gives none and
     * [WatchFeedJson.versionOf] reads a version above [WatchFeedJson.VERSION], and malformed
     * otherwise. So the file holds the decoder's answers, never what a case was meant to show.
     */
    @Test
    fun decodeCasesAreWhatTheDecoderSays() {
        val cases = decodeCases()
        assertEquals(97, cases.size, "every decode case is written")
        for (case in cases) {
            assertEquals(kotlinAnswer(textOf(case)), case.string("result"), case.string("name"))
        }
        // kotlinx reads a quoted number or boolean, and a bare digit for a Char, as its value (the
        // Lead's call 8, Ruling 57 item 2).
        for (name in QUOTED + LENIENT) assertEquals("feed", decodeCase(name).string("result"), name)
    }

    /**
     * Every case carries its class (Ruling 56 item 2), and each class's cases are what the file's
     * rule says: [classOf] is that rule, worked out from the text alone, and the generator's
     * declared class must agree with it, as `result` must agree with the decoder.
     * - FOREIGN: kotlinx's encoder, as the phone configures it, writes no such text;
     * - ENCODER: a value of a model the plans name, in the encoder's own writing. Where that model
     *   is [WatchFeed], the text is [WatchFeedJson.encode]'s own ([MODEL_TEXTS]); an older or later
     *   phone's is the same encoder over its JSON tree;
     * - ONE_CHANGE: an ENCODER case's text with one value changed, removed or set to null.
     *
     * The port is held exactly on ENCODER and ONE_CHANGE, and on FOREIGN only not to crash and to
     * read a feed's values exactly, so Kotlin's answer there is information. A new FOREIGN case
     * needs a Lead's ruling ([FOREIGN_CASES] is fixed). A case of another section runs on a feed
     * and carries that feed's class, always ENCODER; the cases of `filled`, `inDigits` and the
     * contract are not feeds.
     */
    @Test
    fun everyCaseHasTheClassItsRuleGives() {
        val cases = decodeCases()
        val encoderTrees = cases.map { judgedText(it) }
            .filter { !isForeign(it) && holdsAModelsValue(Json.parseToJsonElement(it)) }
            .map { Json.parseToJsonElement(it) }
        for (case in cases) {
            val name = case.string("name")
            val declared = case["class"]?.jsonPrimitive?.content ?: fail("$name has no class")
            assertEquals(classOf(judgedText(case), encoderTrees), declared, name)
        }
        val byClass = cases.groupBy({ it.string("class") }, { it.string("name") })
        assertEquals(setOf(ENCODER, ONE_CHANGE, FOREIGN), byClass.keys)
        assertEquals(FOREIGN_CASES, byClass.getValue(FOREIGN), "a new FOREIGN case needs a Lead's ruling (Ruling 56 item 3)")
        for (case in cases) {
            val name = case.string("name")
            val portAnswer = case["swift"]?.jsonPrimitive?.content ?: case.string("result")
            when (case.string("class")) {
                ENCODER -> {
                    // A model's value is read, or is newer: never malformed, and the port answers as Kotlin.
                    val version = Json.parseToJsonElement(textOf(case)).jsonObject.getValue("version").jsonPrimitive.int
                    assertEquals(if (version > WatchFeedJson.VERSION) "newer" else "feed", case.string("result"), name)
                    assertNull(case["swift"], name)
                }
                // Each is refused by the port: no model's value is there to read (call 8 and Ruling 57
                // item 2 for the quoted and lenient cases, which kotlinx reads leniently).
                ONE_CHANGE -> assertEquals("malformed", portAnswer, name)
                FOREIGN -> assertNull(case["swift"], "$name: on a FOREIGN case Kotlin's answer is information, never the port's")
            }
        }
        // Generated by the encoder itself wherever the model is this build's: the text is
        // WatchFeedJson.encode's own for the feed it holds (its version aside).
        val modelTexts = cases.filter { it.string("class") == ENCODER }.filter { case ->
            val feed = runCatching { PHONE_JSON.decodeFromString(WatchFeed.serializer(), judgedText(case)) }.getOrNull()
            feed != null && WatchFeedJson.encode(feed) == judgedText(case)
        }
        assertEquals(MODEL_TEXTS, modelTexts.map { it.string("name") })
        // The other sections run on ENCODER feeds and carry their class; the cases of filled,
        // inDigits and the contract are no feed and carry none.
        val classOfFeed = cases.associate { it.string("name") to it.string("class") }
        for (key in listOf("spoken", "labels", "days", "clock", "words", "moments", "starts")) {
            assertTrue(section(key).isNotEmpty(), key)
            for (case in section(key)) {
                assertEquals(ENCODER, classOfFeed[case.string("feed")], "$key runs on ${case.string("feed")}")
                assertEquals(ENCODER, case["class"]?.jsonPrimitive?.content, "$key on ${case.string("feed")}")
            }
        }
        for (key in listOf("filled", "inDigits")) {
            assertTrue(section(key).isNotEmpty() && section(key).none { "class" in it || "feed" in it }, key)
        }
        val contract = vectors.getValue("contract").jsonObject
        for (key in listOf("fingerprints", "deliveries", "stores", "echoes")) {
            assertTrue(contract.getValue(key).jsonArray.none { "class" in it.jsonObject }, "contract.$key")
        }
    }

    /**
     * The port refuses exactly the cases where kotlinx reads a value of the wrong type leniently,
     * and differs on no other case: the four quoted cases of the Lead's call 8, and [LENIENT], one
     * for each other field the port reads that kotlinx would read so (Ruling 57 item 2). Each is
     * an accepted difference in the safe direction: no phone writes the wrong type, and a refusal
     * keeps the last feed. A new difference needs a new Lead's ruling, never a quiet line in the
     * file. Each is ONE_CHANGE, where the port is held exactly, to `swift`.
     */
    @Test
    fun swiftDiffersOnlyWhereKotlinReadsAWrongType() {
        val differing = decodeCases().filter { "swift" in it }
        assertEquals(QUOTED + LENIENT, differing.map { it.string("name") })
        for (case in differing) {
            assertEquals("feed", case.string("result"), case.string("name"))
            assertEquals("malformed", case.string("swift"), case.string("name"))
            assertEquals(ONE_CHANGE, case.string("class"), case.string("name"))
        }
    }

    /**
     * Ruling 57 item 2's cases are every field the port reads that kotlinx reads leniently, each
     * once: a number or a boolean written as a string, which kotlinx 1.11's decoder reads as the
     * value (an Int or a Long by `consumeNumericLiteral`, a Double by `consumeStringLenient` and
     * `Double.parseDouble`, a Boolean by `consumeBooleanLenient`, measured with javap), and a `Char`
     * written as a bare digit, which `decodeChar`'s `consumeStringLenient` reads. The fields come from the model's own
     * serializers ([lenientPaths]); the four the quoted cases change are on the sample, the rest on
     * oneDay, the value itself written in the lenient form, so Kotlin reads each as the feed of that
     * value written in its own type: oneDay's own feed, value for value, but for
     * `bareRingDigitZero`, whose field oneDay leaves null, which is written as the bare `0` an
     * ar-SA phone's ring would send quoted. Plan 3's fields ([PLAN_3_LENIENT]) are read by the port
     * since plan 4 Task 3, which added their cases.
     */
    @Test
    fun everyFieldThePortReadsHasItsLenientCase() {
        val oneDayText = textOf(decodeCase("oneDay"))
        val oneDayFeed = assertNotNull(WatchFeedJson.decode(oneDayText))
        val quoted = QUOTED.associateWith { name ->
            changedPaths(Json.parseToJsonElement(textOf(decodeCase("sample"))), Json.parseToJsonElement(textOf(decodeCase(name)))).single()
        }
        assertEquals(listOf("writtenAt", "version", "place.qiblaBearing", "ui.rtl"), quoted.values.toList())
        val lenient = LENIENT.associateWith { name ->
            val text = textOf(decodeCase(name))
            val path = changedPaths(Json.parseToJsonElement(oneDayText), Json.parseToJsonElement(text)).single()
            val written = Json.parseToJsonElement(text).at(path)
            val kind = lenientPaths(WatchFeed.serializer().descriptor).getValue(path.replace(Regex("\\[[0-9]+]"), "[]"))
            if (kind == PrimitiveKind.CHAR) {
                assertTrue(!written.isString && written.content.single().isDigit(), "$name: a bare digit")
            } else {
                assertTrue(written.isString, "$name: written as a string")
            }
            // The same value written in its own type: a Char quoted, anything else bare.
            val proper = Json.parseToJsonElement(text).withFirstAt(path.replace(Regex("\\[[0-9]+]"), "[]")) {
                if (kind == PrimitiveKind.CHAR) JsonPrimitive(written.content) else JsonUnquotedLiteral(written.content)
            }
            val feed = assertNotNull(WatchFeedJson.decode(proper.toString()), "$name in its own type")
            assertEquals(feed, WatchFeedJson.decode(text), "$name is the feed of its value to Kotlin")
            if (name == "bareRingDigitZero") {
                assertEquals(oneDayFeed.copy(ui = oneDayFeed.ui.copy(ringDigitZero = '0')), feed, name)
            } else {
                assertEquals(oneDayFeed, feed, "$name is oneDay's feed to Kotlin")
            }
            path.replace(Regex("\\[[0-9]+]"), "[]")
        }
        val read = lenientPaths(WatchFeed.serializer().descriptor).keys
        assertEquals(read.toList(), (quoted.values + lenient.values).sortedBy { read.indexOf(it) }, "each field the port reads, once")
        assertTrue(PLAN_3_LENIENT.all { it in lenient.values }, "plan 3's fields have theirs (plan 4 Task 3)")
    }

    /**
     * Plan 3's keys as the reviewers measured them (Ruling 57 item 4), each oneDay with that one
     * value changed and refused by kotlinx: a `null` where the field has a default that is not null
     * (`screens`, `latinScript`), a ring zero of two characters, and a clock time that is a number.
     * They are ONE_CHANGE, so the port refuses them exactly as Kotlin does.
     */
    @Test
    fun plan3sKeysAreRefusedAsKotlinRefusesThem() {
        val oneDay = Json.parseToJsonElement(textOf(decodeCase("oneDay")))
        assertEquals(PLAN_3_REFUSED.keys.toList(), decodeCases().map { it.string("name") }.filter { it in PLAN_3_REFUSED })
        for ((name, path) in PLAN_3_REFUSED) {
            val case = decodeCase(name)
            assertEquals("malformed", case.string("result"), name)
            assertEquals(ONE_CHANGE, case.string("class"), name)
            assertNull(case["swift"], name)
            assertEquals(listOf(path), changedPaths(oneDay, Json.parseToJsonElement(textOf(case))), name)
        }
        // Inside the words too: every word, list and map has a default that is not null, so a null
        // in any one of them is refused (Swift's testANullInEveryWordIsRefused holds the port to it).
        val ui = oneDay.jsonObject.getValue("ui").jsonObject
        val screens = ui.getValue("screens").jsonObject
        assertEquals(WatchScreenWords.serializer().descriptor.elementsCount, screens.size)
        for (key in screens.keys) {
            val text = oneDay.jsonObject.with("ui", ui.with("screens", screens.with(key, JsonNull))).toString()
            assertNull(WatchFeedJson.decode(text), "screens.$key null")
            assertEquals("malformed", kotlinAnswer(text), "screens.$key null")
        }
    }

    /**
     * An older phone's feed lacks plan 3's fields as it lacks plan 1's later ones: the screens'
     * words, the ring's zero, the script and every day's 12-hour times; and the phone's Show
     * sunrise, which 1.6.0 (44) is the first phone to write; and the Tile's compact countdown,
     * which it never wrote. Kotlin reads it with their defaults, so it speaks English, draws its
     * ring in its own digits, capitalises in Latin, shows Sunrise and writes the Tile's countdown
     * in its full forms.
     */
    @Test
    fun theOlderPhoneHasNoneOfPlan3sFields() {
        val older = Json.parseToJsonElement(textOf(decodeCase("olderPhone"))).jsonObject
        val ui = older.getValue("ui").jsonObject
        for (key in listOf("screens", "ringDigitZero", "latinScript", "spoken", "digitZero", "showSunrise")) assertTrue(key !in ui, "ui.$key")
        val countdown = ui.getValue("countdown").jsonObject
        for (key in COMPACT_COUNTDOWN) assertTrue(key !in countdown, "ui.countdown.$key")
        val feed = assertNotNull(WatchFeedJson.decode(textOf(decodeCase("olderPhone"))))
        assertEquals(WatchRules.countdown(feed, 4320), WatchRules.compactCountdown(feed, 4320))
        for (day in older.getValue("days").jsonArray) assertTrue("clockTime" !in day.jsonObject, "a day's clockTime")
        val expect = decodeCase("olderPhone").getValue("expect").jsonObject
        assertEquals(JsonNull, expect.getValue("ringDigitZero"))
        assertEquals("true", expect.getValue("latinScript").jsonPrimitive.content)
        assertEquals("true", expect.getValue("showSunrise").jsonPrimitive.content)
        val words = section("words").single { it.string("feed") == "olderPhone" }.getValue("words")
        assertEquals(PHONE_JSON.encodeToJsonElement(WatchScreenWords.serializer(), WatchScreenWords.ENGLISH), words)
    }

    /**
     * Each `words` case is its feed's [WatchFeed.words], the screens' words with the English for
     * every one left blank, written whole: the four samples, the older phone's (all English), and
     * `blankWords`, whose words are blank or not by Kotlin's own `isBlank` (a space, a no-break
     * space, an ideographic space, a tab and a line feed, each of the four controls U+001C to
     * U+001F alone and the line separator are blank; U+0085 and a zero-width space are not), whose
     * empty lists take the English and whose non-empty ones stay as they are, and whose presets
     * overlay the English, a blank name taking the English and an id the English lacks kept after
     * the built-ins. Each of U+001D, U+001E and U+001F has a word of its own (Ruling 60 item 1b), so
     * a port's blank rule that stops short of U+001F, or takes U+001C alone, reads one of them as a
     * word.
     */
    @Test
    fun theWordsCasesAreEachFeedsWordsWithTheEnglish() {
        val words = section("words")
        assertEquals(SAMPLES + "olderPhone" + "blankWords", words.map { it.string("feed") })
        for (case in words) {
            val feed = assertNotNull(WatchFeedJson.decode(textOf(decodeCase(case.string("feed")))), case.string("feed"))
            assertEquals(PHONE_JSON.encodeToJsonElement(WatchScreenWords.serializer(), feed.words()), case.getValue("words"), case.string("feed"))
        }
        val blank = PHONE_JSON.decodeFromJsonElement(WatchScreenWords.serializer(), words.single { it.string("feed") == "blankWords" }.getValue("words"))
        val english = WatchScreenWords.ENGLISH
        assertEquals(english.today, blank.today)
        assertEquals(english.tasbeeh, blank.tasbeeh)
        assertEquals(Char(0x85).toString(), blank.qibla)
        assertEquals(Char(0x200B).toString(), blank.openOnPhone)
        assertEquals(english.updateOnWatch, blank.updateOnWatch)
        // U+001C to U+001F, each alone in a word: a Char.isWhitespace to Kotlin, so blank.
        assertTrue((0x1C..0x1F).all { Char(it).isWhitespace() })
        val controls = buildJsonObject {
            put("updateOnWatch", Char(0x1C).toString())
            put("round", Char(0x1D).toString())
            put("of", Char(0x1E).toString())
            put("bezelHint", Char(0x1F).toString())
        }
        val sent = Json.parseToJsonElement(textOf(decodeCase("blankWords"))).jsonObject
            .getValue("ui").jsonObject.getValue("screens").jsonObject
        for ((key, value) in controls) assertEquals(value, sent.getValue(key), "blankWords sends $key as one control")
        assertEquals(english.round, blank.round)
        assertEquals(english.of, blank.of)
        assertEquals(english.bezelHint, blank.bezelHint)
        assertEquals(english.rowCurrent, blank.rowCurrent)
        assertEquals(english.rowPassed, blank.rowPassed)
        assertEquals(english.rowNext, blank.rowNext)
        assertEquals(english.crownHint, blank.crownHint)
        assertEquals("Taqwa ", blank.appName)
        assertEquals(english.cardinals, blank.cardinals)
        assertEquals(english.turnRight, blank.turnRight)
        assertEquals(listOf(WatchCount(180, " ")), blank.turnLeft)
        assertEquals(english.presets.keys.toList() + "custom_2" + "custom_1", blank.presets.keys.toList())
        assertEquals(english.presets.getValue("subhanallah"), blank.presets.getValue("subhanallah"))
        assertEquals("الحمد لله", blank.presets.getValue("alhamdulillah"))
        assertEquals("Yours", blank.presets.getValue("custom_2"))
    }

    /**
     * Each `words` case carries its presets' ids in Kotlin's order, `presetIds` (Ruling 60 item 1e),
     * since the port's dictionary of names keeps none: [TasbeehPresets.builtIn]'s, whatever order
     * the phone sent them in, then each id the built-ins lack in the order the phone sent it. So plan
     * 4 Task 8 can hold the watch's list to Kotlin's. `blankWords` sends its presets out of that
     * order (an id the built-ins lack first, alhamdulillah before subhanallah, a second such id last),
     * so a list that keeps the phone's order for the built-ins, or sorts the rest, differs.
     */
    @Test
    fun theWordsCasesCarryThePresetsInTheBuiltInsOrder() {
        val builtIn = TasbeehPresets.builtIn.map { it.id }
        assertEquals(builtIn, WatchScreenWords.ENGLISH.presets.keys.toList())
        for (case in section("words")) {
            val name = case.string("feed")
            val ids = case.getValue("presetIds").jsonArray.map { it.jsonPrimitive.content }
            val feed = assertNotNull(WatchFeedJson.decode(textOf(decodeCase(name))), name)
            assertEquals(feed.words().presets.keys.toList(), ids, name)
            val others = feed.ui.screens.presets.filter { (id, title) -> id !in builtIn && title.isNotBlank() }.keys
            assertEquals(builtIn + others, ids, name)
        }
        val sent = Json.parseToJsonElement(textOf(decodeCase("blankWords"))).jsonObject.getValue("ui").jsonObject
            .getValue("screens").jsonObject.getValue("presets").jsonObject.keys.toList()
        assertEquals(listOf("custom_2", "alhamdulillah", "subhanallah", "custom_1"), sent)
        val blank = section("words").single { it.string("feed") == "blankWords" }
        assertEquals(builtIn + "custom_2" + "custom_1", blank.getValue("presetIds").jsonArray.map { it.jsonPrimitive.content })
    }

    /**
     * What each feed's `expect` adds for plan 3's fields: the ring's zero (null for none), whether
     * the interface is in Latin letters, whether the phone shows Sunrise, and day 0's Asr as
     * [WatchRules.clockText] draws it and [WatchRules.spokenClock] says it (null with no day).
     */
    @Test
    fun theExpectHoldsPlan3sFieldsAndDay0sAsr() {
        val feeds = decodeCases().filter { "expect" in it }
        assertTrue(feeds.size > 40, "feeds with an expect: ${feeds.size}")
        for (case in feeds) {
            val name = case.string("name")
            val expect = case.getValue("expect").jsonObject
            val feed = assertNotNull(WatchFeedJson.decode(textOf(case)), name)
            assertEquals(feed.ui.ringDigitZero?.toString(), expect.getValue("ringDigitZero").jsonPrimitive.contentOrNull, name)
            assertEquals(feed.ui.latinScript.toString(), expect.getValue("latinScript").jsonPrimitive.content, name)
            assertEquals(feed.ui.showSunrise.toString(), expect.getValue("showSunrise").jsonPrimitive.content, name)
            val asr = expect.getValue("day0Asr")
            val day0 = feed.days.firstOrNull()
            if (day0 == null) {
                assertEquals(JsonNull, asr, name)
            } else {
                assertEquals(WatchRules.clockText(day0, WatchTime.ASR), asr.jsonObject.string("clockText"), name)
                assertEquals(WatchRules.spokenClock(day0, WatchTime.ASR), asr.jsonObject.string("spokenClock"), name)
            }
        }
        val saudi = decodeCase("sampleArSa").getValue("expect").jsonObject
        assertEquals("0", saudi.string("ringDigitZero"))
        assertEquals("٠", saudi.string("digitZero"))
        assertEquals("false", saudi.getValue("latinScript").jsonPrimitive.content)
        val twelve = decodeCase("sampleEn12").getValue("expect").jsonObject.getValue("day0Asr").jsonObject
        assertEquals("4:10\u00A0PM", twelve.string("clockText"))
        assertEquals("\u20684:10\u00A0PM\u2069", twelve.string("spokenClock"))
    }

    /**
     * The Tile's compact countdown (Mohamed's report of 9 November 2026): `nullCompactHours` is
     * oneDay with `ui.countdown.compactHours` null, which kotlinx refuses, so the port refuses it
     * too, as it does a null at every other key whose default is not null; the Apple Watch reads
     * the three fields for nothing else. `olderPhone`, without them, reads them empty
     * ([theOlderPhoneHasNoneOfPlan3sFields]).
     */
    @Test
    fun theCompactCountdownIsReadAsKotlinReadsIt() {
        val oneDay = Json.parseToJsonElement(textOf(decodeCase("oneDay")))
        val refused = decodeCase("nullCompactHours")
        assertEquals(ONE_CHANGE, refused.string("class"))
        assertEquals("malformed", refused.string("result"))
        assertNull(refused["swift"])
        assertEquals(listOf("ui.countdown.compactHours"), changedPaths(oneDay, Json.parseToJsonElement(textOf(refused))))
    }

    /**
     * The phone's Show sunrise (the owner's decision of 9 November 2026), each case oneDay with one
     * change to `ui.showSunrise`: `sunriseHidden` sets it false, as a phone sends it until the user
     * turns it on, and is the encoder's own text, which the port reads with Sunrise hidden;
     * `nullShowSunrise` sets it null, which kotlinx refuses, so the port refuses it too and never
     * reads the null as the default. `olderPhone`, without the key, reads true
     * ([theOlderPhoneHasNoneOfPlan3sFields]), and `quotedShowSunrise` is its lenient case.
     */
    @Test
    fun theSunriseCasesAreReadAsKotlinReadsThem() {
        val oneDay = Json.parseToJsonElement(textOf(decodeCase("oneDay")))
        val hidden = decodeCase("sunriseHidden")
        assertEquals(ENCODER, hidden.string("class"))
        assertEquals("feed", hidden.string("result"))
        assertEquals("false", hidden.getValue("expect").jsonObject.getValue("showSunrise").jsonPrimitive.content)
        assertEquals(listOf("ui.showSunrise"), changedPaths(oneDay, Json.parseToJsonElement(textOf(hidden))))
        val refused = decodeCase("nullShowSunrise")
        assertEquals(ONE_CHANGE, refused.string("class"))
        assertEquals("malformed", refused.string("result"))
        assertNull(refused["swift"])
        assertEquals(listOf("ui.showSunrise"), changedPaths(oneDay, Json.parseToJsonElement(textOf(refused))))
        assertEquals("true", decodeCase("oneDay").getValue("expect").jsonObject.getValue("showSunrise").jsonPrimitive.content)
        assertTrue("quotedShowSunrise" in LENIENT)
    }

    /**
     * Ruling 57 item 5's moments, each a gap in the earlier vectors that a mutant of the port
     * survived. `ishaAtTheNextFajr` has day 0's Isha exactly at day 1's Fajr: a second before it the
     * next start is day 1's Fajr, since yesterday's starts count only *before* today's Fajr (R12).
     * `aDayOfOneMinute` begins day 1 a minute after day 0, so when its date has turned no start has
     * arrived: the previous is yesterday's Isha, still ahead (the fallback), and the progress is 0,
     * the period running backwards. `ishaFirstInADayOfOneMinute` is the same with day 0's Isha
     * moved before its Fajr, so the fallback's Isha is the next start too: a period of no length,
     * whose progress is 0 by the guard, where the time since that Isha, negative, over 0 would be
     * −∞. `startsAtTheEndOfTime` has every time at the end of time and day 1 beginning near its
     * start: once the date has turned, the previous is yesterday's Isha and the next is today's
     * Fajr, at one instant more than half of a Long ahead. That is a period of no length whose time
     * since its start wraps to a positive number, as Kotlin's `Long` does: the guard alone answers
     * 0 there, where read `total < 0` it would leave +∞, and the clamp 1.
     */
    @Test
    fun theMomentCasesReachTheTieAndTheFallback() {
        val moments = section("moments")
        val tie = assertNotNull(WatchFeedJson.decode(textOf(decodeCase("ishaAtTheNextFajr"))))
        assertEquals(tie.days[1].times.fajr, tie.days[0].times.isha)
        val beforeFajr = moments.single { it.string("feed") == "ishaAtTheNextFajr" && it.long("now") == tie.days[1].times.fajr - 1 }
        assertEquals("FAJR", beforeFajr.getValue("next").jsonObject.string("prayer"))
        assertEquals(1, beforeFajr.getValue("next").jsonObject.int("dayIndex"))
        val oneMinute = assertNotNull(WatchFeedJson.decode(textOf(decodeCase("aDayOfOneMinute"))))
        assertEquals(oneMinute.days[0].midnight + 60, oneMinute.days[1].midnight)
        val fallback = moments.filter { it.string("feed") == "aDayOfOneMinute" && it["previous"] is JsonObject }
            .filter { it.getValue("previous").jsonObject.long("at") > it.long("now") }
        assertTrue(fallback.isNotEmpty(), "a moment whose previous is still ahead: yesterday's Isha, the fallback")
        for (case in fallback) {
            assertEquals("ISHA", case.getValue("previous").jsonObject.string("prayer"))
            assertEquals(0, case.getValue("previous").jsonObject.int("dayIndex"))
            assertEquals(0.0, case.getValue("progress").jsonPrimitive.content.toDouble())
            assertEquals(1, WatchRules.today(oneMinute, case.long("now")))
        }
        val ishaFirst = assertNotNull(WatchFeedJson.decode(textOf(decodeCase("ishaFirstInADayOfOneMinute"))))
        assertTrue(ishaFirst.days[0].times.isha < ishaFirst.days[0].times.fajr)
        val noLength = moments.filter { it.string("feed") == "ishaFirstInADayOfOneMinute" }
            .filter { it["next"] is JsonObject && it.getValue("next") == it.getValue("previous") }
        assertTrue(noLength.isNotEmpty(), "a moment whose next is its previous: a period of no length")
        for (case in noLength) {
            assertEquals("ISHA", case.getValue("previous").jsonObject.string("prayer"))
            assertEquals(0.0, case.getValue("progress").jsonPrimitive.content.toDouble())
        }
        val endOfTime = assertNotNull(WatchFeedJson.decode(textOf(decodeCase("startsAtTheEndOfTime"))))
        val wrapping = moments.filter { it.string("feed") == "startsAtTheEndOfTime" }
            .filter { it["next"] is JsonObject && it["previous"] is JsonObject }
            .filter { it.getValue("next").jsonObject.long("at") == it.getValue("previous").jsonObject.long("at") }
        assertTrue(wrapping.isNotEmpty(), "a period of no length whose time since its start wraps")
        for (case in wrapping) {
            val previous = case.getValue("previous").jsonObject
            assertEquals("ISHA", previous.string("prayer"))
            assertEquals(0, previous.int("dayIndex"))
            assertEquals("FAJR", case.getValue("next").jsonObject.string("prayer"))
            assertEquals(1, case.getValue("next").jsonObject.int("dayIndex"))
            assertEquals(1, WatchRules.today(endOfTime, case.long("now")))
            assertTrue(previous.long("at") > case.long("now"), "yesterday's Isha still ahead: the fallback")
            assertTrue(case.long("now") - previous.long("at") > 0L, "the time since it wraps to a positive number")
            assertEquals(0.0, case.getValue("progress").jsonPrimitive.content.toDouble())
        }
    }

    /**
     * [WatchRules.starts] leaves out a start at or after the next day's Fajr (R16): on
     * `ishaAtTheNextFajr` day 0's Isha, exactly at it, and on `tromsoMay` an Isha after it.
     */
    @Test
    fun theStartsCasesDropAStartAtTheNextFajr() {
        val starts = section("starts").associate { it.string("feed") to it.getValue("starts").jsonArray.map { start -> start.jsonObject } }
        assertEquals(setOf("ishaAtTheNextFajr", "tromsoMay"), starts.keys)
        val tie = starts.getValue("ishaAtTheNextFajr")
        assertEquals(9, tie.size)
        assertTrue(tie.none { it.string("prayer") == "ISHA" && it.int("dayIndex") == 0 })
    }

    /**
     * A feed whose spoken hours or minutes are an empty list has no spoken countdown, at any time
     * left (spokenCountdown's guard): the watch then says the screen's words.
     */
    @Test
    fun theSpokenCasesOnAnEmptyListAreNull() {
        for (name in listOf("emptySpokenHours", "emptySpokenMinutes")) {
            val cases = section("spoken").filter { it.string("feed") == name }
            assertEquals(10, cases.size, name)
            assertTrue(cases.all { it["spoken"] is JsonNull }, name)
        }
    }

    /**
     * [WatchRules.filled] keeps a place that names no part as it is: `%0$s`, whose number is below
     * the first part's, a place beyond the parts, and one beyond an Int.
     */
    @Test
    fun theFilledCasesKeepEveryPlaceWithNoPart() {
        val filled = section("filled").associate { it.string("pattern") to it.string("filled") }
        for (kept in listOf("%0\$s", "%3\$s", "%2147483648\$s")) assertEquals(kept, filled.getValue(kept), kept)
    }

    /**
     * [WatchRules.inDigits] adds the digit to the zero in 16 bits, as Kotlin's `Char` does: from
     * U+FFFA, 6 is U+0000.
     */
    @Test
    fun theInDigitsCasesWrapInSixteenBits() {
        val wrapped = section("inDigits").single { it.string("zero") == Char(0xFFFA).toString() }
        assertEquals("0123456789", wrapped.string("text"))
        assertEquals((0xFFFA..0xFFFF).map { Char(it) } + (0..3).map { Char(it) }, wrapped.string("inDigits").toList())
    }

    /**
     * The labels case by the feed's language tag as Kotlin reads it on the JVM (Ruling 57 item 6):
     * `Locale.forLanguageTag`'s language alone, so a tag with an extension is Turkish, an ill-formed
     * one is no language, and only Turkish, Azerbaijani and Lithuanian case otherwise than the
     * default, where Foundation's ICU also cases Greek its own way.
     */
    @Test
    fun theLabelsCaseByTheLanguageAsKotlinReadsIt() {
        assertEquals("İKİNDİ", label("turkishIkindiTagged", day = 0, prayer = "ASR"))
        assertEquals("IKINDI", label("turkishIkindiIllFormed", day = 0, prayer = "ASR"))
        assertEquals("İKİNDİ", label("azerbaijaniIkindiInCapitals", day = 0, prayer = "ASR"))
        assertEquals("IKINDI", label("lithuanianDotAbove", day = 0, prayer = "ASR"))
        assertEquals("ΆΣΡ", label("greekTonos", day = 0, prayer = "ASR"))
    }

    /**
     * A newer phone's feed need not have this build's shape, and the watch must still say "Update
     * Taqwa on your watch" (spec 1.1, plan 3's call 5). So beside `version2`, the sample with its
     * version alone raised, two `newer` cases have a body this build cannot read even at its own
     * version: a port that decodes first, takes the version from what it decoded and calls any
     * failure malformed answers malformed for them, where Kotlin, reading the version on its own
     * ([WatchFeedJson.versionOf]), answers newer. Every newer case declares a version above
     * [WatchFeedJson.VERSION]. The fourth is `duplicateVersionNewerLast`, whose version is written
     * twice ([aKeyWrittenTwiceIsReadByItsLastCopy]).
     */
    @Test
    fun aNewerFeedOfAnotherShapeIsNewer() {
        val newer = decodeCases().filter { it.string("result") == "newer" }
        assertEquals(listOf("version2", "newerShape", "newerDayShape", "duplicateVersionNewerLast"), newer.map { it.string("name") })
        for (case in newer) {
            val version = assertNotNull(WatchFeedJson.versionOf(textOf(case)), case.string("name"))
            assertTrue(version > WatchFeedJson.VERSION, case.string("name"))
        }
        val unreadable = newer.filter { case ->
            val atThisVersion = Json.parseToJsonElement(textOf(case)).jsonObject.with("version", JsonPrimitive(WatchFeedJson.VERSION))
            WatchFeedJson.decode(atThisVersion.toString()) == null
        }
        assertEquals(listOf("newerShape", "newerDayShape"), unreadable.map { it.string("name") })
    }

    /**
     * The forms on the one-day sample, measured both ways (the Lead's call 8, review rounds 1 to 4
     * of plan 4 Task 1). Each is oneDay with one change, and none carries `swift`. The port is held
     * by each case's class ([everyCaseHasTheClassItsRuleGives], Ruling 56): exactly on ONE_CHANGE
     * and ENCODER, and on FOREIGN only not to crash and to read a feed's values exactly, so there
     * Kotlin's answer, held here, is information.
     * - [KOTLIN_REFUSES] are texts kotlinx refuses and some Foundation reader would take
     *   (`JSONDecoder` reads 1.0 into an `Int`, a 64-bit `Int` holds 3000000000, both readers skip
     *   a byte-order mark, a `Character` is a whole grapheme, `JSONSerialization` bridges `true` to
     *   the `Int` 1, both readers take a trailing comma, and a key spelled with an escape is the key
     *   itself to both). Those of them a phone could write with one value changed (`true`, a number
     *   above 32 bits, a digitZero of two UTF-16 units) are ONE_CHANGE, so the port refuses them as
     *   Kotlin does; the number spellings, the mark, the commas and the escaped key are FOREIGN;
     * - [KOTLIN_READS] are integers written with an exponent and the version's key spelled with an
     *   escape, FOREIGN, and a later phone's keys at version 1 ([LATER_KEYS]), ENCODER, which the
     *   port reads exactly. Kotlin reads each as the one-day sample's own feed, value for value,
     *   though its text is not its text.
     */
    @Test
    fun theFormsOnOneDayAreOneChangeEach() {
        val oneDay = assertNotNull(WatchFeedJson.decode(textOf(decodeCase("oneDay"))))
        val oneDayText = textOf(decodeCase("oneDay"))
        assertEquals(1, oneDay.days.size)
        assertTrue(LATER_KEYS.all { it in KOTLIN_READS })
        assertTrue(TRAILING_COMMAS.all { it in KOTLIN_REFUSES })
        // The mark is the one change: the text after it is oneDay's own, and only this case has one.
        assertEquals(decodeCase("oneDay").string("json"), decodeCase("byteOrderMark").string("json"))
        assertEquals(listOf("byteOrderMark"), decodeCases().filter { "bom" in it }.map { it.string("name") })
        assertEquals(BOM + oneDayText, textOf(decodeCase("byteOrderMark")))
        // A trailing comma is the one change: one comma before a closing bracket, and oneDay's text without it.
        // The root's comma sits before the text's last brace, the list's before a bracket, the object's inside the text.
        val closing = TRAILING_COMMAS.map { name ->
            val text = textOf(decodeCase(name))
            val commas = Regex(",(?=[}\\]])").findAll(text).toList()
            assertEquals(1, commas.size, name)
            assertEquals(oneDayText, text.removeRange(commas.single().range), name)
            text.length - commas.single().range.first to text[commas.single().range.first + 1]
        }
        assertEquals(2 to '}', closing[0])
        assertEquals(']', closing[1].second)
        assertTrue(closing[2].first > 2 && closing[2].second == '}')
        // A key spelled with an escape is the key itself: the texts are another case's, but for one key's spelling.
        val escapedText = mapOf("escapedVersionKey" to "oneDay", "escapedKeyFractionalWrittenAt" to "fractionalWrittenAt")
        for ((name, plain) in escapedText) {
            val text = textOf(decodeCase(name))
            assertTrue(Char(92) + "u00" in text, "$name spells a key with an escape")
            assertNotEquals(textOf(decodeCase(plain)), text, name)
            assertEquals(Json.parseToJsonElement(textOf(decodeCase(plain))), Json.parseToJsonElement(text), name)
        }
        assertEquals(ESCAPED_KEYS.toSet(), escapedText.keys)
        for (name in KOTLIN_REFUSES) {
            val case = decodeCase(name)
            assertEquals("malformed", case.string("result"), name)
            assertNull(case["swift"], name)
            assertNull(case["expect"], name)
        }
        for (name in KOTLIN_READS) {
            val case = decodeCase(name)
            assertEquals("feed", case.string("result"), name)
            assertNull(case["swift"], name)
            assertNotEquals(textOf(decodeCase("oneDay")), textOf(case), "$name is written otherwise than oneDay")
            assertEquals(oneDay, WatchFeedJson.decode(textOf(case)), name)
        }
    }

    /**
     * `newerPhone` is a later phone's text at version 1: the encoder's own text of a model's value,
     * with a key no build reads, `future`, in every kind of object the feed holds, each of the
     * model's twelve classes once ([NEWER_PHONE_STRAYS], held to [OBJECT_TYPES], so a class added
     * to the model fails here until `newerPhone` holds a `future` in it too): the root, the place,
     * the interface, its countdown phrases, its spoken words, its screens' words and one of their
     * counts, a day, its times and its 12-hour times, and a phrase (Ruling 60 item 1d; the countdown
     * phrases since its review round 1). Only a 12-hour phone's days carry a `clockTime`, so it is
     * the twelve-hour English sample with the first sample's phrases. Kotlin reads it as that feed,
     * every `future` ignored, and so must the port, each of whose objects decodes its own keys.
     */
    @Test
    fun aLaterPhonesKeyIsIgnoredInEveryObject() {
        val text = textOf(decodeCase("newerPhone"))
        val tree = Json.parseToJsonElement(text)
        val strays = strays(tree, "", setOf("future"))
        assertEquals(NEWER_PHONE_STRAYS, strays.map { it.path })
        assertTrue(strays.all { it.value == JsonPrimitive(1) }, "$strays")
        val kinds = strays.map { OBJECT_TYPES.getValue(it.path.removeSuffix("future").removeSuffix(".")) }
        assertEquals(OBJECT_TYPES.values.toSortedSet().toList(), kinds.sorted(), "one future in each class the model declares")
        fun JsonElement.withoutFuture(): JsonElement = when (this) {
            is JsonObject -> JsonObject(filterKeys { it != "future" }.mapValues { it.value.withoutFuture() })
            is JsonArray -> JsonArray(map { it.withoutFuture() })
            else -> this
        }
        val plain = tree.withoutFuture().toString()
        val feed = assertNotNull(WatchFeedJson.decode(plain), "newerPhone without its strays")
        assertEquals(WatchFeedJson.encode(feed), plain, "newerPhone without its strays is the encoder's own text")
        assertEquals(feed, WatchFeedJson.decode(text), "Kotlin ignores every future")
        assertEquals(Fixtures.capeTownSamples().getValue("en12").copy(tasbeeh = Fixtures.capeTownSample().tasbeeh), feed)
        assertNotNull(feed.days[0].clockTime)
        assertTrue(feed.ui.screens.turnRight.isNotEmpty() && feed.tasbeeh.isNotEmpty())
    }

    /**
     * A later phone may add a field at version 1, since a field added with a default does not raise
     * [WatchFeedJson.VERSION], and every build ignores a key it does not know. Such a key may share a
     * name with one of the model's integer fields ([INTEGER_PATHS]) and hold anything. So each
     * [LATER_KEYS] case holds keys named like the integer fields at paths the model does not have:
     * among them a fraction, a string and a version below the root, some before an object's own keys
     * and some after them. Kotlin reads each as oneDay's own feed
     * ([theFormsOnOneDayAreOneChangeEach]). A port that checks an integer's form by its key's
     * name, wherever the key sits, refuses them, where the Galaxy reads them: its checks must follow
     * the model's own paths. [INTEGER_PATHS] are those paths, held to the model's serializers.
     * They are ENCODER (Ruling 56), so the port reads them exactly.
     */
    @Test
    fun aLaterPhonesKeysNamedLikeTheIntegersAreIgnored() {
        assertEquals(INTEGER_PATHS, integerPaths(WatchFeed.serializer().descriptor))
        val names = INTEGER_PATHS.map { it.substringAfterLast('.') }.toSet()
        for (name in LATER_KEYS) {
            val strays = strays(Json.parseToJsonElement(textOf(decodeCase(name))), "", names)
            fun has(what: String, test: (Stray) -> Boolean) = assertTrue(strays.any(test), "$name has $what: $strays")
            has("a fraction") { val value = it.value as? JsonPrimitive; value != null && !value.isString && '.' in value.content }
            has("a string") { (it.value as? JsonPrimitive)?.isString == true }
            has("a version below the root") { it.path != "version" && it.path.endsWith(".version") }
            has("one before an object's own keys") { it.where == Stray.Where.BEFORE }
            has("one after an object's own keys") { it.where == Stray.Where.AFTER }
        }
    }

    /**
     * A key written twice in one object (review round 3), FOREIGN (Ruling 56): no encoder writes one,
     * so what follows is Kotlin's reading, held here as information, and the port is held only not
     * to crash and to read a feed's values exactly. kotlinx reads every copy as its field, so a
     * copy that is no value of the field makes the text malformed, and it keeps the last copy, the
     * version's too, holding only that copy to 1..[WatchFeedJson.VERSION]. Foundation's
     * `JSONDecoder` and `JSONSerialization` keep the first, so on the version the answer flips both
     * ways. Each [DUPLICATE_KEYS] case is oneDay with one key written a
     * second time at the path named, its two copies read alike by no reader: each copy alone (the
     * other renamed to a key no build reads) gives another answer or another feed, so the copy
     * Kotlin keeps shows. A feed or a newer version is the last copy's. No other case writes a key
     * twice. Kotlin holds every copy to its field's grammar, the one not kept included
     * ([WHOLE_FRACTION_FIRST], review round 4).
     */
    @Test
    fun aKeyWrittenTwiceIsReadByItsLastCopy() {
        val oneDay = Json.parseToJsonElement(textOf(decodeCase("oneDay")))
        for (case in decodeCases()) {
            val name = case.string("name")
            val text = textOf(case)
            val repeats = repeatedKeys(text)
            val path = DUPLICATE_KEYS[name]
            if (path == null) {
                assertEquals(emptyList(), repeats, "$name writes a key twice")
                continue
            }
            val repeat = repeats.singleOrNull() ?: fail("$name writes one key twice: $repeats")
            assertEquals(path, repeat.path, name)
            assertEquals(2, repeat.at.size, name)
            val firstAlone = renamedAt(text, repeat.at[1])
            val lastAlone = renamedAt(text, repeat.at[0])
            // oneDay with a copy added: before oneDay's own, or after it where the name ends in Last.
            val added = if (name.endsWith("Last")) firstAlone else lastAlone
            assertEquals(oneDay, Json.parseToJsonElement(added).withoutRenamed(), "$name is oneDay with a key written again")
            val readings = listOf(firstAlone, lastAlone).map { kotlinAnswer(it) to WatchFeedJson.decode(it) }
            assertNotEquals(readings[0], readings[1], "$name's two copies read alike, so the copy Kotlin keeps does not show")
            when (case.string("result")) {
                "feed" -> assertEquals(WatchFeedJson.decode(lastAlone), WatchFeedJson.decode(text), name)
                "newer" -> assertEquals(WatchFeedJson.versionOf(lastAlone), WatchFeedJson.versionOf(text), name)
                else -> assertTrue(readings.any { it.first == "malformed" } && readings.any { it.first != "malformed" }, name)
            }
        }
        assertEquals(
            listOf("newer", "feed", "feed", "malformed", "malformed", "malformed", "feed", "feed", "malformed"),
            DUPLICATE_KEYS.keys.map { decodeCase(it).string("result") },
        )
        assertEquals(DUPLICATE_KEYS.keys.toList(), decodeCases().map { it.string("name") }.filter { it in DUPLICATE_KEYS })
        // Every copy of an integer field is held to the grammar, the one not kept included (review
        // round 4): the first copy is the kept copy's own number written with ".0", so the two copies
        // are one number to JSONDecoder, and the kept copy alone is oneDay's feed.
        assertTrue(WHOLE_FRACTION_FIRST.all { it in DUPLICATE_KEYS })
        for (name in WHOLE_FRACTION_FIRST) {
            val text = textOf(decodeCase(name))
            val repeat = repeatedKeys(text).single()
            assertTrue(repeat.path in INTEGER_PATHS, name)
            val (first, last) = repeat.at.map { valueAt(text, it) }
            assertEquals("$last.0", first, name)
            assertEquals("malformed", decodeCase(name).string("result"), name)
            assertEquals("feed", kotlinAnswer(renamedAt(text, repeat.at[0])), name)
        }
    }

    /**
     * Day 6 of each sample is Friday 23 October (day 0 is Saturday 17 October, since the samples
     * start yesterday), and its Dhuhr's label is the Jumuʿah in capitals, never "DHUHR". Day 0's is
     * Dhuhr's own.
     */
    @Test
    fun theFridayLabelIsTheJumuah() {
        val friday = mapOf(
            "sample" to "JUMUʿAH",
            "sampleEn12" to "JUMUʿAH",
            "sampleArLy12" to "الجمعة",
            "sampleArSa" to "الجمعة",
        )
        val saturday = mapOf("sample" to "DHUHR", "sampleEn12" to "DHUHR", "sampleArLy12" to "الظهر", "sampleArSa" to "الظهر")
        for ((name, jumuah) in friday) {
            val feed = assertNotNull(WatchFeedJson.decode(textOf(decodeCase(name))), name)
            assertEquals("2026-10-23", feed.days[6].date, name)
            assertEquals("2026-10-17", feed.days[0].date, name)
            assertEquals(jumuah, label(name, day = 6, prayer = "DHUHR"), name)
            assertNotEquals("DHUHR", label(name, day = 6, prayer = "DHUHR"), name)
            assertEquals(saturday.getValue(name), label(name, day = 0, prayer = "DHUHR"), name)
        }
    }

    /**
     * The `days` cases sit on both sides of each turn of the date they name: a second before a day
     * begins is the day before (or no day, before the feed), the second it begins and the one after
     * are that day, and the second the last day ends is no day. `sample` has every midnight, so it
     * turns over at them; `noMidnights` has none (every midnight 0), so it turns over at solar
     * midnight, the fallback; `tromsoMay` is the high-latitude night, its midnights real. A feed
     * held to the wrong premise (plan 4's errata, p4 #3: `tromsoMay`'s midnights are not 0, and it
     * has 3 days, not 31) fails here.
     */
    @Test
    fun theDaysCasesSitOnBothSidesOfEachTurnOver() {
        val covered = mapOf("sample" to listOf(0, 1, 30), "noMidnights" to listOf(0, 1, 30), "tromsoMay" to listOf(0, 1, 2))
        val days = section("days")
        for ((name, indices) in covered) {
            val feed = assertNotNull(WatchFeedJson.decode(textOf(decodeCase(name))), name)
            if (name == "noMidnights") {
                assertTrue(feed.days.all { it.midnight == 0L }, "$name has no midnight")
            } else {
                assertTrue(feed.days.all { it.midnight != 0L }, "$name has every midnight")
            }
            assertEquals(indices.last() + 1, feed.days.size, "$name's last covered day is its last")
            val cases = days.filter { it.string("feed") == name }
            for (index in indices) {
                val start = WatchRules.dayStart(feed, index)
                assertEquals(if (index == 0) null else index - 1, todayAt(cases, start - 1), "$name, before day $index")
                assertEquals(index, todayAt(cases, start), "$name, as day $index begins")
                assertEquals(index, todayAt(cases, start + 1), "$name, after day $index begins")
            }
            val ended = cases.last()
            assertNull(ended["today"]?.jsonPrimitive?.longOrNull, "$name, after its last day")
            assertEquals(indices.last(), todayAt(cases, ended.long("now") - 1), "$name, its last second")
        }
    }

    /**
     * Every one of a day's six times has a clock case, keyed by [WatchTime] and Sunrise among them
     * (plan 4's errata, p4 #2): a 12-hour Sunrise is drawn with its day period and said inside a
     * first-strong isolate, and a 24-hour time, in any digits, is the same both ways.
     */
    @Test
    fun everyTimeOfTheDayHasAClockCaseSunriseIncluded() {
        val clock = section("clock")
        for (name in SAMPLES) {
            for (day in listOf(0, 6)) {
                val times = clock.filter { it.string("feed") == name && it.int("day") == day }.map { it.string("time") }
                assertEquals(WatchTime.entries.map { it.name }, times, "$name, day $day")
            }
        }
        fun case(feed: String, time: String) =
            clock.first { it.string("feed") == feed && it.int("day") == 0 && it.string("time") == time }
        assertEquals("6:01\u00A0AM", case("sampleEn12", "SUNRISE").string("clockText"))
        assertEquals("\u20686:01\u00A0AM\u2069", case("sampleEn12", "SUNRISE").string("spokenClock"))
        assertEquals("\u20684:10\u00A0م\u2069", case("sampleArLy12", "ASR").string("spokenClock"))
        assertEquals("6:01", case("sample", "SUNRISE").string("clockText"))
        assertEquals("6:01", case("sample", "SUNRISE").string("spokenClock"))
        assertEquals("١٦:١٠", case("sampleArSa", "ASR").string("clockText"))
        assertEquals("١٦:١٠", case("sampleArSa", "ASR").string("spokenClock"))
    }

    /**
     * `clockForms` (plan 4 Task 3) holds [WatchRules.spokenClock]'s isolate rule to Kotlin's own
     * `Char.isDigit` beyond the samples' digits: on day 0, a 24-hour phone's clock strings in
     * Bengali, extended Arabic-Indic and Devanagari digits, each a decimal digit and so said as it
     * is, a time in the mathematical bold digits, a pair of UTF-16 units each of which is no digit
     * to Kotlin and so isolated, and two with a point or a space; on day 1, a 12-hour phone's
     * `clockTime` with its marker first (`ÖS 4:10`, as the phone writes Turkish), a narrow no-break
     * space before it, and one with no day period at all, said as it is; on day 2 (Ruling 60 item
     * 1a), clock strings in [NUMERALS], numbers that are no decimal digit (superscript and circled
     * digits, circled numbers and a Roman numeral), each one UTF-16 unit that Kotlin's `Char.isDigit`
     * refuses, so every one is isolated. A port that takes any number for a digit (Swift's
     * `Character.isNumber`, or a `numericType`) says them bare.
     */
    @Test
    fun theClockCasesHoldTheIsolateRuleInEveryDigitSet() {
        val forms = section("clock").filter { it.string("feed") == "clockForms" }
        assertEquals(18, forms.size)
        for (time in WatchTime.entries) {
            val numerals = NUMERALS.of(time)
            val day2 = forms.single { it.int("day") == 2 && it.string("time") == time.name }
            assertEquals(numerals, day2.string("clockText"), "day 2 $time drawn")
            assertEquals(Char(0x2068) + numerals + Char(0x2069), day2.string("spokenClock"), "day 2 $time isolated")
            // Each holds a number of one unit that is no decimal digit, and nothing else but digits and a colon.
            assertTrue(numerals.any { Character.getType(it).toByte() in NOT_DECIMAL_NUMBERS }, "$time: $numerals")
            assertTrue(numerals.all { it == ':' || it.isDigit() || Character.getType(it).toByte() in NOT_DECIMAL_NUMBERS }, "$time: $numerals")
        }
        fun spoken(day: Int, time: String) = forms.single { it.int("day") == day && it.string("time") == time }.string("spokenClock")
        val isolate = { text: String -> "\u2068$text\u2069" }
        assertEquals("০৪:৩৩", spoken(0, "FAJR"))
        assertEquals("۰۶:۰۱", spoken(0, "SUNRISE"))
        assertEquals("१२:३३", spoken(0, "DHUHR"))
        assertEquals(isolate(MATH_BOLD_ASR), spoken(0, "ASR"))
        assertEquals(isolate("19.04"), spoken(0, "MAGHRIB"))
        assertEquals(isolate("20 24"), spoken(0, "ISHA"))
        assertEquals(isolate("ÖÖ 4:33"), spoken(1, "FAJR"))
        assertEquals(isolate("6:01\u202FAM"), spoken(1, "SUNRISE"))
        assertEquals("12:33", spoken(1, "DHUHR"))
        assertEquals(isolate("ÖS 4:10"), spoken(1, "ASR"))
    }

    // ---- Reading the vectors

    private fun section(key: String): List<JsonObject> = vectors.getValue(key).jsonArray.map { it.jsonObject }

    private fun decodeCases(): List<JsonObject> = section("decode")

    private fun decodeCase(name: String): JsonObject =
        decodeCases().firstOrNull { it.string("name") == name } ?: fail("no decode case $name")

    /**
     * The text a decode case holds, or the text of the file beside this one that it names, after a
     * byte-order mark when the case says `bom`.
     */
    private fun textOf(case: JsonObject): String {
        val text = case["json"]?.jsonPrimitive?.content
            ?: sampleFiles[case.string("file")]
            ?: fail("${case.string("name")} names no text")
        return if (case["bom"]?.jsonPrimitive?.content == "true") BOM + text else text
    }

    /** The text a case's class is judged on: its text, a file's without the line end it closes with. */
    private fun judgedText(case: JsonObject): String =
        if ("file" in case) textOf(case).removeSuffix("\n") else textOf(case)

    private fun label(feed: String, day: Int, prayer: String): String =
        section("labels").firstOrNull { it.string("feed") == feed && it.int("day") == day && it.string("prayer") == prayer }
            ?.string("label") ?: fail("no label for $feed, day $day, $prayer")

    private fun todayAt(cases: List<JsonObject>, now: Long): Int? {
        val case = cases.firstOrNull { it.long("now") == now } ?: fail("no days case at $now")
        return case["today"]?.jsonPrimitive?.longOrNull?.toInt()
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
    private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

    private companion object {
        const val FILE = "feed-vectors.json"
        const val HOW_TO_WRITE = "To write it: ./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors. " +
            "Read the diff before you commit it."

        /** The four texts kotlinx reads and the port refuses (the Lead's call 8), in the file's order. */
        val QUOTED = listOf("quotedWrittenAt", "quotedVersion", "quotedBearing", "quotedRtl")

        /**
         * Ruling 57 item 2: oneDay with one more field the port reads written in the wrong type
         * kotlinx reads leniently, which the port refuses, in the file's order: each a field of
         * [lenientPaths] but the four [QUOTED] change and [PLAN_3_LENIENT].
         */
        val LENIENT = listOf(
            "quotedFajr",
            "quotedSunrise",
            "quotedDhuhr",
            "quotedAsr",
            "quotedMaghrib",
            "quotedIsha",
            "quotedFajrEnd",
            "quotedAsrEnd",
            "quotedMidnight",
            "quotedDeclination",
            "quotedSouthern",
            "bareDigitZero",
            "quotedTwelveHour",
            "quotedHoursUpTo",
            "quotedMinutesUpTo",
            "quotedTurnRightUpTo",
            "quotedTurnLeftUpTo",
            "bareRingDigitZero",
            "quotedLatinScript",
            "quotedShowSunrise",
            "quotedTarget",
        )

        /** The fields the four [QUOTED] cases write as strings, on the sample. */
        val QUOTED_PATHS = setOf("writtenAt", "version", "place.qiblaBearing", "ui.rtl")

        /**
         * The countdown's compact forms, the Galaxy Tile's alone: the port reads them only so that it
         * refuses what Kotlin refuses ([theCompactCountdownIsReadAsKotlinReadsIt]).
         */
        val COMPACT_COUNTDOWN = listOf("compactHoursMinutes", "compactHours", "compactMinutes")

        /** Plan 3's fields kotlinx reads leniently, which the port reads since plan 4 Task 3, with their cases. */
        val PLAN_3_LENIENT = setOf("ui.screens.turnRight[].upTo", "ui.screens.turnLeft[].upTo", "ui.ringDigitZero", "ui.latinScript")

        /**
         * Ruling 57 item 4: plan 3's keys written as kotlinx refuses them, each oneDay with one
         * change, by the path it changes, in the file's order.
         */
        val PLAN_3_REFUSED = linkedMapOf(
            "nullScreens" to "ui.screens",
            "nullLatinScript" to "ui.latinScript",
            "twoCharacterRingZero" to "ui.ringDigitZero",
            "numberClockTime" to "days[0].clockTime",
        )

        /**
         * Where `newerPhone` holds its key no build reads, `future`, in [strays]' spelling and the
         * text's order: one in every kind of object the feed holds, each of the model's twelve
         * classes ([OBJECT_TYPES]) once (Ruling 60 item 1d), the countdown phrases (since its review
         * round 1), the screens' words, the face screen's (plan 5 Task 5), one of their counts (`turnRight`'s first) and day 0's 12-hour
         * times among them.
         */
        val NEWER_PHONE_STRAYS = listOf(
            "days[].times.future",
            "days[].clockTime.future",
            "days[].future",
            "place.future",
            "ui.countdown.future",
            "ui.spoken.future",
            "ui.screens.turnRight[].future",
            "ui.screens.future",
            "ui.face.future",
            "ui.future",
            "tasbeeh[].future",
            "future",
        )

        /** 16:10 in the mathematical bold digits, U+1D7CF and U+1D7CE among them: each two UTF-16 units. */
        val MATH_BOLD_ASR: String = listOf(0x1D7CF, 0x1D7D4, ':'.code, 0x1D7CF, 0x1D7CE)
            .joinToString("") { String(Character.toChars(it)) }

        /**
         * `clockForms`' day 2 (Ruling 60 item 1a): a day's clock strings in numbers that are no
         * decimal digit, each of one UTF-16 unit: superscript digits (U+2070, U+2074, U+2076, U+00B9,
         * U+00B3), circled digits (U+2460 to U+2463, U+24EA) and numbers (U+246F, U+2472), and the
         * Roman numeral ten (U+2169) beside Western digits. Unicode's No and Nl, never Nd.
         */
        val NUMERALS: WatchSix<String> = WatchSix(
            fajr = units(0x2070, 0x2074, ':'.code, 0x00B3, 0x00B3),
            sunrise = units(0x2070, 0x2076, ':'.code, 0x2070, 0x00B9),
            dhuhr = units(0x2460, 0x2461, ':'.code, 0x2462, 0x2462),
            asr = units(0x246F, ':'.code, 0x2460, 0x24EA),
            maghrib = units(0x2472, ':'.code, 0x24EA, 0x2463),
            isha = units(0x2169, 0x2169, ':'.code, '2'.code, '4'.code),
        )

        /** Unicode's numbers that are no decimal digit: other numbers (No) and letter numbers (Nl). */
        val NOT_DECIMAL_NUMBERS: Set<Byte> = setOf(Character.OTHER_NUMBER, Character.LETTER_NUMBER)

        /** A string of these UTF-16 units. */
        private fun units(vararg codes: Int): String = String(CharArray(codes.size) { Char(codes[it]) })

        // ---- The cases' classes (Ruling 56 item 2)

        const val ENCODER = "ENCODER"
        const val ONE_CHANGE = "ONE_CHANGE"
        const val FOREIGN = "FOREIGN"

        /**
         * The FOREIGN cases, in the file's order: fixed, since a new one needs a Lead's ruling (Ruling
         * 56 item 3: every existing case kept, none added).
         */
        val FOREIGN_CASES = listOf(
            "notJson",
            "fractionalVersion",
            "fractionalNewerVersion",
            "fractionalWrittenAt",
            "fractionalTime",
            "fractionalTarget",
            "byteOrderMark",
            "trailingCommaAtTheTop",
            "trailingCommaInAList",
            "trailingCommaInAnObject",
            "escapedKeyFractionalWrittenAt",
            "exponentVersion",
            "exponentWrittenAt",
            "escapedVersionKey",
            "duplicateVersionNewerLast",
            "duplicateVersionNewerFirst",
            "duplicateVersionZeroFirst",
            "duplicateVersionWholeFractionFirst",
            "duplicateWrittenAtFractionLast",
            "duplicateWrittenAtWholeFractionFirst",
            "duplicateMidnightInADay",
            "duplicatePhrasesLast",
            "duplicateCityNotAString",
        )

        /**
         * The ENCODER cases whose text is [WatchFeedJson.encode]'s own for the feed it holds (its
         * version aside), in the file's order. The other ENCODER cases are an older or a later
         * phone's, which the same encoder writes over their JSON tree.
         */
        val MODEL_TEXTS = listOf(
            "sample",
            "sampleEn12",
            "sampleArLy12",
            "sampleArSa",
            "version2",
            "emptyDays",
            "sunriseHidden",
            "oneDay",
            "turkishIkindi",
            "englishIkindi",
            "noMidnights",
            "tromsoMay",
            "ishaAtTheNextFajr",
            "aDayOfOneMinute",
            "ishaFirstInADayOfOneMinute",
            "startsAtTheEndOfTime",
            "emptySpokenHours",
            "emptySpokenMinutes",
            "turkishIkindiTagged",
            "turkishIkindiIllFormed",
            "azerbaijaniIkindiInCapitals",
            "lithuanianDotAbove",
            "greekTonos",
            "blankWords",
            "clockForms",
        )

        /** The phone's encoder, configured as [WatchFeedJson]'s, here to write a JSON tree and read a feed with no version check. */
        val PHONE_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** An integer as kotlinx's encoder writes one: digits alone, no point, no exponent, no leading zero. */
        private val WHOLE = Regex("-?(0|[1-9][0-9]*)")

        /**
         * The file's rule for a case's class, on [text] alone and the ENCODER cases' trees:
         * - FOREIGN when kotlinx's encoder writes no such text ([isForeign]);
         * - else ENCODER when it holds a value of a model the plans name ([holdsAModelsValue]);
         * - else ONE_CHANGE when it is an ENCODER case's text with one value changed, removed or
         *   set to null ([changedPaths]: one path);
         * - else none, which no case may be.
         */
        fun classOf(text: String, encoderTrees: List<JsonElement>): String? {
            if (isForeign(text)) return FOREIGN
            val tree = Json.parseToJsonElement(text)
            if (holdsAModelsValue(tree)) return ENCODER
            if (encoderTrees.any { changedPaths(it, tree).size == 1 }) return ONE_CHANGE
            return null
        }

        /**
         * Text kotlinx's encoder never writes: its own reader reads no JSON from it (a cut text, a
         * trailing comma, a bad escape, a mark before it), or the encoder, writing back the JSON it
         * reads, writes another text (a number in another spelling, an escape in a key, a key
         * written twice, a raw control character), or a number at one of the model's integer fields
         * ([INTEGER_PATHS]), which is an Int or a Long in every model the plans name, has a point or
         * an exponent.
         */
        fun isForeign(text: String): Boolean {
            val tree = try {
                PHONE_JSON.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                return true
            }
            if (PHONE_JSON.encodeToString(JsonElement.serializer(), tree) != text) return true
            return integersAt(tree, "").any { !WHOLE.matches(it) }
        }

        private val WORDS = setOf("true", "false")

        /** The numbers written at the model's integer paths in [element], which sits at [path], as written. */
        private fun integersAt(element: JsonElement, path: String): List<String> = when (element) {
            is JsonObject -> element.entries.flatMap { (key, value) -> integersAt(value, child(path, key)) }
            is JsonArray -> element.flatMap { integersAt(it, "$path[]") }
            // A number: not a string, null, true or false, which are another type, never another spelling.
            is JsonPrimitive -> if (path in INTEGER_PATHS && element !is JsonNull && !element.isString && element.content !in WORDS) {
                listOf(element.content)
            } else {
                emptyList()
            }
        }

        /**
         * A value of a model the plans name: a newer phone's, a version above [WatchFeedJson.VERSION]
         * whatever the rest holds; or, at 1 to [WatchFeedJson.VERSION], a value of [WatchFeed] at
         * every key the model has ([holdsAValueOf]), any other key a later phone's and ignored, and a
         * defaulted key free to be absent, as an older phone leaves it.
         */
        fun holdsAModelsValue(tree: JsonElement): Boolean {
            val version = ((tree as? JsonObject)?.get("version") as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
                ?: return false
            if (version > WatchFeedJson.VERSION) return true
            return version >= 1 && holdsAValueOf(tree, WatchFeed.serializer().descriptor)
        }

        /**
         * Whether [element] is a value of the field [descriptor] describes: an Int a whole number in
         * 32 bits and a Long in 64, a Double a number, a Boolean true or false, a String a string and
         * a Char a string of one UTF-16 unit, null only where the field is nullable, an object with
         * each of its class's keys a value of its field and every key without a default there.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun holdsAValueOf(element: JsonElement, descriptor: SerialDescriptor): Boolean {
            if (element is JsonNull) return descriptor.isNullable
            val primitive = element as? JsonPrimitive
            return when (descriptor.kind) {
                PrimitiveKind.INT -> primitive != null && !primitive.isString && primitive.content.toIntOrNull() != null
                PrimitiveKind.LONG -> primitive != null && !primitive.isString && primitive.content.toLongOrNull() != null
                PrimitiveKind.DOUBLE -> primitive != null && !primitive.isString && primitive.content.toDoubleOrNull()?.isFinite() == true
                PrimitiveKind.BOOLEAN -> primitive != null && !primitive.isString && primitive.content in setOf("true", "false")
                PrimitiveKind.STRING -> primitive != null && primitive.isString
                PrimitiveKind.CHAR -> primitive != null && primitive.isString && primitive.content.length == 1
                StructureKind.CLASS -> element is JsonObject && (0 until descriptor.elementsCount).all { index ->
                    val value = element[descriptor.getElementName(index)]
                    if (value == null) descriptor.isElementOptional(index) else holdsAValueOf(value, descriptor.getElementDescriptor(index))
                }
                StructureKind.LIST -> element is JsonArray && element.all { holdsAValueOf(it, descriptor.getElementDescriptor(0)) }
                StructureKind.MAP -> element is JsonObject && element.values.all { holdsAValueOf(it, descriptor.getElementDescriptor(1)) }
                else -> fail("the class rule does not read ${descriptor.serialName}, of kind ${descriptor.kind}")
            }
        }

        /** The paths at which [a] and [b] differ: a key in one alone, a list of another length, or another value. */
        fun changedPaths(a: JsonElement, b: JsonElement, path: String = ""): List<String> = when {
            a is JsonObject && b is JsonObject -> (a.keys + b.keys).flatMap { key ->
                val x = a[key]
                val y = b[key]
                if (x == null || y == null) listOf(child(path, key)) else changedPaths(x, y, child(path, key))
            }
            a is JsonArray && b is JsonArray && a.size == b.size -> a.indices.flatMap { changedPaths(a[it], b[it], "$path[$it]") }
            a == b -> emptyList()
            else -> listOf(path)
        }

        /**
         * Forms on oneDay that some Foundation reader would take and kotlinx refuses, in the file's
         * order: the port refuses the ONE_CHANGE among them, as Kotlin does, and on the FOREIGN ones
         * Kotlin's answer is information (Ruling 56).
         */
        val KOTLIN_REFUSES = listOf(
            "fractionalVersion",
            "fractionalNewerVersion",
            "booleanVersion",
            "wideVersion",
            "fractionalWrittenAt",
            "fractionalTime",
            "fractionalTarget",
            "wideTarget",
            "byteOrderMark",
            "combiningDigitZero",
            "astralDigitZero",
            "lineBreakDigitZero",
            "trailingCommaAtTheTop",
            "trailingCommaInAList",
            "trailingCommaInAnObject",
            "escapedKeyFractionalWrittenAt",
        )

        /** oneDay with one comma before a closing bracket: of the root, of the phrases' list, of the place. */
        val TRAILING_COMMAS = listOf("trailingCommaAtTheTop", "trailingCommaInAList", "trailingCommaInAnObject")

        /** oneDay with a key spelled with an escape, and fractionalWrittenAt with its key so spelled. */
        val ESCAPED_KEYS = listOf("escapedVersionKey", "escapedKeyFractionalWrittenAt")

        /**
         * oneDay with a later phone's keys at version 1, named like the integer fields at paths the
         * model does not have: at the top, in a day and its times, and in the place, the words, the
         * spoken counts and a phrase ([aLaterPhonesKeysNamedLikeTheIntegersAreIgnored]).
         */
        val LATER_KEYS = listOf("laterKeysAtTheTop", "laterKeysInADay", "laterKeysInThePlaceUiAndPhrases")

        /**
         * What kotlinx reads as oneDay's own feed, value for value: integers written with an
         * exponent and the version's key spelled with an escape, FOREIGN, and [LATER_KEYS], ENCODER
         * and so feeds for the port too.
         */
        val KOTLIN_READS = listOf("exponentVersion", "exponentWrittenAt", "escapedVersionKey") + LATER_KEYS

        /**
         * oneDay with one key written a second time, by that key's path, in the file's order
         * ([aKeyWrittenTwiceIsReadByItsLastCopy]). The copy added comes after oneDay's own where the
         * name ends in Last, and before it elsewhere.
         */
        val DUPLICATE_KEYS = linkedMapOf(
            "duplicateVersionNewerLast" to "version",
            "duplicateVersionNewerFirst" to "version",
            "duplicateVersionZeroFirst" to "version",
            "duplicateVersionWholeFractionFirst" to "version",
            "duplicateWrittenAtFractionLast" to "writtenAt",
            "duplicateWrittenAtWholeFractionFirst" to "writtenAt",
            "duplicateMidnightInADay" to "days[].midnight",
            "duplicatePhrasesLast" to "tasbeeh",
            "duplicateCityNotAString" to "place.city",
        )

        /**
         * The [DUPLICATE_KEYS] cases whose first copy, the one not kept, is an integer field written
         * as the kept copy's own number with ".0" (review round 4). `JSONDecoder` reads both copies
         * into an `Int` alike, where kotlinx refuses the text: FOREIGN, so the port may answer feed,
         * which reads the value 1792303200 or 1 exactly (Ruling 56).
         */
        val WHOLE_FRACTION_FIRST = listOf("duplicateVersionWholeFractionFirst", "duplicateWrittenAtWholeFractionFirst")

        /** A key written more than once in one object: its path, and where each copy's opening quote is. */
        data class Repeat(val path: String, val at: List<Int>)

        /**
         * Every key [text] writes more than once in one object. kotlinx's tree keeps one value for a
         * key, so this reads the text itself: its strings, brackets and colons, nothing else, so that
         * a trailing comma or a mark before the text does not stop it. A key is read with its escapes
         * undone, as both decoders read it.
         */
        fun repeatedKeys(text: String): List<Repeat> {
            class Open(val path: String, val isObject: Boolean) {
                val keys = linkedMapOf<String, MutableList<Int>>()
                var key = ""
            }
            val open = ArrayDeque<Open>()
            val repeats = mutableListOf<Repeat>()
            fun here() = open.lastOrNull()?.let { if (it.isObject) child(it.path, it.key) else "${it.path}[]" } ?: ""
            var at = 0
            while (at < text.length) {
                when (text[at]) {
                    '{' -> open.addLast(Open(here(), isObject = true))
                    '[' -> open.addLast(Open(here(), isObject = false))
                    '}', ']' -> open.removeLastOrNull()?.let { closed ->
                        closed.keys.forEach { (key, copies) -> if (copies.size > 1) repeats += Repeat(child(closed.path, key), copies) }
                    }
                    '"' -> {
                        var end = at + 1
                        while (end < text.length && text[end] != '"') end += if (text[end] == Char(92)) 2 else 1
                        val next = (end + 1 until text.length).firstOrNull { !text[it].isWhitespace() }
                        val inner = open.lastOrNull()
                        if (inner != null && inner.isObject && next != null && text[next] == ':') {
                            val key = Json.parseToJsonElement(text.substring(at, end + 1)).jsonPrimitive.content
                            inner.key = key
                            inner.keys.getOrPut(key) { mutableListOf() } += at
                        }
                        at = end
                    }
                }
                at++
            }
            return repeats
        }

        /** The value written after the key whose opening quote is at [at], as written: a number or a word, never an object or a list. */
        fun valueAt(text: String, at: Int): String {
            var end = at + 1
            while (text[end] != '"') end += if (text[end] == Char(92)) 2 else 1
            val colon = text.indexOf(':', end)
            val stop = (colon + 1 until text.length).first { text[it] == ',' || text[it] == '}' || text[it] == ']' }
            return text.substring(colon + 1, stop).trim()
        }

        /** [text] with the key whose opening quote is at [at] renamed to one no build reads (`~` before it). */
        fun renamedAt(text: String, at: Int): String = text.substring(0, at + 1) + "~" + text.substring(at + 1)

        /** [this] without the keys [renamedAt] made. */
        fun JsonElement.withoutRenamed(): JsonElement = when (this) {
            is JsonObject -> JsonObject(filterKeys { !it.startsWith("~") }.mapValues { it.value.withoutRenamed() })
            is JsonArray -> JsonArray(map { it.withoutRenamed() })
            else -> this
        }

        // ---- The model's paths

        /**
         * Where the model's integer fields (Kotlin's `Int` and `Long`) sit, from the root: an
         * object's key after a dot, and `[]` for any item of a list. Nowhere else is an integer
         * read, so nowhere else is its form checked.
         */
        val INTEGER_PATHS = setOf(
            "version",
            "writtenAt",
            "days[].times.fajr",
            "days[].times.sunrise",
            "days[].times.dhuhr",
            "days[].times.asr",
            "days[].times.maghrib",
            "days[].times.isha",
            "days[].times.fajrEnd",
            "days[].times.asrEnd",
            "days[].midnight",
            "ui.spoken.hours[].upTo",
            "ui.spoken.minutes[].upTo",
            "ui.screens.turnRight[].upTo",
            "ui.screens.turnLeft[].upTo",
            "tasbeeh[].target",
        )

        private fun child(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

        /** The model's own integer paths, read from its serializers' descriptors. */
        @OptIn(ExperimentalSerializationApi::class)
        fun integerPaths(descriptor: SerialDescriptor, path: String = ""): Set<String> =
            (0 until descriptor.elementsCount).flatMap { index ->
                val at = child(path, descriptor.getElementName(index))
                val element = descriptor.getElementDescriptor(index)
                when (element.kind) {
                    PrimitiveKind.INT, PrimitiveKind.LONG -> listOf(at)
                    StructureKind.CLASS -> integerPaths(element, at)
                    StructureKind.LIST -> integerPaths(element.getElementDescriptor(0), "$at[]")
                    else -> emptyList()
                }
            }.toSet()

        /** The keys of each object the model declares, by its path. */
        @OptIn(ExperimentalSerializationApi::class)
        fun objectKeys(descriptor: SerialDescriptor, path: String = ""): Map<String, Set<String>> =
            (0 until descriptor.elementsCount).fold(mapOf(path to descriptor.elementNames.toSet())) { keys, index ->
                val at = child(path, descriptor.getElementName(index))
                val element = descriptor.getElementDescriptor(index)
                keys + when (element.kind) {
                    StructureKind.CLASS -> objectKeys(element, at)
                    StructureKind.LIST -> element.getElementDescriptor(0).let { item ->
                        if (item.kind == StructureKind.CLASS) objectKeys(item, "$at[]") else emptyMap()
                    }
                    else -> emptyMap()
                }
            }

        val OBJECT_KEYS: Map<String, Set<String>> by lazy { objectKeys(WatchFeed.serializer().descriptor) }

        /**
         * The class of each object the model declares, by its path as [objectKeys] gives it: its
         * serial name, without a nullable's `?`, so `days[].clock`, `days[].names` and
         * `days[].clockTime` are all `WatchSix`. A map, such as the screens' presets, is no class
         * here: each of its keys is an id the phone chose, not a field.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun objectTypes(descriptor: SerialDescriptor, path: String = ""): Map<String, String> =
            (0 until descriptor.elementsCount).fold(mapOf(path to descriptor.serialName.removeSuffix("?"))) { types, index ->
                val at = child(path, descriptor.getElementName(index))
                val element = descriptor.getElementDescriptor(index)
                types + when (element.kind) {
                    StructureKind.CLASS -> objectTypes(element, at)
                    StructureKind.LIST -> element.getElementDescriptor(0).let { item ->
                        if (item.kind == StructureKind.CLASS) objectTypes(item, "$at[]") else emptyMap()
                    }
                    else -> emptyMap()
                }
            }

        val OBJECT_TYPES: Map<String, String> by lazy { objectTypes(WatchFeed.serializer().descriptor) }

        /**
         * Where the model's fields that kotlinx reads leniently sit, from the root, in declaration
         * order, with their kind: an Int, a Long, a Double or a Boolean, which kotlinx also reads
         * from a string holding the value, and a Char, which it also reads from a bare token.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun lenientPaths(descriptor: SerialDescriptor, path: String = ""): Map<String, PrimitiveKind> =
            (0 until descriptor.elementsCount).fold(linkedMapOf()) { paths, index ->
                val at = child(path, descriptor.getElementName(index))
                val element = descriptor.getElementDescriptor(index)
                paths.apply {
                    when (val kind = element.kind) {
                        PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.DOUBLE, PrimitiveKind.BOOLEAN, PrimitiveKind.CHAR ->
                            put(at, kind as PrimitiveKind)
                        StructureKind.CLASS -> putAll(lenientPaths(element, at))
                        StructureKind.LIST -> element.getElementDescriptor(0).let { item ->
                            if (item.kind == StructureKind.CLASS) putAll(lenientPaths(item, "$at[]"))
                        }
                        else -> Unit
                    }
                }
            }

        /** The value at [path] in [this], a path as [changedPaths] writes it (`days[0].times.fajr`). */
        fun JsonElement.at(path: String): JsonPrimitive = path.split('.').fold(this) { element, segment ->
            val key = segment.substringBefore('[')
            val item = element.jsonObject.getValue(key)
            if ('[' in segment) item.jsonArray[segment.substringAfter('[').removeSuffix("]").toInt()] else item
        }.jsonPrimitive

        /**
         * A key named like one of [INTEGER_PATHS]' fields, at a path the model does not have, and
         * where it sits: before the model's own keys in its object, among them, after them, or in an
         * object the model does not have.
         */
        data class Stray(val path: String, val value: JsonElement, val where: Where) {
            enum class Where { BEFORE, AMONG, AFTER, IN_A_LATER_OBJECT }
        }

        /** Every [Stray] in [element], which sits at [path], for the field names [names]. */
        fun strays(element: JsonElement, path: String, names: Set<String>): List<Stray> = when (element) {
            is JsonObject -> {
                val own = OBJECT_KEYS[path].orEmpty()
                val known = element.keys.withIndex().filter { it.value in own }.map { it.index }
                element.entries.withIndex().flatMap { (index, entry) ->
                    val at = child(path, entry.key)
                    val where = when {
                        known.isEmpty() -> Stray.Where.IN_A_LATER_OBJECT
                        index < known.first() -> Stray.Where.BEFORE
                        index > known.last() -> Stray.Where.AFTER
                        else -> Stray.Where.AMONG
                    }
                    val stray = entry.key in names && entry.key !in own
                    (if (stray) listOf(Stray(at, entry.value, where)) else emptyList()) + strays(entry.value, at, names)
                }
            }
            is JsonArray -> element.flatMap { strays(it, "$path[]", names) }
            else -> emptyList()
        }

        /** What a watch makes of [json]: `FeedStore`'s reading of the decoder's two answers. */
        fun kotlinAnswer(json: String): String = when {
            WatchFeedJson.decode(json) != null -> "feed"
            (WatchFeedJson.versionOf(json) ?: 0) > WatchFeedJson.VERSION -> "newer"
            else -> "malformed"
        }

        /** The four samples' files, by name, as `VectorsTest` writes them. */
        val sampleFiles: Map<String, String> by lazy {
            linkedMapOf("sample-feed-capetown.json" to WatchFeedJson.encode(Fixtures.capeTownSample()) + "\n").apply {
                Fixtures.capeTownSamples().forEach { (name, feed) -> put("sample-feed-capetown-$name.json", WatchFeedJson.encode(feed) + "\n") }
            }
        }

        /** The file's text, exactly. */
        val vectorsText: String by lazy { feedVectors() }

        val vectors: JsonObject by lazy { Json.parseToJsonElement(vectorsText).jsonObject }

        // ---- Writing the vectors

        /**
         * A UTF-8 byte-order mark. A case that opens with one says `bom` and keeps it out of `json`:
         * Foundation's JSONSerialization drops a U+FEFF that opens a string value, written raw or
         * escaped (JSONDecoder keeps it), so a Swift reader of the file could not otherwise see it.
         */
        val BOM = Char(0xFEFF).toString()

        /**
         * One decode case: its text, inline or the file of that name beside this one, after a
         * byte-order mark when [bom], the answer it is declared to give, which
         * [decodeCasesAreWhatTheDecoderSays] holds to the decoder's, and the class it is declared to
         * have, which [everyCaseHasTheClassItsRuleGives] holds to the rule's. [swift] is the port's
         * answer where it differs.
         */
        class Decode(
            val name: String,
            val result: String,
            val caseClass: String,
            val json: String? = null,
            val file: String? = null,
            val swift: String? = null,
            val bom: Boolean = false,
        ) {
            val text: String get() = (if (bom) BOM else "") + (json ?: sampleFiles.getValue(checkNotNull(file)))
        }

        fun feedVectors(): String {
            val cases = declaredDecodes()
            val feeds = cases.associate { it.name to it }
            fun feed(name: String): WatchFeed = checkNotNull(WatchFeedJson.decode(feeds.getValue(name).text)) { "$name is no feed" }
            // A case of another section carries the class of the feed it runs on.
            fun withClass(items: List<String>) = items.map { item ->
                val case = Json.parseToJsonElement(item).jsonObject
                JsonObject(case + ("class" to JsonPrimitive(feeds.getValue(case.getValue("feed").jsonPrimitive.content).caseClass))).toString()
            }

            return buildString {
                appendLine("{")
                appendLine("  \"about\": ${JsonPrimitive(ABOUT)},")
                append(sectionText("decode", cases.map(::decodeJson)))
                append(sectionText("spoken", withClass(spokenCases(::feed))))
                append(sectionText("labels", withClass(labelCases(::feed))))
                append(sectionText("days", withClass(dayCases(::feed))))
                append(sectionText("clock", withClass(clockCases(::feed))))
                append(sectionText("words", withClass(wordsCases(::feed))))
                append(sectionText("moments", withClass(momentCases(::feed))))
                append(sectionText("starts", withClass(startsCases(::feed))))
                append(sectionText("filled", filledCases()))
                append(sectionText("inDigits", inDigitsCases()))
                appendLine("  \"contract\": {")
                append(contract())
                appendLine("  }")
                appendLine("}")
            }
        }

        /**
         * The texts, each the English sample (`sample-feed-capetown.json`) with one change, but for
         * the other samples, the feeds the later sections run on, `newerShape` (`WatchFeedJsonTest`'s
         * own newer text), and the newer shape, the missing version, the number and text forms, a
         * later phone's keys and a key written twice on `oneDay`: the sample cut to its first day, a
         * 3 KB feed, so that each of those is `oneDay` with one change (`newerDayShape` with two, its
         * version and its times; `escapedKeyFractionalWrittenAt` with two, a fraction and its key's
         * spelling; a [LATER_KEYS] case with its later keys). The sample is re-written from its parsed
         * form byte for byte (checked below), so a change is the only difference.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun declaredDecodes(): List<Decode> {
            val sampleFeed = Fixtures.capeTownSample()
            val sampleText = WatchFeedJson.encode(sampleFeed)
            val sample = Json.parseToJsonElement(sampleText).jsonObject
            check(sample.toString() == sampleText) { "the sample does not re-write as it was written" }
            val days = sample.getValue("days").jsonArray
            val ui = sample.getValue("ui").jsonObject
            val place = sample.getValue("place").jsonObject
            val future = JsonPrimitive(1)

            // ENCODER texts are written by kotlinx's encoder itself (Ruling 56 item 2): a value of the
            // model by WatchFeedJson.encode, and an older or a later phone's value by the same encoder
            // over its JSON tree, which writes each number, string and word as a model's field of that
            // type is written.
            fun encoded(name: String, result: String, feed: WatchFeed) = Decode(name, result, ENCODER, json = WatchFeedJson.encode(feed))

            fun encoded(name: String, result: String, tree: JsonObject) =
                Decode(name, result, ENCODER, json = PHONE_JSON.encodeToString(JsonElement.serializer(), tree))

            // ONE_CHANGE and FOREIGN texts: an ENCODER case's tree with its change, as written.
            fun inline(name: String, result: String, caseClass: String, json: JsonObject, swift: String? = null) =
                Decode(name, result, caseClass, json = json.toString(), swift = swift)

            // Older than every defaulted field the plans name: the days' Hijri day and month, spoken
            // date and midnight, the spoken countdown, his phrases and the digits (plan 1),
            // plan 3's screens' words, ring zero, script and 12-hour times (plan 4 Task 3), the
            // phone's Show sunrise (the owner's decision of 9 November 2026), which a 1.6.0 (43)
            // phone never wrote, and the Tile's compact countdown (Mohamed's report of the same
            // day), which a 1.6.0 (44) phone never wrote.
            val olderPhone = sample.without("tasbeeh")
                .with("days", JsonArray(days.map { it.jsonObject.without("hijriDayMonth", "gregorianSpoken", "midnight", "clockTime") }))
                .with(
                    "ui",
                    ui.without("spoken", "digitZero", "screens", "ringDigitZero", "latinScript", "showSunrise")
                        .with("countdown", ui.getValue("countdown").jsonObject.without(*COMPACT_COUNTDOWN.toTypedArray())),
                )

            // A key no build reads, in every kind of object a later phone might add one to, each of
            // the model's classes once (aLaterPhonesKeyIsIgnoredInEveryObject): the countdown
            // phrases, the screens' words, one of their counts and a day's 12-hour times among them
            // (Ruling 60 item 1d). Only a 12-hour phone's days carry a clockTime, so the later phone
            // is the twelve-hour English sample with the first sample's phrases.
            val later = Json.parseToJsonElement(
                WatchFeedJson.encode(Fixtures.capeTownSamples().getValue("en12").copy(tasbeeh = sampleFeed.tasbeeh)),
            ).jsonObject
            val laterUi = later.getValue("ui").jsonObject
            val laterScreens = laterUi.getValue("screens").jsonObject
            fun JsonObject.withFuture() = this.with("future", future)
            fun JsonElement.withFutureInTheFirst(): JsonArray = JsonArray(jsonArray.mapIndexed { i, item -> if (i == 0) item.jsonObject.withFuture() else item })
            val newerPhone = later
                .with("place", later.getValue("place").jsonObject.withFuture())
                .with(
                    "ui",
                    laterUi
                        .with("countdown", laterUi.getValue("countdown").jsonObject.withFuture())
                        .with("spoken", laterUi.getValue("spoken").jsonObject.withFuture())
                        .with("screens", laterScreens.with("turnRight", laterScreens.getValue("turnRight").withFutureInTheFirst()).withFuture())
                        .with("face", laterUi.getValue("face").jsonObject.withFuture())
                        .withFuture(),
                )
                .with(
                    "days",
                    JsonArray(
                        later.getValue("days").jsonArray.mapIndexed { index, day ->
                            if (index != 0) day else day.jsonObject
                                .with("times", day.jsonObject.getValue("times").jsonObject.withFuture())
                                .with("clockTime", day.jsonObject.getValue("clockTime").jsonObject.withFuture())
                                .withFuture()
                        },
                    ),
                )
                .with("tasbeeh", later.getValue("tasbeeh").withFutureInTheFirst())
                .withFuture()

            val day0 = days[0].jsonObject
            val nullInADefaultedField = sample.with("days", JsonArray(listOf(day0.with("hijriDayMonth", JsonNull)) + days.drop(1)))

            // The sample cut to its first day: the base of the forms below, each one change to it.
            val oneDay = sample.with("days", JsonArray(listOf(day0)))
            val oneDayFeed = sampleFeed.copy(days = sampleFeed.days.take(1))
            check(oneDay.toString() == WatchFeedJson.encode(oneDayFeed)) { "oneDay is not the encoder's text of the sample's first day" }
            val oneDayTimes = day0.getValue("times").jsonObject
            val oneDayPhrases = sample.getValue("tasbeeh").jsonArray
            fun oneDayWith(key: String, value: JsonElement) = oneDay.with(key, value)
            fun oneDayWithTimes(times: JsonElement) = oneDay.with("days", JsonArray(listOf(day0.with("times", times))))
            fun oneDayWithUi(key: String, value: JsonElement) = oneDay.with("ui", ui.with(key, value))
            fun oneDayWithTarget(value: JsonElement) = oneDay.with(
                "tasbeeh",
                JsonArray(oneDayPhrases.mapIndexed { i, p -> if (i == 0) p.jsonObject.with("target", value) else p }),
            )
            // A number as it is written, so that 1.0 stays 1.0 and 1e0 stays 1e0.
            fun written(text: String) = JsonUnquotedLiteral(text)
            val writtenAt = sample.getValue("writtenAt").jsonPrimitive.content
            val writtenAtZeros = writtenAt.length - writtenAt.trimEnd('0').length
            check(writtenAtZeros > 0) { "writtenAt $writtenAt has no trailing zero to write as an exponent" }
            val asr = oneDayTimes.getValue("asr").jsonPrimitive.content
            val target = oneDayPhrases[0].jsonObject.getValue("target").jsonPrimitive.content
            // Digit zeros of one grapheme, which Swift's String.count reads as one Character, in two
            // UTF-16 units, which a Kotlin Char cannot hold: a zero with a combining acute, the
            // mathematical bold zero U+1D7CE, and a CR LF line break.
            val combiningZero = "0" + Char(0x0301)
            val astralZero = String(Character.toChars(0x1D7CE))
            val lineBreak = Char(13).toString() + Char(10)
            check(combiningZero.length == 2 && astralZero.length == 2 && lineBreak.length == 2)

            // A later phone's keys at version 1, named like the integer fields at paths the model does
            // not have: a fraction, a string and a version below the root, before an object's own keys
            // and after them (aLaterPhonesKeysNamedLikeTheIntegersAreIgnored). Each is oneDay's feed.
            fun JsonObject.between(before: Map<String, JsonElement>, after: Map<String, JsonElement>): JsonObject {
                check(keys.none { it in before || it in after }) { "a later key shares a name with one of its object's own" }
                return JsonObject(before + this + after)
            }
            fun later(vararg entries: Pair<String, Any>): Map<String, JsonElement> = linkedMapOf(
                *entries.map { (key, value) ->
                    key to when (value) {
                        is JsonElement -> value
                        is String -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value)
                        else -> error("$key: $value")
                    }
                }.toTypedArray(),
            )
            fun laterObject(vararg entries: Pair<String, Any>) = JsonObject(later(*entries))
            val laterKeysAtTheTop = oneDay.between(
                before = later("upTo" to 0.5, "earlier" to laterObject("version" to 2, "writtenAt" to "soon")),
                after = later("target" to "all", "future" to laterObject("version" to 2.5, "writtenAt" to 1.5, "target" to "x", "asr" to 1.5)),
            )
            val laterKeysInADay = oneDayWithTimes(
                oneDayTimes.between(
                    before = later("midnight" to 0.5),
                    after = later("upTo" to "x", "next" to laterObject("version" to 2)),
                ),
            ).let { feed ->
                val day = feed.getValue("days").jsonArray[0].jsonObject
                feed.with(
                    "days",
                    JsonArray(
                        listOf(
                            day.between(
                                before = later("version" to 1.5, "writtenAt" to "yesterday"),
                                after = later("angles" to laterObject("fajr" to 18.5, "isha" to 17.5), "target" to "x"),
                            ),
                        ),
                    ),
                )
            }
            val spoken = ui.getValue("spoken").jsonObject
            val screens = ui.getValue("screens").jsonObject
            fun JsonObject.withFirst(key: String, change: (JsonObject) -> JsonObject) =
                this.with(key, JsonArray(getValue(key).jsonArray.mapIndexed { i, item -> if (i == 0) change(item.jsonObject) else item }))
            val laterKeysInThePlaceUiAndPhrases = oneDay
                .with(
                    "place",
                    place.between(
                        before = later("target" to 0.5),
                        after = later("midnight" to "x", "later" to laterObject("version" to 2.5)),
                    ),
                )
                .with(
                    "ui",
                    ui
                        .with(
                            "spoken",
                            spoken
                                .withFirst("hours") { it.between(before = later("target" to 1.5), after = later("isha" to "x")) }
                                .between(before = later("upTo" to 1.5), after = later("fajr" to "x")),
                        )
                        .with("screens", screens.withFirst("turnRight") { it.between(before = later("target" to 2.5), after = emptyMap()) })
                        .between(before = later("version" to 2), after = later("writtenAt" to "x")),
                )
                .withFirst("tasbeeh") { it.between(before = later("upTo" to 0.5), after = later("version" to "x", "writtenAt" to 2.5)) }

            // Texts no JsonObject can hold, written on oneDay's own text (review round 3): a trailing
            // comma, a key spelled with an escape, and a key written twice.
            val oneDayText = oneDay.toString()
            val placeText = place.toString()
            val phrasesText = oneDayPhrases.toString()
            fun String.once(old: String, new: String): String {
                val at = indexOf(old)
                check(at >= 0 && indexOf(old, at + 1) < 0) { "$old is not in the text exactly once" }
                return replaceRange(at, at + old.length, new)
            }
            // A key with its first letter written as a Unicode escape (v as U+0076), which both
            // decoders read as the key itself. Char(92) is the backslash.
            fun escaped(key: String) = "\"" + Char(92) + "u" + key[0].code.toString(16).padStart(4, '0') + key.drop(1) + "\""
            fun member(key: String, value: JsonElement) = "${JsonPrimitive(key)}:$value"
            // The object's text with a member written before its own keys, or after them.
            fun String.withFirst(member: String) = "{$member," + drop(1)
            fun String.withLast(member: String) = dropLast(1) + ",$member}"
            val fractionalWrittenAt = oneDayWith("writtenAt", written("$writtenAt.0"))

            // The labels' copies: a Turkish phone whose Asr is "ikindi", and the same names on English.
            val ikindi = Fixtures.capeTownSample().let { feed ->
                feed.copy(days = feed.days.map { it.copy(names = it.names.copy(asr = "ikindi")) })
            }
            // The days' fallback: no midnight anywhere, so every date turns over at solar midnight,
            // with day 1's times ten minutes late (as watch-rules-vectors.json's capetown-no-midnights
            // has them), so that a day's start is halfway between two Dhuhrs that differ.
            val noMidnights = Fixtures.withoutMidnights(
                Fixtures.capeTownSample().let { feed ->
                    feed.copy(days = feed.days.mapIndexed { index, day -> if (index == 1) Fixtures.shifted(day, 600) else day })
                },
            )

            // Ruling 57 item 2: every other field the port reads that kotlinx reads leniently, on
            // oneDay, its own value written in that wrong type: a number or a boolean as a string,
            // a Char as a bare digit. The port refuses each (a safe difference: no phone writes one).
            // Plan 3's fields among them since the port reads them (plan 4 Task 3): oneDay's ring
            // zero is null, so its case writes the bare 0 an ar-SA phone's ring would quote.
            val lenientFields = lenientPaths(WatchFeed.serializer().descriptor).filterKeys { it !in QUOTED_PATHS }
            val lenient = lenientFields.map { (path, kind) ->
                val last = path.substringAfterLast('.')
                val repeated = lenientFields.keys.count { it.substringAfterLast('.') == last } > 1
                val parent = path.substringBeforeLast('.').substringAfterLast('.').removeSuffix("[]")
                val name = (if (kind == PrimitiveKind.CHAR) "bare" else "quoted") +
                    (if (repeated) parent.replaceFirstChar { it.uppercase() } else "") + last.replaceFirstChar { it.uppercase() }
                val json = oneDay.withFirstAt(path) { value ->
                    val content = if (value is JsonNull) "0" else value.jsonPrimitive.content
                    if (kind == PrimitiveKind.CHAR) JsonUnquotedLiteral(content) else JsonPrimitive(content)
                }
                inline(name, "feed", ONE_CHANGE, json.jsonObject, swift = "malformed")
            }

            // Ruling 57 item 4: plan 3's keys written as kotlinx refuses them, each oneDay with one
            // change: a null for the words and the script, which have defaults that are not null, a
            // ring zero of two characters, and a day's 12-hour times as a number.
            val plan3Refused = listOf(
                inline("nullScreens", "malformed", ONE_CHANGE, oneDayWithUi("screens", JsonNull)),
                inline("nullLatinScript", "malformed", ONE_CHANGE, oneDayWithUi("latinScript", JsonNull)),
                inline("twoCharacterRingZero", "malformed", ONE_CHANGE, oneDayWithUi("ringDigitZero", JsonPrimitive("01"))),
                inline("numberClockTime", "malformed", ONE_CHANGE, oneDay.with("days", JsonArray(listOf(day0.with("clockTime", JsonPrimitive(5)))))),
            )

            // The phone's Show sunrise (the owner's decision of 9 November 2026): oneDay with it
            // off, as a phone sends it by default, and with it null, which kotlinx refuses as it
            // refuses a null at every key whose default is not null (theSunriseCasesAreReadAsKotlinReadsThem).
            val sunrise = listOf(
                encoded("sunriseHidden", "feed", oneDayFeed.copy(ui = oneDayFeed.ui.copy(showSunrise = false))),
                inline("nullShowSunrise", "malformed", ONE_CHANGE, oneDayWithUi("showSunrise", JsonNull)),
            )

            // The Tile's compact countdown (Mohamed's report of 9 November 2026): oneDay with
            // ui.countdown.compactHours null, which kotlinx refuses as it refuses a null at every key
            // whose default is not null (theCompactCountdownIsReadAsKotlinReadsIt).
            val compact = listOf(
                inline("nullCompactHours", "malformed", ONE_CHANGE, oneDayWithUi("countdown", ui.getValue("countdown").jsonObject.with("compactHours", JsonNull))),
            )

            // Plan 4 Task 3: oneDay with words that are blank by Kotlin's isBlank and words that
            // are not (theWordsCasesAreEachFeedsWordsWithTheEnglish), so the port's orEnglish is
            // held to Kotlin's on each; and the sample's first three days with clock strings and
            // 12-hour times in forms the samples lack (theClockCasesHoldTheIsolateRuleInEveryDigitSet),
            // day 2's in numbers that are no decimal digit (Ruling 60 item 1a).
            val blankWords = oneDayFeed.copy(
                ui = oneDayFeed.ui.copy(
                    screens = WatchScreenWords.ENGLISH.copy(
                        today = " ",
                        tasbeeh = Char(0x00A0).toString(),
                        qibla = Char(0x0085).toString(),
                        appName = "Taqwa ",
                        openOnPhone = Char(0x200B).toString(),
                        updateOnWatch = Char(0x001C).toString(),
                        rowCurrent = Char(0x2028).toString(),
                        rowPassed = Char(9).toString() + Char(10),
                        rowNext = "",
                        round = Char(0x001D).toString(),
                        of = Char(0x001E).toString(),
                        bezelHint = Char(0x001F).toString(),
                        crownHint = Char(0x3000).toString(),
                        // Out of the built-ins' order, with two ids they lack (Ruling 60 item 1e).
                        presets = linkedMapOf(
                            "custom_2" to "Yours",
                            "alhamdulillah" to "الحمد لله",
                            "subhanallah" to " ",
                            "custom_1" to "Mine",
                        ),
                        cardinals = emptyList(),
                        turnRight = emptyList(),
                        turnLeft = listOf(WatchCount(180, " ")),
                    ),
                ),
            )
            val clockForms = sampleFeed.copy(
                days = listOf(
                    sampleFeed.days[0].copy(
                        clock = WatchSix(
                            fajr = "০৪:৩৩",
                            sunrise = "۰۶:۰۱",
                            dhuhr = "१२:३३",
                            asr = MATH_BOLD_ASR,
                            maghrib = "19.04",
                            isha = "20 24",
                        ),
                        clockTime = null,
                    ),
                    sampleFeed.days[1].copy(
                        clockTime = WatchSix(
                            fajr = "ÖÖ 4:33",
                            sunrise = "6:01" + Char(0x202F) + "AM",
                            dhuhr = "12:33",
                            asr = "ÖS 4:10",
                            maghrib = "7:04" + Char(0x00A0) + "PM",
                            isha = "8:24",
                        ),
                    ),
                    // Ruling 60 item 1a: numbers that are no decimal digit, each isolated when spoken.
                    sampleFeed.days[2].copy(clock = NUMERALS, clockTime = null),
                ),
                tasbeeh = emptyList(),
            )

            // Ruling 57 item 5: the feeds of the rule cases no earlier vector held. The sample's first
            // two days, with day 0's Isha moved onto day 1's Fajr, or with day 1 beginning a minute
            // after day 0, and then also day 0's Isha a minute before its Fajr; and oneDay with an
            // empty list of spoken hours, or of minutes.
            val twoDays = sampleFeed.days.take(2)
            val ishaAtTheNextFajr = sampleFeed.copy(
                days = listOf(twoDays[0].copy(times = twoDays[0].times.copy(isha = twoDays[1].times.fajr)), twoDays[1]),
            )
            val aDayOfOneMinute = sampleFeed.copy(days = listOf(twoDays[0], twoDays[1].copy(midnight = twoDays[0].midnight + 60)))
            val ishaFirst = aDayOfOneMinute.days[0].let { it.copy(times = it.times.copy(isha = it.times.fajr - 60)) }
            val ishaFirstInADayOfOneMinute = aDayOfOneMinute.copy(days = listOf(ishaFirst, aDayOfOneMinute.days[1]))
            // After review: the same two days with every time at the end of time and day 1 beginning
            // near its start, so that once its date has turned yesterday's Isha, the previous, and
            // today's Fajr, the next, fall at one instant more than half of a Long ahead: a period of
            // no length whose time since its start wraps to a positive number.
            val theEndOfTime = Long.MAX_VALUE - 1_000
            fun atTheEndOfTime(day: WatchDay, midnight: Long) = day.copy(
                midnight = midnight,
                times = WatchTimes(
                    fajr = theEndOfTime,
                    sunrise = theEndOfTime,
                    dhuhr = theEndOfTime,
                    asr = theEndOfTime,
                    maghrib = theEndOfTime,
                    isha = theEndOfTime,
                    fajrEnd = day.times.fajrEnd?.let { theEndOfTime },
                    asrEnd = day.times.asrEnd?.let { theEndOfTime },
                ),
            )
            val theStartOfTime = Long.MIN_VALUE + 100_000
            val startsAtTheEndOfTime = sampleFeed.copy(
                days = listOf(atTheEndOfTime(twoDays[0], theStartOfTime - 86_400), atTheEndOfTime(twoDays[1], theStartOfTime)),
            )
            val spokenWords = checkNotNull(oneDayFeed.ui.spoken)
            fun oneDayWithSpoken(words: WatchSpoken) = oneDayFeed.copy(ui = oneDayFeed.ui.copy(spoken = words))

            // Ruling 57 item 6: oneDay with its Asr named otherwise, under language tags that
            // Foundation and Java read otherwise: an extension, an ill-formed tag, a language in
            // capitals, Lithuanian's dot above and Greek's tonos.
            fun asrNamed(name: String, language: String) = oneDayFeed.copy(
                days = oneDayFeed.days.map { it.copy(names = it.names.copy(asr = name)) },
                ui = oneDayFeed.ui.copy(language = language),
            )

            return listOf(
                // ENCODER: the samples, WatchFeedJson.encode's own files (VectorsTest writes them),
                Decode("sample", "feed", ENCODER, file = "sample-feed-capetown.json"),
                Decode("sampleEn12", "feed", ENCODER, file = "sample-feed-capetown-en12.json"),
                Decode("sampleArLy12", "feed", ENCODER, file = "sample-feed-capetown-ar-ly12.json"),
                Decode("sampleArSa", "feed", ENCODER, file = "sample-feed-capetown-ar-sa.json"),
                // an older phone's and a later phone's at version 1, and a later phone's at version 2,
                encoded("olderPhone", "feed", olderPhone),
                encoded("newerPhone", "feed", newerPhone),
                encoded("version2", "newer", sampleFeed.copy(version = 2)),
                // and newer feeds this build cannot read the body of: the version is all that tells the
                // watch to say "Update Taqwa on your watch" (aNewerFeedOfAnotherShapeIsNewer).
                encoded("newerShape", "newer", buildJsonObject { put("version", 2); put("layout", "something else") }),
                encoded(
                    "newerDayShape",
                    "newer",
                    oneDayWithTimes(JsonArray(WatchTime.entries.map { oneDayTimes.getValue(it.name.lowercase()) }))
                        .with("version", JsonPrimitive(2)),
                ),
                // ONE_CHANGE: an ENCODER case with one value changed, removed or set to null,
                inline("version0", "malformed", ONE_CHANGE, sample.with("version", JsonPrimitive(0))),
                inline("missingVersion", "malformed", ONE_CHANGE, oneDay.without("version")),
                // FOREIGN: a cut text,
                Decode("notJson", "malformed", FOREIGN, json = "{"),
                inline("noDays", "malformed", ONE_CHANGE, sample.without("days")),
                inline("nullInADefaultedField", "malformed", ONE_CHANGE, nullInADefaultedField),
                inline("twoCharacterZero", "malformed", ONE_CHANGE, sample.with("ui", ui.with("digitZero", JsonPrimitive("01")))),
                encoded("emptyDays", "feed", sampleFeed.copy(days = emptyList())),
                // the quoted four, ONE_CHANGE, which kotlinx reads and the port refuses (call 8),
                inline("quotedWrittenAt", "feed", ONE_CHANGE, sample.with("writtenAt", JsonPrimitive(sample.getValue("writtenAt").jsonPrimitive.content)), swift = "malformed"),
                inline("quotedVersion", "feed", ONE_CHANGE, sample.with("version", JsonPrimitive("1")), swift = "malformed"),
                inline("quotedBearing", "feed", ONE_CHANGE, sample.with("place", place.with("qiblaBearing", JsonPrimitive(place.getValue("qiblaBearing").jsonPrimitive.content))), swift = "malformed"),
                inline("quotedRtl", "feed", ONE_CHANGE, sample.with("ui", ui.with("rtl", JsonPrimitive(ui.getValue("rtl").jsonPrimitive.content))), swift = "malformed"),
            ) + lenient + plan3Refused + sunrise + compact + listOf(
                // The forms on the one-day sample, each one change to it
                // (theFormsOnOneDayAreOneChangeEach): the base, then the forms kotlinx refuses,
                encoded("oneDay", "feed", oneDayFeed),
                inline("fractionalVersion", "malformed", FOREIGN, oneDayWith("version", written("1.0"))),
                inline("fractionalNewerVersion", "malformed", FOREIGN, oneDayWith("version", written("2.0"))),
                inline("booleanVersion", "malformed", ONE_CHANGE, oneDayWith("version", JsonPrimitive(true))),
                inline("wideVersion", "malformed", ONE_CHANGE, oneDayWith("version", JsonPrimitive(4_294_967_297L))),
                inline("fractionalWrittenAt", "malformed", FOREIGN, fractionalWrittenAt),
                inline("fractionalTime", "malformed", FOREIGN, oneDayWithTimes(oneDayTimes.with("asr", written("$asr.0")))),
                inline("fractionalTarget", "malformed", FOREIGN, oneDayWithTarget(written("$target.0"))),
                inline("wideTarget", "malformed", ONE_CHANGE, oneDayWithTarget(JsonPrimitive(3_000_000_000L))),
                Decode("byteOrderMark", "malformed", FOREIGN, json = oneDay.toString(), bom = true),
                inline("combiningDigitZero", "malformed", ONE_CHANGE, oneDayWithUi("digitZero", JsonPrimitive(combiningZero))),
                inline("astralDigitZero", "malformed", ONE_CHANGE, oneDayWithUi("digitZero", JsonPrimitive(astralZero))),
                inline("lineBreakDigitZero", "malformed", ONE_CHANGE, oneDayWithUi("digitZero", JsonPrimitive(lineBreak))),
                // a trailing comma, which Foundation's readers take (review round 3), at the top, in a
                // list and in an object,
                Decode("trailingCommaAtTheTop", "malformed", FOREIGN, json = oneDayText.dropLast(1) + ",}"),
                Decode("trailingCommaInAList", "malformed", FOREIGN, json = oneDayText.once(phrasesText, phrasesText.dropLast(1) + ",]")),
                Decode("trailingCommaInAnObject", "malformed", FOREIGN, json = oneDayText.once(placeText, placeText.dropLast(1) + ",}")),
                // and fractionalWrittenAt with its key spelled with an escape,
                Decode(
                    "escapedKeyFractionalWrittenAt",
                    "malformed",
                    FOREIGN,
                    json = fractionalWrittenAt.toString().once("\"writtenAt\":", escaped("writtenAt") + ":"),
                ),
                // and reads these, as Foundation's JSONDecoder does:
                inline("exponentVersion", "feed", FOREIGN, oneDayWith("version", written("1e0"))),
                inline("exponentWrittenAt", "feed", FOREIGN, oneDayWith("writtenAt", written("${writtenAt.trimEnd('0')}e$writtenAtZeros"))),
                Decode("escapedVersionKey", "feed", FOREIGN, json = oneDayText.once("{\"version\":", "{" + escaped("version") + ":")),
                // ENCODER: a later phone's keys at version 1, which every build ignores, whatever their names.
                encoded("laterKeysAtTheTop", "feed", laterKeysAtTheTop),
                encoded("laterKeysInADay", "feed", laterKeysInADay),
                encoded("laterKeysInThePlaceUiAndPhrases", "feed", laterKeysInThePlaceUiAndPhrases),
                // FOREIGN: a key written twice (review round 3), each copy read by kotlinx as its field
                // and the last kept, where Foundation's readers keep the first
                // (aKeyWrittenTwiceIsReadByItsLastCopy, Kotlin's answers as information).
                Decode("duplicateVersionNewerLast", "newer", FOREIGN, json = oneDayText.withLast(member("version", JsonPrimitive(2)))),
                Decode("duplicateVersionNewerFirst", "feed", FOREIGN, json = oneDayText.withFirst(member("version", JsonPrimitive(2)))),
                // a version below 1 before the version 1 kept: Kotlin holds only the kept copy to 1..VERSION,
                Decode("duplicateVersionZeroFirst", "feed", FOREIGN, json = oneDayText.withFirst(member("version", JsonPrimitive(0)))),
                // and a whole number with a fraction in the copy not kept, which JSONDecoder reads into
                // an Int as the kept copy's own number (review round 4), and kotlinx refuses:
                Decode("duplicateVersionWholeFractionFirst", "malformed", FOREIGN, json = oneDayText.withFirst(member("version", written("1.0")))),
                Decode("duplicateWrittenAtFractionLast", "malformed", FOREIGN, json = oneDayText.withLast(member("writtenAt", written("1.5")))),
                Decode(
                    "duplicateWrittenAtWholeFractionFirst",
                    "malformed",
                    FOREIGN,
                    json = oneDayText.withFirst(member("writtenAt", written("$writtenAt.0"))),
                ),
                Decode(
                    "duplicateMidnightInADay",
                    "feed",
                    FOREIGN,
                    json = oneDayText.once(day0.toString(), day0.toString().withFirst(member("midnight", JsonPrimitive(1)))),
                ),
                Decode("duplicatePhrasesLast", "feed", FOREIGN, json = oneDayText.withLast(member("tasbeeh", JsonArray(listOf(oneDayPhrases[0]))))),
                Decode(
                    "duplicateCityNotAString",
                    "malformed",
                    FOREIGN,
                    json = oneDayText.once(placeText, placeText.withFirst(member("city", JsonPrimitive(5)))),
                ),
                // ENCODER: the feeds the later sections run on.
                encoded("turkishIkindi", "feed", ikindi.copy(ui = ikindi.ui.copy(language = "tr"))),
                encoded("englishIkindi", "feed", ikindi),
                encoded("noMidnights", "feed", noMidnights),
                encoded("tromsoMay", "feed", Fixtures.tromsoMay()),
                // ENCODER: the feeds of Ruling 57 item 5's rule cases,
                encoded("ishaAtTheNextFajr", "feed", ishaAtTheNextFajr),
                encoded("aDayOfOneMinute", "feed", aDayOfOneMinute),
                encoded("ishaFirstInADayOfOneMinute", "feed", ishaFirstInADayOfOneMinute),
                encoded("startsAtTheEndOfTime", "feed", startsAtTheEndOfTime),
                encoded("emptySpokenHours", "feed", oneDayWithSpoken(spokenWords.copy(hours = emptyList()))),
                encoded("emptySpokenMinutes", "feed", oneDayWithSpoken(spokenWords.copy(minutes = emptyList()))),
                // and of item 6's labels.
                encoded("turkishIkindiTagged", "feed", asrNamed("ikindi", "tr-u-ca-gregory")),
                encoded("turkishIkindiIllFormed", "feed", asrNamed("ikindi", "tr_TR")),
                encoded("azerbaijaniIkindiInCapitals", "feed", asrNamed("ikindi", "AZ")),
                encoded("lithuanianDotAbove", "feed", asrNamed("i" + Char(0x0307) + "kindi", "lt")),
                encoded("greekTonos", "feed", asrNamed("άσρ", "el")),
                // ENCODER: plan 4 Task 3's words and clock forms.
                encoded("blankWords", "feed", blankWords),
                encoded("clockForms", "feed", clockForms),
            )
        }

        /** A decode case on one line: `expect` is what Kotlin decodes from the text, for a feed. */
        fun decodeJson(case: Decode): String = buildJsonObject {
            put("name", case.name)
            put("class", case.caseClass)
            put("result", case.result)
            case.swift?.let { put("swift", it) }
            WatchFeedJson.decode(case.text)?.let { feed ->
                putJsonObject("expect") {
                    val day0 = feed.days.firstOrNull()
                    put("days", feed.days.size)
                    put("day0HijriDayMonth", day0?.hijriDayMonth)
                    put("day0GregorianSpoken", day0?.gregorianSpoken)
                    put("day0Midnight", day0?.midnight)
                    put("digitZero", feed.ui.digitZero.toString())
                    put("ringDigitZero", feed.ui.ringDigitZero?.toString())
                    put("latinScript", feed.ui.latinScript)
                    put("showSunrise", feed.ui.showSunrise)
                    if (day0 == null) {
                        put("day0Asr", JsonNull)
                    } else {
                        putJsonObject("day0Asr") {
                            put("clockText", WatchRules.clockText(day0, WatchTime.ASR))
                            put("spokenClock", WatchRules.spokenClock(day0, WatchTime.ASR))
                        }
                    }
                    put("hasSpoken", feed.ui.spoken != null)
                    put("phrases", feed.tasbeeh.size)
                    put("city", feed.place.city)
                }
            }
            if (case.bom) put("bom", true)
            if (case.file != null) put("file", case.file) else put("json", case.json)
        }.toString()

        /** The samples, each by its decode case's name. */
        val SAMPLES = listOf("sample", "sampleEn12", "sampleArLy12", "sampleArSa")

        /** The feeds the labels run on beyond the samples, day 0 of each: item 6's after the two first. */
        val LABEL_FEEDS = listOf(
            "turkishIkindi",
            "englishIkindi",
            "turkishIkindiTagged",
            "turkishIkindiIllFormed",
            "azerbaijaniIkindiInCapitals",
            "lithuanianDotAbove",
            "greekTonos",
        )

        /** A start as the rules vectors write one. */
        fun startJson(start: WatchStart): JsonObject = buildJsonObject {
            put("prayer", start.prayer.name)
            put("at", start.at)
            put("dayIndex", start.dayIndex)
        }

        /**
         * Ruling 57 item 5: a rules case, in the shape of watch-rules-vectors.json's, at instants no
         * earlier case reached: on `ishaAtTheNextFajr` around day 1's Fajr, where day 0's Isha
         * falls too, and on `aDayOfOneMinute` before day 0's Fajr once day 1 has begun, where no start
         * has arrived and the previous is yesterday's Isha; on `ishaFirstInADayOfOneMinute` that Isha
         * is the next start too, a period of no length; and on `startsAtTheEndOfTime` yesterday's Isha
         * and today's Fajr fall at one instant more than half of a Long ahead, a period of no length
         * whose time since its start wraps.
         */
        fun momentCases(feed: (String) -> WatchFeed): List<String> {
            val tie = feed("ishaAtTheNextFajr").days[1].times.fajr
            val oneMinute = feed("aDayOfOneMinute").days[0]
            val endOfTime = feed("startsAtTheEndOfTime").days[1]
            return listOf(
                Triple("ishaAtTheNextFajr", "a second before day 1's Fajr, where day 0's Isha falls too", tie - 1),
                Triple("ishaAtTheNextFajr", "day 1's Fajr and day 0's Isha", tie),
                Triple("ishaAtTheNextFajr", "a second after them", tie + 1),
                Triple("aDayOfOneMinute", "day 1, a minute in, before every start of day 0", oneMinute.midnight + 120),
                Triple("aDayOfOneMinute", "a second before day 0's Fajr", oneMinute.times.fajr - 1),
                Triple("aDayOfOneMinute", "day 0's Fajr, the first start to arrive", oneMinute.times.fajr),
                Triple("ishaFirstInADayOfOneMinute", "day 1, a minute in, before day 0's Isha, the next start and the previous", oneMinute.midnight + 120),
                Triple(
                    "startsAtTheEndOfTime",
                    "day 1, ten seconds in, more than half of a Long before day 0's Isha and day 1's Fajr, the previous and the next",
                    endOfTime.midnight + 10,
                ),
            ).map { (name, what, now) ->
                val watchFeed = feed(name)
                val moment = WatchRules.moment(watchFeed, now)
                buildJsonObject {
                    put("name", what)
                    put("feed", name)
                    put("now", now)
                    put("next", moment?.next?.let(::startJson) ?: JsonNull)
                    put("previous", moment?.previous?.let(::startJson) ?: JsonNull)
                    put("secondsLeft", moment?.secondsLeft)
                    // The Float itself, widened to a Double, so that the port's Float is held exactly.
                    put("progress", moment?.progress?.toDouble())
                    put("countdown", moment?.let { WatchRules.countdown(watchFeed, it.secondsLeft) })
                    put("litOnList", WatchRules.litOnList(watchFeed, now)?.name)
                    put("litOnGlance", WatchRules.litOnGlance(watchFeed, now)?.name)
                }.toString()
            }
        }

        /**
         * [WatchRules.starts] whole: on `ishaAtTheNextFajr`, whose day 0 Isha falls exactly at day 1's
         * Fajr, and on `tromsoMay`, whose Isha falls after it (Ruling 57 item 5).
         */
        fun startsCases(feed: (String) -> WatchFeed): List<String> = listOf("ishaAtTheNextFajr", "tromsoMay").map { name ->
            buildJsonObject {
                put("feed", name)
                put("starts", JsonArray(WatchRules.starts(feed(name)).map(::startJson)))
            }.toString()
        }

        /**
         * [WatchRules.filled] on each kind of place (Ruling 57 item 5): in order and out of it, more
         * than once, a part holding a place, and the places that name no part, which stay as they
         * are: `%0$s`, one beyond the parts and one beyond an Int; then text that is no place.
         */
        fun filledCases(): List<String> = listOf(
            "%1\$s" to listOf("Asr"),
            "in %1\$s h %2\$s min" to listOf("1", "12"),
            "%2\$s-এর মধ্যে %1\$s" to listOf("৩৩", "১০০"),
            "%1\$s, again %1\$s" to listOf("Asr"),
            "said as typed: %1\$s" to listOf("%2\$s", "Asr"),
            "%0\$s" to listOf("Asr"),
            "%3\$s" to listOf("Asr", "16:10"),
            "%2147483648\$s" to listOf("Asr"),
            "%01\$s" to listOf("Asr"),
            "%%1\$s" to listOf("Asr"),
            "%1\$s" + Char(0x0301) to listOf("Asr"),
            "%1\$ %\$s %1s 100%" to listOf("Asr"),
            "no parts: %1\$s" to emptyList(),
        ).map { (pattern, parts) ->
            buildJsonObject {
                put("pattern", pattern)
                put("parts", JsonArray(parts.map(::JsonPrimitive)))
                put("filled", WatchRules.filled(pattern, *parts.toTypedArray()))
            }.toString()
        }

        /**
         * [WatchRules.inDigits] in each zero a phone sends, with text that is no Western digit kept,
         * and from U+FFFA, where the digit added to the zero passes the end of 16 bits and wraps to
         * U+0000, as Kotlin's `Char` arithmetic does (Ruling 57 item 5). No zero within nine of a
         * surrogate: Kotlin would write a lone surrogate, which no JSON reader reads.
         */
        fun inDigitsCases(): List<String> = listOf(
            "0123456789" to '0',
            "0123456789" to '٠',
            "1:11:36" to '০',
            "16:10, ١٢" to '۰',
            "0123456789" to Char(0xFFFA),
        ).map { (text, zero) ->
            buildJsonObject {
                put("text", text)
                put("zero", zero.toString())
                put("inDigits", WatchRules.inDigits(text, zero))
            }.toString()
        }

        /**
         * Each of the six times of days 0 and 6 as a surface draws it ([WatchRules.clockText]) and as a
         * spoken line splices it in ([WatchRules.spokenClock]), keyed by [WatchTime], Sunrise among
         * them; and of `clockForms`' days 0, 1 and 2, the clock strings and 12-hour times in the forms
         * the samples lack, day 2's in numbers that are no decimal digit (Ruling 60 item 1a).
         */
        fun clockCases(feed: (String) -> WatchFeed): List<String> = (SAMPLES + "clockForms").flatMap { name ->
            val watchFeed = feed(name)
            (if (name == "clockForms") listOf(0, 1, 2) else listOf(0, 6)).flatMap { index ->
                val day = watchFeed.days[index]
                WatchTime.entries.map { time ->
                    buildJsonObject {
                        put("feed", name)
                        put("day", index)
                        put("time", time.name)
                        put("clockText", WatchRules.clockText(day, time))
                        put("spokenClock", WatchRules.spokenClock(day, time))
                    }.toString()
                }
            }
        }

        /**
         * Each feed's words as its screens draw and say them ([WatchFeed.words]): the screens' words
         * with the English for every one left blank, written whole, defaults and all, on the four
         * samples, the older phone's (no words, so all English) and `blankWords`; with `presetIds`, the
         * presets' ids in Kotlin's order, which a dictionary does not keep (Ruling 60 item 1e).
         */
        fun wordsCases(feed: (String) -> WatchFeed): List<String> = (SAMPLES + "olderPhone" + "blankWords").map { name ->
            val words = feed(name).words()
            buildJsonObject {
                put("feed", name)
                put("words", PHONE_JSON.encodeToJsonElement(WatchScreenWords.serializer(), words))
                put("presetIds", JsonArray(words.presets.keys.map { JsonPrimitive(it) }))
            }.toString()
        }

        /**
         * Day 0's Asr, said with each time left: both sides of each minute, hour and plural that
         * changes the words; on the samples, an older phone's feed with no spoken words, and the two
         * with an empty list of them (Ruling 57 item 5).
         */
        fun spokenCases(feed: (String) -> WatchFeed): List<String> =
            (SAMPLES + "olderPhone" + "emptySpokenHours" + "emptySpokenMinutes").flatMap { name ->
            val watchFeed = feed(name)
            val day = watchFeed.days[0]
            val prayer = day.names.of(WatchTime.ASR)
            val clock = WatchRules.spokenClock(day, WatchTime.ASR)
            listOf(0L, 59L, 60L, 119L, 3_600L, 4_319L, 4_320L, 7_199L, 36_000L, 359_999L).map { seconds ->
                buildJsonObject {
                    put("feed", name)
                    put("name", prayer)
                    put("clock", clock)
                    put("secondsLeft", seconds)
                    put("spoken", WatchRules.spokenCountdown(watchFeed, prayer, clock, seconds))
                }.toString()
            }
        }

        /**
         * Each prayer's label: its name that day, read by its [WatchTime], in capitals by the feed's
         * language tag, exactly as `ComplicationFields.nextPrayer` writes it.
         */
        fun labelCases(feed: (String) -> WatchFeed): List<String> =
            (SAMPLES.flatMap { name -> listOf(0, 6).map { name to it } } + LABEL_FEEDS.map { it to 0 })
                .flatMap { (name, day) ->
                    val watchFeed = feed(name)
                    WatchPrayer.entries.map { prayer ->
                        buildJsonObject {
                            put("feed", name)
                            put("day", day)
                            put("prayer", prayer.name)
                            put(
                                "label",
                                watchFeed.days[day].names.of(WatchTime.of(prayer)).uppercase(Locale.forLanguageTag(watchFeed.ui.language)),
                            )
                        }.toString()
                    }
                }

        /**
         * Which day each instant is on, and where that day began, a second before, at and after the
         * turn of the date into each day named, and on both sides of the last day's end: a day after
         * its midnight, or half a day after its Dhuhr where it has none (`WatchRules.todayIndex`).
         */
        fun dayCases(feed: (String) -> WatchFeed): List<String> =
            listOf("sample" to listOf(0, 1, 30), "noMidnights" to listOf(0, 1, 30), "tromsoMay" to listOf(0, 1, 2))
                .flatMap { (name, indices) ->
                    val watchFeed = feed(name)
                    val last = watchFeed.days.last()
                    val end = if (last.midnight != 0L) last.midnight + 86_400L else last.times.dhuhr + 43_200L
                    val instants = indices.flatMap { index ->
                        val start = WatchRules.dayStart(watchFeed, index)
                        listOf(
                            "a second before day $index begins" to start - 1,
                            "day $index begins" to start,
                            "a second after day $index begins" to start + 1,
                        )
                    } + listOf("the last second of the last day" to end - 1, "the last day has ended" to end)
                    instants.map { (what, now) ->
                        val today = WatchRules.today(watchFeed, now)
                        buildJsonObject {
                            put("name", what)
                            put("feed", name)
                            put("now", now)
                            put("today", today)
                            put("dayStart", today?.let { WatchRules.dayStart(watchFeed, it) })
                        }.toString()
                    }
                }

        private const val ORIGIN = "0123456789abcdef"
        private const val FINGERPRINT = "fedcba9876543210"
        private const val OTHER_FEED = "00000000000000ff"
        private const val OTHER_ORIGIN = "1111111111111111"

        /** The link as both ends read it, and the receivers' rules (Ruling 50), each Kotlin's own answer. */
        fun contract(): String {
            val held = WatchDelivery(ORIGIN, 5, FINGERPRINT)
            val keys = buildJsonObject {
                put("CONTEXT_FEED_KEY", WatchLinkContract.CONTEXT_FEED_KEY)
                put("CONTEXT_DELIVERY_KEY", WatchLinkContract.CONTEXT_DELIVERY_KEY)
                put("REFRESH_MESSAGE_KEY", WatchLinkContract.REFRESH_MESSAGE_KEY)
                put("REFRESH_REPLY_KEY", WatchLinkContract.REFRESH_REPLY_KEY)
            }
            val bytes = buildJsonObject {
                put("FEED_BUDGET_BYTES", WatchLinkContract.FEED_BUDGET_BYTES)
                put("ENVELOPE_BYTES", WatchLinkContract.ENVELOPE_BYTES)
                put("USER_INFO_LIMIT_BYTES", WatchLinkContract.USER_INFO_LIMIT_BYTES)
                put("CONTEXT_LIMIT_BYTES", WatchLinkContract.CONTEXT_LIMIT_BYTES)
            }
            val fingerprints = listOf("0123456789abcdef", "0123456789ABCDEF", "0123456789abcde", "0123456789abcdef0", "", "0123456789abcdeg")
                .map { text ->
                    buildJsonObject {
                        put("text", text)
                        put("valid", WatchLinkContract.isFingerprint(text))
                    }.toString()
                }
            // The valid forms, then every refused form of WatchLinkContractTest.anythingElseIsNoDelivery.
            val deliveries = listOf(
                "number 1" to "$ORIGIN/1/$FINGERPRINT",
                "number 7" to "$ORIGIN/7/$FINGERPRINT",
                "the highest number, Long.MAX_VALUE (an Int64 in Swift)" to "$ORIGIN/${Long.MAX_VALUE}/$FINGERPRINT",
                "nothing, a watch that holds none" to "",
                "the fingerprint alone, as before Ruling 50" to FINGERPRINT,
                "no fingerprint" to "$ORIGIN/7",
                "number 0: numbers start at 1" to "$ORIGIN/0/$FINGERPRINT",
                "a negative number" to "$ORIGIN/-1/$FINGERPRINT",
                "a leading zero: one number has one form" to "$ORIGIN/07/$FINGERPRINT",
                "a plus sign" to "$ORIGIN/+7/$FINGERPRINT",
                "a space" to "$ORIGIN/ 7/$FINGERPRINT",
                "no number" to "$ORIGIN//$FINGERPRINT",
                "an extra segment" to "$ORIGIN/7/$FINGERPRINT/x",
                "a trailing slash" to "$ORIGIN/7/$FINGERPRINT/",
                "a leading slash" to "/$ORIGIN/7/$FINGERPRINT",
                "an origin in capitals" to "${ORIGIN.uppercase()}/7/$FINGERPRINT",
                "a fingerprint in capitals" to "$ORIGIN/7/${FINGERPRINT.uppercase()}",
                "one past Long.MAX_VALUE" to "$ORIGIN/9223372036854775808/$FINGERPRINT",
                "an overflow" to "$ORIGIN/99999999999999999999/$FINGERPRINT",
            ).map { (name, text) ->
                val delivery = WatchLinkContract.deliveryOf(text)
                buildJsonObject {
                    put("name", name)
                    put("text", text)
                    if (delivery != null) {
                        put("origin", delivery.origin)
                        put("seq", delivery.seq)
                        put("fingerprint", delivery.fingerprint)
                    }
                }.toString()
            }
            val stores = listOf(
                Triple("nothing held", null, held),
                Triple("a greater number", held, WatchDelivery(ORIGIN, 6, OTHER_FEED)),
                Triple("the same feed under a greater number", held, WatchDelivery(ORIGIN, 6, FINGERPRINT)),
                Triple("the same number, another feed: stale", held, WatchDelivery(ORIGIN, 5, OTHER_FEED)),
                Triple("the same number, the same feed: a duplicate", held, held),
                Triple("a lower number: late", held, WatchDelivery(ORIGIN, 4, OTHER_FEED)),
                Triple("another origin, with a lower number", held, WatchDelivery(OTHER_ORIGIN, 1, OTHER_FEED)),
                Triple("another origin, with the same number and feed", held, WatchDelivery(OTHER_ORIGIN, 5, FINGERPRINT)),
                Triple(
                    "the highest number after the one below it",
                    WatchDelivery(ORIGIN, Long.MAX_VALUE - 1, FINGERPRINT),
                    WatchDelivery(ORIGIN, Long.MAX_VALUE, OTHER_FEED),
                ),
                Triple(
                    "the highest number again",
                    WatchDelivery(ORIGIN, Long.MAX_VALUE, FINGERPRINT),
                    WatchDelivery(ORIGIN, Long.MAX_VALUE, OTHER_FEED),
                ),
            ).map { (name, holding, arriving) ->
                buildJsonObject {
                    put("name", name)
                    holding?.let { put("held", it.text) }
                    put("arriving", arriving.text)
                    put("stores", WatchLinkContract.stores(holding, arriving))
                }.toString()
            }
            val echoes = listOf(null, held, WatchDelivery(ORIGIN, Long.MAX_VALUE, OTHER_FEED)).map { holding ->
                buildJsonObject {
                    holding?.let { put("held", it.text) }
                    put("echo", WatchLinkContract.echoOf(holding))
                }.toString()
            }
            val steps = JsonArray(WatchLinkContract.STORE_STEPS.map { JsonPrimitive(it.name) })

            return buildString {
                appendLine("    \"keys\": $keys,")
                appendLine("    \"bytes\": $bytes,")
                append(sectionText("fingerprints", fingerprints, indent = "    "))
                append(sectionText("deliveries", deliveries, indent = "    "))
                append(sectionText("stores", stores, indent = "    "))
                append(sectionText("echoes", echoes, indent = "    "))
                appendLine("    \"storeSteps\": $steps")
            }
        }

        /** `"key": [`, a line for each item, `],`: the file reads one case to a line. */
        fun sectionText(key: String, items: List<String>, indent: String = "  "): String = buildString {
            appendLine("$indent${JsonPrimitive(key)}: [")
            items.forEachIndexed { index, item -> appendLine("$indent  $item" + if (index < items.lastIndex) "," else "") }
            appendLine("$indent],")
        }

        fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(toMutableMap().apply { put(key, value) })

        fun JsonObject.without(vararg keys: String): JsonObject = JsonObject(filterKeys { it !in keys })

        /** [this] with the value at [path] changed: `a.b` by key, and `a[].b` in the first item of the list `a`. */
        fun JsonElement.withFirstAt(path: String, change: (JsonElement) -> JsonElement): JsonElement {
            if (path.isEmpty()) return change(this)
            val segment = path.substringBefore('.')
            val rest = path.substringAfter('.', "")
            val key = segment.removeSuffix("[]")
            val value = jsonObject.getValue(key)
            val changed = if (segment.endsWith("[]")) {
                JsonArray(listOf(value.jsonArray[0].withFirstAt(rest, change)) + value.jsonArray.drop(1))
            } else {
                value.withFirstAt(rest, change)
            }
            return jsonObject.with(key, changed)
        }

        val ABOUT = "Test vectors for the watchOS app's Swift port of the feed's decoder and rules, beyond watch-rules-vectors.json, " +
            "written by SwiftPortVectorsTest.kt from Kotlin's own answers (plan 4, Task 1). " +
            "decode: texts a watch may be handed: the four samples by file, the English sample with one change (olderPhone to " +
            "quotedRtl, notJson and newerPhone apart), newerPhone, a later phone's: the twelve-hour English sample with the " +
            "first sample's phrases and a key no build reads, future, in every kind of object the feed holds, each of the " +
            "model's twelve classes once, the countdown phrases, the screens' words, the face screen's words, one of their counts and day 0's 12-hour " +
            "times among them (Ruling 60 item 1d), the lenient cases (quotedFajr to quotedTarget, Ruling 57 item 2), " +
            "each oneDay with one more field the port reads written in a wrong type kotlinx reads leniently, " +
            "plan 3's keys as kotlinx refuses them " +
            "(nullScreens to numberClockTime, Ruling 57 item 4), the phone's Show sunrise (sunriseHidden, oneDay with " +
            "ui.showSunrise false, and nullShowSunrise, with it null; olderPhone has no such key and shows Sunrise), " +
            "the feeds the later sections run on " +
            "(turkishIkindi, englishIkindi, noMidnights, tromsoMay, and Ruling 57's ishaAtTheNextFajr, aDayOfOneMinute, " +
            "ishaFirstInADayOfOneMinute, startsAtTheEndOfTime, emptySpokenHours, emptySpokenMinutes, the labels' " +
            "turkishIkindiTagged to greekTonos, and plan 4 Task 3's blankWords and clockForms), newerShape, and the texts " +
            "on oneDay, the sample cut to its first day: missingVersion and newerDayShape " +
            "(at version 2, its times a list), the number and text forms from fractionalVersion to escapedVersionKey, each oneDay " +
            "with one change (escapedKeyFractionalWrittenAt is fractionalWrittenAt with its key's first letter written as a " +
            "Unicode escape), the laterKeys cases, oneDay with keys a later phone may add at version 1, named like the integer " +
            "fields at paths the model does not have, and the duplicate cases, oneDay with one key written a second time. " +
            "A case's text is json, or the whole text of file, a file in this directory, after a UTF-8 byte-order mark " +
            "(EF BB BF) where bom is true. The mark is a field of its own because " +
            "Foundation's JSONSerialization drops a U+FEFF that opens a string value (JSONDecoder keeps it). " +
            "result is what a Wear watch makes of it (FeedStore): feed when WatchFeedJson.decode reads it, newer when it does not and " +
            "WatchFeedJson.versionOf reads a version above ${WatchFeedJson.VERSION}, and malformed otherwise. A newer feed need not " +
            "have this build's shape (newerShape, newerDayShape), so the version is read before the body. " +
            "class: each case has one (Ruling 56), which this rule gives from its text alone (a file's without the line end it " +
            "closes with), and SwiftPortVectorsTest holds every case to it. FOREIGN when kotlinx's encoder, configured as " +
            "WatchFeedJson's, writes no such text: kotlinx's own reader reads no JSON from it (notJson, a trailing comma, a " +
            "byte-order mark), or the encoder, writing back the JSON kotlinx reads from it, writes another text (an integer " +
            "written with an exponent or as 1792303200.0, a key spelled with an escape, a key written twice), or a number at " +
            "one of the model's integer fields (the root's version and writtenAt, a day's midnight and times, an upTo of a " +
            "spoken or screen count, a phrase's target, an Int or a Long in every model the plans name) has a point or an " +
            "exponent (1.0, 2.0, 100.0). Otherwise ENCODER when the text holds a value of a model the plans name: a version " +
            "above ${WatchFeedJson.VERSION}, whatever the rest (a newer phone's: version2, newerShape, newerDayShape), or a " +
            "version of 1 with every key the model has holding a value of its field (an Int a whole number in 32 bits, a Long " +
            "in 64, a Double a number, a Boolean true or false, a String a string, a Char a string of one UTF-16 unit, null " +
            "only where the field is nullable) and every key without a default there, any other key a later phone's and " +
            "ignored (newerPhone, and the laterKeys cases, named like the integer fields at paths the model does not have), " +
            "and a defaulted key free to be absent, as an older phone leaves it (olderPhone). Where the model is this build's, " +
            "the text is WatchFeedJson.encode's own; an older or a later phone's is the same encoder writing its JSON tree. " +
            "Otherwise ONE_CHANGE: an ENCODER case's text with one value changed, removed or set to null (version0, " +
            "missingVersion, noDays, nullInADefaultedField, twoCharacterZero, the four quoted cases, the lenient cases, " +
            "nullScreens, nullLatinScript, twoCharacterRingZero, numberClockTime, nullShowSunrise, booleanVersion, wideVersion, wideTarget, " +
            "and the three digitZero cases of two UTF-16 units). The port is held by " +
            "class. On ENCODER and ONE_CHANGE it answers exactly swift where it is given and result elsewhere, and for a feed " +
            "decodes exactly expect. swift is on the four quoted cases and the lenient cases alone, each a value of the wrong " +
            "type that kotlinx reads leniently as its value and the port refuses: a number or a boolean written as a string " +
            "(kotlinx's consumeNumericLiteral for an Int or a Long, consumeStringLenient and Double.parseDouble for a " +
            "Double, consumeBooleanLenient for a Boolean), and a Char written as a bare digit " +
            "(decodeChar's consumeStringLenient). Each is an accepted difference in the safe direction (the Lead's call 8, " +
            "Ruling 57 item 2): no phone writes a wrong type, and a refusal keeps the last feed the watch understood. There is " +
            "one lenient case for each field the port reads that kotlinx would read so, plan 3's among them since the port " +
            "reads them (plan 4 Task 3: quotedTurnRightUpTo, quotedTurnLeftUpTo, bareRingDigitZero, which writes the bare 0 " +
            "where oneDay has null, and quotedLatinScript), and quotedShowSunrise for the phone's Show sunrise. On FOREIGN it must not " +
            "crash or hang, and where it answers feed, every value it read is exactly the JSON value the text holds, with no " +
            "truncation or rounding and the Int32 and Int64 ranges checked, as far as Foundation's Decimal keeps a number's " +
            "digits: about 38 significant digits, past which a FOREIGN number may read as its rounded whole value (Ruling 57 " +
            "item 1; ENCODER and ONE_CHANGE texts hold plain integers, so they are unaffected). A refusal is always a right " +
            "answer there. result and expect are Kotlin's answer, kept as information, which the port is not held to. So the " +
            "port reads the " +
            "version first, a version above ${WatchFeedJson.VERSION} being newer before the body is read, then decodes with " +
            "Foundation's JSONDecoder, Kotlin's Int an Int32, a Long an Int64 and digitZero one UTF-16 unit, and needs no JSON " +
            "scanner of its own (Ruling 56 item 4). Kotlin on the FOREIGN texts, as information: kotlinx refuses an integer " +
            "field written with a point, and reads one written with an exponent when its value is whole, its digits read into " +
            "a Long (so a mantissa beyond a Long is malformed) and the exponent applied in double arithmetic, which judges " +
            "whether the value is whole; it reads a key with its escapes undone, refuses a trailing comma and a mark before " +
            "the text, and reads every copy of a key written twice as its field and keeps the last, where Foundation's readers " +
            "keep the first. It skips the value of a key the model lacks with skipElement, which balances its brackets and " +
            "reads each string in it with its escapes checked, so a bad escape there is malformed and other text that is not " +
            "JSON there is not. And it reads forms Foundation refuses (leading zeros, capital booleans, a raw tab, line break " +
            "or NUL in a string, a lone surrogate, and 23., .5 or 1e-400 for a Double), which no case holds. For a feed, " +
            "expect is what Kotlin decodes: " +
            "the number of days, day 0's hijriDayMonth, gregorianSpoken and midnight (null with no day), ui.digitZero, " +
            "ui.ringDigitZero (null for none), ui.latinScript, ui.showSunrise, day0Asr (day 0's Asr as WatchRules.clockText draws it and " +
            "WatchRules.spokenClock says it, null with no day), whether ui.spoken is there, the number of his phrases, and " +
            "the city. " +
            "Every case of spoken, labels, days, clock, words, moments and starts names its feed, a decode case, and carries that " +
            "case's class, always ENCODER: the port gives exactly its answer. The cases of filled, inDigits and contract are " +
            "no feeds and carry no class: the port gives exactly Kotlin's answer on each. " +
            "spoken: WatchRules.spokenCountdown(feed, name, clock, secondsLeft) for day 0's Asr, its name and its time as " +
            "WatchRules.spokenClock gives it; null for a feed with no spoken words, or with an empty list of spoken hours or " +
            "minutes (emptySpokenHours, emptySpokenMinutes). " +
            "labels: the prayer's name on that day in capitals, uppercased by ui.language's tag, as ComplicationFields.nextPrayer " +
            "writes it (Turkish capitalises i as İ): on the JVM, by the language Locale.forLanguageTag reads from the tag, its " +
            "primary subtag in any case, so tr-u-ca-gregory is Turkish and AZ Azerbaijani, and tr_TR, ill-formed, is no " +
            "language; and only Turkish, Azerbaijani and Lithuanian (its dot above dropped) case otherwise than the default, " +
            "where Foundation's ICU also cases Greek its own way (its tonos dropped), so greekTonos is the default's (Ruling 57 " +
            "item 6). Day 6 of each sample is Friday 23 October, whose Dhuhr is the Jumuʿah. " +
            "days: WatchRules.today(feed, now) and WatchRules.dayStart(feed, today), null when now is on no day of the feed, " +
            "a second before, at and after the start of each day named and on both sides of the last day's end. sample turns " +
            "over at its midnights; noMidnights has none, so its dates turn over at solar midnight, halfway between two Dhuhrs; " +
            "tromsoMay is the high-latitude night. " +
            "clock: each of the six times of days 0 and 6, keyed by WatchTime (Sunrise among them), as WatchRules.clockText draws it " +
            "and as WatchRules.spokenClock splices it into a spoken line: a 12-hour time with its day period, isolated by U+2068 and " +
            "U+2069 when spoken; a 24-hour time, in any digits, the same both ways. On clockForms, days 0, 1 and 2: clock " +
            "strings in Bengali, extended Arabic-Indic and Devanagari digits, said as they are, and in the mathematical bold " +
            "digits, each two UTF-16 units that Kotlin's Char.isDigit does not take, so isolated; 12-hour times with the " +
            "marker first, a narrow no-break space, and none; and on day 2 (Ruling 60 item 1a) clock strings in numbers that " +
            "are no decimal digit (Unicode's No and Nl: superscript and circled digits, circled numbers, the Roman numeral " +
            "ten), each one UTF-16 unit Char.isDigit does not take, so isolated: a digit is a decimal digit, Nd, never any " +
            "number. A clock time is the phone's string, a 12-hour phone's day period " +
            "the one its clockTime carries (Ruling 58 item 3). " +
            "words: WatchFeed.words(), the feed's ui.screens with the English for every word left blank, written whole with " +
            "every key, on the four samples, olderPhone (no words, so all English) and blankWords. Blank is Kotlin's isBlank: " +
            "empty, or every UTF-16 unit a Char.isWhitespace (a space separator, a line or paragraph separator, tab to " +
            "carriage return, U+001C to U+001F, each of the four alone in a word of blankWords), so U+0085 and U+200B are " +
            "words; a list is English only when empty; the " +
            "presets are the English map overlaid by the feed's names that are not blank, an id the English lacks kept. " +
            "presetIds (Ruling 60 item 1e): the presets' ids in Kotlin's order, which a dictionary does not keep: " +
            "TasbeehPresets.builtIn's, whatever order the phone sent them in, then each id they lack in the order the phone " +
            "sent it (blankWords sends custom_2, alhamdulillah, subhanallah and custom_1, and its ids end custom_2, custom_1). " +
            "moments (Ruling 57 item 5): a rules case in watch-rules-vectors.json's shape (WatchRules.moment's next, previous, " +
            "secondsLeft and progress, the Float widened to a Double, so exact; countdown, litOnList and litOnGlance) at " +
            "instants no earlier case reached: on ishaAtTheNextFajr, the sample's first two days with day 0's Isha moved onto " +
            "day 1's Fajr, around that instant, where yesterday's starts count only before today's Fajr; and on " +
            "aDayOfOneMinute, the same two days with day 1 beginning a minute after day 0, before day 0's Fajr, where no start " +
            "has arrived when the date has turned, so the previous is yesterday's Isha, still ahead, and the progress 0; on " +
            "ishaFirstInADayOfOneMinute, with that Isha before its Fajr, it is the next start too, a period of no length, " +
            "whose progress is 0 by the guard (the time since that Isha, negative, over 0 is -Infinity); and on " +
            "startsAtTheEndOfTime, the same two days with every time at Long.MAX_VALUE - 1000 and day 1 beginning at " +
            "Long.MIN_VALUE + 100000, ten seconds in: the previous is yesterday's Isha and the next today's Fajr, at one " +
            "instant more than half of a Long ahead, a period of no length whose time since its start wraps to a positive " +
            "number, as a Long does, so the progress is 0 by the guard alone, which " +
            "read total < 0 would leave +Infinity, and the clamp 1. " +
            "starts: WatchRules.starts whole, on ishaAtTheNextFajr and tromsoMay: a start at or after the next day's Fajr is " +
            "left out. " +
            "filled: WatchRules.filled(pattern, parts): each place %n\$s replaced by the nth part, in any order and more than " +
            "once, a part never filled in turn, and a place that names no part (%0\$s, one beyond the parts, one beyond an " +
            "Int) kept as it is. " +
            "inDigits: WatchRules.inDigits(text, zero): each Western digit written as the digit that many places after zero, " +
            "added in 16 bits as Kotlin's Char does, so from U+FFFA the 6 is U+0000; other text kept. " +
            "contract: WatchLinkContract's context and message keys and its byte figures; isFingerprint; deliveryOf on each text, " +
            "with the delivery it reads (origin, seq as an Int64, fingerprint) or none; stores(held, arriving), held absent when the " +
            "watch holds nothing; echoOf(held); and the order a receiver stores in, STORE_STEPS (Ruling 50). The delivery travels " +
            "beside the feed and never inside it, so no decode text holds one. The contract's cases are not feeds and carry no " +
            "class: the port gives exactly Kotlin's answer on each. " +
            "Nobody edits this file: change the Kotlin, then rewrite it with " +
            "./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors and commit the result."
    }
}
