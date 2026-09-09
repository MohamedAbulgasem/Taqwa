package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory [KeyValueStore] double, kept local to this test until a shared one exists. */
private class FakeKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) {
        values[key] = value
    }
    override fun getString(key: String): String? = values[key]
}

class AyahPoolMirrorTest {

    // Field/entry separators, built from the escapes rather than pasted as literal control
    // characters (per the format's own rule: never paste U+001E/U+001F raw into source).
    private val fs = '\u001F'.toString()
    private val rs = '\u001E'.toString()

    private val entryOne = AyahPoolEntry(
        surah = 9,
        ayah = 51,
        surahLatin = "At-Tawbah",
        surahArabic = "التوبة",
        arabic = "قُلْ لَنْ يُصِيبَنَا إِلَّا مَا كَتَبَ اللَّهُ لَنَا",
        translation = "Say, \"Never will we be struck except by what Allah has decreed for us\"",
    )
    private val entryTwo = AyahPoolEntry(
        surah = 65,
        ayah = 3,
        surahLatin = "At-Talaq",
        surahArabic = "الطلاق",
        // Deliberately containing characters that are field/entry separators in *other* wire
        // formats (| ; =) and an embedded newline, to prove only U+001E/U+001F matter here.
        arabic = "وَمَنْ يَتَّقِ اللَّهَ | يَجْعَلْ لَهُ ; مَخْرَجًا = مِنْ\nحَيْثُ",
        translation = "And whoever fears Allah = He will make; for him | a way\nout",
    )
    private val mirror = AyahPoolMirror(
        languageTag = "en-US",
        translationId = "en.sahih",
        translationRtl = false,
        arabicIndicDigits = false,
        entries = listOf(entryOne, entryTwo),
    )

    // -- serialize / deserialize round trip --------------------------------------------------

    @Test
    fun roundTripsEntriesContainingOtherFormatsSeparatorsAndArabicText() {
        val restored = AyahPoolMirror.deserialize(AyahPoolMirror.serialize(mirror))
        assertEquals(mirror, restored)
    }

    @Test
    fun roundTripsWhenTranslationIsOff() {
        val noTranslation = mirror.copy(
            translationId = "none",
            entries = mirror.entries.map { it.copy(translation = "") },
        )
        val restored = AyahPoolMirror.deserialize(AyahPoolMirror.serialize(noTranslation))
        assertEquals(noTranslation, restored)
        assertFalse(restored!!.showsTranslation)
    }

    @Test
    fun showsTranslationIsTrueWhenAnyEntryHasNonEmptyTranslationAndIdIsNotNone() {
        assertTrue(mirror.showsTranslation)
    }

    @Test
    fun showsTranslationIsFalseWhenEveryEntryTranslationIsEmptyEvenIfIdIsSet() {
        val allEmpty = mirror.copy(entries = mirror.entries.map { it.copy(translation = "") })
        assertFalse(allEmpty.showsTranslation)
    }

    @Test
    fun roundTripsTheArabicIndicDigitChoice() {
        // The app's own answer, not one derived from the tag: an Arabic UI on a device whose ICU
        // data keeps Western digits writes `false` under an `ar` tag, and that has to survive the
        // wire (D2).
        val arabicIndic = mirror.copy(languageTag = "ar-LY", arabicIndicDigits = true)
        val restored = AyahPoolMirror.deserialize(AyahPoolMirror.serialize(arabicIndic))
        assertEquals(arabicIndic, restored)
        assertEquals(true, restored?.arabicIndicDigits)

        val western = mirror.copy(languageTag = "ar-EG", arabicIndicDigits = false)
        assertEquals(false, AyahPoolMirror.deserialize(AyahPoolMirror.serialize(western))?.arabicIndicDigits)
    }

    @Test
    fun theSerialisedHeaderCarriesVersionTwo() {
        assertEquals("2", AyahPoolMirror.serialize(mirror).substringBefore(fs))
    }

    @Test
    fun roundTripsRtlTranslation() {
        val rtl = mirror.copy(translationRtl = true)
        val restored = AyahPoolMirror.deserialize(AyahPoolMirror.serialize(rtl))
        assertEquals(true, restored?.translationRtl)
    }

    @Test
    fun roundTripsWithNoEntries() {
        val empty = mirror.copy(entries = emptyList())
        val restored = AyahPoolMirror.deserialize(AyahPoolMirror.serialize(empty))
        assertEquals(empty, restored)
    }

    // -- malformed input ----------------------------------------------------------------------

    @Test
    fun emptyStringDeserializesToNull() {
        assertNull(AyahPoolMirror.deserialize(""))
    }

    @Test
    fun garbageDeserializesToNull() {
        assertNull(AyahPoolMirror.deserialize("not a valid mirror at all"))
    }

    @Test
    fun aFutureVersionDeserializesToNull() {
        val futureVersion = listOf("3", "en-US", "en.sahih", "0", "0").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(futureVersion))
    }

    @Test
    fun aVersionOneMirrorDeserializesToNull() {
        // The version-1 header has no digit field, and there is nothing honest to default it to
        // (D2): the widgets treat "no mirror" as "write one" (Android) or "show the placeholder"
        // (iOS), which is the right behaviour for a mirror left by the previous build.
        val versionOne = listOf("1", "en-US", "en.sahih", "0").joinToString(fs)
        val entry = listOf("9", "51", "At-Tawbah", "التوبة", "text", "trans").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(versionOne + rs + entry))
    }

    @Test
    fun aHeaderMissingTheDigitFieldDeserializesToNull() {
        val fourFieldHeader = listOf("2", "en-US", "en.sahih", "0").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(fourFieldHeader))
    }

    @Test
    fun aDigitFieldThatIsNotZeroOrOneDeserializesToNull() {
        val header = listOf("2", "en-US", "en.sahih", "0", "yes").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(header))
    }

    @Test
    fun anEntryWithFiveFieldsDeserializesToNull() {
        val header = listOf("2", "en-US", "en.sahih", "0", "0").joinToString(fs)
        // Five fields: missing the trailing translation field.
        val shortEntry = listOf("9", "51", "At-Tawbah", "التوبة", "text").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(header + rs + shortEntry))
    }

    @Test
    fun anEntryWithNonIntegerSurahDeserializesToNull() {
        val header = listOf("2", "en-US", "en.sahih", "0", "0").joinToString(fs)
        val badEntry = listOf("nine", "51", "At-Tawbah", "التوبة", "text", "trans").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(header + rs + badEntry))
    }

    @Test
    fun aStrayTrailingEmptyEntryBlockIsMalformedNotTolerated() {
        // A trailing entry separator leaves an empty final block after split — not tolerated.
        val serialized = AyahPoolMirror.serialize(mirror)
        assertNull(AyahPoolMirror.deserialize(serialized + rs))
    }

    @Test
    fun aTranslationEndingInNewlineRoundTripsUnchanged() {
        // A newline inside a field is ordinary content per the format (spec §4) — even when
        // it's the very last character of the very last entry's translation, right before the
        // end of the whole string.
        val withTrailingNewline = mirror.copy(
            entries = mirror.entries.mapIndexed { i, e ->
                if (i == mirror.entries.lastIndex) e.copy(translation = e.translation + "\n") else e
            },
        )
        val restored = AyahPoolMirror.deserialize(AyahPoolMirror.serialize(withTrailingNewline))
        assertEquals(withTrailingNewline, restored)
    }

    @Test
    fun aHeaderWithTheWrongFieldCountDeserializesToNull() {
        val tooFewFields = listOf("2", "en-US", "en.sahih").joinToString(fs)
        assertNull(AyahPoolMirror.deserialize(tooFewFields))
    }

    // -- entryFor / rotation -------------------------------------------------------------------

    @Test
    fun entryForUsesAyahRotationIndexFor() {
        val epochDay = 20705L
        val seed = 123L
        val expectedIndex = AyahRotation.indexFor(epochDay, seed, mirror.entries.size)
        assertEquals(mirror.entries[expectedIndex], mirror.entryFor(epochDay, seed))
    }

    @Test
    fun entryForOnEmptyEntriesIsNull() {
        val empty = mirror.copy(entries = emptyList())
        assertNull(empty.entryFor(20705L, 123L))
    }

    // -- store read / write --------------------------------------------------------------------

    @Test
    fun writeThenReadRoundTripsThroughTheStore() {
        val store = FakeKeyValueStore()
        AyahPoolMirror.write(store, mirror)
        assertEquals(mirror, AyahPoolMirror.read(store))
    }

    @Test
    fun readOnAnEmptyStoreIsNull() {
        assertNull(AyahPoolMirror.read(FakeKeyValueStore()))
    }

    @Test
    fun writeStoresOnlyUnderTheMirrorKeyNotTheSeedKey() {
        val store = FakeKeyValueStore()
        AyahPoolMirror.write(store, mirror)
        assertNull(AyahPoolMirror.seed(store))
    }

    // -- seed -----------------------------------------------------------------------------------

    @Test
    fun writeSeedThenSeedRoundTrips() {
        val store = FakeKeyValueStore()
        AyahPoolMirror.writeSeed(store, 8_675_309L)
        assertEquals(8_675_309L, AyahPoolMirror.seed(store))
    }

    @Test
    fun seedIsNullWhenAbsent() {
        assertNull(AyahPoolMirror.seed(FakeKeyValueStore()))
    }

    @Test
    fun seedIsNullWhenStoredValueIsNotALong() {
        val store = FakeKeyValueStore()
        store.putString(AyahPoolMirror.SEED_KEY, "not-a-long")
        assertNull(AyahPoolMirror.seed(store))
    }

    @Test
    fun negativeSeedsRoundTrip() {
        val store = FakeKeyValueStore()
        AyahPoolMirror.writeSeed(store, -42L)
        assertEquals(-42L, AyahPoolMirror.seed(store))
    }
}
