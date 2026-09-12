package world.taqwa.app.feature.recitation

import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.Reciter
import kotlin.test.Test
import kotlin.test.assertEquals

/** The three pure decisions the recitation surface is built on. */
class RecitationStateTest {

    private val reciter = Reciter(
        id = "ar.alafasy",
        nameEn = "Mishary Rashid Alafasy",
        nameAr = "مشاري راشد العفاسي",
        style = "murattal",
        kbps = 64,
        gapMs = 300,
        hue = "amber",
        release = "audio-ar.alafasy-v1",
        totalBytes = 1L,
    )

    private fun bar(surah: Int, playing: Boolean) =
        BarState(reciter, surah, ayah = 1, ayahCount = 7, fraction = 0f, playing = playing, buffering = false)

    @Test
    fun `an untouched surah shows the idle speaker`() {
        assertEquals(HeaderState.Idle, headerStateOf(2, "ar.alafasy", emptyMap(), null))
    }

    @Test
    fun `a surah arriving shows the ring at the fraction it has reached`() {
        val downloads = mapOf(
            DownloadKey("ar.alafasy", 2) to DownloadState.Downloading(25L, 100L),
        )
        assertEquals(HeaderState.Downloading(0.25f), headerStateOf(2, "ar.alafasy", downloads, null))
    }

    @Test
    fun `a queued surah shows an empty ring and a verifying one a full ring`() {
        val queued = mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Queued)
        val verifying = mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Verifying)
        assertEquals(HeaderState.Downloading(0f), headerStateOf(2, "ar.alafasy", queued, null))
        assertEquals(HeaderState.Downloading(1f), headerStateOf(2, "ar.alafasy", verifying, null))
    }

    @Test
    fun `a failed download leaves the button idle rather than stuck in a ring`() {
        val failed = mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Failed(DownloadFailure.SERVER))
        assertEquals(HeaderState.Idle, headerStateOf(2, "ar.alafasy", failed, null))
    }

    @Test
    fun `playing beats a download of the same surah`() {
        val downloads = mapOf(DownloadKey("ar.alafasy", 2) to DownloadState.Downloading(25L, 100L))
        assertEquals(HeaderState.Playing, headerStateOf(2, "ar.alafasy", downloads, bar(2, playing = true)))
        assertEquals(HeaderState.Paused, headerStateOf(2, "ar.alafasy", downloads, bar(2, playing = false)))
    }

    @Test
    fun `a bar for another surah does not light this surah's button`() {
        assertEquals(HeaderState.Idle, headerStateOf(2, "ar.alafasy", emptyMap(), bar(112, playing = true)))
    }

    @Test
    fun `the fraction counts ayahs and fills in between them`() {
        val state = PlaybackState(ayah = 3, ayahCount = 10, positionMs = 500L, durationMs = 1_000L)
        assertEquals(0.25f, barFraction(state, 0f), 0.0001f)
    }

    @Test
    fun `the fraction holds its last value while the platform reports no duration`() {
        val between = PlaybackState(ayah = 4, ayahCount = 10, positionMs = 0L, durationMs = 0L)
        assertEquals(0.25f, barFraction(between, 0.25f), 0.0001f)
    }

    @Test
    fun `the fraction is zero with nothing loaded`() {
        assertEquals(0f, barFraction(PlaybackState.EMPTY, 0.6f), 0.0001f)
    }

    @Test
    fun `the sheet is ready with the Wi-Fi note until mobile data is allowed`() {
        assertEquals(SheetPhase.Ready(true), sheetPhaseOf(null, downloadOnMobileData = false, total = 10L))
        assertEquals(SheetPhase.Ready(false), sheetPhaseOf(null, downloadOnMobileData = true, total = 10L))
    }

    @Test
    fun `verifying is drawn as a full progress bar rather than as a fourth state`() {
        assertEquals(
            SheetPhase.Downloading(10L, 10L),
            sheetPhaseOf(DownloadState.Verifying, downloadOnMobileData = false, total = 10L),
        )
    }

    @Test
    fun `following leaves the page alone within four seconds of a touch`() {
        var now = 10_000L
        val following = FollowingState { now }
        following.moved()
        assertEquals(Follow.LEAVE_ALONE, following.decide(away = 0))
        now += FOLLOW_GRACE_MS
        assertEquals(Follow.SCROLL, following.decide(away = 0))
    }

    @Test
    fun `following offers the pill rather than dragging the reader back a screen`() {
        val following = FollowingState { 0L }
        assertEquals(Follow.PILL, following.decide(away = 2))
    }

    @Test
    fun `asking to go back re-arms following at once`() {
        var now = 10_000L
        val following = FollowingState { now }
        following.moved()
        following.rearm()
        assertEquals(Follow.SCROLL, following.decide(away = 0))
    }
}
