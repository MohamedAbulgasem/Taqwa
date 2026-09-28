package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.RamadanRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.MethodVariant
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.AlgeriaWilayas
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Libya, Tunisia, Algeria, Morocco, Mauritania and Sudan (profiles-tested.md data-maghreb-libya;
 * authorities-arab-world-turkiye.md). Every margin here is fitted on this core (the gate's Fitter,
 * 5 s safety) against the official tables in `tools/timetables/official/gate/` (ly-awqaf, tn-inm,
 * dz-marw, ma-habous, mr-ministry, sd-ministry); the proof is
 * `docs/research/2026-09-prayer-times/proof/7d-maghreb-libya.md`. Only derived parameters are
 * written here: angles, minutes, margins in seconds and points, never a printed time.
 */
object Maghreb {

    // Libya.

    /**
     * Libya: the General Authority of Awqaf and Dar al-Ifta (Tripoli; the 1992–94 observation
     * committee's table, statement of 8 Feb 2022). The national method at each of the 22 cities
     * awqaf.gov.ly prints (the owner's answer, spec §6.2 a and §10.3): Fajr 18.5°, Isha 18.3°. The 21
     * other cities' tables are held for 25–26 Sep 2026 only, and Tripoli's alone spans the seasons
     * (23 checked days, March 2025 to October 2026), drifting against this model through the year
     * (Fajr about a minute later in spring and summer than in late September, Dhuhr later in winter,
     * sunrise earlier in summer). So each margin is the city's late-September offset from Tripoli
     * (the most cautious of three readings of two days) plus Tripoli's worst over its 23 days, plus
     * 5 s: never early at any city that drifts as Tripoli does. That is a projection outside late
     * September, not a measurement; Tripoli's own days are measured. The end of eating is that 18.5°
     * dawn + 7 s (ruling R59, the strictest reading: each western city's earliest late-September day
     * against Tripoli's API mean, plus Tripoli's earliest over its 23 days, 5 s; Tripoli the tightest);
     * imsak is Fajr − 10 or − 11 in print, so the dawn − 11. Jalu and Awjila print their Asr 7.5 min
     * after the others' rule, their own offset. East of 18.5° E and south of 29.5° N, Fajr and
     * Maghrib follow the local adhan and not this national method, and the end of eating is the
     * 19.5° dawn the mosques there call ([variants], ruling R73). Class D: at most 3 min late on
     * every day held (the east and south's Fajr and Maghrib measured only at the owner's two
     * observations there; their sunrise, Dhuhr, Asr and Isha still at all 14 of their cities).
     */
    val libyaMethod = TimetableMethod(
        id = "ly.awqaf",
        fajrAngle = 18.5,
        isha = IshaRule.Angle(18.3),
        margins = margins(start = 0, sunrise = -98, fajr = 63, dhuhr = 246, asr = 29, maghrib = 261, isha = 42),
        endOfEatingMarginSeconds = 7,
        imsakMinutesBeforeFajr = 11,
    )

    /** Jalu and Awjila print their Asr 7.5 min after the others' rule: the authority's own offset, every month. */
    private val libyaLateAsr = libyaMethod.copy(
        id = "ly.awqaf.late-asr",
        monthlyOffsets = mapOf(Prayer.ASR to IntArray(12) { LATE_ASR_SECONDS }),
    )

    private fun libyan(name: String, lat: Double, lon: Double, method: TimetableMethod? = null) =
        AuthorityUnit(name.lowercase().replace(' ', '-'), name, GeoPoint(lat, lon), 30.0, method)

    val libyaUnits = UnitSet(
        "ly.awqaf",
        listOf(
            libyan("Tripoli", 32.8872, 13.1913), libyan("Benghazi", 32.1167, 20.0667), libyan("Sabha", 27.0377, 14.4283),
            libyan("Tobruk", 32.0836, 23.9764), libyan("Misrata", 32.3754, 15.0925), libyan("Ghadames", 30.1333, 9.5),
            libyan("Kufra", 24.1833, 23.2833), libyan("Derna", 32.767, 22.6367), libyan("Sirte", 31.2089, 16.5887),
            libyan("Bayda", 32.7627, 21.7551), libyan("Zawiya", 32.7571, 12.7276), libyan("Hun", 29.1268, 15.9477),
            libyan("Jalu", 29.0331, 21.5482, libyaLateAsr), libyan("Emsaed", 31.5889, 25.052), libyan("Ajdabiya", 30.7554, 20.2263),
            libyan("Khoms", 32.6486, 14.2619), libyan("Marj", 32.4925, 20.8286), libyan("Bani Walid", 31.7566, 13.9942),
            libyan("Ras Lanuf", 30.4833, 18.55), libyan("Zliten", 32.4674, 14.5687), libyan("Brega", 30.4061, 19.5739),
            libyan("Awjila", 29.1081, 21.2869, libyaLateAsr),
        ),
    ) { libyaMethod.atEdge("ly.awqaf.edge", libyaMethod.margins.widened(60)) }

    val libya: RegistryEntry = single(
        id = "ly.awqaf", nameKey = "authority_awqaf_libya", entryClass = EntryClass.D_AUTHORITY, method = libyaMethod,
        school = AsrSchool.STANDARD, countries = setOf("LY"),
    )

    // Tunisia.

    /**
     * Tunisia: the Institut National de la Météorologie, per delegation at its own coordinates (258;
     * the six checked are at INM's own reference points). Fajr 18°, Isha 18°, Dhuhr = transit + 7 and
     * Maghrib = sunset + 2, sunrise floored; Asr first-principles plus a monthly table (its definition
     * is none of those tested); imsak Fajr − 10 (INM prints none in the tables held). Margins fitted on
     * the four low delegations' 2026 tables (Tunis and Sfax every day, Tabarka and Ben Guerdane every
     * third), the end of eating from the dawn − 41 s; held out, Tunis 2019–2025 and the two elevated
     * delegations, where nothing was early. Elevated delegations apply a horizon dip to Fajr, sunrise,
     * Maghrib and Isha (Tataouine ~0.45°, Tala ~1.0°, found on their own tables in the research);
     * their dipped Fajr takes 60 s more. Beyond the six (class D), the deepest dip seen (1°) for
     * sunrise, Maghrib and Isha, the end of eating at the dipped dawn, and the Fajr start undipped, so
     * it is never before a low delegation's. Class B: at most 1 min late at the low delegations, 2 at
     * the dipped ones.
     */
    val tunisiaMethod = TimetableMethod(
        id = "tn.inm",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(dhuhr = 7, maghrib = 2),
        margins = margins(start = 0, sunrise = -27, fajr = -51, dhuhr = -15, asr = 14, maghrib = 26, isha = 43),
        monthlyOffsets = mapOf(Prayer.ASR to intArrayOf(-57, -58, -45, -38, -35, -35, -15, 7, 20, 10, 9, -20)),
        endOfEatingMarginSeconds = -41,
        imsakMinutesBeforeFajr = 10,
    )

    private fun dipped(id: String, dip: Double) = tunisiaMethod.copy(
        id = "tn.inm.$id", horizonDeg = -0.8333 - dip, twilightDipDeg = dip,
        margins = tunisiaMethod.margins.copy(fajr = tunisiaMethod.margins.fajr + 60),
    )

    val tunisiaUnits = UnitSet(
        "tn.inm",
        listOf(
            AuthorityUnit("tunis", "Tunis", GeoPoint(36.8, 10.183), 25.0),
            AuthorityUnit("sfax", "Sfax", GeoPoint(34.733, 10.75), 25.0),
            AuthorityUnit("tataouine", "Tataouine", GeoPoint(32.933, 10.45), 25.0, dipped("tataouine", 0.45)),
            AuthorityUnit("tala", "Tala", GeoPoint(35.572, 8.67), 25.0, dipped("tala", 1.0)),
            AuthorityUnit("tabarka", "Tabarka", GeoPoint(36.952, 8.758), 25.0),
            AuthorityUnit("ben-guerdane", "Ben Guerdane", GeoPoint(33.139, 11.215), 25.0),
        ),
    ) {
        tunisiaMethod.atEdge("tn.inm.edge", tunisiaMethod.margins.widened(60)).copy(
            horizonDeg = -1.8333, isha = IshaRule.Angle(19.0), endOfEating = EndOfEating.DawnAngle(19.0),
        )
    }

    val tunisia: RegistryEntry = single(
        id = "tn.inm", nameKey = "authority_inm", entryClass = EntryClass.B, method = tunisiaMethod,
        school = AsrSchool.STANDARD, countries = setOf("TN"),
    )

    // Algeria.

    /**
     * Algeria: the Ministry of Religious Affairs and Waqfs, 1448 calendars: Fajr 18°, Isha 17°,
     * Maghrib + 3, every column rounded up, sunrise included; the Asr of a one-shot formula with the
     * sun at 12h UT; no elevation, no imsak. The three base cities' margins are fitted on Algiers's
     * 1448 table and held out on Djelfa's, Adrar's and eight days of Algiers's 1447 calendar (nothing
     * early, at most 1 min late at all three). Class B there: MARW's own reference points are not
     * published.
     *
     * Every other wilaya is computed at its seat (the owner's answer, spec §10.4). The ministry prints
     * those as a base city plus a difference per month (Adrar's group per half month); Oran's and
     * Tamanrasset's derived tables fit their seats' own margins, class D there (Oran at most 3 min
     * late, Tamanrasset 3 and Asr 6, a recorded exception), and every other seat takes the later of
     * the two (not measured).
     */
    val algeriaMethod = TimetableMethod(
        id = "dz.marw",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.0),
        asrModel = AsrModel.UTC12_ONE_SHOT,
        authorityMinutes = EventOffsets(maghrib = 3),
        margins = margins(start = 0, sunrise = 49, fajr = 14, dhuhr = 7, asr = 10, maghrib = 11, isha = 14),
        endOfEatingMarginSeconds = 46,
    )

    /** Oran's seat, fitted on the ministry's Oran table (Algiers plus Oran's monthly differences). */
    private val oranMethod = algeriaMethod.copy(
        id = "dz.marw.oran",
        margins = margins(start = 0, sunrise = 62, fajr = 135, dhuhr = 21, asr = 111, maghrib = 132, isha = 144),
        endOfEatingMarginSeconds = 65,
    )

    /** Tamanrasset's seat, fitted on the ministry's Tamanrasset table (Adrar plus its half-monthly differences). */
    private val tamanrassetMethod = algeriaMethod.copy(
        id = "dz.marw.tamanrasset",
        margins = margins(start = 0, sunrise = 67, fajr = 188, dhuhr = 19, asr = 195, maghrib = 178, isha = 194),
        endOfEatingMarginSeconds = 62,
    )

    /** Every other seat: the later of the two derived tables' starts, the earlier sunrise (no table of its own). */
    private val wilayaMethod = algeriaMethod.atEdge(
        "dz.marw.wilaya",
        margins(start = 0, sunrise = 62, fajr = 188, dhuhr = 21, asr = 195, maghrib = 178, isha = 194),
    )

    private val tamanrassetAsr = LateLimit(
        6,
        "The ministry prints Tamanrasset as Adrar's table plus one difference each half month, which does not " +
            "follow Tamanrasset's own Asr through the half month; so that the Asr computed at its seat is never " +
            "before that printed time, it is up to 6 min after it on some days.",
        setOf(TimedEvent.ASR),
    )

    /** A wilaya whose table is held: its base city's point (the research's) or its seat, its method and class. */
    private class Measured(val point: GeoPoint?, val method: TimetableMethod?, val entryClass: EntryClass, val lateLimits: List<LateLimit> = emptyList())

    private val measuredWilayas = mapOf(
        16 to Measured(GeoPoint(36.7538, 3.0588), null, EntryClass.B),
        17 to Measured(GeoPoint(34.6704, 3.2630), null, EntryClass.B),
        1 to Measured(GeoPoint(27.8742, -0.2939), null, EntryClass.B),
        31 to Measured(null, oranMethod, EntryClass.D_AUTHORITY),
        11 to Measured(null, tamanrassetMethod, EntryClass.D_AUTHORITY, listOf(tamanrassetAsr)),
    )

    /**
     * A place's wilaya is its own region when known (ruling R32); the nearest seat only otherwise,
     * which in the Sahara can be another wilaya's (Aoulef, in Adrar, is nearer In Salah). The whole
     * wilaya follows its seat's table, so the seat rides as the fixed point everywhere in it; a
     * wilaya whose table is held is measured only within its class's reach of the seat (ruling R40:
     * about 45 km for B, 70 for D), beyond which its own sun can be later than the seat's by more than
     * the class allows and the rest of the wilaya claims no figure.
     */
    val algeriaUnits: UnitSet = run {
        val cores = mutableMapOf<String, AuthorityUnit>()
        val rests = mutableMapOf<String, AuthorityUnit>()
        for (seat in AlgeriaWilayas.seats) {
            val id = seat.code.toString()
            val held = measuredWilayas[seat.code]
            if (held == null) {
                rests[id] = AuthorityUnit(id, seat.name, seat.point, WILAYA_REACH_KM, wilayaMethod, measured = false)
                continue
            }
            val point = held.point ?: seat.point
            cores[id] = AuthorityUnit(
                id, seat.name, point, lateReachKm(point.lat, held.entryClass), held.method,
                entryClass = held.entryClass.takeIf { it != EntryClass.B }, lateLimits = held.lateLimits,
            )
            rests[id] = AuthorityUnit("$id-rest", seat.name, point, WILAYA_REACH_KM, held.method, measured = false)
        }
        UnitSet(
            "dz.marw",
            // Cores first: at the same distance the nearest-unit search keeps the first.
            cores.values.toList() + rests.values.toList(),
            choose = { place ->
                place.admin1?.let(AlgeriaWilayas::seatFor)?.let { seat ->
                    val id = seat.code.toString()
                    cores[id]?.takeIf { distanceKm(GeoPoint(place.lat, place.lon), it.point) <= it.radiusKm } ?: rests.getValue(id)
                }
            },
        ) { wilayaMethod }
    }

    val algeria: RegistryEntry = single(
        id = "dz.marw", nameKey = "authority_marw", entryClass = EntryClass.B, method = algeriaMethod,
        school = AsrSchool.STANDARD, countries = setOf("DZ"),
    )

    // Morocco.

    /**
     * Morocco: the Ministry of Habous and Islamic Affairs, about 190 city tables. Fajr 19° (rounded
     * down), Isha 17°; national margins for Fajr, Dhuhr, Asr and Isha and the end of eating (the dawn
     * − 9 s), fitted on the ten cities' live month (13 Sep–12 Oct 2026); the held-out Wayback pages of
     * 2015–2025 widened Fajr by 7 s and Asr by 18 s.
     * Sunrise and Maghrib are corrected per city for elevation, so each fitted city has its own,
     * fitted the same way and then [HABOUS_SAFETY] more (the city points are the centres, Habous's are
     * unpublished, and most cities' pages cover one or two seasons); Ifrane's, with no page but the
     * live month, [IFRANE_SAFETY] more. The base sunrise and Maghrib hold at every place checked.
     * Elsewhere, at the other ~180 Habous cities, sunrise −510 s and Maghrib +510 s, beyond Imilchil's
     * (about 2,150 m): recorded exceptions ([edgeSunrise], [edgeMaghrib]); and a minute more on Fajr,
     * Dhuhr, Asr and Isha, since Habous's point for those cities is not known either (like the Libyan
     * and Tunisian edges). Class B at the fitted cities.
     */
    val moroccoMethod = TimetableMethod(
        id = "ma.habous",
        fajrAngle = 19.0,
        isha = IshaRule.Angle(17.0),
        margins = margins(start = 0, sunrise = -479, fajr = -29, dhuhr = 293, asr = 12, maghrib = 466, isha = -7),
        endOfEatingMarginSeconds = -9,
    )

    /** Spec §6.3 (ruling R41): beyond the fitted cities, sunrise and Maghrib take a margin for any elevation. */
    private val edgeSunrise = LateLimit(
        7,
        "Beyond the cities whose own Habous table Taqwa fits, the place's elevation is not known, so sunrise takes a " +
            "national margin that covers the highest town checked (Imilchil, about 2,150 m): at a low town it is up to " +
            "7 min before Habous's sunrise.",
        setOf(TimedEvent.SUNRISE),
    )

    private val edgeMaghrib = LateLimit(
        6,
        "Beyond the cities whose own Habous table Taqwa fits, the place's elevation is not known, so Maghrib takes a " +
            "national margin that covers the highest town checked (Imilchil, about 2,150 m): at a low town it is up to " +
            "6 min after Habous's Maghrib.",
        setOf(TimedEvent.MAGHRIB),
    )

    /** A fitted city is held to its class's own limit, not the national margin's. */
    private val fittedCityLimit = LateLimit(
        2,
        "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for " +
            "other places does not apply here.",
        setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB),
    )

    private fun habous(name: String, lat: Double, lon: Double, sunrise: Int, maghrib: Int, safety: Int = HABOUS_SAFETY) = AuthorityUnit(
        id = name.lowercase(), name = name, point = GeoPoint(lat, lon), radiusKm = 25.0,
        method = moroccoMethod.copy(
            id = "ma.habous.${name.lowercase()}",
            margins = moroccoMethod.margins.copy(sunrise = sunrise - safety, maghrib = maghrib + safety),
        ),
        lateLimits = listOf(fittedCityLimit),
    )

    val moroccoUnits = UnitSet(
        "ma.habous",
        listOf(
            habous("Casablanca", 33.5731, -7.5898, -179, 271),
            habous("Rabat", 34.0209, -6.8416, -163, 225),
            habous("Oujda", 34.6814, -1.9086, -314, 365),
            habous("Tangier", 35.7595, -5.8340, -225, 272),
            habous("Laayoune", 27.1253, -13.1625, -128, 220),
            habous("Dakhla", 23.6848, -15.9580, -145, 219),
            habous("Figuig", 32.1090, -1.2290, -354, 385),
            habous("Midelt", 32.6852, -4.7451, -441, 456),
            habous("Lagouira", 20.9331, -17.0347, -118, 201),
            habous("Ifrane", 33.5228, -5.1106, -403, 421, IFRANE_SAFETY),
        ),
    ) { moroccoMethod.atEdge("ma.habous.edge", moroccoMethod.margins.widened(60).copy(sunrise = -510, maghrib = 510)) }

    val morocco: RegistryEntry = single(
        id = "ma.habous", nameKey = "authority_habous", entryClass = EntryClass.B, method = moroccoMethod,
        school = AsrSchool.STANDARD, countries = setOf("MA", "EH"), lateLimits = listOf(edgeSunrise, edgeMaghrib),
    )

    // Mauritania and Sudan.

    /**
     * Mauritania: the ministry's Ramadan imsakiyas only (HIGH for what they show, LOW that a year-round
     * table is followed): Fajr 19.5°, Isha 17.5°, Maghrib = sunset + 2, imsak = Fajr − 5. Fitted on
     * Nouadhibou's 1444 page and widened by Nouakchott's held-out 1445 page (Fajr, Dhuhr and Asr were
     * a minute early there with the first fit). Every start keeps at least the plain safe 30 s after
     * the method itself (two Ramadans are one season): Dhuhr never before transit. Sunrise −124 s, the
     * tables' own (earlier than a −0.83° sunrise). Class D, not measured beyond the two cities' Ramadans.
     */
    val mauritaniaMethod = TimetableMethod(
        id = "mr.ministry",
        fajrAngle = 19.5,
        isha = IshaRule.Angle(17.5),
        authorityMinutes = EventOffsets(maghrib = 2),
        margins = margins(start = SAFE_START, sunrise = -124),
        endOfEatingMarginSeconds = SAFE_END,
        imsakMinutesBeforeFajr = 5,
    )

    val mauritania: RegistryEntry = single(
        id = "mr.ministry", nameKey = "authority_mauritania", entryClass = EntryClass.D_AUTHORITY, method = mauritaniaMethod,
        school = AsrSchool.STANDARD, countries = setOf("MR"),
    )

    private val sudanEndOfEating = LateLimit(
        7,
        "News reports of Sudan's 2026 imsakiya, not the table itself, put its dawn near 19.4°, earlier than the 18.3° " +
            "of the one table held (Khartoum, Ramadan 1443); until a current table is held the fast begins at the 19.5° " +
            "dawn, up to 7 min before that table's Fajr.",
        setOf(TimedEvent.END_OF_EATING),
    )

    /**
     * Ruling R74: a 2026 Ramadan imsakiya circulating under the Fiqh Academy's name (not the table
     * itself, which is Ramadan 1443/2022) puts Isha at Maghrib + 90, 13–19 min after the verified
     * table's 18.0° angle on the research's projected 2024–2030 dates; the gate's own worst day
     * against the held 2022 table (a different season) is 20 min. A floor, never earlier than that
     * table, of the two on the Academy's own dates only: on the verified table's own 30 Ramadan
     * days, the floor is the honest exception.
     */
    private val sudanRamadanIsha = LateLimit(
        20,
        "A 2026 Ramadan imsakiya circulating under the Fiqh Academy's name (not the verified 2022 table) puts Isha " +
            "at Maghrib + 90; on the entry's Ramadan dates only, Isha never earlier than that, up to 20 min after the " +
            "2022 table's 18.0° angle.",
        setOf(TimedEvent.ISHA),
    )

    /**
     * Sudan: the Fiqh Academy's 2022 Khartoum imsakiya implies Fajr ≈ 18.3°, Isha ≈ 18.0°, Dhuhr ≈ +
     * 2 (fitted: +131 s); news reports of 2026, and nothing more, imply ≈ 19.4° (LOW). The Fajr start
     * at 18.3°, the end of eating at 19.5° (the earlier, a recorded exception). Every start keeps at
     * least the plain safe 30 s after the method (one Ramadan's table): Maghrib, which the imsakiya
     * prints about half a minute before the standard sunset, never before it. The Academy calls an
     * imsak before Fajr unfounded, so none. On the entry's Ramadan dates, Isha is also never earlier
     * than Maghrib + 90 min (ruling R74: a 2026 imsakiya circulating under the Fiqh Academy's name,
     * unverified but corroborated by a second, independent republication; a floor, not a replacement,
     * so the 18.0° angle still wins where it falls later — never asserted for any other month). Class
     * D.
     */
    val sudanMethod = TimetableMethod(
        id = "sd.ministry",
        fajrAngle = 18.3,
        isha = IshaRule.Angle(18.0),
        margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, dhuhr = 131),
        ramadan = RamadanRule(ishaFloorMinutesAfterMaghrib = 90),
        endOfEating = EndOfEating.DawnAngle(19.5),
        endOfEatingMarginSeconds = SAFE_END,
    )

    val sudan: RegistryEntry = single(
        id = "sd.ministry", nameKey = "authority_sudan_fiqh", entryClass = EntryClass.D_AUTHORITY, method = sudanMethod,
        school = AsrSchool.STANDARD, countries = setOf("SD"), lateLimits = listOf(sudanEndOfEating, sudanRamadanIsha),
    )

    /**
     * Eastern and southern Libya follow the local adhan for Fajr and Maghrib, not the national
     * method computed at each city (ruling R73). The owner heard it on 27 Sep 2026, 700 km apart,
     * at Benghazi and at Sabha: each exactly the 19.5° dawn and sunset + 1 minute, floored, and
     * 7 (Fajr) and 4 (Maghrib) minutes earlier than the national method there — safe (never early)
     * but not what the mosques call. So there Fajr is the 19.5° dawn with the plain safe margin
     * (like Mauritania and Sudan), never before it; Maghrib is sunset + 1 minute with the same
     * margin, never before it either; the end of eating stays that 19.5° dawn as before, its own
     * margin unchanged; imsak, still "dawn − 11", follows along since the dawn it reads is now the
     * 19.5° one. Beyond 30 km of every city (the edge, rulings R44 and R45) Fajr and Maghrib keep
     * the edge's minute on top of that safe margin, as every other start there does. Dhuhr, Asr,
     * Isha and sunrise keep the national method. Fajr and Maghrib are measured only at the owner's
     * two observations (`open/LY-AWQAF-OWNER`); the gate no longer holds the national table's Fajr
     * and Maghrib columns there against these cities (their other columns still checked). The
     * west's fitted margin, unaffected, is for the 18.5° dawn at its tables.
     */
    val variants = listOf(
        MethodVariant("ly.awqaf", Regions.libyaEastSouth) {
            it.copy(
                fajrAngle = 19.5,
                authorityMinutes = it.authorityMinutes.copy(maghrib = 1),
                margins = it.margins.copy(
                    fajr = SAFE_START + beyondNational(it.margins.fajr, libyaMethod.margins.fajr),
                    maghrib = SAFE_START + beyondNational(it.margins.maghrib, libyaMethod.margins.maghrib),
                ),
                endOfEating = EndOfEating.DawnAngle(19.5),
                endOfEatingMarginSeconds = minOf(it.endOfEatingMarginSeconds, SAFE_END),
            )
        },
    )

    /**
     * What a place's start margin carries beyond the national one: nothing at a city (every unit's
     * method shares [libyaMethod]'s margins), the edge's minute beyond them. Never less than nothing,
     * so the variant's safe margin is never narrowed.
     */
    private fun beyondNational(margin: Int, national: Int): Int = maxOf(0, margin - national)

    val entries = listOf(libya, tunisia, algeria, morocco, mauritania, sudan)

    /** Habous's fitted cities: 20 s more than the Fitter's 5 on sunrise and Maghrib. */
    private const val HABOUS_SAFETY = 20

    /** Ifrane, fitted on its live month alone: the most another city's held-out pages widened a margin (Dakhla's Maghrib). */
    private const val IFRANE_SAFETY = 36
    private const val LATE_ASR_SECONDS = 450
    private const val WILAYA_REACH_KM = 700.0
}
