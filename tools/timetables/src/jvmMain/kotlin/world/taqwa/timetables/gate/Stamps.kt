package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.timetables.Json
import java.io.File
import java.security.MessageDigest

/**
 * The gate's stamps (spec §5): per entry, what was checked and how it came out, as statistics only.
 * A stamp never holds an official time: places, first and last date, place-days, Ramadan days,
 * early and late-end counts per event, the late histogram, the exact share, the worst late minutes,
 * the cells declared not followed and their dates (ruling R82), and the engine hash they were
 * measured on. Committed as `official/stamps/<entry>.json`; the same
 * inputs always write the same bytes.
 *
 * The engine hash covers the engine's core (everything under `prayer/engine/` but the authority
 * files and their data, plus the Umm al-Qura dates, the `Prayer` keys and the Hijri calendar every
 * Ramadan falls back to) and the entry's own methods as resolved at each checked point, so one
 * authority's change leaves every other entry's stamp as it was.
 */
object Stamps {

    /** Writes a stamp for each entry of [result] with checked days into [dir]; returns the files. */
    fun write(result: GateResult, dir: File, repoRoot: File): List<File> {
        dir.mkdirs()
        val core = coreHash(repoRoot)
        return result.entries.values.filter { it.placeDayCount > 0 }.map { s ->
            val file = dir.resolve("${s.entry.id}.json")
            file.writeText(Json.pretty(stamp(s, core)))
            file
        }
    }

    /** One entry's stamp, as JSON values. */
    fun stamp(s: EntryStats, coreHash: String): Map<String, Any?> = linkedMapOf<String, Any?>(
        "entry" to s.entry.id,
        "class" to s.classes.joinToString("/") { it.label() },
        "engineHash" to engineHash(coreHash, s.fingerprints),
        // Early starts, late ends, cells over the late limit and days out of order: 0 when green.
        "broken" to s.broken,
        "places" to s.places.size,
        "first" to s.first?.toString(),
        "last" to s.last?.toString(),
        "placeDays" to s.placeDayCount,
        "heldOutDays" to s.testDays,
        // Place-days in Ramadan by Umm al-Qura's own dates.
        "ramadanDays" to s.ramadanDays,
        "outOfOrderDays" to s.outOfOrder,
        // Lines the reader could not take (skipped), and lines repeating a date (each checked).
        "unreadableLines" to s.unreadable,
        "repeatedLines" to s.duplicates,
        "events" to events(s.events),
        // The same over the held-out rows alone: the proof proper (spec §5).
        "heldOut" to linkedMapOf(
            "placeDays" to s.testDays,
            "ramadanDays" to s.testRamadanDays,
            "events" to events(s.heldOut),
        ),
        // A cautious entry's Maghrib cap against each member's printed Maghrib (spec §3.6), in minutes.
        "maghribCap" to if (s.maghribGaps.isEmpty() && s.maghribUnchecked == 0) null else linkedMapOf(
            "uncheckedDays" to s.maghribUnchecked,
            "members" to s.maghribGaps.entries.associate { (member, g) ->
                member to linkedMapOf("days" to g.days, "cappedDays" to g.capped, "leastGap" to g.least, "mostGap" to g.most)
            },
        ),
        // The limits the events were held to (rulings R37, R41): each event's exception from its unit,
        // else its entry, else its class's.
        "lateLimits" to limits(s.events),
        // Per authority unit: its own place-days, limits and tally.
        "units" to if (s.units.isEmpty()) null else s.units.entries.associate { (id, u) ->
            id to linkedMapOf<String, Any?>(
                "name" to u.name,
                "placeDays" to u.placeDays.size,
                "broken" to u.broken,
                "lateLimits" to limits(u.events),
                "events" to events(u.events),
            )
        },
    ).filterValues { it != null }

    private fun events(events: Map<Event, EventStats>): Map<String, Any?> = events.entries.associate { (event, e) ->
        event.key to linkedMapOf(
            "checked" to e.checked,
            if (event.isStart) ("early" to e.early) else ("lateEnd" to e.lateEnd),
            // Minutes on the safe side: late for a start, early for an end.
            "late" to linkedMapOf("0" to e.late[0], "1" to e.late[1], "2" to e.late[2], "3+" to e.late[3]),
            "exactShare" to Math.round(e.exact * 10_000) / 10_000.0,
            "worstLate" to e.worst,
            "lateLimit" to e.limits.maxOrNull(),
            "lateLimitFrom" to e.applied.values.joinToString(", ") { it.source },
            "overLimit" to e.over,
            // Cells the day declared not followed (ruling R82), written only where there are some.
            "declared" to e.declared.takeIf { it > 0 },
            "declaredDates" to e.declaredDates.takeIf { it.isNotEmpty() }?.let(::dateRanges),
        ).filterValues { it != null }
    }

    /**
     * Each limit applied, "<source>: <minutes> min", with its reason and the events it covered.
     * Two limits merge into one entry — their events unioned — only when their source, minutes
     * and reason are all equal, i.e. they are the same [Limit]. Two *different* limits (different
     * reasons) can still share a source and minutes: DUM RF's Moscow Fajr and its end-of-eating
     * limit are both 4 min from "unit moscow", but for different reasons. Each then keeps its own
     * entry, disambiguated by its earliest event so the label stays readable and deterministic,
     * e.g. "unit moscow: 4 min (fajr)" and "unit moscow: 4 min (endOfEating)".
     */
    private fun limits(events: Map<Event, EventStats>): Map<String, Any?> {
        val found = LinkedHashMap<Limit, MutableList<String>>()
        for ((event, e) in events) for (limit in e.applied.values) found.getOrPut(limit) { mutableListOf() } += event.key
        val entries = found.entries.sortedWith(compareBy({ it.key.source }, { it.key.minutes }, { it.value.sorted().firstOrNull() ?: "" }))
        fun label(limit: Limit) = "${limit.source}: ${limit.minutes} min"
        val collisions = entries.groupingBy { label(it.key) }.eachCount()
        return entries.associate { (limit, keys) ->
            val base = label(limit)
            val key = if (collisions.getValue(base) > 1) "$base (${keys.sorted().first()})" else base
            key to linkedMapOf<String, Any?>("reason" to limit.reason, "events" to keys).filterValues { it != null }
        }
    }

    /** The core's sources, hashed with their paths (see the class comment). */
    fun coreHash(repoRoot: File): String {
        val engine = repoRoot.resolve("shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine")
        val files = engine.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
            val path = file.relativeTo(engine).invariantSeparatorsPath
            !path.startsWith("registry/authorities/") && (!path.startsWith("registry/data/") || path == "registry/data/UmmAlQuraDates.kt")
        }.toList() + listOf(
            repoRoot.resolve("widgetcore/src/commonMain/kotlin/world/taqwa/app/domain/Prayer.kt"),
            repoRoot.resolve("shared/src/commonMain/kotlin/world/taqwa/app/hijri/TabularHijriCalendar.kt"),
        )
        val digest = MessageDigest.getInstance("SHA-256")
        for (file in files.sortedBy { it.relativeTo(repoRoot).invariantSeparatorsPath }) {
            require(file.isFile) { "engine source missing: $file" }
            digest.update(file.relativeTo(repoRoot).invariantSeparatorsPath.toByteArray())
            digest.update(0)
            digest.update(file.readBytes())
            digest.update(0)
        }
        return hex(digest.digest())
    }

    /** The stamp's hash: the core's and every method the entry was checked with. */
    fun engineHash(coreHash: String, fingerprints: Collection<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(coreHash.toByteArray())
        fingerprints.sorted().forEach {
            digest.update(0)
            digest.update(it.toByteArray())
        }
        return hex(digest.digest()).take(16)
    }

    /** What decided a row's days: the class, the points and each method, stable across runs. */
    fun fingerprint(resolution: Resolution): String = buildString {
        append(resolution.entry.id).append(' ').append(resolution.entryClass).append(' ').append(resolution.entry.school)
        append(" at ").append(resolution.point).append(" unit ").append(resolution.unitName).append(' ').append(resolution.unitPoint)
        resolution.method?.let { append(' ').append(fingerprint(it)) }
        resolution.members.forEach { append(" member ").append(it.id).append('#').append(it.shareRank).append(' ').append(fingerprint(it.method)) }
    }

    /** [method] in words; its arrays by content (a data class prints an array's identity). */
    fun fingerprint(method: TimetableMethod): String = buildString {
        append(method.copy(fajrAngleByDayOfYear = null, ishaAngleByDayOfYear = null, monthlyOffsets = null))
        method.fajrAngleByDayOfYear?.let { append(" fajrCurve=").append(it.contentToString()) }
        method.ishaAngleByDayOfYear?.let { append(" ishaCurve=").append(it.contentToString()) }
        method.monthlyOffsets?.entries?.sortedBy { it.key }?.forEach { (prayer, values) ->
            append(" monthly.").append(prayer).append('=').append(values.contentToString())
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
