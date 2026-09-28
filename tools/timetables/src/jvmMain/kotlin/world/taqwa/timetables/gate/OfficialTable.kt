package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import java.io.File

/**
 * One printed day: its civil [date] and each time column as minutes after that date's local
 * midnight (24:10 is 1450), null where the table prints nothing ("-").
 */
data class OfficialDay(val date: LocalDate, val minutes: List<Int?>)

/**
 * An official table as the gate reads it: its [days] in file order. [unreadable] counts lines the
 * reader could not take (an OCR's "?" after a time, a typo like "112:28"), skipped and reported, never
 * guessed; [duplicates] counts lines whose date an earlier line already had. Both lines of a
 * repeated date are kept and checked: whichever the authority meant, neither may be broken.
 */
class OfficialTable(val days: List<OfficialDay>, val unreadable: Int, val duplicates: Int) {

    companion object {
        /** [file] read by the reader named [format], expecting [columns] time columns. */
        fun read(file: File, format: String, columns: Int): OfficialTable =
            from(OfficialFormats.named(format).read(file.readLines(), columns))

        /** The reader's days, with the lines that repeat a date counted. */
        fun from(read: OfficialFormat.Read): OfficialTable {
            val seen = HashSet<LocalDate>()
            val repeated = read.days.count { !seen.add(it.date) }
            return OfficialTable(read.days, read.unreadable, repeated)
        }
    }
}

/**
 * Where the gate reads a row's table. `open/…` comes from the checkout's own
 * `tools/timetables/official/` ([checkout]), so an openly licensed table committed with the rows
 * that check it is the one read; everything else from the [archive] root. Without the archive
 * root (CI) nothing is [held] and every row is skipped; with it, a table that is not there is a
 * mistake in the gate file.
 */
class OfficialRoots(val archive: File, val checkout: File) {
    val held: Boolean get() = archive.isDirectory

    fun file(path: String): File = if (path.startsWith("open/")) checkout.resolve(path) else archive.resolve(path)

    companion object {
        /** The same folder for both (test fixtures). */
        fun of(dir: File) = OfficialRoots(dir, dir)
    }
}

/**
 * A reader for one layout of official tables. Each reader is one file in
 * `world.taqwa.timetables.gate.formats`, an `object` named after its format in PascalCase plus
 * `Format` (`daily` → `DailyFormat`, `twelve-hour` → `TwelveHourFormat`), and a gate row names it
 * in its `format` column: adding a reader touches neither [Gate] nor any other reader.
 */
interface OfficialFormat {
    /** What a reader took from a file: every day it could read, and how many lines it could not. */
    class Read(val days: List<OfficialDay>, val unreadable: Int)

    /** [lines] of a file, each day with exactly [columns] time columns. */
    fun read(lines: List<String>, columns: Int): Read
}

/** Finds a reader by its name (see [OfficialFormat]). */
object OfficialFormats {
    const val PACKAGE = "world.taqwa.timetables.gate.formats"

    private val cache = HashMap<String, OfficialFormat>()

    fun className(name: String): String =
        PACKAGE + "." + name.split('-', '_').joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } } + "Format"

    @Synchronized
    fun named(name: String): OfficialFormat = cache.getOrPut(name) {
        require(name.matches(Regex("[a-z0-9]+([-_][a-z0-9]+)*"))) { "format '$name': lower-case words joined by '-'" }
        val type = try {
            Class.forName(className(name))
        } catch (_: ClassNotFoundException) {
            throw IllegalArgumentException("no reader for format '$name': add `object ${className(name).substringAfterLast('.')} : OfficialFormat` in $PACKAGE")
        }
        // A Kotlin object's instance is its static INSTANCE field (no kotlin-reflect needed).
        val instance = runCatching { type.getField("INSTANCE").get(null) }.getOrNull()
            ?: type.getDeclaredConstructor().newInstance()
        instance as? OfficialFormat ?: throw IllegalArgumentException("${type.name} is not an OfficialFormat")
    }
}
