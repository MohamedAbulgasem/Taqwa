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
    suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText)
    fun play()
    fun pause()
    fun toggle()
    fun seekToAyah(n: Int)
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
}

/** The two settings the surface reads and writes: which voice, and whether mobile data is
 * allowed. Deliberately not the whole [world.taqwa.app.settings.SettingsRepository]. */
interface RecitationSettingsPort {
    val settings: Flow<RecitationSettings>
    suspend fun setReciter(id: String)
}

/** The picker's fifteen-second audition. */
interface ClipPort {
    fun play(bytes: ByteArray)
    fun stop()
}

interface DownloaderPort {
    val states: StateFlow<Map<DownloadKey, DownloadState>>
    fun enqueue(key: DownloadKey, allowMobileOnce: Boolean)
    fun cancel(key: DownloadKey)
    suspend fun retry(key: DownloadKey)
}

fun RecitationLibrary.asPort(): LibraryPort = object : LibraryPort {
    override fun downloaded(reciterId: String): Flow<Set<Int>> = this@asPort.downloaded(reciterId)
    override suspend fun isDownloaded(reciterId: String, surah: Int): Boolean =
        this@asPort.isDownloaded(reciterId, surah)
}

fun SettingsRepository.asRecitationPort(): RecitationSettingsPort = object : RecitationSettingsPort {
    override val settings: Flow<RecitationSettings> get() = recitationSettings
    override suspend fun setReciter(id: String) = setRecitationReciter(id)
}

fun ClipPlayer.asPort(): ClipPort = object : ClipPort {
    override fun play(bytes: ByteArray) = this@asPort.play(bytes)
    override fun stop() = this@asPort.stop()
}

fun RecitationPlayer.asPort(): PlayerPort = object : PlayerPort {
    override val state: StateFlow<PlaybackState> get() = this@asPort.state
    override suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText) =
        this@asPort.load(reciter, surah, startAyah, text)
    override fun play() = this@asPort.play()
    override fun pause() = this@asPort.pause()
    override fun toggle() = this@asPort.toggle()
    override fun seekToAyah(n: Int) = this@asPort.seekToAyah(n)
    override fun next() = this@asPort.next()
    override fun previous() = this@asPort.previous()
    override fun stop() = this@asPort.stop()
}

fun SurahDownloader.asPort(): DownloaderPort = object : DownloaderPort {
    override val states: StateFlow<Map<DownloadKey, DownloadState>> get() = this@asPort.states
    override fun enqueue(key: DownloadKey, allowMobileOnce: Boolean) = this@asPort.enqueue(key, allowMobileOnce)
    override fun cancel(key: DownloadKey) = this@asPort.cancel(key)
    override suspend fun retry(key: DownloadKey) = this@asPort.retry(key)
}
