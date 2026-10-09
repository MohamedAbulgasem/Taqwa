package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.authorities.CentralAsia
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaceCodec
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.timetables.Json
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

/**
 * Writes `shared/.../registry/data/QmdbPlaceList.kt`: every distinct point of QMDB's own city list as a kz.qmdb unit,
 * each with its reach measured by [QmdbReach] (the owner's decision of 9 Oct 2026, every QMDB place a unit).
 *
 *     ./gradlew -p tools/timetables generateQmdbPlaces [-Pcities=<cities.json.gz>] [-Pout=<file>] [-Pthreads=N]
 *
 * `cities` defaults to the private archive's capture of QMDB's list (`archive/raw/manual/kz-qmdb/2026-10-06/
 * cities.json.gz`, api.muftyat.kz/cities/ read page by page); coordinates are derived facts, never a time. A point
 * two places share is one unit, its lowest id. Re-run after any change to kz.qmdb's method, bands or curves (a reach
 * is measured with them, and the file records what with: [QmdbReach.fingerprint], which `CityUnitsTest` compares),
 * then run the gate and commit; `CityUnitsTest` checks a large sample's edges.
 */
fun main(args: Array<String>) {
    val options = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        options[args[i]] = args.getOrNull(i + 1) ?: fail("${args[i]} needs a value")
        i += 2
    }
    val repo = File(options["--repo"] ?: fail("--repo is required"))
    val cities = File(options["--cities"] ?: fail("--cities is required"))
    val out = File(
        options["--out"] ?: repo.resolve("shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/QmdbPlaceList.kt").path,
    )
    val threads = options["--threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors()
    if (!cities.isFile) fail("no city list at $cities")

    val list = QmdbList.read(cities)
    println("QMDB's list: ${list.size} places, ${list.distinctBy { it.lat7 to it.lon7 }.size} distinct points (${list.firstOrNull()?.source})")
    val started = System.nanoTime()
    val reaches = QmdbList.reaches(list, threads) { done, total ->
        if (done % 250 == 0 || done == total) {
            val s = (System.nanoTime() - started) / 1e9
            println("  $done / $total reaches measured (${"%.0f".format(s)} s)")
        }
    }
    out.writeText(QmdbList.render(reaches, list.firstOrNull()?.source ?: "", QmdbReach.fingerprint()))
    println("Wrote ${reaches.size} places to ${out.relativeTo(repo).invariantSeparatorsPath}")
}

private fun fail(problem: String): Nothing {
    System.err.println("generateQmdbPlaces: $problem")
    exitProcess(2)
}

/** QMDB's city list as the generator reads it, and the generated file's text. */
object QmdbList {
    /** One place of the list: its id, and its coordinates in 1e-7 degrees exactly as the list writes them. */
    class Entry(val id: Int, val lat7: Long, val lon7: Long, val source: String) {
        val point: GeoPoint get() = GeoPoint(lat7 / QmdbPlaceCodec.SCALE.toDouble(), lon7 / QmdbPlaceCodec.SCALE.toDouble())
    }

    /** A distinct point and its measured reach. */
    class Reached(val id: Int, val lat7: Long, val lon7: Long, val reachKm: Double)

    private fun seventh(text: String): Long = BigDecimal(text).movePointRight(7).longValueExact()

    @Suppress("UNCHECKED_CAST")
    fun read(file: File): List<Entry> {
        val root = Json.parse(GZIPInputStream(file.inputStream()).readBytes().decodeToString()) as Map<String, Any?>
        val source = (root["source"] as? String).orEmpty()
        val pages = root["pages"] as List<Map<String, Any?>>
        return pages.flatMap { it["results"] as List<Map<String, Any?>> }.map { p ->
            Entry((p["id"] as Long).toInt(), seventh(p["lat"] as String), seventh(p["lng"] as String), source)
        }
    }

    /** Each distinct point of [list] (its lowest id) with its reach, in id order, measured on [threads] threads. */
    fun reaches(list: List<Entry>, threads: Int, progress: (Int, Int) -> Unit = { _, _ -> }): List<Reached> {
        val points = list.groupBy { it.lat7 to it.lon7 }.values.map { at -> at.minBy { it.id } }.sortedBy { it.id }
        val keyed = (QmdbPlaces.all + QmdbPlaces.unanswered).associateBy { it.qmdbId }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val jobs: List<Future<Reached>> = points.map { e ->
                pool.submit<Reached> {
                    val point = e.point
                    val unit = AuthorityUnit(
                        keyed[e.id]?.key ?: "qmdb-${e.id}", "QMDB ${e.id}", point, lateReachKm(point.lat, EntryClass.D_AUTHORITY),
                        method = CentralAsia.kazakhstanAt(point), named = false,
                    )
                    Reached(e.id, e.lat7, e.lon7, QmdbReach.reachKm(unit))
                }
            }
            return jobs.mapIndexed { k, job -> job.get().also { progress(k + 1, jobs.size) } }
        } finally {
            pool.shutdown()
        }
    }

    /** The places per string part, under a JVM string constant's 64 KB. */
    private const val PER_PART = 3000

    fun render(places: List<Reached>, source: String, fingerprint: String): String = buildString {
        val lat0 = (QmdbPlaceCodec.LAT_BASE * QmdbPlaceCodec.SCALE).toLong()
        val lon0 = (QmdbPlaceCodec.LON_BASE * QmdbPlaceCodec.SCALE).toLong()
        val packed = places.map { p ->
            QmdbPlaceCodec.digits(p.id.toLong(), 3) + QmdbPlaceCodec.digits(p.lat7 - lat0, 5) +
                QmdbPlaceCodec.digits(p.lon7 - lon0, 5) + QmdbPlaceCodec.digits(Math.round(p.reachKm * 10), 2)
        }
        val parts = packed.chunked(PER_PART).map { it.joinToString("") }
        append("// GENERATED FILE — do not edit by hand.\n")
        append("//\n")
        append("// Written by `./gradlew -p tools/timetables generateQmdbPlaces` (GenerateQmdbPlaces.kt) from QMDB's own city\n")
        append("// list, ${source.substringBefore(" (").ifBlank { "api.muftyat.kz/cities/" }}, and the engine's own rule for each place's\n")
        append("// reach (QmdbReach). Coordinates and kilometres only, never a time. Re-run after any change to kz.qmdb's method,\n")
        append("// bands or curves, then run the gate and commit.\n")
        append("package world.taqwa.app.prayer.engine.registry.data\n\n")
        append("/**\n")
        append(" * Every distinct point of QMDB's city list (${places.size} points), each a kz.qmdb unit: [Place.qmdbId] the list's\n")
        append(" * lowest id there, its coordinates as the list writes them, and the unit's reach in km: as far as every time\n")
        append(" * shown stays within ${QmdbReach.LIMIT_MINUTES} min of the unit's own day and of the user's own point alone\n")
        append(" * (QmdbReach). Packed by [QmdbPlaceCodec].\n")
        append(" */\n")
        append("object QmdbPlaceList {\n")
        append("    class Place(val qmdbId: Int, val lat: Double, val lon: Double, val reachKm: Double)\n\n")
        append("    const val COUNT = ${places.size}\n\n")
        append("    /**\n")
        append("     * What the reaches were measured with (QmdbReach.fingerprint: kz.qmdb's method, bands and curves, the rule\n")
        append("     * and the codec). `CityUnitsTest` fails when it no longer matches: run the generator again.\n")
        append("     */\n")
        append("    const val FINGERPRINT = \"$fingerprint\"\n\n")
        append("    val places: List<Place> by lazy { QmdbPlaceCodec.decode(listOf(${parts.indices.joinToString(", ") { "part${it + 1}" }}).joinToString(\"\")) }\n")
        // Seven places a line, so that a reach that moves shows as one changed line.
        for ((k, part) in parts.withIndex()) {
            append("\n    private val part${k + 1} =\n")
            val lines = part.chunked(QmdbPlaceCodec.WIDTH * 7)
            for ((n, line) in lines.withIndex()) {
                append("        \"").append(line).append('"').append(if (n < lines.lastIndex) " +\n" else "\n")
            }
        }
        append("}\n")
    }
}
