package world.taqwa.app.feature.recitation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import world.taqwa.app.feature.quran.FakeQuranSource
import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.NowPlayingText
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.RecitationManifest
import world.taqwa.app.recitation.RecitationSettings
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.recitation.SurahAsset
import world.taqwa.app.recitation.SurahSkip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The controller's decisions, with every collaborator replaced. Nothing here touches a file, a
 * DataStore or a platform player — which is the point of the ports in `RecitationPorts.kt`: this
 * class is only judgement, and judgement is what a unit test can hold to account.
 *
 * The scope is `runTest`'s own `backgroundScope` on an unconfined dispatcher, so every flow the
 * controller assembles settles before the next assertion rather than a frame later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecitationControllerTest {

    private val alafasy = Reciter(
        id = "ar.alafasy",
        nameEn = "Mishary Rashid Alafasy",
        nameAr = "مشاري راشد العفاسي",
        style = "murattal",
        kbps = 64,
        gapMs = 300,
        hue = "amber",
        release = "audio-ar.alafasy-v1",
        totalBytes = 860_636_852L,
        surahs = listOf(SurahAsset(1, 381_780L, "a"), SurahAsset(2, 58_538_153L, "b"), SurahAsset(112, 112_778L, "c")),
    )

    private val husary = alafasy.copy(
        id = "ar.husary",
        nameEn = "Mahmoud Khalil Al-Husary",
        nameAr = "محمود خليل الحصري",
        hue = "clay",
        release = "audio-ar.husary-v1",
    )

    private val catalogue = RecitationManifest(
        schema = 1,
        generated = "2026-09-12T00:00:00Z",
        base = "https://example.invalid/",
        reciters = listOf(alafasy, husary),
    )

    private class FakePlayer : PlayerPort {
        val loads = mutableListOf<Triple<String, Int, Int>>()
        var plays = 0
        var toggles = 0
        var stops = 0
        private val _state = MutableStateFlow(PlaybackState.EMPTY)
        override val state: StateFlow<PlaybackState> = _state.asStateFlow()
        private val _skips = MutableSharedFlow<SurahSkip>(extraBufferCapacity = 4)
        override val skips: Flow<SurahSkip> = _skips

        /** A lock screen, headset or car pressing previous or next. */
        fun press(skip: SurahSkip) { _skips.tryEmit(skip) }

        fun emit(value: PlaybackState) {
            _state.value = value
        }

        override suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText) {
            loads += Triple(reciter.id, surah, startAyah)
            lastText = text
            _state.value = PlaybackState(
                reciterId = reciter.id,
                surah = surah,
                ayah = startAyah,
                ayahCount = 7,
                playing = true,
            )
        }

        var lastText: NowPlayingText? = null
        var pauses = 0
        override fun play() {
            plays++
            _state.value = _state.value.copy(playing = true)
        }
        override fun pause() {
            pauses++
            _state.value = _state.value.copy(playing = false)
        }
        override fun toggle() { toggles++ }
        override fun seekToAyah(n: Int) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun stop() {
            stops++
            _state.value = PlaybackState.EMPTY
        }
    }

    private class FakeDownloader : DownloaderPort {
        val enqueued = mutableListOf<Pair<DownloadKey, Boolean>>()
        val cancelled = mutableListOf<DownloadKey>()
        private val _states = MutableStateFlow<Map<DownloadKey, DownloadState>>(emptyMap())
        override val states: StateFlow<Map<DownloadKey, DownloadState>> = _states.asStateFlow()

        fun emit(value: Map<DownloadKey, DownloadState>) {
            _states.value = value
        }

        override fun enqueue(key: DownloadKey, allowMobileOnce: Boolean) {
            enqueued += key to allowMobileOnce
        }

        override fun cancel(key: DownloadKey) {
            cancelled += key
        }

        override suspend fun retry(key: DownloadKey) {
            enqueued += key to false
        }

        val batches = mutableListOf<Pair<String, Boolean>>()
        val batchesCancelled = mutableListOf<String>()

        override fun enqueueReciter(reciterId: String, allowMobileOnce: Boolean) {
            batches += reciterId to allowMobileOnce
        }

        override fun cancelReciter(reciterId: String) {
            batchesCancelled += reciterId
        }
    }

    private class FakeLibrary : LibraryPort {
        val owned = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
        override fun downloaded(reciterId: String): Flow<Set<Int>> = owned.map { it[reciterId].orEmpty() }
        override suspend fun isDownloaded(reciterId: String, surah: Int): Boolean =
            surah in owned.value[reciterId].orEmpty()

        fun put(reciterId: String, surahs: Set<Int>) {
            owned.value = owned.value + (reciterId to surahs)
        }

        val bytes = mutableMapOf<String, Long>()
        val deleted = mutableListOf<Pair<String, Int>>()
        val wiped = mutableListOf<String>()

        override suspend fun bytesUsed(reciterId: String): Long = bytes[reciterId] ?: 0L
        override suspend fun bytesUsedTotal(): Long = bytes.values.sum()

        override suspend fun delete(reciterId: String, surah: Int) {
            deleted += reciterId to surah
            owned.value = owned.value + (reciterId to (owned.value[reciterId].orEmpty() - surah))
        }

        override suspend fun deleteReciter(reciterId: String) {
            wiped += reciterId
            owned.value = owned.value - reciterId
        }
    }

    private class FakeSettings : RecitationSettingsPort {
        val stored = MutableStateFlow(RecitationSettings())
        override val settings: Flow<RecitationSettings> = stored
        override suspend fun setReciter(id: String) {
            stored.value = stored.value.copy(reciterId = id)
        }

        override suspend fun setDownloadOnMobileData(value: Boolean) {
            stored.value = stored.value.copy(downloadOnMobileData = value)
        }

        override suspend fun setAutoDownload(value: Boolean) {
            stored.value = stored.value.copy(autoDownload = value, autoDownloadAsked = true)
        }
    }

    private class FakeClips : ClipPort {
        var played = 0
        var stopped = 0
        private var onEnd: (() -> Unit)? = null
        override fun play(bytes: ByteArray, onEnd: () -> Unit) {
            played++
            this.onEnd = onEnd
        }
        override fun stop() {
            stopped++
            onEnd = null
        }

        /** The clip playing out by itself; a stopped clip has nobody left to tell. */
        fun finish() {
            val end = onEnd
            onEnd = null
            end?.invoke()
        }
    }

    private class Harness(
        val player: FakePlayer = FakePlayer(),
        val downloader: FakeDownloader = FakeDownloader(),
        val library: FakeLibrary = FakeLibrary(),
        val settings: FakeSettings = FakeSettings(),
        val clips: FakeClips = FakeClips(),
    )

    /** What the controller told the outside world about engagement, in order (privacy spec
     * §2.2, §2.4): "mark" and "refresh". */
    private class Engagement {
        val events = mutableListOf<String>()
    }

    private fun controller(
        harness: Harness,
        scope: kotlinx.coroutines.CoroutineScope,
        previews: Set<String> = setOf("ar.alafasy"),
        engagement: Engagement = Engagement(),
    ) = RecitationController(
        manifests = { catalogue },
        library = harness.library,
        downloader = harness.downloader,
        player = harness.player,
        settings = harness.settings,
        quran = FakeQuranSource(),
        clips = harness.clips,
        previewBytes = { id -> if (id in previews) ByteArray(8) else null },
        scope = scope,
        markEngaged = { engagement.events += "mark" },
        refreshCatalogue = { engagement.events += "refresh" },
    )

    @Test
    fun `a downloaded surah plays from the ayah that was asked for`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1, 112))
        val controller = controller(harness, backgroundScope)

        controller.requestPlay(112, 3)

        assertEquals(listOf(Triple("ar.alafasy", 112, 3)), harness.player.loads)
        assertEquals(1, harness.player.plays)
        assertNull(controller.state.value.sheet)
    }

    @Test
    fun `a surah that is not on the phone opens the download sheet instead`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        val controller = controller(harness, backgroundScope)

        controller.requestPlay(2, 255)

        assertTrue(harness.player.loads.isEmpty())
        val sheet = assertNotNull(controller.state.value.sheet)
        assertEquals(2, sheet.surah)
        assertEquals("ar.alafasy", sheet.reciter.id)
        assertEquals(58_538_153L, sheet.bytes)
        assertEquals(SheetPhase.Ready(needsWifiNote = true), sheet.phase)
    }

    @Test
    fun `confirming the sheet enqueues the surah with the override flag`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        val controller = controller(harness, backgroundScope)

        controller.requestPlay(2, 255)
        controller.confirmDownload(allowMobileOnce = true)

        assertEquals(listOf(DownloadKey("ar.alafasy", 2) to true), harness.downloader.enqueued)
    }

    @Test
    fun `the download committing plays the ayah the reader originally asked for`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)

            controller.requestPlay(2, 255)
            controller.confirmDownload(allowMobileOnce = false)
            assertTrue(harness.player.loads.isEmpty())

            // What the downloader does when a surah commits: it enters the library.
            harness.library.put("ar.alafasy", setOf(2))

            assertEquals(listOf(Triple("ar.alafasy", 2, 255)), harness.player.loads)
            assertNull(controller.state.value.sheet)
        }

    @Test
    fun `a surah committing for a reciter nobody is waiting on plays nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(2, 255)
            controller.confirmDownload(allowMobileOnce = false)

            harness.library.put("ar.husary", setOf(2))

            assertTrue(harness.player.loads.isEmpty())
        }

    @Test
    fun `a failed download shows its reason on the sheet`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        val controller = controller(harness, backgroundScope)
        controller.requestPlay(2, 255)

        harness.downloader.emit(
            mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Failed(DownloadFailure.NEEDS_WIFI)),
        )

        assertEquals(SheetPhase.Failed(DownloadFailure.NEEDS_WIFI), controller.state.value.sheet?.phase)
    }

    @Test
    fun `a running download becomes the sheet's progress bar and the header's ring`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(2, 255)

            harness.downloader.emit(
                mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Downloading(29_269_076L, 58_538_153L)),
            )

            assertEquals(
                SheetPhase.Downloading(29_269_076L, 58_538_153L),
                controller.state.value.sheet?.phase,
            )
            val header = controller.state.value.header(2)
            assertTrue(header is HeaderState.Downloading)
            assertEquals(0.5f, header.fraction, 0.01f)
        }

    @Test
    fun `a new surah starts the progress line at nothing rather than where the last one ended`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(1, 1)
            harness.player.emit(
                PlaybackState(
                    reciterId = "ar.alafasy",
                    surah = 1,
                    ayah = 7,
                    ayahCount = 7,
                    positionMs = 4_000L,
                    durationMs = 4_000L,
                    playing = true,
                ),
            )
            assertEquals(1f, controller.state.value.bar?.fraction ?: -1f, 0.01f)

            // The first item of a surah reports no duration yet, which is when `barFraction`
            // answers with the fraction it was last given.
            harness.player.emit(
                PlaybackState(
                    reciterId = "ar.alafasy",
                    surah = 2,
                    ayah = 1,
                    ayahCount = 286,
                    playing = true,
                ),
            )

            assertEquals(0f, controller.state.value.bar?.fraction ?: -1f, 0.001f)
        }

    @Test
    fun `cancelling the download cancels the key and forgets the waiting play`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(2, 255)
            controller.confirmDownload(allowMobileOnce = false)

            controller.cancelDownload()
            harness.library.put("ar.alafasy", setOf(2))

            assertEquals(listOf(DownloadKey("ar.alafasy", 2)), harness.downloader.cancelled)
            assertTrue(harness.player.loads.isEmpty())
        }

    @Test
    fun `picking a reciter that has this surah swaps the voice at the current ayah`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            harness.library.put("ar.husary", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            harness.player.emit(
                PlaybackState(reciterId = "ar.alafasy", surah = 112, ayah = 3, ayahCount = 4, playing = true),
            )

            controller.pickReciter("ar.husary")

            assertEquals(Triple("ar.husary", 112, 3), harness.player.loads.last())
            assertEquals("ar.husary", controller.state.value.reciter?.id)
        }

    @Test
    fun `picking a reciter without this surah offers the download and leaves the old voice playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.openPicker()
            controller.requestPlay(112, 1)
            val loadsBefore = harness.player.loads.size

            controller.pickReciter("ar.husary")

            assertEquals(loadsBefore, harness.player.loads.size)
            val sheet = assertNotNull(controller.state.value.sheet)
            assertEquals(112, sheet.surah)
            assertEquals("ar.husary", sheet.reciter.id)
            assertEquals("ar.alafasy", sheet.playingMeanwhile?.id)
            assertFalse(controller.state.value.pickerOpen)
            assertEquals("ar.husary", controller.state.value.reciter?.id)
            assertEquals("ar.alafasy", controller.state.value.bar?.reciter?.id)
            assertTrue(controller.state.value.bar?.playing == true)
        }

    @Test
    fun `confirming the switch changes the voice at the ayah being heard when it lands`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.pickReciter("ar.husary")

            controller.confirmDownload(allowMobileOnce = false)
            assertEquals(listOf(DownloadKey("ar.husary", 112) to false), harness.downloader.enqueued)
            // The old voice has moved on by the time the new one's copy lands.
            harness.player.emit(
                PlaybackState(reciterId = "ar.alafasy", surah = 112, ayah = 3, ayahCount = 4, playing = true),
            )
            harness.library.put("ar.husary", setOf(112))

            assertEquals(Triple("ar.husary", 112, 3), harness.player.loads.last())
            assertNull(controller.state.value.sheet)
            assertEquals("ar.husary", controller.state.value.bar?.reciter?.id)
            assertEquals(0, harness.player.pauses)
        }

    @Test
    fun `a switch that lands on a paused recitation stays paused`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.pickReciter("ar.husary")
            controller.confirmDownload(allowMobileOnce = false)
            harness.player.emit(
                PlaybackState(reciterId = "ar.alafasy", surah = 112, ayah = 3, ayahCount = 4, playing = false),
            )

            harness.library.put("ar.husary", setOf(112))

            assertEquals(Triple("ar.husary", 112, 3), harness.player.loads.last())
            assertEquals(1, harness.player.pauses)
            assertFalse(harness.player.state.value.playing)
        }

    @Test
    fun `the bar shows how far the incoming voice's copy has got`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            assertNull(controller.state.value.bar?.incoming)
            controller.pickReciter("ar.husary")
            controller.confirmDownload(allowMobileOnce = false)

            harness.downloader.emit(mapOf(DownloadKey("ar.husary", 112) to DownloadState.Downloading(50L, 100L)))

            assertEquals(0.5f, controller.state.value.bar?.incoming ?: -1f, 0.001f)
            // Playing still beats the download on the header: a tap there is still pause.
            assertEquals(HeaderState.Playing, controller.state.value.header(112))
        }

    @Test
    fun `re-picking the voice a switch is waiting on keeps the switch`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.pickReciter("ar.husary")
            controller.confirmDownload(allowMobileOnce = false)
            controller.dismissSheet()
            harness.downloader.emit(mapOf(DownloadKey("ar.husary", 112) to DownloadState.Downloading(1L, 10L)))

            // The row is already selected; tapping it again is an easy thing to do.
            controller.openPicker()
            controller.pickReciter("ar.husary")
            assertNotNull(controller.state.value.sheet)
            harness.player.emit(
                PlaybackState(reciterId = "ar.alafasy", surah = 112, ayah = 4, ayahCount = 4, playing = true),
            )
            harness.library.put("ar.husary", setOf(112))

            assertEquals(Triple("ar.husary", 112, 4), harness.player.loads.last())
        }

    @Test
    fun `picking a voice whose copy is already arriving arms the switch without a confirm`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            // A whole-Quran batch for Al-Husary is already fetching this surah.
            harness.downloader.emit(mapOf(DownloadKey("ar.husary", 112) to DownloadState.Downloading(1L, 10L)))

            controller.pickReciter("ar.husary")
            harness.library.put("ar.husary", setOf(112))

            assertEquals("ar.husary", harness.player.loads.last().first)
        }

    @Test
    fun `a switch whose recitation has since ended starts nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.pickReciter("ar.husary")
            controller.confirmDownload(allowMobileOnce = false)
            // The surah read itself out: the player tore itself down, not through the controller.
            harness.player.emit(PlaybackState.EMPTY)

            harness.library.put("ar.husary", setOf(112))

            assertEquals(1, harness.player.loads.size)
            assertNull(controller.state.value.bar)
            assertNull(controller.state.value.sheet)
        }

    @Test
    fun `a plain download play still starts with nothing playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 2)
            controller.confirmDownload(allowMobileOnce = false)

            harness.library.put("ar.alafasy", setOf(112))

            assertEquals(Triple("ar.alafasy", 112, 2), harness.player.loads.last())
        }

    @Test
    fun `switching previews keeps the recitation paused until the second clip ends`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope, previews = setOf("ar.alafasy", "ar.husary"))
            controller.requestPlay(112, 1)
            controller.previewReciter("ar.alafasy")
            assertEquals(1, harness.player.pauses)

            controller.previewReciter("ar.husary")
            assertEquals("ar.husary", controller.state.value.previewing)
            assertEquals(1, harness.player.plays)
            assertFalse(harness.player.state.value.playing)

            harness.clips.finish()
            assertEquals(2, harness.player.plays)
            assertNull(controller.state.value.previewing)
        }

    @Test
    fun `picking another voice supersedes a switch still waiting`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.pickReciter("ar.husary")
            controller.confirmDownload(allowMobileOnce = false)

            controller.pickReciter("ar.alafasy")
            harness.library.put("ar.husary", setOf(112))

            assertEquals(1, harness.player.loads.size)
            assertEquals("ar.alafasy", controller.state.value.bar?.reciter?.id)
        }

    @Test
    fun `picking the voice already playing while paused resumes it`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            harness.player.pause()

            controller.pickReciter("ar.alafasy")

            assertEquals(2, harness.player.plays)
            assertEquals(1, harness.player.loads.size)
            assertTrue(harness.player.state.value.playing)
        }

    @Test
    fun `picking the voice already playing while playing leaves it alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)

            controller.pickReciter("ar.alafasy")

            assertEquals(1, harness.player.plays)
            assertEquals(1, harness.player.loads.size)
            assertNull(controller.state.value.sheet)
        }

    // ── Surah skips (spec §15.1) ────────────────────────────────────────────────────────

    @Test
    fun `next and previous move to the neighbouring surah from its first ayah`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2, 112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(1, 3)

            controller.nextSurah()
            assertEquals(Triple("ar.alafasy", 2, 1), harness.player.loads.last())

            controller.previousSurah()
            assertEquals(Triple("ar.alafasy", 1, 1), harness.player.loads.last())
        }

    @Test
    fun `the ends of the Quran have no neighbour`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        val controller = controller(harness, backgroundScope)
        controller.requestPlay(1, 1)

        controller.previousSurah()

        assertEquals(1, harness.player.loads.size)
        assertNull(controller.state.value.sheet)
    }

    @Test
    fun `a next surah not on the phone is offered like any other`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(1, 1)

            controller.nextSurah()

            assertEquals(1, harness.player.loads.size)
            assertEquals(2, controller.state.value.sheet?.surah)
        }

    @Test
    fun `the lock screen's previous and next are surah moves too`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(1, 5)

            harness.player.press(SurahSkip.NEXT)
            assertEquals(Triple("ar.alafasy", 2, 1), harness.player.loads.last())

            harness.player.press(SurahSkip.PREVIOUS)
            assertEquals(Triple("ar.alafasy", 1, 1), harness.player.loads.last())
        }

    @Test
    fun `a skip with nothing playing does nothing`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1, 2))
        val controller = controller(harness, backgroundScope)

        harness.player.press(SurahSkip.NEXT)

        assertTrue(harness.player.loads.isEmpty())
    }

    // ── Downloading without asking (spec §15.3) ─────────────────────────────────────────

    @Test
    fun `the sheet offers auto-download ticked until the reader has answered once`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(2, 1)
            assertTrue(controller.state.value.sheet?.autoDownloadDefault == true)

            controller.confirmDownload(allowMobileOnce = false, autoDownload = false)
            controller.dismissSheet()
            controller.requestPlay(112, 1)

            assertTrue(controller.state.value.sheet?.autoDownloadDefault == false)
            assertFalse(controller.state.value.autoDownload)
        }

    @Test
    fun `confirming with the switch on fetches the next surah without a sheet`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(2, 1)
            controller.confirmDownload(allowMobileOnce = false, autoDownload = true)
            assertTrue(controller.state.value.autoDownload)
            controller.dismissSheet()

            controller.requestPlay(112, 3)

            assertNull(controller.state.value.sheet)
            assertEquals(DownloadKey("ar.alafasy", 112) to false, harness.downloader.enqueued.last())
            harness.library.put("ar.alafasy", setOf(112))
            assertEquals(Triple("ar.alafasy", 112, 3), harness.player.loads.last())
        }

    @Test
    fun `a refused automatic download opens the sheet on its reason once`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.settings.stored.value = RecitationSettings(autoDownload = true, autoDownloadAsked = true)
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            assertNull(controller.state.value.sheet)

            harness.downloader.emit(mapOf(DownloadKey("ar.alafasy", 112) to DownloadState.Failed(DownloadFailure.NEEDS_WIFI)))
            assertEquals(SheetPhase.Failed(DownloadFailure.NEEDS_WIFI), controller.state.value.sheet?.phase)

            controller.dismissSheet()
            harness.downloader.emit(mapOf(DownloadKey("ar.alafasy", 112) to DownloadState.Failed(DownloadFailure.NEEDS_WIFI)))
            assertNull(controller.state.value.sheet)
        }

    @Test
    fun `picking a voice without the surah fetches it quietly when asking is off`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.settings.stored.value = RecitationSettings(autoDownload = true, autoDownloadAsked = true)
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)

            controller.pickReciter("ar.husary")

            assertNull(controller.state.value.sheet)
            assertEquals(DownloadKey("ar.husary", 112) to false, harness.downloader.enqueued.last())
            harness.player.emit(PlaybackState(reciterId = "ar.alafasy", surah = 112, ayah = 2, ayahCount = 4, playing = true))
            harness.library.put("ar.husary", setOf(112))
            assertEquals(Triple("ar.husary", 112, 2), harness.player.loads.last())
        }

    @Test
    fun `the bar's ring shows the next surah arriving`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.settings.stored.value = RecitationSettings(autoDownload = true, autoDownloadAsked = true)
        harness.library.put("ar.alafasy", setOf(1))
        val controller = controller(harness, backgroundScope)
        controller.requestPlay(1, 1)

        controller.nextSurah()
        harness.downloader.emit(mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Downloading(25L, 100L)))

        assertEquals(0.25f, controller.state.value.bar?.incoming ?: -1f, 0.001f)
        assertEquals(1, controller.state.value.bar?.surah)
    }

    @Test
    fun `the settings switch is written straight through`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        val controller = controller(harness, backgroundScope)

        controller.setAutoDownload(true)

        assertTrue(harness.settings.stored.value.autoDownload)
        assertTrue(controller.state.value.autoDownload)
    }

    // ── Previews over a recitation (spec §14.5) ─────────────────────────────────────────

    @Test
    fun `a preview pauses the recitation and the end of the clip gives it back`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)

            controller.previewReciter("ar.alafasy")
            assertEquals(1, harness.player.pauses)
            assertEquals("ar.alafasy", controller.state.value.previewing)

            harness.clips.finish()
            assertEquals(2, harness.player.plays)
            assertNull(controller.state.value.previewing)
        }

    @Test
    fun `closing the picker mid-preview gives the recitation back`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.openPicker()
            controller.previewReciter("ar.alafasy")

            controller.closePicker()

            assertTrue(harness.clips.stopped > 0)
            assertEquals(2, harness.player.plays)
            assertTrue(harness.player.state.value.playing)
        }

    @Test
    fun `a preview over a paused recitation resumes nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            harness.player.pause()

            controller.previewReciter("ar.alafasy")
            harness.clips.finish()

            assertEquals(1, harness.player.pauses)
            assertEquals(1, harness.player.plays)
        }

    @Test
    fun `pressing play on the bar during a preview ends it and is the only resume`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            controller.previewReciter("ar.alafasy")

            controller.toggle()
            harness.clips.finish()

            assertEquals(1, harness.player.toggles)
            assertNull(controller.state.value.previewing)
            assertTrue(harness.clips.stopped > 0)
            assertEquals(1, harness.player.plays)
        }

    @Test
    fun `picking a voice that has the surah mid-preview starts it with no second resume`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            harness.library.put("ar.husary", setOf(112))
            val controller = controller(harness, backgroundScope, previews = setOf("ar.alafasy", "ar.husary"))
            controller.requestPlay(112, 1)
            controller.previewReciter("ar.husary")
            assertEquals(1, harness.player.pauses)

            controller.pickReciter("ar.husary")

            assertEquals(Triple("ar.husary", 112, 1), harness.player.loads.last())
            assertEquals(2, harness.player.plays)
            assertNull(controller.state.value.previewing)
        }

    @Test
    fun `picking a voice without the surah mid-preview gives the old voice back`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope, previews = setOf("ar.alafasy", "ar.husary"))
            controller.requestPlay(112, 1)
            controller.previewReciter("ar.husary")

            controller.pickReciter("ar.husary")

            assertNotNull(controller.state.value.sheet)
            assertEquals(1, harness.player.loads.size)
            assertEquals(2, harness.player.plays)
            assertTrue(harness.player.state.value.playing)
        }

    @Test
    fun `an unknown stored reciter falls back to the catalogue's first voice`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.settings.stored.value = RecitationSettings(reciterId = "ar.withdrawn")
            val controller = controller(harness, backgroundScope)

            assertEquals("ar.alafasy", controller.state.value.reciter?.id)
        }

    @Test
    fun `the header button pauses what is already playing on that surah`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)

            controller.onHeaderTap(112, 1)

            assertEquals(1, harness.player.toggles)
            assertEquals(1, harness.player.loads.size)
        }

    @Test
    fun `the header button on another surah starts that one instead`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)

            controller.onHeaderTap(1, 4)

            assertEquals(0, harness.player.toggles)
            assertEquals(Triple("ar.alafasy", 1, 4), harness.player.loads.last())
        }

    @Test
    fun `dismissing the bar stops the player`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(112))
        val controller = controller(harness, backgroundScope)
        controller.requestPlay(112, 1)

        controller.dismissBar()

        assertEquals(1, harness.player.stops)
        assertNull(controller.state.value.bar)
    }

    @Test
    fun `the picker only offers a preview for a reciter this build carries a clip for`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)

            controller.openPicker()

            assertEquals(setOf("ar.alafasy"), controller.state.value.previewable)
            assertTrue(controller.state.value.pickerOpen)
        }

    @Test
    fun `a second tap on the same preview stops it`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        val controller = controller(harness, backgroundScope)

        controller.previewReciter("ar.alafasy")
        assertEquals(1, harness.clips.played)
        assertEquals("ar.alafasy", controller.state.value.previewing)

        controller.previewReciter("ar.alafasy")
        assertEquals(1, harness.clips.played)
        assertTrue(harness.clips.stopped > 0)
        assertNull(controller.state.value.previewing)
    }

    @Test
    fun `a reciter with no bundled clip plays nothing`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        val controller = controller(harness, backgroundScope)

        controller.previewReciter("ar.husary")

        assertEquals(0, harness.clips.played)
        assertNull(controller.state.value.previewing)
    }

    @Test
    fun `the lock screen is given the surah and the reciter and not the ayah`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1))
            val controller = controller(harness, backgroundScope)

            controller.requestPlay(1, 3)

            assertEquals(
                NowPlayingText("Al-Fatihah", "Mishary Rashid Alafasy"),
                harness.player.lastText,
            )
        }

    @Test
    fun `an Arabic interface names the surah and the reciter in Arabic`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1))
            val controller = controller(harness, backgroundScope)
            controller.setArabicUi(true)

            controller.requestPlay(1, 3)

            assertEquals(
                NowPlayingText("الفاتحة", "مشاري راشد العفاسي"),
                harness.player.lastText,
            )
        }

    // ── The whole Quran and the Downloads screen (spec §5.6, §12.8) ─────────────────────

    @Test
    fun `the whole Quran is asked for by reciter and priced at what is missing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1))
            val controller = controller(harness, backgroundScope)

            assertEquals(
                WholeQuran.Offer(bytes = 58_538_153L + 112_778L, missing = 2),
                controller.state.value.wholeQuran,
            )

            controller.downloadWholeQuran(allowMobileOnce = true)

            assertEquals(listOf("ar.alafasy" to true), harness.downloader.batches)
        }

    @Test
    fun `a batch this app started reports itself even when one surah is left in it`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2))
            val controller = controller(harness, backgroundScope)
            controller.downloadWholeQuran()
            harness.downloader.emit(mapOf(DownloadKey("ar.alafasy", 112) to DownloadState.Queued))

            assertEquals(WholeQuran.Running(done = 2, total = 3), controller.state.value.wholeQuran)
        }

    @Test
    fun `a batch that has finished stops claiming to be running`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)
            controller.downloadWholeQuran()
            harness.downloader.emit(mapOf(DownloadKey("ar.alafasy", 1) to DownloadState.Queued))
            assertTrue(controller.state.value.wholeQuran is WholeQuran.Running)

            harness.library.put("ar.alafasy", setOf(1, 2, 112))
            harness.downloader.emit(emptyMap())

            assertNull(controller.state.value.wholeQuran)
        }

    @Test
    fun `cancelling the batch cancels the reciter and leaves what landed alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1))
            val controller = controller(harness, backgroundScope)
            controller.downloadWholeQuran()

            controller.cancelWholeQuran()

            assertEquals(listOf("ar.alafasy"), harness.downloader.batchesCancelled)
            assertEquals(setOf(1), controller.state.value.downloaded)
        }

    @Test
    fun `deleting the surah that is playing stops the player first`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(1, 1)

            controller.deleteSurah("ar.alafasy", 1)

            assertEquals(1, harness.player.stops)
            assertEquals(listOf("ar.alafasy" to 1), harness.library.deleted)
        }

    @Test
    fun `deleting a surah nobody is listening to leaves the playback alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(1, 1)

            controller.deleteSurah("ar.alafasy", 2)

            assertEquals(0, harness.player.stops)
            assertEquals(listOf("ar.alafasy" to 2), harness.library.deleted)
        }

    @Test
    fun `deleting a whole reciter cancels its batch and stops its voice`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1, 2))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(2, 1)

            controller.deleteReciter("ar.alafasy")

            assertEquals(listOf("ar.alafasy"), harness.downloader.batchesCancelled)
            assertEquals(1, harness.player.stops)
            assertEquals(listOf("ar.alafasy"), harness.library.wiped)
        }

    @Test
    fun `storage reports each reciter off the disk and skips the empty ones`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(1))
            harness.library.bytes["ar.alafasy"] = 381_780L
            val controller = controller(harness, backgroundScope)

            val storage = controller.storage()

            assertEquals(381_780L, storage.total)
            assertEquals(mapOf("ar.alafasy" to 381_780L), storage.byReciter)
            assertEquals(0L, storage.of("ar.husary"))
        }

    @Test
    fun `the mobile data switch is written straight through`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            val controller = controller(harness, backgroundScope)

            controller.setDownloadOnMobileData(true)

            assertTrue(harness.settings.stored.value.downloadOnMobileData)
            assertTrue(controller.state.value.downloadOnMobileData)
        }

    // ── Engagement (privacy spec §2.2, §2.4) ─────────────────────────────────────────────

    @Test
    fun `opening the picker marks engagement and asks for a catalogue refresh`() =
        runTest(UnconfinedTestDispatcher()) {
            val engagement = Engagement()
            val controller = controller(Harness(), backgroundScope, engagement = engagement)

            controller.openPicker()

            // Marked first, so the refresher's own gate sees the flag when it asks.
            assertEquals(listOf("mark", "refresh"), engagement.events)
        }

    @Test
    fun `the header button marks engagement without a refresh`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        val controller = controller(harness, backgroundScope, engagement = engagement)

        controller.onHeaderTap(1, 1)

        assertEquals(listOf("mark"), engagement.events, "a tap to play is not a reason to fetch the catalogue")
    }

    @Test
    fun `play on an ayah row marks engagement`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val controller = controller(Harness(), backgroundScope, engagement = engagement)

        controller.requestPlay(112, 1)

        assertEquals(listOf("mark"), engagement.events)
    }

    @Test
    fun `the recitation settings screen marks engagement and refreshes`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val controller = controller(Harness(), backgroundScope, engagement = engagement)

        controller.onSettingsOpened()

        assertEquals(listOf("mark", "refresh"), engagement.events)
    }

    @Test
    fun `download the whole Quran marks engagement`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val controller = controller(Harness(), backgroundScope, engagement = engagement)

        controller.downloadWholeQuran()

        assertEquals(listOf("mark"), engagement.events)
    }
}
