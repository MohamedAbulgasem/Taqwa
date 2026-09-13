package world.taqwa.app.feature.recitation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.Test
import kotlin.test.assertEquals
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
        override fun play() { plays++ }
        override fun pause() = Unit
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
    }

    private class FakeClips : ClipPort {
        var played = 0
        var stopped = 0
        override fun play(bytes: ByteArray) { played++ }
        override fun stop() { stopped++ }
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
    fun `picking a reciter without this surah leaves the old voice playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness()
            harness.library.put("ar.alafasy", setOf(112))
            val controller = controller(harness, backgroundScope)
            controller.requestPlay(112, 1)
            val loadsBefore = harness.player.loads.size

            controller.pickReciter("ar.husary")

            assertEquals(loadsBefore, harness.player.loads.size)
            assertNull(controller.state.value.sheet)
            assertEquals("ar.husary", controller.state.value.reciter?.id)
            assertEquals("ar.alafasy", controller.state.value.bar?.reciter?.id)
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
