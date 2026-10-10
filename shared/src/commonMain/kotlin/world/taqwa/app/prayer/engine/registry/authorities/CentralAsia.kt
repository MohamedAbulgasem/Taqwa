package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.MethodVariant
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.Where
import world.taqwa.app.prayer.engine.registry.beyondTable
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaceList
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.single
import kotlin.math.min

/** Uzbekistan, Kazakhstan and Kyrgyzstan (research Central Asia sections). Hanafi throughout (spec §3.7). */
object CentralAsia {

    /**
     * Uzbekistan: the Muslim Board (muslim.uz). The islom.uz taqvim calls adhan-js with 15.5°/15.5°,
     * Hanafi, Maghrib = sunset + 4 (documented in code; the live site equals it, confirmed live on
     * 25-26 Sep 2026). Until some date between Jul and Sep 2026 it was Isha 15°, Dhuhr + 5,
     * Maghrib + 3 (Wayback captures, Jul 2024 - Jul 2026). Margins fitted (task 7f, `uz-board.tsv`,
     * `proof/7f-asia.md`) over both eras together, since it is not certain which one every cached
     * client sees, or exactly when the switch happened: the older rule's Dhuhr is far enough ahead
     * of the newer rule's that Dhuhr keeps a wide + 274 s margin, and sunrise a - 246 s one, driven
     * by the last old-rule capture (19 Jul 2026). Saharlik equals the 15.5° Fajr. Class D (spec
     * §6.2 a).
     */
    val uzbekistanMethod = TimetableMethod(
        id = "uz.board",
        fajrAngle = 15.5,
        isha = IshaRule.Angle(15.5),
        authorityMinutes = EventOffsets(maghrib = 4),
        margins = EventOffsets(fajr = -7, sunrise = -246, dhuhr = 274, asr = -30, maghrib = -26, isha = -69),
    )

    val uzbekistan: RegistryEntry = single(
        id = "uz.board", nameKey = "authority_uzbek_board", entryClass = EntryClass.D_AUTHORITY, method = uzbekistanMethod,
        school = AsrSchool.HANAFI, countries = setOf("UZ"),
        lateLimits = listOf(
            LateLimit(5, "the margin also covers the pre-Jul-2026 rule's earlier sunrise (task 7f); an early end is the safe side", setOf(TimedEvent.SUNRISE)),
            LateLimit(6, "the margin also covers the pre-Jul-2026 rule's Dhuhr + 5 min next to the new rule's own (task 7f)", setOf(TimedEvent.DHUHR)),
            LateLimit(4, "the margin also covers both the 15° and 15.5° Isha eras together (task 7f)", setOf(TimedEvent.ISHA)),
        ),
    )

    /**
     * Kazakhstan: QMDB (muftyat.kz), documented in its homepage code: praytimes.js ISNA 15°/15°, Hanafi,
     * the AngleBased high-latitude rule, sunrise − 3 and Dhuhr, Asr, Maghrib + 3 below 48° N, − 5 and +
     * 5 at or above it. Margins fitted (task 7f, `kz-qmdb.tsv`, `proof/7f-asia.md`) over Almaty
     * (below 48°N) and Astana (at/above it) across the whole of 2026, one set of margins covering
     * both authority-minute variants, then refitted (fix round of 3 Oct 2026) with QMDB's 2026 and
     * 2027 tables for Astana, Kokshetau, Pavlodar and Petropavl as fit rows, Almaty 2027 and Kostanay
     * held out: Astana's 2027 July Fajr (+ 8 s) and the northern cities' August Isha (+ 11 s) were
     * 1 min early at the 2026-only margins; sunrise, Asr and Maghrib moved to the fitted bounds too
     * (sunrise earlier, Asr and Maghrib later). The north-west round (6 Oct 2026) added QMDB's own
     * places north of Petropavl to its northernmost (Isakovka, 55.4° N), the west and every zone
     * without a table (Oral, Aksay, Aktobe, Khromtau, Shalqar, Atyrau, Makat, Aktau, Kyzylorda), the
     * places nearest 48° N and places either side of the curves' 0.1° steps: 27 places in all. Its fit
     * moved Asr to − 6 and Maghrib to − 14 (later, decided at Krasny Yar and Isakovka in 2027), and its
     * curves are read at each place's own latitude as well as the grid ([PlaceCurves]'s qmdb rule).
     * The end of eating's margin differs by region ([variants]). Class D.
     */
    val kazakhstanMethod = TimetableMethod(
        id = "kz.qmdb",
        fajrAngle = 15.0,
        isha = IshaRule.Angle(15.0),
        authorityMinutes = EventOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
        margins = EventOffsets(fajr = 8, sunrise = 14, dhuhr = -22, asr = -6, maghrib = -14, isha = 11),
        highLatitude = HighLatRule.Legacy("angle"),
    )

    val kazakhstan: RegistryEntry = single(
        id = "kz.qmdb", nameKey = "authority_qmdb", entryClass = EntryClass.D_AUTHORITY, method = kazakhstanMethod,
        school = AsrSchool.HANAFI, countries = setOf("KZ"), measured = true,
        lateLimits = listOf(
            LateLimit(
                8,
                "QMDB's AngleBased Fajr runs ahead of the model from about 46N, around the solstice there and from late April to " +
                    "early June in the north, more the farther north: up to 4 min from 46N to 48N, 5 to 7 from 48N to 54.4N, " +
                    "8 from 54.5N to Isakovka, QMDB's northernmost place (task 7f, fix rounds of 3 and 6 Oct 2026)",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                6,
                "the same AngleBased residual in Isha from July to mid-August, up to 5 min from Astana to Pavlodar and 6 from " +
                    "54.3N to QMDB's northernmost place, Isakovka (task 7f, fix rounds of 3 and 6 Oct 2026)",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                8,
                "the end of eating keeps clear of QMDB's AngleBased residual, so on its other days it comes up to 8 min before " +
                    "the printed Fajr at and above 48N, as far north as Isakovka, and up to 4 from 46N to 48N " +
                    "(end-of-eating audit, 3 Oct 2026; north-west round, 6 Oct 2026)",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * Kyrgyzstan: the Muftiate (muftiyat.kg), one region-wide homepage JSON fetch of 115 localities
     * (LOW): Bishkek fits Fajr ≈ 18°, Isha ≈ 16°, Hanafi Asr, Maghrib well past sunset (its JSON
     * prints both "sunset" and a later "maghrib" separately). Margins fitted (task 7f,
     * `kg-default.tsv`, `proof/7f-asia.md`) on that single archived day alone: Maghrib's + 397 s
     * margin is this large only because the Muftiate's own Maghrib runs about 7 min past sunset, not
     * because the method is loosely fitted; one day is not a season, so this stays class D and
     * unmeasured.
     */
    val kyrgyzstanMethod = TimetableMethod(
        id = "kg.default",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(16.0),
        margins = EventOffsets(fajr = -64, sunrise = -10, dhuhr = -62, asr = 47, maghrib = 397, isha = -45),
    )

    val kyrgyzstan: RegistryEntry = single(
        id = "kg.default", nameKey = "authority_kyrgyz_muftiate", entryClass = EntryClass.D_AUTHORITY,
        method = kyrgyzstanMethod, school = AsrSchool.HANAFI, countries = setOf("KG"),
    )

    /**
     * Kazakhstan from 46° N to 48° N: QMDB's offsets are still ± 3 there, but its AngleBased Fajr binds
     * around the summer solstice from about 46.15° N (north-west round, 6 Oct 2026). Kept here rather
     * than in [Regions], beside [Regions.kazakhNorth], since it is this authority's alone; declared
     * before [variants], which reads it.
     */
    private val kazakhMiddle = Where { it.lat >= 46.0 && it.lat < 48.0 }

    /**
     * QMDB's minutes are ± 5 at and above 48° N. There the end of eating also starts
     * [KAZAKH_NORTH_END_OF_EATING] before the model's dawn: QMDB's AngleBased Fajr runs ahead of
     * the model's in spring and early summer (the residual its Fajr limit records), and the fast
     * begins at QMDB's printed Fajr, so the end of eating at the plain dawn came out after it: up to
     * 4 min at Astana (29 days of 2026, the first 12 May; end-of-eating audit, 3 Oct 2026). The
     * residual grows to the north. The margin is the fitter's bound over QMDB's 2026 and 2027 tables
     * for Astana, Kokshetau, Kostanay, Pavlodar and Petropavl (54.9° N), decided by 6 May 2027 at
     * the north; Astana's 2026 table alone asked for − 221 s, which the 2027 table and every city
     * north of it break (fix round of 3 Oct 2026). The north-west round (6 Oct 2026) held it to
     * account to the country's edge: QMDB's three northernmost places, to Isakovka (55.4° N), and
     * the west at and above 48° N ask for − 366 s with the curves read at each place's own latitude
     * (decided at Isakovka on 3 May 2027), so − 374 s stands.
     *
     * From 46° N to 48° N QMDB's minutes are ± 3, but its AngleBased Fajr binds there too, around
     * the summer solstice (from about 46.15° N), and the end of eating at margin 0 came up to 3 min
     * after QMDB's printed Fajr at Atyrau, Shalqar, Ayagoz, Oteshqali Atambayev, Mamyrsu and Makat
     * (late May to mid-June, 2026 and 2027). Its margin, [KAZAKH_MIDDLE_END_OF_EATING], is the
     * fitter's bound at the band's top (Oteshqali Atambayev, QMDB's place nearest 48° N, and
     * Ayagoz), decided on 30 May 2027. Below 46° N the rule never binds, and Almaty's own bound
     * (+ 18 s) needs nothing, so the margin there stays 0: an end only ever moves earlier.
     */
    val variants = listOf(
        MethodVariant("kz.qmdb", Regions.kazakhNorth) {
            it.copy(
                authorityMinutes = EventOffsets(sunrise = -5, dhuhr = 5, asr = 5, maghrib = 5),
                endOfEatingMarginSeconds = min(it.endOfEatingMarginSeconds, KAZAKH_NORTH_END_OF_EATING),
            )
        },
        MethodVariant("kz.qmdb", kazakhMiddle) {
            it.copy(endOfEatingMarginSeconds = min(it.endOfEatingMarginSeconds, KAZAKH_MIDDLE_END_OF_EATING))
        },
    )

    /**
     * [kazakhstanMethod] with the variants of [point]'s own band ([variants]): a unit's table is QMDB's at the
     * unit's point, so its minutes and its end of eating's margin are that point's band's. The registry applies the
     * user's own band on top ([Registry]'s variants, by the user's point): each variant only moves a start later and
     * an end earlier, and keeps the earlier end of eating of the two (the min above), so where a unit reaches
     * across 46° N or 48° N both bands' times are bounded, the city's table's and the user's own.
     */
    internal fun kazakhstanAt(point: GeoPoint): TimetableMethod = when {
        Regions.kazakhNorth.contains(point) -> northMethod
        kazakhMiddle.contains(point) -> middleMethod
        else -> southMethod
    }

    private fun banded(point: GeoPoint): TimetableMethod =
        variants.fold(kazakhstanMethod) { method, variant -> if (variant.area.contains(point)) variant.change(method) else method }

    // One method per band, shared by every unit in it (5,676 units, three methods).
    private val northMethod by lazy { banded(GeoPoint(48.0, 70.0)) }
    private val middleMethod by lazy { banded(GeoPoint(47.0, 70.0)) }
    private val southMethod by lazy { banded(GeoPoint(43.0, 70.0)) }

    /**
     * Ruling R44 and the owner's decision of 9 Oct 2026 ("every QMDB place a unit"): QMDB prints a table for each
     * of its 5,694 places (its city list), a place's mosques follow its own, and the engine computed the user's own
     * point: at the app's own point for a city a few kilometres from QMDB's, Fajr, Dhuhr, Asr, Maghrib or Isha began
     * up to a minute before the city's table (main at 705c4f7f: Oral on 121 days of 2026–27, Aktobe 70, Astana 14,
     * Pavlodar 8, and at Oskemen's city point a sunrise after the table's on 4).
     *
     * Every distinct point of QMDB's list ([QmdbPlaceList], 5,676) is a unit at QMDB's own point, which rides as the
     * fixed point beside the user's (ruling R15: starts the later, ends the earlier), with its own band's minutes
     * ([kazakhstanAt]). Each reach is measured with the engine's own rule (QmdbReach in tools/timetables: the unit's
     * point beside the user's, the AngleBased curves on the grid and at both latitudes, the bands either side of
     * 46° N and 48° N): as far as every time shown stays within 3 min of the unit's own day and of the user's own
     * point alone ([QmdbPlaceList.FINGERPRINT] records what it was measured with).
     *
     * The city's table (the owner's decision of 9 Oct 2026): inside the reach of one of the app's cities' places
     * ([QmdbPlaces.appCities], 81 places for 83 of the app's 84 Kazakh cities), a user takes the city's place even
     * where a village or suburb of QMDB's list lies nearer, since a city's mosques follow the city's table; inside
     * two cities' reaches, the nearer city. Everywhere else, the nearest place whose reach holds the user, so a
     * village's user follows the village's own table and nobody a city tens of kilometres off. A city's unit
     * carries the app's own city ([AuthorityUnit.cityId]), so About names the city whose table it is.
     *
     * The 87 places whose tables are checked ([QmdbPlaces.all]: the gate's 29 and QMDB's place for every other city
     * of the app's list) are measured, gated at their own point and at the app's point for the city (kz-qmdb.tsv).
     * Every other place, the five QMDB serves no table for ([QmdbPlaces.unanswered]) among them, is a unit that
     * claims no figure. A measured place whose own tables, replayed across its reach (kz-qmdb-reach.tsv), pass the
     * entry's Fajr, Isha or end-of-eating limit carries its own ([QmdbPlaces.reachLimits], [reachLateLimits]).
     * Beyond every reach, a point table's edge ([beyondTable], rulings R44 and R45): the user's own point a minute
     * later, the end of eating at SAFE_END or the bands' own earlier margins, and no figure (spec §3.5). R45's end
     * bound, the nearest place's point, is kept within one reach only: a reach is where every time stays within 3 min
     * of the user's own point, and within three reaches the bound cost up to 12 min (the review of 10 Oct 2026), so
     * beyond every reach it never applies. About and the site call the place by the app's name for it
     * ([AuthorityUnit.named]).
     */
    val kazakhstanUnits: UnitSet by lazy {
        val measured = QmdbPlaces.all.associateBy { it.qmdbId }
        val unanswered = QmdbPlaces.unanswered.associateBy { it.qmdbId }
        // Each city's place and the app's city it is the table of (the first of the app's cities naming it).
        val cityOf = QmdbPlaces.appCities.reversed().associate { it.key to it.geonamesId }
        val limits = QmdbPlaces.reachLimits.associate { it.key to reachLateLimits(it) }
        val places = QmdbPlaceList.places.map { place ->
            val named = measured[place.qmdbId] ?: unanswered[place.qmdbId]
            val point = if (named != null) GeoPoint(named.lat, named.lon) else GeoPoint(place.lat, place.lon)
            AuthorityUnit(
                id = named?.key ?: "qmdb-${place.qmdbId}", name = named?.name ?: "QMDB ${place.qmdbId}", point = point,
                radiusKm = place.reachKm, method = kazakhstanAt(point), measured = place.qmdbId in measured, named = false,
                cityId = named?.key?.let { cityOf[it] }, lateLimits = named?.key?.let { limits[it] }.orEmpty(),
            )
        }
        val cities = places.filter { it.cityId != null }
        check(cities.size == cityOf.size) { "kz.qmdb: an app city's place is not a unit of QMDB's list" }
        // A checked place inside a city's reach (Zhenis, in Zhetysay's) is the city's: its table is held against the
        // city's times (kz-qmdb.tsv), and its own unit, which no user inside the city's reach takes, claims no figure.
        val units = places.map { if (it.measured && it.cityId == null && cityHolding(cities, it.point) != null) it.copy(measured = false) else it }
        // The user's own band, not the nearest place's, as on main: the registry applies it ([variants], by the user).
        UnitSet("kz.qmdb", units, choose = { place -> cityHolding(cities, GeoPoint(place.lat, place.lon)) }) { user ->
            val nearest = units.minBy { distanceKm(user, it.point) }
            kazakhstanMethod.beyondTable("kz.qmdb.edge", nearest.point, user, nearest.radiusKm, endsReachFactor = 1.0)
        }
    }

    /** A measured place's own limits across its reach ([QmdbPlaces.reachLimits]), each event's with its reason (R41). */
    private fun reachLateLimits(r: QmdbPlaces.ReachLimit): List<LateLimit> = listOfNotNull(
        r.fajr?.let {
            LateLimit(
                it,
                "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased " +
                    "Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so " +
                    "Fajr comes up to $it min after the place's table",
                setOf(TimedEvent.FAJR),
            )
        },
        r.isha?.let {
            LateLimit(
                it,
                "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased " +
                    "Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so " +
                    "Isha comes up to $it min after the place's table",
                setOf(TimedEvent.ISHA),
            )
        },
        r.endOfEating?.let {
            LateLimit(
                it,
                "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating " +
                    "kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so " +
                    "it comes up to $it min before the place's printed Fajr",
                setOf(TimedEvent.END_OF_EATING),
            )
        },
    )

    /** The nearest of [cities] whose reach holds [user], or null (then the nearest place of all). */
    private fun cityHolding(cities: List<AuthorityUnit>, user: GeoPoint): AuthorityUnit? {
        var best: AuthorityUnit? = null
        var bestKm = Double.MAX_VALUE
        for (city in cities) {
            val km = distanceKm(user, city.point)
            if (km <= city.radiusKm && km < bestKm) {
                best = city
                bestKm = km
            }
        }
        return best
    }

    /** The end of eating's margin at and above 48° N, in seconds (fitted from Astana to Isakovka, 2026 and 2027). */
    private const val KAZAKH_NORTH_END_OF_EATING = -374

    /** The end of eating's margin from 46° N to 48° N, in seconds (fitted at Oteshqali Atambayev and Ayagoz, 2026 and 2027). */
    private const val KAZAKH_MIDDLE_END_OF_EATING = -150

    val entries = listOf(uzbekistan, kazakhstan, kyrgyzstan)
}
