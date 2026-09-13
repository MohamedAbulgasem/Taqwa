package world.taqwa.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReportMailTest {
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en")
    private val at = Instant.parse("2026-09-13T21:05:12Z")

    @Test
    fun theSubjectNamesTheVersionAndThePlatform() {
        assertEquals("Taqwa report · 1.0.0 (20) · Android 16 (SDK 36)", ReportMail.subject(info))
    }

    @Test
    fun encodingKeepsUnreservedCharactersAndEscapesTheRest() {
        assertEquals("a-b_c.d~e", ReportMail.percentEncode("a-b_c.d~e"))
        assertEquals("a%20b%0Ac", ReportMail.percentEncode("a b\nc"))
        assertEquals("%26%3F%23%2B%25", ReportMail.percentEncode("&?#+%"))
        assertEquals("%D8%AA%D9%82%D9%88%D9%89", ReportMail.percentEncode("تقوى"))
    }

    @Test
    fun mailtoCarriesRecipientSubjectAndBody() {
        val url = ReportMail.mailto("support@taqwa.world", "Hi there", "line 1\nline 2")
        assertEquals("mailto:support@taqwa.world?subject=Hi%20there&body=line%201%0Aline%202", url)
    }

    @Test
    fun aShortReportIsTheBodyUntouched() {
        // Hand-written rather than rendered: a real trace under the test runner is longer than
        // the cap, which is exactly what the next test is for.
        val text = "Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\napp: 1.0.0 (20)\nplatform: Android 16 (SDK 36)\n" +
            "device: samsung SM-S918B\nlanguage: en\nthread: main\n\njava.lang.RuntimeException: boom\n\tat a.b.C.d(C.kt:1)\n"
        val body = ReportMail.body(info, CrashReport(at, text), "No crash report is saved on this phone.")
        assertEquals(text, body)
    }

    @Test
    fun aLongReportKeepsItsHeaderAndCutsTheTraceOnALine() {
        val text = CrashReportFormat.render(RuntimeException("x".repeat(5_000)), "main", info, at)
        val body = ReportMail.body(info, CrashReport(at, text), "none")
        assertTrue(body.length <= ReportMail.BODY_MAX + CrashReportFormat.TRIMMED.length + 1, "${body.length}")
        assertTrue(body.startsWith("Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\napp: 1.0.0 (20)\n"), body)
        assertTrue(body.endsWith("\n" + CrashReportFormat.TRIMMED), body)
    }

    @Test
    fun withoutAReportTheBodySaysSoAndStillCarriesTheDevice() {
        val body = ReportMail.body(info, null, "No crash report is saved on this phone.")
        assertEquals(
            "No crash report is saved on this phone.\n\napp: 1.0.0 (20)\nplatform: Android 16 (SDK 36)\ndevice: samsung SM-S918B\nlanguage: en\n",
            body,
        )
    }
}
