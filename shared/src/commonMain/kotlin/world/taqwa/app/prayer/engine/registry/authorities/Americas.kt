package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.OwnTable
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TableEnvelope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.cautious
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.convention
import world.taqwa.app.prayer.engine.registry.pointTable
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.tablesAtTheirPoints

/**
 * The United States, Canada, Australia and New Zealand (research North America and Oceania
 * sections; spec §6.1–§6.2).
 */
object Americas {

    private val safe = margins(start = SAFE_START, sunrise = SAFE_SUNRISE)

    // United States (spec §6.2 b: a majority convention with the MWL 1986 minutes).

    /**
     * The United States: FCNA/ISNA 15°/15° (36 of 42 named mosques, 39 of 43 Mawaqit tables, 73 of 93
     * Madina tables; FCNA's current advice of 27–29 Oct 2017, reaffirmed 18 Sep 2024), no binding
     * authority. Standard Asr (spec §3.7). The mosque tables that follow 15°/15° are its floors (Task 7g,
     * 35 tables from New York to California): many add their own minutes (Zuhr up to + 5 at Irving and
     * MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS ± 2 in the safe
     * direction), and the app is never before any of them. Margins fitted on 14 of the tables and widened
     * by the other 21's excess (proof.md); the end of eating before every table's Fajr (none prints an
     * imsak).
     */
    val usa: RegistryEntry = single(
        id = "us.isna", nameKey = "authority_isna", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "us.isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0),
            margins = margins(start = 154, sunrise = -98, fajr = 71, dhuhr = 314, maghrib = 266, isha = 150),
            endOfEatingMarginSeconds = -99,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("US", "PR", "VI", "GU", "AS", "MP", "UM"), measured = true,
        lateLimits = listOf(
            LateLimit(
                8,
                US_FLOORS + " Zuhr up to 7 min after the tables that add nothing (8 at Dar Al Noor's and East Cobb's), " +
                    US_ENVELOPE + " 8.",
                setOf(TimedEvent.DHUHR),
            ),
            LateLimit(
                9,
                US_FLOORS + " Maghrib up to 6 min after the tables that add nothing at their mosques, " + US_REACH + " 7, " +
                    US_ENVELOPE + " 9.",
                setOf(TimedEvent.MAGHRIB),
            ),
            LateLimit(
                7,
                US_FLOORS + " Asr, a minute or two after the angle at some tables, up to 5 min after the others' at their " +
                    "mosques, " + US_REACH + " 6, " + US_ENVELOPE + " 7.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                8,
                US_FLOORS + " Isha, a minute or two after the angle at some tables (ADAMS's Isha 2 min later by its rule), up " +
                    "to 5 min after the others' at their mosques, " + US_REACH + " 7 (MEC's), " + US_ENVELOPE + " 8.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                7,
                US_FLOORS + " ADAMS moves Fajr 2 min earlier (its safe direction): Fajr up to 4 min after its, " + US_REACH +
                    " 6, " + US_ENVELOPE + " 7.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                6,
                US_FLOORS + " ADAMS moves sunrise 2 min earlier (its safe direction): sunrise, never after its, up to 4 min " +
                    "before the others' at their mosques, " + US_REACH + " 5, " + US_ENVELOPE + " 6.",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                8,
                US_FLOORS + " ADAMS moves its dawn 2 min earlier (its safe direction): the end of eating, never after its " +
                    "Fajr, up to 4 min before the others' at their mosques, " + US_REACH + " 5, " + US_ENVELOPE + " 8.",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * The US mosques' own tables, each printed for the mosque (us-isna.tsv; rulings R30, R40 and R44): within a minute's
     * reach of the nearest (about 18–24 km) its point rides beside the user's, so the app is never before that table
     * there, nor any end after it ([tablesAtTheirPoints]). On main, 6 to 7 km from them, the app's own Houston, Irving and
     * Plano had a Fajr or a Zuhr a minute before ISGH's, ICI's and Plano Masjid's tables in 13, 8 and 1 cells of 2026,
     * and Sterling a sunrise or an end of eating a minute after ADAMS's in 75 (the city-point audit of 10–11 Oct 2026).
     * Beyond every reach the convention at the user's own point, as before.
     */
    val usaUnits: UnitSet = tablesAtTheirPoints(
        "us.isna", EntryClass.A, usa.method!!,
        listOf(
            OwnTable("adams", "ADAMS Center, Sterling", GeoPoint(39.037, -77.383)),
            OwnTable("dar-al-hijrah", "Dar Al-Hijrah, Falls Church", GeoPoint(38.8614, -77.1467)),
            OwnTable("dar-al-noor", "Dar Al Noor, Manassas", GeoPoint(38.6767, -77.3703)),
            OwnTable("icnv", "Islamic Center of Northern Virginia, Fairfax", GeoPoint(38.8459, -77.3413)),
            OwnTable("al-iman", "Al-Iman Mosque, Astoria", GeoPoint(40.7684, -73.9116)),
            OwnTable("tawheed", "Tawheed Center, Detroit", GeoPoint(42.3436, -83.2227)),
            OwnTable("iagd", "IAGD, Rochester Hills", GeoPoint(42.6356, -83.1495)),
            OwnTable("ecic", "East Cobb Islamic Center, Marietta", GeoPoint(33.97849, -84.48726)),
            OwnTable("mcc-chicago", "MCC, Chicago", GeoPoint(41.9603, -87.7291)),
            OwnTable("isgh", "ISGH, Houston", GeoPoint(29.7369, -95.4251)),
            OwnTable("ici", "Islamic Center of Irving", GeoPoint(32.8427, -97.0107)),
            OwnTable("plano-masjid", "Plano Masjid", GeoPoint(33.059901, -96.750624)),
            OwnTable("iioc", "IIOC, Anaheim", GeoPoint(33.8517, -117.8887)),
            OwnTable("mca", "MCA, Santa Clara", GeoPoint(37.3772, -121.9595)),
            OwnTable("masjid-manhattan", "Masjid Manhattan", GeoPoint(40.7081, -74.0050)),
            OwnTable("bay-ridge", "86 St Islamic Center, Bay Ridge", GeoPoint(40.6235, -74.0299)),
            OwnTable("jamaica", "Jamaica Muslim Center", GeoPoint(40.7126, -73.7957)),
            OwnTable("dar-us-salaam", "Dar-us-Salaam, College Park", GeoPoint(39.0150, -76.9113)),
            OwnTable("mcc-silver-spring", "MCC, Silver Spring", GeoPoint(39.1054, -77.0034)),
            OwnTable("dearborn", "American Moslem Society, Dearborn", GeoPoint(42.3043, -83.1436)),
            OwnTable("unity-center", "Muslim Unity Center, Bloomfield Hills", GeoPoint(42.605, -83.3158)),
            OwnTable("at-taqwa", "Masjid At-Taqwa, Ann Arbor", GeoPoint(42.2499, -83.6610)),
            OwnTable("mec", "MEC, Morton Grove", GeoPoint(42.0381, -87.7708)),
            OwnTable("mosque-foundation", "Mosque Foundation, Bridgeview", GeoPoint(41.7229, -87.8030)),
            OwnTable("orland-park", "The Prayer Center of Orland Park", GeoPoint(41.5896, -87.8724)),
            OwnTable("maryam", "Maryam Islamic Center, Sugar Land", GeoPoint(29.59449, -95.683845)),
            OwnTable("mishkah", "Mishkah Masjid, Houston", GeoPoint(29.6779852, -95.3969742)),
            OwnTable("iant", "IANT, Richardson", GeoPoint(32.9378, -96.7309)),
            OwnTable("frisco", "Islamic Center of Frisco", GeoPoint(33.1725659, -96.8347209)),
            OwnTable("epic", "East Plano Islamic Center", GeoPoint(33.05, -96.7487)),
            OwnTable("icoi", "Islamic Center of Irvine", GeoPoint(33.6965, -117.765)),
            OwnTable("isoc", "ISOC, Garden Grove", GeoPoint(33.7555, -117.9584)),
            OwnTable("icsgv", "ICSGV, Rowland Heights", GeoPoint(33.9955, -117.884)),
            OwnTable("king-fahad", "King Fahad Mosque, Culver City", GeoPoint(34.0119, -118.4102)),
            OwnTable("ocif", "OCIF, Mission Viejo", GeoPoint(33.6206, -117.6758)),
        ),
    )

    /** The Chicago 18° block's tables, each printed for its mosque (us-chicago.tsv). */
    private val chicagoBlockTables = listOf(
        OwnTable("villa-park", "Islamic Foundation, Villa Park", GeoPoint(41.8898, -87.989)),
        OwnTable("makki", "Makki Masjid, Chicago", GeoPoint(41.9704, -87.7144)),
        OwnTable("darussalam", "Masjid DarusSalam, Lombard", GeoPoint(41.9028, -88.047)),
    )

    /**
     * The Chicago metro: a block of 18°/15° with Hanafi Asr (Islamic Foundation Villa Park prints
     * "Fajr 18 degrees Isha 15 degrees Asr Hanfi"; Makki Masjid and Masjid DarusSalam Lombard the same),
     * with the US floors' minutes (its own tables' Zuhr up to + 5 and Maghrib up to + 3.5 lie within them).
     */
    private val chicagoBlock = convention(
        "us.chicago.eighteen", "timetable_chicago_eighteen",
        usa.method!!.copy(id = "us.chicago.eighteen", fajrAngle = 18.0),
        2,
        measuredAt = chicagoBlockTables.map { it.point },
    )

    /**
     * The 18° block's three tables, each its mosque's (us-chicago.tsv), as [usaUnits]; the block stays measured within
     * class C's reach of them, as before.
     */
    val chicagoBlockUnits: UnitSet =
        tablesAtTheirPoints(chicagoBlock.id, EntryClass.A, chicagoBlock.method, chicagoBlockTables, measuredBeyond = true)

    /**
     * The Chicago metro: its mosques differ on Fajr (Task 7g, seven tables held): the 18° block (Villa
     * Park, Makki, DarusSalam Lombard) and 15°/15° (MCC Chicago, MEC Morton Grove, Mosque Foundation
     * Bridgeview, the Prayer Center of Orland Park), all with Hanafi Asr, Arab mosques included (spec
     * §3.7). Cautious: Fajr no earlier than the 15° mosques', the end of eating no later than the 18°
     * block's dawn.
     */
    val chicago = cautious(
        id = "us.chicago", members = listOf(usa.asMember(1), chicagoBlock),
        school = AsrSchool.HANAFI, schoolKnown = true, scope = Scope.CITY, countries = setOf("US"), measured = true,
        named = { it == "us.isna" },
        lateLimits = listOf(
            LateLimit(
                34,
                "Chicago's mosques differ on Fajr: the 15° mosques' Fajr, never before it, runs up to 31 min after the " +
                    "18° block's (Villa Park, Makki, DarusSalam) at their mosques; " + CHICAGO_REACH + " 32, " +
                    CHICAGO_ENVELOPE + " 34.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                34,
                "Chicago's mosques differ on Fajr: the end of eating is the 18° block's dawn, never after it, up to 32 min " +
                    "before the 15° mosques' Fajr (MCC, MEC, Mosque Foundation, Orland Park) at their mosques; " +
                    CHICAGO_REACH + " 33, " + CHICAGO_ENVELOPE + " 34.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                7,
                CHICAGO_FLOORS + " Zuhr and Maghrib up to 6 min at the mosques; " + CHICAGO_REACH + " 7.",
                setOf(TimedEvent.DHUHR, TimedEvent.MAGHRIB),
            ),
            LateLimit(
                7,
                CHICAGO_FLOORS + " Asr up to 5 min at the mosques; " + CHICAGO_REACH + " 6, " + CHICAGO_ENVELOPE + " 7.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(7, CHICAGO_FLOORS + " Isha up to 5 min at the mosques; " + CHICAGO_REACH + " 7.", setOf(TimedEvent.ISHA)),
            LateLimit(4, CHICAGO_FLOORS + " Sunrise up to 3 min before theirs at the mosques; " + CHICAGO_REACH + " 4.", setOf(TimedEvent.SUNRISE)),
        ),
    )

    // Canada.

    /**
     * Montreal (12 of 16 mosques) and Ottawa (6 of 6) follow ISNA 15°/15°, Standard Asr (spec §3.6). As in
     * the US, their 15°/15° tables are its floors (Task 7g, 18 Mawaqit tables): margins fitted on nine and
     * held out on the other nine, which showed none early (proof.md).
     */
    val canadaIsna: RegistryEntry = single(
        id = "ca.isna", nameKey = "authority_isna", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0),
            margins = margins(start = 71, sunrise = -160, fajr = 110, dhuhr = 10, maghrib = 131, isha = 83),
            endOfEatingMarginSeconds = -103,
        ),
        school = AsrSchool.STANDARD, scope = Scope.CITY, countries = setOf("CA"), measured = true,
        lateLimits = listOf(
            LateLimit(5, CANADA_FLOORS + " Fajr, Maghrib and sunrise up to 5 min from the others'.",
                setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.MAGHRIB)),
            LateLimit(
                6,
                CANADA_FLOORS + " The end of eating up to 5 min from the others', and beside every other mosque's table within " +
                    "class D's reach of the user (the city-point round of 11 Oct 2026), up to 6.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                6,
                CANADA_FLOORS + " Asr up to 4 min after the others' at their mosques, and within a minute's reach of a mosque, " +
                    "where its point rides beside the user's (rulings R30 and R44), up to 5, and beside every other mosque's " +
                    "table within class D's reach of the user (the city-point round of 11 Oct 2026), up to 6.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(4, CANADA_FLOORS + " Isha up to 4 min after the others'.", setOf(TimedEvent.ISHA)),
        ),
    )

    /**
     * Montreal's and Ottawa's mosques' own tables (ca-isna.tsv), as [usaUnits]. On main, 8 of the 82 app cities within
     * 20 km of the mosque nearest to them had a start before its table or an end after it on some days (Snowdon a
     * sunrise after Masjid Taiba's, 1.7 km off; the city-point audit of 10–11 Oct 2026).
     */
    val canadaIsnaUnits: UnitSet = tablesAtTheirPoints(
        "ca.isna", EntryClass.A, canadaIsna.method!!,
        listOf(
            OwnTable("al-andalous", "Al Andalous Islamic Center, Saint-Laurent", GeoPoint(45.5140, -73.6760)),
            OwnTable("aisha", "Aisha Islamic Center, Montreal", GeoPoint(45.4920, -73.5650)),
            OwnTable("taiba", "Masjid Taiba, Cote-des-Neiges", GeoPoint(45.5012, -73.6317), listOf(isnaReach(7, "Fajr", TimedEvent.FAJR))),
            OwnTable("west-island", "Islamic Center of West Island", GeoPoint(45.4790, -73.8130), listOf(isnaReach(6, "Fajr", TimedEvent.FAJR))),
            OwnTable("ccml", "CCML, Longueuil", GeoPoint(45.5314, -73.4869)),
            OwnTable("al-rawdah", "Masjid Al-Rawdah, Cartierville", GeoPoint(45.5294, -73.7225)),
            OwnTable("oma", "Ottawa Main Mosque", GeoPoint(45.4015, -75.7413)),
            OwnTable("abrar", "Abrar Masjid, Ottawa", GeoPoint(45.3516, -75.7955), listOf(isnaReach(5, "Isha", TimedEvent.ISHA))),
            OwnTable("ottawa-south", "Ottawa South Mosque", GeoPoint(45.3949, -75.6812)),
            OwnTable("al-madinah", "Al-Madinah Center, Montreal", GeoPoint(45.4950, -73.5770)),
            OwnTable("dar-al-arkam", "Dar Al Arkam, Montreal", GeoPoint(45.5629, -73.5950)),
            OwnTable("badr", "Centre Islamique Badr, Montreal", GeoPoint(45.5989, -73.5909)),
            OwnTable("laval", "Centre Culturel Islamique de Laval", GeoPoint(45.5476, -73.7543)),
            OwnTable("as-salam", "Masjid As-Salam, Montreal", GeoPoint(45.4972, -73.5731)),
            OwnTable("canadian-islamic-center", "Canadian Islamic Center, Dollard-des-Ormeaux", GeoPoint(45.4926, -73.8380)),
            OwnTable("aim", "Abdullah Issa Masjid, Nepean", GeoPoint(45.3428, -75.7089)),
            OwnTable("kanata", "Kanata Muslim Association", GeoPoint(45.3561, -75.9284)),
            OwnTable("jami-omar", "Jami Omar, Nepean", GeoPoint(45.3116, -75.8284)),
        ),
    )

    /**
     * The Islamic Foundation of Toronto: "Fajr Dawn 15 dg / Isha Night 13.5° dg", Hanafi; Dhuhr and
     * Maghrib about a minute after transit and sunset. Margins fitted on its 2026 table (Task 7g:
     * January to June, widened by July to December's excess), its faults left out (proof.md).
     */
    val ift: RegistryEntry = single(
        id = "ca.ift", nameKey = "authority_ift", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.ift", fajrAngle = 15.0, isha = IshaRule.Angle(13.5), asrModel = AsrModel.EXACT_MOMENT,
            authorityMinutes = EventOffsets(dhuhr = 1, maghrib = 1),
            margins = margins(start = 33, sunrise = 27, fajr = 82, dhuhr = -44, asr = 51, isha = 82),
        ),
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("CA"),
    )

    /**
     * The Islamic Institute of Toronto: 15°, Maghrib about sunset + 2, Isha = Maghrib + 90, Standard Asr
     * about 2 min after the exact one. Margins fitted on its 2026 table as IFT's.
     */
    val iit: RegistryEntry = single(
        id = "ca.iit", nameKey = "authority_iit", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.iit", fajrAngle = 15.0, isha = IshaRule.AfterMaghrib(90), asrModel = AsrModel.EXACT_MOMENT,
            authorityMinutes = EventOffsets(maghrib = 2),
            margins = margins(start = 18, sunrise = -54, fajr = 78, dhuhr = 49, asr = 107),
        ),
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("CA"),
        lateLimits = listOf(
            LateLimit(
                4,
                "IIT's own table drifts over its year: its sunrise from 2 min before to 2 min after the sun's, its " +
                    "Maghrib from sunset to sunset + 2.5 (and Isha, 90 min after it, with it); the margins hold it " +
                    "never early all year, so in spring they run up to 4 min.",
                setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB, TimedEvent.ISHA),
            ),
        ),
    )

    /**
     * MAC Masjid Toronto: plain 15°/15°, nearest, no Dhuhr + 1, Asr 1–2 min after the exact one. Margins
     * fitted on its 2026 table as IFT's.
     */
    val mac: RegistryEntry = single(
        id = "ca.mac", nameKey = "authority_mac_toronto", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.mac", fajrAngle = 15.0, isha = IshaRule.Angle(15.0), asrModel = AsrModel.DAILY_0H_UT,
            margins = margins(start = -22, sunrise = 20, fajr = -19, dhuhr = -23, asr = 24, isha = -12),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("CA"),
    )

    /**
     * Toronto and the GTA: no majority (spec §3.6): the Islamic Foundation of Toronto (most followed
     * in the mockup), the Islamic Institute of Toronto and MAC Masjid Toronto. School not known:
     * the later Asr.
     */
    val toronto = cautious(
        id = "ca.toronto", members = listOf(ift.asMember(1), iit.asMember(2), mac.asMember(3)),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.CITY, countries = setOf("CA"), measured = true,
        named = { true },
        lateLimits = listOf(
            LateLimit(
                4,
                "None of the three tables prints an imsak, so each Fajr is where its fast begins; the end is the " +
                    "earliest member's own dawn less its margin, up to 4 min before the tables' Fajr.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                7,
                TORONTO_SPREAD + " West of the mosques the user's own sun is later than the tables' points (at Milton, " +
                    "the reach's western end, by 2.5 min), and Fajr and Isha shown follow it: up to 6 and 7 min.",
                setOf(TimedEvent.FAJR, TimedEvent.ISHA),
            ),
            LateLimit(
                6,
                TORONTO_SPREAD + " West of the mosques the user's own sun is later (at Milton by 2.5 min), and Dhuhr, " +
                    "Asr and Maghrib shown follow it: up to 5, 6 and 5 min.",
                setOf(TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB),
            ),
            LateLimit(
                3,
                TORONTO_SPREAD + " Sunrise is the earliest of the three, each a little before its own table's.",
                setOf(TimedEvent.SUNRISE),
            ),
        ),
    )

    /**
     * Each Toronto table is its own mosque's (ruling R30), so each is a unit at the mosque, reaching
     * as far as class D's three minutes east to west (ruling R40: about 60 km, the GTA from Oshawa to
     * Oakville): there the table's point rides as the method's fixed point beside the user's
     * (starts the later, ends the earlier), for the cautious members and for someone who follows one
     * of them alone. Mosque points from the research's fits (IFT Scarborough, IIT Scarborough, MAC
     * Masjid Toronto downtown).
     */
    val torontoUnits: List<UnitSet> = listOf(
        Triple(ift, "Islamic Foundation of Toronto", GeoPoint(43.7980, -79.2417)),
        Triple(iit, "Islamic Institute of Toronto", GeoPoint(43.8187, -79.2298)),
        Triple(mac, "MAC Masjid Toronto", GeoPoint(43.6555, -79.3858)),
    ).map { (entry, name, point) -> pointTable(entry, name, point) }

    /** Each Canadian family's own tables, each printed for its mosque (ca-cautious.tsv). */
    private val eighteenTables = listOf(
        OwnTable("al-omari", "Al Omari Masjid, Edmonton", GeoPoint(53.6, -113.441)),
        OwnTable("al-ansar", "Masjid Al Ansar, Edmonton", GeoPoint(53.5606, -113.53)),
        OwnTable("al-farooq", "Masjid Al Farooq, Edmonton", GeoPoint(53.4675, -113.4302)),
        OwnTable("surrey-jamea", "Surrey Jamea Masjid", GeoPoint(49.1342, -122.8787)),
    )
    private val fifteenTables = listOf(OwnTable("imam-malik", "Markaz Al Imam Malik, Edmonton", GeoPoint(53.608, -113.526)))
    private val ishaNinetyTables = listOf(OwnTable("calgary-sw", "Calgary Islamic Centre SW", GeoPoint(51.0395, -114.1685)))

    /**
     * The Edmonton mosques' 18° family (Al Omari, Al Ansar, Al Farooq; Surrey Jamea in BC): Fajr 18°, no
     * earlier than a quarter of the night before sunrise (or, where the sun misses 18°, the MWL proportion
     * from 45°), Isha the earlier of 18° (Al Farooq's; Al Omari and Al Ansar 17°) and a sixth of the night
     * (PlaceCurves); Zuhr up to + 5 and Maghrib up to + 6 (Al Omari from August). Margins fitted on their
     * 2026 tables (Task 7g).
     */
    private val eighteenFamily = convention(
        "ca.eighteen", "timetable_eighteen_degrees",
        TimetableMethod(
            id = "ca.eighteen", fajrAngle = 18.0, isha = IshaRule.Angle(18.0),
            margins = margins(start = 169, sunrise = -105, fajr = 171, dhuhr = 264, maghrib = 316, isha = 95),
            endOfEatingMarginSeconds = -300,
        ),
        1,
        measuredAt = eighteenTables.map { it.point },
    )

    /**
     * Edmonton's 15° family (Markaz Al Imam Malik): Fajr 15°, no earlier than a quarter of the night before
     * sunrise, Isha 16.5°, no later than 28 % of the night after sunset (PlaceCurves). Margins fitted on its
     * 2026 table (Task 7g).
     */
    private val fifteenFamily = convention(
        "ca.fifteen", "timetable_fifteen_degrees",
        TimetableMethod(
            id = "ca.fifteen", fajrAngle = 15.0, isha = IshaRule.Angle(16.5),
            margins = margins(start = -23, sunrise = 18, fajr = -15, asr = 9, maghrib = -17, isha = -30),
            endOfEatingMarginSeconds = -204,
        ),
        2,
        measuredAt = fifteenTables.map { it.point },
    )

    /**
     * Calgary Islamic Centre SW's family: Fajr 15°, no earlier than 23.5 % of the night before sunrise
     * (PlaceCurves), Isha = Maghrib + 90. Margins fitted on its 2026 table (Task 7g).
     */
    private val ishaNinety = convention(
        "ca.isha90", "timetable_isha_ninety",
        TimetableMethod(
            id = "ca.isha90", fajrAngle = 15.0, isha = IshaRule.AfterMaghrib(90),
            margins = margins(start = 21, sunrise = -98, fajr = 124, dhuhr = 6, asr = 86),
            endOfEatingMarginSeconds = -336,
        ),
        3,
        measuredAt = ishaNinetyTables.map { it.point },
    )

    /**
     * Each family's own tables as [usaUnits]; each family stays measured within class C's reach of them, as before. On
     * main the app's own Calgary, 6 km east of Calgary Islamic Centre SW, had a Fajr a minute before its table on 8 days,
     * and Surrey's an Isha a minute before Surrey Jamea's on 1 (the city-point audit of 10–11 Oct 2026).
     */
    val canadaFamilyUnits: List<UnitSet> = listOf(
        tablesAtTheirPoints(eighteenFamily.id, EntryClass.A, eighteenFamily.method, eighteenTables, measuredBeyond = true),
        tablesAtTheirPoints(fifteenFamily.id, EntryClass.A, fifteenFamily.method, fifteenTables, measuredBeyond = true),
        tablesAtTheirPoints(ishaNinety.id, EntryClass.A, ishaNinety.method, ishaNinetyTables, measuredBeyond = true),
    )

    /**
     * Canada outside Toronto, Montreal and Ottawa: no majority (3 of 10 Madina tables 15°/15°, 2 MWL in
     * Alberta, 5 all different; each Prairie and BC mosque its own summer rule). Members (Task 7g), each
     * with its own summer rule fitted on its tables: the Edmonton mosques' 18° family (the most followed of
     * the tables held), Edmonton's 15° family, and Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90. The plain ISNA and MWL presets are not members: where the
     * sun only just reaches their angles, in May and August on the Prairies, their Fajr falls near midnight
     * and would end the fast hours before any table's. School not known: the later Asr.
     */
    val canada = cautious(
        id = "ca.cautious",
        members = listOf(eighteenFamily, fifteenFamily, ishaNinety),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT, countries = setOf("CA"), measured = true,
        named = { false },
        lateLimits = listOf(
            LateLimit(
                36,
                CANADA_FAMILIES + " Fajr, the 15° families', never before theirs, runs up to 36 min after the 18° family's.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                39,
                CANADA_FAMILIES + " The end of eating, the 18° family's dawn, never after it, runs up to 38 min before the " +
                    "15° families' Fajr at their mosques; " + FAMILIES_REACH + " 39.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                77,
                CANADA_FAMILIES + " Isha is the latest family's: Imam Malik's 16.5° (up to 28 % of the night) in spring and " +
                    "autumn, the 18° family's in winter, up to 77 min after Calgary Islamic Centre SW's Maghrib + 90 and " +
                    "the 18° family's spring Isha.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                6,
                CANADA_FAMILIES + " Each keeps its own tables' minutes (Al Farooq's Zuhr + 5, Al Omari's Maghrib + 6 from " +
                    "August): Zuhr and Maghrib up to 5 min after the others' at their mosques; " + FAMILIES_REACH + " 6.",
                setOf(TimedEvent.DHUHR, TimedEvent.MAGHRIB),
            ),
            LateLimit(
                5,
                CANADA_FAMILIES + " Each keeps its own tables' minutes (Asr up to 3 min after the exact one): Asr up to 5 min " +
                    "after the others'.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                5,
                CANADA_FAMILIES + " Sunrise, the earliest family's, up to 4 min before the others' at their mosques; " +
                    FAMILIES_REACH + " 5.",
                setOf(TimedEvent.SUNRISE),
            ),
        ),
    )

    // Australia and New Zealand.

    /**
     * Lakemba Mosque (LMA), 1448 PDF, 349 days fitted, no method stated: Fajr 18° rounded down, sunrise
     * rounded down, Maghrib sunset rounded up, Isha = Maghrib + 90, Dhuhr noon + 2..7 and Asr from 12 min
     * before to 22 min after the Shafi'i Asr, both moving month by month: per-month offsets (seconds after
     * its latest minutes, + 7 and + 22) and margins fitted on the whole table (Task 7g).
     */
    val lakemba: RegistryEntry = single(
        id = "au.lma", nameKey = "authority_lakemba", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "au.lma", fajrAngle = 18.0, isha = IshaRule.AfterMaghrib(90),
            authorityMinutes = EventOffsets(dhuhr = 7, asr = 22),
            margins = margins(start = 0, sunrise = -19, fajr = -25, asr = 7, maghrib = 5, isha = 14), endOfEatingMarginSeconds = -17,
            monthlyOffsets = mapOf(
                Prayer.DHUHR to intArrayOf(-226, -208, -157, -111, -136, -201, -214, -142, -184, -70, -66, -87),
                Prayer.ASR to intArrayOf(-861, -1772, -1783, -1200, -789, -804, -1142, -1431, -995, -356, -80, -76),
            ),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("AU"),
        lateLimits = listOf(
            LateLimit(
                15,
                "LMA's Asr runs from 12 min before to 22 min after the Shafi'i Asr on a smooth seasonal curve no rule here " +
                    "reproduces; per-month offsets keep it never early, up to 15 min after it within its fastest-moving " +
                    "months (October, December and January).",
                setOf(TimedEvent.ASR),
            ),
        ),
    )

    /** Lakemba Mosque's table is printed for Lakemba (ruling R30). */
    val lakembaUnits: UnitSet = pointTable(lakemba, "Lakemba Mosque", GeoPoint(-33.92, 151.0756))

    /**
     * Diyanet's Sydney table (au-cautious.tsv's "Diyanet Sydney", au.cautious's second member, the TURKEY preset) is
     * printed for a point Diyanet does not publish: read at Lakemba's in the gate, it could as well be the city's own
     * (Sydney's centre, where Diyanet's city tables usually sit) or the Turkish community's (the Gallipoli Mosque,
     * Auburn). The city-point round of 11 Oct 2026 (the owner's rule: safe against every plausible point): within class
     * D's reach of any of the three the member rides them all beside the user's own point ([TableEnvelope], every start
     * the latest and every end the earliest), so no time is before Diyanet's table, nor any end after it, wherever its
     * point is among them. The first round found the sunrise a minute after the table at Fairfield, Cabramatta,
     * Liverpool, Casula and Prestons, 13 to 19 km west of Lakemba, on 1 to 6 days. Beyond them, and everywhere else the
     * TURKEY preset is followed, the method at the user's own point as before, measured as before.
     */
    val sydneyDiyanetUnits: UnitSet = run {
        val points = listOf(
            AuthorityUnit("sydney-lakemba", "Lakemba", GeoPoint(-33.92, 151.0756), 0.0, named = false, endsBoundAtUserPoint = true),
            AuthorityUnit("sydney-centre", "Sydney", GeoPoint(-33.86785, 151.20732), 0.0, named = false, endsBoundAtUserPoint = true),
            AuthorityUnit("sydney-auburn", "Auburn", GeoPoint(-33.8494, 151.0331), 0.0, named = false, endsBoundAtUserPoint = true),
        )
        val envelope = TableEnvelope(points, EntryClass.D_AUTHORITY)
        UnitSet(Diyanet.other.id, emptyList(), measuredBeyond = true) { user -> envelope.beyond(user, Diyanet.other.method!!) }
    }

    /**
     * Australia: no dominant table (HIGH): Lakemba's, and Diyanet's per-suburb tables (the TURKEY preset ± 1).
     * Maghrib is capped at LMA's (the most followed) where the two spread, 5–6 min before Diyanet's
     * (its sunset + 7), as the spec's cautious Maghrib (Task 7g's gate, proof.md).
     */
    val australia = cautious(
        id = "au.cautious", members = listOf(lakemba.asMember(1), Diyanet.other.asMember(2)),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.COUNTRY, countries = setOf("AU"), measured = true,
        named = { true },
        lateLimits = listOf(
            LateLimit(
                18,
                "Asr is the later member's: LMA's, on its per-month offsets up to 15 min after its own table, and the " +
                    "TURKEY preset's with its allowance for Diyanet's daily model (subtask 7b): up to 18 min after the " +
                    "earlier table's.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                10,
                "Isha is the later member's, the TURKEY preset's 17° or LMA's Maghrib + 90: up to 10 min after the other's.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                9,
                "Sunrise is the earlier member's, the TURKEY preset's (the sun's less 7 min): up to 8 min before LMA's at " +
                    "Lakemba, and up to 9 at the app's own Sydney, 12 km east of it, where the user's own sun rises earlier.",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                4,
                "Zuhr is the later member's: the TURKEY preset's (+ 5) and LMA's (+ 2 to + 7 by month), up to 4 min after " +
                    "the earlier table's.",
                setOf(TimedEvent.DHUHR),
            ),
            LateLimit(
                2,
                "Fajr and the end of eating keep each member's own margins: up to 2 min from the tables'.",
                setOf(TimedEvent.FAJR, TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /** New Zealand: FIANZ, 20 cities (fitted, MEDIUM-HIGH): 18°/18°, Shafi'i Asr, no offsets, nearest. Auckland is held. */
    val fianzMethod = TimetableMethod(id = "nz.fianz", fajrAngle = 18.0, isha = IshaRule.Angle(18.0), margins = safe)

    /**
     * FIANZ's Auckland table, printed for Auckland (ruling R30), its reach by class D (ruling R40). Margins fitted
     * on its March and June 2026 pages, widened by September's Fajr excess to the whole table's (Task 7g).
     */
    val fianzUnits = UnitSet(
        "nz.fianz",
        listOf(
            AuthorityUnit(
                "auckland", "Auckland", GeoPoint(-36.84853, 174.76349), lateReachKm(-36.84853, EntryClass.D_AUTHORITY),
                fianzMethod.copy(
                    margins = margins(start = -13, sunrise = 18, fajr = -2, asr = -16, maghrib = -1, isha = -4),
                    endOfEatingMarginSeconds = 18,
                ),
            ),
        ),
    ) { fianzMethod.atEdge("nz.fianz.edge", margins(start = 60 + SAFE_START, sunrise = -60 + SAFE_SUNRISE)) }

    val newZealand: RegistryEntry = single(
        id = "nz.fianz", nameKey = "authority_fianz", entryClass = EntryClass.D_AUTHORITY, method = fianzMethod,
        school = AsrSchool.STANDARD, countries = setOf("NZ"),
    )

    val entries = listOf(usa, chicago, canadaIsna, ift, iit, mac, toronto, canada, lakemba, australia, newZealand)

    /** Why the US times run later than many of its 15°/15° tables (Task 7g's gate, proof.md). */
    private const val US_FLOORS = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and " +
        "MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app " +
        "is never before any of them:"

    /** Why the US times run later still within a mosque's reach (the city-point audit of 10–11 Oct 2026). */
    private const val US_REACH = "and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and " +
        "R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to"

    /**
     * Why the US times run later still where several mosques' tables reach a place (the city-point round of 11 Oct 2026,
     * the owner's rule: rather late, never early): every other mosque table within class D's reach of the user rides
     * beside the nearest's ([TableEnvelope]), each start the latest and each end the earliest of them all.
     */
    private const val US_ENVELOPE = "and beside every other mosque's table within class D's reach of the user (ruling R40's, " +
        "about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor " +
        "any end after one), up to"

    /** One ISNA mosque's own exception within its reach (ruling R41): [what] up to [minutes] min after its table there. */
    private fun isnaReach(minutes: Int, what: String, event: TimedEvent) = LateLimit(
        minutes,
        "Within a minute's reach of this mosque its point rides beside the user's (rulings R30 and R44): east of it the " +
            "time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk. $what up to " +
            "$minutes min after its table there.",
        setOf(event),
    )

    /** Why Montreal's and Ottawa's times run later than many of their tables (Task 7g's gate, proof.md). */
    private const val CANADA_FLOORS = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, " +
        "Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up " +
        "to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it " +
        "(Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any:"

    /** Why Canada's cautious times run later than its tables (Task 7g's gate, proof.md). */
    private const val CANADA_FAMILIES = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each " +
        "with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre " +
        "SW's 15° with Isha = Maghrib + 90); the app is never before any of them."

    /** Why Chicago's times run later still at the app's own points near its tables (the city-point audit of 10–11 Oct 2026). */
    private const val CHICAGO_REACH = "within a minute's reach of a mosque its point rides beside the user's (rulings R30 and " +
        "R44), and at the app's own cities near them, east, west, north or south of the mosque, up to"

    /** Why Chicago's times run later still where several of its mosques' tables reach a place (the city-point round of 11 Oct 2026). */
    private const val CHICAGO_ENVELOPE = "and beside every other mosque's table of the same member within class D's reach of the " +
        "user (about 61 km), where a user may still follow it (the owner's rule: never before any of them), up to"

    /** Why Canada's cautious times run later still at the app's own points near the families' tables (the same audit). */
    private const val FAMILIES_REACH = "within a minute's reach of a family's table its point rides beside the user's (rulings " +
        "R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to"

    /** Why Chicago's times run later than its tables (Task 7g's gate, proof.md). */
    private const val CHICAGO_FLOORS = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US " +
        "mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less:"

    /** Why Toronto's cautious times run later than its three tables (Task 7g's gate, proof.md). */
    private const val TORONTO_SPREAD = "Toronto's three tables round and offset their times irregularly against their own " +
        "rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the " +
        "year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the " +
        "latest printed time at the mosques."

}
