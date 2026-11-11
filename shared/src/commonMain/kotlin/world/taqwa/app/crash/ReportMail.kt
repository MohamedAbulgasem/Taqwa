package world.taqwa.app.crash

/**
 * The support email (crash spec §4): a `mailto:` URL the phone's mail app opens with the
 * recipient, subject and body filled in. The person reads it and taps Send there; the app itself
 * sends nothing.
 */
object ReportMail {
    /**
     * What mail apps reliably accept from a link, counted on the decoded body, every newline
     * included. The full report stays on the phone.
     */
    const val BODY_MAX = 1_800

    /** Not localised: it is read by support, and it has to sort and search the same way for everyone. */
    fun subject(info: DeviceInfo): String = "Taqwa report · ${info.appVersion} (${info.build}) · ${info.platform}"

    /**
     * The body's layout, which the person sees as the mail opens: every filled-in line in one block
     * at the top with no blank line inside it (the no-report line, then the device lines, or the
     * crash report's own lines, the blank one below its header left out; then the notification
     * lines, [NotificationDiagnostics], two at most), then a gap, then the empty line where the
     * person types.
     *
     * The body ends with [TYPING_PLACE], three newlines, for what Gmail for Android drew: the owner
     * opened two `mailto:` links built exactly like this body on his Samsung S23 (Android 16) on
     * 9 Nov 2026. Ending with two newlines, Gmail showed three blank lines above the cursor; ending
     * with three, it showed two, the one he prefers. More newlines drawing fewer blank lines is
     * Gmail's own handling of a `mailto:` body, not a count this code can reason its way to, so the
     * constant follows what was seen. Gmail also adds his signature below the cursor. iOS Mail has
     * not yet been seen with this body, nor have Samsung Email, Outlook or Gmail for iOS. Newlines
     * stay %0A: iOS 14.6 Mail showed the RFC's %0D%0A as a literal `<BR>`
     * (https://developer.apple.com/forums/thread/681023).
     *
     * The whole body, the typing place included, is never longer than [BODY_MAX]. A report that
     * would make it longer loses the bottom of its trace, cut on a line so the last frame is whole
     * and ended by [CrashReportFormat.TRIMMED]; its header lines are kept whole.
     */
    fun body(info: DeviceInfo, report: CrashReport?, noReportLine: String, diagnostics: List<String> = emptyList()): String {
        val lines = diagnostics.sumOf { it.length + 1 }
        val data = if (report == null) {
            listOf(
                noReportLine,
                "app: ${info.appVersion} (${info.build})",
                "platform: ${info.platform}",
                "device: ${info.device}",
                "language: ${info.language}",
            ).joinToString("\n")
        } else {
            // Every line of the report as saved, but no blank one: not the one below its header,
            // and none after its last frame, where only the typing place goes.
            val text = report.text.lineSequence().filter { it.isNotBlank() }.joinToString("\n")
            trace(text, room = BODY_MAX - lines - TYPING_PLACE.length)
        }
        return (listOf(data) + diagnostics).joinToString("\n") + TYPING_PLACE
    }

    /**
     * Three newlines after the last filled-in line, which Gmail for Android drew as two blank lines
     * above the cursor (see [body]).
     */
    private const val TYPING_PLACE = "\n\n\n"

    /** [text] whole if it is at most [room] long; otherwise its top, cut on a line, and the trimmed line. */
    private fun trace(text: String, room: Int): String {
        if (text.length <= room) return text
        val keep = (room - 1 - CrashReportFormat.TRIMMED.length).coerceAtLeast(0)
        // Cut on a line boundary so the last frame is whole, never mid-path.
        val cut = text.lastIndexOf('\n', keep).takeIf { it > 0 } ?: keep
        return text.take(cut) + "\n" + CrashReportFormat.TRIMMED
    }

    fun mailto(to: String, subject: String, body: String): String =
        "mailto:$to?subject=${percentEncode(subject)}&body=${percentEncode(body)}"

    /**
     * RFC 3986: unreserved characters as they are, every other byte of the UTF-8 form as %XX.
     * Spaces are %20, never `+`: some mail apps read a `+` in a mailto body literally.
     */
    fun percentEncode(s: String): String = buildString {
        for (byte in s.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                append(ch)
            } else {
                append('%')
                append(HEX[c shr 4])
                append(HEX[c and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
