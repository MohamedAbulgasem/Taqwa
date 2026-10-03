package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.MethodVariant
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.single

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
     * both authority-minute variants. The end of eating's margin is the exception: it differs by
     * region ([variants]). Tables held for Almaty and Astana. Class D.
     */
    val kazakhstanMethod = TimetableMethod(
        id = "kz.qmdb",
        fajrAngle = 15.0,
        isha = IshaRule.Angle(15.0),
        authorityMinutes = EventOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
        margins = EventOffsets(fajr = -2, sunrise = 19, dhuhr = -22, asr = -11, maghrib = -17, isha = -3),
        highLatitude = HighLatRule.Legacy("angle"),
    )

    val kazakhstan: RegistryEntry = single(
        id = "kz.qmdb", nameKey = "authority_qmdb", entryClass = EntryClass.D_AUTHORITY, method = kazakhstanMethod,
        school = AsrSchool.HANAFI, countries = setOf("KZ"), measured = true,
        lateLimits = listOf(
            LateLimit(5, "QMDB's AngleBased curve at Astana (≥48N) leaves a residual of up to 5 min in mid-May (task 7f)", setOf(TimedEvent.FAJR)),
            LateLimit(4, "the same AngleBased residual at Astana, in mid-July, up to 4 min (task 7f)", setOf(TimedEvent.ISHA)),
            LateLimit(
                8,
                "at and above 48N the end of eating keeps clear of QMDB's spring AngleBased residual as far north as " +
                    "Petropavl, so on its other days it comes up to 8 min before the printed Fajr (end-of-eating audit, 3 Oct 2026)",
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
     * QMDB's minutes are ± 5 at and above 48° N. There the end of eating also starts
     * [KAZAKH_NORTH_END_OF_EATING] before the model's dawn: QMDB's AngleBased Fajr runs ahead of
     * the model's in spring and early summer (the residual its Fajr limit records), and the fast
     * begins at QMDB's printed Fajr, so the end of eating at the plain dawn came out after it: up to
     * 4 min at Astana (29 days of 2026, the first 12 May; end-of-eating audit, 3 Oct 2026). The
     * residual grows to the north. The margin is the fitter's bound over QMDB's 2026 and 2027 tables
     * for Astana, Kokshetau, Kostanay, Pavlodar and Petropavl (54.9° N), decided by 6 May 2027 at
     * the north; Astana's 2026 table alone asked for − 221 s, which the 2027 table and every city
     * north of it break (fix round of 3 Oct 2026). North of Petropavl (to 55.4° N) the margin is an
     * extrapolation, and for an end that is the unsafe side: a deeper residual there would be a late
     * end that no held table can show. Almaty's own bound (+ 18 s) needs nothing below 48° N, so
     * the margin there stays 0: an end only ever moves earlier.
     */
    val variants = listOf(
        MethodVariant("kz.qmdb", Regions.kazakhNorth) {
            it.copy(
                authorityMinutes = EventOffsets(sunrise = -5, dhuhr = 5, asr = 5, maghrib = 5),
                endOfEatingMarginSeconds = KAZAKH_NORTH_END_OF_EATING,
            )
        },
    )

    /** The end of eating's margin at and above 48° N, in seconds (fitted from Astana to Petropavl, 2026 and 2027). */
    private const val KAZAKH_NORTH_END_OF_EATING = -374

    val entries = listOf(uzbekistan, kazakhstan, kyrgyzstan)
}
