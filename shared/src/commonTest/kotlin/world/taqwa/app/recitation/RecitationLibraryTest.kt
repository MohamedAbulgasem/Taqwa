package world.taqwa.app.recitation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import world.taqwa.app.settings.SettingsKeys
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecitationLibraryTest {

    private val fs = FakeFileSystem()
    private val paths = RecitationPaths("/files".toPath())
    private fun dataStore() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-recitation-${Random.nextULong()}.preferences_pb".toPath()
    }

    private fun library(store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> = dataStore()) =
        RecitationLibrary(paths, store, fs)

    private val ayahs = listOf(ByteArray(64) { 1 }, ByteArray(96) { 2 })
    private val surah = buildTaqa(surah = 112, ayahs = ayahs)

    /** Puts a finished download in place, as the downloader would leave it. */
    private fun writePart(reciterId: String, n: Int, content: ByteArray = surah) {
        fs.createDirectories(paths.reciterDir(reciterId))
        fs.write(paths.partFile(reciterId, n)) { write(content) }
    }

    private fun writeSurah(reciterId: String, n: Int, content: ByteArray = surah) {
        fs.createDirectories(paths.reciterDir(reciterId))
        fs.write(paths.surahFile(reciterId, n)) { write(content) }
    }

    private fun hashOf(content: ByteArray): String {
        val scratch = "/scratch.bin".toPath()
        fs.write(scratch) { write(content) }
        val hash = TaqaFile(scratch, fs).sha256()
        fs.delete(scratch)
        return hash
    }

    @Test
    fun pathsFollowTheLayoutTheSpecDescribes() {
        assertEquals("/files/quran/audio".toPath(), paths.root())
        assertEquals("/files/quran/audio/ar.alafasy/002.taqa".toPath(), paths.surahFile("ar.alafasy", 2))
        assertEquals("/files/quran/audio/ar.alafasy/114.taqa".toPath(), paths.surahFile("ar.alafasy", 114))
        assertEquals("/files/quran/audio/ar.alafasy/002.taqa.part".toPath(), paths.partFile("ar.alafasy", 2))
        assertEquals("/files/quran/manifest.json".toPath(), paths.manifestCache())
        assertEquals(2, paths.surahOf(paths.surahFile("ar.alafasy", 2)))
        assertEquals(null, paths.surahOf(paths.partFile("ar.alafasy", 2)))
    }

    @Test
    fun commitVerifiesTheHashBeforeTheSurahIsOwned() = runTest {
        val library = library()
        writePart("ar.alafasy", 112)
        assertTrue(library.commit("ar.alafasy", 112, hashOf(surah)))
        assertTrue(fs.exists(paths.surahFile("ar.alafasy", 112)))
        assertFalse(fs.exists(paths.partFile("ar.alafasy", 112)))
        assertEquals(setOf(112), library.downloaded("ar.alafasy").first())
        assertTrue(library.isDownloaded("ar.alafasy", 112))
        assertFalse(library.isDownloaded("ar.husary", 112))
        // The hash is compared without regard to case; the manifest publishes lower-case hex.
        fs.checkNoOpenFiles()
    }

    @Test
    fun commitRefusesAndDeletesAPartThatHashesWrong() = runTest {
        val library = library()
        writePart("ar.alafasy", 112)
        assertFalse(library.commit("ar.alafasy", 112, "0".repeat(64)))
        // Deleted rather than left to be resumed: a resumed download appends and every byte after
        // the damage would be wrong too.
        assertFalse(fs.exists(paths.partFile("ar.alafasy", 112)))
        assertFalse(fs.exists(paths.surahFile("ar.alafasy", 112)))
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
    }

    @Test
    fun commitWithNothingToCommitIsFalse() = runTest {
        assertFalse(library().commit("ar.alafasy", 1, "whatever"))
    }

    @Test
    fun deleteRemovesTheFileAndTheRegistryEntry() = runTest {
        val library = library()
        writePart("ar.alafasy", 112)
        library.commit("ar.alafasy", 112, hashOf(surah))
        library.delete("ar.alafasy", 112)
        assertFalse(fs.exists(paths.surahFile("ar.alafasy", 112)))
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
        // Twice is not an error; the Downloads screen can be tapped twice.
        library.delete("ar.alafasy", 112)
    }

    @Test
    fun deleteReciterTakesEverythingOfThatVoice() = runTest {
        val library = library()
        writePart("ar.alafasy", 112)
        library.commit("ar.alafasy", 112, hashOf(surah))
        writePart("ar.husary", 112)
        library.commit("ar.husary", 112, hashOf(surah))
        library.deleteReciter("ar.alafasy")
        assertFalse(fs.exists(paths.reciterDir("ar.alafasy")))
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
        assertEquals(setOf(112), library.downloaded("ar.husary").first())
    }

    @Test
    fun bytesUsedCountsWhatIsActuallyOnDisk() = runTest {
        val library = library()
        assertEquals(0L, library.bytesUsed("ar.alafasy"))
        assertEquals(0L, library.bytesUsedTotal())
        writeSurah("ar.alafasy", 112)
        writeSurah("ar.alafasy", 113)
        writeSurah("ar.husary", 112)
        val one = surah.size.toLong()
        assertEquals(2 * one, library.bytesUsed("ar.alafasy"))
        assertEquals(3 * one, library.bytesUsedTotal())
        // A cancelled download occupies the disk whether the registry knows it or not.
        writePart("ar.alafasy", 2, ByteArray(500))
        assertEquals(2 * one + 500, library.bytesUsed("ar.alafasy"))
    }

    @Test
    fun reconcileDropsARegistryEntryWhoseFileHasGone() = runTest {
        val store = dataStore()
        val library = library(store)
        writePart("ar.alafasy", 112)
        library.commit("ar.alafasy", 112, hashOf(surah))
        fs.delete(paths.surahFile("ar.alafasy", 112))
        library.reconcile()
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
        // The key itself is removed rather than left holding an empty set.
        assertFalse(store.data.first().asMap().keys.any {
            it.name == SettingsKeys.RECITATION_DOWNLOADED_PREFIX + "ar.alafasy"
        })
    }

    @Test
    fun reconcileAdoptsAWholeFileTheRegistryNeverHeardOf() = runTest {
        val library = library()
        writeSurah("ar.alafasy", 112)
        writeSurah("ar.alafasy", 114)
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
        library.reconcile()
        assertEquals(setOf(112, 114), library.downloaded("ar.alafasy").first())
    }

    @Test
    fun reconcileLeavesHalfADownloadWhereItIs() = runTest {
        val library = library()
        writeSurah("ar.alafasy", 112, surah.copyOfRange(0, surah.size - 8))
        writePart("ar.alafasy", 113)
        library.reconcile()
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
        // Neither is deleted — the downloader may still resume or overwrite them.
        assertTrue(fs.exists(paths.surahFile("ar.alafasy", 112)))
        assertTrue(fs.exists(paths.partFile("ar.alafasy", 113)))
    }

    @Test
    fun reconcileFixesBothDirectionsAtOnceAndIsIdempotent() = runTest {
        val store = dataStore()
        val library = library(store)
        // The registry claims two surahs; only one is there, and a third is there unclaimed.
        store.edit {
            it[SettingsKeys.recitationDownloadedKey("ar.alafasy")] = setOf("1", "2", "not a surah")
        }
        writeSurah("ar.alafasy", 2)
        writeSurah("ar.alafasy", 36)
        library.reconcile()
        assertEquals(setOf(2, 36), library.downloaded("ar.alafasy").first())
        library.reconcile()
        assertEquals(setOf(2, 36), library.downloaded("ar.alafasy").first())
    }

    @Test
    fun reconcileWithNothingAnywhereIsHarmless() = runTest {
        val library = library()
        library.reconcile()
        assertEquals(emptySet(), library.downloaded("ar.alafasy").first())
        assertEquals(0L, library.bytesUsedTotal())
    }
}
