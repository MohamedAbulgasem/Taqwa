package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.TimedEvent
import java.io.File

/**
 * One event an official column can hold. A start is checked for being **early** (shown before the
 * official minute) and measured in minutes late; an end (sunrise, the end of eating, imsak) for
 * being a **late end** (shown after it) and measured in minutes early. [key] names it in the report,
 * and the stamps; [timed] is the registry's name for it, which a late-limit exception covers (R41).
 */
enum class Event(val key: String, val isStart: Boolean, val timed: TimedEvent) {
    FAJR("fajr", true, TimedEvent.FAJR),
    SUNRISE("sunrise", false, TimedEvent.SUNRISE),
    DHUHR("dhuhr", true, TimedEvent.DHUHR),
    ASR_STANDARD("asrStandard", true, TimedEvent.ASR),
    ASR_HANAFI("asrHanafi", true, TimedEvent.ASR),
    MAGHRIB("maghrib", true, TimedEvent.MAGHRIB),
    ISHA("isha", true, TimedEvent.ISHA),
    END_OF_EATING("endOfEating", false, TimedEvent.END_OF_EATING),
    IMSAK("imsak", false, TimedEvent.IMSAK);

    companion object {
        fun byKey(key: String): Event? = entries.firstOrNull { it.key == key }
    }
}

enum class Split { FIT, TEST }

/**
 * One line of a gate file: an official table and how to check the engine against it.
 *
 * - [path]: the table, relative to the official root (`tools/timetables/official/`).
 * - [entry]: the registry id it is checked against (by id, never by place); [unit]: an authority
 *   unit's id when the `entry` column reads `<entry>/<unit>`.
 * - [lat], [lon]: where the table applies; null (both empty) to take the unit's reference point, or
 *   the method's own fixed point (MUIS).
 * - [zone]: the place's civil IANA zone, the one the engine computes in.
 * - [clock]: the zone the table is printed in, where it is not [zone] (ICC London in GMT all year,
 *   Morocco's legal clock): an IANA zone or a fixed offset such as `UTC+01:00`; null for [zone].
 * - [columns]: what each time column holds, in order; a column may hold several events (`F+E`) or
 *   none (`-`).
 * - [format]: the reader for the file ([OfficialFormats]).
 * - [school]: the school of the table's `A` column, or null when it has none.
 * - [split]: [Split.FIT] rows fit margins ([Fitter]); [Split.TEST] rows are the held-out proof.
 * - [member]: for a cautious (class C) entry, the id of the member whose printed table this is
 *   (ruling R38); null for every other entry.
 */
data class GateRow(
    val source: String,
    val line: Int,
    val path: String,
    val entry: String,
    val unit: String?,
    val lat: Double?,
    val lon: Double?,
    val zone: String,
    val columns: List<List<Event>>,
    val format: String,
    val school: AsrSchool?,
    val split: Split,
    val note: String,
    val member: String? = null,
    val clock: String? = null,
) {
    val where: String get() = "$source:$line"

    /** The zone the printed times are read in. */
    val clockZone: String get() = clock ?: zone
}

/** Why a cautious member's day is excused (ruling R117): its table does not publish it, or publishes it wrongly. */
enum class ExcuseKind(val key: String, val words: String) {
    /** The table prints the day wrongly: a fault recorded with its reason. */
    FAULT("fault", "recorded faults"),

    /** The table does not print the day at all. */
    UNPUBLISHED("unpublished", "days it does not publish");

    companion object {
        fun byKey(key: String): ExcuseKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One `@excuse` line of a gate file (ruling R117): days a cautious [entry]'s [member] table leaves
 * out of its rows for a recorded reason — it does not publish them, or publishes them wrongly
 * ([kind]) — at [at] (one point of the member's rows, as the rows give it), or at every point its
 * rows are read at (null, written `*`). [reason] says why in words: dates and differences only,
 * never a printed time (ruling R69). The gate records each such day, per member and point, where
 * the member's rows there do not hold it; a page shows the day only where another member's table
 * was checked on it (spec §2).
 *
 *     @excuse  <entry>  <member>  <* | lat,lon>  <yyyy-mm-dd[..yyyy-mm-dd]>  <fault | unpublished>  <reason>
 */
data class Excuse(
    val source: String,
    val line: Int,
    val entry: String,
    val member: String,
    val at: GeoPoint?,
    val days: ClosedRange<LocalDate>,
    val kind: ExcuseKind,
    val reason: String,
) {
    val where: String get() = "$source:$line"
}

/**
 * Every gate row, from the `official/gate/<group>.tsv` files; [sources] are the files read, and
 * [excuses] their `@excuse` lines (ruling R117). An exception to a late limit is not the gate
 * file's to make: it lives with its registry entry or unit (ruling R37, `LateLimit`), so that the
 * gate proves and About states the same number.
 */
data class GateManifest(
    val rows: List<GateRow>,
    val sources: List<String> = rows.map { it.source }.distinct(),
    val excuses: List<Excuse> = emptyList(),
) {

    /**
     * Only the rows of [groups] (file names without `.tsv`) and [entries], when given. A group or
     * an entry that matches nothing is a mistake in the command, and fails.
     */
    fun only(groups: Set<String> = emptySet(), entries: Set<String> = emptySet()): GateManifest {
        val problems = mutableListOf<String>()
        val known = sources.map { it.removeSuffix(".tsv") }.toSet()
        for (group in (groups - known).sorted()) problems += "no gate file $group.tsv (known: ${known.sorted().joinToString(" ")})"
        val inGroups = rows.filter { groups.isEmpty() || it.source.removeSuffix(".tsv") in groups }
        for (entry in (entries - inGroups.map { it.entry }.toSet()).sorted()) {
            problems += "no gate row for entry $entry" + if (groups.isEmpty()) "" else " in ${groups.sorted().joinToString(" ")}"
        }
        if (problems.isNotEmpty()) throw GateError(problems)
        return GateManifest(
            rows = inGroups.filter { entries.isEmpty() || it.entry in entries },
            sources = sources.filter { groups.isEmpty() || it.removeSuffix(".tsv") in groups },
            excuses = excuses.filter { excuse ->
                (groups.isEmpty() || excuse.source.removeSuffix(".tsv") in groups) && (entries.isEmpty() || excuse.entry in entries)
            },
        )
    }

    companion object {
        /**
         * The columns every gate file's header names, in any order. `format` is the reader's name
         * (`daily` for the one-line-per-day layout of `official/README.md`).
         */
        val HEADER = listOf("path", "entry", "lat", "lon", "zone", "columns", "format", "school", "split", "note")

        /**
         * Columns a header may add: `member` (a cautious entry's member, ruling R38) and `clock`
         * (the zone the table is printed in, when it is not the place's `zone`).
         */
        val OPTIONAL = listOf("member", "clock")

        /**
         * Column tokens: `F` Fajr, `S` sunrise, `D` Dhuhr, `A` Asr in the row's `school`, `As` the
         * Standard and `Ah` the Hanafi Asr whatever the school, `M` Maghrib, `I` Isha, `E` the end
         * of eating, `Im` imsak (the authority's printed precaution), `-` a column not checked.
         * `+` joins several events on one column: `F+E` where the Fajr printed is also when eating
         * stops. (`I` is Isha, as in the manifest's notes; the brief's `Is` is refused, so that no
         * table is read with the end of eating and Isha swapped.)
         */
        private val TOKENS = mapOf(
            "F" to Event.FAJR, "S" to Event.SUNRISE, "D" to Event.DHUHR, "As" to Event.ASR_STANDARD,
            "Ah" to Event.ASR_HANAFI, "M" to Event.MAGHRIB, "I" to Event.ISHA, "E" to Event.END_OF_EATING,
            "Im" to Event.IMSAK,
        )

        /** Every `*.tsv` directly in [dir], in name order; an absent directory has no rows. */
        fun load(dir: File): GateManifest {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".tsv") }?.sortedBy { it.name } ?: emptyList()
            val parsed = files.map { parse(it.name, it.readText()) }
            return GateManifest(parsed.flatMap { it.rows }, files.map { it.name }, parsed.flatMap { it.excuses })
        }

        /**
         * One gate file. Blank lines and `#` comments are skipped, and `@excuse` lines read as
         * [Excuse]s wherever they stand; the first other line is the header. Every problem is
         * reported at once, with its line.
         */
        fun parse(source: String, text: String): GateManifest {
            val problems = mutableListOf<String>()
            val rows = mutableListOf<GateRow>()
            val excuses = mutableListOf<Excuse>()
            var header: List<String>? = null
            var headerOk = false
            text.lines().forEachIndexed { index, raw ->
                val line = index + 1
                val where = "$source:$line"
                if (raw.isBlank() || raw.startsWith("#")) return@forEachIndexed
                val cells = raw.split('\t').map { it.trim() }
                when {
                    cells[0] == "@lateLimit" ->
                        problems += "$where: a late-limit exception lives with its registry entry or unit (ruling R37: LateLimit)"
                    cells[0] == EXCUSE -> excuse(cells, source, line, problems)?.let(excuses::add)
                    cells[0].startsWith("@") -> problems += "$where: unknown directive ${cells[0]}"
                    header == null -> {
                        val missing = HEADER - cells.toSet()
                        val unknown = cells - (HEADER + OPTIONAL).toSet()
                        headerOk = missing.isEmpty() && unknown.isEmpty() && cells.size == cells.toSet().size
                        if (!headerOk) {
                            problems += "$where: the header must name ${HEADER.joinToString(" ")}" +
                                " (and may add ${OPTIONAL.joinToString(" ")})" +
                                (if (missing.isNotEmpty()) "; missing ${missing.joinToString(" ")}" else "") +
                                (if (unknown.isNotEmpty()) "; unknown ${unknown.joinToString(" ")}" else "")
                        }
                        header = cells
                    }
                    else -> {
                        val names: List<String> = header
                        // A header already refused: its rows cannot be read.
                        if (!headerOk) return@forEachIndexed
                        if (cells.size > names.size) {
                            problems += "$where: ${cells.size} cells for ${names.size} columns"
                            return@forEachIndexed
                        }
                        val row = (HEADER + OPTIONAL).associateWith { "" } + names.zip(cells + List(names.size - cells.size) { "" })
                        row(row, source, line, problems)?.let(rows::add)
                    }
                }
            }
            if (problems.isNotEmpty()) throw GateError(problems)
            return GateManifest(rows, listOf(source), excuses)
        }

        /** The directive of an [Excuse] line. */
        const val EXCUSE = "@excuse"

        private fun excuse(cells: List<String>, source: String, line: Int, problems: MutableList<String>): Excuse? {
            val where = "$source:$line"
            if (cells.size != 7 || cells.any { it.isEmpty() }) {
                return null.also { problems += "$where: $EXCUSE <entry> <member> <* or lat,lon> <date or from..to> <fault or unpublished> <reason>" }
            }
            val atText = cells[3]
            val at = if (atText == "*") {
                null
            } else {
                val parts = atText.split(',').map { it.trim().toDoubleOrNull() }
                val lat = parts.getOrNull(0)
                val lon = parts.getOrNull(1)
                if (parts.size != 2 || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    return null.also { problems += "$where: point '$atText' (* or lat,lon)" }
                }
                GeoPoint(lat, lon)
            }
            val days = runCatching {
                val ends = cells[4].split("..")
                require(ends.size in 1..2)
                LocalDate.parse(ends.first())..LocalDate.parse(ends.last())
            }.getOrNull()
            if (days == null || days.start > days.endInclusive) {
                return null.also { problems += "$where: dates '${cells[4]}' (yyyy-mm-dd or from..to)" }
            }
            val kind = ExcuseKind.byKey(cells[5])
                ?: return null.also { problems += "$where: '${cells[5]}' (${ExcuseKind.entries.joinToString(" or ") { it.key }})" }
            return Excuse(source, line, cells[1], cells[2], at, days, kind, cells[6])
        }

        private fun row(cells: Map<String, String>, source: String, line: Int, problems: MutableList<String>): GateRow? {
            val where = "$source:$line"
            val before = problems.size
            fun need(name: String): String = cells.getValue(name).also { if (it.isEmpty()) problems += "$where: $name is empty" }

            val path = need("path")
            if (path.startsWith("/") || ".." in path.split('/')) problems += "$where: path must be relative to the official folder"
            val (entry, unit) = need("entry").split('/', limit = 2).let { it[0] to it.getOrNull(1) }
            val latText = cells.getValue("lat")
            val lonText = cells.getValue("lon")
            val lat = latText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val lon = lonText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            if (latText.isNotEmpty() && (lat == null || lat !in -90.0..90.0)) problems += "$where: lat '$latText'"
            if (lonText.isNotEmpty() && (lon == null || lon !in -180.0..180.0)) problems += "$where: lon '$lonText'"
            if (latText.isEmpty() != lonText.isEmpty()) problems += "$where: give both lat and lon, or neither"
            val zone = need("zone")
            val school = when (val s = need("school")) {
                "standard" -> AsrSchool.STANDARD
                "hanafi" -> AsrSchool.HANAFI
                "-" -> null
                else -> null.also { problems += "$where: school '$s' (standard, hanafi or -)" }
            }
            val columns = need("columns").split(' ').filter { it.isNotEmpty() }.map { token ->
                if (token == "-") {
                    emptyList()
                } else {
                    token.split('+').mapNotNull { part ->
                        when (part) {
                            "A" -> when (school) {
                                AsrSchool.STANDARD -> Event.ASR_STANDARD
                                AsrSchool.HANAFI -> Event.ASR_HANAFI
                                null -> null.also { problems += "$where: column A needs the row's school" }
                            }
                            else -> TOKENS[part] ?: null.also {
                                problems += "$where: unknown column '$part' (${TOKENS.keys.joinToString(" ")} A -; I is Isha)"
                            }
                        }
                    }
                }
            }
            val flat = columns.flatten()
            if (flat.size != flat.toSet().size) problems += "$where: an event is mapped to two columns"
            if (flat.isEmpty()) problems += "$where: no column is checked"
            val format = need("format")
            val split = when (val s = need("split")) {
                "fit" -> Split.FIT
                "test" -> Split.TEST
                else -> Split.TEST.also { problems += "$where: split '$s' (fit or test)" }
            }
            if (problems.size > before) return null
            return GateRow(
                source = source, line = line, path = path, entry = entry, unit = unit, lat = lat, lon = lon, zone = zone,
                columns = columns, format = format, school = school, split = split, note = cells.getValue("note"),
                member = cells.getValue("member").takeIf { it.isNotEmpty() },
                clock = cells.getValue("clock").takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** Every problem in the gate's inputs, listed together. */
class GateError(val problems: List<String>) : RuntimeException(problems.joinToString("\n"))
