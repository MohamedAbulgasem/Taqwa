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
        val fetched = fromDisk?.let {
            try {
                ManifestJson.parse(it.decodeToString())
            } catch (e: Exception) {
                // Fall through to the bundled copy. Deliberately not deleted: it costs a few KB,
                // and a manifest written for a newer schema is one an upgrade may yet be able to
                // read.
                null
            }
        }
        val shipped = ManifestJson.parse(bundled().decodeToString())
        return if (fetched != null && newer(fetched, shipped)) fetched else shipped
    }
}

/**
 * Which of two catalogues is in force: **the one generated later**, not simply the fetched one.
 *
 * The cache is refreshed at most once a day, so a build that ships a newer catalogue — the
 * ordinary way a reciter is added — would otherwise be masked by whatever the last refresh
 * happened to fetch, for up to a day after the update, on exactly the launch where the reader
 * goes looking for the new voice. [RecitationManifest.generated] is an ISO-8601 instant in UTC
 * written by one pipeline, so comparing the two as text orders them; anything that does not look
 * like one leaves the fetched copy in force, which is the behaviour a manifest from the network
 * has always had.
 */
internal fun newer(fetched: RecitationManifest, shipped: RecitationManifest): Boolean {
    if (!isInstant(fetched.generated) || !isInstant(shipped.generated)) return true
    return fetched.generated >= shipped.generated
}

/** `2026-09-12T19:34:13Z`, and nothing else: same length, same separators, digits between. */
private fun isInstant(value: String): Boolean = value.length == 20 &&
    value[4] == '-' && value[7] == '-' && value[10] == 'T' &&
    value[13] == ':' && value[16] == ':' && value[19] == 'Z' &&
    value.filterIndexed { i, _ -> i !in setOf(4, 7, 10, 13, 16, 19) }.all { it.isDigit() }

/**
 * The catalogue bundled with the build. `Res.readBytes` throws rather than returning null for a
 * file that is not there, which is the right shape here: the manifest is not optional, and a build
 * without one is a build that should fail loudly the first time recitation is opened.
 */
suspend fun bundledManifestBytes(): ByteArray = Res.readBytes("files/recitation/manifest.json")

/**
 * The reciter's bundled fifteen-second preview (spec §3), or null when this build has none.
 *
 * Today only Alafasy's clip is in the tree; the pipeline publishes the other nine later. A
 * missing file is an ordinary answer here rather than an error, and the picker draws no play
 * triangle for a reciter it gets null for — `Res.readBytes` throws for a file that is not
 * bundled, which is why this catches rather than checks.
 */
suspend fun recitationPreviewBytes(reciterId: String): ByteArray? = try {
    Res.readBytes("files/recitation/previews/$reciterId.mp3")
} catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
    throw cancelled
} catch (missing: Exception) {
    null
}

/** The provider the app runs on. */
fun createManifestProvider(paths: RecitationPaths = createRecitationPaths()): ManifestProvider =
    ManifestProvider(paths, FileSystem.SYSTEM, ::bundledManifestBytes)
