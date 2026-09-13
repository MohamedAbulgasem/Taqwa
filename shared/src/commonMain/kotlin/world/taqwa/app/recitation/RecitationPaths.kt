package world.taqwa.app.recitation

import okio.FileSystem
import okio.Path

/**
 * Where downloaded recitation lives (spec 3a §8): app-private storage, so it goes when the app
 * goes, and out of the way of anything the user can see.
 *
 * ```
 * <files>/quran/manifest.json          the catalogue, refreshed from the network
 * <files>/quran/audio/<reciter>/003.taqa      a surah the reader owns
 * <files>/quran/audio/<reciter>/003.taqa.part a surah still arriving
 * ```
 *
 * The `.part` sibling is what makes a resumed download honest: a file only takes its real name
 * once its SHA-256 has matched the manifest, so nothing that is not verified can ever be played.
 *
 * A plain class over an injected [files] directory rather than a platform singleton, so the tests
 * can point it at a `FakeFileSystem`; the platform's own directory comes from
 * [recitationFilesDirectory].
 */
class RecitationPaths(val files: Path) {

    /** `<files>/quran/audio`. */
    fun root(): Path = files / QURAN / AUDIO

    fun reciterDir(reciterId: String): Path = root() / reciterId

    /** `<files>/quran/audio/<reciter>/<nnn>.taqa`, the surah number always three digits. */
    fun surahFile(reciterId: String, surah: Int): Path =
        reciterDir(reciterId) / (surah.toString().padStart(3, '0') + EXTENSION)

    /** The in-flight sibling of [surahFile]. */
    fun partFile(reciterId: String, surah: Int): Path =
        reciterDir(reciterId) / (surah.toString().padStart(3, '0') + EXTENSION + PART)

    /** `<files>/quran/manifest.json` — the cached catalogue, absent until one has been fetched. */
    fun manifestCache(): Path = files / QURAN / MANIFEST

    /** The surah a `.taqa` file name names, or null for a name this scheme did not write. */
    fun surahOf(file: Path): Int? {
        val name = file.name
        if (!name.endsWith(EXTENSION)) return null
        return name.removeSuffix(EXTENSION).toIntOrNull()
    }

    /** Creates the audio root and the reciter's own directory; both are idempotent. */
    fun prepare(fs: FileSystem, reciterId: String? = null) {
        fs.createDirectories(if (reciterId == null) root() else reciterDir(reciterId))
    }

    private companion object {
        const val QURAN = "quran"
        const val AUDIO = "audio"
        const val MANIFEST = "manifest.json"
        const val EXTENSION = ".taqa"
        const val PART = ".part"
    }
}

/**
 * The app-private directory recitation hangs off, created if it is not there and — on iOS —
 * excluded from iCloud backup, because a gigabyte of audio that can be fetched again should never
 * be in anybody's backup.
 */
expect fun recitationFilesDirectory(): Path

/** The paths this app actually uses, over the platform's own directory. */
fun createRecitationPaths(): RecitationPaths = RecitationPaths(recitationFilesDirectory())
