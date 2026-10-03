package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Levant
import world.taqwa.app.prayer.engine.registry.data.AlgeriaWilayas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The registry against the app's own city list (`cities.csv`): every country and every city resolves
 * to an entry, and the region shapes in [Regions] agree with the admin-1 region GeoNames gives each
 * city. Lives in `androidHostTest` because it reads the bundled resource from the file system, like
 * [world.taqwa.app.city.CityNamesBundleTest]; the registry itself never reads it.
 */
class RegistryCitiesTest {

    private class City(val name: String, val region: String, val countryCode: String, val lat: Double, val lon: Double, val zone: String) {
        val place get() = Place(lat, lon, zone, countryCode)
    }

    private val cities: List<City> by lazy {
        val moduleRelative = File("src/commonMain/composeResources/files/cities.csv")
        val file = if (moduleRelative.exists()) moduleRelative else File("shared/src/commonMain/composeResources/files/cities.csv")
        file.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val f = line.split(",")
            City(f[1], f[2], f[4], f[5].toDouble(), f[6].toDouble(), f[7])
        }
    }

    private fun citiesIn(country: String, vararg regions: String) =
        cities.filter { it.countryCode == country && it.region in regions }

    @Test
    fun `every country code in the city list resolves to a registry entry`() {
        val codes = cities.map { it.countryCode }.distinct()
        assertTrue(codes.size > 200, "only ${codes.size} codes read")
        for (code in codes) {
            val sample = cities.first { it.countryCode == code }
            val r = Registry.resolve(sample.place)
            assertEquals(r.entry, Registry.byId(r.entry.id), code)
        }
    }

    @Test
    fun `every algerian and bruneian city's region names its wilaya or district`() {
        val algerian = cities.filter { it.countryCode == "DZ" }
        assertTrue(algerian.size > 250, "only ${algerian.size} Algerian cities read")
        for (city in algerian) {
            val seat = assertNotNull(AlgeriaWilayas.seatFor(city.region), "${city.name}: ${city.region}")
            assertEquals(seat.name, Registry.resolve(city.place.copy(admin1 = city.region)).unitName, city.name)
        }
        for (city in cities.filter { it.countryCode == "BN" }) {
            val district = assertNotNull(Registry.resolve(city.place.copy(admin1 = city.region)).unitName, city.name)
            assertTrue(regionKey(city.region).startsWith(regionKey(district)), "${city.name}: ${city.region} is not $district")
        }
    }

    @Test
    fun `every city resolves and a single method or members come with it`() {
        for (city in cities) {
            val r = Registry.resolve(city.place)
            if (r.entryClass == EntryClass.C) {
                assertTrue(r.entry.members.isNotEmpty(), city.name)
            } else {
                assertNotNull(r.method, "${city.name} ${city.countryCode}")
            }
        }
    }

    /**
     * Where each entry is tried: the most populous city Automatic resolves to it, else a city of its
     * countries, else (a global method) Makkah, London and Jakarta.
     */
    private fun examplesOf(entry: RegistryEntry): List<City> {
        val automatic = cities.firstOrNull { Registry.automaticEntry(it.place).id == entry.id }
        if (automatic != null) return listOf(automatic)
        val inCountry = cities.firstOrNull { it.countryCode in entry.countries }
        if (inCountry != null) return listOf(inCountry)
        return listOf("Makkah" to "SA", "London" to "GB", "Jakarta" to "ID").map { (name, cc) ->
            cities.first { it.name == name && it.countryCode == cc }
        }
    }

    @Test
    fun `every method computes ordered whole-minute days at its own example place`() {
        val dates = listOf(LocalDate(2026, 3, 20), LocalDate(2026, 6, 21), LocalDate(2026, 12, 21), LocalDate(2028, 2, 29))
        for (entry in Registry.entries) {
            for (city in examplesOf(entry)) {
                val r = Registry.resolveEntry(entry, city.place)
                val methods = listOfNotNull(r.method) + r.members.map { it.method }
                assertTrue(methods.isNotEmpty(), entry.id)
                val zone = TimeZone.of(city.zone)
                for (method in methods) for (date in dates) {
                    val label = "${entry.id}/${method.id} at ${city.name} on $date"
                    val day = DayComputer.compute(method, r.point, date, zone, entry.school, Registry.ramadanCalendarFor(entry))
                    val order = listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)
                    assertEquals(order.sorted(), order, label)
                    assertTrue(day.endOfEating <= day.fajr, label)
                    day.imsak?.let { assertTrue(it <= day.fajr, label) }
                    for (instant in order + day.endOfEating) assertEquals(0L, instant.epochSeconds % 60, label)
                }
            }
        }
    }

    /** All cities GeoNames puts in [regions] resolve to [entryId], and no other city of [country] does. */
    private fun assertRegion(entryId: String, country: String, vararg regions: String, allowedOutside: Set<String> = emptySet()) {
        val inside = citiesIn(country, *regions)
        assertTrue(inside.isNotEmpty(), "no cities in $regions")
        for (city in inside) assertEquals(entryId, Registry.resolve(city.place).entry.id, "${city.name} (${city.region})")
        val strays = cities.filter { it.countryCode == country && it.region !in regions }
            .filter { Registry.resolve(it.place).entry.id == entryId }
            .map { "${it.name} (${it.region})" }
            .filterNot { it in allowedOutside }
        assertTrue(strays.isEmpty(), "$entryId outside its regions: $strays")
    }

    @Test
    fun `tatarstan dagestan and gaza follow their own authorities`() {
        assertRegion(
            "ru.dumrt", "RU", "Tatarstan Republic",
            allowedOutside = setOf(
                "Oktyabrsky (Bashkortostan Republic)", "Tuymazy (Bashkortostan Republic)", "Agidel’ (Bashkortostan Republic)",
                "Volzhsk (Mariy-El Republic)", "Vyatskiye Polyany (Kirov Oblast)",
            ),
        )
        assertRegion("ru.dumrd", "RU", "Dagestan")
        assertRegion("ps.gaza.cautious", "PS", "Gaza Strip")
    }

    @Test
    fun `dubai's zones follow iacad and the other emirates follow awqaf`() {
        assertRegion("ae.iacad.dubai", "AE", "Dubai")
        for (city in cities.filter { it.countryCode == "AE" && it.region != "Dubai" }) {
            assertEquals("ae.awqaf", Registry.resolve(city.place).entry.id, city.name)
        }
    }

    @Test
    fun `iran's sunni provinces carry their own school and every other town the later asr`() {
        val hanafi = setOf("Sistan and Baluchestan", "Golestan", "Razavi Khorasan", "North Khorasan", "South Khorasan Province")
        val shafii = setOf("Kurdistan Province", "Hormozgan")
        val iranian = cities.filter { it.countryCode == "IR" }
        assertTrue(iranian.size > 100, "only ${iranian.size} Iranian cities read")
        assertTrue(iranian.map { it.region }.toSet().containsAll(hanafi + shafii))
        for (city in iranian) {
            val r = Registry.resolve(city.place.copy(admin1 = city.region))
            val expected = when (city.region) {
                in hanafi -> "ir.hanafi"
                in shafii -> "ir.shafii"
                else -> "ir.default" // Asadabad, Sonqor, Kahnuj, Manujan, Takab, Larestan: not known, the later
            }
            assertEquals(expected, r.entry.id, "${city.name} (${city.region})")
            assertTrue(r.shiaRegion, city.name)
            // With no region known, no province is assumed.
            assertEquals("ir.default", Registry.resolve(city.place).entry.id, city.name)
        }
    }

    @Test
    fun `the shia regions list covers its places`() {
        for (city in citiesIn("PK", "Gilgit-Baltistan") + citiesIn("AF", "Bamyan", "Daykundi")) {
            assertTrue(Registry.resolve(city.place).shiaRegion, city.name)
        }
        for (city in citiesIn("YE", "Şa‘dah", "Amanat Alasimah", "Omran", "Ḩajjah")) {
            assertTrue(Registry.resolve(city.place).shiaRegion, city.name)
        }
        for (city in citiesIn("YE", "Aden", "Muhafazat Hadramaout", "Ta‘izz")) {
            assertTrue(!Registry.resolve(city.place).shiaRegion, city.name)
        }
        for (code in listOf("IQ", "BH", "AZ", "LB", "KW", "IR")) {
            for (city in cities.filter { it.countryCode == code }) assertTrue(Registry.resolve(city.place).shiaRegion, city.name)
        }
        for (city in cities.filter { it.countryCode == "SA" }) {
            assertEquals(city.lon > 48.0, Registry.resolve(city.place).shiaRegion, city.name)
        }
        for (code in listOf("TR", "EG", "MA", "JO", "SY", "MY", "ID", "GB", "US")) {
            for (city in cities.filter { it.countryCode == code }) assertTrue(!Registry.resolve(city.place).shiaRegion, city.name)
        }
    }

    @Test
    fun `every scottish city takes glasgow's asr and no other uk city does`() {
        val british = cities.filter { it.countryCode == "GB" }
        assertTrue(british.count { it.region == "Scotland" } > 50, "too few Scottish cities read")
        for (city in british) {
            val r = Registry.resolve(city.place)
            if (r.entry.id != "gb.cautious") continue // London
            val fifteen = r.members.single { it.id == "gb.fifteen" }.method
            assertEquals(city.region == "Scotland", fifteen.monthlyOffsets != null, "${city.name} (${city.region})")
        }
    }

    @Test
    fun `every algerian city is computed at a wilaya seat`() {
        for (city in cities.filter { it.countryCode == "DZ" }) {
            val r = Registry.resolve(city.place)
            assertEquals("dz.marw", r.entry.id, city.name)
            assertNotNull(r.unitName, city.name)
        }
    }

    @Test
    fun `every kemenag unit is reached through the app's engine at its own point and by its city entry`() {
        // Ruling R113: the app computes every place at three decimals (PrayerEngine.canonical), which moves a point
        // by up to about 0.08 km, and a unit's radius is never below 0.1 km; so each unit's own point, and the app's
        // city entry at that point (the 18 units' points are the app's own GeoNames points), resolve to the unit
        // the way the app resolves them.
        val units = Units.of("id.kemenag")!!.units
        assertEquals(18, units.size)
        val date = LocalDate(2026, 3, 20)
        for (unit in units) {
            val zone = when {
                unit.point.lon > 130.0 -> "Asia/Jayapura"
                unit.point.lon > 114.0 -> "Asia/Makassar"
                else -> "Asia/Jakarta"
            }
            val ownPoint = PrayerEngine.dayTimes(Place(unit.point.lat, unit.point.lon, zone, "ID"), date, EngineSettings())
            assertEquals(unit.name, ownPoint.resolution.unitName, "${unit.id} at its own point through the app's engine")
            val entries = cities.filter { it.countryCode == "ID" && distanceKm(GeoPoint(it.lat, it.lon), unit.point) < 0.1 }
            assertTrue(entries.isNotEmpty(), "${unit.id} ${unit.name}: no city entry at its point")
            for (city in entries) {
                val viaApp = PrayerEngine.dayTimes(city.place, date, EngineSettings())
                assertEquals(unit.name, viaApp.resolution.unitName, "${city.name} (${city.region}) through the app's engine")
                assertTrue(viaApp.resolution.measured, city.name)
            }
        }
    }

    @Test
    fun `libya's east and south stop eating at the nineteen and a half degree dawn`() {
        for (city in cities.filter { it.countryCode == "LY" }) {
            val r = Registry.resolve(city.place)
            val eastOrSouth = city.lon > 18.5 || city.lat < 29.5
            val expected = if (eastOrSouth) EndOfEating.DawnAngle(19.5) else EndOfEating.SameAsFajrDawn
            assertEquals(expected, r.method!!.endOfEating, city.name)
        }
    }

    @Test
    fun `every palestinian city follows its own printed town's table through the app's engine (ruling R118)`() {
        // The PA prints one offset for each town after al-Aqsa's table; a place takes its own town's table, or the
        // nearest printed town's, never a neighbour's (Tulkarm's + 1 is not Nablus's + 0, Rafah's + 4 not Gaza's + 3).
        val own = mapOf(
            "East Jerusalem" to "Jerusalem", "Old City" to "Jerusalem", "Ramallah" to "Ramallah", "Bethlehem" to "Bethlehem",
            "Janīn" to "Jenin", "Nablus" to "Nablus", "Jericho" to "Jericho", "Hebron" to "Hebron", "Idhnā" to "Idhna",
            "Dūrā" to "Dura", "Ţūlkarm" to "Tulkarm", "Qalqīlyah" to "Qalqilya", "Gaza" to "Gaza", "Rafaḩ" to "Rafah",
            "Khān Yūnis" to "Khan Yunis", "Dayr al Balaḩ" to "Deir al-Balah",
        )
        val palestine = cities.filter { it.countryCode == "PS" }
        assertTrue(palestine.size >= 40, "only ${palestine.size} Palestinian cities read")
        assertTrue(palestine.map { it.name }.containsAll(own.keys), "a printed town is missing from the city list")
        val chosen = EngineSettings(timetable = TimetableChoice.Entry("ps.iftaa"), timetableConfirmed = true)
        val date = LocalDate(2026, 10, 3)
        for (city in palestine) {
            // At the point the app computes, three decimals (ruling R113): Al Qararah lies 5 km from both Khan Yunis and
            // Deir al-Balah (both + 4).
            fun e3(degrees: Double) = kotlin.math.round(degrees * 1000.0) / 1000.0
            val nearest = Levant.paTowns.minBy { distanceKm(GeoPoint(e3(city.lat), e3(city.lon)), it.point) }.name
            own[city.name]?.let { assertEquals(it, nearest, "${city.name} is its own printed town") }
            val viaApp = PrayerEngine.dayTimes(city.place, date, chosen)
            assertEquals("ps.iftaa", viaApp.effectiveEntry.id, city.name)
            assertEquals(nearest, viaApp.effective.unitName, "${city.name} with the PA's table chosen")
            assertTrue(viaApp.effective.measured, city.name)
            val automatic = PrayerEngine.dayTimes(city.place, date, EngineSettings()).resolution
            if (city.region == "Gaza Strip") {
                assertEquals("ps.gaza.cautious", automatic.entry.id, city.name)
            } else {
                assertEquals("ps.iftaa", automatic.entry.id, city.name)
                assertEquals(nearest, automatic.unitName, "${city.name} on Automatic")
            }
        }
    }
}
