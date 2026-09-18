package world.taqwa.app.feature.recitation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import world.taqwa.app.audio.ClipPlayer
import world.taqwa.app.recitation.RecitationLibrary
import world.taqwa.app.recitation.RecitationSettings
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.NowPlayingText
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.recitation.RecitationPlayer
import world.taqwa.app.recitation.SurahDownloader
import world.taqwa.app.recitation.SurahSkip

/**
 * The two seams [RecitationController] is written against.
 *
 * [RecitationPlayer] and [SurahDownloader] are `expect class`es: they exist only once per target,
 * they cannot be subclassed and their constructors reach for a `MediaSessionService` and for
 * WorkManager. A controller that named them directly could not be driven from
 * `:shared:testDebugUnitTest` at all — and the controller is *only* decisions, which is precisely
 * the layer worth testing. So the decisions are written against these two interfaces and the real
 * classes are adapted into them at the one place the graph is built.
 *
 * They are deliberately the same methods with the same names: this is an adapter, not a
 * redesign, and anything the controller wants that is not here belongs on the class itself.
 */
interface PlayerPort {
    val state: StateFlow<PlaybackState>

    /** The lock screen's previous/next, which move by surah (spec §15.1). */
    val skips: Flow<SurahSkip>

    /** A surah that has played out, held by the player for the decision (spec §16.1). */
    val surahEnds: Flow<Int>
    suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText)
    fun play()
    fun pause()
    fun toggle()
    fun seekToAyah(n: Int)
    fun seekToSurahTime(positionMs: Long)
    fun next()
    fun previous()
    fun stop()
}

/**
 * What the controller asks the library: which surahs of a reciter are on the phone. Two methods
 * of [RecitationLibrary]'s dozen — everything else it does (committing, deleting, reconciling)
 * belongs to the downloader and to app start-up, not to the reader's surface.
 */
interface LibraryPort {
    fun downloaded(reciterId: String): Flow<Set<Int>>
    suspend fun isDownloaded(reciterId: String, surah: Int): Boolean

    /** Counted off the disk, for the Downloads screen (spec §5.6). See [RecitationLibrary.bytesUsed]. */
    suspend fun bytesUsed(reciterId: String): Long
    suspend fun bytesUsedTotal(): Long

    /** The Downloads screen's two deletes. Committing and reconciling stay off this seam: they
     * belong to the downloader and to app start-up, not to anything a reader taps. */
    suspend fun delete(reciterId: String, surah: Int)
    suspend fun deleteReciter(reciterId: String)
}

/** The two settings the surface reads and writes: which voice, and whether mobile data is
 * allowed. Deliberately not the whole [world.taqwa.app.settings.SettingsRepository]. */
interface RecitationSettingsPort {
    val settings: Flow<RecitationSettings>
    suspend fun setReciter(id: String)
    suspend fun setDownloadOnMobileData(value: Boolean)
    suspend fun setAutoDownload(value: Boolean)
}

/** The picker's fifteen-second audition. [onEnd] fires when the clip plays out, not on [stop]. */
interface ClipPort {
    fun play(bytes: ByteArray, onEnd: () -> Unit)
    fun stop()
}

interface DownloaderPort {
    val states: StateFlow<Map<DownloadKey, DownloadState>>
    fun enqueue(key: DownloadKey, allowMobileOnce: Boolean)
    fun cancel(key: DownloadKey)
    suspend fun retry(key: DownloadKey)

    /** "Download the whole Quran for this reciter" (spec §12.8) and the Cancel beside it. */
    fun enqueueReciter(reciterId: String, allowMobileOnce: Boolean)
    fun cancelReciter(reciterId: String)
}

fun RecitationLibrary.asPort(): LibraryPort = object : LibraryPort {
    override fun downloaded(reciterId: String): Flow<Set<Int>> = this@asPort.downloaded(reciterId)
    override suspend fun isDownloaded(reciterId: String, surah: Int): Boolean =
        this@asPort.isDownloaded(reciterId, surah)
    override suspend fun bytesUsed(reciterId: String): Long = this@asPort.bytesUsed(reciterId)
    override suspend fun bytesUsedTotal(): Long = this@asPort.bytesUsedTotal()
    override suspend fun delete(reciterId: String, surah: Int) = this@asPort.delete(reciterId, surah)
    override suspend fun deleteReciter(reciterId: String) = this@asPort.deleteReciter(reciterId)
}

fun SettingsRepository.asRecitationPort(): RecitationSettingsPort = object : RecitationSettingsPort {
    override val settings: Flow<RecitationSettings> get() = recitationSettings
    override suspend fun setReciter(id: String) = setRecitationReciter(id)
    override suspend fun setDownloadOnMobileData(value: Boolean) = setRecitationMobileData(value)
    override suspend fun setAutoDownload(value: Boolean) = setRecitationAutoDownload(value)
}

fun ClipPlayer.asPort(): ClipPort = object : ClipPort {
    override fun play(bytes: ByteArray, onEnd: () -> Unit) = this@asPort.play(bytes, onEnd)
    override fun stop() = this@asPort.stop()
}

fun RecitationPlayer.asPort(): PlayerPort = object : PlayerPort {
    override val state: StateFlow<PlaybackState> get() = this@asPort.state
    override val skips: Flow<SurahSkip> get() = this@asPort.skips
    override val surahEnds: Flow<Int> get() = this@asPort.surahEnds
    override suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText) =
        this@asPort.load(reciter, surah, startAyah, text)
    override fun play() = this@asPort.play()
    override fun pause() = this@asPort.pause()
    override fun toggle() = this@asPort.toggle()
    override fun seekToAyah(n: Int) = this@asPort.seekToAyah(n)
    override fun seekToSurahTime(positionMs: Long) = this@asPort.seekToSurahTime(positionMs)
    override fun next() = this@asPort.next()
    override fun previous() = this@asPort.previous()
    override fun stop() = this@asPort.stop()
}

fun SurahDownloader.asPort(): DownloaderPort = object : DownloaderPort {
    override val states: StateFlow<Map<DownloadKey, DownloadState>> get() = this@asPort.states
    override fun enqueue(key: DownloadKey, allowMobileOnce: Boolean) = this@asPort.enqueue(key, allowMobileOnce)
    override fun cancel(key: DownloadKey) = this@asPort.cancel(key)
    override suspend fun retry(key: DownloadKey) = this@asPort.retry(key)
    override fun enqueueReciter(reciterId: String, allowMobileOnce: Boolean) =
        this@asPort.enqueueReciter(reciterId, allowMobileOnce)
    override fun cancelReciter(reciterId: String) = this@asPort.cancelReciter(reciterId)
}
