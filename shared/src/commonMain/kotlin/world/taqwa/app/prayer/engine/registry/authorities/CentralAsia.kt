package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.Alongside
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
import world.taqwa.app.prayer.engine.registry.OwnTable
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.Where
import world.taqwa.app.prayer.engine.registry.beyondEveryUnit
import world.taqwa.app.prayer.engine.registry.data.KyrgyzLocalities
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaceList
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaces
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.ridingBeyond
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.tablesAtTheirPoints
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
     * by the old rule's captures. Saharlik equals the 15.5° Fajr. Class D (spec §6.2 a).
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
     * The Board's Tashkent table is printed for Tashkent (uz-board.tsv; rulings R30, R40 and R44): within a minute's reach of
     * it (about 21 km) its point rides beside the user's, so the app is never before it there and no end comes after it
     * ([world.taqwa.app.prayer.engine.registry.tablesAtTheirPoints]). On main, at the app's own Tashkent, 4.4 km south-west
     * of it, a sunrise came a minute after its table's (the city-point audit of 10–11 Oct 2026). No figure is claimed,
     * there or elsewhere.
     */
    val uzbekistanUnits: UnitSet = tablesAtTheirPoints(
        uzbekistan.id, EntryClass.A, uzbekistanMethod,
        listOf(
            OwnTable(
                "tashkent", "Tashkent", GeoPoint(41.2995, 69.2401),
                listOf(
                    LateLimit(
                        6,
                        "the margin also covers the pre-Jul-2026 rule's earlier sunrise (task 7f), and within a minute's reach of " +
                            "Tashkent's point the earlier of its sunrise and the user's own is shown (rulings R30 and R44): up " +
                            "to 6 min before its table's; an early end is the safe side",
                        setOf(TimedEvent.SUNRISE),
                    ),
                ),
            ),
        ),
        measured = false,
    )

    /**
     * Kazakhstan: QMDB (muftyat.kz), documented in its homepage code: praytimes.js ISNA 15°/15°, Hanafi,
     * the AngleBased high-latitude rule, sunrise − 3 and Dhuhr, Asr, Maghrib + 3 below 48° N, − 5 and +
     * 5 at or above it. Margins fitted (task 7f, `kz-qmdb.tsv`, `proof/7f-asia.md`) over Almaty
     * (below 48°N) and Astana (at/above it) across the whole of 2026, one set of margins covering
     * both authority-minute variants, then refitted (fix round of 3 Oct 2026) with QMDB's 2026 and
     * 2027 tables for Astana, Kokshetau, Pavlodar and Petropavl as fit rows, Almaty 2027 and Kostanay
     * held out: Astana's 2027 Fajr (+ 8 s) and the northern cities' Isha (+ 11 s) were
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
     * Kyrgyzstan: the Spiritual Administration of Muslims of Kyrgyzstan, the Muftiate (muftiyat.kg; the state-recognised
     * Hanafi muftiate, its majority inferred, LOW in the research). Its calendar page prints a table for each of its 115
     * localities, for any range of dates, and prints the same month-day times every year: its 2026 and 2027 tables are
     * one table (kg-default.tsv, all 115 localities captured 12 Nov 2026). The method is rebuilt from them: Fajr 18°,
     * Isha 16°, Hanafi Asr, the sun's place read once a day at its transit ([SunModel.CLASSIC_NOON]) on 2023's sun
     * ([fixedTableYear], the year whose sun fits best: within a printed minute's rounding at every locality, its 2019,
     * 2027 and 2031 phases a little worse), Dhuhr the transit rounded to the minute, sunrise, sunset, Fajr and Isha
     * rounded down, Maghrib its own sunset + 7 min on every day of every locality ([authorityMinutes]). The Muftiate
     * gives no coordinates; each locality is a unit at the point its own table is computed for ([kyrgyzstanUnits]).
     *
     * Margins fitted on 58 localities' tables (kg-default.tsv's fit rows, their 2027 tables too, which repeat 2026's) and
     * held out on the other 57. Dhuhr takes [KYRGYZ_DHUHR] whatever the fit
     * asks: the Muftiate's Dhuhr is the transit of 2023's sun rounded to the nearest minute, so its printed minute can be
     * up to half a minute before the transit, and a year's own transit is up to 23 s from 2023's through the leap
     * cycle (2024–2035); so Dhuhr is never before the sun's own transit, and comes a minute after the Muftiate's printed
     * minute on most days. Until 12 Nov 2026 the margins were fitted on one archived day at Bishkek, with the year's own
     * sun, and the Dhuhr shown came a minute before the Muftiate's on about 170 days a year (the engine survey of
     * 11 Nov 2026). Class B at its localities: the method is rebuilt, a year with Ramadan at 115 places is never early
     * on held-out tables, but the points are recovered, not published.
     */
    val kyrgyzstanMethod = TimetableMethod(
        id = "kg.default",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(16.0),
        sunModel = SunModel.CLASSIC_NOON,
        fixedTableYear = KYRGYZ_TABLE_YEAR,
        authorityMinutes = EventOffsets(maghrib = 7),
        margins = EventOffsets(fajr = -37, sunrise = -8, dhuhr = KYRGYZ_DHUHR, asr = 72, maghrib = -52, isha = -50),
        endOfEatingMarginSeconds = -9,
    )

    val kyrgyzstan: RegistryEntry = single(
        id = "kg.default", nameKey = "authority_kyrgyz_muftiate", entryClass = EntryClass.B,
        method = kyrgyzstanMethod, school = AsrSchool.HANAFI, countries = setOf("KG"), measured = true,
        lateLimits = listOf(
            LateLimit(
                3,
                "within a locality's reach, a minute of longitude (about 21 km), Taqwa shows the later start and the earlier " +
                    "end of the locality's table and the user's own sun, and where a neighbouring locality's reach holds the " +
                    "place too, of that locality's table as well (ruling R205), so toward the reach's edge and beside a " +
                    "neighbour a start comes up to 3 min after the table and an end up to 3 min before it (each locality's " +
                    "own tables replayed across its reach, kg-default-reach.tsv, 13 Nov 2026)",
                setOf(TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB),
            ),
            LateLimit(
                4,
                "within a locality's reach, a minute of longitude (about 21 km), Taqwa shows the later start and the earlier " +
                    "end of the locality's table and the user's own sun: up to 3 min toward the reach's edge north and south " +
                    "of the table's point; and where a neighbouring locality's reach holds the place too, of that " +
                    "locality's table as well (ruling R205), so in ground two localities share Fajr and Isha come up to " +
                    "4 min after the place's own table and the end of eating up to 4 min before its Fajr (each locality's " +
                    "own tables replayed across its reach, kg-default-reach.tsv, 13 Nov 2026)",
                setOf(TimedEvent.FAJR, TimedEvent.ISHA, TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * Ruling R44, as for QMDB's places ([kazakhstanUnits]): every locality of the Muftiate's list
     * ([KyrgyzLocalities.all], 115) is a unit at the point its own table is computed for, which rides as the fixed point
     * beside the user's (ruling R15: starts the later, ends the earlier), out to a minute of longitude (R40's class A
     * reach, about 21 km); a user takes the nearest locality whose reach holds them, its name and its figure, and
     * rides the others whose reach holds them too (R205, below). East or west of a table's point
     * the user's own sun moves a time by at most a minute; north or south it moves Fajr, Isha and the end of eating
     * more, so each locality's own tables are replayed across its reach (kg-default-reach.tsv, generateReachRows, the
     * Kyrgyz round's review of 12 Nov 2026): never early, and after the table toward the edge and beside a neighbour
     * (or an end before it) by up to the entry's [LateLimit]; each unit's own figure is its stamp row. Each locality's table is also
     * checked at its own point (kg-default.tsv), and the app's 32 Kyrgyz cities, each inside one, at the app's own
     * points. Aravan's table is computed about 52 km south of the town ([KyrgyzLocalities.aravanTown]): the town is a
     * unit of its own whose fixed point is that table's point, so a user there is never before Aravan's table nor
     * before the town's own sun, and its sunrise, Isha and end of eating carry their own limits. Beyond
     * every reach, the method at the user's own point a minute later, the end of eating at SAFE_END
     * ([beyondEveryUnit]), with no figure (class D there), and beside it the nearest locality's own day at its own
     * point, with any other locality about as near (ruling R260, the second audit's G1, 14 Nov 2026: just past a
     * reach the edge alone was a minute before that locality's printed Maghrib, Isha and Asr on some days, Ramadan
     * 1448 among them, and ended eating a minute after its printed Fajr; [ridingBeyond]: out to five minutes of
     * longitude from the locality's point, about 103 km; farther, the edge alone). Until 12 Nov 2026 the Bishkek table
     * alone was a unit (tablesAtTheirPoints), with the rest of the country computed at the user's own point.
     *
     * Ruling R205 (the audit of 12 Nov 2026; the Lead's decision, pending the owner's confirmation, and built as the
     * never-early reading meanwhile): R44's "the nearest alone" does not hold for Kyrgyzstan. The Muftiate prints by locality and draws no boundaries, and its localities stand closer than their
     * reaches are wide (Suzak and Manas 7 km apart, Bishkek, Sokuluk and Manas airport within 25 km of each other), so a
     * user inside two reaches may live in either locality and follow either table; with the nearest alone such a place
     * was shown before the other locality's printed table (the audit's points: Maghrib a minute early on up to 226 days
     * of a year, Fajr up to 2 min early, the end of eating up to 2 min after the neighbour's printed Fajr; all the
     * shared ground swept before the rider, 2027's tables: Fajr up to 2 min early on up to 277 days at a place, Maghrib
     * up to 2 on up to 319, Isha up to 2 on up to 330, the end of eating up to 2 min after the neighbour's printed Fajr
     * on up to 334, and beside Aravan town Fajr up to 5 min and Asr and Maghrib up to 3). So, as Diyanet's districts
     * (R142), ESA's cities (R165) and Oman's localities (R167): the nearest locality still holds the place, and every
     * other locality whose reach holds the user rides beside it ([TimetableMethod.envelopePoints]: every start the
     * latest and every end the earliest of those tables' points; one method serves them all). Aravan town rides as its
     * table's point, and where it is the unit it keeps that point as its fixed point. The cost is lateness against the
     * place's own table where a neighbour's is later, at a locality's own point too where a neighbour's reach holds it
     * (kg-default-reach.tsv and each unit's stamp row): in ground two localities share, Fajr, Isha and the end of
     * eating by up to 4 min where it was 3 (the entry's [LateLimit]), every other time within the 3 it was; and in the
     * one strip where the neighbour is Aravan town, whose table is computed 52 km to the south, Fajr by up to 7 min in
     * Kara-Suu's part and 6 in Osh's, Asr 5 and 4, Maghrib 4 in Kara-Suu's, and in the town's own north-east sunrise 4
     * and the end of eating 7 (those units' own limits; no city of the app's list lies in the strip, the west of Osh
     * city does; whether Aravan's table should ride that far at all, 52 km from where it is computed, is a question
     * left with the owner, docs/BUILD-LOG.md). kg-default-envelope.tsv holds every such place to the neighbours' tables, and the overlapSweep task
     * replays every locality's table on a 1.5 km lattice of all the shared ground.
     */
    val kyrgyzstanUnits: UnitSet by lazy {
        // R205's cost beside Aravan town: the Muftiate's Aravan table, computed about 52 km south of the town, rides in the
        // ground the town's reach shares with Osh's and Kara-Suu's (the town is 25 km from Osh's point, 37 from Kara-Suu's).
        fun besideAravanTown(name: String, fajr: Int, asr: Int, maghrib: Int?): List<LateLimit> {
            val where = "in the strip of $name's reach that Aravan town's reach holds too (within about 21 km of the town), " +
                "the Muftiate's Aravan table rides beside $name's (ruling R205), and that table is computed about 52 km " +
                "south of the town (0.47° of latitude)"
            return listOfNotNull(
                LateLimit(
                    fajr,
                    "$where, where the summer dawn is later: Fajr comes up to $fajr min after $name's own table there " +
                        "(kg-default-reach.tsv, 13 Nov 2026)",
                    setOf(TimedEvent.FAJR),
                ),
                LateLimit(
                    asr,
                    "$where, where the winter afternoon is longer: Asr comes up to $asr min after $name's own table there " +
                        "(kg-default-reach.tsv, 13 Nov 2026)",
                    setOf(TimedEvent.ASR),
                ),
                maghrib?.let {
                    LateLimit(
                        it,
                        "$where, where the winter sun sets later: Maghrib comes up to $it min after $name's own table there " +
                            "(kg-default-reach.tsv, 13 Nov 2026)",
                        setOf(TimedEvent.MAGHRIB),
                    )
                },
            )
        }
        val besideAravan = mapOf(
            "kara-suu" to besideAravanTown("Kara-Suu", fajr = 7, asr = 5, maghrib = 4),
            "osh" to besideAravanTown("Osh", fajr = 6, asr = 4, maghrib = null),
        )
        val localities = KyrgyzLocalities.all.map { l ->
            val point = GeoPoint(l.lat, l.lon)
            AuthorityUnit(
                l.key, l.name, point, lateReachKm(l.lat, EntryClass.A), named = false,
                lateLimits = besideAravan[l.key].orEmpty(),
            )
        }
        val aravan = localities.single { it.id == "aravan" }
        val town = KyrgyzLocalities.aravanTown
        val aravanTown = AuthorityUnit(
            town.key, town.name, GeoPoint(town.lat, town.lon), lateReachKm(town.lat, EntryClass.A),
            method = kyrgyzstanMethod.copy(fixedPoint = aravan.point), named = false,
            lateLimits = listOf(
                LateLimit(
                    4,
                    "the Muftiate's Aravan table is computed about 52 km south of the town (0.47° of latitude), and in the " +
                        "north-east of the town's reach Kara-Suu's and Osh's tables ride beside it (ruling R205): sunrise " +
                        "comes up to 4 min before the Aravan table's there, the earliest of the three tables and the " +
                        "place's own sun (kg-default-reach.tsv, 13 Nov 2026)",
                    setOf(TimedEvent.SUNRISE),
                ),
                LateLimit(
                    5,
                    "the Muftiate's Aravan table is computed about 52 km south of the town (0.47° of latitude), so the user's " +
                        "own later summer dusk is the start: Isha comes up to 4 min after the table's at the town from late " +
                        "April to August, and up to 5 north of it within the town's reach (kg-default.tsv, " +
                        "kg-default-reach.tsv, 12 Nov 2026)",
                    setOf(TimedEvent.ISHA),
                ),
                LateLimit(
                    7,
                    "the Muftiate's Aravan table is computed about 52 km south of the town (0.47° of latitude), so the user's " +
                        "own earlier summer dawn is the end of eating: it comes up to 4 min before the table's Fajr at the town " +
                        "from late April to August, up to 6 north of it within the town's reach, and up to 7 in the " +
                        "north-east of it, where Kara-Suu's and Osh's earlier Fajr rides too (ruling R205; kg-default.tsv, " +
                        "kg-default-reach.tsv, 13 Nov 2026)",
                    setOf(TimedEvent.END_OF_EATING),
                ),
            ),
        )
        val all = localities + aravanTown
        UnitSet(
            "kg.default",
            all,
            around = { user, unit, placed ->
                // R205: every other locality whose reach holds the user, at the point its table is computed for.
                val others = all.filter { it.id != unit.id && distanceKm(user, it.point) <= it.radiusKm }
                    .map { it.method?.fixedPoint ?: it.point }
                    .filter { it != placed.fixedPoint }
                if (others.isEmpty()) placed else placed.copy(envelopePoints = (placed.envelopePoints + others).distinct())
            },
        ) { user ->
            // R260: past every reach, the nearest locality's own day (and any about as near) beside the edge's.
            val edge = kyrgyzstanMethod.beyondEveryUnit("kg.default.edge")
            val riding = ridingBeyond(all, user)
            if (riding.isEmpty()) edge else edge.copy(alongside = riding.map { Alongside(it.point, it.method ?: kyrgyzstanMethod) })
        }
    }

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
     * 4 min at Astana (29 days of 2026; end-of-eating audit, 3 Oct 2026). The
     * residual grows to the north. The margin is the fitter's bound over QMDB's 2026 and 2027 tables
     * for Astana, Kokshetau, Kostanay, Pavlodar and Petropavl (54.9° N), decided in the north;
     * Astana's 2026 table alone asked for − 221 s, which the 2027 table and every city
     * north of it break (fix round of 3 Oct 2026). The north-west round (6 Oct 2026) held it to
     * account to the country's edge: QMDB's three northernmost places, to Isakovka (55.4° N), and
     * the west at and above 48° N ask for − 366 s with the curves read at each place's own latitude
     * (decided at Isakovka), so − 374 s stands.
     *
     * From 46° N to 48° N QMDB's minutes are ± 3, but its AngleBased Fajr binds there too, around
     * the summer solstice (from about 46.15° N), and the end of eating at margin 0 came up to 3 min
     * after QMDB's printed Fajr at Atyrau, Shalqar, Ayagoz, Oteshqali Atambayev, Mamyrsu and Makat
     * (late May to mid-June, 2026 and 2027). Its margin, [KAZAKH_MIDDLE_END_OF_EATING], is the
     * fitter's bound at the band's top (Oteshqali Atambayev, QMDB's place nearest 48° N, and
     * Ayagoz). Below 46° N the rule never binds, and Almaty's own bound
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
     * Every other table whose reach holds the user (ruling R249, the owner's ruling of 13 Nov 2026, which replaces
     * the nearest place alone of 9 Oct 2026, as ruling R205 did for the Kyrgyz Muftiate): QMDB draws no boundary
     * between its places and their reaches overlap, so a user inside several may follow any of those tables, and the
     * nearest one alone began a prayer up to 3 min before a neighbour's (the audit of 12 Nov 2026). The place followed
     * and named stays the one above; every other place whose reach holds the user rides beside it as that place's own
     * day at its own point ([riding], [TimetableMethod.alongside]: its own band's minutes and its curves at its own
     * latitude, exactly what its own point shows), so every start is the latest of those tables' and the user's own,
     * and sunrise and the end of eating the earliest. Each place carries the reach it already had (ruling R250): the
     * distance its own table was measured to carry alone, not widened, since a wider one would make a place wait for
     * tables QMDB's own places nearer by already answer for.
     *
     * The 87 places whose tables are checked ([QmdbPlaces.all]: the gate's 29 and QMDB's place for every other city
     * of the app's list) are measured, gated at their own point and at the app's point for the city (kz-qmdb.tsv).
     * Every other place, the five QMDB serves no table for ([QmdbPlaces.unanswered]) among them, is a unit that
     * claims no figure. A measured place whose own tables, replayed across its reach with the other places' tables
     * riding (kz-qmdb-reach.tsv, ruling R252), pass the entry's Fajr, Isha or end-of-eating limit, or class D's for
     * sunrise, Dhuhr, Asr or Maghrib, carries its own for those events ([QmdbPlaces.reachLimits], [reachLateLimits]).
     * Beyond every reach, a point table's edge with no end bound ([beyondEveryUnit], rulings R44 and R45): the user's
     * own point a minute later, the end of eating at SAFE_END or the bands' own earlier margins, and no figure (spec
     * §3.5); within three reaches the nearest place's end bound had cost up to 12 min (the review of 10 Oct 2026).
     * About and the site call the place by the app's name for it ([AuthorityUnit.named]).
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
        // The user's own band, not a place's, as on main: the registry applies it ([variants], by the user).
        lateinit var set: UnitSet
        set = UnitSet(
            "kz.qmdb", units,
            choose = { place -> cityHolding(cities, GeoPoint(place.lat, place.lon)) },
            around = { user, unit, placed ->
                val riders = riding(user, unit, set.holding(user))
                if (riders.isEmpty()) placed else placed.copy(alongside = placed.alongside + riders.map { Alongside(it.point, it.method ?: kazakhstanAt(it.point)) })
            },
        ) {
            kazakhstanMethod.beyondEveryUnit("kz.qmdb.edge")
        }
        set
    }

    /**
     * The places whose own days ride beside [unit]'s for a user at [user] (ruling R249): every other of [holders],
     * the places whose reach holds the user, less those whose day can never be the one shown (ruling R251).
     *
     * South of 46° N every place keeps one method ([southMethod]: QMDB's AngleBased rule does not bind there, so the
     * twilight curves are flat and no minute changes with the band), and each time is the sun's at the place's
     * latitude less four minutes a degree of longitude. Over a reach's span of latitude each event moves one way
     * from south to north on any one day, so a place with a neighbour to its north and one to its south, each at
     * least [RIDE_CLEAR] of longitude west of it, never starts a prayer after both, and with two such neighbours to
     * its east never ends one before both: its day is bounded by theirs on every side and need not be computed.
     *
     * The neighbours are the other places of the band whose reach holds the user, on either side. Where the place
     * followed and the user are in the band themselves, the place's point and the user's own bound it too, from the
     * west alone: the day shown is read at both, each start the later of the two, so no start shown comes before
     * either's. They are no bound from the east. Sunrise, sunset and the end of eating are the earlier of the two,
     * but Maghrib ends at the day's Isha and Dhuhr at its Asr (`PrayerDay.ends`), which are the later of the two, so
     * a place whose Isha came before the day's own could still end Maghrib first. Counted on both sides (as built on
     * 13 Nov 2026), such a place was set aside and Maghrib's end, or Dhuhr's, stood a minute late beside the full
     * ride's on 194 of 872,899 southern place-days of 2027, at 18 of 2,939 points; no time shown moved, and the app
     * reads Fajr's and Asr's ends alone (the review of 14 Nov 2026, which also ran this rule against every holder
     * riding: 2,957 points, 1,079,305 days, the whole day equal).
     *
     * [RIDE_CLEAR] of longitude is 2.4 s of the sun's time; where an event's time turns from south to north inside
     * the span (near an equinox for the day's ends, a solstice for Asr) it exceeds both ends' by 0.2 s at most
     * (measured over 0.7° of latitude at every tenth of a degree from 40° N to 46° N, every day of the year).
     *
     * From 46° N the AngleBased curves are read on a tenth-of-a-degree grid and change with the latitude in steps,
     * and the bands' own minutes differ: every place whose reach holds the user rides there, with nothing set aside.
     */
    internal fun riding(user: GeoPoint, unit: AuthorityUnit, holders: List<AuthorityUnit>): List<AuthorityUnit> {
        val others = holders.filter { it.id != unit.id }
        if (others.isEmpty()) return others
        val south = others.filter { it.method === southMethod }
        if (south.isEmpty()) return others
        val east = south.map { it.point }
        val west = if (unit.method === southMethod && kazakhstanAt(user) === southMethod) east + unit.point + user else east
        return others.filter { it.method !== southMethod || !boundedOnEverySide(it.point, west, east) }
    }

    /**
     * Whether [west] holds a point north and one south of [point] at least [RIDE_CLEAR] to its west, and [east] one
     * north and one south at least as far to its east.
     */
    private fun boundedOnEverySide(point: GeoPoint, west: List<GeoPoint>, east: List<GeoPoint>): Boolean =
        boundedFrom(point, west) { it <= point.lon - RIDE_CLEAR } && boundedFrom(point, east) { it >= point.lon + RIDE_CLEAR }

    /** Whether [by] holds a point at or north of [point] and one at or south of it, each on the side [beyond] takes its longitude for. */
    private inline fun boundedFrom(point: GeoPoint, by: List<GeoPoint>, beyond: (Double) -> Boolean): Boolean {
        var north = false
        var south = false
        for (other in by) {
            if (!beyond(other.lon)) continue
            if (other.lat >= point.lat) north = true
            if (other.lat <= point.lat) south = true
            if (north && south) return true
        }
        return false
    }

    /**
     * A measured place's own limits across its reach ([QmdbPlaces.reachLimits]), each event's with its reason (R41):
     * what the place's own table and the user's own point give toward the reach's edge, and the wait for every other
     * place's table whose reach holds the user too (ruling R249), as the gate measured them together (ruling R252).
     */
    private fun reachLateLimits(r: QmdbPlaces.ReachLimit): List<LateLimit> {
        val across = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026, and again on " +
            "14 Nov 2026 with every other QMDB place whose reach holds the ground riding beside it, ruling R249)"
        // Said as it is (the review of 14 Nov 2026): what rides is each place's day as the engine computes it at the
        // place's own point, which is QMDB's own table there only where one is held (87 of the 5,676 places).
        val wait = "the wait for every other QMDB place whose reach holds the user (that place's own day as Taqwa " +
            "computes it, held against QMDB's table for it where one is held)"
        fun sun(minutes: Int?, event: TimedEvent, what: String, says: String): LateLimit? = minutes?.let {
            LateLimit(it, "$across, the later of the place's $what and the user's own and $wait add up, so $says", setOf(event))
        }
        return listOfNotNull(
            r.fajr?.let {
                LateLimit(
                    it,
                    "$across, QMDB's AngleBased Fajr residual at the place's own table, the later of the place's dawn and the " +
                        "user's own and $wait add up, so Fajr comes up to $it min after the place's table",
                    setOf(TimedEvent.FAJR),
                )
            },
            r.sunrise?.let {
                LateLimit(
                    it,
                    "$across, the earlier of the place's sunrise and the user's own and $wait (the earliest sunrise of them) " +
                        "add up, so sunrise comes up to $it min before the place's printed sunrise",
                    setOf(TimedEvent.SUNRISE),
                )
            },
            sun(r.dhuhr, TimedEvent.DHUHR, "noon", "Dhuhr comes up to ${r.dhuhr} min after the place's table"),
            sun(r.asr, TimedEvent.ASR, "afternoon", "Asr comes up to ${r.asr} min after the place's table"),
            sun(r.maghrib, TimedEvent.MAGHRIB, "sunset", "Maghrib comes up to ${r.maghrib} min after the place's table"),
            r.isha?.let {
                LateLimit(
                    it,
                    "$across, QMDB's AngleBased Isha residual at the place's own table, the later of the place's dusk and the " +
                        "user's own and $wait add up, so Isha comes up to $it min after the place's table",
                    setOf(TimedEvent.ISHA),
                )
            },
            r.endOfEating?.let {
                LateLimit(
                    it,
                    "$across, the end of eating kept clear of QMDB's AngleBased Fajr, the earlier of the place's dawn and the " +
                        "user's own and $wait (the earliest end of them) add up, so it comes up to $it min before the " +
                        "place's printed Fajr",
                    setOf(TimedEvent.END_OF_EATING),
                )
            },
        )
    }

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

    /** The least longitude, in degrees, between a place and the neighbours that bound its day ([riding]): 2.4 s of the sun's time. */
    private const val RIDE_CLEAR = 0.01

    /** The end of eating's margin at and above 48° N, in seconds (fitted from Astana to Isakovka, 2026 and 2027). */
    private const val KAZAKH_NORTH_END_OF_EATING = -374

    /** The end of eating's margin from 46° N to 48° N, in seconds (fitted at Oteshqali Atambayev and Ayagoz, 2026 and 2027). */
    private const val KAZAKH_MIDDLE_END_OF_EATING = -150

    /** The year whose sun the Kyrgyz Muftiate's one table is computed on ([kyrgyzstanMethod]). */
    private const val KYRGYZ_TABLE_YEAR = 2023

    /**
     * Kyrgyz Dhuhr's margin, in seconds: never before the date's own transit, whose distance from 2023's is at most
     * 23 s from 2024 to 2035 ([kyrgyzstanMethod]).
     */
    private const val KYRGYZ_DHUHR = 30

    val entries = listOf(uzbekistan, kazakhstan, kyrgyzstan)
}
