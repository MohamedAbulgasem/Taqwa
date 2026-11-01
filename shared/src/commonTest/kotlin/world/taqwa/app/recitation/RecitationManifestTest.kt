package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A manifest of the shape the pipeline writes, cut down to two reciters. */
internal const val TWO_RECITERS = """
{
  "schema": 1,
  "generated": "2026-09-12T18:04:00Z",
  "base": "https://github.com/MohamedAbulgasem/Taqwa-data/releases/download/",
  "reciters": [
    {
      "id": "ar.alafasy",
      "nameEn": "Mishary Rashid Alafasy",
      "nameAr": "مشاري راشد العفاسي",
      "style": "murattal",
      "kbps": 64,
      "gapMs": 300,
      "hue": "amber",
      "photo": null,
      "release": "audio-ar.alafasy-v1",
      "totalBytes": 903001088,
      "surahs": [
        { "n": 1, "bytes": 123456, "sha256": "aa11" },
        { "n": 2, "bytes": 60817408, "sha256": "bb22" }
      ]
    },
    {
      "id": "ar.husary",
      "nameEn": "Mahmoud Khalil Al-Husary",
      "nameAr": "محمود خليل الحصري",
      "style": "murattal",
      "kbps": 64,
      "gapMs": 80,
      "hue": "clay",
      "photo": null,
      "release": "audio-ar.husary-v1",
      "totalBytes": 1297281024,
      "surahs": [
        { "n": 114, "bytes": 22222, "sha256": "cc33" }
      ]
    }
  ]
}
"""

class RecitationManifestTest {

    @Test
    fun aManifestWhoseBaseIsNotHttpsIsRejected() {
        val cleartext = TWO_RECITERS.replace("https://github.com", "http://github.com")
        assertFailsWith<IllegalArgumentException> { ManifestJson.parse(cleartext) }
    }

    @Test
    fun aReciterIdOrReleaseThatIsNotAPlainNameIsRejected() {
        // Each names a directory and a file under the audio root, so a traversal in either would
        // point outside it. The bundled ids ("ar.alafasy") and releases ("audio-ar.alafasy-v1")
        // pass; anything with a slash or the two directory names does not.
        assertFailsWith<IllegalArgumentException> {
            ManifestJson.parse(TWO_RECITERS.replace("\"id\": \"ar.husary\"", "\"id\": \"../../ar.husary\""))
        }
        assertFailsWith<IllegalArgumentException> {
            ManifestJson.parse(TWO_RECITERS.replace("audio-ar.husary-v1", "releases/../audio"))
        }
        assertTrue(RecitationManifest.isPlainSegment("ar.alafasy"))
        assertTrue(RecitationManifest.isPlainSegment("audio-ar.alafasy-v1"))
        assertTrue(!RecitationManifest.isPlainSegment(".."))
        assertTrue(!RecitationManifest.isPlainSegment("a/b"))
        assertTrue(!RecitationManifest.isPlainSegment(""))
        // The unmodified fixture still parses, so the checks are not rejecting the real shape.
        assertEquals(2, ManifestJson.parse(TWO_RECITERS).reciters.size)
    }

    @Test
    fun parsesTheCatalogueAndRoundTripsIt() {
        val manifest = ManifestJson.parse(TWO_RECITERS)
        assertEquals(1, manifest.schema)
        assertEquals("2026-09-12T18:04:00Z", manifest.generated)
        assertEquals(listOf("ar.alafasy", "ar.husary"), manifest.reciters.map { it.id })

        val alafasy = manifest.reciter("ar.alafasy")!!
        assertEquals("Mishary Rashid Alafasy", alafasy.nameEn)
        assertEquals("مشاري راشد العفاسي", alafasy.nameAr)
        assertEquals("murattal", alafasy.style)
        assertEquals(64, alafasy.kbps)
        assertEquals(300, alafasy.gapMs)
        assertEquals(ReciterHue.AMBER, alafasy.hueColors)
        assertNull(alafasy.photo)
        assertEquals(903001088L, alafasy.totalBytes)
        assertEquals(60817408L, alafasy.surah(2)!!.bytes)
        assertEquals("bb22", alafasy.surah(2)!!.sha256)
        assertNull(manifest.reciter("ar.nobody"))

        // Encoded and read back is the same catalogue, which is what lets a fetched manifest be
        // cached as bytes and trusted on the next start.
        assertEquals(manifest, ManifestJson.parse(ManifestJson.encode(manifest)))
    }

    @Test
    fun ignoresKeysThisBuildDoesNotKnow() {
        val withExtras = TWO_RECITERS
            .replace("\"schema\": 1,", "\"schema\": 1, \"mirrors\": [\"https://example.invalid\"],")
            .replace("\"style\": \"murattal\",", "\"style\": \"murattal\", \"timings\": \"word-v2\",")
        val manifest = ManifestJson.parse(withExtras)
        assertEquals(2, manifest.reciters.size)
        assertEquals(300, manifest.reciter("ar.alafasy")!!.gapMs)
    }

    @Test
    fun rejectsANewerSchema() {
        val newer = TWO_RECITERS.replace("\"schema\": 1,", "\"schema\": 2,")
        val thrown = assertFailsWith<UnsupportedManifest> { ManifestJson.parse(newer) }
        assertEquals(2, thrown.schema)
    }

    @Test
    fun assetUrlPadsTheSurahToThreeDigits() {
        val manifest = ManifestJson.parse(TWO_RECITERS)
        val alafasy = manifest.reciter("ar.alafasy")!!
        val base = "https://github.com/MohamedAbulgasem/Taqwa-data/releases/download/audio-ar.alafasy-v1/"
        assertEquals(base + "ar.alafasy-001.taqa", manifest.assetUrl(alafasy, 1))
        assertEquals(base + "ar.alafasy-012.taqa", manifest.assetUrl(alafasy, 12))
        assertEquals(base + "ar.alafasy-114.taqa", manifest.assetUrl(alafasy, 114))
        assertEquals(base + "ar.alafasy-002.taqa", manifest.assetUrl("ar.alafasy", 2))
        assertNull(manifest.assetUrl("ar.nobody", 2))
    }

    @Test
    fun everyLaunchHueHasColoursAndTheTenAreDistinct() {
        assertEquals(10, Reciters.LAUNCH.size)
        assertEquals(10, Reciters.IDS.toSet().size)
        val hues = Reciters.LAUNCH.map { it.hue }
        assertEquals(10, hues.toSet().size)
        for (hue in hues) {
            assertEquals(hue, ReciterHue.of(hue.name.lowercase()))
            assertTrue(hue.disc(dark = false) != 0L && hue.glyph(dark = false) != 0L, "$hue light")
            assertTrue(hue.disc(dark = true) != 0L && hue.glyph(dark = true) != 0L, "$hue dark")
        }
    }

    @Test
    fun anUnknownHueFallsBackToAmber() {
        assertEquals(ReciterHue.AMBER, ReciterHue.of("chartreuse"))
        assertEquals(ReciterHue.MOSS, ReciterHue.of("MOSS"))
    }

    @Test
    fun theTenMonogramsAreTheGivenNamesInitial() {
        assertEquals("م", monogramInitial("مشاري راشد العفاسي"))
        assertEquals("ع", monogramInitial("عبد الباسط عبد الصمد"))
        assertEquals("م", monogramInitial("ماهر المعيقلي"))
        assertEquals("م", monogramInitial("محمود خليل الحصري"))
        assertEquals("م", monogramInitial("محمد صديق المنشاوي"))
        assertEquals("ع", monogramInitial("عبد الرحمن السديس"))
        assertEquals("س", monogramInitial("سعود الشريم"))
        assertEquals("أ", monogramInitial("أبو بكر الشاطري"))
        assertEquals("ع", monogramInitial("علي الحذيفي"))
        assertEquals("أ", monogramInitial("أحمد العجمي"))
    }

    @Test
    fun aNameOutsideTheTableFallsBackToTheFirstWord() {
        // A reciter added by a later manifest: no table entry, so the rule answers.
        assertEquals("س", monogramInitial("سعد الغامدي"))
        // The article is not a name.
        assertEquals("غ", monogramInitial("الغامدي"))
        assertEquals("", monogramInitial("   "))
    }

    @Test
    fun theManifestReciterCarriesItsOwnMonogram() {
        val manifest = ManifestJson.parse(TWO_RECITERS)
        assertEquals("م", manifest.reciter("ar.alafasy")!!.monogram)
        assertEquals("م", manifest.reciter("ar.husary")!!.monogram)
    }

    // ── The bundled catalogue as the trust anchor (security review C1, 26 October) ────────

    private val shipped = ManifestJson.parse(TWO_RECITERS)

    @Test
    fun aCatalogueIsFaithfulToItself() {
        assertTrue(shipped.isFaithfulTo(shipped))
    }

    @Test
    fun aShippedReleaseWhoseHashChangedIsRefused() {
        assertFalse(ManifestJson.parse(TWO_RECITERS.replace("\"bb22\"", "\"ee55\"")).isFaithfulTo(shipped))
    }

    @Test
    fun aShippedReleaseWhoseSizeChangedIsRefused() {
        assertFalse(ManifestJson.parse(TWO_RECITERS.replace("\"bytes\": 22222", "\"bytes\": 22223")).isFaithfulTo(shipped))
    }

    @Test
    fun aSurahAShippedReleaseNeverHadIsRefused() {
        // The shipped Husary release lists only 114 here, so a 113 under the same name is a file
        // the build never vouched for.
        val husary = shipped.reciter("ar.husary")!!
        val grown = shipped.copy(
            reciters = listOf(shipped.reciters[0], husary.copy(surahs = husary.surahs + SurahAsset(113, 999, "dd44"))),
        )
        assertFalse(grown.isFaithfulTo(shipped))
    }

    @Test
    fun aBaseOtherThanTheShippedOneIsRefused() {
        val moved = TWO_RECITERS.replace(
            "https://github.com/MohamedAbulgasem/Taqwa-data/releases/download/",
            "https://example.invalid/releases/download/",
        )
        assertFalse(ManifestJson.parse(moved).isFaithfulTo(shipped))
    }

    @Test
    fun aReciterMayBeWithdrawnAddedOrGivenANewRelease() {
        val alafasy = shipped.reciter("ar.alafasy")!!
        val husary = shipped.reciter("ar.husary")!!
        // Withdrawn: nothing the build shipped is contradicted by its absence.
        assertTrue(shipped.copy(reciters = listOf(alafasy)).isFaithfulTo(shipped))
        // Added: a voice the build never knew carries whatever its release holds.
        val added = husary.copy(id = "ar.minshawi", release = "audio-ar.minshawi-v1", surahs = listOf(SurahAsset(1, 5, "ff66")))
        assertTrue(shipped.copy(reciters = shipped.reciters + added).isFaithfulTo(shipped))
        // Re-released: new audio for a shipped voice arrives under a new release name.
        val v2 = alafasy.copy(release = "audio-ar.alafasy-v2", surahs = listOf(SurahAsset(2, 999, "dd44")))
        assertTrue(shipped.copy(reciters = listOf(v2, husary)).isFaithfulTo(shipped))
    }

    @Test
    fun aHashWrittenInCapitalsIsTheSameHash() {
        // The commit compares hashes without regard to case, so the anchor does too.
        assertTrue(ManifestJson.parse(TWO_RECITERS.replace("\"bb22\"", "\"BB22\"")).isFaithfulTo(shipped))
    }
}
