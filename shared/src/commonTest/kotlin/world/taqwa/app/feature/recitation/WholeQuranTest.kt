package world.taqwa.app.feature.recitation

import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.recitation.SurahAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What "Download the whole Quran" costs and how far it has got (spec §5.4, §12.8). */
class WholeQuranTest {

    /** Four surahs of a megabyte each, so every sum in here is readable at a glance. */
    private val reciter = Reciter(
        id = "ar.alafasy",
        nameEn = "Mishary Rashid Alafasy",
        nameAr = "مشاري راشد العفاسي",
        style = "murattal",
        kbps = 64,
        gapMs = 300,
        hue = "amber",
        release = "audio-ar.alafasy-v1",
        totalBytes = 10_000_000L,
        surahs = listOf(
            SurahAsset(1, 1_000_000L, "a"),
            SurahAsset(2, 2_000_000L, "b"),
            SurahAsset(3, 3_000_000L, "c"),
            SurahAsset(4, 4_000_000L, "d"),
        ),
    )

    private fun key(surah: Int) = DownloadKey("ar.alafasy", surah)

    @Test
    fun `the price is the surahs that are missing and not the whole reciter`() {
        assertEquals(10_000_000L, wholeQuranBytes(reciter, emptySet()))
        assertEquals(7_000_000L, wholeQuranBytes(reciter, setOf(1, 2)))
        assertEquals(0L, wholeQuranBytes(reciter, setOf(1, 2, 3, 4)))
    }

    @Test
    fun `a surah on the phone that the catalogue no longer publishes changes no price`() {
        assertEquals(10_000_000L, wholeQuranBytes(reciter, setOf(99)))
        assertEquals(listOf(1, 2, 3, 4), missingSurahs(reciter, setOf(99)))
    }

    @Test
    fun `nothing downloading is an offer priced at what is left`() {
        val row = wholeQuranOf(reciter, setOf(1), emptyMap(), declared = false)
        assertEquals(WholeQuran.Offer(bytes = 9_000_000L, missing = 3), row)
    }

    @Test
    fun `a complete reciter offers nothing at all`() {
        assertNull(wholeQuranOf(reciter, setOf(1, 2, 3, 4), emptyMap(), declared = false))
    }

    @Test
    fun `a reciter with no catalogue entry offers nothing`() {
        assertNull(wholeQuranOf(null, emptySet(), emptyMap(), declared = false))
        assertNull(wholeQuranOf(reciter.copy(surahs = emptyList()), emptySet(), emptyMap(), declared = false))
    }

    @Test
    fun `one surah in flight is not a batch unless this app started one`() {
        val downloads = mapOf(key(2) to DownloadState.Downloading(1L, 2_000_000L))
        // The reader tapped Download on the sheet: the settings row must not claim a batch.
        assertEquals(
            WholeQuran.Offer(bytes = 9_000_000L, missing = 3),
            wholeQuranOf(reciter, setOf(1), downloads, declared = false),
        )
        assertEquals(
            WholeQuran.Running(done = 1, total = 4),
            wholeQuranOf(reciter, setOf(1), downloads, declared = true),
        )
    }

    @Test
    fun `two surahs of one reciter in flight is a batch even after a restart`() {
        val downloads = mapOf(
            key(2) to DownloadState.Downloading(1L, 2_000_000L),
            key(3) to DownloadState.Queued,
        )
        assertEquals(
            WholeQuran.Running(done = 1, total = 4),
            wholeQuranOf(reciter, setOf(1), downloads, declared = false),
        )
    }

    @Test
    fun `the count is of the whole reciter and not of the batch`() {
        // Three already owned, the fourth arriving: "4 of 4" is only true when it lands, and what
        // the reader wants meanwhile is 3 of 4 — the same arithmetic the summary notification uses.
        val downloads = mapOf(key(4) to DownloadState.Downloading(1L, 4_000_000L))
        val row = wholeQuranOf(reciter, setOf(1, 2, 3), downloads, declared = true)
        assertEquals(WholeQuran.Running(done = 3, total = 4), row)
        assertEquals(0.75f, (row as WholeQuran.Running).fraction)
    }

    @Test
    fun `a surah outside the catalogue is not counted towards the total`() {
        val downloads = mapOf(key(4) to DownloadState.Queued)
        assertEquals(
            WholeQuran.Running(done = 1, total = 4),
            wholeQuranOf(reciter, setOf(1, 99), downloads, declared = true),
        )
    }

    @Test
    fun `a batch refused for room says so and keeps its price`() {
        val downloads = mapOf(
            key(1) to DownloadState.Failed(DownloadFailure.NOT_ENOUGH_SPACE),
            key(2) to DownloadState.Failed(DownloadFailure.NOT_ENOUGH_SPACE),
        )
        assertEquals(
            WholeQuran.Failed(DownloadFailure.NOT_ENOUGH_SPACE, bytes = 10_000_000L),
            wholeQuranOf(reciter, emptySet(), downloads, declared = true),
        )
    }

    @Test
    fun `a failure nobody asked this row for is left to the sheet that did`() {
        val downloads = mapOf(key(2) to DownloadState.Failed(DownloadFailure.CHECKSUM))
        assertEquals(
            WholeQuran.Offer(bytes = 10_000_000L, missing = 4),
            wholeQuranOf(reciter, emptySet(), downloads, declared = false),
        )
    }

    @Test
    fun `work still moving beats a failure beside it`() {
        val downloads = mapOf(
            key(1) to DownloadState.Failed(DownloadFailure.SERVER),
            key(2) to DownloadState.Downloading(1L, 2_000_000L),
        )
        assertEquals(
            WholeQuran.Running(done = 0, total = 4),
            wholeQuranOf(reciter, emptySet(), downloads, declared = true),
        )
    }

    @Test
    fun `another reciter downloading is none of this row's business`() {
        val downloads = mapOf(
            DownloadKey("ar.husary", 1) to DownloadState.Queued,
            DownloadKey("ar.husary", 2) to DownloadState.Queued,
        )
        assertEquals(
            WholeQuran.Offer(bytes = 10_000_000L, missing = 4),
            wholeQuranOf(reciter, emptySet(), downloads, declared = false),
        )
    }
}
