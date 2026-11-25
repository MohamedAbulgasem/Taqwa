package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.SouthAsia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rulings R190 to R194 (13 Nov 2026), under the owner's rule "never before the timetable the local majority follows":
 * Dawat-e-Islami prints a card for each city of Pakistan and India, later than the Karachi method at the same point, and a
 * place is never before its own city's card nor any end after it. The cards themselves are restricted data (ruling R69):
 * this test holds the rules only, never a printed minute; the gate holds the cards (`pk-dawateislami.tsv`,
 * `in-dawateislami.tsv`).
 */
class CityCardsTest {
    private val dates = (1..12).flatMap { m -> listOf(LocalDate(2026, m, 3), LocalDate(2026, m, 18)) }

    private class Country(
        val entryId: String,
        val code: String,
        val zone: String,
        val base: TimetableMethod,
        val cards: List<SouthAsia.CityCard>,
        val table: String,
        val beyond: (GeoPoint) -> TimetableMethod,
    )

    private val pakistan = Country("pk.karachi", "PK", "Asia/Karachi", SouthAsia.pakistanMethod, SouthAsia.pakistanCards, "karachi", SouthAsia::pakistanBeyondCards)
    private val india = Country("in.karachi", "IN", "Asia/Kolkata", SouthAsia.indiaMethod, SouthAsia.indiaCards, "delhi", SouthAsia::indiaBeyondCards)
    private val countries = listOf(pakistan, india)

    private fun Country.at(p: GeoPoint) = Registry.resolveEntry(Registry.byId(entryId)!!, Place(p.lat, p.lon, zone, code))

    private fun day(method: TimetableMethod, point: GeoPoint, date: LocalDate, zone: String, school: AsrSchool): PrayerDay =
        DayComputer.compute(method, point, date, TimeZone.of(zone), school, Registry.ramadanCalendar())

    /** [shown] never before [table] and no end after it. */
    private fun assertBounded(shown: PrayerDay, table: PrayerDay, what: String) {
        assertTrue(shown.fajr >= table.fajr && shown.dhuhr >= table.dhuhr && shown.asr >= table.asr, "$what: a start before")
        assertTrue(shown.maghrib >= table.maghrib && shown.isha >= table.isha, "$what: a start before")
        assertTrue(shown.sunrise <= table.sunrise && shown.endOfEating <= table.endOfEating, "$what: an end after")
    }

    /** [later]'s margins never before [earlier]'s for any start, nor after for any end. */
    private fun assertNeverBefore(later: TimetableMethod, earlier: TimetableMethod, what: String) {
        val a = later.margins
        val b = earlier.margins
        assertTrue(a.fajr >= b.fajr && a.dhuhr >= b.dhuhr && a.asr >= b.asr && a.maghrib >= b.maghrib && a.isha >= b.isha, "$what: a start before")
        assertTrue(a.sunrise <= b.sunrise && later.endOfEatingMarginSeconds <= earlier.endOfEatingMarginSeconds, "$what: an end after")
    }

    /** [km] north and [eastKm] east of [p]. */
    private fun GeoPoint.moved(km: Double, eastKm: Double = 0.0) =
        GeoPoint(lat + km / 111.32, lon + eastKm / (111.32 * kotlin.math.cos(lat * kotlin.math.PI / 180)))

    /** [method] holds [card]'s own worst seconds, with no safety: no start's margin before the card's, no end's after. */
    private fun assertCovers(method: TimetableMethod, card: SouthAsia.CityCard, what: String) {
        val m = method.margins
        assertTrue(m.fajr >= card.fajr && m.dhuhr >= card.dhuhr && m.asr >= card.asr, "$what: a start before ${card.key}'s card")
        assertTrue(m.maghrib >= card.maghrib && m.isha >= card.isha, "$what: a start before ${card.key}'s card")
        assertTrue(m.sunrise <= card.sunrise && method.endOfEatingMarginSeconds <= card.endOfEating, "$what: an end after ${card.key}'s card")
    }

    @Test
    fun `a card moves a method later only and every card keeps the same safety past its own worst second`() {
        // Ruling R235 (13 Nov 2026): November is the one month a plain request reaches, for every card, so no card is
        // held through the seasons and none keeps less than another. The safety is one figure, never under 45 s.
        assertTrue(SouthAsia.CARD_SAFETY >= 45, "no card's other months are held: none keeps under 45 s")
        for (country in countries) for (card in country.cards) {
            val on = card.on(country.base)
            assertNeverBefore(on, country.base, "${card.key} against the method")
            val m = on.margins
            val safety = SouthAsia.CARD_SAFETY
            assertTrue(m.fajr >= card.fajr + safety && m.dhuhr >= card.dhuhr + safety && m.asr >= card.asr + safety, card.key)
            assertTrue(m.maghrib >= card.maghrib + safety && m.isha >= card.isha + safety, card.key)
            assertTrue(m.sunrise <= card.sunrise - safety && on.endOfEatingMarginSeconds <= card.endOfEating - safety, card.key)
            // Nothing but the margins: the Karachi method's angles and rules are the card's own.
            assertEquals(country.base, on.copy(margins = country.base.margins, endOfEatingMarginSeconds = country.base.endOfEatingMarginSeconds))
        }
    }

    @Test
    fun `each card is its own city's unit and no farther while the entry's own table rides as it did`() {
        for (country in countries) {
            val units = assertNotNull(Units.of(country.entryId))
            val envelope = assertNotNull(units.envelope)
            assertEquals(country.cards.map { it.key }, units.units.map { it.id })
            for (card in country.cards) {
                val unit = units.unit(card.key)
                assertEquals(card.key, units.unitFor(Place(card.point.lat, card.point.lon, country.zone, country.code))?.id)
                assertEquals(lateReachKm(card.point.lat, EntryClass.A), unit.radiusKm, 1e-9, "${card.key}: a minute's reach")
                assertEquals(false, unit.measured, "${card.key}: no figure is claimed")
                if (card.key == country.table) {
                    assertNull(unit.method, "the table's unit is the entry's own method, its card within the unit only")
                    assertEquals(lateReachKm(card.point.lat, EntryClass.D_AUTHORITY), envelope.boundKm(unit), 1e-9)
                    assertEquals(RIDE_REACHES * unit.radiusKm, envelope.rideKm(unit), 1e-9)
                } else {
                    assertEquals(card.on(country.base), unit.method)
                    assertEquals(unit.radiusKm, envelope.boundKm(unit), 1e-9, "${card.key}: bound to its own city")
                    assertEquals(0.0, envelope.rideKm(unit), 0.0, "${card.key}: a city's card never rides past its city")
                }
            }
        }
    }

    @Test
    fun `at a card's city the day shown is never before the card's own day nor any end after it`() {
        for (country in countries) for (card in country.cards) {
            val r = country.at(card.point)
            assertEquals(card.key, r.unitId)
            assertEquals(EntryClass.D_AUTHORITY, r.entryClass, "${card.key}: a published method, not a checked timetable")
            assertEquals(false, r.measured, "${card.key}: no figure is claimed")
            val method = assertNotNull(r.method)
            val own = card.on(country.base)
            for (school in AsrSchool.entries) for (date in dates) {
                val shown = day(method, card.point, date, country.zone, school)
                assertBounded(shown, day(own, card.point, date, country.zone, school), "${card.key} $date $school")
                // The entry's own table as well (Banuri's at Karachi, the Delhi table): the later of two bodies holds.
                assertBounded(shown, day(country.base, card.point, date, country.zone, school), "${card.key} $date $school, the method")
            }
        }
    }

    @Test
    fun `where several cities' cards hold a place it is never before any of them`() {
        val units = assertNotNull(Units.of(pakistan.entryId))
        val pairs = pakistan.cards.flatMap { a -> pakistan.cards.filter { it.key > a.key }.map { a to it } }.filter { (a, b) ->
            distanceKm(a.point, b.point) < 1.9 * minOf(units.unit(a.key).radiusKm, units.unit(b.key).radiusKm)
        }
        assertTrue(pairs.isNotEmpty(), "Islamabad and Rawalpindi, Haripur and Havelian")
        for ((a, b) in pairs) {
            val mid = GeoPoint((a.point.lat + b.point.lat) / 2, (a.point.lon + b.point.lon) / 2)
            // Every card whose own city holds the midpoint: the two, and a third between them where there is one.
            val holding = pakistan.cards.filter { distanceKm(mid, it.point) <= units.unit(it.key).radiusKm }
            assertTrue(a in holding && b in holding, "${a.key}/${b.key}")
            val r = pakistan.at(mid)
            assertTrue(holding.any { it.key == r.unitId }, "${a.key}/${b.key}: ${r.unitId}")
            val method = assertNotNull(r.method)
            for (school in AsrSchool.entries) for (date in dates) {
                val shown = day(method, mid, date, pakistan.zone, school)
                for (card in holding) {
                    assertBounded(shown, day(card.on(pakistan.base), card.point, date, pakistan.zone, school), "${a.key}/${b.key}: ${card.key} $date $school")
                }
            }
        }
    }

    @Test
    fun `a city whose own card is held is its own unit even within another city's reach`() {
        // The review of 13 Nov 2026: the app's Kallar Kahar lay in Chakwal's unit, and its own card begins Maghrib after
        // Chakwal's with its safety. A card held is its city's unit, whichever other card's reach holds its point; the
        // other card rides beside it, so the day is never before either.
        for (country in countries) {
            val units = assertNotNull(Units.of(country.entryId))
            val inside = country.cards.flatMap { a -> country.cards.filter { it !== a && distanceKm(a.point, it.point) <= units.unit(it.key).radiusKm }.map { a to it } }
            if (country === pakistan) assertTrue(inside.size >= 4, "Kallar Kahar within Chakwal's reach, Khairpur within Sukkur's, Wazirabad within Gujrat's")
            for ((card, other) in inside) {
                val r = country.at(card.point)
                assertEquals(card.key, r.unitId, "${card.key} within ${other.key}'s reach")
                val method = assertNotNull(r.method)
                assertTrue(method.alongside.any { it.point == other.point }, "${other.key}'s card rides beside ${card.key}'s")
                for (school in AsrSchool.entries) for (date in dates) {
                    val shown = day(method, card.point, date, country.zone, school)
                    assertBounded(shown, day(card.on(country.base), card.point, date, country.zone, school), "${card.key} $date $school")
                    assertBounded(shown, day(other.on(country.base), other.point, date, country.zone, school), "${card.key} under ${other.key} $date $school")
                }
            }
        }
    }

    @Test
    fun `within a card's unit past the city itself a place is never before what it would take with no card held`() {
        // Ruling R194: the next town has a card of its own, not held, so the unit's card alone proves nothing there.
        var checked = 0
        for (country in countries) {
            val units = assertNotNull(Units.of(country.entryId))
            for (card in country.cards.filter { it.key != country.table }) {
                val radius = units.unit(card.key).radiusKm
                assertTrue(SouthAsia.CITY_KM < radius, "${card.key}: the city itself is within its unit")
                val near = card.point.moved(SouthAsia.CITY_KM / 2)
                val nearMethod = assertNotNull(country.at(near).method)
                if (country.at(near).unitId == card.key) {
                    assertTrue(nearMethod.alongside.none { it.point == near }, "${card.key}: the city itself is its card's alone")
                }
                for (far in listOf(card.point.moved((SouthAsia.CITY_KM + radius) / 2), card.point.moved(0.0, -(SouthAsia.CITY_KM + radius) / 2))) {
                    val r = country.at(far)
                    val unit = country.cards.firstOrNull { it.key == r.unitId && it.key != country.table } ?: continue
                    if (distanceKm(far, unit.point) <= SouthAsia.CITY_KM) continue
                    checked++
                    val method = assertNotNull(r.method)
                    val beyond = country.beyond(far)
                    val own = assertNotNull(method.alongside.singleOrNull { it.point == far }, "${card.key}: the place's own day rides beside the card's")
                    assertEquals(beyond.margins, own.method.margins, card.key)
                    assertEquals(beyond.endOfEatingMarginSeconds, own.method.endOfEatingMarginSeconds, card.key)
                    val alone = Registry.methodAlone(country.entryId, beyond, far, country.zone)
                    for (school in AsrSchool.entries) for (date in dates) {
                        val shown = day(method, far, date, country.zone, school)
                        assertBounded(shown, day(alone, far, date, country.zone, school), "${unit.key} at $far $date $school, with no card held")
                        assertBounded(shown, day(unit.on(country.base), unit.point, date, country.zone, school), "${unit.key} at $far $date $school, the unit's card")
                    }
                }
            }
        }
        assertTrue(checked > countries.sumOf { it.cards.size }, "most cards' outskirts are theirs: $checked")
    }

    @Test
    fun `every held card left out would be covered by what the others ask`() {
        // The proof there is for a place with no card held (rulings R192 and R194): each held card, were it not held, is no
        // later than the margins the other cards make for its place, on every event, with no safety of its own counted.
        for (card in pakistan.cards) {
            val others = pakistan.cards - card
            assertCovers(SouthAsia.bandOf(others, SouthAsia.terrain(card.point)), card, "the others' ${SouthAsia.terrain(card.point)}")
        }
        for (card in india.cards) assertCovers(SouthAsia.latestOf(india.base, india.cards - card), card, "the other Indian cards")
    }

    @Test
    fun `Karachi and the coast east of it lie in the plains`() {
        // The review of 13 Nov 2026: read as upland, the eastern suburbs past Karachi's unit ended sunrise 4 min before
        // Karachi's own card. The Hub river's west bank and Balochistan stay upland.
        val karachi = pakistan.cards.single { it.key == pakistan.table }.point
        for (km in listOf(0.0, 20.0, 35.0, 60.0, 110.0)) assertEquals(SouthAsia.Terrain.PLAINS, SouthAsia.terrain(karachi.moved(0.0, km)), "$km km east")
        assertEquals(SouthAsia.Terrain.PLAINS, SouthAsia.terrain(karachi.moved(20.0, 30.0)))
        assertEquals(SouthAsia.Terrain.UPLAND, SouthAsia.terrain(karachi.moved(10.0, -40.0)))
    }

    @Test
    fun `a Pakistani place with no card held takes the latest margins the held cards ask for its terrain`() {
        val bands = pakistan.cards.groupBy { SouthAsia.terrain(it.point) }
        assertEquals(SouthAsia.Terrain.entries.toSet(), bands.keys, "a card held in every band")
        val edges = SouthAsia.Terrain.entries.associateWith { SouthAsia.pakistanBeyondCards(bands.getValue(it).first().point) }
        for ((band, edge) in edges) {
            assertNeverBefore(edge, pakistan.base, "$band against the method")
            assertTrue(edge.fixedPoint == null && edge.alongside.isEmpty() && edge.envelopePoints.isEmpty(), "$band: the place's own point")
            assertTrue(edge.endOfEatingMarginSeconds <= SAFE_END, "$band: ruling R44")
            for (card in pakistan.cards) {
                val on = card.on(pakistan.base)
                // Fajr, Dhuhr, Asr, Isha and the end of eating follow each city's width: every card's, whatever the band.
                assertTrue(edge.margins.fajr >= on.margins.fajr && edge.margins.dhuhr >= on.margins.dhuhr, "$band ${card.key}")
                assertTrue(edge.margins.asr >= on.margins.asr && edge.margins.isha >= on.margins.isha, "$band ${card.key}")
                assertTrue(edge.endOfEatingMarginSeconds <= on.endOfEatingMarginSeconds, "$band ${card.key}")
                // Sunrise and Maghrib follow the height of the ground: every card of the band and of the lower ones.
                if (SouthAsia.terrain(card.point) <= band) {
                    assertTrue(edge.margins.maghrib >= on.margins.maghrib && edge.margins.sunrise <= on.margins.sunrise, "$band ${card.key}")
                }
            }
        }
        val (plains, upland, mountain) = SouthAsia.Terrain.entries.map { edges.getValue(it) }
        assertNeverBefore(upland, plains, "the uplands against the plains")
        assertNeverBefore(mountain, upland, "the mountains against the uplands")
    }

    @Test
    fun `beyond the units the table's own card rides as far as the table's bound and the table alone in its ride`() {
        // Rulings R190 and R194. The card is printed for the whole city, and Karachi's eastern suburbs lie past its unit:
        // within the bound reach of the entry's table the card's own day rides beside the place's; past it, in the
        // table's ride, the table's day on the method alone, as before the cards; past the ride, the place alone.
        for (country in countries) {
            val units = assertNotNull(Units.of(country.entryId))
            val envelope = assertNotNull(units.envelope)
            val city = units.unit(country.table)
            val card = country.cards.single { it.key == country.table }
            val within = city.point.moved(0.0, (city.radiusKm + envelope.boundKm(city)) / 2)
            val riding = city.point.moved(0.0, (envelope.boundKm(city) + envelope.rideKm(city)) / 2)
            val past = city.point.moved(0.0, envelope.rideKm(city) + 5)
            for (p in listOf(within, riding, past)) {
                val r = country.at(p)
                assertNull(r.unitId, "$p")
                assertEquals(EntryClass.D_AUTHORITY, r.entryClass)
                assertEquals(false, r.measured, "no figure beyond the cards")
                val method = assertNotNull(r.method)
                assertNull(method.fixedPoint, "$p: the place's own point")
                assertNeverBefore(method, country.beyond(p), "$p")
                assertNeverBefore(method, country.base, "$p against the method")
                if (p === past) {
                    assertTrue(method.alongside.isEmpty(), "past the ride, the place alone")
                } else {
                    val table = method.alongside.single()
                    assertEquals(city.point, table.point)
                    val rides = if (p === within) card.on(country.base) else country.base
                    assertEquals(rides.margins, table.method.margins, if (p === within) "the city's own card" else "the table's own day, not the card's")
                    assertEquals(rides.endOfEatingMarginSeconds, table.method.endOfEatingMarginSeconds)
                }
                val alone = Registry.methodAlone(country.entryId, country.base, p, country.zone)
                for (school in AsrSchool.entries) for (date in dates) {
                    val shown = day(method, p, date, country.zone, school)
                    assertBounded(shown, day(alone, p, date, country.zone, school), "$p $date $school against the method alone")
                    if (p !== past) assertBounded(shown, day(country.base, city.point, date, country.zone, school), "$p $date $school against the table")
                    if (p === within) assertBounded(shown, day(card.on(country.base), city.point, date, country.zone, school), "$p $date $school against the card")
                }
            }
        }
    }

    @Test
    fun `an Indian place with no card held takes the latest margins the held cards ask`() {
        // Ruling R194 (the review of 13 Nov 2026): the method alone began a prayer before the city's own card at nine of
        // the ten Indian cities asked, so "exactly as before" beyond the card cities was before the card.
        val units = assertNotNull(Units.of(india.entryId))
        for (far in listOf(GeoPoint(26.84, 80.95), GeoPoint(21.15, 79.09), GeoPoint(34.08, 74.8), GeoPoint(9.93, 76.27))) {
            assertNull(units.unitFor(Place(far.lat, far.lon, india.zone, india.code)), "$far")
            val edge = units.outside(far)
            assertTrue(edge.fixedPoint == null && edge.alongside.isEmpty() && edge.envelopePoints.isEmpty(), "$far: the place's own point")
            assertEquals(SouthAsia.indiaBeyondCards(far).margins, edge.margins)
            assertNeverBefore(edge, india.base, "$far against the method")
            assertTrue(edge.endOfEatingMarginSeconds <= SAFE_END, "$far: ruling R44")
            for (card in india.cards) assertNeverBefore(edge, card.on(india.base), "$far against ${card.key}'s card")
        }
    }
}
