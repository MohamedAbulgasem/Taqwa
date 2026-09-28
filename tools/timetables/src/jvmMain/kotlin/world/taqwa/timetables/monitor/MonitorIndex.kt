package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One table the fetchers hold (`archive/tables/monitor/index.tsv`, written by
 * `tools/timetables/monitor/fetch.py` when it changes, never committed): where it is, the point and
 * clock it applies to, how the gate reads it, and its content hash. The fetchers carry every field
 * from their own metadata (derived parameters: ids, points, zones, column layouts), never a printed
 * time. Whether a table is new, changed, or checked with an engine that changed is not the index's
 * to say: the monitor remembers the signature each table was last checked with ([MonitorState]).
 *
 * - [entry]: the registry entry the table is a table of, `id` or `id/unit` (a unit named checks at
 *   the unit's own point, like a gate row with lat and lon empty); null for a mosque calendar.
 * - [survey]: for a mosque calendar, the `official/survey/<folder>` whose faults and outliers
 *   cover it ([Surveys]); the calendar's id is [key].
 * - [fetched]: the date the content last changed.
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
    val fetched: String,
    val note: String,
) {
    val id: String get() = "$source/$key"

    /** Everything the check depends on but the engine: the content and how it is read. */
    val metadata: String
        get() = listOf(hash, path, lat, lon, zone, clock, cc, entry, survey, columns, format, school).joinToString("|")
}

object MonitorIndex {
    val HEADER = listOf(
        "source", "key", "path", "name", "lat", "lon", "zone", "clock", "cc", "entry", "survey", "columns", "format", "school",
        "hash", "fetched", "note",
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
                fetched = row.getValue("fetched"), note = row.getValue("note"),
            )
        }
    }
}

/** One source's run in the fetch log (`monitor/fetch/latest.json`): `ok`, `partial`, `failed` or `skipped`. */
data class SourceRun(val source: String, val status: String, val message: String, val requests: Int, val tables: Map<String, String>)

/**
 * The fetch log `fetch.py` writes: the date it ran, each source's outcome, and [error] when the
 * driver itself broke (then the sources are whatever it got to).
 */
data class FetchLog(val date: LocalDate, val sources: List<SourceRun>, val error: String? = null) {
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
            return FetchLog(LocalDate.parse(root["date"] as String), sources, root["error"] as? String)
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
    /** Read by hand, or run only when named (`fetch.py` treats either field the same way). */
    val manual: Boolean get() = fetcher == "manual" || cadence == "manual"

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

/** Own-table lateness over the limit, as first raised: since when, over how many cells, and the worst minutes. */
data class Lateness(val since: LocalDate, val days: Int, val worst: Int)

/**
 * What the monitor remembers of one table: the [signature] it was last checked with (content,
 * metadata and the engine), its content [hash], the [date], whether it was [red] (checked again
 * next run whatever changed) and its open [lateness].
 */
data class CheckRecord(val signature: String, val hash: String, val date: LocalDate, val red: Boolean, val lateness: Lateness?)

/** A tier-1 item as last notified: its failing days and worst minutes (more of either is news). */
data class Notified(val days: Int, val worst: Int)

/**
 * What the monitor remembers between runs (`monitor/state.json`): each table's check record
 * ([checked]), the attention set last notified ([notified]), the last backup reminder's date and
 * the last run's outcome. Paths in it are relative to the archive root, so the same state serves
 * the owner's Mac and the cloud runner. Written atomically.
 */
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

    val checked: MutableMap<String, CheckRecord> = run {
        val out = linkedMapOf<String, CheckRecord>()
        @Suppress("UNCHECKED_CAST")
        for ((id, v) in (values["checked"] as? Map<String, Any?>).orEmpty()) {
            val r = v as? Map<String, Any?> ?: continue
            @Suppress("UNCHECKED_CAST")
            val late = (r["lateness"] as? Map<String, Any?>)?.let {
                Lateness(LocalDate.parse(it["since"] as String), (it["days"] as? Long ?: 0L).toInt(), (it["worst"] as? Long ?: 0L).toInt())
            }
            out[id] = CheckRecord(
                r["signature"] as? String ?: "", r["hash"] as? String ?: "", LocalDate.parse(r["date"] as String),
                r["red"] as? Boolean ?: false, late,
            )
        }
        // The first monitor's state named the red tables alone: they are checked again, like any table
        // whose signature is not remembered.
        for (id in (values["redTables"] as? List<*>).orEmpty().mapNotNull { it as? String }) {
            if (id !in out) out[id] = CheckRecord("", "", LocalDate(1970, 1, 1), red = true, lateness = null)
        }
        values.remove("redTables")
        out
    }

    var notified: Map<String, Notified>
        get() {
            @Suppress("UNCHECKED_CAST")
            return (values["notified"] as? Map<String, Any?>).orEmpty().mapValues { (_, v) ->
                val m = v as? Map<String, Any?>
                Notified((m?.get("days") as? Long ?: 0L).toInt(), (m?.get("worst") as? Long ?: 0L).toInt())
            }
        }
        set(value) {
            values["notified"] = value.toSortedMap().mapValues { (_, n) -> linkedMapOf("days" to n.days, "worst" to n.worst) }
        }

    fun note(key: String, value: Any?) {
        values[key] = value
    }

    fun save() {
        values["checked"] = checked.toSortedMap().mapValues { (_, r) ->
            linkedMapOf<String, Any?>(
                "signature" to r.signature, "hash" to r.hash, "date" to r.date.toString(), "red" to r.red,
                "lateness" to r.lateness?.let { linkedMapOf("since" to it.since.toString(), "days" to it.days, "worst" to it.worst) },
            ).filterValues { it != null }
        }
        writeAtomic(file, Json.pretty(values))
    }
}

/** Writes [text] to [file] through a sibling temp file and an atomic move, so a kill mid-write leaves the old file. */
fun writeAtomic(file: File, text: String) {
    file.absoluteFile.parentFile.mkdirs()
    val tmp = File(file.absoluteFile.parentFile, ".${file.name}.tmp")
    tmp.writeText(text)
    try {
        Files.move(tmp.toPath(), file.absoluteFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(tmp.toPath(), file.absoluteFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * What the backup step wrote (`monitor/backup.json`): how many files `backup.py` mirrored and the
 * newest file's date; or [error] when the mirror was configured and did not run; or [skipped]
 * when no mirror is configured (the private repository is the backup, ruling R94).
 */
data class BackupRun(
    val date: LocalDate,
    val copied: Int,
    val total: Int,
    val newest: LocalDate?,
    val target: String,
    val error: String? = null,
    val skipped: String? = null,
) {
    companion object {
        fun load(file: File): BackupRun? {
            if (!file.isFile) return null
            @Suppress("UNCHECKED_CAST")
            val root = Json.parse(file.readText()) as Map<String, Any?>
            return BackupRun(
                LocalDate.parse(root["date"] as String), (root["copied"] as? Long ?: 0L).toInt(), (root["total"] as? Long ?: 0L).toInt(),
                (root["newest"] as? String)?.take(10)?.let(LocalDate::parse), root["target"] as? String ?: "",
                root["error"] as? String, root["skipped"] as? String,
            )
        }
    }
}
