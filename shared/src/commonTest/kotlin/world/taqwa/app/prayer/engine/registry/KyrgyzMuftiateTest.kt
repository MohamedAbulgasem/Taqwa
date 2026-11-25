package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.CentralAsia
import world.taqwa.app.prayer.engine.registry.data.KyrgyzLocalities
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The Kyrgyz Muftiate's localities as kg.default's units (12 Nov 2026, kg-default.tsv): what the gate proved against the
 * Muftiate's tables is restricted data, so this holds only how the registry places a user, and the sun's own bound on
 * Dhuhr, never a printed minute.
 */
class KyrgyzMuftiateTest {

    private val zone = "Asia/Bishkek"

    private fun resolve(lat: Double, lon: Double) = Registry.resolve(Place(lat, lon, zone, "KG"))

    @Test
    fun `every locality is a measured class B unit at its own point`() {
        val units = assertNotNull(Units.of("kg.default"))
        assertEquals(KyrgyzLocalities.all.size + 1, units.units.size, "115 localities and Aravan town")
        assertEquals(115, KyrgyzLocalities.all.size)
        for (l in KyrgyzLocalities.all) {
            val r = resolve(l.lat, l.lon)
            assertEquals("kg.default", r.entry.id, l.key)
            assertEquals(l.key, r.unitId, "${l.name} resolves to its own table")
            assertEquals(EntryClass.B, r.entryClass, l.key)
            assertTrue(r.measured, l.key)
            assertEquals(GeoPoint(l.lat, l.lon), r.method!!.fixedPoint, "${l.name}'s point rides beside the user's")
        }
    }

    @Test
    fun `aravan town is held to the aravan table's own point`() {
        val town = KyrgyzLocalities.aravanTown
        val table = KyrgyzLocalities.all.single { it.key == "aravan" }
        val r = resolve(town.lat, town.lon)
        assertEquals("aravan-town", r.unitId)
        assertEquals(EntryClass.B, r.entryClass)
        assertEquals(GeoPoint(table.lat, table.lon), r.method!!.fixedPoint)
    }

    /** The point a unit's own table is computed for: its own, or Aravan's for Aravan town. */
    private fun tablePoint(unit: AuthorityUnit): GeoPoint = unit.method?.fixedPoint ?: unit.point

    /** The day [unit]'s own table gives at the point it is computed for, nothing riding beside it. */
    private fun tableDay(at: Resolution, unit: AuthorityUnit, date: LocalDate, tz: TimeZone): PrayerDay {
        val point = tablePoint(unit)
        val alone = Registry.methodAlone("kg.default", CentralAsia.kyrgyzstanMethod.copy(fixedPoint = point), point, zone)
        val there = at.copy(point = point, method = alone)
        // The table's own sunrise, before the real sun at the table's point caps it (ruling R265): that cap is the sun at
        // another point, not the table, and a place west of it rises later.
        return DayPipeline.unended(there, date, tz).copy(sunrise = DayPipeline.beforeRealSun(there, date, tz).sunrise)
    }

    /** Fails where [shown] starts a prayer before [table] does, or ends the fast or the dawn prayer after it. */
    private fun assertNeverBefore(shown: PrayerDay, table: PrayerDay, what: String) {
        assertTrue(shown.fajr >= table.fajr, "$what: Fajr ${shown.fajr} before ${table.fajr}")
        assertTrue(shown.dhuhr >= table.dhuhr, "$what: Dhuhr ${shown.dhuhr} before ${table.dhuhr}")
        assertTrue(shown.asr >= table.asr, "$what: Asr ${shown.asr} before ${table.asr}")
        assertTrue(shown.asrOther >= table.asrOther, "$what: the other school's Asr ${shown.asrOther} before ${table.asrOther}")
        assertTrue(shown.maghrib >= table.maghrib, "$what: Maghrib ${shown.maghrib} before ${table.maghrib}")
        assertTrue(shown.isha >= table.isha, "$what: Isha ${shown.isha} before ${table.isha}")
        assertTrue(shown.sunrise <= table.sunrise, "$what: sunrise ${shown.sunrise} after ${table.sunrise}")
        assertTrue(shown.endOfEating <= table.endOfEating, "$what: the end of eating ${shown.endOfEating} after ${table.endOfEating}")
    }

    /**
     * Ruling R205 (the audit of 12 Nov 2026): the Muftiate prints by locality, with no boundaries, and the reaches
     * overlap, so a place inside several may follow any of those tables. Wherever two localities' reaches meet (each
     * pair's midpoint and each one's own point), the place rides every table whose reach holds it, and what it shows
     * is never before any of those tables' days, nor an end after one, every eleventh day of 2027. The tables here
     * are modelled (the Muftiate's method at each table's own point, as the engine computes it): this test holds the
     * rule; the printed tables themselves are held by the gate's rows (kg-default-envelope.tsv, kg-default-reach.tsv).
     */
    @Test
    fun `a place inside several localities' reaches is never before any of their tables`() {
        val units = assertNotNull(Units.of("kg.default")).units
        val tz = TimeZone.of(zone)
        var pairs = 0
        var places = 0
        for (a in units) for (b in units) {
            if (a.id >= b.id || distanceKm(a.point, b.point) >= a.radiusKm + b.radiusKm) continue
            pairs++
            val between = GeoPoint((a.point.lat + b.point.lat) / 2, (a.point.lon + b.point.lon) / 2)
            for (p in listOf(between, a.point, b.point)) {
                val holders = units.filter { distanceKm(p, it.point) <= it.radiusKm }
                if (holders.size < 2) continue
                places++
                val r = resolve(p.lat, p.lon)
                val m = assertNotNull(r.method)
                val ridden = listOfNotNull(m.fixedPoint) + m.envelopePoints
                for (h in holders) {
                    assertTrue(tablePoint(h) in ridden, "${p.lat},${p.lon} follows ${r.unitId} inside ${h.id}'s reach and does not ride its table")
                }
                var date = LocalDate(2027, 1, 1)
                while (date.year == 2027) {
                    val shown = DayPipeline.unended(r, date, tz)
                    for (h in holders) assertNeverBefore(shown, tableDay(r, h, date, tz), "${p.lat},${p.lon} (${r.unitId}) against ${h.id} on $date")
                    date = date.plus(11, DateTimeUnit.DAY)
                }
            }
        }
        assertTrue(pairs > 100, "$pairs pairs of overlapping reaches")
        assertTrue(places > pairs, "$places places inside several reaches")
    }

    /**
     * The audit's own points (findings F1 and F8, 12 Nov 2026) and the app's Ivanovka, which follows Tokmok inside
     * Kant's reach: each is held to every table whose reach holds it, the one it follows and its neighbours'.
     */
    @Test
    fun `the audit's places between two localities ride both tables`() {
        val units = assertNotNull(Units.of("kg.default")).units.associateBy { it.id }
        val cases = listOf(
            Triple(42.4079, 78.2035, listOf("karakol", "kyzyl-suu")),
            Triple(42.87, 74.44, listOf("bishkek", "sokuluk", "manas-airport")),
            Triple(42.9463, 74.3809, listOf("sokuluk", "manas-airport", "bishkek")),
            Triple(42.88778, 75.085, listOf("tokmok", "kant")),
        )
        for ((lat, lon, ids) in cases) {
            val m = assertNotNull(resolve(lat, lon).method)
            val ridden = listOfNotNull(m.fixedPoint) + m.envelopePoints
            for (id in ids) {
                val unit = units.getValue(id)
                assertTrue(distanceKm(GeoPoint(lat, lon), unit.point) <= unit.radiusKm, "$lat,$lon is outside $id's reach")
                assertTrue(tablePoint(unit) in ridden, "$lat,$lon does not ride $id's table")
            }
        }
    }

    /** Aravan town keeps the Aravan table's point as its fixed point whatever rides beside it (ruling R205). */
    @Test
    fun `aravan town keeps its table's point where a neighbour rides beside it`() {
        val units = assertNotNull(Units.of("kg.default")).units
        val town = units.single { it.id == "aravan-town" }
        val table = tablePoint(town)
        val neighbours = units.filter { it.id != town.id && distanceKm(town.point, it.point) < town.radiusKm + it.radiusKm }
        assertTrue(neighbours.isNotEmpty(), "Aravan town has no neighbour within reach")
        for (n in neighbours) {
            // A place a neighbour's reach and the town's both hold: the town's point where the neighbour reaches it, else between.
            val p = if (distanceKm(town.point, n.point) <= n.radiusKm) town.point else GeoPoint((town.point.lat + n.point.lat) / 2, (town.point.lon + n.point.lon) / 2)
            if (distanceKm(p, town.point) > town.radiusKm || distanceKm(p, n.point) > n.radiusKm) continue
            val r = resolve(p.lat, p.lon)
            val m = assertNotNull(r.method)
            if (r.unitId == town.id) {
                assertEquals(table, m.fixedPoint, "the town's fixed point is its table's")
                assertTrue(n.point in m.envelopePoints, "the town does not ride ${n.id}")
            } else {
                assertTrue(table in m.envelopePoints, "${r.unitId} inside the town's reach does not ride the Aravan table's point")
            }
        }
    }

    @Test
    fun `beyond every locality's reach the method is the user's own a minute later and claims no class B`() {
        // The Naryn region's east, about 100 km from the nearest locality.
        val r = resolve(41.0, 77.5)
        assertEquals("kg.default", r.entry.id)
        assertNull(r.unitId)
        assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
        val m = r.method!!
        assertNull(m.fixedPoint)
        assertEquals(CentralAsia.kyrgyzstanMethod.margins.fajr + 60, m.margins.fajr)
    }

    /**
     * Ruling R260 (the second audit's G1, 14 Nov 2026): past every reach the nearest locality's own day, and any
     * locality's no more than a quarter farther, rides beside the edge's, out to five minutes of longitude from its
     * point; the edge's own day is what it was, and the place keeps class D. The audit's points, 26 km east and west of
     * Kochkor's point, had shown Maghrib before Kochkor's printed table and ended eating after its printed Fajr in
     * Ramadan 1448; and every locality's own ground just past its reach and 30 km farther on, every thirteenth day of
     * 2027, is never before that locality's day nor the edge's.
     */
    @Test
    fun `past every reach the nearest locality's own day rides beside the edge`() {
        val units = assertNotNull(Units.of("kg.default")).units
        val tz = TimeZone.of(zone)
        val kochkor = units.single { it.id == "kochkor" }
        fun days(): List<LocalDate> = generateSequence(LocalDate(2027, 1, 1)) { it.plus(13, DateTimeUnit.DAY) }.takeWhile { it.year == 2027 }.toList()
        fun check(p: GeoPoint, unit: AuthorityUnit, what: String) {
            val r = resolve(p.lat, p.lon)
            assertNull(r.unitId, what)
            assertEquals(EntryClass.D_AUTHORITY, r.entryClass, what)
            val m = assertNotNull(r.method)
            assertNull(m.fixedPoint, what)
            assertEquals(CentralAsia.kyrgyzstanMethod.margins.fajr + 60, m.margins.fajr, what)
            val riding = ridingBeyond(units, p)
            assertEquals(unit, riding.first(), what)
            assertEquals(riding.map { it.point }, m.alongside.map { it.point }, what)
            val alone = r.copy(method = m.copy(alongside = emptyList()))
            for (date in days()) {
                val shown = DayPipeline.unended(r, date, tz)
                for (rider in riding) assertNeverBefore(shown, tableDay(r, rider, date, tz), "$what beside ${rider.id} on $date")
                assertNeverBefore(shown, DayPipeline.unended(alone, date, tz), "$what beside the edge alone on $date")
            }
        }
        check(GeoPoint(42.21377, 76.06970), kochkor, "26 km east of Kochkor")
        check(GeoPoint(42.21377, 75.43830), kochkor, "26 km west of Kochkor")
        var checked = 0
        for (unit in units) for (bearing in listOf(0.0, 90.0, 180.0, 270.0)) for (past in listOf(0.5, 30.0)) {
            val km = unit.radiusKm + past
            val rad = bearing * kotlin.math.PI / 180
            val p = GeoPoint(unit.point.lat + km * kotlin.math.cos(rad) / 111.195, unit.point.lon + km * kotlin.math.sin(rad) / (111.195 * kotlin.math.cos(unit.point.lat * kotlin.math.PI / 180)))
            if (units.any { distanceKm(p, it.point) <= it.radiusKm }) continue
            if (units.minBy { distanceKm(p, it.point) } != unit) continue
            check(p, unit, "${unit.id} at ${km.toInt()} km, bearing $bearing")
            checked++
        }
        assertTrue(checked > 100, "checked $checked points")
        // Farther than five minutes of longitude from every locality, the edge alone, as before.
        for (unit in units) {
            val p = GeoPoint(unit.point.lat - (edgeRideKm(unit) + 1.0) / 111.195, unit.point.lon)
            assertTrue(unit !in ridingBeyond(units, p), unit.id)
        }
    }

    @Test
    fun `dhuhr is never before the date's own transit from 2024 to 2035`() {
        // The Muftiate prints the transit of 2023's sun rounded to the nearest minute; a year's own transit is up to 23 s
        // from 2023's through the leap cycle. Every ninth day at the localities farthest north, south, east and west and
        // at the app's largest cities, and Aravan town.
        val keys = listOf("bishkek", "osh", "kamyshanovka", "daroot-korgon", "engilchek", "kok-tash-batken", "karakol", "naryn", "batken")
        val points = keys.map { k -> KyrgyzLocalities.all.single { it.key == k }.let { GeoPoint(it.lat, it.lon) } } +
            KyrgyzLocalities.aravanTown.let { GeoPoint(it.lat, it.lon) }
        val tz = TimeZone.of(zone)
        var checked = 0
        for (p in points) {
            val resolution = resolve(p.lat, p.lon)
            var date = LocalDate(2024, 1, 1)
            while (date.year <= 2035) {
                val shown = DayPipeline.unended(resolution, date, tz).dhuhr
                val noonOffset = tz.offsetAt(date.atTime(12, 0).toInstant(tz)).totalSeconds
                val transit = SunClock(p.lat, p.lon, date, noonOffset, SunModel.EXACT).transit()
                val own = Instant.fromEpochMilliseconds((transit * 1000.0).roundToLong())
                assertTrue(shown >= own, "${p.lat},${p.lon} on $date: Dhuhr $shown before the sun's own transit $own")
                checked++
                date = date.plus(9, DateTimeUnit.DAY)
            }
        }
        assertTrue(checked > 4000, "checked $checked days")
    }
}
