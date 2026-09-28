package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.beyondTable
import world.taqwa.app.prayer.engine.registry.data.DiyanetEuropeCurves
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/**
 * Türkiye: Diyanet İşleri Başkanlığı, by its own algorithm (profiles-tested.md, data-diyanet-kemenag;
 * HIGH): the sun's declination and equation of time once a day at 0h UT, İmsak 18°, Yatsı 17°
 * (statement of 17 Jul 2013), the documented temkin (vakithesaplama.diyanet.gov.tr/temkin.php:
 * sunrise −7, Dhuhr +5, Asr +4, Maghrib +7), no elevation, rounded to the nearest minute. İmsak is
 * the Fajr itself, and the end of eating (the same printed column).
 *
 * Margins (Task 7b) fitted on the research's five districts (İstanbul, Ankara, Tekirdağ, Edirne,
 * Van; 1,980 days) with the research's 10 s of safety rather than the Fitter's 5, so that a user a
 * few kilometres east of their own ilçe's point, inside İstanbul's or Ankara's reach, stays on the
 * safe side: Fajr − 11 s, Dhuhr − 14 s, Asr − 12 s, Maghrib − 12 s, Isha − 14 s, sunrise and the end
 * of eating + 19 s (nearest rounding is ± 30 s; this model is within 8 s of Diyanet's own). Held
 * out on the other eight districts (3,168 days) and at nine more districts beyond every unit: never
 * early, the units at most a minute late. İstanbul's printed row for 26 Sep 2026 comes out a minute
 * late at Fajr, Asr and Isha (their moments fall 19–27 s past the minute, inside the safety).
 *
 * Units are the analysed districts: the six at Diyanet's own published points
 * (vakit_kiyaslamalari.php) and seven at the app's coordinates, where the same rule held. İstanbul
 * and Ankara publish many more ilçe of their own, so their units reach only as far as their point,
 * riding beside the user's, stays within class A's minute east to west (ruling R40): about 21 km. A district
 * not analysed (class D there, spec §6.2 a) is Diyanet's method at the user's point with +60 s on
 * starts and −60 s on sunrise (brief): about 20 km of offset from its unknown district point.
 */
object Diyanet {
    private val temkin = EventOffsets(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 7)

    val method = TimetableMethod(
        id = "tr.diyanet",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.0),
        sunModel = SunModel.DAILY_0H_UT,
        asrModel = AsrModel.DAILY_0H_UT,
        authorityMinutes = temkin,
        margins = margins(start = -14, sunrise = 19, fajr = -11, asr = -12, maghrib = -12),
        endOfEatingMarginSeconds = 19,
    )

    /** An ilçe Taqwa has not analysed: its point is unknown. */
    val outside = method.atEdge("tr.diyanet.edge", margins(start = 60, sunrise = -60))

    private fun district(id: String, name: String, lat: Double, lon: Double, radiusKm: Double = 15.0) =
        AuthorityUnit(id, name, GeoPoint(lat, lon), radiusKm)

    val units = UnitSet(
        "tr.diyanet",
        listOf(
            district("9541", "İstanbul", 41.012, 28.974, radiusKm = lateReachKm(41.012, EntryClass.A)),
            district("9206", "Ankara", 39.938, 32.848, radiusKm = lateReachKm(39.938, EntryClass.A)),
            district("9879", "Tekirdağ", 40.973, 27.511),
            district("9146", "Adana", 36.987, 35.326, radiusKm = 20.0),
            district("20089", "Hatay", 36.2, 36.153),
            district("9587", "Karaman", 37.181, 33.214),
            district("9419", "Edirne", 41.67719, 26.55597),
            district("9352", "Çanakkale", 40.15552, 26.41271),
            district("9930", "Van", 38.49457, 43.38323),
            district("9507", "Hakkâri", 37.57444, 43.74083),
            district("9594", "Kars", 40.59825, 43.08548),
            district("9522", "Iğdır", 39.92371, 44.045),
            district("9451", "Erzurum", 39.90861, 41.27694),
        ),
    ) { outside }

    val entry: RegistryEntry = single(
        id = "tr.diyanet", nameKey = "authority_diyanet", entryClass = EntryClass.A, method = method,
        school = AsrSchool.STANDARD, countries = setOf("TR"),
    )

    /**
     * Diyanet's method in Europe since 1 Jan 2023 (the 2021 Istanbul congress; IGGÖ's statement):
     * Fajr 18°, Isha 16°, the same temkin; north of 44.5° its takdir rule, fitted as Fajr = sunrise −
     * 20.6 % and Isha = Maghrib + 18.5–19 % of the night in core summer (MEDIUM), with ramps. At its
     * own city tables (europeUnits) each prayer follows that table's own curve; elsewhere its Fajr is a
     * curve for the place, never before 19 % of the night (PlaceCurves), and its Isha the real 16°,
     * else sunset + 22 % of the night (Diyanet's counts from its Maghrib, sunset + 7, up to 3 % of a
     * short night): late but never early. North of 44.5° the fast begins at its own earliest dawns,
     * read from the nearest city table to the south (ruling R39: EndOfEatingDawns). Followed by
     * DITIB, IGMG, IGGÖ (Austria), FIDS (Switzerland) and Diyanet mosques across Europe.
     *
     * Margins fitted by Task 7b on six of its twelve city tables (Sarajevo, Zürich, München, Paris,
     * Berlin, Stockholm) and held out on the other six, 5 s safety: Fajr − 13 s, sunrise + 10 s,
     * Dhuhr − 13 s, Asr − 12 s, Maghrib − 15 s, Isha − 10 s (the plain angles, on days no rule
     * sets), the end of eating + 9 s.
     */
    val europeMethod = method.copy(
        id = "tr.diyanet.europe",
        isha = IshaRule.Angle(16.0),
        margins = margins(start = -13, sunrise = 10, asr = -12, maghrib = -15, isha = -10),
        highLatitude = HighLatRule.NightFraction(fajrFraction = 0.19, ishaFraction = 0.22),
        endOfEatingMarginSeconds = 9,
    )

    /**
     * Where Diyanet publishes city tables with this method and a Turkish community follows them (the
     * research's Diyanet city list, ruling R50): Germany, Austria, Switzerland and Liechtenstein
     * (FIDS), France, Belgium, the Netherlands, the UK, Sweden, Norway, Denmark, Finland, and Bosnia
     * (Sarajevo). Not Kosovo, Albania or Australia, where Diyanet's own tables keep the 17° Isha of its
     * Turkish method (this one's 16° would be early there). Its scope stays GLOBAL until Task 8's
     * ruling R50 lands, which makes these countries (and the Automatic entries listing it) its scope.
     */
    val europe: RegistryEntry = single(
        id = "tr.diyanet.europe", nameKey = "authority_diyanet_europe", entryClass = EntryClass.D_AUTHORITY,
        method = europeMethod, school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
        countries = setOf("DE", "AT", "CH", "LI", "FR", "BE", "NL", "GB", "SE", "NO", "DK", "FI", "BA"),
    )

    /**
     * Diyanet prints a table for each European city at its own point (ruling R44): the twelve held
     * are units at their points, reaching as far as class D's 3 minutes (about 40–60 km). North of
     * 44.5° each follows its own table's takdir ([cityMethod]); Sarajevo, south of it, the plain
     * method. Beyond every reach, the plain method at the user's point a minute later, the nearest
     * table's point still bounding the ends within three reaches (ruling R45). Built as
     * `pointTables` builds it, so that Stockholm and Oslo can carry their own exceptions.
     */
    val europeUnits: UnitSet = run {
        val units = listOf(
            europeTable("sarajevo", "Sarajevo", GeoPoint(43.84864, 18.35644), method = null),
            europeTable("zurich", "Zürich", GeoPoint(47.36667, 8.55)),
            europeTable("freiburg", "Freiburg", GeoPoint(47.9959, 7.85222)),
            europeTable("munich", "München", GeoPoint(48.13743, 11.57549)),
            europeTable("wien", "Wien", GeoPoint(48.20849, 16.37208)),
            europeTable("paris", "Paris", GeoPoint(48.85341, 2.3488)),
            europeTable("brussels", "Brussels", GeoPoint(50.85045, 4.34878), lateLimits = listOf(springEnd(4))),
            europeTable("london", "London", GeoPoint(51.5074, -0.1278), lateLimits = listOf(springEnd(4))),
            europeTable("amsterdam", "Amsterdam", GeoPoint(52.37403, 4.88969), lateLimits = listOf(springEnd(4))),
            europeTable("berlin", "Berlin", GeoPoint(52.52437, 13.41053), lateLimits = listOf(springEnd(4))),
            europeTable(
                "stockholm", "Stockholm", GeoPoint(59.32938, 18.06871),
                method = cityMethod("stockholm").let { it.copy(margins = it.margins.copy(fajr = it.margins.fajr + STOCKHOLM_IFIS_FAJR)) },
                lateLimits = listOf(LateLimit(5, STOCKHOLM_FAJR, setOf(TimedEvent.FAJR)), springEnd(5)),
            ),
            europeTable(
                "oslo", "Oslo", GeoPoint(59.91273, 10.74609),
                lateLimits = listOf(springFajr(4), springEnd(5), LateLimit(8, OSLO_SUNRISE, setOf(TimedEvent.SUNRISE))),
            ),
        )
        UnitSet("tr.diyanet.europe", units) { user ->
            val nearest = units.minBy { distanceKm(user, it.point) }
            europeMethod.beyondTable("tr.diyanet.europe.edge", nearest.point, user, nearest.radiusKm)
        }
    }

    private fun europeTable(
        key: String,
        name: String,
        point: GeoPoint,
        method: TimetableMethod? = cityMethod(key),
        lateLimits: List<LateLimit> = emptyList(),
    ) = AuthorityUnit(
        "tr.diyanet.europe.$key", name, point, lateReachKm(point.lat, EntryClass.D_AUTHORITY), method, lateLimits = lateLimits,
    )

    /** Where the spring takdir moves Fajr by minutes a day, the leap-cycle envelope makes Fajr up to [minutes] late. */
    private fun springFajr(minutes: Int) = LateLimit(minutes, SPRING_FAJR, setOf(TimedEvent.FAJR))

    /** The same envelope makes the end of eating up to [minutes] early. */
    private fun springEnd(minutes: Int) = LateLimit(minutes, SPRING_END, setOf(TimedEvent.END_OF_EATING))

    private const val SPRING_FAJR =
        "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest " +
            "of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed " +
            "time by as much as a day's step on those days."

    private const val SPRING_END =
        "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating " +
            "takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes " +
            "before the printed time by as much as a day's step on those days."

    private const val OSLO_SUNRISE =
        "Diyanet prints Oslo's June and July sunrise up to 7 min after the sun's own (its rule is not published); " +
            "the app keeps the sun's, the safe side."

    /**
     * A takdir city's own method (rulings R39, R42, R44): Fajr, Isha and the end of eating at the
     * depressions its own table prints (DiyanetEuropeCurves: never a start before, nor the end after,
     * any printed minute), with the curves' own margins. Its id keeps PlaceCurves from replacing the
     * curves with the generic rule; beyond its reach the edge ("tr.diyanet.europe.edge") takes the
     * generic rule again.
     */
    private fun cityMethod(key: String): TimetableMethod {
        val curves = DiyanetEuropeCurves.byCity.getValue(key)
        return europeMethod.copy(
            id = "tr.diyanet.europe.$key",
            fajrAngleByDayOfYear = curves.fajr,
            ishaAngleByDayOfYear = curves.isha,
            endOfEating = EndOfEating.DawnAngle(18.0, curves.end),
            margins = europeMethod.margins.copy(fajr = CITY_CURVE_START, isha = CITY_CURVE_START),
            endOfEatingMarginSeconds = CITY_CURVE_END,
        )
    }

    /**
     * Stockholm's Fajr takes this many seconds more (Task 7g, review fix round 2): Islamiska Förbundet's
     * 2025–26 pages, which print Diyanet's Stockholm times, give Fajr a minute later than Diyanet's own
     * Stockholm table on some days (40 of them, December 2025 to June 2026); the curve, derived from the
     * latter, was a minute before them. 64 s clears every one; with the fits' 5 s safety, 69.
     */
    private const val STOCKHOLM_IFIS_FAJR = 69

    private const val STOCKHOLM_FAJR = SPRING_FAJR + " Stockholm's Fajr also waits for Islamiska Förbundet's pages, a " +
        "minute later than Diyanet's own table on some days: up to 5 min after Diyanet's in April and August."

    /** The margins on a city's own curves, which are derived 25 s before each printed minute. */
    private const val CITY_CURVE_START = -29
    private const val CITY_CURVE_END = 30

    /**
     * The old picker's Turkey method: Diyanet's algorithm at any point, the plain ±30 s (ruling R31),
     * and on Asr the adhan2 allowance of the other Other methods (Task 7h, [ADHAN2_ASR_ALLOWANCE],
     * proof/7h-other-and-default.md) plus [TURKEY_ASR_EXTRA]: the old picker's adhan2 TURKEY preset
     * interpolates the sun between days where this model takes it once at 0h UT, and its Asr ran up
     * to 65 s after this one's (Task 7b, all of 2026 at 13 places in both schools; London and Berlin
     * in February, New York and Los Angeles in winter), so 7h's 35 s alone left up to 23 days a year
     * a minute early there.
     */
    val other: RegistryEntry = single(
        id = "other.turkey", nameKey = "method_turkey", entryClass = EntryClass.D_AUTHORITY,
        method = method.copy(
            id = "other.turkey",
            margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE + TURKEY_ASR_EXTRA),
            endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    /** The rest of adhan2 TURKEY's lead over this model's Asr beyond [ADHAN2_ASR_ALLOWANCE] (65 − 35 s), and 5 s. */
    internal const val TURKEY_ASR_EXTRA = 35

    val entries = listOf(entry, europe, other)
}
