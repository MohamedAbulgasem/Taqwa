package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.Americas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The city-point round of 11 Oct 2026, under the owner's rule "rather late, never early": a place is never before any
 * table printed for its own point whose reach holds it, nor any end after one (the envelope), BIK's printed town figures,
 * the nearest Habous table's ends, Diyanet's Sydney table at every plausible point, and a named table followed wherever
 * it is chosen.
 */
class CityPointsEnvelopeTest {
    private val dates = (1..12).flatMap { m -> listOf(LocalDate(2026, m, 3), LocalDate(2026, m, 18)) }

    private fun at(entryId: String, lat: Double, lon: Double, zone: String, cc: String) =
        Registry.resolveEntry(Registry.byId(entryId)!!, Place(lat, lon, zone, cc))

    private fun day(method: TimetableMethod, point: GeoPoint, date: LocalDate, zone: String): PrayerDay =
        DayComputer.compute(method, point, date, TimeZone.of(zone), AsrSchool.STANDARD, Registry.ramadanCalendar())

    /** [shown] never before [table] and no end after it. */
    private fun assertBounded(shown: PrayerDay, table: PrayerDay, what: String) {
        assertTrue(shown.fajr >= table.fajr && shown.dhuhr >= table.dhuhr && shown.asr >= table.asr, "$what: a start before")
        assertTrue(shown.maghrib >= table.maghrib && shown.isha >= table.isha, "$what: a start before")
        assertTrue(shown.sunrise <= table.sunrise && shown.endOfEating <= table.endOfEating, "$what: an end after")
    }

    @Test
    fun `ottawa rides every mosque table whose reach holds it and is never before Abrar's or Kanata's`() {
        val zone = "America/Toronto"
        val ottawa = GeoPoint(45.41117, -75.69812)
        val r = at("ca.isna", ottawa.lat, ottawa.lon, zone, "CA")
        assertEquals("ottawa-south", r.unitId)
        val method = assertNotNull(r.method)
        val abrar = GeoPoint(45.3516, -75.7955)
        val kanata = GeoPoint(45.3561, -75.9284)
        assertTrue(abrar in method.envelopePoints && kanata in method.envelopePoints, "${method.envelopePoints}")
        for (table in listOf(abrar, kanata)) {
            val alone = at("ca.isna", table.lat, table.lon, zone, "CA").method!!.copy(envelopePoints = emptyList())
            for (date in dates) assertBounded(day(method, ottawa, date, zone), day(alone.copy(fixedPoint = null), table, date, zone), "$table $date")
        }
    }

    @Test
    fun `just beyond a mosque's minute the nearest table still rides and no figure is claimed`() {
        // North-east Calgary (Saddle Ridge), 19 km from Calgary Islamic Centre SW, beyond its minute's reach.
        val units = assertNotNull(Units.of("ca.isha90"))
        val saddleRidge = Place(51.13, -113.95, "America/Edmonton", "CA")
        assertNull(units.unitFor(saddleRidge))
        val edge = units.outside(GeoPoint(saddleRidge.lat, saddleRidge.lon))
        assertEquals(GeoPoint(51.0395, -114.1685), edge.fixedPoint)
        assertEquals(FixedPointMode.BOTH, edge.fixedPointMode)
        assertTrue(edge.endsBoundAtUserPoint)
        // Beyond every table's bound reach, the method alone, as before.
        assertNull(units.outside(GeoPoint(52.27, -113.81)).fixedPoint)
    }

    /** [shown] exactly the safe side of [own] and [table]: every start the later, every end the earlier, nothing more. */
    private fun assertSafeSide(shown: PrayerDay, own: PrayerDay, table: PrayerDay, what: String) {
        assertEquals(maxOf(own.fajr, table.fajr), shown.fajr, "$what: Fajr")
        assertEquals(maxOf(own.dhuhr, table.dhuhr), shown.dhuhr, "$what: Dhuhr")
        assertEquals(maxOf(own.asr, table.asr), shown.asr, "$what: Asr")
        assertEquals(maxOf(own.maghrib, table.maghrib), shown.maghrib, "$what: Maghrib")
        assertEquals(maxOf(own.isha, table.isha), shown.isha, "$what: Isha")
        assertEquals(minOf(own.sunrise, table.sunrise), shown.sunrise, "$what: sunrise")
        assertEquals(minOf(own.endOfEating, table.endOfEating), shown.endOfEating, "$what: end of eating")
    }

    @Test
    fun `past every bound reach the nearest table's own day rides out to five reaches with no figure`() {
        // The owner's decision of 17 Oct 2026: Winchester, Virginia, 69 km west of the ADAMS Center, is past every US
        // mosque's bound reach (65 km at ADAMS), where on main its times were back at its own point and its sunrise and end
        // of eating came after ADAMS's table. Now ADAMS's own day rides beside Winchester's, out to five of its reaches
        // (108 km): the day shown is the safe side of the two days, each at its own point.
        val zone = "America/New_York"
        val adams = GeoPoint(39.037, -77.383)
        val winchester = GeoPoint(39.18566, -78.16333)
        val units = assertNotNull(Units.of("us.isna"))
        val envelope = assertNotNull(units.envelope)
        assertTrue(envelope.holding(winchester).isEmpty())
        assertEquals("adams", envelope.nearestRiding(winchester)?.id)
        assertEquals(5 * units.unit("adams").radiusKm, envelope.rideKm(units.unit("adams")), 1e-9)
        val r = at("us.isna", winchester.lat, winchester.lon, zone, "US")
        assertNull(r.unitId)
        assertEquals(false, r.measured, "no figure past the bound, as before")
        val method = assertNotNull(r.method)
        assertTrue(method.fixedPoint == null && method.envelopePoints.isEmpty(), "the user's own point stays the method's alone")
        val table = method.alongside.single()
        assertEquals(adams, table.point)
        assertTrue(table.method.fixedPoint == null && table.method.envelopePoints.isEmpty(), "ADAMS's own day at its own point")
        val alone = Registry.methodAlone("us.isna", Registry.byId("us.isna")!!.method!!, winchester, zone)
        for (date in dates) {
            val shown = day(method, winchester, date, zone)
            val theirs = day(table.method, adams, date, zone)
            assertBounded(shown, theirs, "ADAMS on $date")
            // Never earlier than main, which showed the method at Winchester alone, and nothing later than the two need.
            val own = day(alone, winchester, date, zone)
            assertBounded(shown, own, "Winchester alone on $date")
            assertSafeSide(shown, own, theirs, "Winchester on $date")
        }
        // Harrisonburg, over 130 km from every US mosque's table, past every ride: the method alone, as before.
        val harrisonburg = at("us.isna", 38.44957, -78.86892, zone, "US").method!!
        assertTrue(harrisonburg.alongside.isEmpty() && harrisonburg.envelopePoints.isEmpty() && harrisonburg.fixedPoint == null)
    }

    @Test
    fun `a family read on a night fraction rides the table's day with its own latitude's curves`() {
        // The reviews of 17 Oct 2026: Camrose, Alberta, 64 km from Edmonton's Al Farooq, with the table's point read beside
        // the user's on curves taken at both latitudes, had its Isha up to 10 min later than both points alone. Now each day
        // keeps its own latitude's curves, and the member's day is exactly the safe side of the two.
        val zone = "America/Edmonton"
        val camrose = GeoPoint(53.01684, -112.83525)
        val member = Registry.resolve(Place(camrose.lat, camrose.lon, zone, "CA")).members.first { it.id == "ca.eighteen" }.method
        val table = member.alongside.single()
        assertEquals(GeoPoint(53.4675, -113.4302), table.point)
        val base = Registry.entries.flatMap { it.members }.first { it.id == "ca.eighteen" }.method
        val alone = Registry.methodAlone("ca.eighteen", base, camrose, zone)
        for (date in (1..12).flatMap { m -> (1..28 step 3).map { LocalDate(2026, m, it) } }) {
            val shown = day(member, camrose, date, zone)
            assertSafeSide(shown, day(alone, camrose, date, zone), day(table.method, table.point, date, zone), "Camrose on $date")
        }
    }

    @Test
    fun `manchester central's wifaqul table rides wifaqul chosen and keeps its bound under automatic`() {
        // Leeds, 58 km off, past the bound's 50 km: Wifaqul chosen there rides Manchester Central's own day (on main its Fajr
        // came 5 min before the table on all seven printed days); the UK's cautious member keeps its bound, the owner's
        // question 2 of 17 Oct 2026, accepted at the bound for now.
        val leeds = GeoPoint(53.79648, -1.54785)
        val manchester = GeoPoint(53.4631, -2.2285)
        assertEquals("manchester-central", assertNotNull(Units.of("gb.wifaqul")?.envelope).nearestRiding(leeds)?.id)
        val chosen = at("gb.wifaqul", leeds.lat, leeds.lon, "Europe/London", "GB").method!!
        assertTrue(chosen.envelopePoints.isEmpty() && chosen.fixedPoint == null)
        assertEquals(listOf(manchester), chosen.alongside.map { it.point })
        val automatic = Registry.resolve(Place(leeds.lat, leeds.lon, "Europe/London", "GB"))
        val member = automatic.members.first { it.id == "gb.wifaqul" }.method
        assertTrue(member.alongside.isEmpty() && member.envelopePoints.isEmpty() && member.fixedPoint == null)
        assertTrue(automatic.alongside.isEmpty())
    }

    @Test
    fun `rabita's calendar rides its own day from a minute's reach of helsinki out to five`() {
        fun rabita(lat: Double, lon: Double) =
            Registry.resolve(Place(lat, lon, "Europe/Helsinki", "FI")).members.first { it.id == "se.rabita" }.method
        val helsinki = GeoPoint(60.1699, 24.9384)
        val zone = "Europe/Helsinki"
        // Porvoo, 47 km east of Helsinki, past the region's 45 km: on main its Fajr came up to 3 min before the calendar.
        val porvoo = rabita(60.39233, 25.66507)
        val atHelsinki = porvoo.alongside.single()
        assertEquals(helsinki, atHelsinki.point)
        assertTrue(porvoo.fixedPoint == null && porvoo.envelopePoints.isEmpty())
        // Helsinki's own day, past Diyanet's Helsinki unit (41.5 km) its Asr a minute later: the member's own at Helsinki,
        // the region's margins included.
        val ownAtHelsinki = rabita(helsinki.lat, helsinki.lon)
        assertTrue(ownAtHelsinki.alongside.isEmpty(), "Helsinki itself rides nothing")
        assertEquals(ownAtHelsinki.margins.copy(asr = ownAtHelsinki.margins.asr + 60), atHelsinki.method.margins)
        assertEquals(ownAtHelsinki.endOfEatingMarginSeconds, atHelsinki.method.endOfEatingMarginSeconds)
        // Sipoo, 30 km out inside the region and Diyanet's unit, rides Helsinki's own day as it is (its own margins the
        // region's); Lauttasaari, 4 km out within a minute's reach, and Lahti, past the ride, do not.
        val sipoo = rabita(60.37643, 25.27156)
        assertEquals(listOf(helsinki), sipoo.alongside.map { it.point })
        assertEquals(ownAtHelsinki.margins, sipoo.margins)
        assertEquals(ownAtHelsinki.margins, sipoo.alongside.single().method.margins)
        assertTrue(rabita(60.15896, 24.86797).alongside.isEmpty())
        assertTrue(rabita(60.98267, 25.66151).alongside.isEmpty())
        assertEquals(false, Registry.resolve(Place(60.39233, 25.66507, zone, "FI")).measured)
        // 49 km west of Helsinki, 4 km north of Lohja (the reviews of 17 Oct 2026: the end of eating up to 4 min after the
        // calendar there, with Helsinki's point on the user's curves and the member's own margin): now never after
        // Helsinki's own day.
        val lohjaNorth = GeoPoint(60.2850, 24.0650)
        val there = rabita(lohjaNorth.lat, lohjaNorth.lon)
        for (date in (1..12).flatMap { m -> (2..28 step 2).map { LocalDate(2026, m, it) } }) {
            val shown = day(there, lohjaNorth, date, zone)
            val theirs = day(ownAtHelsinki, helsinki, date, zone)
            assertTrue(shown.endOfEating <= theirs.endOfEating && shown.sunrise <= theirs.sunrise, "north of Lohja on $date")
            assertTrue(shown.fajr >= theirs.fajr && shown.isha >= theirs.isha && shown.dhuhr >= theirs.dhuhr, "north of Lohja on $date")
            assertTrue(shown.maghrib >= theirs.maghrib, "north of Lohja on $date: Maghrib before Helsinki's own day")
        }
    }

    @Test
    fun `canada's cautious times ride the isna mosques' own days outside montreal and ottawa`() {
        // The owner's decision of 17 Oct 2026, its own example: Drummondville, 87 km from CCML (Longueuil), past ISNA's
        // bound and outside Montréal's box, where Automatic is Canada's cautious times. On main its Fajr came up to 5 min
        // before CCML's table on 341 days of 2026. Now CCML's own day rides beside the cautious day.
        val zone = "America/Toronto"
        val drummondville = Place(45.88336, -72.48241, zone, "CA")
        val r = Registry.resolve(drummondville)
        assertEquals("ca.cautious", r.entry.id)
        val ccml = GeoPoint(45.5314, -73.4869)
        assertEquals(listOf(ccml), r.alongside.map { it.point })
        assertEquals(false, r.measured, "no figure, as before")
        val theirs = r.alongside.single()
        for (date in dates) {
            val shown = world.taqwa.app.prayer.engine.DayPipeline.unended(r, date, TimeZone.of(zone), AsrSchool.STANDARD)
            assertBounded(shown, day(theirs.method, ccml, date, zone), "CCML on $date")
            val alone = world.taqwa.app.prayer.engine.DayPipeline.unended(r.copy(alongside = emptyList()), date, TimeZone.of(zone), AsrSchool.STANDARD)
            assertBounded(shown, alone, "the cautious day alone on $date")
        }
        // Inside Montréal's box Automatic is ISNA itself, and in Toronto and on the Prairies nothing of ISNA's rides.
        assertTrue(Registry.resolve(Place(45.5017, -73.5673, zone, "CA")).alongside.isEmpty())
        assertTrue(Registry.resolve(Place(51.04473, -114.07188, "America/Edmonton", "CA")).alongside.isEmpty())
    }

    @Test
    fun `kosovo carries BIK's own town figures and the Takvim rides everywhere in Kosovo`() {
        val zone = "Europe/Belgrade"
        val dragash = at("xk.bik", 42.0265, 20.65333, zone, "XK").method!!
        assertEquals(listOf(GeoPoint(42.5, 21.0 - 2.04 * 0.25)), dragash.startPoints)
        val prishtina = at("xk.bik", 42.67272, 21.16688, zone, "XK").method!!
        assertEquals(listOf(GeoPoint(42.5, 21.0 + 1.04 * 0.25)), prishtina.endPoints)
        assertTrue(prishtina.startPoints.isEmpty())
        val peja = at("xk.bik", 42.65914, 20.28828, zone, "XK").method!!
        assertTrue(peja.startPoints.isEmpty() && peja.endPoints.isEmpty())
        // Leposaviq, beyond the base unit: the base still rides, with no figure.
        val leposaviq = at("xk.bik", 43.10389, 20.80278, zone, "XK")
        assertNull(leposaviq.unitId)
        assertEquals(GeoPoint(42.5, 21.0), leposaviq.method!!.fixedPoint)
        // Sharri's table is the Takvim plus two minutes: never before it at Dragash.
        val base = at("xk.bik", 42.5, 21.0, zone, "XK").method!!.copy(startPoints = emptyList(), endPoints = emptyList())
        for (date in dates) {
            val shown = day(dragash, GeoPoint(42.0265, 20.65333), date, zone)
            val takvim = day(base, GeoPoint(42.5, 21.0), date, zone)
            assertTrue(shown.fajr >= takvim.fajr.plus(2.minutes), "$date fajr")
            assertTrue(shown.isha >= takvim.isha.plus(2.minutes), "$date isha")
        }
    }

    @Test
    fun `sidi taibi in Rabat's unit ends no later than Kenitra's table too`() {
        val r = at("ma.habous", 34.18698, -6.68176, "Africa/Casablanca", "MA")
        assertEquals("rabat", r.unitId)
        assertEquals(listOf(GeoPoint(34.26101, -6.5802)), r.method!!.endPoints)
        assertTrue(r.method!!.startPoints.isEmpty())
    }

    @Test
    fun `a named table chosen beyond its area rides its point for every start and end`() {
        val basildon = at("gb.london.lupt", 51.56844, 0.45782, "Europe/London", "GB")
        assertNull(basildon.unitName)
        assertEquals(FixedPointMode.BOTH, basildon.method!!.fixedPointMode)
        assertNotNull(basildon.method!!.fixedPoint)
        val sharjah = at("ae.iacad.dubai", 25.3463, 55.4209, "Asia/Dubai", "AE")
        assertNull(sharjah.unitName)
        assertEquals(GeoPoint(25.07725, 55.30927), sharjah.method!!.fixedPoint)
        assertEquals(FixedPointMode.BOTH, sharjah.method!!.fixedPointMode)
        // Fujairah follows Hatta's table, the nearest, and is never before Dubai's main table either (round 2's review).
        val fujairah = at("ae.iacad.dubai", 25.11641, 56.34141, "Asia/Dubai", "AE")
        assertEquals(GeoPoint(24.80073, 56.12726), fujairah.method!!.fixedPoint)
        assertTrue(GeoPoint(25.07725, 55.30927) in fujairah.method!!.startPoints && GeoPoint(25.07725, 55.30927) in fujairah.method!!.endPoints)
        assertTrue(sharjah.method!!.startPoints.isEmpty())
    }

    @Test
    fun `rabita's calendar is held across the helsinki region`() {
        // Round 2's review: Sipoo's Isha a minute before Rabita's calendar on 84 days, Espoonlahti's end of eating up to
        // 2 min after it on 31. Within 45 km of Helsinki the member's Isha a minute later and its end of eating 2 earlier.
        fun rabita(lat: Double, lon: Double) =
            Registry.resolve(Place(lat, lon, "Europe/Helsinki", "FI")).members.first { it.id == "se.rabita" }.method
        val sipoo = rabita(60.37643, 25.27156)
        val turku = rabita(60.4518, 22.2666)
        assertEquals(turku.margins.isha + 60, sipoo.margins.isha)
        assertEquals(turku.endOfEatingMarginSeconds - 120, sipoo.endOfEatingMarginSeconds)
    }

    @Test
    fun `diyanet's sydney table rides every plausible point of its near Sydney only`() {
        val fairfield = Registry.resolve(Place(-33.8723, 150.9561, "Australia/Sydney", "AU"))
        val turkey = fairfield.members.first { it.id == "other.turkey" }.method
        assertNotNull(turkey.fixedPoint)
        assertEquals(2, turkey.envelopePoints.size)
        val melbourne = Registry.resolve(Place(-37.8136, 144.9631, "Australia/Melbourne", "AU"))
        assertNull(melbourne.members.first { it.id == "other.turkey" }.method.fixedPoint)
    }

    @Test
    fun `diyanet's preset chosen around sydney is never before its sydney table at any plausible point`() {
        // The owner's decision of 17 Oct 2026 (question 4): someone in Sydney who picks Diyanet means the Turkish mosques'
        // timetable. Its point is not published, so the preset chosen on its own, anywhere within class D's reach of
        // Lakemba, Sydney's centre or Auburn, is never before the preset at any of the three, nor any end after it: west to
        // Glenmore Park, south to Wollongong and Cronulla, north to the Central Coast and Hornsby, east to Manly.
        val zone = "Australia/Sydney"
        val alone = Registry.byId("other.turkey")!!.method!!
        val places = listOf(
            GeoPoint(-33.86667, 150.95), GeoPoint(-33.91938, 150.92588), GeoPoint(-33.76667, 150.91667),
            GeoPoint(-33.8178, 151.00348), GeoPoint(-33.79068, 150.6693), GeoPoint(-34.424, 150.89345),
            GeoPoint(-34.06251, 151.14961), GeoPoint(-33.42979, 151.37144), GeoPoint(-33.70244, 151.09931),
            GeoPoint(-33.79798, 151.28826), GeoPoint(-33.86785, 151.20732),
        )
        for (user in places) {
            val chosen = at("other.turkey", user.lat, user.lon, zone, "AU")
            val method = assertNotNull(chosen.method)
            assertNotNull(method.fixedPoint, "$user")
            for (table in Americas.sydneyDiyanetPoints.map { it.point }) {
                for (date in dates) assertBounded(day(method, user, date, zone), day(alone, table, date, zone), "$user against $table on $date")
            }
        }
    }
}
