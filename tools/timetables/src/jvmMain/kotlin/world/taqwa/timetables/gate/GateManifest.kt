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

    /**
     * A row of a `<group>-envelope.tsv` file (the city-point round 2 review, 11 Oct 2026): a table printed for its own point
     * replayed at an app city its bound reach holds, whichever table the city follows, or none, so that the gate guards the
     * envelope ("never before any table whose reach holds the place, nor any end after it"). Only the promise is checked:
     * an early start, a late end or a day out of order breaks the gate as anywhere, but the lateness against a table the
     * place does not follow is not the place's figure: it is recorded in no unit, held to no late limit and kept out of
     * the members' checked days and Maghrib gaps (those are the place's own tables' to say).
     *
     * A `<group>-promise.tsv` file's rows are held the same way (ruling R247): a table kept by hand that no place is known
     * to follow, a calculator's own table for a point, say, replayed at that point so that no start comes before it. It
     * is no published timetable of the entry's: in no stamp, in no count of published tables, and never written or
     * removed by `generateEnvelopeRows`.
     */
    val promiseOnly: Boolean get() = source.endsWith(PROMISE_ONLY_SUFFIX) || source.endsWith(HAND_PROMISE_SUFFIX)

    /**
     * A row of a `<group>-reach.tsv` file: a unit's own table replayed at a point of its reach
     * (`generateReachRows`). Such a row is never the row a table's days are counted at while a row of
     * another file reads the same table ([Counted], ruling R214).
     */
    val reach: Boolean get() = source.endsWith(REACH_SUFFIX)

    /** A row that reads ends alone (a printed imsak, an end of eating): it checks them and adds no place-day. */
    val endsOnly: Boolean get() = columns.flatten().let { events -> events.isNotEmpty() && events.none { it.isStart } }

    companion object {
        /** The file-name ending of the generated promise-only gate files, the envelope's ([promiseOnly]). */
        const val PROMISE_ONLY_SUFFIX = "-envelope.tsv"

        /** The file-name ending of the promise-only gate files kept by hand ([promiseOnly], ruling R247). */
        const val HAND_PROMISE_SUFFIX = "-promise.tsv"

        /** The file-name ending of the reach rows' gate files ([reach]). */
        const val REACH_SUFFIX = "-reach.tsv"
    }
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
 * One `@demoted` line of a gate file (ruling R185): the monitor's record that [entry], a checked
 * timetable (class A or B), broke a promise in a capture of its authority's own site — at its
 * authority unit [unit], or, with no unit, the entry as a whole ([since] is the day `prove` found
 * it; [reason] says what in words: dates, counts and minutes only, never a printed time, ruling
 * R69). `prove` writes the line by itself on the first failing day, in the gate file the failing
 * row would have joined; the gate carries it into the entry's stamp, and from there the website
 * holds the city pages of the unit (of the whole entry, without one) back (`Proven`), a Ramadan no
 * longer counts the entry as covered, and no release is cut while the registry still calls the
 * entry a checked timetable (`CheckStamps`). A person removes the line only with the failing days
 * proven (the method refitted, their rows in the gate and green; or the authority's own misprint
 * recorded and the corrected table's rows green), or leaves it standing with the registry's class
 * lowered.
 *
 *     @demoted  <entry>[/<unit>]  <yyyy-mm-dd>  <reason>
 */
data class Demotion(val source: String, val line: Int, val entry: String, val unit: String?, val since: LocalDate, val reason: String) {
    val where: String get() = "$source:$line"

    /** "entry" or "entry/unit", as the line spells it. */
    val target: String get() = if (unit == null) entry else "$entry/$unit"

    /** This line already says what [other] would: the same unit, or the entry as a whole. */
    fun covers(entry: String, unit: String?): Boolean = this.entry == entry && (this.unit == null || this.unit == unit)
}

/**
 * One `@fitted` line of a gate file (ruling R214, the sweep of finding F20): a margin of [entry] (at its unit [unit], or
 * wherever it is read) was set on [events] of the table at [path] — every table whose path fits it, where it holds a
 * `*` — though the rows reading it are marked `test`: the first fit left a held-out day early, and the margin was
 * widened to that row's excess, or the margin was read off every table held. Such a cell passes by construction, so it
 * is a fit cell in every figure shown ([Counted]); the row stays `test` for the events no margin was set on, which is
 * why this is a line of its own and not the row's split. [reason] says which margin and when, in words. A line that no
 * test row answers is a mistake, and refused.
 *
 * **A derived table is held out only where what it was made from is** (the review round of 14 Nov 2026). The archive
 * holds tables no authority printed as they stand: a perpetual table assigned to another year's dates, a town's table
 * made of the capital's plus the town's printed minutes. Each prints numbers its source printed, so where a margin was
 * fitted on the source, a `@fitted` line names the derived table too: its days check the engine against another year's
 * sun or another town's, and show in the days checked, never among the days the margins did not see.
 *
 *     @fitted  <entry>[/<unit>]  <path | pattern with one *>  <events: F S D A As Ah M I E Im, or *>  <reason>
 */
data class FittedOn(
    val source: String,
    val line: Int,
    val entry: String,
    val unit: String?,
    val path: String,
    val events: Set<Event>,
    val reason: String,
) {
    val where: String get() = "$source:$line"

    /**
     * Whether the line names [row]'s table: the entry's (the unit's, when one is named), by path, or by a pattern with one
     * `*` standing for any run of characters (a prefix where it ends the pattern; a folder's `*-2027.txt` is every town's
     * table of that year).
     */
    fun names(row: GateRow): Boolean {
        if (row.entry != entry || (unit != null && row.unit != unit)) return false
        val star = path.indexOf('*')
        if (star < 0) return row.path == path
        val head = path.substring(0, star)
        val tail = path.substring(star + 1)
        return row.path.length >= head.length + tail.length && row.path.startsWith(head) && row.path.endsWith(tail)
    }
}

/**
 * Every gate row, from the `official/gate/<group>.tsv` files; [sources] are the files read,
 * [excuses] their `@excuse` lines (ruling R117), [demotions] their `@demoted` lines (ruling R185) and [fitted] their
 * `@fitted` lines (ruling R214). An exception to a late limit is not the gate
 * file's to make: it lives with its registry entry or unit (ruling R37, `LateLimit`), so that the
 * gate proves and About states the same number.
 */
data class GateManifest(
    val rows: List<GateRow>,
    val sources: List<String> = rows.map { it.source }.distinct(),
    val excuses: List<Excuse> = emptyList(),
    val demotions: List<Demotion> = emptyList(),
    val fitted: List<FittedOn> = emptyList(),
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
            demotions = demotions.filter { d ->
                (groups.isEmpty() || d.source.removeSuffix(".tsv") in groups) && (entries.isEmpty() || d.entry in entries)
            },
            fitted = fitted.filter { f ->
                (groups.isEmpty() || f.source.removeSuffix(".tsv") in groups) && (entries.isEmpty() || f.entry in entries)
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
            return GateManifest(
                parsed.flatMap { it.rows }, files.map { it.name }, parsed.flatMap { it.excuses }, parsed.flatMap { it.demotions },
                parsed.flatMap { it.fitted },
            )
        }

        /**
         * One gate file. Blank lines and `#` comments are skipped, and `@excuse`, `@demoted` and `@fitted` lines
         * read as [Excuse]s, [Demotion]s and [FittedOn]s wherever they stand; the first other line is the header. Every problem is
         * reported at once, with its line.
         */
        fun parse(source: String, text: String): GateManifest {
            val problems = mutableListOf<String>()
            val rows = mutableListOf<GateRow>()
            val excuses = mutableListOf<Excuse>()
            val demotions = mutableListOf<Demotion>()
            val fitted = mutableListOf<FittedOn>()
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
                    cells[0] == DEMOTED -> demotion(cells, source, line, problems)?.let(demotions::add)
                    cells[0] == FITTED -> fittedOn(cells, source, line, problems)?.let(fitted::add)
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
            // A demotion names an entry this file has rows for: a line for an entry spelt wrongly would demote nothing.
            for (d in demotions) {
                if (rows.none { it.entry == d.entry }) problems += "${d.where}: $DEMOTED names ${d.entry}, which has no row in $source"
            }
            // A fitted line names a held-out cell of this file: one that names nothing would lower no figure, silently.
            for (f in fitted) {
                if (rows.none { row -> row.split == Split.TEST && f.names(row) && row.columns.flatten().any { it in f.events } }) {
                    problems += "${f.where}: $FITTED names no test row of $source reading those events at ${f.path}"
                }
            }
            if (problems.isNotEmpty()) throw GateError(problems)
            return GateManifest(rows, listOf(source), excuses, demotions, fitted)
        }

        /** The directive of a [FittedOn] line. */
        const val FITTED = "@fitted"

        private fun fittedOn(cells: List<String>, source: String, line: Int, problems: MutableList<String>): FittedOn? {
            val where = "$source:$line"
            if (cells.size != 5 || cells.any { it.isEmpty() }) {
                return null.also { problems += "$where: $FITTED <entry>[/<unit>] <path or pattern with one *> <events or *> <reason>" }
            }
            val entry = cells[1].substringBefore('/')
            val unit = cells[1].substringAfter('/', "").ifEmpty { null }
            if (entry.isEmpty() || cells[1].endsWith("/")) return null.also { problems += "$where: $FITTED names '${cells[1]}' (<entry> or <entry>/<unit>)" }
            if (cells[2].count { it == '*' } > 1) return null.also { problems += "$where: $FITTED pattern '${cells[2]}' holds more than one *" }
            val events = if (cells[3] == "*") {
                Event.entries.toSet()
            } else {
                cells[3].split(' ').filter { it.isNotEmpty() }.flatMap { token ->
                    when (token) {
                        "A" -> listOf(Event.ASR_STANDARD, Event.ASR_HANAFI)
                        else -> listOf(TOKENS[token] ?: return null.also { problems += "$where: event '$token' (${TOKENS.keys.joinToString(" ")}, A or *)" })
                    }
                }.toSet()
            }
            return FittedOn(source, line, entry, unit, cells[2], events, cells[4])
        }

        /** The directive of a [Demotion] line. */
        const val DEMOTED = "@demoted"

        /** A [Demotion] as its gate file's line. */
        fun demotedLine(entry: String, unit: String?, since: LocalDate, reason: String): String =
            listOf(DEMOTED, if (unit == null) entry else "$entry/$unit", since.toString(), reason.replace('\t', ' ').replace('\n', ' ').trim())
                .joinToString("\t")

        private fun demotion(cells: List<String>, source: String, line: Int, problems: MutableList<String>): Demotion? {
            val where = "$source:$line"
            if (cells.size != 4 || cells.any { it.isEmpty() }) {
                return null.also { problems += "$where: $DEMOTED <entry>[/<unit>] <yyyy-mm-dd> <reason>" }
            }
            val since = runCatching { LocalDate.parse(cells[2]) }.getOrNull()
                ?: return null.also { problems += "$where: date '${cells[2]}' (yyyy-mm-dd)" }
            val entry = cells[1].substringBefore('/')
            val unit = cells[1].substringAfter('/', "").ifEmpty { null }
            if (entry.isEmpty() || cells[1].endsWith("/")) return null.also { problems += "$where: $DEMOTED names '${cells[1]}' (<entry> or <entry>/<unit>)" }
            return Demotion(source, line, entry, unit, since, cells[3])
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
