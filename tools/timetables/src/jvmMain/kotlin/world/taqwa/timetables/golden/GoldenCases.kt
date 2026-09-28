package world.taqwa.timetables.golden

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.SchoolChoice
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brief D (rulings R84, R85; spec §3.2): about 2,000 invented place-days for the golden vector.
 *
 * A [Seed] is one invented point: a plausible location (never a table's own reference point — every
 * seed is nudged by [jitter], at least 5 km, ruling R69) with the country code and IANA zone Automatic
 * needs to resolve it, and (for Iran) the admin-1 name the Hanafi/Shafi'i region rule reads. [polar]
 * marks a high-latitude or far-southern point, which draws its dates from the wider [POLAR_DATES]
 * pool too, so the transition weeks and the two solstices are well covered there.
 *
 * Regenerate with `./gradlew -p tools/timetables generateGoldenVector` after any change here or to
 * the engine or registry (KDoc on [GenerateGoldenVectorKt]).
 */
data class Seed(
    val label: String,
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val countryCode: String,
    val admin1: String? = null,
    val polar: Boolean = false,
) {
    fun place(): Place = Place(lat, lon, zoneId, countryCode, admin1)
}

/**
 * One EngineSettings variant a case is computed under (spec §3.2's "EngineSettings paths"): most
 * cases are [Automatic]; a small share force the Hanafi Asr, the Saudi "5 minutes later" Fajr, an
 * explicit timetable (an Other method, global everywhere, or a named entry only in scope at its own
 * seed), or a positive per-prayer adjustment (never negative: a negative one only applies once
 * confirmed under the resolved entry, which the generator does not know in advance — see the report).
 */
sealed interface SettingsCase {
    data object Automatic : SettingsCase
    data object HanafiSchool : SettingsCase
    data object SaudiLater : SettingsCase
    data class ChosenEntry(val id: String) : SettingsCase
    data class Adjustment(val prayer: Prayer, val minutes: Int) : SettingsCase

    fun toEngineSettings(): EngineSettings = when (this) {
        Automatic -> EngineSettings()
        HanafiSchool -> EngineSettings(school = SchoolChoice.Hanafi)
        SaudiLater -> EngineSettings(saudiFajrLater = true)
        is ChosenEntry -> EngineSettings(timetable = TimetableChoice.Entry(id), timetableConfirmed = true)
        is Adjustment -> EngineSettings(adjustmentsMinutes = mapOf(prayer to minutes))
    }

    /** The token [GoldenVectorTest][world.taqwa.app.prayer.engine.golden] decodes back to these settings. */
    fun toToken(): String = when (this) {
        Automatic -> "AUTO"
        HanafiSchool -> "HANAFI"
        SaudiLater -> "SAUDI"
        is ChosenEntry -> "CHOSEN:$id"
        is Adjustment -> "ADJ:${prayer.name}:$minutes"
    }
}

/** One place-day to compute and commit: [seed]'s place, [date] and [settings]. */
data class GoldenCase(val seed: Seed, val date: LocalDate, val settings: SettingsCase)

/**
 * Nudges a hand-picked coordinate 8-13 km away, in a direction that spreads out over successive
 * seeds (the golden-angle step, ~137.5°) rather than always the same bearing, so an invented point
 * never lands on, or nearly on, a table's own reference point (ruling R69). A fixed formula: the same
 * [index] always gives the same offset, no randomness.
 */
private fun jitter(lat: Double, lon: Double, index: Int): Pair<Double, Double> {
    val angleDeg = (index * 137.508) % 360.0
    val km = 8.0 + (index % 7) * 0.8 // 8.0 .. 12.8 km
    val rad = Math.toRadians(angleDeg)
    val kmPerDegLat = 111.32
    val cosLat = cos(Math.toRadians(lat)).let { if (it < 0.05) 0.05 else it }
    val dLat = (km / kmPerDegLat) * cos(rad)
    val dLon = (km / (kmPerDegLat * cosLat)) * sin(rad)
    return (lat + dLat) to (lon + dLon)
}

private var seedIndex = 0

/** One invented seed point, nudged by [jitter] before it is kept. */
private fun s(
    label: String,
    lat: Double,
    lon: Double,
    zoneId: String,
    countryCode: String,
    admin1: String? = null,
    polar: Boolean = false,
): Seed {
    val (jLat, jLon) = jitter(lat, lon, seedIndex++)
    return Seed(label, jLat, jLon, zoneId, countryCode, admin1, polar)
}

/**
 * Invented points across the countries and named regions the registry resolves by (Regions.kt,
 * Registry.countryEntries): weighted towards where most Muslims live (the Gulf, the Levant, North and
 * sub-Saharan Africa, Turkey, South and South-east Asia) with enough of the smaller cautious-Europe,
 * Americas and Oceania entries, the region overrides (London's M25, Chicago, Toronto, Montreal,
 * Ottawa, Cape Town, Gaza, Dubai, Tatarstan, Dagestan, Bashkortostan, Sandžak, Preševo, Iran's
 * Hanafi/Shafi'i provinces) and Arctic and far-southern points, so that every registry entry and
 * every class resolves at least once (checked by [GoldenCasesTest]).
 */
val SEEDS: List<Seed> = listOf(
    // Arabia and the Gulf.
    s("Riyadh", 24.7136, 46.6753, "Asia/Riyadh", "SA"),
    s("Abu Dhabi", 24.4539, 54.3773, "Asia/Dubai", "AE"),
    s("Dubai Marina", 25.0805, 55.1403, "Asia/Dubai", "AE"), // inside Regions.dubai
    s("Doha", 25.2854, 51.5310, "Asia/Qatar", "QA"),
    s("Kuwait City", 29.3759, 47.9774, "Asia/Kuwait", "KW"),
    s("Manama", 26.2285, 50.5860, "Asia/Bahrain", "BH"),
    s("Muscat", 23.5859, 58.4059, "Asia/Muscat", "OM"),

    // The Levant and Iraq.
    s("Amman", 31.9454, 35.9284, "Asia/Amman", "JO"),
    s("Irbid", 32.5556, 35.8500, "Asia/Amman", "JO"),
    s("Ramallah", 31.9038, 35.2034, "Asia/Hebron", "PS"),
    s("Gaza City", 31.5000, 34.4700, "Asia/Gaza", "PS"), // inside Regions.gaza
    s("Beirut", 33.8938, 35.5018, "Asia/Beirut", "LB"),
    s("Damascus", 33.5138, 36.2765, "Asia/Damascus", "SY"),
    s("Baghdad", 33.3152, 44.3661, "Asia/Baghdad", "IQ"),
    s("Sanaa", 15.3694, 44.1910, "Asia/Aden", "YE"),

    // North Africa.
    s("Tripoli", 32.8872, 13.1913, "Africa/Tripoli", "LY"),
    s("Benghazi", 32.1167, 20.0667, "Africa/Tripoli", "LY"), // Regions.libyaEastSouth
    s("Sabha", 27.0377, 14.4283, "Africa/Tripoli", "LY"), // libyaEastSouth (south)
    s("Tunis", 36.8065, 10.1815, "Africa/Tunis", "TN"),
    s("Algiers", 36.7538, 3.0588, "Africa/Algiers", "DZ"),
    s("Oran", 35.6969, -0.6331, "Africa/Algiers", "DZ"),
    s("Rabat", 34.0209, -6.8416, "Africa/Casablanca", "MA"),
    s("Casablanca", 33.5731, -7.5898, "Africa/Casablanca", "MA"),
    s("Marrakech", 31.6295, -7.9811, "Africa/Casablanca", "MA"),
    s("Nouakchott", 18.0735, -15.9582, "Africa/Nouakchott", "MR"),
    s("Khartoum", 15.5007, 32.5599, "Africa/Khartoum", "SD"),
    s("Cairo", 30.0444, 31.2357, "Africa/Cairo", "EG"),
    s("Aswan", 24.0889, 32.8998, "Africa/Cairo", "EG"),

    // Sub-Saharan Africa (default.safe.africa unless noted).
    s("Lagos", 6.5244, 3.3792, "Africa/Lagos", "NG"),
    s("Nairobi", -1.2921, 36.8219, "Africa/Nairobi", "KE"),
    s("Dar es Salaam", -6.7924, 39.2083, "Africa/Dar_es_Salaam", "TZ"),
    s("Addis Ababa", 9.0300, 38.7400, "Africa/Addis_Ababa", "ET"),
    s("Dakar", 14.7167, -17.4677, "Africa/Dakar", "SN"),
    s("Johannesburg", -26.2041, 28.0473, "Africa/Johannesburg", "ZA"), // za.jamiat
    s("Durban", -29.8587, 31.0218, "Africa/Johannesburg", "ZA"), // za.jamiat
    s("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg", "ZA"), // Regions.capeTown -> za.cape

    // Turkey, the Balkans, Russia and Central Asia.
    s("Ankara", 39.9334, 32.8597, "Europe/Istanbul", "TR"),
    s("Istanbul", 41.0082, 28.9784, "Europe/Istanbul", "TR"),
    s("Izmir", 38.4237, 27.1428, "Europe/Istanbul", "TR"),
    s("Van", 38.4891, 43.4089, "Europe/Istanbul", "TR"),
    s("Sarajevo", 43.8563, 18.4131, "Europe/Sarajevo", "BA"),
    s("Mostar", 43.3438, 17.8078, "Europe/Sarajevo", "BA"),
    s("Pristina", 42.6629, 21.1655, "Europe/Belgrade", "XK"),
    s("Tirana", 41.3275, 19.8187, "Europe/Tirane", "AL"),
    s("Podgorica", 42.4304, 19.2594, "Europe/Podgorica", "ME"),
    s("Novi Pazar (Sandzak)", 43.1367, 20.5122, "Europe/Belgrade", "RS"), // Regions.sandzak -> ba.iz
    s("Presevo valley", 42.6500, 21.6300, "Europe/Belgrade", "RS"), // Regions.presevo -> xk.bik
    s("Belgrade", 44.7866, 20.4489, "Europe/Belgrade", "RS"), // default.safe.balkans
    s("Moscow", 55.7558, 37.6173, "Europe/Moscow", "RU"), // ru.dumrf
    s("Kazan area (Tatarstan)", 55.90, 49.30, "Europe/Moscow", "RU"), // Regions.tatarstan -> ru.dumrt
    s("Makhachkala (Dagestan)", 42.9849, 47.5047, "Europe/Moscow", "RU"), // Regions.dagestan -> ru.dumrd
    s("Ufa (Bashkortostan)", 54.7388, 55.9721, "Asia/Yekaterinburg", "RU"), // Regions.bashkortostan
    s("Almaty", 43.2220, 76.8512, "Asia/Almaty", "KZ"),
    s("Astana (northern Kazakhstan)", 51.1694, 71.4491, "Asia/Almaty", "KZ"), // Regions.kazakhNorth
    s("Tashkent", 41.2995, 69.2401, "Asia/Tashkent", "UZ"),
    s("Bishkek", 42.8746, 74.5698, "Asia/Bishkek", "KG"),
    s("Dushanbe", 38.5598, 68.7870, "Asia/Dushanbe", "TJ"),
    s("Ashgabat", 37.9601, 58.3261, "Asia/Ashgabat", "TM"),
    s("Baku", 40.4093, 49.8671, "Asia/Baku", "AZ"), // not in the registry: default.safe
    s("Tehran", 35.6892, 51.3890, "Asia/Tehran", "IR", admin1 = "Tehran"),
    s("Mashhad (Iran Hanafi)", 36.2605, 59.6168, "Asia/Tehran", "IR", admin1 = "Razavi Khorasan"),
    s("Sanandaj (Iran Shafii)", 35.3219, 46.9862, "Asia/Tehran", "IR", admin1 = "Kurdistan"),

    // South, Central and South-east Asia.
    s("Singapore", 1.3521, 103.8198, "Asia/Singapore", "SG"),
    s("Bandar Seri Begawan", 4.9031, 114.9398, "Asia/Brunei", "BN"),
    s("Kuala Lumpur", 3.1390, 101.6869, "Asia/Kuala_Lumpur", "MY"),
    s("Kuching (Sarawak)", 1.5533, 110.3592, "Asia/Kuching", "MY"),
    s("Kota Kinabalu (Sabah)", 5.9804, 116.0735, "Asia/Kuching", "MY"),
    s("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", "ID"),
    s("Banda Aceh", 5.5483, 95.3238, "Asia/Jakarta", "ID"),
    s("Jayapura (Papua)", -2.5330, 140.7181, "Asia/Jayapura", "ID"),
    s("Karachi", 24.8607, 67.0011, "Asia/Karachi", "PK"),
    s("Delhi", 28.6139, 77.2090, "Asia/Kolkata", "IN"),
    s("Dhaka", 23.8103, 90.4125, "Asia/Dhaka", "BD"),
    s("Kabul", 34.5553, 69.2075, "Asia/Kabul", "AF"),
    s("Colombo", 6.9271, 79.8612, "Asia/Colombo", "LK"),
    s("Kathmandu", 27.7172, 85.3240, "Asia/Kathmandu", "NP"),
    s("Bangkok", 13.7563, 100.5018, "Asia/Bangkok", "TH"),
    s("Manila", 14.5995, 120.9842, "Asia/Manila", "PH"),
    s("Beijing", 39.9042, 116.4074, "Asia/Shanghai", "CN"), // not in the registry: default.safe
    s("Tokyo", 35.6762, 139.6503, "Asia/Tokyo", "JP"), // not in the registry: default.safe

    // Europe.
    s("London (inside the M25)", 51.5074, -0.1278, "Europe/London", "GB"),
    s("Glasgow (outside the M25)", 55.8642, -4.2518, "Europe/London", "GB"),
    s("Dublin", 53.3498, -6.2603, "Europe/Dublin", "IE"),
    s("Paris", 48.8566, 2.3522, "Europe/Paris", "FR"),
    s("Brussels", 50.8503, 4.3517, "Europe/Brussels", "BE"),
    s("Amsterdam", 52.3676, 4.9041, "Europe/Amsterdam", "NL"),
    s("Berlin", 52.5200, 13.4050, "Europe/Berlin", "DE"),
    s("Vienna", 48.2082, 16.3738, "Europe/Vienna", "AT"),
    s("Zurich", 47.3769, 8.5417, "Europe/Zurich", "CH"),
    s("Oslo", 59.9139, 10.7522, "Europe/Oslo", "NO"),
    s("Stockholm", 59.3293, 18.0686, "Europe/Stockholm", "SE"),
    s("Copenhagen", 55.6761, 12.5683, "Europe/Copenhagen", "DK"),
    s("Helsinki", 60.1699, 24.9384, "Europe/Helsinki", "FI"),
    s("Reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik", "IS", polar = true),
    s("Madrid", 40.4168, -3.7038, "Europe/Madrid", "ES"), // default.safe.europe
    s("Athens", 37.9838, 23.7275, "Europe/Athens", "GR"), // default.safe.balkans

    // Arctic.
    s("Tromso", 69.6492, 18.9553, "Europe/Oslo", "NO", polar = true),
    s("Longyearbyen (Svalbard)", 78.2232, 15.6267, "Europe/Oslo", "SJ", polar = true),
    s("Nuuk (Greenland)", 64.1836, -51.7214, "America/Nuuk", "GL", polar = true),
    s("Murmansk", 68.9585, 33.0827, "Europe/Moscow", "RU", polar = true), // default.safe outside Tatarstan/Dagestan/Bashkortostan
    s("Utqiagvik (Alaska)", 71.2906, -156.7887, "America/Anchorage", "US", polar = true),
    s("Iqaluit (Nunavut)", 63.7467, -68.5170, "America/Iqaluit", "CA", polar = true),

    // The Americas.
    s("Chicago metro", 41.8781, -87.6298, "America/Chicago", "US"), // Regions.chicago -> us.chicago
    s("New York (rest of the US)", 40.7128, -74.0060, "America/New_York", "US"),
    s("Toronto (GTA)", 43.6532, -79.3832, "America/Toronto", "CA"), // Regions.toronto -> ca.toronto
    s("Montreal", 45.5017, -73.5673, "America/Toronto", "CA"), // Regions.montreal -> ca.isna
    s("Ottawa", 45.4215, -75.6972, "America/Toronto", "CA"), // Regions.ottawa -> ca.isna
    s("Vancouver (rest of Canada)", 49.2827, -123.1207, "America/Vancouver", "CA"),
    s("Edmonton", 53.5461, -113.4938, "America/Edmonton", "CA"),
    s("Mexico City", 19.4326, -99.1332, "America/Mexico_City", "MX"),
    s("Sao Paulo", -23.5505, -46.6333, "America/Sao_Paulo", "BR"),
    s("Buenos Aires", -34.6037, -58.3816, "America/Argentina/Buenos_Aires", "AR"),
    s("Ushuaia (southern Argentina)", -54.8019, -68.3030, "America/Argentina/Ushuaia", "AR", polar = true),
    s("Santiago", -33.4489, -70.6693, "America/Santiago", "CL"),
    s("Punta Arenas (southern Chile)", -53.1638, -70.9171, "America/Punta_Arenas", "CL", polar = true),
    s("Stanley (Falklands)", -51.6960, -57.8508, "Atlantic/Stanley", "FK", polar = true),

    // Oceania.
    s("Sydney", -33.8688, 151.2093, "Australia/Sydney", "AU"),
    s("Hobart (Tasmania)", -42.8821, 147.3272, "Australia/Hobart", "AU", polar = true),
    s("Auckland", -36.8485, 174.7633, "Pacific/Auckland", "NZ"),
    s("Invercargill (southern NZ)", -46.4132, 168.3538, "Pacific/Auckland", "NZ", polar = true),
)

/** [SEEDS] by [Seed.label], for the extra settings-variety cases below (and [GoldenCasesTest]). */
private val seedByLabel: Map<String, Seed> = SEEDS.associateBy { it.label }
internal fun seed(label: String): Seed = requireNotNull(seedByLabel[label]) { "no seed named '$label'" }

private fun ramadanStart(year: Int): LocalDate {
    val calendar = Registry.ramadanCalendar()
    var date = LocalDate(year, 1, 1)
    var guard = 0
    while (!calendar.isRamadan(date)) {
        date = date.plus(1, DateTimeUnit.DAY)
        if (++guard > 400) error("no Ramadan date found in $year")
    }
    return date
}

private val YEARS = 2026..2030

/**
 * A general date pool spanning 2026-2030: each year's equinoxes, solstices and a Ramadan day (found
 * by [ramadanStart], never a printed table's date), 29 February 2028, and the US and EU DST changes
 * in 2026 and 2027 (2nd Sunday of March / 1st Sunday of November; last Sunday of March / October).
 */
val GENERAL_DATES: List<LocalDate> = buildList {
    for (year in YEARS) {
        add(LocalDate(year, 3, 20))
        add(LocalDate(year, 6, 21))
        add(LocalDate(year, 9, 22))
        add(LocalDate(year, 12, 21))
        add(ramadanStart(year).plus(3, DateTimeUnit.DAY))
    }
    add(LocalDate(2028, 2, 29))
    add(LocalDate(2026, 3, 8)); add(LocalDate(2026, 11, 1)) // US DST
    add(LocalDate(2026, 3, 29)); add(LocalDate(2026, 10, 25)) // EU DST
    add(LocalDate(2027, 3, 14)); add(LocalDate(2027, 11, 7)) // US DST
    add(LocalDate(2027, 3, 28)); add(LocalDate(2027, 10, 31)) // EU DST
}

/**
 * Extra dates for [Seed.polar] points: the weeks Fajr or Isha's own sign usually vanishes or
 * reappears near 60-70°, the solstices (polar day and polar night) and two more transition-ish
 * dates, each year 2026-2030.
 */
val POLAR_DATES: List<LocalDate> = buildList {
    for (year in YEARS) {
        add(LocalDate(year, 5, 5))
        add(LocalDate(year, 5, 25))
        add(LocalDate(year, 6, 21))
        add(LocalDate(year, 7, 20))
        add(LocalDate(year, 8, 5))
        add(LocalDate(year, 2, 15))
        add(LocalDate(year, 10, 25))
        add(LocalDate(year, 12, 21))
    }
}

/** [count] dates for [seed] (its own index [i] among [SEEDS]), spread over its date pool. */
private fun datesFor(seed: Seed, i: Int, count: Int): List<LocalDate> {
    val pool = if (seed.polar) POLAR_DATES + GENERAL_DATES else GENERAL_DATES
    return (0 until count).map { pool[(i * 11 + it * 7) % pool.size] }.distinct()
}

/** Entries only ever reached as a cautious member, never Automatic's own pick anywhere: exercised by
 * choosing them explicitly (in scope wherever they are a member, spec §2.2, ruling R50) at a seed in
 * their own country. */
internal val MEMBER_ONLY_ENTRIES: List<Pair<String, String>> = listOf(
    "gb.wifaqul" to "London (inside the M25)",
    "ie.ifi" to "Dublin",
    "ie.icci" to "Dublin",
    "fr.gmp" to "Paris",
    "be.emb" to "Brussels",
    "de.vikz" to "Berlin",
    "no.irn" to "Oslo",
    "tr.diyanet.europe" to "Brussels",
    "ca.ift" to "Toronto (GTA)",
    "ca.iit" to "Toronto (GTA)",
    "ca.mac" to "Toronto (GTA)",
    "au.lma" to "Sydney",
    "ps.gaza.awqaf" to "Gaza City",
    "za.mjc" to "Cape Town",
    "za.voc" to "Cape Town",
)

/** Every other-method id (spec §2.2's "Other methods"), each global (ruling R50: in scope anywhere). */
internal val OTHER_METHOD_IDS: List<String> = Registry.otherMethods.map { it.id }

/** ≈2,000 place-days (spec §3.2, ruling R84): [SEEDS] × dates, mostly Automatic, plus a small share of
 * every other [SettingsCase] so the settings paths are covered too. */
val GOLDEN_CASES: List<GoldenCase> = buildList {
    SEEDS.forEachIndexed { i, seed ->
        val count = if (seed.polar) 22 else 16
        for (date in datesFor(seed, i, count)) add(GoldenCase(seed, date, SettingsCase.Automatic))
    }
    // Hanafi Asr forced, at a spread of points across regions.
    for (label in listOf(
        "Karachi", "Delhi", "Kabul", "Ufa (Bashkortostan)", "Mashhad (Iran Hanafi)", "Chicago metro",
        "Johannesburg", "Ankara", "Dhaka", "Bishkek", "Tashkent",
    )) {
        add(GoldenCase(seed(label), GENERAL_DATES[3], SettingsCase.HanafiSchool))
        add(GoldenCase(seed(label), GENERAL_DATES[13], SettingsCase.HanafiSchool))
    }
    // The Saudi "5 minutes later" Fajr option, only meaningful in Saudi Arabia.
    for (date in listOf(GENERAL_DATES[1], GENERAL_DATES[16], GENERAL_DATES[24])) {
        add(GoldenCase(seed("Riyadh"), date, SettingsCase.SaudiLater))
    }
    // Every Other method, at a couple of different points each (spec §2.2, Scope.GLOBAL).
    val otherPoints = listOf("Riyadh", "London (inside the M25)", "Toronto (GTA)", "Jakarta")
    for ((k, id) in OTHER_METHOD_IDS.withIndex()) {
        val label = otherPoints[k % otherPoints.size]
        add(GoldenCase(seed(label), GENERAL_DATES[k % GENERAL_DATES.size], SettingsCase.ChosenEntry(id)))
        add(GoldenCase(seed(label), GENERAL_DATES[(k + 12) % GENERAL_DATES.size], SettingsCase.ChosenEntry(id)))
    }
    // Cautious members that Automatic never resolves to directly (see MEMBER_ONLY_ENTRIES's KDoc).
    for ((id, label) in MEMBER_ONLY_ENTRIES) {
        add(GoldenCase(seed(label), GENERAL_DATES[6], SettingsCase.ChosenEntry(id)))
        add(GoldenCase(seed(label), GENERAL_DATES[19], SettingsCase.ChosenEntry(id)))
    }
    // A positive per-prayer adjustment (always applied, spec §3.3; a negative one needs a
    // confirmation the generator would have to resolve first, see the report's deviations).
    val adjustmentPoints = listOf(
        "Cairo" to Prayer.FAJR, "Jakarta" to Prayer.DHUHR, "London (inside the M25)" to Prayer.ASR,
        "Toronto (GTA)" to Prayer.MAGHRIB, "Tromso" to Prayer.ISHA, "Sydney" to Prayer.FAJR,
        "Lagos" to Prayer.DHUHR, "Istanbul" to Prayer.ISHA,
    )
    for ((label, prayer) in adjustmentPoints) {
        add(GoldenCase(seed(label), GENERAL_DATES[9], SettingsCase.Adjustment(prayer, 7)))
    }
}
