package world.taqwa.app.crash

import kotlin.time.Instant

/** A report read back from disk: when it was saved, and the whole text as saved. */
data class CrashReport(val savedAt: Instant, val text: String)

/**
 * The text of a crash report (crash spec §2): header lines first, a blank line, then the trace.
 * The header is fixed in order so support can read it at a glance, and so [parse] can find the
 * moment it was saved without a real parser. The cap keeps a runaway recursive exception from
 * filling the disk; what is cut is the bottom of the trace, which is the least useful part.
 */
object CrashReportFormat {
    const val TITLE = "Taqwa crash report"
    const val SAVED_PREFIX = "saved: "
    const val MAX_CHARS = 16_000
    const val TRIMMED = "… (trimmed)"

    fun render(throwable: Throwable, threadName: String, info: DeviceInfo, savedAt: Instant): String {
        val header = buildString {
            appendLine(TITLE)
            appendLine("$SAVED_PREFIX$savedAt")
            appendLine("app: ${info.appVersion} (${info.build})")
            appendLine("platform: ${info.platform}")
            appendLine("device: ${info.device}")
            appendLine("language: ${info.language}")
            appendLine("thread: $threadName")
            appendLine()
        }
        val full = header + throwable.stackTraceToString()
        if (full.length <= MAX_CHARS) return full
        return full.take(MAX_CHARS) + "\n" + TRIMMED
    }

    fun parse(text: String): CrashReport? {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != TITLE) return null
        if (!lines.hasNext()) return null
        val saved = lines.next()
        if (!saved.startsWith(SAVED_PREFIX)) return null
        val at = runCatching { Instant.parse(saved.removePrefix(SAVED_PREFIX)) }.getOrNull() ?: return null
        return CrashReport(at, text)
    }
}
