package world.taqwa.app.recitation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

/**
 * One surah of one reciter, as one file (spec 3a §4). The ayahs inside are the corpus's own MP3s
 * byte for byte — the licence permits redistribution at the published bitrate and forbids
 * re-encoding, so nothing here touches the audio; the container only records where each ayah
 * starts.
 *
 * ```
 * 0..3    magic "TAQA"
 * 4       version, 1
 * 5..7    reserved, zero
 * 8..11   index length, u32 big-endian
 * 12..    index JSON, UTF-8
 * then    the ayah MP3s back to back, in ayah order
 * ```
 *
 * Not a zip: iOS has no zip reader in Foundation and Kotlin/Native would need a cinterop for one.
 * The shape earns two things beyond simplicity — a surah is one HTTP request, resumable with
 * `Range`, and the first few kilobytes alone say where every ayah begins, so ayah 1 can play while
 * the rest of the file is still arriving.
 */
@Serializable
data class TaqaAyah(
    val n: Int,
    /** Offset from `dataStart`, not from the start of the file. See [TaqaIndex.dataStart]. */
    val off: Long,
    val len: Long,
)

/**
 * The parsed header of a `.taqa`. [dataStart] is the absolute offset the audio begins at, which is
 * what turns an ayah's relative [TaqaAyah.off] into a file offset; every reader of the container —
 * the players, the downloader's verifier — goes through here rather than doing that sum itself.
 */
data class TaqaIndex(
    val reciter: String,
    val surah: Int,
    val kbps: Int,
    val ayahs: List<TaqaAyah>,
    val dataStart: Long,
) {
    fun ayah(n: Int): TaqaAyah? = ayahs.firstOrNull { it.n == n }

    /** What a whole, undamaged file of this index measures: the header plus every ayah. */
    val expectedFileBytes: Long get() = dataStart + (ayahs.maxOfOrNull { it.off + it.len } ?: 0L)

    /** Absolute file offsets of ayah [n], both ends inclusive — the shape an HTTP `Range` wants. */
    fun rangeOf(n: Int): LongRange {
        val ayah = ayah(n) ?: throw MalformedTaqa("Surah $surah has no ayah $n")
        val first = dataStart + ayah.off
        return first until (first + ayah.len)
    }

    companion object {
        const val MAGIC = "TAQA"
        const val VERSION = 1

        /** Magic, version, reserved, index length: what must be read before anything else can be. */
        const val FIXED_HEADER = 12

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * How many bytes of index follow the fixed header — so a reader that has only the first
         * twelve bytes knows exactly how much more to fetch before calling [parse].
         *
         * @throws MalformedTaqa if [header] is short, or is not a version-1 `.taqa` at all.
         */
        fun indexLength(header: ByteArray): Int {
            if (header.size < FIXED_HEADER) {
                throw MalformedTaqa("A .taqa header is $FIXED_HEADER bytes; got ${header.size}")
            }
            val magic = header.copyOfRange(0, 4).decodeToString()
            if (magic != MAGIC) throw MalformedTaqa("Not a .taqa: magic was \"$magic\"")
            val version = header[4].toInt() and 0xFF
            if (version != VERSION) throw MalformedTaqa("Unsupported .taqa version $version")
            val length = ((header[8].toInt() and 0xFF) shl 24) or
                ((header[9].toInt() and 0xFF) shl 16) or
                ((header[10].toInt() and 0xFF) shl 8) or
                (header[11].toInt() and 0xFF)
            if (length <= 0) throw MalformedTaqa("A .taqa index cannot be $length bytes")
            return length
        }

        /**
         * Parses the header of a container. [header] needs to hold at least the first
         * `12 + indexLength` bytes and may hold the whole file; everything past the index is
         * ignored, which is what lets a partly downloaded file be read.
         *
         * Pure — no file system, no platform. The players and the downloader both need this and
         * neither should have to open the file twice to get it.
         *
         * @throws MalformedTaqa on a bad magic, an unknown version, a truncated buffer or an index
         * that is not the JSON this version writes.
         */
        fun parse(header: ByteArray): TaqaIndex {
            val length = indexLength(header)
            val end = FIXED_HEADER + length
            if (header.size < end) {
                throw MalformedTaqa("A .taqa index of $length bytes needs $end bytes; got ${header.size}")
            }
            val text = header.copyOfRange(FIXED_HEADER, end).decodeToString()
            val wire = try {
                json.decodeFromString(Wire.serializer(), text)
            } catch (e: Exception) {
                throw MalformedTaqa("A .taqa index that is not readable JSON: ${e.message}")
            }
            if (wire.ayahs.isEmpty()) throw MalformedTaqa("A .taqa with no ayahs in its index")
            return TaqaIndex(
                reciter = wire.reciter,
                surah = wire.surah,
                kbps = wire.kbps,
                ayahs = wire.ayahs,
                dataStart = end.toLong(),
            )
        }
    }

    /** The index exactly as it is written inside the file. */
    @Serializable
    internal data class Wire(
        val reciter: String,
        val surah: Int,
        val kbps: Int,
        val ayahs: List<TaqaAyah>,
    )
}

/** A `.taqa` that is not one, or not this version of one. */
class MalformedTaqa(message: String) : IllegalArgumentException(message)

/**
 * A `.taqa` on disk. Everything that reads recitation audio — both players, the downloader's
 * verifier, the library's reconciliation — goes through this class, so the container's layout is
 * known in exactly one place.
 *
 * The index is read once and kept; the audio never is. `readAyah` and `ayahRange` exist so a
 * player can hand the platform a byte range rather than a temporary file per ayah.
 */
class TaqaFile(
    val path: Path,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    private var cached: TaqaIndex? = null

    /** @throws MalformedTaqa if the file is not a readable version-1 container. */
    fun index(): TaqaIndex {
        cached?.let { return it }
        val parsed = fs.read(path) {
            val fixed = ByteArray(TaqaIndex.FIXED_HEADER)
            if (!request(TaqaIndex.FIXED_HEADER.toLong())) {
                throw MalformedTaqa("A .taqa shorter than its own header: $path")
            }
            readFully(fixed)
            val length = TaqaIndex.indexLength(fixed)
            val rest = try {
                readByteArray(length.toLong())
            } catch (e: okio.EOFException) {
                throw MalformedTaqa("A .taqa whose index of $length bytes is not all there: $path")
            }
            TaqaIndex.parse(fixed + rest)
        }
        cached = parsed
        return parsed
    }

    /** Absolute file offsets of ayah [n], both ends inclusive. */
    fun ayahRange(n: Int): LongRange = index().rangeOf(n)

    /** The ayah's MP3, exactly the bytes the corpus published. */
    fun readAyah(n: Int): ByteArray {
        val range = ayahRange(n)
        val length = (range.last - range.first + 1).toInt()
        return fs.openReadOnly(path).use { handle ->
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val got = handle.read(range.first + read, bytes, read, length - read)
                if (got == -1) throw MalformedTaqa("Ayah $n runs past the end of $path")
                read += got
            }
            bytes
        }
    }

    /**
     * The whole file's SHA-256 as lower-case hex — what the manifest publishes and what a download
     * is checked against before it is allowed to become a surah the reader owns. Streamed through
     * okio rather than read into memory: Al-Baqarah is 58 MB.
     */
    fun sha256(): String = fs.source(path).use { source ->
        HashingSource.sha256(source).use { hashing ->
            hashing.buffer().readAll(blackholeSink())
            hashing.hash.hex()
        }
    }

    /** The size on disk matches what the manifest says the finished asset measures. */
    fun isComplete(expectedBytes: Long): Boolean =
        fs.metadataOrNull(path)?.size == expectedBytes

    /**
     * The file accounts for itself without a manifest: the header parses and the file is at least
     * as long as its own index says it must be. This is what `RecitationLibrary.reconcile` can ask
     * of a file it finds on disk that the registry has never heard of — there is no manifest entry
     * to compare it with at that point, and half a download must not be adopted as a surah.
     */
    fun isWhole(): Boolean = try {
        (fs.metadataOrNull(path)?.size ?: return false) >= index().expectedFileBytes
    } catch (e: MalformedTaqa) {
        false
    } catch (e: okio.IOException) {
        false
    }
}
