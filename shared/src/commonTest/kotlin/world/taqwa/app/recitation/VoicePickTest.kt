package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoicePickTest {

    private fun voice(id: String, language: String, country: String = "", offline: Boolean = true, installed: Boolean = true, quality: Int = 300) =
        VoiceCandidate(id, language, country, offline, installed, quality)

    @Test
    fun `only an offline installed voice of the language counts`() {
        val voices = listOf(
            voice("fr-net", "fr", "FR", offline = false),
            voice("fr-missing", "fr", "FR", installed = false),
            voice("en-local", "en", "US"),
        )
        assertNull(VoicePick.best("fr", voices))
        assertTrue(VoicePick.downloadable("fr", voices))
        assertEquals("en-local", VoicePick.best("en", voices)?.id)
        assertFalse(VoicePick.downloadable("ur", voices))
        // A network-only voice has nothing to download.
        assertFalse(VoicePick.downloadable("de", listOf(voice("de-net", "de", "DE", offline = false))))
    }

    @Test
    fun `java's legacy indonesian code is indonesian`() {
        assertEquals("id-local", VoicePick.best("id", listOf(voice("id-local", "in", "ID")))?.id)
        assertEquals("id", VoicePick.normalize("ind"))
        assertEquals("ar", VoicePick.normalize("ar-001"))
    }

    @Test
    fun `quality wins and then the preferred country`() {
        val bengali = listOf(voice("bn-in", "bn", "IN"), voice("bn-bd", "bn", "BD"))
        assertEquals("bn-bd", VoicePick.best("bn", bengali)?.id)
        val english = listOf(voice("en-us", "en", "US", quality = 300), voice("en-gb-premium", "en", "GB", quality = 500))
        assertEquals("en-gb-premium", VoicePick.best("en", english)?.id)
    }

    @Test
    fun `between equals a voice named local wins before the id decides`() {
        // Google's alias sorts first by id alone ('U' before 'u'); its local twin must win the tie.
        val google = listOf(voice("en-US-language", "en", "US"), voice("en-us-x-tpf-LOCAL", "en", "US"))
        assertEquals("en-us-x-tpf-LOCAL", VoicePick.best("en", google)?.id)
        // Two local voices: the id decides between them.
        val twoLocal = google + voice("en-us-x-iol-local", "en", "US")
        assertEquals("en-us-x-iol-local", VoicePick.best("en", twoLocal)?.id)
        // Quality and the preferred country still come first.
        val better = listOf(voice("en-US-language", "en", "US", quality = 400), voice("en-us-x-iol-local", "en", "US"))
        assertEquals("en-US-language", VoicePick.best("en", better)?.id)
        val preferred = listOf(voice("en-US-language", "en", "US"), voice("en-gb-x-gba-local", "en", "GB"))
        assertEquals("en-US-language", VoicePick.best("en", preferred)?.id)
    }
}
