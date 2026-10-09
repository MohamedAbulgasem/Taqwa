package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.DayRule
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.Harmonics
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.beyondTable
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraCities
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/**
 * Saudi Arabia: the Umm al-Qura calendar (KACST), rebuilt from 13,146 place-days of its open API
 * at 12 places, 2024–2030 (profiles-tested.md, data-umm-al-qura).
 *
 * - Fajr 18.5° with the declination biased by a 3-harmonic series of the day of year; Asr from the
 *   Asr-moment declination plus 0.4 × that bias.
 * - Isha = Maghrib + 90 min, + 120 on Umm al-Qura's Ramadan dates ([UmmAlQuraDates.ramadanDates]).
 * - On the lag dates ([UmmAlQuraDates.lagDates], passed to the day computer) a row holds the
 *   previous day's astronomy: starts take the later of the two days, sunrise and the end of eating
 *   the earlier, so that nothing is early if KACST stops the lag ([lagDateLimit]). After the last
 *   date the list covers (2030) every day is taken as a lag date (ruling R71).
 * - The API prints no end of eating: the fast begins at its Fajr.
 *
 * Margins (Task 7a, sa-ummalqura.tsv): fitted on this core over the nine tables that end by 2027
 * (6,205 place-days), the Conventions' formulas with 5 s: Fajr +10, sunrise −8, Dhuhr +10, Asr +11,
 * Maghrib and Isha +10; the held-out tables (Makkah and Riyadh 2024–2030, Turaif) need no more. The
 * end of eating is ⌊dawn + 47 s⌋: the fit rows allow + 50, but Turaif (held out) sits only 2 s
 * inside that, so it takes the fit over every table with its 5 s (proof 7a-gulf-egypt.md).
 * Held out, 0 early and 0 late ends on 6,941 place-days. Class A, Standard Asr (Shafi'i, as
 * published), with [lagDateLimit].
 */
object UmmAlQura {
    /**
     * Class A's minute was passed at the tables' own points on the lag dates alone (rulings R37, R41): on a lag date
     * the API prints the previous day's times, and Taqwa shows the later of the two days' starts and the earlier of
     * their sunrises and ends of eating, a minute more than the class allows on a few days (over 13,146 place-days:
     * Fajr on 1, sunrise on 9, the end of eating on 21, every one a lag date). Across a unit's reach (one minute of
     * longitude, ruling R40) the start shown is the later of the unit's table and the user's own sun, and the ends the
     * earlier, so a minute more again at its edge: each unit's own tables, replayed at 8 bearings and at half and
     * 95 % of its radius (sa-ummalqura-reach.tsv, the review of 9 Oct 2026, "prove it across the unit"), came up to
     * 2 min after it at every start ([lagDateLimit], [reachStartsLimit]) and 3 before it at sunrise and the end of
     * eating ([reachEndsLimit]); at nine units of the north and the east, Fajr up to 3 ([reachFajrLimit]).
     */
    val lagDateLimit = LateLimit(
        2,
        "on the few dates a year Umm al-Qura's calendar repeats the previous day's times, Taqwa shows the later Fajr of " +
            "the two days, so as never to be early if the calendar stops repeating them; and across a unit's reach the " +
            "later of the unit's table and the user's own sun (the reach rows, 9 Oct 2026)",
        setOf(TimedEvent.FAJR),
    )

    /** Across a unit's reach, the later of its table's start and the user's own sun ([lagDateLimit]'s KDoc). */
    val reachStartsLimit = LateLimit(
        2,
        "within a unit's reach, a minute of longitude, the start shown is the later of the unit's table and the user's " +
            "own sun, so toward the reach's edge it comes up to 2 min after the table (ruling R40; each unit's own tables " +
            "replayed across its reach, 9 Oct 2026)",
        setOf(TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
    )

    /** The lag dates' earlier sunrise and end of eating, and across a unit's reach the earlier of its table's and the user's own. */
    val reachEndsLimit = LateLimit(
        3,
        "on the lag dates Taqwa shows the earlier sunrise and end of eating of the two days, and within a unit's reach the " +
            "earlier of the unit's table and the user's own, so toward the reach's edge they come up to 3 min before the " +
            "table (each unit's own tables replayed across its reach, 9 Oct 2026)",
        setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
    )

    /**
     * Nine units of the north and the east, where a lag date's later Fajr and the reach's later sun meet at the edge:
     * Fajr up to 3 min after the unit's table there (the reach rows, 9 Oct 2026).
     */
    private val reachFajrLimit = LateLimit(
        3,
        "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr " +
            "comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)",
        setOf(TimedEvent.FAJR),
    )

    /** The units whose reach's edge needs [reachFajrLimit] (the gate, sa-ummalqura-reach.tsv). */
    private val reachFajr = setOf(
        "al-qurayyat", "al-uwayqiliyah", "az-zulfi", "dhahran", "dumah-al-jandal", "haql", "khafji", "mawqaq", "turaif",
    )

    /** Umm al-Qura's Fajr declination bias (degrees), by day of year. */
    val fajrBias = Harmonics(
        -0.008390,
        listOf(0.032536 to 0.323067, -0.046890 to 0.030917, 0.003527 to 0.019364),
    )

    val method = TimetableMethod(
        id = "sa.ummalqura",
        fajrAngle = 18.5,
        isha = IshaRule.AfterMaghrib(90, 120),
        fajrDeclinationBias = fajrBias,
        asrBiasFactor = 0.4,
        margins = margins(start = 10, sunrise = -8, asr = 11),
        dayRule = DayRule.LAG_DATES_UQ,
        endOfEatingMarginSeconds = 47,
    )

    val entry: RegistryEntry = single(
        id = "sa.ummalqura", nameKey = "authority_umm_al_qura", entryClass = EntryClass.A, method = method,
        school = AsrSchool.STANDARD, countries = setOf("SA"), measured = true,
        lateLimits = listOf(lagDateLimit, reachStartsLimit, reachEndsLimit),
    )

    /**
     * Tayma ([taymaTown]): the town's times are the later of its own sun and KACST's Tayma table, computed a degree
     * north of it, so they run up to 5 min after that table at Fajr and 3 at sunrise, Asr, Maghrib and Isha over
     * 2026-27 (the app's point for Tayma, sa-ummalqura.tsv): class D there (ruling R57), with Fajr's own limit, and
     * every other event at the class's 3 rather than the entry's 2 (its own limits, which the entry's would
     * otherwise give).
     */
    private val taymaLimits = listOf(
        LateLimit(
            5,
            "KACST's list places Tayma a degree north of the town, so its Tayma table is computed 111 km north of it, " +
                "and the Fajr shown in the town, the later of that table's and the town's own, comes up to 5 min after " +
                "the table in spring and autumn (city-points round, 9 Oct 2026)",
            setOf(TimedEvent.FAJR),
        ),
        LateLimit(
            3,
            "the same table computed a degree north of Tayma: the sunrise and the end of eating shown in the town, the " +
                "earlier of that table's and the town's own, come up to 3 min before the table's, and Dhuhr, Asr, Maghrib " +
                "and Isha up to 3 after it (class D's 3, not the entry's 2; city-points round, 9 Oct 2026)",
            setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA),
        ),
    )

    private val cityUnits: List<AuthorityUnit> = UmmAlQuraCities.all.map { city ->
        val tayma = city.key == "tayma"
        AuthorityUnit(
            city.key, city.name, GeoPoint(city.lat, city.lon), lateReachKm(city.lat, EntryClass.A),
            entryClass = if (tayma) EntryClass.D_AUTHORITY else null,
            lateLimits = when {
                tayma -> taymaLimits
                city.key in reachFajr -> listOf(reachFajrLimit)
                else -> emptyList()
            },
            named = false, cityId = UmmAlQuraCities.appCityOf[city.key],
        )
    }

    /**
     * Ruling R44 (the city-points round of 9 Oct 2026): the official page prints one table per place of KACST's own
     * city list, the API's answer at that place's coordinates, and every mosque of a city follows its city's. At the
     * app's own point for Riyadh, 3.8 km east of KACST's, the user's own sun began Fajr, Dhuhr, Asr, Maghrib or Isha
     * a minute before Riyadh's table on 27 days of 2024–2030 (39 cells), and at Madinah's, 8 km from KACST's,
     * sunrise and the end of eating came a minute after Madinah's on 38 days (40 cells; main at 705c4f7f). Each of
     * [UmmAlQuraCities]' 173 places is a unit at KACST's own point, which rides as the fixed point beside the user's
     * (ruling R15: starts the later, ends the earlier), within its R40 reach for class A, one minute of longitude
     * (24 to 27 km); gated at its own point, at the app's point for each Saudi city (sa-ummalqura.tsv) and across its
     * reach (sa-ummalqura-reach.tsv). Beyond every unit, the nearest place's table as a point table's edge
     * ([beyondTable], rulings R44 and R45): the user's own point a minute later, the end of eating without its fitted
     * 47 s (a margin fitted at a table's point applies there only, so SAFE_END), and within three reaches of the
     * nearest place its point still bounding sunrise and the end of eating; no figure (spec §3.5). About and the site
     * call the place by the app's name for the city whose table it is ([AuthorityUnit.named], [AuthorityUnit.cityId]).
     */
    val units = UnitSet("sa.ummalqura", cityUnits, choose = ::taymaTown) { user ->
        val nearest = cityUnits.minBy { distanceKm(user, it.point) }
        method.beyondTable("sa.ummalqura.edge", nearest.point, user, nearest.radiusKm)
    }

    /**
     * KACST's list places Tayma at 28.63° N, a degree north of the town (the app's city list and every map put it at
     * 27.62° N, at the same longitude), so the official page's Tayma table is the API's answer 111 km north of the
     * town, and at the town's own point the user's own sun began Asr, Maghrib and Isha up to 3 min, and Fajr up to 2,
     * before it on about 150 days a year (the city-points round of 9 Oct 2026). Within a minute's reach of the town
     * the place takes that table's unit, so that its times are never before the table the town's mosques read, nor
     * before the town's own sun.
     */
    private fun taymaTown(place: Place): AuthorityUnit? =
        cityUnits.firstOrNull { it.id == "tayma" }?.takeIf {
            distanceKm(GeoPoint(place.lat, place.lon), TAYMA_TOWN) <= lateReachKm(TAYMA_TOWN.lat, EntryClass.A)
        }

    /** The town of Tayma, as the app's city list places it (GeoNames 101516). */
    private val TAYMA_TOWN = GeoPoint(27.62233, 38.53882)

    /**
     * The old picker's Umm al-Qura, anywhere: the same rebuilt method with the plain safe rounding of
     * the Other methods (ruling R31), not the margins fitted on Saudi Arabia's own points.
     */
    val other: RegistryEntry = single(
        id = "other.ummalqura", nameKey = "method_umm_al_qura", entryClass = EntryClass.D_AUTHORITY,
        method = method.copy(
            id = "other.ummalqura", margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE),
            endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    val entries = listOf(entry, other)
}
