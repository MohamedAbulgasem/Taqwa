package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.CentralAsia
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ruling R249 (the owner's ruling of 13 Nov 2026, which replaces the nearest place alone of 9 Oct 2026 for
 * Kazakhstan): QMDB prints a table for each of its places, draws no boundary between them, and their reaches overlap,
 * so a place inside several waits for every one of those tables. QMDB's printed tables are restricted data and are
 * held by the gate's rows (kz-qmdb.tsv, kz-qmdb-reach.tsv, kz-qmdb-envelope.tsv); here each table is the engine's own
 * day at the place's own point, and the test holds the rule, never a printed minute.
 */
class KazakhRideTest {

    private val id = "kz.qmdb"
    private val zone = "Asia/Almaty"
    private val tz = TimeZone.of(zone)
    private val set by lazy { assertNotNull(Units.of(id)) }
    private val base by lazy { assertNotNull(assertNotNull(Registry.byId(id)).method) }

    /** QMDB's densest ground (Maktaaral and Zhetysay's villages): two of its points, each inside more than 150 reaches. */
    private val dense = listOf(GeoPoint(40.686322, 68.285832), GeoPoint(40.901015, 68.453861))

    /** Ground where a table set aside by the place followed and the user alone had the earliest Isha (the review of 14 Nov 2026). */
    private val ends = listOf(
        GeoPoint(43.215073920021645, 77.72956358709088), GeoPoint(43.29302673754177, 77.74287117179722),
        GeoPoint(41.13159685805267, 69.22774216749441),
    )

    private fun resolve(at: GeoPoint) = Registry.resolve(Place(at.lat, at.lon, zone, "KZ"))

    private val tables = HashMap<String, TimetableMethod>()

    /** [unit]'s own table: its method at its own point, nothing riding beside it. */
    private fun table(unit: AuthorityUnit): TimetableMethod =
        tables.getOrPut(unit.id) { Registry.methodInUnit(id, base, unit, unit.point, zone, alone = true) }

    /** The day [unit]'s own table gives at its own point. */
    private fun tableDay(at: Resolution, unit: AuthorityUnit, date: LocalDate): PrayerDay =
        DayPipeline.unended(at.copy(point = unit.point, method = table(unit)), date, tz)

    /** QMDB's three bands: south of 46° N, from there to 48° N, and north of it. */
    private fun band(point: GeoPoint): Int = if (point.lat >= 48.0) 2 else if (point.lat >= 46.0) 1 else 0

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
     * Places inside several reaches, across the whole list: every 23rd place of QMDB's and every place within a
     * sixth of a degree of 46° N and 48° N (where the bands' minutes and margins change), each at its own point and
     * halfway to its nearest neighbour, wherever more than one reach holds the point; the middle of the ground two
     * places of different bands share (a dozen pairs, all across 46° N: no reach crosses 48° N into another's); and
     * the densest ground.
     */
    private fun overlapPoints(): List<GeoPoint> {
        val units = set.units
        val picked = units.filterIndexed { i, u -> i % 23 == 0 || abs(u.point.lat - 46.0) < 1.0 / 6 || abs(u.point.lat - 48.0) < 1.0 / 6 }
        val points = ArrayList<GeoPoint>(dense)
        for (u in picked) {
            points += u.point
            val near = set.holding(u.point).filter { it.id != u.id }.minByOrNull { distanceKm(u.point, it.point) } ?: continue
            points += GeoPoint((u.point.lat + near.point.lat) / 2, (u.point.lon + near.point.lon) / 2)
        }
        val edge = units.filter { abs(it.point.lat - 46.0) < 1.0 || abs(it.point.lat - 48.0) < 1.0 }
        for (a in edge) for (b in edge) {
            if (band(a.point) >= band(b.point)) continue
            val apart = distanceKm(a.point, b.point)
            if (apart >= a.radiusKm + b.radiusKm) continue
            // Along the line from a to b, the stretch both reaches hold runs from (apart - b's reach) to a's reach.
            val along = (maxOf(apart - b.radiusKm, 0.0) + minOf(a.radiusKm, apart)) / 2 / apart
            points += GeoPoint(a.point.lat + along * (b.point.lat - a.point.lat), a.point.lon + along * (b.point.lon - a.point.lon))
        }
        return points.filter { set.holding(it).size > 1 }
    }

    @Test
    fun `a place inside several QMDB places' reaches is never before any of their tables`() {
        var places = 0
        var acrossBands = 0
        var tables = 0
        for (p in overlapPoints()) {
            val holders = set.holding(p)
            places++
            tables += holders.size
            if (holders.map { band(it.point) }.toSet().size > 1) acrossBands++
            val r = resolve(p)
            assertEquals(id, r.entry.id)
            var date = LocalDate(2027, 1, 1 + places % 20)
            while (date.year == 2027) {
                val shown = DayPipeline.unended(r, date, tz)
                for (h in holders) assertNeverBefore(shown, tableDay(r, h, date), "${p.lat},${p.lon} (${r.unitId}) against ${h.id} on $date")
                date = date.plus(29, DateTimeUnit.DAY)
            }
        }
        assertTrue(places > 300, "$places places inside several reaches")
        assertTrue(acrossBands > 5, "$acrossBands places whose tables lie in more than one band")
        assertTrue(tables > 5 * places, "$tables tables over $places places")
    }

    @Test
    fun `the pairs the audit named wait for each other's tables at the app's own city points and between them`() {
        // The audit of 12 Nov 2026 (the R205 entry's Kazakh paragraph): with the nearest place alone Talgar was shown
        // before Almaty's table, Kentau before Turkistan's, and Almaty ended eating after Talgar's Fajr; Konaev and
        // Otegen Batyr were among the worst pairs of the sweep. Each city at the app's own point for it, wherever the
        // other's reach holds that point, and the middle of the ground the two reaches share, every day of the year.
        val byKey = set.units.associateBy { it.id }
        val cityPoint = QmdbPlaces.appCities.associate { it.key to GeoPoint(it.lat, it.lon) }
        for ((first, second) in listOf("talgar" to "almaty", "kentau" to "turkistan", "konaev" to "otegen-batyr")) {
            val a = assertNotNull(byKey[first])
            val b = assertNotNull(byKey[second])
            val apart = distanceKm(a.point, b.point)
            assertTrue(apart < a.radiusKm + b.radiusKm, "$first and $second share no ground")
            val along = (maxOf(apart - b.radiusKm, 0.0) + minOf(a.radiusKm, apart)) / 2 / apart
            val between = GeoPoint(a.point.lat + along * (b.point.lat - a.point.lat), a.point.lon + along * (b.point.lon - a.point.lon))
            var held = 0
            for (p in listOf(assertNotNull(cityPoint[first]), assertNotNull(cityPoint[second]), between)) {
                val tables = set.holding(p).filter { it.id == first || it.id == second }
                if (p === between) assertEquals(2, tables.size, "both reaches hold the ground between $first and $second")
                val r = resolve(p)
                for (h in tables) {
                    held++
                    var date = LocalDate(2027, 1, 1)
                    while (date.year == 2027) {
                        assertNeverBefore(DayPipeline.unended(r, date, tz), tableDay(r, h, date), "${p.lat},${p.lon} (${r.unitId}) against ${h.id} on $date")
                        date = date.plus(1, DateTimeUnit.DAY)
                    }
                }
            }
            // Each city's own table at its own point, both tables between them, and a city inside the other's reach.
            assertTrue(held >= 4, "$first and $second: $held tables held")
        }
        // Talgar's own point lies inside Almaty's reach and Kentau's inside Turkistan's: the two cases the audit named.
        for ((city, other) in listOf("talgar" to "almaty", "kentau" to "turkistan")) {
            val p = assertNotNull(cityPoint[city])
            assertEquals(city, resolve(p).unitId)
            assertTrue(set.holding(p).any { it.id == other }, "$other's reach holds $city's own point")
        }
    }

    @Test
    fun `the place followed and named is still the city's or the nearest`() {
        // Ruling R249 changes what rides, not whose table a place is: the app's city where its reach holds the user,
        // else the nearest place, exactly as before (AsiaProofTest holds the named cases).
        for (p in overlapPoints()) {
            val place = Place(p.lat, p.lon, zone, "KZ")
            val unit = assertNotNull(set.unitFor(place))
            val r = resolve(p)
            assertEquals(unit.id, r.unitId)
            assertEquals(unit.cityId, r.unitCityId)
            assertEquals(unit.measured, r.measured)
            assertEquals(unit.point, assertNotNull(r.method).fixedPoint, "${unit.id}'s point rides beside the user's")
        }
    }

    @Test
    fun `from 46 degrees north every other table whose reach holds the place rides as its own point shows it`() {
        var riding = 0
        for (p in overlapPoints()) {
            val r = resolve(p)
            val m = assertNotNull(r.method)
            val ridden = m.alongside.associateBy { it.point }
            for (h in set.holding(p)) {
                if (h.id == r.unitId) {
                    assertTrue(h.point !in ridden, "${h.id} rides beside itself at ${p.lat},${p.lon}")
                    continue
                }
                if (h.point.lat < 46.0) continue
                val beside = assertNotNull(ridden[h.point], "${p.lat},${p.lon} follows ${r.unitId} inside ${h.id}'s reach and does not ride its table")
                riding++
                // Its own band's minutes and margin, read at its own point, whatever band the user stands in.
                val own = table(h)
                assertEquals(own.authorityMinutes, beside.method.authorityMinutes, h.id)
                assertEquals(own.endOfEatingMarginSeconds, beside.method.endOfEatingMarginSeconds, h.id)
                assertEquals(null, beside.method.fixedPoint, h.id)
            }
            // Nothing rides from beyond its own reach.
            for (a in m.alongside) {
                assertTrue(set.holding(p).any { it.point == a.point }, "${p.lat},${p.lon} rides a table at ${a.point} whose reach does not hold it")
            }
        }
        assertTrue(riding > 500, "$riding tables riding from 46 degrees north")
    }

    /** [method] with every other holder of [p] riding, none set aside: what ruling R249 asks for in full. */
    private fun withEveryTable(method: TimetableMethod, p: GeoPoint, followed: String?): TimetableMethod = method.copy(
        alongside = set.holding(p).filter { it.id != followed }.map {
            Alongside(it.point, table(it).copy(fixedPoint = null))
        },
    )

    @Test
    fun `a southern table set aside is bounded by the tables that ride so the day is the same with it`() {
        // Ruling R251: south of 46° N a table with neighbours to its north and south on both sides is never the one
        // that sets a time, so it is not computed. The day shown equals the day with every table riding, flags and all.
        val south = overlapPoints().filter { it.lat < 46.2 }
        var setAside = 0
        for ((i, p) in (dense + south).withIndex()) {
            val r = resolve(p)
            val m = assertNotNull(r.method)
            val all = withEveryTable(m, p, r.unitId)
            setAside += all.alongside.size - m.alongside.size
            var date = LocalDate(2027, 1, 1 + (if (i < dense.size) 0 else i % 11))
            while (date.year == 2027) {
                assertEquals(DayPipeline.unended(r.copy(method = all), date, tz), DayPipeline.unended(r, date, tz), "${p.lat},${p.lon} on $date")
                date = date.plus(if (i < dense.size) 1 else 11, DateTimeUnit.DAY)
            }
        }
        assertTrue(setAside > 1000, "$setAside tables set aside")
    }

    @Test
    fun `a southern table set aside never ends a prayer before the tables that ride on any day of the year`() {
        // Ruling R251, the review of 14 Nov 2026: the day's own Isha and Asr are the later of the place followed and
        // the user, and they are Maghrib's and Dhuhr's ends, so the place followed and the user bound another table's
        // starts from the west and never its ends from the east. Counted as bounds on both sides, a table whose Isha
        // came before the day's own was set aside and Maghrib ended a minute after it on some twenty days of a summer
        // at these points (east of Esik and south of Abay). Every day of two years, the whole day and its ends.
        var setAside = 0
        for (p in ends) {
            val r = resolve(p)
            val m = assertNotNull(r.method)
            val all = withEveryTable(m, p, r.unitId)
            setAside += all.alongside.size - m.alongside.size
            var date = LocalDate(2027, 1, 1)
            while (date.year < 2029) {
                assertEquals(DayPipeline.unended(r.copy(method = all), date, tz), DayPipeline.unended(r, date, tz), "${p.lat},${p.lon} on $date")
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
        assertTrue(setAside > 0, "$setAside tables set aside")
    }

    @Test
    fun `the place followed and the user bound a table from the west alone`() {
        // The rule itself, on a made ground: a table with the place followed to its north-east and the user to its
        // south-east (and two tables west of it) still rides, since the day's own Isha is the later of those two and
        // cannot hold Maghrib's end; with two other tables to its east it is set aside.
        fun unit(id: String, lat: Double, lon: Double) = AuthorityUnit(id, id, GeoPoint(lat, lon), 40.0, method = CentralAsia.kazakhstanAt(GeoPoint(lat, lon)))
        val followed = unit("followed", 43.3, 77.2)
        val user = GeoPoint(43.1, 77.2)
        val middle = unit("middle", 43.2, 77.0)
        val west = listOf(unit("north-west", 43.3, 76.8), unit("south-west", 43.1, 76.8))
        val east = listOf(unit("north-east", 43.3, 77.1), unit("south-east", 43.1, 77.1))
        assertTrue(middle in CentralAsia.riding(user, followed, listOf(followed, middle) + west), "bounded from the east by the day's own points alone")
        assertTrue(middle !in CentralAsia.riding(user, followed, listOf(followed, middle) + west + east), "bounded by other tables on every side")
        // From the west the place followed and the user do bound it: with them to its west and two tables to its east.
        val eastward = unit("eastward", 43.2, 77.4)
        val beyond = listOf(unit("far north-east", 43.3, 77.6), unit("far south-east", 43.1, 77.6))
        assertTrue(eastward !in CentralAsia.riding(user, followed, listOf(followed, eastward) + beyond), "bounded from the west by the day's own points")
    }

    @Test
    fun `the densest ground computes a bounded number of days`() {
        for (p in dense) {
            val holders = set.holding(p)
            assertTrue(holders.size > 150, "${holders.size} reaches hold ${p.lat},${p.lon}")
            val riders = assertNotNull(resolve(p).method).alongside.size
            assertTrue(riders in 1..60, "$riders days alongside at ${p.lat},${p.lon}")
        }
        // Nowhere on the list's own points does a place compute more than 60 days beside its own.
        val most = set.units.filterIndexed { i, _ -> i % 7 == 0 }.maxOf { assertNotNull(resolve(it.point).method).alongside.size }
        assertTrue(most <= 60, "$most days alongside")
    }

    @Test
    fun `south of 46 degrees north the AngleBased rule never binds and every place keeps one method`() {
        // What ruling R251 stands on: the band's curves are flat, so two places differ only by where the sun is.
        for (lat in listOf(40.6, 43.25, 45.0, 45.9, 45.999)) {
            val user = GeoPoint(lat, 68.0)
            val unit = AuthorityUnit("probe", "Probe", GeoPoint(lat - 0.2, 68.1), 40.0)
            for (m in listOf(Registry.methodAlone(id, base, user, zone), Registry.methodInUnit(id, base, unit, user, zone, alone = true))) {
                assertTrue(m.fajrAngleByDayOfYear?.all { it == m.fajrAngle } ?: true, "Fajr's curve at $lat")
                val isha = (m.isha as IshaRule.Angle).degrees
                assertTrue(m.ishaAngleByDayOfYear?.all { it == isha } ?: true, "Isha's curve at $lat")
                val end = m.endOfEating
                if (end is EndOfEating.DawnAngle) assertTrue(end.bySlot?.all { it == end.degrees } ?: true, "the end of eating's curve at $lat")
            }
        }
    }

    @Test
    fun `a place's own late limit says the wait and is one the entry's or the class's does not already give`() {
        // Ruling R252: the wait for the other tables is part of what a place's reach costs, so a checked place's own
        // limit for an event is stated only where the gate's worst there passes the entry's limit (or class D's 3 for
        // the events the entry states none for), names the wait in its reason, and no unchecked place claims one.
        val entry = assertNotNull(Registry.byId(id))
        val carrying = set.units.filter { it.lateLimits.isNotEmpty() }
        assertTrue(carrying.isNotEmpty())
        for (unit in carrying) {
            assertTrue(unit.measured, "${unit.id} carries a limit without a checked table")
            for (limit in unit.lateLimits) for (event in limit.events) {
                val otherwise = entry.lateLimits.firstOrNull { event in it.events }?.minutes ?: 3
                assertTrue(limit.minutes > otherwise, "${unit.id} $event: ${limit.minutes} is within the $otherwise it had")
                assertEquals(limit, lateLimitFor(event, unit, entry), "${unit.id} $event")
                assertTrue("whose reach holds the user" in limit.reason, "${unit.id} $event: ${limit.reason}")
            }
        }
        // A place with no limit of its own keeps the entry's for Fajr and the class's (none stated) for the sun's events.
        val plain = set.units.first { it.lateLimits.isEmpty() }
        assertEquals(entry.lateLimits.first { TimedEvent.FAJR in it.events }, lateLimitFor(TimedEvent.FAJR, plain, entry))
        assertEquals(null, lateLimitFor(TimedEvent.MAGHRIB, plain, entry))
    }

    @Test
    fun `a reach is still measured with its own table alone`() {
        // Ruling R250: each place keeps the reach it had, the distance its own table carries by itself (QmdbReach).
        val p = dense.first()
        val unit = assertNotNull(set.unitFor(Place(p.lat, p.lon, zone, "KZ")))
        assertTrue(Registry.methodInUnit(id, base, unit, p, zone).alongside.isNotEmpty())
        assertTrue(Registry.methodInUnit(id, base, unit, p, zone, alone = true).alongside.isEmpty())
        // A place no other reach holds shows what it showed.
        val lone = set.units.first { set.holding(it.point).size == 1 }
        val r = resolve(lone.point)
        assertEquals(lone.id, r.unitId)
        assertTrue(assertNotNull(r.method).alongside.isEmpty())
        var date = LocalDate(2027, 1, 1)
        while (date.year == 2027) {
            assertEquals(tableDay(r, lone, date), DayPipeline.unended(r, date, tz), "${lone.id} on $date")
            date = date.plus(31, DateTimeUnit.DAY)
        }
    }
}
