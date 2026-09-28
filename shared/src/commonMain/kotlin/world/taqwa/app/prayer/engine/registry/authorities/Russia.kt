package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.CALCULATED_NAME
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.DumRfMoscow
import world.taqwa.app.prayer.engine.registry.data.EndOfEatingDawns
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Russia has no national authority; each muftiate publishes its own (research Russia section).
 * Tatarstan, Dagestan and Bashkortostan are regions (see Regions); the rest of Russia takes DUM RF's
 * method, the Moscow muftiate's, at the user's point.
 */
object Russia {

    /**
     * Tatarstan: DUM RT's 2026 table for 44 localities (xlsx, CC BY 4.0; HIGH). Sahur ends at 18°
     * (rounded down), the prayer's own start; the separate "mosque Fajr" (sunrise − 91, "совершается в
     * мечетях") is a congregation time and is not shown (spec §3.3). Zuhr is a fixed time per town
     * (Kazan 12:00), shown as printed until its meaning is confirmed; the gate finds it exact at all
     * 19 unit towns, so no late-limit exception is recorded for it. Hanafi Asr, sunrise and Maghrib
     * rounded up (so a floored sunrise is never after it), Isha 15°.
     *
     * White nights: sahur = sunrise − 121 and Isha = Maghrib + 90 on the nights the sun does not
     * reach 18° at the locality (the evening before such a morning takes Maghrib + 90). The 2026 table
     * applies it on exactly those nights at all 19 unit towns, so its dates move with latitude (Agryz
     * 3 May–10 Aug, Kazan 6 May–8 Aug, Bugulma 10 May–3 Aug) and with the leap cycle (Kazan's first
     * night is 5 or 6 May). [HighLatRule.DumRtSummer] follows it the same way, night by night at the
     * place's points (ruling R58). A night too close to call takes each time's safe side (ruling
     * R61): Fajr at sunrise − 121, the end of sahur at the sun's lowest point and Isha at the later of
     * Maghrib + 90 and the 15° dusk. At a town that is a sun within 0.01° of 18°, where DUM RT's sun
     * and ours may fall either side (Zelenodolsk 5 May and Yelabuga 8 Aug in 2026, one to four
     * nights a year among the 19 towns); elsewhere a sun within 0.2° of it at the user's point, where
     * the locality DUM RT would print for may lie 20 km away (two to four nights a year).
     *
     * Units are the 19 localities in the app's city list, each with its printed Zuhr; elsewhere in
     * Tatarstan the latest Zuhr any locality prints (12:15) and 90 s either way, and, since the
     * locality DUM RT would print for may lie 20 km off and around the white nights the real dawn
     * moves by minutes with every few km, on every night Fajr and Isha are the latest and the end of
     * sahur the earliest that a table printed anywhere within 0.2° of latitude would give (sampled
     * every 0.05°; ruling R65), besides R61's safe side on the nights within 0.2° of 18° (up to about
     * two hours from the time a table prints on those). Against the 19 towns' tables from 5.5 to
     * 22 km away no night from April to September is on the wrong side. What the band adds: at most
     * a minute from October to February and 2 min in March, June, July and September; in late April
     * to mid-May and in August, on the nights more than 0.5° from 18°, Fajr up to 8 min later, Isha 3
     * and the end of sahur up to 20 min earlier, where the locality 22 km off prints them so.
     *
     * Margins fitted on Kazan's table and widened by the excess the 18 other towns' tables held out
     * showed, plus 5 s (the Fitter's safety): Fajr + 16 s and the end of sahur − 34 s for the real
     * dawns that fall minutes from the sun's lowest point (Yelabuga's 8 Aug and Bavly's 3 Aug, where
     * a crossing moves a minute with the town's point), sunrise + 37 s, Asr + 78 s, Maghrib + 21 s,
     * Isha + 13 s. Zainsk's table runs 20–50 s later than its app point gives, so it keeps its own
     * starts. Class B.
     */
    val dumRtMethod = TimetableMethod(
        id = "ru.dumrt",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(15.0),
        margins = margins(start = SAFE_START, sunrise = 37, fajr = 16, asr = 78, maghrib = 21, isha = 13),
        endOfEatingMarginSeconds = -34,
        fixedDhuhrLocalMinutes = 12 * 60,
        highLatitude = HighLatRule.DumRtSummer(),
    )

    /**
     * A locality at its app point with its printed [zuhr]; [margins], where its own table asked for
     * later starts than the entry's (Zainsk).
     */
    private fun town(
        name: String,
        lat: Double,
        lon: Double,
        zuhr: String,
        margins: EventOffsets? = null,
        lateLimits: List<LateLimit> = emptyList(),
    ): AuthorityUnit {
        val (h, m) = zuhr.split(":").map { it.toInt() }
        val minutes = h * 60 + m
        val id = name.lowercase().replace(' ', '-')
        val own = minutes != 12 * 60 || margins != null
        return AuthorityUnit(
            id = id, name = name, point = GeoPoint(lat, lon), radiusKm = 25.0, lateLimits = lateLimits,
            method = if (!own) {
                null
            } else {
                dumRtMethod.copy(id = "ru.dumrt.$id", fixedDhuhrLocalMinutes = minutes, margins = margins ?: dumRtMethod.margins)
            },
        )
    }

    val dumRtUnits = UnitSet(
        "ru.dumrt",
        listOf(
            town("Kazan", 55.78874, 49.12214, "12:00"),
            town("Naberezhnye Chelny", 55.73718, 52.41961, "12:00"),
            town("Nizhnekamsk", 55.63794, 51.81502, "11:50"),
            town("Almetyevsk", 54.90395, 52.31793, "12:00"),
            town(
                "Zelenodolsk", 55.84376, 48.51784, "12:05",
                lateLimits = listOf(
                    LateLimit(62, KNIFE_EDGE_NIGHT, setOf(TimedEvent.ISHA)),
                    LateLimit(132, KNIFE_EDGE_NIGHT, setOf(TimedEvent.END_OF_EATING)),
                ),
            ),
            town("Bugulma", 54.5378, 52.7985, "12:00"),
            town(
                "Yelabuga", 55.76232, 52.04425, "12:00",
                lateLimits = listOf(
                    LateLimit(126, KNIFE_EDGE_NIGHT, setOf(TimedEvent.FAJR)),
                    LateLimit(12, YELABUGA_SAHUR, setOf(TimedEvent.END_OF_EATING)),
                ),
            ),
            town("Leninogorsk", 54.5971, 52.45124, "12:00"),
            town("Chistopol", 55.36612, 50.64399, "12:00"),
            // Its own table held out: starts up to 77 s (Fajr, on a night that only just reaches 18°),
            // 101 s (Asr), 45 s (Maghrib) and 24 s (Isha) later than the entry's margins give here.
            town(
                "Zainsk", 55.3195, 52.06942, "12:00",
                margins = dumRtMethod.margins.copy(fajr = 77, asr = 101, maghrib = 45, isha = 24),
                lateLimits = listOf(
                    LateLimit(3, ZAINSK_FAJR, setOf(TimedEvent.FAJR)),
                    LateLimit(3, EDGE_NIGHT_SAHUR, setOf(TimedEvent.END_OF_EATING)),
                ),
            ),
            town("Aznakayevo", 54.85821, 53.08006, "12:00"),
            town("Nurlat", 54.42903, 50.80598, "12:00"),
            town("Bavly", 54.39759, 53.25116, "12:00", lateLimits = listOf(LateLimit(3, EDGE_NIGHT_SAHUR, setOf(TimedEvent.END_OF_EATING)))),
            town("Mendeleyevsk", 55.89692, 52.31119, "12:00"),
            town("Buinsk", 54.97422, 48.29088, "12:15"),
            town("Agryz", 56.5234, 52.99431, "11:45"),
            town("Arsk", 56.09254, 49.87819, "12:00"),
            town("Menzelinsk", 55.72792, 53.1022, "12:00"),
            town("Kukmor", 56.18648, 50.89404, "12:00"),
        ),
    ) { dumRtMethod.atEdge("ru.dumrt.edge", dumRtMethod.margins.widened(90, sunrise = -90)).copy(fixedDhuhrLocalMinutes = 12 * 60 + 15) }

    val dumRt: RegistryEntry = single(
        id = "ru.dumrt", nameKey = "authority_dum_rt", entryClass = EntryClass.B, method = dumRtMethod,
        school = AsrSchool.HANAFI, scope = Scope.UNIT, countries = setOf("RU"),
    )

    /**
     * DUM RF (Moscow and its oblast; HIGH): Fajr 18°, Isha 15° outside summer, Standard Asr, shuruk − 5,
     * Dhuhr + 5, Maghrib + 5. Fajr is also the end of suhur (printed), here at its own earliest dawns
     * (ruling R39: EndOfEatingDawns). Its 2025–26 rows show the summer rule exactly: Fajr the later of
     * 18° and sunrise − 0.30 of the night, Isha the earlier of 15° and sunset + a fraction of the night
     * that runs from 0.25 in early May to 0.30 in June and back to 0.25 by August, stepping every few
     * days (never over 0.301); its 2024 rows followed an older rule (a fixed 1 h 55 min), a known
     * exception. Away from Moscow the rule applies as curves (PlaceCurves: Fajr at 0.30, Isha at 0.305,
     * the latest either way); the rest of Russia (class D, spec §6.2 a) takes it at the user's point,
     * 60 s later.
     */
    val dumRfMethod = TimetableMethod(
        id = "ru.dumrf",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(15.0),
        authorityMinutes = EventOffsets(sunrise = -5, dhuhr = 5, maghrib = 5),
        margins = margins(start = -22, sunrise = -10, fajr = 60, asr = -27, maghrib = -20, isha = -34),
        highLatitude = HighLatRule.NightFraction(fajrFraction = 0.3, ishaFraction = 0.3),
        endOfEatingMarginSeconds = 54,
    )

    /**
     * DUM RF's Moscow table at its point: Fajr, Isha and the end of suhur at the depressions its
     * captured rows print (DumRfMoscow, 104 slots) and on the other slots its rule by its own seasonal
     * fractions, interpolated between the held days; Fajr and Isha each the latest of three
     * neighbouring days (ruling R28), the end the earliest (R39, TwilightCurves.endOfEating). Margins
     * fitted by Task 7b on its 2025–26 rows (5 s safety): Fajr and Isha − 29 s on the curves, sunrise
     * − 10, Dhuhr − 22, Asr − 27, Maghrib − 20, the end + 54; nothing is held out (its captures are
     * sparse).
     */
    private val moscowMethod: TimetableMethod by lazy {
        val fajr = DumRfMoscow.fajr
        val isha = DumRfMoscow.isha
        val dawns = EndOfEatingDawns.dumRfMoscow
        dumRfMethod.copy(
            id = "ru.dumrf.moscow",
            fajrAngleByDayOfYear = DoubleArray(366) { i -> minOf(fajr[(i + 365) % 366], fajr[i], fajr[(i + 1) % 366]) },
            ishaAngleByDayOfYear = DoubleArray(366) { i -> maxOf(isha[(i + 365) % 366], isha[i], isha[(i + 1) % 366]) },
            endOfEating = EndOfEating.DawnAngle(18.0, TwilightCurves.endOfEating(MOSCOW.lat, dawns.latitude, dawns.depressions)),
            margins = dumRfMethod.margins.copy(fajr = -29, isha = -29),
        )
    }

    /**
     * DUM RF prints one table for "Moscow and Moscow oblast", so the whole oblast follows the Moscow
     * table (the M25 case of ruling R44, not a neighbour publishing its own, so no R40 cut). Within
     * R40's 47 km (class D's 3 min) the Moscow unit; on to the oblast's edges (about 165 km) a second
     * unit on the same point, claiming no figure: east of Moscow its starts are the table's and its
     * ends the user's own earlier sun, west of it the other way round, up to about 11 min from the
     * Moscow table at the oblast's edges, never before it. Beyond 60 km east the old edge (the user's
     * own sun a minute later) was early against the Moscow table, which the oblast follows.
     */
    val dumRfUnits: UnitSet by lazy {
        UnitSet(
            "ru.dumrf",
            listOf(
                AuthorityUnit(
                    "moscow", "Moscow", MOSCOW, lateReachKm(MOSCOW.lat, EntryClass.D_AUTHORITY), moscowMethod,
                    lateLimits = listOf(
                        LateLimit(4, MOSCOW_FAJR, setOf(TimedEvent.FAJR)),
                        LateLimit(5, MOSCOW_ISHA, setOf(TimedEvent.ISHA)),
                        LateLimit(4, MOSCOW_END, setOf(TimedEvent.END_OF_EATING)),
                    ),
                ),
                AuthorityUnit("moscow-oblast", "Moscow oblast", MOSCOW, OBLAST_REACH_KM, moscowMethod, measured = false),
            ),
        ) { dumRfMethod.atEdge("ru.dumrf.edge", dumRfMethod.margins.widened(60)) }
    }

    val dumRf: RegistryEntry = single(
        id = "ru.dumrf", nameKey = "authority_dum_rf", entryClass = EntryClass.D_AUTHORITY, method = dumRfMethod,
        school = AsrSchool.STANDARD, countries = setOf("RU"),
    )

    /**
     * Dagestan: DUM RD (islamdag.ru, one month, 26 localities; LOW–MEDIUM): Fajr ≈ 18° + 3, sunrise − 3,
     * Dhuhr + 5, Shafi'i Asr + 3, Maghrib + 5, Isha 15°; its localities' points are not known, so 60 s
     * more on every time beyond the first-guess 30 s. Class D.
     */
    val dumRdMethod = TimetableMethod(
        id = "ru.dumrd",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(15.0),
        authorityMinutes = EventOffsets(fajr = 3, sunrise = -3, dhuhr = 5, asr = 3, maghrib = 5),
        margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE),
    ).let { it.atEdge(it.id, it.margins.widened(60)) }

    val dumRd: RegistryEntry = single(
        id = "ru.dumrd", nameKey = "authority_dum_rd", entryClass = EntryClass.D_AUTHORITY, method = dumRdMethod,
        school = AsrSchool.STANDARD, scope = Scope.UNIT, countries = setOf("RU"),
    )

    /**
     * Bashkortostan: TsDUM (Ufa) not found. Calculated by Taqwa with DUM RF's conventions (the nearest
     * published), and the Hanafi Asr of its majority (spec §3.7).
     */
    val bashkortostan: RegistryEntry = single(
        id = "ru.bashkortostan", nameKey = CALCULATED_NAME, entryClass = EntryClass.D_NONE,
        method = dumRfMethod.atEdge("ru.bashkortostan", dumRfMethod.margins.widened(60)).copy(highLatitude = HighLatRule.Standard),
        school = AsrSchool.HANAFI, scope = Scope.UNIT, countries = setOf("RU"),
    )

    val entries = listOf(dumRt, dumRf, dumRd, bashkortostan)

    private val MOSCOW = GeoPoint(55.75204, 37.61781)

    /** How far Moscow oblast reaches from Moscow's point. */
    private const val OBLAST_REACH_KM = 165.0

    /** Why DUM RF's Moscow Fajr may come up to 4 min after its printed time (proof/7b). */
    private const val MOSCOW_FAJR =
        "From May to August DUM RF's Fajr is sunrise − 0.30 of the night, when the sun stands up to a quarter of a " +
            "degree higher or lower than the day before; the app takes the latest of the neighbouring days so that it " +
            "holds in every year of the leap cycle (ruling R28), up to 4 min after the printed time (May, and late " +
            "July to mid-August)."

    /** Why DUM RF's Moscow Isha may come up to 5 min after its printed time (proof/7b). */
    private const val MOSCOW_ISHA =
        "From May to August DUM RF's Isha is sunset + a fraction of the night that it steps every few days (0.25 in " +
            "early May, 0.30 in June, 0.25 again by August); between its held days the app takes the largest fraction " +
            "within three days and the latest of the neighbouring days (ruling R28), up to 5 min after the printed " +
            "time (early and mid-May)."

    /** Why DUM RF's Moscow end of suhur may come up to 4 min before its printed Fajr (proof/7b). */
    private const val MOSCOW_END =
        "The end of suhur is DUM RF's own printed dawn, which from May to August moves by up to a quarter of a degree " +
            "of the sun a day; the app takes the earliest of the neighbouring days (rulings R28, R39), up to 4 min " +
            "before the printed time (May and late July to mid-August)."

    /** Why Bavly's and Zainsk's end of sahur may come up to 3 min before the printed time (proof/7b). */
    private const val EDGE_NIGHT_SAHUR =
        "On the nights either side of the white nights the dawn falls minutes from the sun's lowest point and moves by " +
            "minutes with the town's point, which DUM RT does not publish, and the daily limit lets the end of sahur " +
            "shown move at most 20 min later a night; it is kept on the safe side of the town's table, up to 3 min early."

    /** Why Zelenodolsk's and Yelabuga's times may run this far from the printed ones on one night (ruling R61, proof/7b). */
    private const val KNIFE_EDGE_NIGHT =
        "On a night whose sun sinks to within 0.01° of 18° at the town (Zelenodolsk 5 May and Yelabuga 8 August in 2026), " +
            "whether DUM RT applies its white-night rule turns on its own sun, which the app cannot match that closely, " +
            "so each time takes its safe side: Fajr at sunrise − 121, the end of sahur at the sun's lowest point and " +
            "Isha at the later of Maghrib + 90 and the 15° dusk, up to this many minutes from the time printed."

    /** Why Yelabuga's end of sahur may come up to 12 min before its printed time (ruling R61, proof/7b). */
    private const val YELABUGA_SAHUR =
        "On 8 August 2026 the sun sinks to 18.008° at Yelabuga, too close to call, so the end of sahur is the sun's " +
            "lowest point, 8 min before the dawn DUM RT prints; the next night's dawn comes 24 min later and the daily " +
            "limit lets the end of sahur shown move at most 20 min later a night, 12 min before the printed time."

    /** Why Zainsk's Fajr may come up to 3 min after its printed time (proof/7b). */
    private const val ZAINSK_FAJR =
        "Zainsk's table runs later than its app point gives (its point is not published); its Fajr keeps the later " +
            "margin its own table needs on the night of 6 May, up to 3 min late on other days."
}
