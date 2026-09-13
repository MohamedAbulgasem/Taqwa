package world.taqwa.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrashReportFormatTest {
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en-GB")
    private val at = Instant.parse("2026-09-13T21:05:12Z")

    @Test
    fun theHeaderNamesTheAppThePhoneAndTheMoment() {
        val text = CrashReportFormat.render(IllegalStateException("boom"), "main", info, at)
        val lines = text.lines()
        assertEquals("Taqwa crash report", lines[0])
        assertEquals("saved: 2026-09-13T21:05:12Z", lines[1])
        assertEquals("app: 1.0.0 (20)", lines[2])
        assertEquals("platform: Android 16 (SDK 36)", lines[3])
        assertEquals("device: samsung SM-S918B", lines[4])
        assertEquals("language: en-GB", lines[5])
        assertEquals("thread: main", lines[6])
        assertEquals("", lines[7])
    }

    @Test
    fun theTraceAndItsCauseFollowTheHeader() {
        val cause = IllegalArgumentException("root")
        val text = CrashReportFormat.render(IllegalStateException("boom", cause), "main", info, at)
        assertTrue("IllegalStateException: boom" in text, text)
        assertTrue("IllegalArgumentException: root" in text, text)
        assertTrue("theTraceAndItsCauseFollowTheHeader" in text, text)
    }

    @Test
    fun aRunawayTraceIsCutAtTheCap() {
        val huge = RuntimeException("x".repeat(40_000))
        val text = CrashReportFormat.render(huge, "main", info, at)
        assertTrue(text.length <= CrashReportFormat.MAX_CHARS + CrashReportFormat.TRIMMED.length + 1, "${text.length}")
        assertTrue(text.endsWith(CrashReportFormat.TRIMMED))
    }

    @Test
    fun parseReadsTheSavedMomentBack() {
        val text = CrashReportFormat.render(RuntimeException("boom"), "main", info, at)
        val report = CrashReportFormat.parse(text)
        assertEquals(at, report?.savedAt)
        assertEquals(text, report?.text)
    }

    @Test
    fun parseRefusesTextThatIsNotAReport() {
        assertNull(CrashReportFormat.parse(""))
        assertNull(CrashReportFormat.parse("saved: yesterday\nsomething"))
        assertNull(CrashReportFormat.parse("Taqwa crash report\nnot a saved line"))
    }
}
