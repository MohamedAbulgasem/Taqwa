package world.taqwa.app.feature.recitation

import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.Reciter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pure decisions the recitation surface is built on. */
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
    fun `the fraction is the surah's own clock once the player has one`() {
        val state = PlaybackState(
            ayah = 1, ayahCount = 10, positionMs = 0L, durationMs = 0L,
            surahPositionMs = 2_200L, surahDurationMs = 8_800L,
        )
        // Not 0.0 for the first ayah, and not held from before: the clock says a quarter.
        assertEquals(0.25f, barFraction(state, 0.9f), 0.0001f)
        assertEquals(1f, barFraction(state.copy(surahPositionMs = 9_000L), 0f), 0.0001f)
    }

    @Test
    fun `the clock reads minutes and seconds or hours once the surah has them`() {
        assertEquals("0:05", formatClock(5_400L, hours = false))
        assertEquals("12:31", formatClock(751_000L, hours = false))
        assertEquals("2:05:10", formatClock(7_510_000L, hours = true))
        // Both ends of a long surah's line share the shape, so the elapsed clock pads its hour.
        assertEquals("0:12:31", formatClock(751_000L, hours = true))
        assertEquals("0:00", formatClock(-3L, hours = false))
    }

    @Test
    fun `the clock pads with the locale's own zero`() {
        val arabic = { n: Int -> n.toString().map { '٠' + (it - '0') }.joinToString("") }
        assertEquals("١٢:٠٥", formatClock(725_000L, hours = false, digits = arabic))
        assertEquals("٢:٠٠:٠٩", formatClock(7_209_000L, hours = true, digits = arabic))
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
    fun `a queued download says so and the sheet can stop calling it progress`() {
        assertEquals(
            SheetPhase.Downloading(0L, 10L, queued = true),
            sheetPhaseOf(DownloadState.Queued, downloadOnMobileData = false, total = 10L),
        )
        assertEquals(
            SheetPhase.Downloading(4L, 10L),
            sheetPhaseOf(DownloadState.Downloading(4L, 10L), downloadOnMobileData = false, total = 10L),
        )
    }

    @Test
    fun `following leaves the page alone within four seconds of a touch`() {
        var now = 10_000L
        val following = FollowingState { now }
        following.moved(scrolling = true)
        assertEquals(Follow.LEAVE_ALONE, following.decide(away = 0))
        now += FOLLOW_GRACE_MS
        assertEquals(Follow.SCROLL, following.decide(away = 0))
    }

    @Test
    fun `the page's own scroll heard stopping after it returned is not a touch`() = runTest {
        val following = FollowingState { 10_000L }
        following.move { following.moved(scrolling = true) }
        // The list reports that it stopped a frame after the scroll has returned.
        following.moved(scrolling = false)
        assertEquals(Follow.SCROLL, following.decide(away = 0))
    }

    @Test
    fun `a scroll the page did not start counts as a touch however it ends`() = runTest {
        var now = 10_000L
        val following = FollowingState { now }
        // A scroll of the page's own too short to be heard at all, then the reader's: a finger
        // held on the list for five seconds, whose letting go is the touch the four count from.
        following.move { }
        following.moved(scrolling = true)
        now += 5_000L
        following.moved(scrolling = false)
        assertEquals(Follow.LEAVE_ALONE, following.decide(away = 0))
    }

    @Test
    fun `a reader who takes hold of the page as it follows still counts`() = runTest {
        val following = FollowingState { 10_000L }
        // The finger cancels the page's scroll and carries on moving the list itself.
        runCatching { following.move { following.moved(scrolling = true); throw CancellationException("taken over") } }
        following.moved(scrolling = false)
        assertEquals(Follow.LEAVE_ALONE, following.decide(away = 0))
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
        following.moved(scrolling = true)
        following.rearm()
        assertEquals(Follow.SCROLL, following.decide(away = 0))
    }

    @Test
    fun `a followed card that fits below the resting line rests there`() {
        assertEquals(600, followOffset(rest = 600, room = 1_900, card = 900, edge = 30))
    }

    @Test
    fun `a followed card too long for the resting line rises until its foot clears the bar`() {
        assertEquals(400, followOffset(rest = 600, room = 1_900, card = 1_500, edge = 30))
    }

    @Test
    fun `a followed card taller than the screen starts at the top edge`() {
        assertEquals(30, followOffset(rest = 600, room = 1_900, card = 2_500, edge = 30))
    }

    @Test
    fun `a followed card not yet laid out rests at the usual line`() {
        assertEquals(600, followOffset(rest = 600, room = 1_900, card = null, edge = 30))
    }

    @Test
    fun `the bar tries its caption from the most said to the least`() {
        val tried = mutableListOf<String>()
        val caption = barCaption("Ayah 56", "56", "Translation") { tried += it; false }
        assertEquals(listOf("Ayah 56 · Translation", "56 · Translation", "Translation"), tried)
        // Nothing fits at all: the word is still the one thing it says.
        assertEquals(BarCaption(null, "Translation"), caption)
    }

    @Test
    fun `the bar says the whole caption while the voice reads when it fits`() {
        assertEquals(BarCaption("Ayah 56", "Translation"), barCaption("Ayah 56", "56", "Translation") { true })
    }

    @Test
    fun `a caption too wide for the bar drops the word ayah before the reading word`() {
        // "Ayah 56 · Translation" is 21 characters and "56 · Translation" is 16.
        assertEquals(
            BarCaption("56", "Translation"),
            barCaption("Ayah 56", "56", "Translation") { it.length <= 16 },
        )
    }

    @Test
    fun `between readings the bar names the ayah however narrow it is`() {
        assertEquals(BarCaption("Ayah 56", null), barCaption("Ayah 56", "56", null) { false })
    }
}
