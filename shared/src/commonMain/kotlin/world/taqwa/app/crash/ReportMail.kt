package world.taqwa.app.crash

/**
 * The support email (crash spec §4): a `mailto:` URL the phone's mail app opens with the
 * recipient, subject and body filled in. The person reads it and taps Send there; the app itself
 * sends nothing.
 */
object ReportMail {
    /** What mail apps reliably accept from a link. The full report stays on the phone. */
    const val BODY_MAX = 1_800

    /** Not localised: it is read by support, and it has to sort and search the same way for everyone. */
    fun subject(info: DeviceInfo): String = "Taqwa report · ${info.appVersion} (${info.build}) · ${info.platform}"

    /**
     * [diagnostics] are the notification lines ([NotificationDiagnostics], two at most), after a
     * blank line below everything else; none leaves the body exactly as it was before they existed.
     */
    fun body(info: DeviceInfo, report: CrashReport?, noReportLine: String, diagnostics: List<String> = emptyList()): String {
        val block = block(diagnostics)
        if (report == null) {
            return buildString {
                appendLine(noReportLine)
                appendLine()
                appendLine("app: ${info.appVersion} (${info.build})")
                appendLine("platform: ${info.platform}")
                appendLine("device: ${info.device}")
                appendLine("language: ${info.language}")
            } + block
        }
        val text = report.text
        // With no block the trace has the whole cap and may run over it by its "trimmed" line, as
        // it always has. With one, the trace makes room for the block, the newline that may join
        // them and that line, so the whole stays under the cap.
        val fits = if (block.isEmpty()) text.length <= BODY_MAX else text.length + 1 + block.length <= BODY_MAX
        val room = if (block.isEmpty()) BODY_MAX else BODY_MAX - block.length - 1 - (CrashReportFormat.TRIMMED.length + 1)
        val trace = if (fits) {
            text
        } else {
            // Cut on a line boundary so the last frame is whole, never mid-path.
            val cut = text.lastIndexOf('\n', room).takeIf { it > 0 } ?: room
            text.take(cut) + "\n" + CrashReportFormat.TRIMMED
        }
        if (block.isEmpty()) return trace
        return (if (trace.endsWith('\n')) trace else trace + "\n") + block
    }

    /** A blank line, then each of [lines] on its own line; nothing at all when there are none. */
    private fun block(lines: List<String>): String =
        if (lines.isEmpty()) "" else lines.joinToString(separator = "", prefix = "\n") { "$it\n" }

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
