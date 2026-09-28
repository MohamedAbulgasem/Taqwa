package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

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
 * over the whole of myQuran's only captured year, 2026, split Jan–Sep fit / Oct–Dec test, safety
 * 5 s), not one shared national value. A single shared margin (the 2025-era shipping figures, kept
 * as [method]'s own for the unfitted "outside" edge) has 2 gaps over the 13 places' 4,745 days,
 * both by a handful of seconds inside the 5 s safety pad (an early Fajr at Banda Aceh on 21 Mar
 * 2026, the Ramadan-adjacent anomaly the old research notes; a late sunrise at Bukittinggi on
 * 8 Feb 2026): fitting per kab/kota removes both, and removes the squeeze a shared value forces on
 * every other place to cover the tightest one (Kota Jakarta's own sunrise had 0 s of slack left
 * under the shared margin fitted to Bukittinggi's wider swing; each place now keeps its own).
 *
 * Units are the 18 fitted kab/kota: the 13 of the research round and, since the monitor round of
 * 29 Sep 2026, Kota Medan, Palembang, Semarang, Surabaya and Yogyakarta, whose tables the weekly
 * monitor first held on 28 Sep 2026 (Sep–Oct 2026, 61 days each): at all five the printed sunrise
 * and Maghrib sit where a −1° horizon puts them, to the rounding minute, and about 4 min from where
 * −2° would. Their margins are their own two-month fit plus [TWO_MONTH_ALLOWANCE]. Kota Jakarta's
 * own core reaches about 15 km with its checked −1° horizon; beyond it, out to R40's reach for
 * class B (about 55 km), a reach unit keeps its point with the deepest plausible −2° horizon and
 * claims nothing (ruling R46; the circle takes in western Kota Bekasi, Pondok Gede — ruled, not
 * revisited here). Elsewhere (class D, spec §6.3) the highland horizon −2° (the deepest plausible,
 * brief) and 2 min more for a capital up to half a degree away. At a lowland kab/kota that edge
 * puts sunrise 6–7 min before Kemenag's and Maghrib 6–7 min after (about 4 min the horizon, 2 the
 * capital's unknown point, the rest the margins and the rounding), as the monitor measured at the
 * five kota before they were fitted; it stays, since a kab/kota's horizon class cannot be told
 * without its own table (Malang, 450 m up, is −1°; Bogor, 260 m, is −2°).
 */
object Kemenag {
    private val ihtiyat = EventOffsets(fajr = 2, sunrise = -2, dhuhr = 2, asr = 2, maghrib = 2, isha = 2)

    /**
     * What a fit over September–October alone misses, in seconds: at each of the 13 kab/kota fitted
     * over the whole of 2026, the fit over its Sep–Oct 2026 capture alone against its full-year fit
     * (the same fitter, the same safety) — the seasonal drift of each event and Kemenag's 21–22 March
     * days, which run 20–30 s later than its own pattern at 9 of the 13 — at most 24 s on Fajr, 6 on
     * sunrise, 15 on Dhuhr, 6 on Asr and 32 on Maghrib and Isha, plus the fitter's 5 s safety once
     * more for a place that may run a little past the worst of the 13 (Palembang's September-only
     * Maghrib fit was a minute early on one October day).
     */
    private val TWO_MONTH_ALLOWANCE = EventOffsets(fajr = 29, sunrise = -11, dhuhr = 20, asr = 11, maghrib = 37, isha = 37)

    val method = TimetableMethod(
        id = "id.kemenag",
        fajrAngle = 20.0,
        isha = IshaRule.Angle(18.0),
        horizonDeg = -1.0,
        authorityMinutes = ihtiyat,
        margins = margins(start = 0, sunrise = -10, fajr = 35, dhuhr = 84, asr = 27, maghrib = 37, isha = 35),
        imsakMinutesBeforeFajr = 10,
    )

    val outside = method.atEdge("id.kemenag.edge", method.margins.widened(EDGE_SECONDS)).copy(horizonDeg = -2.0)

    private class Kabkota(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val highland: Boolean,
        val radiusKm: Double,
        val margins: EventOffsets,
    )

    private fun m(fajr: Int, sunrise: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) =
        EventOffsets(fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, maghrib = maghrib, isha = isha)

    private val fitted = listOf(
        Kabkota("1301", "Kota Jakarta", -6.21462, 106.84513, highland = false, radiusKm = JAKARTA_CORE_KM, m(14, -9, 76, 23, 28, 33)),
        Kabkota("0119", "Kota Banda Aceh", 5.54167, 95.33333, highland = false, radiusKm = 10.0, m(35, -5, 74, 26, 18, 12)),
        Kabkota("2622", "Kota Makassar", -5.14861, 119.43194, highland = false, radiusKm = 12.0, m(12, -7, 71, 19, 13, 31)),
        Kabkota("1709", "Kota Denpasar", -8.65, 115.21667, highland = false, radiusKm = 10.0, m(9, -9, 68, 20, 31, 12)),
        Kabkota("3329", "Kota Jayapura", -2.53371, 140.71813, highland = false, radiusKm = 15.0, m(12, -4, 73, 20, 13, 14)),
        Kabkota("1634", "Kota Malang", -7.9797, 112.6304, highland = false, radiusKm = 10.0, m(8, -8, 84, 19, 37, 30)),
        Kabkota("1219", "Kota Bandung", -6.92222, 107.60694, highland = true, radiusKm = 10.0, m(8, -8, 79, 19, 30, 32)),
        Kabkota("3308", "Kab. Jayawijaya", -4.09583, 138.94806, highland = true, radiusKm = 25.0, m(9, -8, 70, 16, 12, 7)),
        Kabkota("1630", "Kota Batu", -7.87, 112.52833, highland = true, radiusKm = 8.0, m(8, -7, 78, 19, 11, 35)),
        Kabkota("0313", "Kota Bukittinggi", -0.30907, 100.37055, highland = true, radiusKm = 8.0, m(17, -10, 82, 22, 8, 5)),
        Kabkota("1222", "Kota Bogor", -6.59444, 106.78917, highland = true, radiusKm = 10.0, m(12, -5, 80, 26, 13, 18)),
        Kabkota("1208", "Kab. Garut", -7.245, 107.921, highland = true, radiusKm = 25.0, m(14, -5, 74, 27, 15, 15)),
        Kabkota("1429", "Kab. Wonosobo", -7.35889, 109.90306, highland = true, radiusKm = 20.0, m(8, -9, 69, 18, 11, 9)),
        // Monitor round (29 Sep 2026): five more lowland kota, each its own Sep–Oct 2026 fit plus
        // [TWO_MONTH_ALLOWANCE] (see the class KDoc).
        Kabkota("0228", "Kota Medan", 3.58333, 98.66667, highland = false, radiusKm = 12.0, twoMonths(1, -11, 61, 5, 2, 1)),
        Kabkota("0816", "Kota Palembang", -2.91673, 104.7458, highland = false, radiusKm = 12.0, twoMonths(4, -9, 62, 12, 6, 5)),
        Kabkota("1433", "Kota Semarang", -6.99306, 110.42083, highland = false, radiusKm = 12.0, twoMonths(1, -13, 62, 12, -2, 2)),
        Kabkota("1638", "Kota Surabaya", -7.24917, 112.75083, highland = false, radiusKm = 12.0, twoMonths(15, 1, 75, 36, 17, 18)),
        Kabkota("1505", "Kota Yogyakarta", -7.80139, 110.36472, highland = false, radiusKm = 8.0, twoMonths(3, -8, 65, 16, 3, 5)),
    )

    /**
     * A kota fitted on two months (`Fitter.fit`, safety 5 s, over the monitor's Sep–Oct 2026 capture)
     * takes its own fit plus [TWO_MONTH_ALLOWANCE] on every event: the terms two months near the
     * equinox cannot see (monitor round, 29 Sep 2026).
     */
    private fun twoMonths(fajr: Int, sunrise: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) = EventOffsets(
        fajr = fajr + TWO_MONTH_ALLOWANCE.fajr, sunrise = sunrise + TWO_MONTH_ALLOWANCE.sunrise,
        dhuhr = dhuhr + TWO_MONTH_ALLOWANCE.dhuhr, asr = asr + TWO_MONTH_ALLOWANCE.asr,
        maghrib = maghrib + TWO_MONTH_ALLOWANCE.maghrib, isha = isha + TWO_MONTH_ALLOWANCE.isha,
    )

    private fun unitsFor(base: TimetableMethod, idPrefix: String) = fitted.map { k ->
        AuthorityUnit(
            id = k.id, name = k.name, point = GeoPoint(k.lat, k.lon), radiusKm = k.radiusKm,
            method = base.copy(id = "$idPrefix.${k.id}", horizonDeg = if (k.highland) -2.0 else -1.0, margins = k.margins),
        )
    } + jakartaReach(base, idPrefix)

    /**
     * Ruling R46: beyond Kota Jakarta's own core (its checked −1° horizon, about 15 km) its point
     * still rides as the fixed point out to R40's reach for class B (about 55 km: Depok, Bekasi,
     * Tangerang, which publish their own tables), with the deepest plausible horizon (−2°), and no
     * "at most" figure is claimed there.
     */
    private fun jakartaReach(base: TimetableMethod, idPrefix: String): AuthorityUnit {
        val core = fitted.first { it.id == "1301" }
        return AuthorityUnit(
            id = "1301-reach", name = "Jabodetabek", point = GeoPoint(core.lat, core.lon),
            radiusKm = lateReachKm(core.lat, EntryClass.B),
            method = base.copy(id = "$idPrefix.1301.reach", horizonDeg = -2.0), measured = false,
        )
    }

    val units = UnitSet("id.kemenag", unitsFor(method, "id.kemenag")) { outside }

    val entry: RegistryEntry = single(
        id = "id.kemenag", nameKey = "authority_kemenag", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("ID"), nearby = listOf("id.muhammadiyah"),
    )

    /**
     * Muhammadiyah's Subuh at 18° (Munas Tarjih XXXI, adopted 24 Mar 2021; it changes Subuh only), with
     * Kemenag's other parameters and units: a named timetable, a large organised minority (spec §9).
     */
    val muhammadiyahMethod = method.copy(id = "id.muhammadiyah", fajrAngle = 18.0)

    val muhammadiyahUnits = UnitSet("id.muhammadiyah", unitsFor(muhammadiyahMethod, "id.muhammadiyah")) {
        outside.copy(id = "id.muhammadiyah.edge", fajrAngle = 18.0)
    }

    val muhammadiyah: RegistryEntry = single(
        id = "id.muhammadiyah", nameKey = "authority_muhammadiyah", entryClass = EntryClass.D_AUTHORITY,
        method = muhammadiyahMethod, school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    val entries = listOf(entry, muhammadiyah)

    private const val EDGE_SECONDS = 120

    /** Kota Jakarta's own core: DKI Jakarta within about 15 km of its point. */
    private const val JAKARTA_CORE_KM = 15.0
}
