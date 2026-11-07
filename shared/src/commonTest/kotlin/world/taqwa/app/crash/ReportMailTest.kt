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

    /** The two notification lines at their longest (NotificationDiagnosticsTest). */
    private val lines = listOf(
        "notifications: off, exact alarms: off, battery: not optimised, ringer: do not disturb",
        "last alarm: under 1 h ago (no channel), app updated: under 1 h ago",
    )
    private val block = "\n" + lines.joinToString("") { "$it\n" }

    @Test
    fun withoutAReportTheNotificationLinesFollowTheDeviceAfterABlankLine() {
        val body = ReportMail.body(info, null, "No crash report is saved on this phone.", lines)
        assertEquals(
            "No crash report is saved on this phone.\n\napp: 1.0.0 (20)\nplatform: Android 16 (SDK 36)\ndevice: samsung SM-S918B\nlanguage: en\n" +
                "\nnotifications: off, exact alarms: off, battery: not optimised, ringer: do not disturb\n" +
                "last alarm: under 1 h ago (no channel), app updated: under 1 h ago\n",
            body,
        )
    }

    /** A trace of many short frames, as a real one is, so a cut lands near the cap. */
    private val longTrace = "Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\n\njava.lang.RuntimeException: boom\n" +
        (1..200).joinToString("") { "\tat a.b.C.d(C.kt:$it)\n" }

    @Test
    fun noNotificationLinesLeaveTheLongReportExactlyAsItWas() {
        // The cut as it was before the lines existed: at the last newline under the cap.
        val expected = longTrace.take(longTrace.lastIndexOf('\n', ReportMail.BODY_MAX)) + "\n" + CrashReportFormat.TRIMMED
        assertEquals(expected, ReportMail.body(info, CrashReport(at, longTrace), "none"))
        assertEquals(expected, ReportMail.body(info, CrashReport(at, longTrace), "none", emptyList()))
    }

    @Test
    fun aTraceOfShortFramesFillsTheRoomTheLinesLeaveAndNoMore() {
        val body = ReportMail.body(info, CrashReport(at, longTrace), "none", lines)
        assertTrue(body.length <= ReportMail.BODY_MAX, "${body.length}")
        assertTrue(body.length > ReportMail.BODY_MAX - 40, "${body.length}")
        assertTrue(body.endsWith(CrashReportFormat.TRIMMED + "\n" + block), body)
    }

    @Test
    fun aTraceWithNoNewlineToCutAtStillStaysUnderTheCap() {
        val text = "x".repeat(5_000)
        val body = ReportMail.body(info, CrashReport(at, text), "none", lines)
        assertTrue(body.length <= ReportMail.BODY_MAX, "${body.length}")
        assertTrue(body.endsWith(CrashReportFormat.TRIMMED + "\n" + block), body)
    }

    @Test
    fun withAShortReportTheNotificationLinesFollowTheTrace() {
        val text = "Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\n\njava.lang.RuntimeException: boom\n\tat a.b.C.d(C.kt:1)\n"
        val body = ReportMail.body(info, CrashReport(at, text), "none", listOf("notifications: off"))
        assertEquals(text + "\nnotifications: off\n", body)
        // A trace without its last newline gains one, so the blank line is still one.
        assertEquals(text + "\nnotifications: off\n", ReportMail.body(info, CrashReport(at, text.dropLast(1)), "none", listOf("notifications: off")))
    }

    @Test
    fun withALongReportTheTraceMakesRoomAndTheWholeStaysUnderTheCap() {
        val text = CrashReportFormat.render(RuntimeException("x".repeat(5_000)), "main", info, at)
        val body = ReportMail.body(info, CrashReport(at, text), "none", lines)
        assertTrue(body.length <= ReportMail.BODY_MAX, "${body.length}")
        assertTrue(body.startsWith("Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\napp: 1.0.0 (20)\n"), body)
        assertTrue(body.endsWith("\n" + CrashReportFormat.TRIMMED + "\n" + block), body)
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
