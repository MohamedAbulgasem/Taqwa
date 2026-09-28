package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.Json
import java.io.File

/**
 * One table the fetchers hold (`archive/tables/monitor/index.tsv`, written by
 * `tools/timetables/monitor/fetch.py` on every run, never committed): where it is, the point and
 * clock it applies to, how the gate reads it, and whether this run found it new, changed or
 * unchanged. The fetchers carry every field from their own metadata (derived parameters: ids,
 * points, zones, column layouts), never a printed time.
 *
 * - [entry]: the registry entry the table is a table of, `id` or `id/unit` (a unit named checks at
 *   the unit's own point, like a gate row with lat and lon empty); null for a mosque calendar.
 * - [survey]: for a mosque calendar, the `official/survey/<folder>` whose faults and outliers
 *   cover it ([Surveys]); the calendar's id is [key].
 * - [status]: `new`, `changed`, `unchanged` (this run), or `held` (not fetched this run).
 */
data class MonitorTable(
    val source: String,
    val key: String,
    val path: String,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val zone: String,
    val clock: String?,
    val cc: String,
    val entry: String?,
    val survey: String?,
    val columns: String,
    val format: String,
    val school: String,
    val hash: String,
    val status: String,
    val fetched: String,
    val note: String,
) {
    val id: String get() = "$source/$key"
    val isNewOrChanged: Boolean get() = status == "new" || status == "changed"
}

object MonitorIndex {
    val HEADER = listOf(
        "source", "key", "path", "name", "lat", "lon", "zone", "clock", "cc", "entry", "survey", "columns", "format", "school",
        "hash", "status", "fetched", "note",
    )

    fun load(file: File): List<MonitorTable> = if (file.isFile) parse(file.readText()) else emptyList()

    fun parse(text: String): List<MonitorTable> {
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split('\t').map { it.trim() }
        require(header == HEADER) { "index.tsv: the header must be ${HEADER.joinToString(" ")}, not ${header.joinToString(" ")}" }
        return lines.drop(1).map { line ->
            val cells = line.split('\t').map { it.trim() }
            require(cells.size == HEADER.size) { "index.tsv: '$line' has ${cells.size} cells" }
            val row = HEADER.zip(cells).toMap()
            fun opt(name: String) = row.getValue(name).takeIf { it.isNotEmpty() }
            MonitorTable(
                source = row.getValue("source"), key = row.getValue("key"), path = row.getValue("path"), name = row.getValue("name"),
                lat = opt("lat")?.toDouble(), lon = opt("lon")?.toDouble(), zone = row.getValue("zone"), clock = opt("clock"),
                cc = row.getValue("cc"), entry = opt("entry"), survey = opt("survey"), columns = row.getValue("columns"),
                format = row.getValue("format"), school = row.getValue("school"), hash = row.getValue("hash"),
                status = row.getValue("status"), fetched = row.getValue("fetched"), note = row.getValue("note"),
            )
        }
    }
}

/** One source's run in the fetch log (`monitor/fetch/latest.json`): `ok`, `partial`, `failed` or `skipped`. */
data class SourceRun(val source: String, val status: String, val message: String, val requests: Int, val tables: Map<String, String>)

/** The fetch log `fetch.py` writes: the date it ran and each source's outcome. */
data class FetchLog(val date: LocalDate, val sources: List<SourceRun>) {
    companion object {
        fun load(file: File): FetchLog? {
            if (!file.isFile) return null
            @Suppress("UNCHECKED_CAST")
            val root = Json.parse(file.readText()) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val sources = (root["sources"] as? Map<String, Any?>).orEmpty().map { (id, v) ->
                val run = v as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                SourceRun(
                    id, run["status"] as? String ?: "failed", run["message"] as? String ?: "",
                    (run["requests"] as? Long ?: 0L).toInt(),
                    (run["tables"] as? Map<String, Any?>).orEmpty().mapValues { it.value as? String ?: "" },
                )
            }
            return FetchLog(LocalDate.parse(root["date"] as String), sources)
        }
    }
}

/**
 * One line of the committed catalogue `official/monitor/sources.tsv`: the source id, the
 * registry entries it proves, its fetcher (`manual` for a source read by hand), its cadence
 * (`weekly`, `monthly`, `manual`), when its next edition is expected (a date, or `-`), the
 * points fetched and a note. Metadata only.
 */
data class Source(
    val id: String,
    val entries: String,
    val fetcher: String,
    val cadence: String,
    val nextExpected: LocalDate?,
    val points: String,
    val note: String,
) {
    val manual: Boolean get() = fetcher == "manual"

    companion object {
        val HEADER = listOf("source", "entries", "fetcher", "cadence", "next_expected", "points", "note")

        fun load(file: File): List<Source> = parse(file.readText())

        fun parse(text: String): List<Source> {
            val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            require(lines.isNotEmpty() && lines.first().split('\t').map { it.trim() } == HEADER) {
                "sources.tsv: the header must be ${HEADER.joinToString(" ")}"
            }
            return lines.drop(1).map { line ->
                val cells = line.split('\t').map { it.trim() }
                require(cells.size == HEADER.size) { "sources.tsv: '$line' has ${cells.size} cells" }
                val row = HEADER.zip(cells).toMap()
                Source(
                    id = row.getValue("source"), entries = row.getValue("entries"), fetcher = row.getValue("fetcher"),
                    cadence = row.getValue("cadence"),
                    nextExpected = row.getValue("next_expected").takeIf { it != "-" && it.isNotEmpty() }?.let(::monthOrDate),
                    points = row.getValue("points"), note = row.getValue("note"),
                )
            }
        }

        /** `2026-12` is its first day; `2026-12-01` itself. */
        private fun monthOrDate(text: String): LocalDate =
            if (text.length == 7) LocalDate.parse("$text-01") else LocalDate.parse(text)
    }
}

/** What the monitor remembers between runs (`monitor/state.json`): the last backup reminder's date. */
class MonitorState(private val file: File) {
    private val values: MutableMap<String, Any?> = if (file.isFile) {
        @Suppress("UNCHECKED_CAST")
        (Json.parse(file.readText()) as Map<String, Any?>).toMutableMap()
    } else {
        mutableMapOf()
    }

    var lastReminder: LocalDate?
        get() = (values["lastReminder"] as? String)?.let(LocalDate::parse)
        set(value) {
            values["lastReminder"] = value?.toString()
        }

    /** The tables whose check needed attention last run: checked again, changed or not, until green. */
    var redTables: Set<String>
        get() = (values["redTables"] as? List<*>).orEmpty().mapNotNull { it as? String }.toSet()
        set(value) {
            values["redTables"] = value.sorted()
        }

    fun note(key: String, value: Any?) {
        values[key] = value
    }

    fun save() {
        file.parentFile.mkdirs()
        file.writeText(Json.pretty(values))
    }
}

/** What `backup.py` wrote (`monitor/backup.json`): how many files it mirrored and the newest file's date. */
data class BackupRun(val date: LocalDate, val copied: Int, val total: Int, val newest: LocalDate?, val target: String) {
    companion object {
        fun load(file: File): BackupRun? {
            if (!file.isFile) return null
            @Suppress("UNCHECKED_CAST")
            val root = Json.parse(file.readText()) as Map<String, Any?>
            return BackupRun(
                LocalDate.parse(root["date"] as String), (root["copied"] as? Long ?: 0L).toInt(), (root["total"] as? Long ?: 0L).toInt(),
                (root["newest"] as? String)?.take(10)?.let(LocalDate::parse), root["target"] as? String ?: "",
            )
        }
    }
}
