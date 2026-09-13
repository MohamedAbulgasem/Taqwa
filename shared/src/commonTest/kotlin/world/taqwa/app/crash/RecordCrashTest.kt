package world.taqwa.app.crash

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordCrashTest {
    private val info = DeviceInfo("1.0.0", "20", "iOS 18.6", "iPhone14,5", "ar")

    @Test
    fun recordingWritesAReportTheStoreReadsBack() {
        val store = CrashLogStore("/data".toPath(), FakeFileSystem())
        recordCrash(store, IllegalStateException("boom"), "main", info, Instant.parse("2026-09-13T21:05:12Z"))
        val report = store.read()
        assertEquals(Instant.parse("2026-09-13T21:05:12Z"), report?.savedAt)
        assertTrue("IllegalStateException: boom" in report!!.text, report.text)
        assertEquals(report, store.pendingOffer())
    }

    @Test
    fun recordingNeverThrowsWhenTheDiskRefusesTheWrite() {
        val store = CrashLogStore("/data".toPath(), ReadOnlyFileSystem(FakeFileSystem()))
        recordCrash(store, RuntimeException("boom"), "main", info)
        assertNull(store.read())
    }

    @Test
    fun theUnknownDeviceStillMakesAReadableReport() {
        val store = CrashLogStore("/data".toPath(), FakeFileSystem())
        recordCrash(store, RuntimeException("boom"), "main", unknownDevice(), Instant.parse("2026-09-13T21:05:12Z"))
        assertTrue("app: ? (?)" in store.read()!!.text)
    }
}
