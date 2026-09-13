package world.taqwa.app.crash

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class CrashLogStoreTest {
    private val fs = FakeFileSystem()
    private val dir = "/data/taqwa".toPath()
    private val store = CrashLogStore(dir, fs)
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en")

    private fun report(at: String) =
        CrashReportFormat.render(RuntimeException("boom at $at"), "main", info, Instant.parse(at))

    @Test
    fun aWrittenReportReadsBack() {
        store.write(report("2026-09-13T21:05:12Z"))
        assertEquals(Instant.parse("2026-09-13T21:05:12Z"), store.read()?.savedAt)
    }

    @Test
    fun theSecondCrashReplacesTheFirst() {
        store.write(report("2026-09-13T21:05:12Z"))
        store.write(report("2026-09-14T08:00:00Z"))
        assertEquals(Instant.parse("2026-09-14T08:00:00Z"), store.read()?.savedAt)
    }

    @Test
    fun aReportIsPendingUntilOfferedAndANewerOneIsPendingAgain() {
        store.write(report("2026-09-13T21:05:12Z"))
        val first = store.pendingOffer()
        assertEquals(Instant.parse("2026-09-13T21:05:12Z"), first?.savedAt)
        store.markOffered(first!!)
        assertNull(store.pendingOffer())
        // Still on the phone for the About row.
        assertEquals(first.savedAt, store.read()?.savedAt)
        store.write(report("2026-09-14T08:00:00Z"))
        assertEquals(Instant.parse("2026-09-14T08:00:00Z"), store.pendingOffer()?.savedAt)
    }

    @Test
    fun nothingWrittenMeansNothingToRead() {
        assertNull(store.read())
        assertNull(store.pendingOffer())
    }

    @Test
    fun aCorruptFileReadsAsNothing() {
        fs.createDirectories(dir)
        fs.write(dir / CrashLogStore.REPORT_FILE) { writeUtf8("garbage") }
        assertNull(store.read())
        assertNull(store.pendingOffer())
    }

    @Test
    fun clearRemovesBothFiles() {
        store.write(report("2026-09-13T21:05:12Z"))
        store.markOffered(store.read()!!)
        store.clear()
        assertNull(store.read())
        assertFalse(fs.exists(dir / CrashLogStore.OFFERED_FILE))
    }

    @Test
    fun anUnwritableDiskNeverThrows() {
        val broken = CrashLogStore("/nope".toPath(), ReadOnlyFileSystem(fs))
        broken.write(report("2026-09-13T21:05:12Z"))
        assertNull(broken.read())
        broken.markOffered(CrashReport(Instant.parse("2026-09-13T21:05:12Z"), "x"))
        broken.clear()
    }
}
