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

    fun body(info: DeviceInfo, report: CrashReport?, noReportLine: String): String {
        if (report == null) {
            return buildString {
                appendLine(noReportLine)
                appendLine()
                appendLine("app: ${info.appVersion} (${info.build})")
                appendLine("platform: ${info.platform}")
                appendLine("device: ${info.device}")
                appendLine("language: ${info.language}")
            }
        }
        val text = report.text
        if (text.length <= BODY_MAX) return text
        // Cut on a line boundary so the last frame is whole, never mid-path.
        val cut = text.lastIndexOf('\n', BODY_MAX).takeIf { it > 0 } ?: BODY_MAX
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
