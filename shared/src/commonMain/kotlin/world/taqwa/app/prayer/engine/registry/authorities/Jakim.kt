package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Malaysia: the state muftis' takwim, published through JAKIM e-solat (research Malaysia section,
 * HIGH; profiles-tested.md data-wide-band): Subuh 18° since the MKI decision of 20–21 Nov 2019,
 * Isyak 18°, Shafi'i Asr, the classic method (events at the noon declination). Each zone is
 * computed at its latest reference point and rounded up, syuruk at its eastern point rounded down,
 * so there is no national profile: each fitted zone is a unit with its own margins (Task 7c:
 * `Fitter.fit("my.jakim/<zone>")` against the zone's own e-solat 2026 table and its 2025 mirror,
 * safety 5 s, margins fitted over the union of both years — see `JakimZones.kt` and
 * `docs/research/2026-09-prayer-times/proof/7c-sea.md`). Imsak Subuh − 10 (none in Perlis).
 *
 * Outside the 13 fitted zones (class D there) the zone's reference point is unknown, and computing
 * at the user's point is 2–8 min early in the east of wide zones (Kapit, Ranau, Mersing). The
 * safe edge takes, for each prayer, the largest margin of any fitted zone (Kuching's reach about
 * 3 min), and in Borneo, where zones span up to 15 min, 5 min more. That is later than the brief's
 * +90 s on purpose: +90 s is already early at Kuching's own point. Verified in an earlier task at
 * Kapit, Sibu, Mukah, Gua Musang, Jeli, Kuala Krai and Dabong; no official table for any of them
 * is held locally in Task 7c to re-check it, so it is kept as is.
 */
object Jakim {
    val method = TimetableMethod(
        id = "my.jakim",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.CLASSIC_NOON,
        imsakMinutesBeforeFajr = 10,
    )

    private val zones: List<AuthorityUnit> = JakimZones.zones.map { z ->
        AuthorityUnit(
            id = z.id,
            name = z.id,
            point = z.point,
            radiusKm = z.radiusKm,
            method = method.copy(
                id = "my.jakim.${z.id.lowercase()}",
                isha = IshaRule.Angle(z.ishaAngleDeg),
                margins = z.margins,
                horizonDeg = z.horizonDeg,
                startPoints = z.startPoints,
                imsakMinutesBeforeFajr = if (z.imsak) 10 else null,
            ),
            lateLimits = z.sunriseLateLimitMinutes?.let {
                listOf(LateLimit(it, PLS01_SUNRISE_REASON, setOf(TimedEvent.SUNRISE)))
            } ?: emptyList(),
        )
    }

    /** The largest margin any fitted zone needs, prayer by prayer (sunrise: the most negative). */
    private val widest: EventOffsets = zones.mapNotNull { it.method }.filter { it.horizonDeg == -0.8333 }
        .map { it.margins }
        .reduce { a, b ->
            EventOffsets(
                fajr = maxOf(a.fajr, b.fajr), sunrise = minOf(a.sunrise, b.sunrise), dhuhr = maxOf(a.dhuhr, b.dhuhr),
                asr = maxOf(a.asr, b.asr), maghrib = maxOf(a.maghrib, b.maghrib), isha = maxOf(a.isha, b.isha),
            )
        }

    /** Peninsular Malaysia beyond the fitted zones. */
    val peninsularEdge = method.atEdge("my.jakim.edge", widest)

    /** Sabah, Sarawak and Labuan beyond the fitted zones, where zones span up to 15 minutes. */
    val borneoEdge = method.atEdge("my.jakim.edge.borneo", widest.widened(BORNEO_EXTRA_SECONDS))

    val units = UnitSet("my.jakim", zones) { p: GeoPoint -> if (p.lon >= BORNEO_WEST_LON) borneoEdge else peninsularEdge }

    /**
     * The end of eating against e-solat's Subuh (end-of-eating audit, 3 Oct 2026; my-jakim.tsv now
     * checks it, F+E: the fast begins at Subuh, imsak is its precaution): never after it on any of
     * the 9,489 zone-days held, but the Subuh printed is each zone's latest reference point rounded
     * up, and the end the dawn at the place itself rounded down, so it comes up to 4 min before it.
     * The fitter would move it later (+78 to +93 s at Johor and Kuala Lumpur); an end never moves
     * later to close a gap, so the minutes are recorded instead.
     */
    private val endOfEatingLimit = LateLimit(
        4,
        "e-solat prints each zone's Subuh for its latest point, rounded up, and the fast here begins at the dawn of the " +
            "place itself: up to 4 min before the printed Subuh (end-of-eating audit, 3 Oct 2026)",
        setOf(TimedEvent.END_OF_EATING),
    )

    val entry: RegistryEntry = single(
        id = "my.jakim", nameKey = "authority_jakim", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("MY"), lateLimits = listOf(endOfEatingLimit),
    )

    val entries = listOf(entry)

    private const val BORNEO_WEST_LON = 109.5
    private const val BORNEO_EXTRA_SECONDS = 300

    private const val PLS01_SUNRISE_REASON =
        "Kangar's printed sunrise spreads a further minute beyond a plain classic-noon model across the seasons"
}
