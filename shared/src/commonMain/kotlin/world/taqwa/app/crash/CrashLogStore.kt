package world.taqwa.app.crash

import okio.FileSystem
import okio.Path

/**
 * The one crash report kept on the phone (crash spec §2) and whether it has been offered yet
 * (§3.1). Two files beside the settings: `crash-report.txt` is the report, `crash-report.offered`
 * holds the `saved:` moment of the report the sheet already showed, so the same report is never
 * offered twice while a newer one is offered again.
 *
 * Every method swallows I/O failure. This runs inside a crash handler, where a second exception
 * would only hide the first, and at start-up, where a bad disk must not stop the app opening.
 */
class CrashLogStore(private val dir: Path, private val fs: FileSystem = FileSystem.SYSTEM) {
    private val reportFile = dir / REPORT_FILE
    private val offeredFile = dir / OFFERED_FILE

    fun write(text: String) {
        runCatching {
            fs.createDirectories(dir)
            fs.write(reportFile) { writeUtf8(text) }
        }
    }

    fun read(): CrashReport? = runCatching {
        if (!fs.exists(reportFile)) return null
        CrashReportFormat.parse(fs.read(reportFile) { readUtf8() })
    }.getOrNull()

    /** The report, if there is one the sheet has not shown yet. */
    fun pendingOffer(): CrashReport? {
        val report = read() ?: return null
        val offered = runCatching {
            if (fs.exists(offeredFile)) fs.read(offeredFile) { readUtf8() }.trim() else null
        }.getOrNull()
        return if (offered == report.savedAt.toString()) null else report
    }

    fun markOffered(report: CrashReport) {
        runCatching {
            fs.createDirectories(dir)
            fs.write(offeredFile) { writeUtf8(report.savedAt.toString()) }
        }
    }

    fun clear() {
        runCatching { fs.delete(reportFile, mustExist = false) }
        runCatching { fs.delete(offeredFile, mustExist = false) }
    }

    companion object {
        const val REPORT_FILE = "crash-report.txt"
        const val OFFERED_FILE = "crash-report.offered"
    }
}
