package world.taqwa.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReportMailTest {
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en")
    private val at = Instant.parse("2026-09-13T21:05:12Z")
    private val noReport = "No crash report is saved on this phone."

    /** The two notification lines at their longest (NotificationDiagnosticsTest). */
    private val lines = listOf(
        "notifications: off, exact alarms: off, battery: not optimised, ringer: do not disturb",
        "last alarm: under 1 h ago (no channel), app updated: under 1 h ago",
    )

    /**
     * What follows the newline that ends the last filled-in line: two more. Gmail for Android drew
     * that as two blank lines above the cursor on the owner's phone, 9 Nov 2026 ([ReportMail.body]
     * says what was seen and what was not).
     */
    private val typingPlace = "\n\n"

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
    fun theBodyEndsWithThreeEncodedNewlines() {
        val url = ReportMail.mailto("support@taqwa.world", "s", ReportMail.body(info, null, noReport))
        assertTrue(url.endsWith("language%3A%20en%0A%0A%0A"), url)
    }

    @Test
    fun withoutAReportTheBodySaysSoAndStillCarriesTheDevice() {
        assertEquals(
            "No crash report is saved on this phone.\n" +
                "app: 1.0.0 (20)\n" +
                "platform: Android 16 (SDK 36)\n" +
                "device: samsung SM-S918B\n" +
                "language: en\n" +
                "\n\n",
            ReportMail.body(info, null, noReport),
        )
        assertEquals(ReportMail.body(info, null, noReport), ReportMail.body(info, null, noReport, emptyList()))
    }

    @Test
    fun withoutAReportOneNotificationLineFollowsTheLanguageLine() {
        assertEquals(
            "No crash report is saved on this phone.\n" +
                "app: 1.0.0 (20)\n" +
                "platform: Android 16 (SDK 36)\n" +
                "device: samsung SM-S918B\n" +
                "language: en\n" +
                "notifications: off\n" +
                "\n\n",
            ReportMail.body(info, null, noReport, listOf("notifications: off")),
        )
    }

    @Test
    fun withoutAReportTheTwoNotificationLinesFollowTheLanguageLine() {
        assertEquals(
            "No crash report is saved on this phone.\n" +
                "app: 1.0.0 (20)\n" +
                "platform: Android 16 (SDK 36)\n" +
                "device: samsung SM-S918B\n" +
                "language: en\n" +
                "notifications: off, exact alarms: off, battery: not optimised, ringer: do not disturb\n" +
                "last alarm: under 1 h ago (no channel), app updated: under 1 h ago\n" +
                "\n\n",
            ReportMail.body(info, null, noReport, lines),
        )
    }

    /** Hand-written rather than rendered: a real trace under the test runner is longer than the cap. */
    private val shortReport = "Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\napp: 1.0.0 (20)\nplatform: Android 16 (SDK 36)\n" +
        "device: samsung SM-S918B\nlanguage: en\nthread: main\n\njava.lang.RuntimeException: boom\n\tat a.b.C.d(C.kt:1)\n"

    /** [shortReport] in the mail: every line of it, the blank one below its header left out. */
    private val shortReportInMail = "Taqwa crash report\n" +
        "saved: 2026-09-13T21:05:12Z\n" +
        "app: 1.0.0 (20)\n" +
        "platform: Android 16 (SDK 36)\n" +
        "device: samsung SM-S918B\n" +
        "language: en\n" +
        "thread: main\n" +
        "java.lang.RuntimeException: boom\n" +
        "\tat a.b.C.d(C.kt:1)\n"

    @Test
    fun aShortReportIsEveryLineOfItThenTheTypingPlace() {
        assertEquals(shortReportInMail + typingPlace, ReportMail.body(info, CrashReport(at, shortReport), noReport))
        // A report without its last newline gains one, and one with extra ones loses them, so the
        // typing place always comes straight after the last frame.
        assertEquals(shortReportInMail + typingPlace, ReportMail.body(info, CrashReport(at, shortReport.dropLast(1)), noReport))
        assertEquals(shortReportInMail + typingPlace, ReportMail.body(info, CrashReport(at, shortReport + "\n\n"), noReport))
    }

    @Test
    fun withAShortReportTheNotificationLinesFollowTheLastFrame() {
        assertEquals(
            shortReportInMail + "notifications: off\n" + typingPlace,
            ReportMail.body(info, CrashReport(at, shortReport), noReport, listOf("notifications: off")),
        )
        assertEquals(
            "Taqwa crash report\n" +
                "saved: 2026-09-13T21:05:12Z\n" +
                "app: 1.0.0 (20)\n" +
                "platform: Android 16 (SDK 36)\n" +
                "device: samsung SM-S918B\n" +
                "language: en\n" +
                "thread: main\n" +
                "java.lang.RuntimeException: boom\n" +
                "\tat a.b.C.d(C.kt:1)\n" +
                "notifications: off, exact alarms: off, battery: not optimised, ringer: do not disturb\n" +
                "last alarm: under 1 h ago (no channel), app updated: under 1 h ago\n" +
                "\n\n",
            ReportMail.body(info, CrashReport(at, shortReport.dropLast(1)), noReport, lines),
        )
    }

    /** A trace of many short frames, as a real one is, so a cut lands near the cap. */
    private val header = "Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\n\njava.lang.RuntimeException: boom\n"
    private val headerInMail = "Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\njava.lang.RuntimeException: boom\n"
    private fun frames(n: Int) = (1..n).joinToString("") { "\tat a.b.C.d(C.kt:$it)\n" }
    private val longTrace = header + frames(200)

    /**
     * The body a cut report should give, found without the code's arithmetic: the most frames
     * whose whole body, the trimmed line, the notification lines and the typing place included,
     * is no longer than the cap.
     */
    private fun expectedCut(diagnostics: List<String>): String {
        fun bodyWith(n: Int) = headerInMail + frames(n) + CrashReportFormat.TRIMMED + "\n" +
            diagnostics.joinToString("") { "$it\n" } + typingPlace
        val n = (200 downTo 1).first { bodyWith(it).length <= ReportMail.BODY_MAX }
        return bodyWith(n)
    }

    @Test
    fun aLongReportCutWithTheTwoLinesIsExactlyTheMostFramesThatFit() {
        val body = ReportMail.body(info, CrashReport(at, longTrace), noReport, lines)
        assertEquals(expectedCut(lines), body)
        assertTrue(body.length <= ReportMail.BODY_MAX, "${body.length}")
        // Short frames: the cut leaves less than one frame of the cap unused.
        assertTrue(body.length > ReportMail.BODY_MAX - "\tat a.b.C.d(C.kt:200)\n".length, "${body.length}")
    }

    @Test
    fun aLongReportCutWithoutLinesIsExactlyTheMostFramesThatFit() {
        assertEquals(expectedCut(emptyList()), ReportMail.body(info, CrashReport(at, longTrace), noReport))
        assertEquals(expectedCut(emptyList()), ReportMail.body(info, CrashReport(at, longTrace), noReport, emptyList()))
    }

    @Test
    fun aReportThatFillsTheCapExactlyIsNotCutAndOneCharacterMoreIs() {
        val tail = lines.joinToString("") { "$it\n" } + typingPlace
        // A report sized so the whole body is exactly the cap: the header's blank line is left
        // out, and the report's last newline is the one that ends its line in the mail.
        val pad = ReportMail.BODY_MAX - headerInMail.length - tail.length - 1
        val atCap = ReportMail.body(info, CrashReport(at, header + "x".repeat(pad) + "\n"), noReport, lines)
        assertEquals(headerInMail + "x".repeat(pad) + "\n" + tail, atCap)
        assertEquals(ReportMail.BODY_MAX, atCap.length)

        val cut = ReportMail.body(info, CrashReport(at, header + "x".repeat(pad + 1) + "\n"), noReport, lines)
        // The long line goes whole, the cut falling on the newline before it.
        assertEquals(headerInMail + CrashReportFormat.TRIMMED + "\n" + tail, cut)
    }

    @Test
    fun aTraceWithNoNewlineToCutAtStillStaysUnderTheCap() {
        val body = ReportMail.body(info, CrashReport(at, "x".repeat(5_000)), noReport, lines)
        assertTrue(body.length <= ReportMail.BODY_MAX, "${body.length}")
        assertTrue(body.endsWith("x\n" + CrashReportFormat.TRIMMED + "\n" + lines.joinToString("") { "$it\n" } + typingPlace), body)
    }

    @Test
    fun aRenderedLongReportKeepsItsHeaderAndTheWholeStaysUnderTheCap() {
        val text = CrashReportFormat.render(RuntimeException("x".repeat(5_000)), "main", info, at)
        for (diagnostics in listOf(emptyList(), lines)) {
            val body = ReportMail.body(info, CrashReport(at, text), noReport, diagnostics)
            assertTrue(body.length <= ReportMail.BODY_MAX, "${body.length}")
            assertTrue(body.startsWith("Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\napp: 1.0.0 (20)\n"), body)
            assertTrue(body.endsWith(CrashReportFormat.TRIMMED + "\n" + diagnostics.joinToString("") { "$it\n" } + typingPlace), body)
        }
    }
}
