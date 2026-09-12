package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.FileSystem
import okio.IOException
import okio.Path
import world.taqwa.app.settings.SettingsKeys

/** What the reader listens in, and whether a download may leave Wi-Fi (spec 3a §12.1, §12.6). */
data class RecitationSettings(
    val reciterId: String = RecitationManifest.DEFAULT_RECITER,
    val downloadOnMobileData: Boolean = false,
)

/**
 * The surahs the reader owns: a registry in DataStore beside the bookmarks, over files under
 * [RecitationPaths]. Two things are kept apart on purpose —
 *
 * * the **registry** is what the UI reads, so the picker's "12 of 114" and the header button's
 *   state cost nothing and never scan the disk (spec §7);
 * * the **files** are the truth, and [reconcile] is the one place the two are made to agree.
 *
 * A surah enters the registry through [commit] alone, which means it has been hashed against the
 * manifest first: nothing unverified can ever be played.
 */
class RecitationLibrary(
    private val paths: RecitationPaths,
    private val store: DataStore<Preferences>,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {

    /** The surahs of [reciterId] the reader owns, smallest first. */
    fun downloaded(reciterId: String): Flow<Set<Int>> = store.data
        .map { prefs -> decode(prefs[SettingsKeys.recitationDownloadedKey(reciterId)]) }
        .distinctUntilChanged()

    suspend fun isDownloaded(reciterId: String, surah: Int): Boolean =
        surah in downloaded(reciterId).first()

    /** Where the surah lives once it is owned. */
    fun fileFor(reciterId: String, surah: Int): Path = paths.surahFile(reciterId, surah)

    /** Where a download in flight is written until it has been verified. */
    fun partFor(reciterId: String, surah: Int): Path = paths.partFile(reciterId, surah)

    /**
     * Turns a finished `.part` into a surah the reader owns: hashes it, and only if the hash is
     * the one the manifest publishes does it take the real name and enter the registry.
     *
     * A mismatch **deletes** the part rather than leaving it to be resumed. A resumed download
     * appends, so a file that hashes wrong is wrong at some offset nobody knows, and every further
     * byte appended to it would be wrong too; the honest recovery is to fetch it again.
     *
     * @return true if the surah is now owned.
     */
    suspend fun commit(reciterId: String, surah: Int, expectedSha256: String): Boolean {
        val part = partFor(reciterId, surah)
        if (!fs.exists(part)) return false
        val actual = try {
            TaqaFile(part, fs).sha256()
        } catch (e: IOException) {
            return false
        }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            runCatching { fs.delete(part) }
            return false
        }
        val target = fileFor(reciterId, surah)
        paths.prepare(fs, reciterId)
        // `atomicMove` refuses to overwrite on some file systems; the old file is this same surah
        // by definition, and it has already been replaced by a verified one.
        runCatching { fs.delete(target) }
        fs.atomicMove(part, target)
        record(reciterId) { it + surah }
        return true
    }

    /** Forgets one surah, file and registry entry both. */
    suspend fun delete(reciterId: String, surah: Int) {
        runCatching { fs.delete(fileFor(reciterId, surah)) }
        runCatching { fs.delete(partFor(reciterId, surah)) }
        record(reciterId) { it - surah }
    }

    /** Forgets everything of one reciter — the Downloads screen's per-reciter delete. */
    suspend fun deleteReciter(reciterId: String) {
        runCatching { fs.deleteRecursively(paths.reciterDir(reciterId)) }
        store.edit { it.remove(SettingsKeys.recitationDownloadedKey(reciterId)) }
    }

    /**
     * What this reciter's downloads occupy, counted off the disk rather than off the registry:
     * the Downloads screen states a number the user could check in system settings, and a stray
     * `.part` from a cancelled download takes up room whether the registry knows it or not.
     */
    suspend fun bytesUsed(reciterId: String): Long = sizeOf(paths.reciterDir(reciterId))

    suspend fun bytesUsedTotal(): Long = sizeOf(paths.root())

    /**
     * Makes the registry and the disk agree. Called once at app start, because either can move
     * without the other: the system may clear app storage, a download may have been committed as
     * the process died, and a reinstall over the same files is possible on Android.
     *
     * Both directions, and both are needed —
     *
     * * a registry entry with no file is dropped, or the reader taps play on nothing;
     * * a file that stands up on its own ([TaqaFile.isWhole]) but is not in the registry is
     *   adopted, or the reader is asked to download 58 MB they already have.
     *
     * A half-written file is neither: it fails `isWhole`, is not adopted, and is left where it is
     * for the downloader to resume or overwrite.
     */
    suspend fun reconcile() {
        val onDisk = scan()
        val registered = store.data.first().asMap().keys
            .map { it.name }
            .filter { it.startsWith(SettingsKeys.RECITATION_DOWNLOADED_PREFIX) }
            .map { it.removePrefix(SettingsKeys.RECITATION_DOWNLOADED_PREFIX) }
        store.edit { prefs ->
            for (reciterId in (registered + onDisk.keys).distinct()) {
                val key = SettingsKeys.recitationDownloadedKey(reciterId)
                val was = decode(prefs[key])
                val now = onDisk[reciterId].orEmpty()
                if (was == now) continue
                if (now.isEmpty()) prefs.remove(key) else prefs[key] = encode(now)
            }
        }
    }

    /** Every whole `.taqa` under the audio root, by reciter. */
    private fun scan(): Map<String, Set<Int>> {
        val root = paths.root()
        if (!fs.exists(root)) return emptyMap()
        val byReciter = mutableMapOf<String, MutableSet<Int>>()
        for (dir in fs.list(root)) {
            if (fs.metadataOrNull(dir)?.isDirectory != true) continue
            val surahs = mutableSetOf<Int>()
            for (file in fs.list(dir)) {
                val surah = paths.surahOf(file) ?: continue
                if (TaqaFile(file, fs).isWhole()) surahs += surah
            }
            if (surahs.isNotEmpty()) byReciter[dir.name] = surahs
        }
        return byReciter
    }

    private fun sizeOf(dir: Path): Long {
        if (!fs.exists(dir)) return 0L
        var total = 0L
        for (entry in fs.list(dir)) {
            val meta = fs.metadataOrNull(entry) ?: continue
            total += if (meta.isDirectory) sizeOf(entry) else (meta.size ?: 0L)
        }
        return total
    }

    private suspend fun record(reciterId: String, change: (Set<Int>) -> Set<Int>) {
        store.edit { prefs ->
            val key = SettingsKeys.recitationDownloadedKey(reciterId)
            val next = change(decode(prefs[key]))
            if (next.isEmpty()) prefs.remove(key) else prefs[key] = encode(next)
        }
    }

    private fun encode(surahs: Set<Int>): Set<String> = surahs.map { it.toString() }.toSet()

    /** An entry that is not a surah number is dropped, never thrown on: this is user data. */
    private fun decode(raw: Set<String>?): Set<Int> =
        raw.orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
}
