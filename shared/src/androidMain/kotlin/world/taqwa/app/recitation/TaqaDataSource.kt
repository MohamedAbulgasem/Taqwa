package world.taqwa.app.recitation

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import okio.FileSystem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/** `taqa://<reciter>/<surah>/<ayah>` — one ayah, addressed inside its surah's container. */
internal const val TAQA_SCHEME = "taqa"

/** `silence://<milliseconds>` — the reciter's inter-ayah gap, played as an item of its own. */
internal const val SILENCE_SCHEME = "silence"

internal fun ayahUri(reciterId: String, surah: Int, ayah: Int): String =
    "$TAQA_SCHEME://$reciterId/$surah/$ayah"

internal fun gapUri(durationMs: Long): String = "$SILENCE_SCHEME://$durationMs"

/**
 * The media id the app gives queue item [entry]: ids are all that cross the binder to the
 * session, and [queueItemsOf] is how the session reads them back. [generation] names the queue's
 * translations (read-aloud spec §5.5), so a WAV made for a replaced queue is never played.
 */
internal fun mediaIdOf(entry: QueueItem, reciterId: String, surah: Int, generation: Long): String = when (entry) {
    is QueueItem.Ayah -> ayahUri(reciterId, surah, entry.n)
    is QueueItem.Gap -> gapUri(entry.durationMs)
    is QueueItem.Speech -> speechUri(generation, surah, entry.n)
}

/**
 * The queue items [mediaIds] stand for, in order: how the session's `AyahPlayer` rebuilds the
 * app's queue from the items it was given, rather than keeping a copy that could fall out of step.
 * A `silence://` id is a gap or a breath, a `speech://` id the translation read after the ayah
 * before it, and any other id an ayah.
 *
 * Only the kinds and their order matter to the arithmetic the session does, which moves between
 * indices and nothing else: ayahs are counted from one rather than read off their ids, and a
 * silence's length is left at zero. Null when there is no ayah at all, which no queue the app
 * builds can be.
 */
internal fun queueItemsOf(mediaIds: List<String>): List<QueueItem>? {
    var ayah = 0
    val items = mediaIds.map { id ->
        when {
            id.startsWith("$SILENCE_SCHEME://") -> QueueItem.Gap(0L)
            id.startsWith("$SPEECH_SCHEME://") -> QueueItem.Speech(ayah.coerceAtLeast(1))
            else -> QueueItem.Ayah(++ayah)
        }
    }
    return items.takeIf { ayah > 0 }
}

/**
 * Serves one ayah out of the middle of a `.taqa` without extracting it.
 *
 * The container holds the corpus's own MP3s back to back and its index says where each one starts
 * (see [TaqaFile]); this turns ExoPlayer's request for `taqa://ar.alafasy/2/255` into a read of
 * exactly that ayah's byte range of the surah file. Nothing is copied to disk, which matters for a
 * 58 MB Al-Baqarah whose 286 ayahs would otherwise be written out a second time on first play.
 *
 * The wrapped [FileDataSource] does the reading. This class only rewrites the [DataSpec]: the
 * position ExoPlayer asks for is an offset *into the ayah*, and becomes that offset past the
 * ayah's first byte, with the length clamped so a seek or a hungry extractor cannot read into the
 * ayah that follows.
 */
@UnstableApi
internal class TaqaDataSource(
    private val paths: RecitationPaths,
    private val indexes: ConcurrentHashMap<String, TaqaFile>,
) : DataSource {

    private val delegate = FileDataSource()
    private var requested: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val reciter = uri.authority
            ?: throw IOException("A $TAQA_SCHEME URI without a reciter: $uri")
        val segments = uri.pathSegments
        if (segments.size != 2) throw IOException("A $TAQA_SCHEME URI that is not /surah/ayah: $uri")
        val surah = segments[0].toIntOrNull() ?: throw IOException("Not a surah number in $uri")
        val ayah = segments[1].toIntOrNull() ?: throw IOException("Not an ayah number in $uri")

        val file = paths.surahFile(reciter, surah)
        val taqa = indexes.getOrPut(file.toString()) { TaqaFile(file, FileSystem.SYSTEM) }
        val range = try {
            taqa.ayahRange(ayah)
        } catch (e: MalformedTaqa) {
            throw IOException(e)
        }
        val ayahBytes = range.last - range.first + 1
        if (dataSpec.position < 0 || dataSpec.position > ayahBytes) {
            throw IOException("Position ${dataSpec.position} is outside ayah $ayah of $ayahBytes bytes")
        }
        val available = ayahBytes - dataSpec.position
        val length =
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else min(dataSpec.length, available)

        requested = uri
        return delegate.open(
            dataSpec.buildUpon()
                .setUri(Uri.fromFile(file.toFile()))
                .setPosition(range.first + dataSpec.position)
                .setLength(length)
                .build(),
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    /** The `taqa://` URI, not the surah file: it is the ayah ExoPlayer asked for and cached by. */
    override fun getUri(): Uri? = requested ?: delegate.uri

    override fun close() {
        requested = null
        delegate.close()
    }

    /**
     * One factory per player, holding the open containers. [TaqaFile] parses its index once and
     * keeps it, so the 286 ayahs of Al-Baqarah read the header once between them rather than 286
     * times.
     */
    @UnstableApi
    class Factory(private val paths: RecitationPaths) : DataSource.Factory {
        private val indexes = ConcurrentHashMap<String, TaqaFile>()
        override fun createDataSource(): DataSource = TaqaDataSource(paths, indexes)
    }
}
