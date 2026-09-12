package world.taqwa.app.recitation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import world.taqwa.app.resources.Res

/**
 * Which catalogue the app is working from (spec 3a §4): the copy fetched from the data repository
 * if there is one and it still reads, otherwise the copy bundled with the build.
 *
 * The bundled copy is what makes the picker work on a device that has never been online, and the
 * fetched copy is what lets a reciter be added — or withdrawn, if an estate ever asks — without an
 * app update. A cached file that does not parse, or that was written for a newer schema, is
 * ignored rather than repaired: the bundled copy is always a valid answer.
 */
class ManifestProvider(
    private val paths: RecitationPaths,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private val bundled: suspend () -> ByteArray,
) {
    private val lock = Mutex()
    private var cached: RecitationManifest? = null

    /** The catalogue in force, read once per process and then held. */
    suspend fun current(): RecitationManifest {
        cached?.let { return it }
        return lock.withLock {
            cached ?: load().also { cached = it }
        }
    }

    /**
     * Replaces the cached catalogue with [bytes], which are **parsed before anything is written**:
     * a truncated download or a manifest from a newer schema must not be able to take the app's
     * catalogue away, and the file on disk is only ever a manifest that this build could read.
     *
     * @return true if the bytes were a catalogue and are now the one in force.
     */
    suspend fun store(bytes: ByteArray): Boolean {
        val parsed = try {
            ManifestJson.parse(bytes.decodeToString())
        } catch (e: UnsupportedManifest) {
            return false
        } catch (e: Exception) {
            return false
        }
        return lock.withLock {
            try {
                fs.createDirectories(paths.manifestCache().parent!!)
                fs.write(paths.manifestCache()) { write(bytes) }
                cached = parsed
                true
            } catch (e: okio.IOException) {
                false
            }
        }
    }

    /**
     * The reciter by id from the catalogue already in force — null before [current] has ever been
     * called, which is why every screen collects the manifest rather than asking this first. It is
     * here for the places that already have one in hand and only want a name: a notification being
     * built, a player restoring its queue.
     */
    fun reciter(id: String): Reciter? = cached?.reciter(id)

    private suspend fun load(): RecitationManifest {
        val cache = paths.manifestCache()
        val fromDisk = try {
            if (fs.exists(cache)) fs.read(cache) { readByteArray() } else null
        } catch (e: okio.IOException) {
            null
        }
        if (fromDisk != null) {
            try {
                return ManifestJson.parse(fromDisk.decodeToString())
            } catch (e: Exception) {
                // Fall through to the bundled copy. Deliberately not deleted: it costs a few KB,
                // and a manifest written for a newer schema is one an upgrade may yet be able to
                // read.
            }
        }
        return ManifestJson.parse(bundled().decodeToString())
    }
}

/**
 * The catalogue bundled with the build. `Res.readBytes` throws rather than returning null for a
 * file that is not there, which is the right shape here: the manifest is not optional, and a build
 * without one is a build that should fail loudly the first time recitation is opened.
 */
suspend fun bundledManifestBytes(): ByteArray = Res.readBytes("files/recitation/manifest.json")

/** The provider the app runs on. */
fun createManifestProvider(paths: RecitationPaths = createRecitationPaths()): ManifestProvider =
    ManifestProvider(paths, FileSystem.SYSTEM, ::bundledManifestBytes)
