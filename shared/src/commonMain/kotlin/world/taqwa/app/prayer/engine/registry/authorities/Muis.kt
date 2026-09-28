package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.DayRule
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.RamadanRule
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
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.regionKey
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Singapore: MUIS's one national table (data.gov.sg, Singapore Open Data Licence), rule B of
 * profiles-tested.md (data-wide-band; HIGH, 99 % of 1,096 days reproduced): the classic method
 * (accurate transit, events at the noon declination), Fajr 20°, Isha 18°, Shafi'i Asr, always at
 * the fixed point 1.28967 N 103.85007 E (computing at the device fails: Tampines 24–33 early days),
 * starts the later of the day before and the day after (each year's table is a day off, in a
 * direction that flips), sunrise the earlier; Maghrib +1 min in Ramadan; imsak Subuh − 10.
 *
 * Margins refitted on 2024–2025 plus 5 s and checked on the whole of 2024–2026 (Task 7c): Fajr +16,
 * sunrise +60, Dhuhr +80, Asr +20, Maghrib +17, Isha +17, end of eating +59 (a table point rounds
 * `EXACT_MOMENT` and `NOON_SHADOW` and `UTC12_ONE_SHOT` and `DAILY_0H_UT` were all tried against the
 * fit rows: none narrows Asr's spread below `EXACT_MOMENT`'s, so the model is unchanged). Even
 * refitted, MUIS's own rounding is not reproduced to the minute everywhere: on the full 2024–2026
 * table every event but Asr shows at most 2 min late on a handful of days (Fajr 6, sunrise 1,
 * Dhuhr 2, Maghrib 5, Isha 3, end of eating 6, out of 1096) — ruling R57: an exception is for a
 * specific, reasoned case, not for holding on to a class, so with every event needing one the entry
 * is class B outright, not A with six exceptions. Asr runs a further minute late on 6 of those days
 * (worst 3 min, all near an equinox, where the sun's declination moves fastest and a fixed-margin,
 * noon-referenced Asr model separates furthest from whatever rounding MUIS itself applies): its own
 * exception, one minute past class B's own margin.
 */
object Muis {
    val point = GeoPoint(1.28967, 103.85007)

    /** Asr's own exception, one minute past class B's own margin: MUIS's Standard Asr shows a
     * further minute of drift on a handful of dates near the equinoxes, where the sun's declination
     * changes fastest and every noon-referenced Asr model this engine offers (EXACT_MOMENT,
     * NOON_SHADOW, UTC12_ONE_SHOT, DAILY_0H_UT) separates furthest from whatever rounding MUIS
     * applies; no single margin removes it without moving every other day later too. */
    private const val ASR_EXCEPTION_REASON =
        "MUIS's own Standard Asr is not reproduced to the minute; a further minute of drift near the equinoxes " +
            "past class B's own margin"

    val method = TimetableMethod(
        id = "sg.muis",
        fajrAngle = 20.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.CLASSIC_NOON,
        margins = margins(start = 20, sunrise = 60, fajr = 16, dhuhr = 80, asr = 20, maghrib = 17, isha = 17),
        dayRule = DayRule.NEIGHBOURS_MUIS,
        ramadan = RamadanRule(maghribExtraSeconds = 60),
        imsakMinutesBeforeFajr = 10,
        fixedPoint = point,
        endOfEatingMarginSeconds = 59,
    )

    val entry: RegistryEntry = single(
        id = "sg.muis", nameKey = "authority_muis", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("SG"), measured = true,
        lateLimits = listOf(LateLimit(3, ASR_EXCEPTION_REASON, setOf(TimedEvent.ASR))),
    )

    /**
     * The old picker's Singapore method anywhere: 20°/18°, Dhuhr +1 (adhan2's preset), at the user's
     * point with the first-guess ±30 s. Asr carries [ADHAN2_ASR_ALLOWANCE] on top (Task 7h,
     * `docs/research/2026-09-prayer-times/proof/7h-other-and-default.md`: measured at Cape Town,
     * 15 Oct 2026, against the old adhan2-backed engine — adhan2 takes one declination for the whole
     * day rather than iterating it to the Asr moment, up to 34 s later than this engine's own Asr,
     * and Singapore is the one adhan2 preset that rounds Asr up rather than to the nearest minute,
     * which turns that sub-minute gap into a full minute early; the same allowance 7h gives mwl,
     * isna, karachi and moonsighting in `Generic.kt`, and flagged as needed here too since Muis.kt
     * is not 7h's file to edit).
     */
    val other: RegistryEntry = single(
        id = "other.singapore", nameKey = "method_singapore", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "other.singapore",
            fajrAngle = 20.0,
            isha = IshaRule.Angle(18.0),
            authorityMinutes = EventOffsets(dhuhr = 1),
            margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE),
            endOfEatingMarginSeconds = SAFE_END,
            imsakMinutesBeforeFajr = 10,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    /**
     * Brunei: the Ministry of Religious Affairs (Jabatan Mufti Kerajaan): 20°/18°, no precaution
     * minutes, nearest rounding, Dhuhr about + 1, Shafi'i Asr; imsak Suboh − 10. One table, for
     * Bandar Seri Begawan, and documented district minutes (mora.gov.bn: "Daerah Belait hendaklah
     * ditambah 3 minit dan Daerah Tutong … 1 minit"; none for Brunei-Muara and Temburong). So each
     * district is a unit computed at BSB's point beside the user's, with its minutes on every time.
     * A district is the place's region when known (ruling R32), else told by longitude, erring
     * towards the larger minutes near a border (the nearest seat puts Liang, in Belait, in Tutong).
     *
     * Task 7c: the ministry's own SharePoint list for BSB (the archive, not the public repo) is
     * checked, with its duplicate and typo rows dropped (one would put Fajr about 9 min early) —
     * Brunei-Muara's district, whose minutes are all zero, so this table checks it directly. It is
     * never early with the plain ±30 s safe start (worst 2 min late, class B). Belait, Tutong and
     * Temburong's own minutes are still the ministry's documented figure alone, not checked against
     * a table of their own: they stay class D and unmeasured.
     */
    private val bruneiMethod = TimetableMethod(
        id = "bn.mora",
        fajrAngle = 20.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(dhuhr = 1),
        margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE),
        imsakMinutesBeforeFajr = 10,
    )

    private val bandarSeriBegawan = GeoPoint(4.89035, 114.94006)

    private fun district(
        id: String,
        name: String,
        seat: GeoPoint,
        minutes: Int,
        measured: Boolean = false,
        entryClass: EntryClass? = null,
    ) = AuthorityUnit(
        id = id, name = name, point = seat, radiusKm = 60.0, measured = measured, entryClass = entryClass,
        method = bruneiMethod.copy(
            id = "bn.mora.$id",
            authorityMinutes = EventOffsets(
                fajr = minutes, sunrise = minutes, dhuhr = 1 + minutes, asr = minutes, maghrib = minutes, isha = minutes,
            ),
            fixedPoint = bandarSeriBegawan,
        ),
    )

    val bruneiUnits: UnitSet = run {
        val belait = district("belait", "Belait", GeoPoint(4.58361, 114.2312), 3)
        val tutong = district("tutong", "Tutong", GeoPoint(4.80278, 114.64917), 1)
        val muara = district(
            "brunei-muara", "Brunei-Muara", bandarSeriBegawan, 0, measured = true, entryClass = EntryClass.B,
        )
        val temburong = district("temburong", "Temburong", GeoPoint(4.7092, 115.0717), 0)
        val districts = listOf(belait, tutong, muara, temburong)
        val byRegion = mapOf("belait" to belait, "tutong" to tutong, "temburong" to temburong, "bruneimuara" to muara)
        UnitSet(
            "bn.mora",
            districts,
            choose = { place ->
                val here = GeoPoint(place.lat, place.lon)
                val region = place.admin1?.let { name -> byRegion.entries.firstOrNull { regionKey(name).startsWith(it.key) }?.value }
                when {
                    districts.none { distanceKm(here, it.point) <= it.radiusKm } -> null
                    region != null -> region
                    place.lon < BELAIT_EAST_LON -> belait
                    place.lon < TUTONG_EAST_LON -> tutong
                    // East of Brunei Bay and south of it, the Temburong exclave; else Brunei-Muara.
                    place.lon > 114.98 && place.lat < 4.85 -> temburong
                    else -> muara
                }
            },
        ) { bruneiMethod.atEdge("bn.mora.edge", bruneiMethod.margins.widened(180 + 60)) }
    }

    val brunei: RegistryEntry = single(
        id = "bn.mora", nameKey = "authority_mora_brunei", entryClass = EntryClass.D_AUTHORITY,
        method = bruneiMethod, school = AsrSchool.STANDARD, countries = setOf("BN"),
    )

    val entries = listOf(entry, brunei, other)

    private const val BELAIT_EAST_LON = 114.60
    private const val TUTONG_EAST_LON = 114.85
}
