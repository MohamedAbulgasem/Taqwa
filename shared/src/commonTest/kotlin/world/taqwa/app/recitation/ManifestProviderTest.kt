package world.taqwa.app.recitation

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TWO_RECITERS_BASE = "https://github.com/MohamedAbulgasem/Taqwa-data/releases/download/"

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
    fun aCacheWithNoUsableTimestampIsNeverNewer() = runTest {
        // It used to stay in force, which let a manifest that names no time outrank the build for
        // good (security review C1).
        writeCache(
            bundled
                .replace("\"2026-09-12T18:04:00Z\"", "\"whenever\"")
                .replace("\"ar.husary\"", "\"ar.minshawi\""),
        )
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider().current().reciters.map { it.id })
    }

    @Test
    fun aCacheThatRewritesAShippedReleaseIsIgnored() = runTest {
        // A cache written before this rule existed, newer than the build and with one hash swapped.
        writeCache(
            bundled
                .replace("\"2026-09-12T18:04:00Z\"", "\"2026-09-13T09:00:00Z\"")
                .replace("\"bb22\"", "\"ee55\""),
        )
        assertEquals("bb22", provider().current().reciter("ar.alafasy")!!.surah(2)!!.sha256)
    }

    @Test
    fun aCacheWithAnotherBaseIsIgnored() = runTest {
        writeCache(
            bundled
                .replace("\"2026-09-12T18:04:00Z\"", "\"2026-09-13T09:00:00Z\"")
                .replace("https://github.com/MohamedAbulgasem/Taqwa-data/", "https://example.invalid/"),
        )
        assertEquals(TWO_RECITERS_BASE, provider().current().base)
    }

    @Test
    fun storeRefusesACatalogueThatRewritesAShippedRelease() = runTest {
        val provider = provider()
        assertFalse(provider.store(bundled.replace("\"bb22\"", "\"ee55\"").encodeToByteArray()))
        assertFalse(provider.store(bundled.replace("\"bytes\": 22222", "\"bytes\": 22223").encodeToByteArray()))
        assertFalse(fs.exists(paths.manifestCache()))
        assertEquals("bb22", provider.current().reciter("ar.alafasy")!!.surah(2)!!.sha256)
        assertEquals(22222L, provider.current().reciter("ar.husary")!!.surah(114)!!.bytes)
    }

    @Test
    fun storeRefusesACatalogueWithAnotherBase() = runTest {
        val provider = provider()
        val moved = bundled.replace("https://github.com/MohamedAbulgasem/Taqwa-data/", "https://example.invalid/")
        assertFalse(provider.store(moved.encodeToByteArray()))
        assertFalse(fs.exists(paths.manifestCache()))
        assertEquals(TWO_RECITERS_BASE, provider.current().base)
    }

    @Test
    fun storeRefusesACatalogueTheNextStartWouldNotChoose() = runTest {
        // What is put in force now is what load() would choose after a restart: never a manifest
        // that names no time, and never one older than the build's own.
        val provider = provider()
        val unnamed = bundled.replace("\"2026-09-12T18:04:00Z\"", "\"whenever\"").replace("\"ar.husary\"", "\"ar.minshawi\"")
        val older = bundled.replace("\"2026-09-12T18:04:00Z\"", "\"2026-09-11T09:00:00Z\"").replace("\"ar.husary\"", "\"ar.minshawi\"")
        assertFalse(provider.store(unnamed.encodeToByteArray()))
        assertFalse(provider.store(older.encodeToByteArray()))
        assertFalse(fs.exists(paths.manifestCache()))
        assertEquals(listOf("ar.alafasy", "ar.husary"), provider.current().reciters.map { it.id })
    }

    @Test
    fun storeAcceptsAWithdrawnOrAddedReciterAndANewRelease() = runTest {
        val provider = provider()
        val withdrawn = ManifestJson.parse(bundled).let { it.copy(reciters = it.reciters.take(1)) }
        assertTrue(provider.store(ManifestJson.encode(withdrawn).encodeToByteArray()))
        assertEquals(listOf("ar.alafasy"), provider.current().reciters.map { it.id })

        val rereleased = bundled.replace("audio-ar.alafasy-v1", "audio-ar.alafasy-v2").replace("\"bb22\"", "\"ee55\"")
        assertTrue(provider.store(rereleased.encodeToByteArray()))
        assertEquals("ee55", provider.current().reciter("ar.alafasy")!!.surah(2)!!.sha256)
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
