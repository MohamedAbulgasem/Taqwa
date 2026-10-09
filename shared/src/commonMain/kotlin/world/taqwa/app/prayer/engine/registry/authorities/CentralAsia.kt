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
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.lateReachKm
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
    private fun kazakhstanAt(point: GeoPoint): TimetableMethod =
        variants.fold(kazakhstanMethod) { method, variant -> if (variant.area.contains(point)) variant.change(method) else method }

    /**
     * Ruling R44 (the city-points round of 9 Oct 2026): QMDB prints a table for each of its 5,694 places (its city
     * list), every mosque of a city follows the city's, and at the app's own point for a city a few kilometres from
     * QMDB's the user's own sun began Fajr, Dhuhr, Asr, Maghrib or Isha up to a minute before the city's table (main
     * at 705c4f7f: Oral on 121 days of 2026–27, Aktobe 70, Astana 14, Pavlodar 8, at Oskemen's city point a sunrise
     * after the table's on 4). Each of [QmdbPlaces.all]' 87 places (the gate's 29 and QMDB's place for every other
     * city of the app's list) is a unit at QMDB's own point, which rides as the fixed point beside the user's
     * (ruling R15: starts the later, ends the earlier), within its R40 reach for class D, three minutes of
     * longitude (48 to 59 km), with its own band's minutes ([kazakhstanAt]). Gated at its own point and at the app's
     * point for its city (kz-qmdb.tsv). [QmdbPlaces.unanswered]' five, whose tables QMDB's API does not serve, are
     * units too, so that their own point rides beside the user's and no neighbour's figure is claimed there, but
     * they are not measured. Beyond every unit the user's own point with the entry's margins as before, the end of
     * eating at SAFE_END below 46° N (ruling R44; the bands' own margins are earlier still), claiming no figure (spec
     * §3.5). About and the site call the place by the app's name for it ([AuthorityUnit.named]).
     */
    val kazakhstanUnits = UnitSet(
        "kz.qmdb",
        (QmdbPlaces.all.map { it to true } + QmdbPlaces.unanswered.map { it to false }).map { (place, measured) ->
            val point = GeoPoint(place.lat, place.lon)
            AuthorityUnit(
                place.key, place.name, point, lateReachKm(place.lat, EntryClass.D_AUTHORITY), method = kazakhstanAt(point),
                measured = measured, named = false,
            )
        },
    ) { kazakhstanMethod.atEdge("kz.qmdb.edge", kazakhstanMethod.margins) }

    /** The end of eating's margin at and above 48° N, in seconds (fitted from Astana to Isakovka, 2026 and 2027). */
    private const val KAZAKH_NORTH_END_OF_EATING = -374

    /** The end of eating's margin from 46° N to 48° N, in seconds (fitted at Oteshqali Atambayev and Ayagoz, 2026 and 2027). */
    private const val KAZAKH_MIDDLE_END_OF_EATING = -150

    val entries = listOf(uzbekistan, kazakhstan, kyrgyzstan)
}
