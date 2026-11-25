package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.ReachLimits
import world.taqwa.app.prayer.engine.registry.ownLateLimitFor
import world.taqwa.timetables.Json
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import java.io.File
import kotlin.system.exitProcess

/**
 * Ruling R270 (the second trust audit of 14 Nov 2026, finding G26): writes `ReachLimits.kt`, each unit's limit where
 * its own tables read across its reach (the entry's `-reach.tsv` rows, [ReachRows.SPECS]) were measured later than its
 * own recorded limit ([ownLateLimitFor], else its class's) allowed: the stamp's measured worst for that unit and event,
 * as R254 (Bahla, by hand) and R255 (Türkiye, the archive's limits.py) did. Only for [ReachLimitsFile.ENTRIES]; a limit
 * is never lowered below the unit's own, and a figure the reach no longer needs drops out on the next run.
 *
 *     ./gradlew -p tools/timetables gate -Pentry=<the entries> -Pofficial=<archive>   (writes the stamps, red where over)
 *     ./gradlew -p tools/timetables generateReachLimits
 *     ./gradlew -p tools/timetables gate ...                                         (green)
 *
 * The stamp must be one the whole entry was checked for (its reach rows read: `-Pentry`, never `-PgateGroup` of the
 * group file alone). A stamp written without them gives smaller figures, and the next gate run is red on the reach
 * rows, so a partial run cannot pass unnoticed. With `--check` it writes nothing and exits 1 when the committed file is
 * not what it would write; `checkStamps` makes the same check on every run, so a stale file stops a release. A write
 * prints every figure it moves against the file the build was compiled with: a raise is About's new figure, to report.
 */
fun main(args: Array<String>) {
    val repo = File(args.getOrNull(args.indexOf("--repo") + 1)?.takeIf { "--repo" in args } ?: fail("--repo is required"))
    val check = "--check" in args
    val text = ReachLimitsFile.render(repo.resolve("tools/timetables/official/stamps"))
    val file = repo.resolve(ReachLimitsFile.PATH)
    if (check) {
        if (!file.isFile || file.readText() != text) fail("${ReachLimitsFile.PATH} is not what the stamps give: run generateReachLimits, then the gate")
        println("generateReachLimits: ${ReachLimitsFile.PATH} is what the stamps give")
        return
    }
    // Every move against the file this build was compiled with, one line each: a raise is a figure About will say, and
    // the merger reports it rather than absorbing it (the review of 15 Nov 2026).
    val moves = ReachLimitsFile.moves(ReachLimits.MINUTES, ReachLimitsFile.raises(repo.resolve("tools/timetables/official/stamps")))
    moves.forEach { println("generateReachLimits: $it") }
    file.writeText(text)
    println("Wrote ${ReachLimitsFile.PATH} (${moves.size} unit-event${if (moves.size == 1) "" else "s"} moved)")
}

private fun fail(problem: String): Nothing {
    System.err.println("generateReachLimits: $problem")
    exitProcess(1)
}

object ReachLimitsFile {
    const val PATH = "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ReachLimits.kt"

    /**
     * The entries whose units' reach limits are written here (R270): every entry with reach rows ([ReachRows.SPECS]) but
     * Türkiye's and Oman's, whose limits their own files hold (R255's limits.py, R254's Gulf.kt); and since ruling R297
     * every figure claimed across an area ([ReachAreas.AREAS]), an entry with no units keyed by its own id (R298).
     */
    val ENTRIES: List<String> get() =
        (ReachRows.SPECS.map { it.entry } + ReachAreas.AREAS.map { it.entry }).filter { it !in OWN_FILES }.distinct().sorted()

    private val OWN_FILES = setOf("tr.diyanet", "om.mara")

    /** Each entry's units' raises: unit → event → minutes, from [stamps]. */
    @Suppress("UNCHECKED_CAST")
    fun raises(stamps: File): Map<String, Map<String, Map<TimedEvent, Int>>> = ENTRIES.associateWith { id ->
        val entry = requireNotNull(Registry.byId(id)) { "no entry $id" }
        val set = Units.of(id)
        val stamp = Json.parse(stamps.resolve("$id.json").readText()) as Map<String, Any?>
        fun worst(stats: Map<String, Any?>): Map<TimedEvent, Int> {
            val worst = HashMap<TimedEvent, Int>()
            for ((key, value) in (stats["events"] as? Map<String, Any?>).orEmpty()) {
                val event = Event.byKey(key) ?: continue
                val minutes = (((value as Map<String, Any?>)["worstLate"] as? Long) ?: 0L).toInt()
                worst.merge(event.timed, minutes, ::maxOf)
            }
            return worst
        }
        if (set == null) {
            // Ruling R298: an entry with no units (MUIS's), read across the area it claims (R297): its stamp's own events,
            // keyed by its id, against its own recorded limits, else its class's.
            require(id in ReachAreas.AREAS.map { it.entry } && entry.measured) { "$id has no units and claims no area" }
            val raised = worst(stamp).filter { (event, minutes) ->
                minutes > (ownLateLimitFor(event, null, entry)?.minutes ?: Gate.classLimit(entry.entryClass))
            }.toSortedMap()
            return@associateWith if (raised.isEmpty()) emptyMap<String, Map<TimedEvent, Int>>() else mapOf<String, Map<TimedEvent, Int>>(id to raised)
        }
        val units = stamp["units"] as? Map<String, Any?> ?: emptyMap()
        units.entries.mapNotNull { (unitId, stats) ->
            val unit = set.units.firstOrNull { it.id == unitId } ?: return@mapNotNull null
            if (!unit.measured) return@mapNotNull null
            val raised = worst(stats as Map<String, Any?>).filter { (event, minutes) ->
                minutes > (ownLateLimitFor(event, unit, entry)?.minutes ?: Gate.classLimit(unit.entryClass ?: entry.entryClass))
            }.toSortedMap()
            (unitId to raised).takeIf { raised.isNotEmpty() }
        }.toMap<String, Map<TimedEvent, Int>>().toSortedMap()
    }.filterValues { it.isNotEmpty() }

    /** Each unit-event whose figure differs between [before] and [after]: "entry/unit event a -> b" (0 where absent). */
    fun moves(
        before: Map<String, Map<String, Map<TimedEvent, Int>>>,
        after: Map<String, Map<String, Map<TimedEvent, Int>>>,
    ): List<String> = (before.keys + after.keys).sorted().flatMap { entry ->
        val b = before[entry].orEmpty()
        val a = after[entry].orEmpty()
        (b.keys + a.keys).sorted().flatMap { unit ->
            val be = b[unit].orEmpty()
            val ae = a[unit].orEmpty()
            (be.keys + ae.keys).sorted().mapNotNull { event ->
                val from = be[event] ?: 0
                val to = ae[event] ?: 0
                if (from == to) null else "$entry/$unit ${event.name.lowercase()} $from -> $to" + if (to > from) " (raised)" else " (lowered)"
            }
        }
    }

    fun render(stamps: File): String = buildString {
        val all = raises(stamps)
        append("package world.taqwa.app.prayer.engine.registry.data\n\n")
        append("import world.taqwa.app.prayer.engine.registry.TimedEvent\n")
        val used = all.values.flatMap { it.values }.flatMap { it.keys }.toSortedSet()
        for (e in used) append("import world.taqwa.app.prayer.engine.registry.TimedEvent.").append(e.name).append('\n')
        append("\n/**\n")
        append(" * Ruling R270: GENERATED by `./gradlew -p tools/timetables generateReachLimits` (units/GenerateReachLimits.kt) from the\n")
        append(" * gate's stamps; do not edit by hand. Each unit's limits where its own tables, read across its reach (the\n")
        append(" * `-reach.tsv` rows), were measured later than its own recorded limit allowed: entry, unit, event and the measured\n")
        append(" * worst in minutes ([world.taqwa.app.prayer.engine.registry.reachLateLimit]). ")
        append("${all.values.sumOf { u -> u.values.sumOf { it.size } }} unit-events in ${all.values.sumOf { it.size }} units.\n")
        append(" */\n")
        append("object ReachLimits {\n")
        append("    val MINUTES: Map<String, Map<String, Map<TimedEvent, Int>>> by lazy {\n")
        append("        mapOf(\n")
        for (id in all.keys) append("            \"$id\" to ${fn(id)}(),\n")
        append("        )\n    }\n")
        for ((id, units) in all) {
            append("\n    private fun ${fn(id)}(): Map<String, Map<TimedEvent, Int>> = mapOf(\n")
            for ((unit, events) in units) {
                append("        \"$unit\" to mapOf(")
                append(events.entries.joinToString(", ") { "${it.key.name} to ${it.value}" })
                append("),\n")
            }
            append("    )\n")
        }
        append("}\n")
    }

    private fun fn(id: String) = id.split('.').joinToString("") { it.replaceFirstChar(Char::uppercase) }.replaceFirstChar(Char::lowercase)
}
