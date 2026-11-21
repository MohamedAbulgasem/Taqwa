package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.DayRule
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.KemenagKabkota
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Indonesia: Kemenag (Bimas Islam), as republished by the open myQuran API (profiles-tested.md,
 * data-diyanet-kemenag; lowland HIGH, highland MEDIUM): Subuh 20°, Isya 18°, Shafi'i Asr, the
 * "2 menit" ihtiyat on each start and −2 on syuruk (second-hand: tarjih.or.id; fitted), computed at
 * the regency or city capital, sunrise and Maghrib at a horizon of −1° in lowland and −2° in
 * highland kab/kota (fitted to 0.02°; the class cannot be told from elevation: Malang is 1°, Bogor
 * 2°). Imsak = Subuh − 10 on every day checked (R14/R27: counted back from the authority's own dawn
 * as an end, not our rounded start).
 *
 * Task 7c: each of the 13 fitted kab/kota carries its own margins (`Fitter.fit("id.kemenag/<id>")`,
 * over the whole of 2026, then myQuran's only captured year, split Jan–Sep fit / Oct–Dec test,
 * safety 5 s; since 11 Oct 2026 over 2026 and 2027, below), not one shared national value. A single shared margin (the 2025-era shipping figures, kept
 * as [method]'s own for the unfitted "outside" edge) has 2 gaps over the 13 places' 4,745 days,
 * both by a handful of seconds inside the 5 s safety pad (an early Fajr at Banda Aceh on 21 Mar
 * 2026, the Ramadan-adjacent anomaly the old research notes; a late sunrise at Bukittinggi on
 * 8 Feb 2026): fitting per kab/kota removes both, and removes the squeeze a shared value forces on
 * every other place to cover the tightest one (Kota Jakarta's own sunrise had 0 s of slack left
 * under the shared margin fitted to Bukittinggi's wider swing; each place now keeps its own).
 *
 * The March equinox (track T4, 11 Oct 2026). The weekly monitor first read Kemenag's 2027 tables on
 * 5 Oct 2026, and against them the 2026-fitted margins were one minute early on 21 or 22 March 2027 at
 * eight kab/kota (Dhuhr, Maghrib or Isha at Banda Aceh, Bukittinggi, Garut, Bogor, Wonosobo, Batu,
 * Denpasar and Jayawijaya). Kemenag's own tables step there: on 21 and 22 March a printed time lags
 * the smooth curve by about a day's change (some 17 s for Dhuhr, 30 s for Maghrib and Isha), which
 * the rounding makes a whole minute on whichever day it crosses one; in 2026 the step had decided
 * most places' Maghrib and Isha margins, and the Banda Aceh Fajr gap above is the same step. The
 * method now says so ([DayRule.LAG_EQUINOX_KEMENAG]: on those two dates each start is the later of
 * the day's and the day before's, each end the earlier), and every unit's margins are fitted over
 * both published years with it (safety 5 s): never early on any of their 13,140 days, and the
 * margins the step had held up fall back (Maghrib and Isha by up to 30 s at most places), so the
 * shown minute is the printed one on more days than before; a few rise by up to 3 s, and
 * Palembang's Asr by 8 s for a day of its 2027 table. [method]'s own margins, which the edge
 * widens, are kept as they were, the largest of the 2026 fit. The step follows the calendar date, not
 * the equinox (a leap year moves the equinox to 20 March): myQuran answers 2024 and 2028 too (read
 * on 11 Oct 2026), and averaged over the eighteen places the step falls on 21 and 22 March in 2024 and
 * on 21 March alone in 2028, never on 20 March. 2028, never fitted on, is the held-out year: 0 early
 * and 0 late ends on its 6,588 place-days, worst a minute (the end of eating 2, as in the fitted years).
 *
 * Units are the 18 fitted kab/kota: the 13 of the research round and, since the monitor round of
 * 29 Sep 2026, Kota Medan, Palembang, Semarang, Surabaya and Yogyakarta, whose tables the weekly
 * monitor first met on 28 Sep 2026: at all five the printed sunrise and Maghrib sit where a −1°
 * horizon puts them, to the rounding minute, and about 4 min from where −2° would. Their whole 2026
 * was then fetched from the same source and fitted exactly as the 13 were (ruling R104).
 *
 * The round of 12 Nov 2026 ([KemenagKabkota]): 109 more kab/kota, the largest of those whose app cities had all been on
 * the edge (Kota Bekasi, Depok, Tangerang, Tangerang Selatan, Batam, Pekanbaru, Bandar Lampung, Padang, Samarinda,
 * Balikpapan, Banjarmasin, Pontianak, … and the regencies around Jakarta, Bandung, Surabaya and Semarang at their
 * seats). Each one's 2026, 2027 and 2028 were fetched whole from the same republication (the monitor's own endpoint,
 * the place name checked on every month). Each sits at the app's own GeoNames point for its kota or regency seat, as
 * the 18 do: Kemenag publishes no reference point and myQuran's JSON carries none, and recovering one from a table
 * rounded to the minute cannot tell a point's longitude from a constant offset, so the per-unit margins absorb the
 * residual level (Kota Dumai's, about 20 s later than the others', is the largest). Each one's horizon class is read
 * from its own table (five are highland, −2°: Kota Cimahi, Sukabumi and Salatiga, Kab. Bandung and Kuningan; at the
 * wrong class the fitted sunrise and Maghrib margins sit about 4.5 min from the printed ones). Fitted on 2026 alone
 * first and held to 2027 as a true held-out year, 108 of the 109 held every day (Kota Pontianak's Asr was a minute
 * early on two days of March 2027: on the equator Asr's daily change turns as the noon sun crosses overhead); the margins
 * are then fitted over 2026 and 2027 together (safety 5 s), as the 18's are, and 2028, never fitted on, is the held-out
 * year: never early on any of the round's 119,464 place-days, at most a minute after Kemenag's on every event but Asr
 * (2, on three days) and the end of eating (2, as at the 18). Radii follow rulings R103/R113 like the 18's
 * (OpenStreetMap's admin_level-5 boundaries, Overpass, 12 Nov 2026), up to 24.9 km where a regency seat lies far from
 * every neighbour (Kab. Sumenep; Indramayu 24.0), still well inside class B's [lateReachKm] of the point (about 55 km here),
 * so that the user's own sun, which bounds each start beside the point (ruling R15), adds less than the class's limit
 * at the circle's edge. Each unit's own tables are replayed across its circle (gate/id-kemenag-reach.tsv, the review
 * of 12 Nov 2026): never early and no end late; every time at most 2 min on the safe side of Kemenag's (near a
 * circle's edge, where the points' minute becomes 2), but the end of eating on Kab. Sumenep's islands east of the seat,
 * 3 on one day ([lateLimitsOf]).
 *
 * The second round of 12 Nov 2026 ([KemenagKabkota.round2]): 119 more, the largest kab/kota whose kota or regency seat
 * is an app city still on the edge (Kab. Lombok Timur, Asahan, Lombok Tengah, Langkat, Purbalingga, … and 18 kota from
 * Lubuklinggau to Sabang), the same way: each one's 2026, 2027 and 2028 fetched whole, the app's own point for the kota
 * or the seat, the horizon class read from its own table (sixteen highland, from Kab. Karo and Toraja Utara to Kota
 * Tomohon), margins fitted over 2026 and 2027 with 2028 held out. Where no other kab/kota lies within the boundary
 * query's 30 km of the point (eighteen, on islands and long coasts) the radius stops at 29.5 km. Kab. Puncak Jaya and
 * Lanny Jaya print a Subuh about 4 min before the 20° dawn at their points and an Isya about 4.5 min after the 18° dusk
 * (a dip for their height, it seems, as on the highland horizon): the fitted Fajr and Isha margins take both, and the
 * end of eating, which would follow the dawn, is moved earlier with them ([Kabkota.endOfEating]). Beyond their circles
 * the edge allowed less than they do (Isya up to 4 min before their own tables just outside, the end of eating up to
 * 4 min after), so the edge beside a unit carries that unit's own allowance with what the distance needs ([outsideAt],
 * ruling R211): 55 km around every unit, a degree around these two; the units' own tables are read on rings out to
 * there (the tools' KemenagRingTest; gate/id-kemenag-envelope.tsv holds the rings). Each unit's own tables
 * are replayed across its circle as the first round's are (gate/id-kemenag-reach.tsv): never early and no end late,
 * every start at most 2 min after Kemenag's; three circles went to 3 at Asr on a day and are cut back to where every
 * event holds (Kab. Sambas 20.0 km, Nias Selatan 21.0, Pulau Morotai 26.0), and at 29 wide ones the end of eating
 * reached 3 min before Kemenag's Subuh toward the circle's edge, the user's own dawn there coming about a minute
 * before the point's: Kab. Sumenep's exception, an earlier end and never a start ([KemenagKabkota.round2EarlierEnd]).
 * Each of the 29 would hold the 2 min at a smaller circle (16 to 27 km, probed in rings; 7c-sea.md lists them), but
 * the ring cut off would fall back to the edge's 6-7 min. The owner approved the 30 exceptions on 12 Nov 2026 (ruling
 * R186: Kab. Sumenep's and these 29), so they stand: an end of eating up to 3 min before Kemenag's, never a start.
 *
 * Rulings R103 and R113: Kemenag prints one table per kabupaten/kota and a user follows their own,
 * so a unit's circle stays inside its own kab/kota. Each radius is the distance from the unit's
 * point to the nearest boundary of a kabupaten/kota Kemenag prints separately, on OpenStreetMap's
 * administrative boundaries (29 Sep 2026, in the engine's own distance), less a 0.5 km buffer for
 * those boundaries' accuracy, rounded down to 0.1 km and never below 0.1 km, so that the unit's own
 * point still resolves to it after the app's rounding of a location to three decimals (at most
 * 0.08 km). A coastline bounds no table, so a circle may cross it (Makassar, Jayapura, Semarang,
 * Surabaya). Kota Bukittinggi's point is 0.687 km from Kab. Agam: 0.1 km, the city entry and little
 * else. Beyond a circle the edge applies as it did before the unit existed. Kota Jakarta's own core
 * (DKI Jakarta, 7.8 km: Bekasi is 8.365 km away) keeps its checked −1° horizon. Ruling R106 closes
 * ruling R46's reach unit (Jakarta's point riding as the fixed point out to 55 km with the deepest
 * horizon but without the edge's allowance for an unknown capital: a start could fall seconds
 * before a neighbouring kabupaten's own table where its seat lies further west): beyond the core the
 * edge takes over, which is never early against Jakarta's table and every neighbour's for a seat up
 * to half a degree away. It is at least as accurate as the ruling's other closure, the reach unit
 * widened by the same allowance (whose fixed point only added lateness east of Jakarta); against the
 * closed unit itself, each start is 2 min later and sunrise 2 min earlier there. Beyond every unit
 * (class D, spec §6.3) the highland horizon −2° (the deepest plausible, brief) and 2 min
 * more for a capital up to half a degree away. At a lowland kab/kota that edge puts sunrise 6–7 min
 * before Kemenag's and Maghrib 6–7 min after (about 4 min the horizon, 2 the capital's unknown
 * point, the rest the margins and the rounding), as the monitor measured at the five kota before
 * they were fitted; it stays, since a kab/kota's horizon class cannot be told without its own table
 * (Malang, 450 m up, is −1°; Bogor, 260 m, is −2°).
 */
object Kemenag {
    private val ihtiyat = EventOffsets(fajr = 2, sunrise = -2, dhuhr = 2, asr = 2, maghrib = 2, isha = 2)

    val method = TimetableMethod(
        id = "id.kemenag",
        fajrAngle = 20.0,
        isha = IshaRule.Angle(18.0),
        horizonDeg = -1.0,
        authorityMinutes = ihtiyat,
        margins = margins(start = 0, sunrise = -10, fajr = 35, dhuhr = 84, asr = 27, maghrib = 37, isha = 35),
        imsakMinutesBeforeFajr = 10,
        dayRule = DayRule.LAG_EQUINOX_KEMENAG,
    )

    val outside = method.atEdge("id.kemenag.edge", method.margins.widened(EDGE_SECONDS)).copy(horizonDeg = -2.0)

    internal class Kabkota(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val highland: Boolean,
        val radiusKm: Double,
        val margins: EventOffsets,
        /**
         * Seconds added to the end of eating (and the imsak counted back from it), never above 0: where Kemenag's own
         * Subuh runs earlier than the dawn the method reads at its Fajr angle (Kab. Puncak Jaya and Lanny Jaya, high in
         * Papua's mountains), the end must come earlier with it, as Egypt's units' own end-of-eating margins do.
         */
        val endOfEating: Int = 0,
    ) {
        init {
            require(endOfEating <= 0) { "$name: an end of eating only ever moves earlier" }
        }
    }

    internal fun m(fajr: Int, sunrise: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) =
        EventOffsets(fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, maghrib = maghrib, isha = isha)

    private val fitted = listOf(
        Kabkota("1301", "Kota Jakarta", -6.21462, 106.84513, highland = false, radiusKm = JAKARTA_CORE_KM, m(16, -9, 71, 25, 13, 14)),
        Kabkota("0119", "Kota Banda Aceh", 5.54167, 95.33333, highland = false, radiusKm = 0.9, m(12, -5, 70, 27, 12, 12)),
        Kabkota("2622", "Kota Makassar", -5.14861, 119.43194, highland = false, radiusKm = 4.0, m(13, -7, 72, 20, 10, 11)),
        Kabkota("1709", "Kota Denpasar", -8.65, 115.21667, highland = false, radiusKm = 3.3, m(9, -9, 68, 20, 9, 10)),
        Kabkota("3329", "Kota Jayapura", -2.53371, 140.71813, highland = false, radiusKm = 8.9, m(12, -4, 73, 21, 13, 14)),
        Kabkota("1634", "Kota Malang", -7.9797, 112.6304, highland = false, radiusKm = 2.6, m(8, -8, 69, 19, 7, 9)),
        Kabkota("1219", "Kota Bandung", -6.92222, 107.60694, highland = true, radiusKm = 3.8, m(8, -8, 67, 19, 9, 8)),
        Kabkota("3308", "Kab. Jayawijaya", -4.09583, 138.94806, highland = true, radiusKm = 13.3, m(9, -8, 70, 16, 9, 10)),
        Kabkota("1630", "Kota Batu", -7.87, 112.52833, highland = true, radiusKm = 3.4, m(8, -7, 69, 19, 9, 9)),
        Kabkota("0313", "Kota Bukittinggi", -0.30907, 100.37055, highland = true, radiusKm = 0.1, m(8, -10, 67, 22, 8, 6)),
        Kabkota("1222", "Kota Bogor", -6.59444, 106.78917, highland = true, radiusKm = 1.3, m(12, -5, 72, 28, 13, 14)),
        Kabkota("1208", "Kab. Garut", -7.245, 107.921, highland = true, radiusKm = 3.8, m(14, -5, 74, 27, 13, 14)),
        Kabkota("1429", "Kab. Wonosobo", -7.35889, 109.90306, highland = true, radiusKm = 9.9, m(8, -9, 69, 19, 8, 9)),
        // Monitor round (29 Sep 2026, ruling R104): five more lowland kota, fitted over the whole of 2026 like the 13.
        Kabkota("0228", "Kota Medan", 3.58333, 98.66667, highland = false, radiusKm = 3.3, m(2, -14, 63, 18, 4, 4)),
        Kabkota("0816", "Kota Palembang", -2.91673, 104.7458, highland = false, radiusKm = 0.6, m(10, -18, 64, 24, 10, 11)),
        Kabkota("1433", "Kota Semarang", -6.99306, 110.42083, highland = false, radiusKm = 7.3, m(3, -14, 63, 12, 2, 2)),
        Kabkota("1638", "Kota Surabaya", -7.24917, 112.75083, highland = false, radiusKm = 8.0, m(23, -8, 77, 36, 22, 24)),
        Kabkota("1505", "Kota Yogyakarta", -7.80139, 110.36472, highland = false, radiusKm = 1.3, m(9, -10, 67, 19, 7, 6)),
    ) + KemenagKabkota.round + KemenagKabkota.round2

    private fun unitsFor(base: TimetableMethod, idPrefix: String) = fitted.map { k ->
        AuthorityUnit(
            id = k.id, name = k.name, point = GeoPoint(k.lat, k.lon), radiusKm = k.radiusKm,
            method = base.copy(
                id = "$idPrefix.${k.id}", horizonDeg = if (k.highland) -2.0 else -1.0, margins = k.margins,
                endOfEatingMarginSeconds = base.endOfEatingMarginSeconds + k.endOfEating,
            ),
            lateLimits = lateLimitsOf[k.id].orEmpty(),
        )
    }

    /**
     * The units' own exceptions to the class limit (spec §6.3). Kab. Sumenep (the review of 12 Nov 2026, its reach rows):
     * its 24.9 km circle takes in the regency's islands east of the seat, where the user's own dawn, and the end of
     * eating with it, comes about a minute before the seat's. The end is already up to 2 min before Kemenag's imsak at
     * the point (as at the 18), and it reached 3 on one day of the sweep (one day of December 2028, near the reach's
     * eastern edge). An earlier end, never a start, the same exception as Dubai's (Gulf.kt). Approved by the owner on
     * 12 Nov 2026 with the second round's 29 (ruling R186).
     */
    private val lateLimitsOf: Map<String, List<LateLimit>> = mapOf(
        "1626" to listOf(
            LateLimit(
                3,
                "Kab. Sumenep's one table serves the whole regency, and Taqwa also keeps each place's own dawn, " +
                    "about a minute earlier on its islands east of Sumenep",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    ) + KemenagKabkota.round2EarlierEnd.associateWith { id -> listOf(earlierEndAcross(fitted.first { it.id == id }.name)) }

    /**
     * The second round's same exception ([KemenagKabkota.round2EarlierEnd]): a wide kab/kota whose one table, replayed
     * across its circle (its reach rows), showed the end of eating 3 min before Kemenag's Subuh toward the circle's edge,
     * where the user's own dawn comes about a minute before the point's, and no other event over the class limit.
     */
    private fun earlierEndAcross(name: String) = LateLimit(
        3,
        "$name's one table serves the whole ${if (name.startsWith("Kota")) "city" else "regency"}, and Taqwa also keeps " +
            "each place's own dawn, up to about a minute earlier toward the edge of its circle",
        setOf(TimedEvent.END_OF_EATING),
    )

    /**
     * The units whose own fitted allowance is more than the whole plain edge's (ruling R211, the audit of 12 Nov 2026,
     * finding F6): their print departs from the method itself, not from where the table's point lies, so the edge's
     * 2 min for an unknown capital cannot stand for it. By rule, not by name; today Kab. Puncak Jaya and Kab. Lanny
     * Jaya, whose Isya comes about 4.5 min after the 18° dusk and whose Subuh, and the end of eating with it, about
     * 4 min before the 20° dawn.
     */
    private val departingUnits: List<Kabkota> = fitted.filter { k ->
        val e = outside.margins
        k.margins.fajr > e.fajr || k.margins.dhuhr > e.dhuhr || k.margins.asr > e.asr || k.margins.maghrib > e.maghrib ||
            k.margins.isha > e.isha || k.margins.sunrise < e.sunrise ||
            method.endOfEatingMarginSeconds + k.endOfEating < outside.endOfEatingMarginSeconds
    }

    /** The ids of the units whose allowance is more than the plain edge's, which the edge carries to [DEPARTING_CARRY_KM]. */
    val departing: List<String> = departingUnits.map { it.id }

    /**
     * Ground of a unit's own kab/kota that lies beyond [CARRY_KM] of its point, where the edge carries the unit's table
     * all the same: within [radiusKm] of [point]. Listed one by one, since no kab/kota boundary is held: only ground
     * known to be the unit's and seen to need it.
     */
    class FarGround(val unit: String, val name: String, val point: GeoPoint, val radiusKm: Double)

    /**
     * Ground seen to come before its own kab/kota's table on the plain edge, each beyond [CARRY_KM] of the table's
     * point (the review of 13 Nov 2026 measured them over 2026 to 2028):
     *
     * - Bawean (the app's Sangkapura, GeoNames 1628767, on its south coast; the island lies within 16 km of it), Kab.
     *   Gresik's, 145 km north of Gresik: Asar and Isya a minute early on some days;
     * - the Kangean islands (Arjasa, Kangayan) and the Sapeken islands east of them, Kab. Sumenep's, 150 to 270 km
     *   east of Sumenep: Subuh, Zuhur, Asar and Isya 2 to 6 min early every day, Maghrib a minute or two;
     * - the Banda islands, Kab. Maluku Tengah's, 170 km south-east of Masohi: Subuh up to 4 min early;
     * - Serawai on the upper Melawi, Kab. Sintang's, 130 km east of Sintang: a minute or two early on most days;
     * - the Balantak headland, Kab. Banggai's, the east end of Sulawesi's eastern arm, 65 km east of Luwuk: Asar a
     *   minute early on some days.
     *
     * Whose each is, and where it lies, is the builder's reading, with no held source and no boundary: the island
     * grounds are drawn wide over open sea, the two on the mainland around the review's point. The list is what was
     * seen, not every such ground: a kab/kota reaching further than [CARRY_KM] east, north or south of its table's
     * point elsewhere is on the plain edge still. The carry only ever makes a start later and an end earlier.
     */
    val farGrounds: List<FarGround> = listOf(
        FarGround("1606", "Bawean", GeoPoint(-5.84525, 112.65173), radiusKm = 16.0),
        FarGround("1626", "Kangean", GeoPoint(-6.92, 115.45), radiusKm = 36.0),
        FarGround("1626", "Sapeken", GeoPoint(-7.02, 115.98), radiusKm = 40.0),
        FarGround("3105", "Banda", GeoPoint(-4.53, 129.88), radiusKm = 28.0),
        FarGround("2012", "Serawai", GeoPoint(-0.5167, 112.5333), radiusKm = 15.0),
        FarGround("2801", "Balantak", GeoPoint(-0.87, 123.36), radiusKm = 22.0),
    )

    /** How far from the point of unit [unitId] the edge carries its table: [DEPARTING_CARRY_KM] for a [departing] one. */
    fun carryKm(unitId: String): Double = if (unitId in departing) DEPARTING_CARRY_KM else CARRY_KM

    private class Carried(val unit: Kabkota, val point: GeoPoint, val km: Double, val far: List<FarGround>)

    private val carriedUnits: List<Carried> = fitted.map { k ->
        Carried(k, GeoPoint(k.lat, k.lon), carryKm(k.id), farGrounds.filter { it.unit == k.id })
    }.also { all -> require(farGrounds.all { g -> all.any { it.unit.id == g.unit } }) { "a far ground names no unit" } }

    /**
     * The edge at [user] (ruling R211). A circle stops short of the nearest other kab/kota (R103), so the ground beyond
     * it is, on every other side, still the unit's own kab/kota, and its people follow the unit's table, which Kemenag
     * computes at one point for the whole kab/kota. The plain edge ([outside]) is the method at the user's own point
     * with 2 min for an unknown capital; beside a unit the capital is known, so the edge carries that unit's own
     * allowance with what the distance from its point needs on top, wherever that is more than the plain edge's:
     *
     * - the longitude: the sun reaches a place east of the point earlier, [LONGITUDE_SECONDS] a degree, so each start
     *   needs that much more there; west of it each end (sunrise, the end of eating) needs it;
     * - the latitude: [LATITUDE_SECONDS] a degree either way, on every event but Dhuhr (the most a degree of latitude
     *   moves the dawn, Asar or Isya anywhere in Indonesia, at the solstices in the far south);
     * - [CARRY_PAD_SECONDS];
     * - less [HORIZON_SECONDS] at sunrise and Maghrib beside a lowland unit, whose table reads a horizon a degree
     *   shallower than the edge's.
     *
     * A later start and an earlier end only, never the unit's earlier Subuh (the edge's own Fajr stays, later than the
     * table's). It reaches [CARRY_KM] from every unit's point, [DEPARTING_CARRY_KM] from a [departing] unit's, and a
     * unit's listed [farGrounds]; several units in reach: the largest of each. No kab/kota boundary is held, so the
     * reach is a distance and not the kab/kota's own extent: beyond it the edge is [outside], which claims no table.
     */
    fun outsideAt(user: GeoPoint): TimetableMethod {
        val e = outside.margins
        var fajr = e.fajr
        var sunrise = e.sunrise
        var dhuhr = e.dhuhr
        var asr = e.asr
        var maghrib = e.maghrib
        var isha = e.isha
        var endOfEating = outside.endOfEatingMarginSeconds
        for (c in carriedUnits) {
            val k = c.unit
            val north = abs(user.lat - k.lat)
            val inReach = north * KM_PER_DEGREE_OF_LATITUDE <= c.km + 1.0 && distanceKm(user, c.point) <= c.km
            if (!inReach && c.far.none { distanceKm(user, it.point) <= it.radiusKm }) continue
            val east = user.lon - k.lon
            val latitude = north * LATITUDE_SECONDS
            val later = ceil(maxOf(0.0, east) * LONGITUDE_SECONDS + latitude).toInt() + CARRY_PAD_SECONDS
            val noon = ceil(maxOf(0.0, east) * LONGITUDE_SECONDS).toInt() + CARRY_PAD_SECONDS
            val earlier = ceil(maxOf(0.0, -east) * LONGITUDE_SECONDS + latitude).toInt() + CARRY_PAD_SECONDS
            val horizon = if (k.highland) 0 else HORIZON_SECONDS
            fajr = maxOf(fajr, k.margins.fajr + later)
            dhuhr = maxOf(dhuhr, k.margins.dhuhr + noon)
            asr = maxOf(asr, k.margins.asr + later)
            maghrib = maxOf(maghrib, k.margins.maghrib + later - horizon)
            isha = maxOf(isha, k.margins.isha + later)
            sunrise = minOf(sunrise, k.margins.sunrise - earlier + horizon)
            endOfEating = minOf(endOfEating, method.endOfEatingMarginSeconds + k.endOfEating - earlier)
        }
        if (fajr == e.fajr && sunrise == e.sunrise && dhuhr == e.dhuhr && asr == e.asr && maghrib == e.maghrib &&
            isha == e.isha && endOfEating == outside.endOfEatingMarginSeconds
        ) {
            return outside
        }
        return outside.copy(
            margins = EventOffsets(fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, maghrib = maghrib, isha = isha),
            endOfEatingMarginSeconds = endOfEating,
        )
    }

    val units = UnitSet("id.kemenag", unitsFor(method, "id.kemenag")) { outsideAt(it) }

    /**
     * Every held kab/kota table was read from the open myQuran API, which republishes Kemenag's figures: none from
     * Kemenag's own pages (finding F26), so the entry names the [RegistryEntry.republisher] and the app and the site
     * say whose copy the times are checked against.
     */
    val entry: RegistryEntry = single(
        id = "id.kemenag", nameKey = "authority_kemenag", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("ID"), nearby = listOf("id.muhammadiyah"),
        republisher = "myQuran",
    )

    /**
     * Muhammadiyah's Subuh at 18° (Munas Tarjih XXXI, adopted 24 Mar 2021; it changes Subuh only), with
     * Kemenag's other parameters and units: a named timetable, a large organised minority (spec §9).
     */
    val muhammadiyahMethod = method.copy(id = "id.muhammadiyah", fajrAngle = 18.0)

    val muhammadiyahUnits = UnitSet("id.muhammadiyah", unitsFor(muhammadiyahMethod, "id.muhammadiyah")) {
        outsideAt(it).copy(id = "id.muhammadiyah.edge", fajrAngle = 18.0)
    }

    val muhammadiyah: RegistryEntry = single(
        id = "id.muhammadiyah", nameKey = "authority_muhammadiyah", entryClass = EntryClass.D_AUTHORITY,
        method = muhammadiyahMethod, school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    val entries = listOf(entry, muhammadiyah)

    private const val EDGE_SECONDS = 120

    /**
     * How far from a unit's point the edge carries its table (ruling R211): as far as the plain edge's own 2 min were
     * ever meant to stand for a table's point, half a degree of longitude, 55.6 km at the equator.
     */
    const val CARRY_KM = 55.0

    /**
     * How far from a [departing] unit's point: a degree. Its print departs from the method by minutes, so ground of its
     * kab/kota past [CARRY_KM] would be minutes early on the plain edge, not seconds. No boundary is held; a degree is
     * the builder's reading of how far Kab. Puncak Jaya and Kab. Lanny Jaya reach from Mulia and Tiom, and it takes in
     * the seats of the highland regencies around them, which have no table held and may print the same dip.
     */
    const val DEPARTING_CARRY_KM = 111.0

    /** A degree of longitude is four minutes of the sun, on every event. */
    private const val LONGITUDE_SECONDS = 240.0

    /**
     * The most a degree of latitude moves a time between 11.5° S and 6.5° N: the dawn at 20°, Asar and Isya at 18° stay
     * under 144 s a degree, the two [departing] units' Isya under 151 s at the far south and about 115 s where they
     * lie. Fitted to the held tables, not derived alone: with it and [CARRY_PAD_SECONDS] every unit's 2026, 2027 and
     * 2028 hold on rings to the carry's end (the tools' KemenagRingTest).
     */
    private const val LATITUDE_SECONDS = 150.0

    /** For what the two rates leave: the sun's own day-to-day drift over the distance, and the rounding of a point. */
    private const val CARRY_PAD_SECONDS = 15

    /**
     * What the edge's horizon, a degree deeper than a lowland unit's, is worth at sunrise and Maghrib: the sun takes at
     * least 4 min over a degree of height anywhere; a quarter of a minute is left in hand.
     */
    private const val HORIZON_SECONDS = 225

    private const val KM_PER_DEGREE_OF_LATITUDE = 111.195

    /**
     * Kota Jakarta's own core (rulings R103, R113): DKI Jakarta within its point's distance to the
     * nearest neighbouring kota, Bekasi (8.365 km on OpenStreetMap's boundaries), less the buffer,
     * rounded down to 0.1 km.
     */
    private const val JAKARTA_CORE_KM = 7.8
}
