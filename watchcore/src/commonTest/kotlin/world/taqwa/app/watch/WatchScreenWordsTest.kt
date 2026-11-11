package world.taqwa.app.watch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import world.taqwa.app.tasbeeh.TasbeehPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The words of the watch app's screens as the feed carries them ([WatchUi.screens]), and the
 * English a feed without them falls back to ([WatchScreenWords.orEnglish]).
 */
class WatchScreenWordsTest {

    private val english = WatchScreenWords.ENGLISH

    @Test
    fun blankFieldsTakeTheEnglish() {
        val words = WatchScreenWords(round = "الجولة %1\$s").orEnglish()

        assertEquals("الجولة %1\$s", words.round)
        assertEquals("of %1\$s", words.of)
        assertEquals(TasbeehPresets.builtIn.map { it.id }, words.presets.keys.toList())
        assertEquals("After prayer", words.presets["after_prayer"])
        assertEquals("SubhanAllahi wa bihamdihi", words.presets["subhanallahi_wa_bihamdihi"])
        // Every other field is ENGLISH's, the lists among them.
        assertEquals(english.copy(round = "الجولة %1\$s"), words)

        // A word of spaces alone is blank too.
        assertEquals("Qibla", WatchScreenWords(qibla = "  ").orEnglish().qibla)
        // The Apple Watch's two words (plan 4 Task 3) take the English as every other word does.
        val apple = WatchScreenWords(crownHint = " ", appName = "").orEnglish()
        assertEquals("Turn the crown or tap", apple.crownHint)
        assertEquals("Taqwa", apple.appName)
        assertEquals("تقوى", WatchScreenWords(appName = "تقوى").orEnglish().appName)
        // A feed with no words at all speaks English, and the English stays as it is.
        assertEquals(english, WatchScreenWords().orEnglish())
        assertEquals(english, english.orEnglish())
    }

    @Test
    fun aFeedsPresetNamesOverlayTheEnglish() {
        val words = WatchScreenWords(presets = mapOf("subhanallah" to "سبحان الله", "alhamdulillah" to "")).orEnglish()

        // Every built-in keeps its name, in the order of the list: the feed's where it gave one, ...
        assertEquals(TasbeehPresets.builtIn.map { it.id }, words.presets.keys.toList())
        assertEquals("سبحان الله", words.presets["subhanallah"])
        // ... and the English where it gave none, or a blank one.
        assertEquals("Alhamdulillah", words.presets["alhamdulillah"])
        assertEquals("After prayer", words.presets["after_prayer"])
    }

    @Test
    fun everyEnglishPatternHasItsPlaces() {
        listOf(english.round, english.of, english.distance, english.bearing, english.setCompleteSpoken).forEach { pattern ->
            assertTrue("%1\$s" in pattern, pattern)
        }
        // The two-place phrases fill the first place before the second in English. Another language
        // may put the second first (round 2's Bengali count): plan 3 Task 10 checks those by set.
        listOf(
            english.countSpoken,
            english.nextSpoken,
            english.roundSpoken,
            english.nameRoundSpoken,
            english.qiblaDetail,
        ).forEach { pattern ->
            val first = pattern.indexOf("%1\$s")
            val second = pattern.indexOf("%2\$s")
            assertTrue(first >= 0 && second > first, pattern)
        }
        (english.turnRight + english.turnLeft).forEach { assertTrue("%1\$s" in it.phrase, it.phrase) }
        assertEquals(4, english.cardinals.size)
        assertEquals(listOf("N", "E", "S", "W"), english.cardinals)
        assertEquals(TasbeehPresets.builtIn.map { it.id }, english.presets.keys.toList())
    }

    @Test
    fun theDialDrawsTheShortFormsAndTheirPlacesAreTheLongOnes() {
        // Ruling 66 item 1: the Qibla dial draws the watch's short forms, the figures and the unit
        // alone; what a screen reader hears stays the phone's long phrases (distance, qiblaDetail).
        // The short forms are WatchWords', which a native reviewer may change, so none is written
        // here (Ruling 67 item 2): WatchFeedSampleTest holds this fallback's and the Arabic
        // fixture's to what the phone's builder writes, and WatchWordsTest their shape. Each has
        // the long phrase's places.
        for ((name, words) in listOf("english" to english, "ARABIC_SCREENS" to Fixtures.ARABIC_SCREENS)) {
            assertTrue("%1\$s" in words.distanceOnDial && "%2\$s" !in words.distanceOnDial, "$name: ${words.distanceOnDial}")
            assertTrue("%1\$s°" in words.qiblaDetailOnDial && "%2\$s" in words.qiblaDetailOnDial, "$name: ${words.qiblaDetailOnDial}")
            assertEquals(words.distanceOnDial, words.orEnglish().distanceOnDial, name)
            assertEquals(words.qiblaDetailOnDial, words.orEnglish().qiblaDetailOnDial, name)
        }
        assertEquals("Makkah · %1\$s km away", english.distance)
        assertEquals("%1\$s° · %2\$s km to Makkah", english.qiblaDetail)
        assertEquals(english.distanceOnDial, WatchScreenWords().orEnglish().distanceOnDial)
        assertEquals(english.qiblaDetailOnDial, WatchScreenWords(qiblaDetailOnDial = " ").orEnglish().qiblaDetailOnDial)
        // A form a phone writes is kept as written: an example, not WatchWords' own, which may
        // change.
        val arabic = WatchScreenWords(distanceOnDial = "%1\$s كم", qiblaDetailOnDial = "%1\$s° · %2\$s كم").orEnglish()
        assertEquals("%1\$s كم", arabic.distanceOnDial)
        assertEquals("%1\$s° · %2\$s كم", arabic.qiblaDetailOnDial)
    }

    @Test
    fun everySampleCarriesEveryWord() {
        // The samples the debug build and the Swift port start from carry the phone's words, so a
        // screen drawn from one shows them and never the fallback: every field is there and filled,
        // read before orEnglish.
        val samples = linkedMapOf("capetown" to Fixtures.capeTownSample()) + Fixtures.capeTownSamples()
        val fields = WatchScreenWords.serializer().descriptor.elementsCount
        samples.forEach { (name, feed) ->
            // The plain Json leaves out a field at its default, so a word left at "" goes missing.
            val written = Json.encodeToJsonElement(WatchScreenWords.serializer(), feed.ui.screens) as JsonObject
            assertEquals(fields, written.size, "$name writes ${written.keys}")
            written.forEach { (key, value) -> assertTrue(filled(value), "$name: $key is $value") }
            assertEquals(TasbeehPresets.builtIn.map { it.id }, feed.ui.screens.presets.keys.toList(), name)
        }
        assertEquals(english, Fixtures.capeTownSample().ui.screens)
        assertEquals(english, Fixtures.capeTownSamples().getValue("en12").ui.screens)
        assertEquals(Fixtures.ARABIC_SCREENS, Fixtures.capeTownSamples().getValue("ar-ly12").ui.screens)
        assertEquals(Fixtures.ARABIC_SCREENS, Fixtures.capeTownSamples().getValue("ar-sa").ui.screens)
    }

    @Test
    fun aFeedsWordsAreItsScreensWithTheEnglishForTheBlanks() {
        val unworded = Fixtures.capeTown(days = 1, ui = Fixtures.english24().copy(screens = WatchScreenWords()))
        assertEquals(english, unworded.words())

        val libya = Fixtures.capeTown(days = 1, ui = Fixtures.arabicLibya())
        assertEquals(Fixtures.ARABIC_SCREENS, libya.words())
        assertEquals("الجولة %1\$s", libya.words().round)
        assertEquals(listOf("ش", "ق", "ج", "غ"), libya.words().cardinals)
    }

    @Test
    fun theWordsTakeAFewKilobytesAtMost() {
        // The Arabic words, the largest the fixtures hold. Plan 3 Task 10 measures again in all
        // seven languages, with the phone's own words.
        val libya = Fixtures.capeTownSamples().getValue("ar-ly12")
        val unworded = libya.copy(ui = libya.ui.copy(screens = WatchScreenWords()))

        val size = WatchFeedJson.encode(libya).encodeToByteArray().size -
            WatchFeedJson.encode(unworded).encodeToByteArray().size

        assertTrue(size in 1..4_096, "the Arabic words take $size bytes")
    }

    /** A value with something in it: a word that is not blank, a list or an object that is not
     * empty, and every value inside one filled too. */
    private fun filled(value: JsonElement): Boolean = when (value) {
        is JsonPrimitive -> value.content.isNotBlank()
        is JsonArray -> value.isNotEmpty() && value.all(::filled)
        is JsonObject -> value.isNotEmpty() && value.values.all(::filled)
    }
}
