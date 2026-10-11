package world.taqwa.app.adhkar

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalogue as bundled (`files/adhkar/items.json`, `files/adhkar/text/en.json`), parsed from an
 * exact copy of the two files held in [ITEMS_JSON] and [EN_JSON]: `Res.readBytes` does not run in
 * this module's tests (the Android host tests have no `Log`, the iOS ones no bundle), and the copy
 * is what lets the real data be checked on both targets. `AdhkarFixturesTest` fails if the copy
 * ever differs from the bundled files, so these are checks of the shipped data, not of a sample.
 *
 * The Arabic in the file is deliberately not NFC-normalised (the shadda comes before the vowel,
 * as OpenDua prints it), so the wording checks look for words with no shadda-and-vowel pair.
 */
class AdhkarCatalogueTest {

    private val items = AdhkarCatalogue.parse(ITEMS_JSON)

    private fun byId(id: String) = items.first { it.id == id }

    @Test fun `the morning set has eighteen pages in book order`() {
        val morning = items.pagesFor(AdhkarSet.MORNING)
        assertEquals(18, morning.size)
        assertEquals("hisn-075", morning.first().id)
        assertEquals("hisn-096", morning.last().id)
        assertEquals(15, items.pagesFor(AdhkarSet.EVENING).size)
        // The istighfar, one counter shared by both sets, is a page of each.
        assertTrue("hisn-096" in items.pagesFor(AdhkarSet.MORNING).map { it.id })
        assertTrue("hisn-096" in items.pagesFor(AdhkarSet.EVENING).map { it.id })
    }

    @Test fun `the evening set drops the morning only items and adds the night refuge`() {
        val evening = items.pagesFor(AdhkarSet.EVENING)
        assertTrue(evening.none { it.id in setOf("hisn-090", "hisn-093", "hisn-094", "hisn-095") })
        assertEquals("hisn-097", evening.last().id)
        assertTrue(items.pagesFor(AdhkarSet.MORNING).none { it.id == "hisn-097" })
    }

    @Test fun `the book order never goes backwards and every id is its own`() {
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertEquals(items.map { it.hisn }.sorted(), items.map { it.hisn })
    }

    @Test fun `wording variants follow the set`() {
        val item = byId("hisn-078")
        assertTrue(item.arabicFor(AdhkarSet.MORNING)!!.contains("أَصْبَحْنَا"))
        assertTrue(item.arabicFor(AdhkarSet.EVENING)!!.contains("أَمْسَيْنَا"))
        assertNotEquals(item.transliterationFor(AdhkarSet.MORNING), item.transliterationFor(AdhkarSet.EVENING))
    }

    @Test fun `a single wording stored under both serves either set`() {
        val item = byId("hisn-079")
        assertNotNull(item.arabicFor(AdhkarSet.MORNING))
        assertEquals(item.arabicFor(AdhkarSet.MORNING), item.arabicFor(AdhkarSet.EVENING))
        assertEquals(item.transliterationFor(AdhkarSet.MORNING), item.transliterationFor(AdhkarSet.EVENING))
    }

    @Test fun `a wording stored under morning is never offered in the evening`() {
        // Hisn 90 says "we have entered the morning" and is morning only: stored under "morning"
        // rather than "both" so that no caller can serve it to the evening set by accident.
        val item = byId("hisn-090")
        assertEquals(setOf(AdhkarSet.MORNING), item.sets)
        assertNotNull(item.arabicFor(AdhkarSet.MORNING))
        assertNotNull(item.transliterationFor(AdhkarSet.MORNING))
        assertNull(item.arabicFor(AdhkarSet.EVENING))
        assertNull(item.transliterationFor(AdhkarSet.EVENING))
    }

    @Test fun `counts match the book`() {
        assertEquals(100, byId("hisn-091").count)
        assertEquals(10, byId("hisn-092").count)
        assertEquals(3, byId("hisn-097").count)
        assertEquals(3, byId("hisn-076a").count)
        assertTrue(byId("hisn-096").countDaily)
        assertEquals(1, items.count { it.countDaily })
        assertTrue(items.all { it.count >= 1 })
    }

    @Test fun `the quran items carry a reference and no text of their own`() {
        val kursi = byId("hisn-075")
        assertEquals(AdhkarKind.QURAN, kursi.kind)
        assertEquals(QuranRef(surah = 2, ayah = 255, prefix = QuranPrefix.ISTIADHAH), kursi.quran)
        // Only the istiadhah Hisn prints before the verse is the catalogue's own text.
        assertEquals(setOf("both"), kursi.arabic.keys)
        assertTrue(kursi.transliteration.isEmpty())
        assertNull(kursi.transliterationFor(AdhkarSet.MORNING))

        val quls = listOf("hisn-076a", "hisn-076b", "hisn-076c").map { byId(it) }
        assertEquals(listOf(112, 113, 114), quls.map { it.quran!!.surah })
        for (qul in quls) {
            assertEquals(AdhkarKind.QURAN, qul.kind)
            assertNull(qul.quran!!.ayah)
            assertEquals(QuranPrefix.BASMALA, qul.quran!!.prefix)
            assertTrue(qul.arabic.isEmpty())
            assertNull(qul.arabicFor(AdhkarSet.MORNING))
        }
        assertTrue(items.filter { it.kind == AdhkarKind.DUA }.all { it.quran == null })
    }

    @Test fun `every item names where it comes from`() {
        for (item in items) {
            assertTrue(item.sources.isNotEmpty(), item.id)
            assertTrue(item.sources.all { it.collection.isNotBlank() && it.number.isNotBlank() }, item.id)
        }
        assertEquals(listOf(Source("bukhari", "6306")), byId("hisn-079").sources)
    }

    @Test fun `every dua has arabic and a transliteration for each of its sets`() {
        for (item in items.filter { it.kind == AdhkarKind.DUA }) {
            for (set in item.sets) {
                assertFalse(item.arabicFor(set).isNullOrBlank(), "${item.id} $set arabic")
                assertFalse(item.transliterationFor(set).isNullOrBlank(), "${item.id} $set transliteration")
            }
        }
    }

    @Test fun `every dua has an english translation for each of its sets`() {
        val text = AdhkarCatalogue.parseText(EN_JSON)
        assertEquals("en", text.language)
        for (item in items.filter { it.kind == AdhkarKind.DUA }) {
            for (set in item.sets) {
                assertFalse(text.translationFor(item.id, set).isNullOrBlank(), "${item.id} $set")
            }
        }
    }

    @Test fun `the translation follows the set like the arabic does`() {
        val text = AdhkarCatalogue.parseText(EN_JSON)
        assertTrue(text.translationFor("hisn-078", AdhkarSet.MORNING)!!.contains("resurrection"))
        assertTrue(text.translationFor("hisn-078", AdhkarSet.EVENING)!!.contains("return"))
        assertNotNull(text.translationFor("hisn-079", AdhkarSet.EVENING))
        assertNull(text.translationFor("hisn-090", AdhkarSet.EVENING))
        // The Quran is never translated by this file: the reader's own translation shows instead.
        assertNull(text.translationFor("hisn-075", AdhkarSet.MORNING))
        assertNull(text.translationFor("hisn-000", AdhkarSet.MORNING))
    }

    @Test fun `load reads the items file and loadText the language file`() = runTest {
        val asked = mutableListOf<String>()
        val read: suspend (String) -> ByteArray = { path ->
            asked += path
            when (path) {
                "files/adhkar/items.json" -> ITEMS_JSON.encodeToByteArray()
                "files/adhkar/text/en.json" -> EN_JSON.encodeToByteArray()
                else -> error("no file $path")
            }
        }
        assertEquals(items, AdhkarCatalogue.load(read))
        assertEquals("en", AdhkarCatalogue.loadText("en", read)!!.language)
        assertEquals(listOf("files/adhkar/items.json", "files/adhkar/text/en.json"), asked)
    }

    @Test fun `a language with no file is null and a file that does not parse is not`() = runTest {
        assertNull(AdhkarCatalogue.loadText("xx") { error("no such resource") })
        assertFailsWith<SerializationException> {
            AdhkarCatalogue.loadText("xx") { "not json".encodeToByteArray() }
        }
    }

    @Test fun `a language file reaches the reader only once it is marked reviewed`() = runTest {
        // `flag` goes in before "items". A draft reads exactly as no file: false, null and no flag at all.
        fun file(flag: String) = """{"language": "xx", $flag "items": {"hisn-096": {"both": "draft"}}}"""
        suspend fun loaded(flag: String) = AdhkarCatalogue.loadText("xx") { file(flag).encodeToByteArray() }
        assertNull(loaded(""""reviewed": false,"""))
        assertNull(loaded(""""reviewed": null,"""))
        assertNull(loaded(""))
        assertEquals("draft", loaded(""""reviewed": true,""")!!.translationFor("hisn-096", AdhkarSet.MORNING))
        // The parser still reads a draft, so the content checks can see it; the gate is the loader's.
        assertEquals("draft", AdhkarCatalogue.parseText(file(""""reviewed": false,""")).translationFor("hisn-096", AdhkarSet.EVENING))
        assertFalse(AdhkarCatalogue.isReviewed(file("")))
        // OpenDua's English, as bundled, is reviewed.
        assertTrue(AdhkarCatalogue.isReviewed(EN_JSON))
    }

    @Test fun `a schema this build does not know is refused`() {
        assertFailsWith<SerializationException> {
            AdhkarCatalogue.parse("""{"schema": 2, "items": []}""")
        }
    }

    @Test fun `a set or kind this build does not know is refused rather than dropped`() {
        fun file(kind: String, set: String) =
            """{"schema": 1, "items": [{"id": "x", "hisn": 1, "kind": "$kind", "sets": ["$set"], "count": 1}]}"""
        assertEquals(1, AdhkarCatalogue.parse(file("dua", "morning")).size)
        assertFailsWith<SerializationException> { AdhkarCatalogue.parse(file("dua", "noon")) }
        assertFailsWith<SerializationException> { AdhkarCatalogue.parse(file("poem", "morning")) }
    }

    @Test fun `keys a later build adds are ignored and the defaults fill what is missing`() {
        val parsed = AdhkarCatalogue.parse(
            """{"schema": 1, "future": true, "items": [{"id": "x", "hisn": 1, "kind": "dua",
               "sets": ["evening"], "audio": "x.mp3"}]}""",
        ).single()
        assertEquals(1, parsed.count)
        assertFalse(parsed.countDaily)
        assertNull(parsed.quran)
        assertTrue(parsed.arabic.isEmpty() && parsed.transliteration.isEmpty() && parsed.sources.isEmpty())
        assertEquals(setOf(AdhkarSet.EVENING), parsed.sets)
    }
}
