package world.taqwa.app.recitation

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManifestProviderTest {

    private val fs = FakeFileSystem()
    private val paths = RecitationPaths("/files".toPath())
    private val bundled = TWO_RECITERS
    private fun provider(bundledText: String = bundled) =
        ManifestProvider(paths, fs) { bundledText.encodeToByteArray() }

    private fun writeCache(text: String) {
        fs.createDirectories(paths.manifestCache().parent!!)
        fs.write(paths.manifestCache()) { write(text.encodeToByteArray()) }
    }

    @Test
    fun fallsBackToTheBundledCatalogueWhenNothingIsCached() = runTest {
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider().current().reciters.map { it.id })
    }

    @Test
    fun prefersTheCachedCatalogue() = runTest {
        writeCache(bundled.replace("\"ar.husary\"", "\"ar.minshawi\""))
        assertEquals(listOf("ar.alafasy", "ar.minshawi"), provider().current().reciters.map { it.id })
    }

    @Test
    fun prefersTheBundledCatalogueWhenTheBuildIsNewerThanTheLastRefresh() = runTest {
        // The ordinary shape of an app update that adds a reciter: the cache still holds what
        // yesterday's refresh fetched, and the build ships the catalogue that names the new voice.
        writeCache(
            bundled
                .replace("\"2026-09-12T18:04:00Z\"", "\"2026-09-11T09:00:00Z\"")
                .replace("\"ar.husary\"", "\"ar.minshawi\""),
        )
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider().current().reciters.map { it.id })
    }

    @Test
    fun keepsAFetchedCatalogueThatIsNewerThanTheBuild() = runTest {
        writeCache(
            bundled
                .replace("\"2026-09-12T18:04:00Z\"", "\"2026-09-13T09:00:00Z\"")
                .replace("\"ar.husary\"", "\"ar.minshawi\""),
        )
        assertEquals(listOf("ar.alafasy", "ar.minshawi"), provider().current().reciters.map { it.id })
    }

    @Test
    fun aCacheWithNoUsableTimestampStaysInForce() = runTest {
        writeCache(
            bundled
                .replace("\"2026-09-12T18:04:00Z\"", "\"whenever\"")
                .replace("\"ar.husary\"", "\"ar.minshawi\""),
        )
        assertEquals(listOf("ar.alafasy", "ar.minshawi"), provider().current().reciters.map { it.id })
    }

    @Test
    fun ignoresACacheItCannotRead() = runTest {
        writeCache("{ not json")
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider().current().reciters.map { it.id })
    }

    @Test
    fun ignoresACacheFromANewerSchema() = runTest {
        writeCache(bundled.replace("\"schema\": 1,", "\"schema\": 7,"))
        assertEquals(1, provider().current().schema)
    }

    @Test
    fun storeValidatesBeforeItOverwrites() = runTest {
        val provider = provider()
        assertFalse(provider.store("half a downlo".encodeToByteArray()))
        assertFalse(fs.exists(paths.manifestCache()))
        assertFalse(provider.store(bundled.replace("\"schema\": 1,", "\"schema\": 7,").encodeToByteArray()))
        assertFalse(fs.exists(paths.manifestCache()))

        val fresh = bundled.replace("\"ar.husary\"", "\"ar.minshawi\"")
        assertTrue(provider.store(fresh.encodeToByteArray()))
        assertTrue(fs.exists(paths.manifestCache()))
        assertEquals(listOf("ar.alafasy", "ar.minshawi"), provider.current().reciters.map { it.id })
        // And it survives the process: a new provider over the same disk reads the same thing.
        assertEquals(listOf("ar.alafasy", "ar.minshawi"), provider().current().reciters.map { it.id })
    }

    @Test
    fun reciterAnswersOnlyOnceACatalogueIsInForce() = runTest {
        val provider = provider()
        assertNull(provider.reciter("ar.alafasy"))
        provider.current()
        assertEquals("Mishary Rashid Alafasy", provider.reciter("ar.alafasy")?.nameEn)
        assertNull(provider.reciter("ar.nobody"))
    }
}
