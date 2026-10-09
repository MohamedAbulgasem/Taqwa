// GENERATED FILE — do not edit by hand.
//
// Written by `./gradlew -p tools/timetables generateProofStamps` (GenerateProofStamps.kt) from
// every `tools/timetables/official/stamps/<entry>.json`. Re-run it after every stamp refresh (a
// `gate` run that changes a stamp) and commit the result: stamps are statistics only — place-days,
// dates, minute counts — never an official time, so this file is safe to publish (spec §5).
package world.taqwa.app.prayer.engine.registry.data

/**
 * One registry entry's proof, as the About-times screen shows it (spec §2.3, task 11): how many
 * place-days and places the gate has checked it on, the last date it holds (`provenThrough`, null
 * before any stamp exists), the worst lateness seen per event over the whole entry
 * (`worstLateMinutes`, event keys as the gate writes them: "fajr", "sunrise", "dhuhr", "asr" or
 * "asrStandard"/"asrHanafi", "maghrib", "isha", "endOfEating", "imsak") and per authority unit
 * (`worstLateByUnit`, keyed by the unit's id; empty for an entry the gate checks without units),
 * and the late limits the gate held the entry to when the stamp was written ([ProofLateLimit],
 * ruling R41): the class's own (A 1, B 2, C 1 after the latest member, D 3), listed with a null
 * reason, and each recorded exception with its reason, each with the events it covered.
 * `provenThroughByUnit` is each unit's own last checked date where it is before the entry's
 * (`provenThrough`): the date About gives for a place in that unit, as its city page does (ruling R115).
 *
 * A unit or entry with no stamp file (an authority not yet gated, or a class with no official
 * days) has no row: [ProofStamps.of] returns null, and the screen reads that as "not yet compared"
 * (spec §2.3's without-data wording), never as zero.
 */
data class ProofStamp(
    val entryId: String,
    val places: Int,
    val placeDays: Int,
    val ramadanDays: Int,
    val provenThrough: String?,
    val worstLateMinutes: Map<String, Int>,
    val worstLateByUnit: Map<String, Map<String, Int>>,
    val lateLimits: List<ProofLateLimit>,
    val provenThroughByUnit: Map<String, String> = emptyMap(),
)

/**
 * One late limit the gate held this entry to and the events it covered (ruling R41): the class's
 * own with a null reason, or a recorded exception with its reason.
 */
data class ProofLateLimit(val minutes: Int, val reason: String?, val events: List<String>)


object ProofStamps {
    /** One row per stamped entry (`tools/timetables/official/stamps/<id>.json`), sorted by id. */
    val byEntry: Map<String, ProofStamp> = listOf(
        aeAwqaf(),
        aeIacadDubai(),
        alKmsh(),
        atIggo(),
        auCautious(),
        auLma(),
        baIz(),
        bdIfb(),
        beCautious(),
        beEmb(),
        bhCouncil(),
        bnMora(),
        caCautious(),
        caIft(),
        caIit(),
        caIsna(),
        caMac(),
        caToronto(),
        chFids(),
        deCautious(),
        defaultSafe(),
        dzMarw(),
        egEsa(),
        frCautious(),
        frGmp(),
        gbLondonLupt(),
        gbWifaqul(),
        idKemenag(),
        ieCautious(),
        ieIcci(),
        ieIfi(),
        inKarachi(),
        iqSunni(),
        joAwqaf(),
        kgDefault(),
        kwAwqaf(),
        kzQmdb(),
        lbFatwa(),
        lyAwqaf(),
        maHabous(),
        meIzcg(),
        mrMinistry(),
        myJakim(),
        nlCautious(),
        noCautious(),
        noIrn(),
        nzFianz(),
        omMara(),
        otherTurkey(),
        pkKarachi(),
        psGazaAwqaf(),
        psIftaa(),
        qaCalendarhouse(),
        ruDumrf(),
        ruDumrt(),
        saUmmalqura(),
        sdMinistry(),
        seCautious(),
        sgMuis(),
        syAwqaf(),
        tnInm(),
        trDiyanet(),
        trDiyanetEurope(),
        usChicago(),
        usIsna(),
        uzBoard(),
        xkBik(),
        zaCape(),
        zaJamiat(),
        zaMjc(),
        zaVoc(),
    ).associateBy { it.entryId }

    /** The stamp for [entryId], or null where the gate has not checked it (a class D unit still awaiting data). */
    fun of(entryId: String): ProofStamp? = byEntry[entryId]

    private fun aeAwqaf() = ProofStamp(
        entryId = "ae.awqaf",
        places = 10,
        placeDays = 10,
        ramadanDays = 0,
        provenThrough = "2026-09-25",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 3, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun aeIacadDubai() = ProofStamp(
        entryId = "ae.iacad.dubai",
        places = 12,
        placeDays = 20067,
        ramadanDays = 1690,
        provenThrough = "2029-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = aeIacadDubaiUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "Dubai's one table serves the whole city, and Taqwa also keeps each place's own dawn, about a minute earlier in the city's north-east in June", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        provenThroughByUnit = aeIacadDubaiThrough(),
    )

    private fun aeIacadDubaiThrough(): Map<String, String> = mapOf(
        "dubai-rural" to "2026-12-31",
    )

    private fun aeIacadDubaiUnits(): Map<String, Map<String, Int>> = mapOf(
        "dubai" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "dubai-rural" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        "hatta" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 2),
    )

    private fun alKmsh() = ProofStamp(
        entryId = "al.kmsh",
        places = 3,
        placeDays = 780,
        ramadanDays = 63,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = alKmshUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun alKmshUnits(): Map<String, Map<String, Int>> = mapOf(
        "tirana" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun atIggo() = ProofStamp(
        entryId = "at.iggo",
        places = 1,
        placeDays = 184,
        ramadanDays = 19,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = atIggoUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
        ),
    )

    private fun atIggoUnits(): Map<String, Map<String, Int>> = mapOf(
        "at.iggo" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
    )

    private fun auCautious() = ProofStamp(
        entryId = "au.cautious",
        places = 2,
        placeDays = 380,
        ramadanDays = 28,
        provenThrough = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 18, "dhuhr" to 4, "endOfEating" to 2, "fajr" to 2, "isha" to 10, "maghrib" to 1, "sunrise" to 8),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("maghrib")),
            ProofLateLimit(minutes = 2, reason = "Fajr and the end of eating keep each member's own margins: up to 2 min from the tables'.", events = listOf("endOfEating", "fajr")),
            ProofLateLimit(minutes = 4, reason = "Zuhr is the later member's: the TURKEY preset's (+ 5) and LMA's (+ 2 to + 7 by month), up to 4 min after the earlier table's.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 8, reason = "Sunrise is the earlier member's, the TURKEY preset's (the sun's less 7 min): up to 8 min before LMA's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 10, reason = "Isha is the later member's, the TURKEY preset's 17° or LMA's Maghrib + 90: up to 10 min after the other's.", events = listOf("isha")),
            ProofLateLimit(minutes = 18, reason = "Asr is the later member's: LMA's, on its per-month offsets up to 15 min after its own table, and the TURKEY preset's with its allowance for Diyanet's daily model (subtask 7b): up to 18 min after the earlier table's.", events = listOf("asrStandard")),
        ),
    )

    private fun auLma() = ProofStamp(
        entryId = "au.lma",
        places = 1,
        placeDays = 349,
        ramadanDays = 28,
        provenThrough = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 15, "dhuhr" to 3, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = auLmaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 15, reason = "LMA's Asr runs from 12 min before to 22 min after the Shafi'i Asr on a smooth seasonal curve no rule here reproduces; per-month offsets keep it never early, up to 15 min after it within its fastest-moving months (October, December and January).", events = listOf("asrStandard")),
        ),
    )

    private fun auLmaUnits(): Map<String, Map<String, Int>> = mapOf(
        "au.lma" to mapOf("asrStandard" to 15, "dhuhr" to 3, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun baIz() = ProofStamp(
        entryId = "ba.iz",
        places = 3,
        placeDays = 1095,
        ramadanDays = 90,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        worstLateByUnit = baIzUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "IZ BiH prints this town as Sarajevo plus a whole-minute offset for each month, which drifts from the sun within the month; the app follows the month's offset on the safe side, up to 3 min from the printed time.", events = listOf("asrStandard", "endOfEating", "maghrib")),
            ProofLateLimit(minutes = 3, reason = "IZ BiH prints this town as Sarajevo plus a whole-minute offset for each month, which drifts from the sun within the month; the app follows the month's offset on the safe side, up to 3 min from the printed time.", events = listOf("asrStandard", "endOfEating", "maghrib")),
        ),
    )

    private fun baIzUnits(): Map<String, Map<String, Int>> = mapOf(
        "banja-luka" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        "bihac" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        "sarajevo" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 2),
    )

    private fun bdIfb() = ProofStamp(
        entryId = "bd.ifb",
        places = 1,
        placeDays = 71,
        ramadanDays = 6,
        provenThrough = "2026-12-30",
        worstLateMinutes = mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "the district edge makes the fitted sehri end run up to a minute earlier still (task 7f); an early end is the safe side", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "the true-dawn precaution swings across the year and the district edge stacks on it (task 7f)", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "IFB's Isha angle is only documented as about 18° + 1..4 min, and the district edge stacks on it (task 7f)", events = listOf("isha")),
        ),
    )

    private fun beCautious() = ProofStamp(
        entryId = "be.cautious",
        places = 3,
        placeDays = 1201,
        ramadanDays = 87,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 65, "fajr" to 4, "isha" to 53, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "Fajr is the later member's; against Diyanet's Antwerpen and Gent tables (their own units since the monitor round) up to 4 min on the days its spring takdir moves Fajr in steps, and 2 on plain days where EMB's 18° dawn is the later.", events = listOf("fajr")),
            ProofLateLimit(minutes = 53, reason = "Isha is the later member's, EMB's (18°, in summer its clock cap and proportion from 45°): up to 53 min after Diyanet's Antwerpen and Gent tables in July, whose takdir Isha is earlier.", events = listOf("isha")),
            ProofLateLimit(minutes = 65, reason = "The end of eating is the earlier member's, EMB's own dawns (ruling R39): up to 65 min before Diyanet's Antwerpen and Gent imsak in late April and May, when Diyanet's takdir has moved its dawn later.", events = listOf("endOfEating")),
        ),
    )

    private fun beEmb() = ProofStamp(
        entryId = "be.emb",
        places = 1,
        placeDays = 360,
        ramadanDays = 30,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 7, "isha" to 9, "maghrib" to 1, "sunrise" to 2),
        worstLateByUnit = beEmbUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 7, reason = "From mid-May to 1 August EMB holds Fajr at one clock time, and its fast begins then; the curves carry a clock time as the sun's depression, which the day-either-side envelope (ruling R28) moves by a day's change: Fajr up to 7 min after EMB's, the end of eating up to 7 min before it, in May.", events = listOf("endOfEating", "fajr")),
            ProofLateLimit(minutes = 9, reason = "From May to August EMB's Isha stops at a clock time (a few minutes later from July) or follows its own proportion from 45° (about 0.26 of the night in June); the curve takes the later of the clock time and the 45° proportion of 17.5°, never before EMB's, and runs up to 9 min after it in May.", events = listOf("isha")),
        ),
    )

    private fun beEmbUnits(): Map<String, Map<String, Int>> = mapOf(
        "be.emb" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 7, "isha" to 9, "maghrib" to 1, "sunrise" to 2),
    )

    private fun bhCouncil() = ProofStamp(
        entryId = "bh.council",
        places = 9,
        placeDays = 3195,
        ramadanDays = 261,
        provenThrough = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun bnMora() = ProofStamp(
        entryId = "bn.mora",
        places = 1,
        placeDays = 355,
        ramadanDays = 29,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = bnMoraUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun bnMoraUnits(): Map<String, Map<String, Int>> = mapOf(
        "brunei-muara" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun caCautious() = ProofStamp(
        entryId = "ca.cautious",
        places = 6,
        placeDays = 1788,
        ramadanDays = 149,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 5, "dhuhr" to 5, "endOfEating" to 38, "fajr" to 36, "isha" to 77, "maghrib" to 5, "sunrise" to 4),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 4, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Sunrise, the earliest family's, up to 4 min before the others'.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 5, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Each keeps its own tables' minutes (Al Farooq's Zuhr + 5, Al Omari's Maghrib + 6 from August, Asr up to 3 min after the exact one): Zuhr, Asr and Maghrib up to 5 min after the others'.", events = listOf("asrStandard", "dhuhr", "maghrib")),
            ProofLateLimit(minutes = 36, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Fajr, the 15° families', never before theirs, runs up to 36 min after the 18° family's.", events = listOf("fajr")),
            ProofLateLimit(minutes = 38, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. The end of eating, the 18° family's dawn, never after it, runs up to 38 min before the 15° families' Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 77, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Isha is the latest family's: Imam Malik's 16.5° (up to 28 % of the night) in spring and autumn, the 18° family's in winter, up to 77 min after Calgary Islamic Centre SW's Maghrib + 90 and the 18° family's spring Isha.", events = listOf("isha")),
        ),
    )

    private fun caIft() = ProofStamp(
        entryId = "ca.ift",
        places = 1,
        placeDays = 350,
        ramadanDays = 25,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = caIftUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun caIftUnits(): Map<String, Map<String, Int>> = mapOf(
        "ca.ift" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
    )

    private fun caIit() = ProofStamp(
        entryId = "ca.iit",
        places = 1,
        placeDays = 359,
        ramadanDays = 25,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 4, "maghrib" to 4, "sunrise" to 4),
        worstLateByUnit = caIitUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr")),
            ProofLateLimit(minutes = 4, reason = "IIT's own table drifts over its year: its sunrise from 2 min before to 2 min after the sun's, its Maghrib from sunset to sunset + 2.5 (and Isha, 90 min after it, with it); the margins hold it never early all year, so in spring they run up to 4 min.", events = listOf("isha", "maghrib", "sunrise")),
        ),
    )

    private fun caIitUnits(): Map<String, Map<String, Int>> = mapOf(
        "ca.iit" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 4, "maghrib" to 4, "sunrise" to 4),
    )

    private fun caIsna() = ProofStamp(
        entryId = "ca.isna",
        places = 18,
        placeDays = 6414,
        ramadanDays = 510,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 4, "asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 5, "sunrise" to 5),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("dhuhr")),
            ProofLateLimit(minutes = 4, reason = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any: Asr and Isha up to 4 min after the others'.", events = listOf("asrHanafi", "asrStandard", "isha")),
            ProofLateLimit(minutes = 5, reason = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any: Fajr, Maghrib and sunrise and the end of eating up to 5 min from the others'.", events = listOf("endOfEating", "fajr", "maghrib", "sunrise")),
        ),
    )

    private fun caMac() = ProofStamp(
        entryId = "ca.mac",
        places = 1,
        placeDays = 359,
        ramadanDays = 25,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = caMacUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun caMacUnits(): Map<String, Map<String, Int>> = mapOf(
        "ca.mac" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun caToronto() = ProofStamp(
        entryId = "ca.toronto",
        places = 6,
        placeDays = 1400,
        ramadanDays = 50,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 6, "asrStandard" to 6, "dhuhr" to 5, "endOfEating" to 4, "fajr" to 6, "isha" to 7, "maghrib" to 5, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = "Toronto's three tables round and offset their times irregularly against their own rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the latest printed time at the mosques. Sunrise is the earliest of the three, each a little before its own table's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "None of the three tables prints an imsak, so each Fajr is where its fast begins; the end is the earliest member's own dawn less its margin, up to 4 min before the tables' Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "Toronto's three tables round and offset their times irregularly against their own rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the latest printed time at the mosques. West of the mosques the user's own sun is later (at Milton by 2.5 min), and Dhuhr, Asr and Maghrib shown follow it: up to 5, 6 and 5 min.", events = listOf("asrHanafi", "asrStandard", "dhuhr", "maghrib")),
            ProofLateLimit(minutes = 7, reason = "Toronto's three tables round and offset their times irregularly against their own rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the latest printed time at the mosques. West of the mosques the user's own sun is later than the tables' points (at Milton, the reach's western end, by 2.5 min), and Fajr and Isha shown follow it: up to 6 and 7 min.", events = listOf("fajr", "isha")),
        ),
    )

    private fun chFids() = ProofStamp(
        entryId = "ch.fids",
        places = 1,
        placeDays = 32,
        ramadanDays = 0,
        provenThrough = "2026-10-26",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = chFidsUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun chFidsUnits(): Map<String, Map<String, Int>> = mapOf(
        "ch.fids" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun deCautious() = ProofStamp(
        entryId = "de.cautious",
        places = 1,
        placeDays = 61,
        ramadanDays = 0,
        provenThrough = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 43, "isha" to 11, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib")),
            ProofLateLimit(minutes = 2, reason = "Diyanet's European method's own margins (subtask 7b): sunrise up to 2 min before Diyanet's Berlin table.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 5, reason = "The end of eating is the earliest member's, the 18° family's 18.4° dawn (Hamburg's 18.35°, ruling R87), up to 5 min before Diyanet's Berlin imsak.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "Isha is the later member's, VIKZ's: Diyanet's Isha + 10 min by VIKZ's own rule (no VIKZ table is held), up to 11 min after Diyanet's Berlin table.", events = listOf("isha")),
            ProofLateLimit(minutes = 44, reason = "Fajr is the latest member's: the late-dawn family's 13° from September to April (ruling R87), up to 44 min after Diyanet's Berlin table in early September, as its takdir ends, and about 30 in October; in summer VIKZ's, Diyanet's generic European method (VIKZ has no city curves of its own), no earlier than 19 % of the night, up to 12 min after the table.", events = listOf("fajr")),
        ),
    )

    private fun defaultSafe() = ProofStamp(
        entryId = "default.safe",
        places = 15,
        placeDays = 2054,
        ramadanDays = 149,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 4, "asrStandard" to 4, "dhuhr" to 6, "endOfEating" to 5, "fajr" to 12, "isha" to 8, "maghrib" to 6, "sunrise" to 7),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 5, reason = "this region's fast begins 2 min before the 18° dawn, and JAKIM prints Kuala Lumpur's Subuh for its zone's latest point, rounded up: up to 5 min before it (end-of-eating audit, 3 Oct 2026, safe-default.tsv)", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 15, reason = "this region's 18° Isha is deeper (later) than some real authorities' own shallower Isha angle (Dar al-Ifta Cairo's 17.5°, Task 7h, safe-default.tsv) — never early, only later by more than the class default", events = listOf("isha")),
            ProofLateLimit(minutes = 20, reason = "no local convention is known here; nearby authorities elsewhere run deeper safety minutes than this region's flat 1986 baseline (Task 7h, safe-default.tsv)", events = listOf("asrHanafi", "asrStandard", "dhuhr", "fajr", "maghrib", "sunrise")),
        ),
    )

    private fun dzMarw() = ProofStamp(
        entryId = "dz.marw",
        places = 5,
        placeDays = 1783,
        ramadanDays = 145,
        provenThrough = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 6, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = dzMarwUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "The ministry prints Tamanrasset as Adrar's table plus one difference each half month, which does not follow Tamanrasset's own Asr through the half month; so that the Asr computed at its seat is never before that printed time, it is up to 6 min after it on some days.", events = listOf("asrStandard")),
        ),
    )

    private fun dzMarwUnits(): Map<String, Map<String, Int>> = mapOf(
        "1" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "11" to mapOf("asrStandard" to 6, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "16" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "17" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "31" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 2),
    )

    private fun egEsa() = ProofStamp(
        entryId = "eg.esa",
        places = 30,
        placeDays = 2637,
        ramadanDays = 30,
        provenThrough = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = egEsaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "ESA computes Taba's table about half a degree west of Taba, and Taqwa keeps Taba's own earlier sunrise and dawn", events = listOf("endOfEating", "sunrise")),
        ),
    )

    private fun egEsaUnits(): Map<String, Map<String, Int>> = mapOf(
        "alexandria" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "aswan" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bawiti" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "cairo" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dahab" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "damietta" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "edfu" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "el-arish" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "el-dabaa" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "el-tor" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "farafra" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "halaib" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "hurghada" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "kharga" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "luxor" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "marsa-matruh" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "mut" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "nuweiba" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "port-said" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "quseir" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "rafah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ras-gharib" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        "saint-catherine" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sallum" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "shalatin" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sharm-el-sheikh" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sidi-barrani" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "siwa" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "suez" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "taba" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 3),
    )

    private fun frCautious() = ProofStamp(
        entryId = "fr.cautious",
        places = 3,
        placeDays = 1163,
        ramadanDays = 88,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 77, "fajr" to 56, "isha" to 88, "maghrib" to 3, "sunrise" to 8),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = "Each family's tables differ among themselves (Drancy's and Al-Amel's Asr swing by 3-5 min over the year; the 12-13° family prints Zuhr + 5 and Maghrib + 3..4); each member takes its family's latest. Dhuhr runs up to 2 min after the latest of them.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 3, reason = "Each family's tables differ among themselves (Drancy's and Al-Amel's Asr swing by 3-5 min over the year; the 12-13° family prints Zuhr + 5 and Maghrib + 3..4); each member takes its family's latest. Maghrib (capped at the 12–13° family's + 4) runs up to 3 min after the latest of them.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 4, reason = "Each family's tables differ among themselves (Drancy's and Al-Amel's Asr swing by 3-5 min over the year; the 12-13° family prints Zuhr + 5 and Maghrib + 3..4); each member takes its family's latest. Asr (Diyanet's + 4 the latest) runs up to 4 min after the latest of the three tables held.", events = listOf("asrStandard")),
            ProofLateLimit(minutes = 8, reason = "Sunrise is the earliest member's, Diyanet's (the sun's less 7): up to 8 min before the three families' tables, which print the sun's own.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 56, reason = "The 12–13° family prints Fajr from about 12° to 13° (Drancy's at 12.7°); its member takes 12°, the family's latest, never before any of them, so against Drancy's table Fajr runs up to 7 min late; from late May to June Diyanet's Paris table (its takdir, a member since ruling R87) is the latest, up to 16 min after Drancy's. Against Diyanet's own Lyon and Lille tables (their own units since the monitor round) the 12° family's Fajr is the later on most days, up to 56 min after Diyanet's 18° at Lyon in autumn and winter.", events = listOf("fajr")),
            ProofLateLimit(minutes = 77, reason = "The end of eating is the earliest member's: from late April to August the flat 15° family's 15° dawn, which Al-Amel's table (the family's one held) leaves for a later summer Fajr of its own, up to 29 min before the earliest printed Fajr of the three tables in June at Paris and up to 77 min before Diyanet's own Lille table's imsak in late May, when its takdir has moved its dawn later; the rest of the year Diyanet's table's dawn (its 18° and takdir, a member since ruling R87), up to 26 min before the Grande Mosquée's own Fajr in April and August and about 12 in winter, and where the Grande Mosquée's page changes method its own dawn (ruling R80: 16 min on 1 and 2 January, 8 on 29 and 30 September).", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 88, reason = "The flat 15° family prints Isha at 15° (in a Paris June after midnight), while the Grande Mosquée's is sunset + 90 and Drancy's and Al-Amel's summer Isha follow earlier rules of their own; Isha is the latest member's, so in summer it runs up to 38 min after the latest of those three tables at Paris, and up to 88 min after Diyanet's own Lille table in June, whose takdir Isha is earlier still.", events = listOf("isha")),
        ),
    )

    private fun frGmp() = ProofStamp(
        entryId = "fr.gmp",
        places = 1,
        placeDays = 374,
        ramadanDays = 30,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 16, "fajr" to 2, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = frGmpUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "fajr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "From October the Grande Mosquée's page still carries its earlier method, whose Isha moves from about 15.3° to 16° over October; Isha is taken at the later of its 2026 method and 16°, never before either, and so runs up to 5 min after the earlier method's in early October.", events = listOf("isha")),
            ProofLateLimit(minutes = 16, reason = "The GMP prints no imsak, so the fast begins at its own Fajr (ruling R80), within 4 min of it (the end holds for everyone within its table's reach, where its rule's dawn to the south comes up to 2 min earlier in June) but where its page changes method (the Moonsighting Committee's from January to September, its earlier method from October): each change is carried over the two days either side, so the end comes up to 16 min before its printed Fajr on 1 and 2 January and 8 before it on 29 and 30 September.", events = listOf("endOfEating")),
        ),
    )

    private fun frGmpUnits(): Map<String, Map<String, Int>> = mapOf(
        "fr.gmp" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 16, "fajr" to 2, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
    )

    private fun gbLondonLupt() = ProofStamp(
        entryId = "gb.london.lupt",
        places = 11,
        placeDays = 4015,
        ramadanDays = 330,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 3, "asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 5, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = gbLondonLuptUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("dhuhr")),
            ProofLateLimit(minutes = 3, reason = "The M25 follows one table computed at its point; at the M25's western edge the user's own sun is 1.6 min later, and Asr and Maghrib shown follow it.", events = listOf("asrHanafi", "asrStandard", "maghrib")),
            ProofLateLimit(minutes = 3, reason = "The M25 follows one table computed at its point; at the M25's eastern edge the user's own sunrise is 1.7 min earlier, and the sunrise shown follows it.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "The M25 follows one table computed at its point; at its eastern edge the user's own dawn is 1.7 min earlier, and at its northern edge up to 3 min earlier in summer, and the end of eating shown follows it.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "The M25 follows one table computed at its point, and Fajr and Isha shown are the later of the point's and the user's own: at the M25's southern and western edges the user's Fajr, and at its northern and north-western edges the user's Isha, at the table's depression run up to 3 min later than the point's in summer.", events = listOf("fajr", "isha")),
        ),
    )

    private fun gbLondonLuptUnits(): Map<String, Map<String, Int>> = mapOf(
        "gb.london.lupt" to mapOf("asrHanafi" to 3, "asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 5, "maghrib" to 3, "sunrise" to 3),
    )

    private fun gbWifaqul() = ProofStamp(
        entryId = "gb.wifaqul",
        places = 1,
        placeDays = 7,
        ramadanDays = 0,
        provenThrough = "2026-09-30",
        worstLateMinutes = mapOf("asrHanafi" to 0, "dhuhr" to 5, "endOfEating" to 0, "fajr" to 1, "isha" to 0, "maghrib" to 6, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "endOfEating", "fajr", "isha", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "Wifaqul Ulama documents Zuhr at the meridian + 4 and Maghrib at sunset + 5; Manchester Central Mosque, the only table of theirs held, prints the meridian and sunset themselves, so against it Dhuhr runs up to 5 min and Maghrib up to 6 min late.", events = listOf("dhuhr", "maghrib")),
        ),
    )

    private fun idKemenag() = ProofStamp(
        entryId = "id.kemenag",
        places = 18,
        placeDays = 9855,
        ramadanDays = 801,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = idKemenagUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        provenThroughByUnit = idKemenagThrough(),
    )

    private fun idKemenagThrough(): Map<String, String> = mapOf(
        "0119" to "2026-12-31",
        "0313" to "2026-12-31",
        "0816" to "2026-12-31",
        "1208" to "2026-12-31",
        "1222" to "2026-12-31",
        "1429" to "2026-12-31",
        "1630" to "2026-12-31",
        "1709" to "2026-12-31",
        "3308" to "2026-12-31",
    )

    private fun idKemenagUnits(): Map<String, Map<String, Int>> = mapOf(
        "0119" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "0228" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "0313" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "0816" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1208" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1219" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1222" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1301" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1429" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1433" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1505" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1630" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1634" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1638" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "1709" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "2622" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "3308" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "3329" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun ieCautious() = ProofStamp(
        entryId = "ie.cautious",
        places = 1,
        placeDays = 365,
        ramadanDays = 30,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("dhuhr")),
            ProofLateLimit(minutes = 2, reason = "Each member keeps its own margins (IFI's times at its table's minute, the ICCI's rounding), and Maghrib is capped at IFI's where the two spread: starts up to 2 min after the later table's, sunrise up to 2 before the earlier's.", events = listOf("asrStandard", "fajr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "The end of eating is the earlier member's: the ICCI's own rule read as an end (ruling R80), but for most of June, when IFI's own dawn is the earlier; the ICCI's, taken by month and day over the leap cycle, runs up to 3 min before the earlier of the two tables' Fajr in July and August.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "Isha is the later member's; the ICCI's rule on the curve's slots (ruling R28) runs up to 5 min after its table in May.", events = listOf("isha")),
        ),
    )

    private fun ieIcci() = ProofStamp(
        entryId = "ie.icci",
        places = 1,
        placeDays = 365,
        ramadanDays = 30,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = ieIcciUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "The ICCI's code keeps Fajr no earlier than 18/60 of the night before sunrise and Isha no later than 17/60 after sunset (adhan-js's TwilightAngle rule); on the curve's slots each is widened over the day before and after (ruling R28), most when the rule takes over, in late April and May: Isha up to 5 min after its table.", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "The ICCI's code keeps Fajr no earlier than 18/60 of the night before sunrise and Isha no later than 17/60 after sunset (adhan-js's TwilightAngle rule); on the curve's slots each is widened over the day before and after (ruling R28), most when the rule takes over, in late April and May: Fajr up to 7 min after its table.", events = listOf("fajr")),
        ),
    )

    private fun ieIcciUnits(): Map<String, Map<String, Int>> = mapOf(
        "ie.icci" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
    )

    private fun ieIfi() = ProofStamp(
        entryId = "ie.ifi",
        places = 1,
        placeDays = 365,
        ramadanDays = 30,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = ieIfiUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun ieIfiUnits(): Map<String, Map<String, Int>> = mapOf(
        "ie.ifi" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
    )

    private fun inKarachi() = ProofStamp(
        entryId = "in.karachi",
        places = 1,
        placeDays = 30,
        ramadanDays = 0,
        provenThrough = "2026-09-30",
        worstLateMinutes = mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun iqSunni() = ProofStamp(
        entryId = "iq.sunni",
        places = 1,
        placeDays = 1,
        ramadanDays = 0,
        provenThrough = "2025-12-04",
        worstLateMinutes = mapOf("asrStandard" to 0, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 0, "isha" to 0, "maghrib" to 0, "sunrise" to 0),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun joAwqaf() = ProofStamp(
        entryId = "jo.awqaf",
        places = 1,
        placeDays = 171,
        ramadanDays = 2,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = joAwqafUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun joAwqafUnits(): Map<String, Map<String, Int>> = mapOf(
        "jo.awqaf.amman" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun kgDefault() = ProofStamp(
        entryId = "kg.default",
        places = 1,
        placeDays = 1,
        ramadanDays = 0,
        provenThrough = "2026-09-26",
        worstLateMinutes = mapOf("asrHanafi" to 0, "dhuhr" to 0, "endOfEating" to 0, "fajr" to 0, "isha" to 0, "maghrib" to 0, "sunrise" to 0),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun kwAwqaf() = ProofStamp(
        entryId = "kw.awqaf",
        places = 1,
        placeDays = 25,
        ramadanDays = 4,
        provenThrough = "2026-09-25",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun kzQmdb() = ProofStamp(
        entryId = "kz.qmdb",
        places = 164,
        placeDays = 119720,
        ramadanDays = 9676,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = kzQmdbUnits1() + kzQmdbUnits2() + kzQmdbUnits3(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "the same AngleBased residual in Isha from July to mid-August, up to 5 min from Astana to Pavlodar and 6 from 54.3N to QMDB's northernmost place, Isakovka (task 7f, fix rounds of 3 and 6 Oct 2026)", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "the end of eating keeps clear of QMDB's AngleBased residual, so on its other days it comes up to 8 min before the printed Fajr at and above 48N, as far north as Isakovka, and up to 4 from 46N to 48N (end-of-eating audit, 3 Oct 2026; north-west round, 6 Oct 2026)", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 8, reason = "QMDB's AngleBased Fajr runs ahead of the model from about 46N, around the solstice there and from late April to early June in the north, more the farther north: up to 4 min from 46N to 48N, 5 to 7 from 48N to 54.4N, 8 from 54.5N to Isakovka, QMDB's northernmost place (task 7f, fix rounds of 3 and 6 Oct 2026)", events = listOf("fajr")),
        ),
    )

    private fun kzQmdbUnits1(): Map<String, Map<String, Int>> = mapOf(
        "aiteke-bi" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "akkol" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "aksay" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "aksu-pavlodar" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "aksu-turkistan" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "aktau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "aktobe" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "almaty" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "altay" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "aral" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "arkalyk" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "arys" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "astana" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "atbasar" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "atyrau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "ayagoz" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "baikonur" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "balkhash" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "boralday" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bugrovoe" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "ekibastuz" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "embi" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "isakovka" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "kalbatau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "kandyagash" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "karaganda" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "karatau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "kentau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "khromtau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 6, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "kokshetau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "konaev" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "kostanay" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "krasny-yar" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "kulomzino" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "kulsary" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "kyzylorda" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "lenger" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "lisakovsk" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "makanshy" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "makat" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
    )

    private fun kzQmdbUnits2(): Map<String, Map<String, Int>> = mapOf(
        "makinsk" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "mamyrsu" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "mangystau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "merke" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "oral" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "oskemen" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "otegen-batyr" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "oteshqali" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "pavlodar" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "petropavl" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "pulemetovka" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "ridder" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "rudny" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "saran" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "sarkand" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "saryagash" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sarykemer" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "satpayev" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "semey" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "shakhtinsk" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "shalqar" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "shardara" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "shelek" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "shemonaikha" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        "shiyeli" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "shu" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "shymkent" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "spasovka" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "stepnogorsk" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 7, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "taldykorgan" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "talgar" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "taraz" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tekeli" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "temirtau" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 6, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "turar-ryskulov" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "turkistan" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ushtobe" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "vagulino" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 8, "fajr" to 8, "isha" to 6, "maghrib" to 1, "sunrise" to 1),
        "zaysan" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "zhanakorgan" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun kzQmdbUnits3(): Map<String, Map<String, Int>> = mapOf(
        "zhanaozen" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zhanatas" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zharkent" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zhenis" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zhetysay" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zhezkazgan" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "zhosaly" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun lbFatwa() = ProofStamp(
        entryId = "lb.fatwa",
        places = 1,
        placeDays = 382,
        ramadanDays = 31,
        provenThrough = "2026-10-01",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 3, "endOfEating" to 8, "fajr" to 9, "isha" to 7, "maghrib" to 7, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "sunrise")),
            ProofLateLimit(minutes = 7, reason = "the same margin runs to 7 min late on Maghrib and Isha on their worst held-out day", events = listOf("isha", "maghrib")),
            ProofLateLimit(minutes = 8, reason = "the fast begins at the 20° dawn, never after Dar al-Fatwa's printed Fajr, whose own dawn runs 19.1° to 20.0°: up to 8 min before it on the shallowest days (end-of-eating audit, 3 Oct 2026)", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 9, reason = "the perpetual table's single never-early margin, over 384 measured days across two sources and seven years, runs to 9 min late on Fajr on its worst day", events = listOf("fajr")),
        ),
    )

    private fun lyAwqaf() = ProofStamp(
        entryId = "ly.awqaf",
        places = 22,
        placeDays = 119,
        ramadanDays = 3,
        provenThrough = "2026-10-12",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = lyAwqafUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "imsak", "isha", "maghrib", "sunrise")),
        ),
        provenThroughByUnit = lyAwqafThrough(),
    )

    private fun lyAwqafThrough(): Map<String, String> = mapOf(
        "ajdabiya" to "2026-10-05",
        "awjila" to "2026-10-05",
        "bani-walid" to "2026-10-05",
        "bayda" to "2026-10-05",
        "benghazi" to "2026-10-05",
        "brega" to "2026-10-05",
        "derna" to "2026-10-05",
        "emsaed" to "2026-10-05",
        "ghadames" to "2026-10-05",
        "hun" to "2026-10-05",
        "jalu" to "2026-10-05",
        "khoms" to "2026-10-05",
        "kufra" to "2026-10-05",
        "marj" to "2026-10-05",
        "misrata" to "2026-10-05",
        "ras-lanuf" to "2026-10-05",
        "sabha" to "2026-10-05",
        "sirte" to "2026-10-05",
        "tobruk" to "2026-10-05",
        "zawiya" to "2026-10-05",
        "zliten" to "2026-10-05",
    )

    private fun lyAwqafUnits(): Map<String, Map<String, Int>> = mapOf(
        "ajdabiya" to mapOf("asrStandard" to 2, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "awjila" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 2, "sunrise" to 2),
        "bani-walid" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 2),
        "bayda" to mapOf("asrStandard" to 3, "dhuhr" to 2, "isha" to 2, "sunrise" to 0),
        "benghazi" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "brega" to mapOf("asrStandard" to 2, "dhuhr" to 2, "isha" to 2, "sunrise" to 2),
        "derna" to mapOf("asrStandard" to 1, "dhuhr" to 2, "isha" to 1, "sunrise" to 2),
        "emsaed" to mapOf("asrStandard" to 2, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "ghadames" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "imsak" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "hun" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 1, "sunrise" to 3),
        "jalu" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 2, "sunrise" to 2),
        "khoms" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "kufra" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "marj" to mapOf("asrStandard" to 2, "dhuhr" to 2, "isha" to 2, "sunrise" to 2),
        "misrata" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ras-lanuf" to mapOf("asrStandard" to 2, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "sabha" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 0, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "sirte" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "tobruk" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "tripoli" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        "zawiya" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 1),
        "zliten" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 2),
    )

    private fun maHabous() = ProofStamp(
        entryId = "ma.habous",
        places = 40,
        placeDays = 2379,
        ramadanDays = 126,
        provenThrough = "2026-10-12",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "isha" to 2, "maghrib" to 6, "sunrise" to 7),
        worstLateByUnit = maHabousUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "This city's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for other places does not apply here.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha")),
            ProofLateLimit(minutes = 6, reason = "Beyond the cities whose own Habous table Taqwa fits, the place's elevation is not known, so Maghrib takes a national margin that covers the highest town checked (Imilchil, about 2,150 m): at a low town it is up to 6 min after Habous's Maghrib.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 7, reason = "Beyond the cities whose own Habous table Taqwa fits, the place's elevation is not known, so sunrise takes a national margin that covers the highest town checked (Imilchil, about 2,150 m): at a low town it is up to 7 min before Habous's sunrise.", events = listOf("sunrise")),
        ),
    )

    private fun maHabousUnits(): Map<String, Map<String, Int>> = mapOf(
        "casablanca" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dakhla" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "figuig" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ifrane" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "laayoune" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "lagouira" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "midelt" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "oujda" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "rabat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tangier" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun meIzcg() = ProofStamp(
        entryId = "me.izcg",
        places = 1,
        placeDays = 30,
        ramadanDays = 0,
        provenThrough = "2026-09-30",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun mrMinistry() = ProofStamp(
        entryId = "mr.ministry",
        places = 2,
        placeDays = 38,
        ramadanDays = 38,
        provenThrough = "2024-04-09",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "imsak" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "imsak", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun myJakim() = ProofStamp(
        entryId = "my.jakim",
        places = 13,
        placeDays = 9489,
        ramadanDays = 766,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = myJakimUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "Kangar's printed sunrise spreads a further minute beyond a plain classic-noon model across the seasons", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "e-solat prints each zone's Subuh for its latest point, rounded up, and the fast here begins at the dawn of the place itself: up to 4 min before the printed Subuh (end-of-eating audit, 3 Oct 2026)", events = listOf("endOfEating")),
        ),
    )

    private fun myJakimUnits(): Map<String, Map<String, Int>> = mapOf(
        "JHR01" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "JHR02" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "KTN01" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "PHG01" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "PHG06" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 1),
        "PLS01" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 3),
        "PNG01" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "SBH01" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "SBH07" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "SGR01" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "SWK08" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "WLY01" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "WLY02" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun nlCautious() = ProofStamp(
        entryId = "nl.cautious",
        places = 1,
        placeDays = 31,
        ramadanDays = 0,
        provenThrough = "2026-10-25",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 16, "isha" to 6, "maghrib" to 8, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "The members' own margins: the end of eating (Diyanet's earliest dawns) up to 2 min before the tables held.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "Isha is the latest member's, MWL's 17°, whose own table is not held here: up to 6 min after the Moroccan calendar's and Diyanet's.", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "Maghrib is capped at the Moroccan member's, which carries the Turkish and Arab calendars' Maghrib as a floor (ruling R87: 7 of 12 surveyed calendars print Diyanet's, sunset + 6 in spring to + 9 in autumn on the exact sun), so against the Moroccan calendar, which prints sunset + 1..4, it runs up to 8 min late.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 16, reason = "The Moroccan calendar's Fajr moves from about 16° in winter to 14° in May and August; its member takes 14°, the calendar's latest, never before it, and so runs up to 16 min after it in autumn (Task 7g checked it at Amsterdam from 25 September to 25 October).", events = listOf("fajr")),
        ),
    )

    private fun noCautious() = ProofStamp(
        entryId = "no.cautious",
        places = 5,
        placeDays = 2648,
        ramadanDays = 205,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 7, "asrStandard" to 7, "dhuhr" to 6, "endOfEating" to 57, "fajr" to 101, "isha" to 135, "maghrib" to 4, "sunrise" to 58),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 4, reason = "Maghrib is capped at IRN's (the most followed) where the members spread: up to 4 min after it (at Trondheim in June, where IRN's monthly minutes run ahead of its calendar).", events = listOf("maghrib")),
            ProofLateLimit(minutes = 6, reason = "Zuhr is the later member's (IRN's + 4 to + 9, Diyanet's + 5): up to 5 min after the tables', and beyond IRN's calendars, where its edge keeps + 10, up to 6 after Diyanet's Kristiansand table.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 8, reason = "Asr is the later member's, in the later school (the school is not known): up to 8 min after IRN's Trondheim Asr.", events = listOf("asrHanafi", "asrStandard")),
            ProofLateLimit(minutes = 57, reason = "The end of eating is the earlier member's: IRN's own earliest dawns (ruling R39) at Oslo, up to 55 min before Diyanet's imsak and 26 before IRN's Fajr; at Trondheim Diyanet's own table's dawn, up to 55 before IRN's printed Fajr in spring; at Bergen up to 57 before Diyanet's imsak in August.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 58, reason = "Sunrise is the earlier member's, the sun's own less Diyanet's 7 (IRN's is up to 2 min before the sun's): up to 10 min before IRN's Trondheim sunrise, and from late May to late July up to 58 min before Diyanet's own Trondheim table, which holds the day to about nineteen hours around Dhuhr and prints its sunrise up to 53 min after the sun's (the app keeps the sun's, the safe side).", events = listOf("sunrise")),
            ProofLateLimit(minutes = 101, reason = "Fajr is the later member's. At IRN's own calendars Diyanet's European takdir on its own city curves: up to 54 min after IRN's Trondheim calendar in June and 48 after its Oslo calendar. Beyond them, at Diyanet's Kristiansand, Stavanger and Bergen tables (their own units since the monitor round), IRN's edge (its hour before sunrise from April to September, else 16°) is the later: up to 101 min after Diyanet's 18° in spring and autumn.", events = listOf("fajr")),
            ProofLateLimit(minutes = 135, reason = "Isha is the later member's. At IRN's own calendars Diyanet's European takdir on its own city curves: up to 34 min after IRN's calendars (Trondheim's in spring, Oslo's 29). Beyond them, at Diyanet's Kristiansand, Stavanger and Bergen tables, IRN's edge (15°, else the proportion from 45°) is the later: up to 135 min after Diyanet's takdir Isha in spring and summer.", events = listOf("isha")),
        ),
    )

    private fun noIrn() = ProofStamp(
        entryId = "no.irn",
        places = 3,
        placeDays = 1095,
        ramadanDays = 90,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 7, "asrStandard" to 10, "dhuhr" to 6, "endOfEating" to 235, "fajr" to 11, "isha" to 237, "maghrib" to 313, "sunrise" to 42),
        worstLateByUnit = noIrnUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "asrStandard", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "IRN's Isha is each calendar's own curve (ruling R48), widened over the day before and after (ruling R28): up to 4 min after the calendar's.", events = listOf("isha")),
            ProofLateLimit(minutes = 5, reason = "IRN's Zuhr is + 4 in winter and + 9 from spring to autumn, changing within March and September; those two months take the later, up to 5 min after the calendar's before the change.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 6, reason = "IRN's Zuhr at Tromsø is noon + 5, + 10 from 21 March to 20 September; until its 2027 calendar is held the change is taken a day early and a day late: up to 6 min after IRN's on 20 March and 21 September.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 6, reason = "IRN's Fajr is each calendar's own curve (ruling R48), widened over the day before and after (ruling R28): when it moves fastest, in spring and autumn, up to 6 min after the calendar's.", events = listOf("fajr")),
            ProofLateLimit(minutes = 10, reason = "IRN prints no imsak, so the fast begins at its Fajr; the end reads IRN's own earliest dawns (ruling R39), each the earliest of the day before, the day and the day after: where its Fajr moves fastest, in April as the summer rule begins, up to 10 min before its printed Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 36, reason = "IRN's Asr at Tromsø is the later of the shadow and midway between noon and its own Maghrib less 5; with its window dates taken a day either way until its 2027 calendar is held, up to 10 min after IRN's on the Maghrib blends' first and last days (16 April, 28 August) and 7 in the winter blends. In a year whose clocks go back on 31 October (2027, 2032, 2038) the day-early autumn blend starts on summer time: up to 36 in early November.", events = listOf("asrHanafi", "asrStandard")),
            ProofLateLimit(minutes = 42, reason = "IRN prints noon as Tromsø's sunrise in the polar night and keeps it, or its own later sunrise, for some days after the sun returns: there the sunrise shown (IRN's calculator's) is up to 42 min before IRN's in January and 17 in November; elsewhere up to 4.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 56, reason = "IRN's window dates at Tromsø are read from its 2026 calendar alone, so until its 2027 calendar is held each is taken a day early and a day late and the later start kept: on the Fajr blends' first and last days (22 April, 21 August) Fajr runs up to 11 min after IRN's, and up to 5 in the winter blends. In a year whose clocks go back on 31 October (2027, 2032, 2038) the day-early autumn blend starts on summer time: up to 56 min after IRN's in early November, less than 10 from about 23 November.", events = listOf("fajr")),
            ProofLateLimit(minutes = 235, reason = "IRN prints no imsak, so the fast begins at its Fajr. Where its Makkah-time Fajr at Tromsø falls after the sun has risen (late April to mid-May and late July to mid-August; declared not followed) the fast begins at the unit's own dawn before sunrise, up to 235 min before IRN's Fajr; elsewhere up to 5.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 237, reason = "Where IRN's Makkah-time Isha comes before the real sunset at Tromsø (late April to mid-August), Isha follows the Maghrib shown, up to 237 min after IRN's; otherwise, with the window dates taken a day either way, up to 12 on the Isha blends' edge days (23 April, 19-20 August) and 5 in the winter blends, and up to 65 in early November of a year whose clocks go back on 31 October (2027, 2032, 2038), when the day-early blend starts on summer time.", events = listOf("isha")),
            ProofLateLimit(minutes = 313, reason = "From mid-April to late August IRN's Maghrib at Tromsø is Makkah's sunset + 5, while the sun there sets hours later: Maghrib never comes before the real sunset, up to 313 min after IRN's (mid-May and late July); in the winter blends, with the window dates taken a day either way, up to 12, and in early November of a year whose clocks go back on 31 October (2027, 2032, 2038), when the day-early blend starts on summer time, up to 71.", events = listOf("maghrib")),
        ),
    )

    private fun noIrnUnits(): Map<String, Map<String, Int>> = mapOf(
        "no.irn.oslo" to mapOf("asrHanafi" to 2, "asrStandard" to 2, "dhuhr" to 5, "endOfEating" to 10, "fajr" to 6, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "no.irn.tromso" to mapOf("asrHanafi" to 7, "asrStandard" to 10, "dhuhr" to 6, "endOfEating" to 235, "fajr" to 11, "isha" to 237, "maghrib" to 313, "sunrise" to 42),
        "no.irn.trondheim" to mapOf("asrHanafi" to 2, "asrStandard" to 2, "dhuhr" to 5, "endOfEating" to 10, "fajr" to 6, "isha" to 4, "maghrib" to 2, "sunrise" to 2),
    )

    private fun nzFianz() = ProofStamp(
        entryId = "nz.fianz",
        places = 1,
        placeDays = 122,
        ramadanDays = 19,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = nzFianzUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun nzFianzUnits(): Map<String, Map<String, Int>> = mapOf(
        "auckland" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun omMara() = ProofStamp(
        entryId = "om.mara",
        places = 1,
        placeDays = 488,
        ramadanDays = 30,
        provenThrough = "2031-09-30",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = omMaraUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun omMaraUnits(): Map<String, Map<String, Int>> = mapOf(
        "muscat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun otherTurkey() = ProofStamp(
        entryId = "other.turkey",
        places = 6,
        placeDays = 2418,
        ramadanDays = 174,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun pkKarachi() = ProofStamp(
        entryId = "pk.karachi",
        places = 1,
        placeDays = 91,
        ramadanDays = 0,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 3, "asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun psGazaAwqaf() = ProofStamp(
        entryId = "ps.gaza.awqaf",
        places = 1,
        placeDays = 6,
        ramadanDays = 0,
        provenThrough = "2020-07-21",
        worstLateMinutes = mapOf("asrStandard" to 0, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 0),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun psIftaa() = ProofStamp(
        entryId = "ps.iftaa",
        places = 29,
        placeDays = 22634,
        ramadanDays = 1839,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = psIftaaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun psIftaaUnits(): Map<String, Map<String, Int>> = mapOf(
        "ps.iftaa.acre" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.beersheba" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.beisan" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.beit-awwa" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.bethlehem" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.deir-al-balah" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.dura" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.gaza" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.haifa" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.hebron" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.idhna" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.jaffa" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.jenin" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.jericho" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.jerusalem" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.kafr-qasim" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.khan-yunis" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.lydd" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.nablus" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.nazareth" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.qalqilya" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.rafah" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.ramallah" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.ramla" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.safed" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.tayibe" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.tiberias" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.tulkarm" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "ps.iftaa.umm-al-fahm" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
    )

    private fun qaCalendarhouse() = ProofStamp(
        entryId = "qa.calendarhouse",
        places = 8,
        placeDays = 2963,
        ramadanDays = 243,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 3, "maghrib" to 3, "sunrise" to 1),
        worstLateByUnit = qaCalendarhouseUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "Al Khor follows Doha's calendar, and Taqwa also keeps Al Khor's own later summer sunset with the calendar's Maghrib + 3 (its Ramadan Maghrib, kept all year)", events = listOf("isha", "maghrib")),
        ),
    )

    private fun qaCalendarhouseUnits(): Map<String, Map<String, Int>> = mapOf(
        "al-khor" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 3, "maghrib" to 3, "sunrise" to 1),
        "doha" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
    )

    private fun ruDumrf() = ProofStamp(
        entryId = "ru.dumrf",
        places = 1,
        placeDays = 107,
        ramadanDays = 0,
        provenThrough = "2026-09-25",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 1, "sunrise" to 2),
        worstLateByUnit = ruDumrfUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "The end of suhur is DUM RF's own printed dawn, which from May to August moves by up to a quarter of a degree of the sun a day; the app takes the earliest of the neighbouring days (rulings R28, R39), up to 4 min before the printed time (May and late July to mid-August).", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "From May to August DUM RF's Fajr is sunrise − 0.30 of the night, when the sun stands up to a quarter of a degree higher or lower than the day before; the app takes the latest of the neighbouring days so that it holds in every year of the leap cycle (ruling R28), up to 4 min after the printed time (May, and late July to mid-August).", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "From May to August DUM RF's Isha is sunset + a fraction of the night that it steps every few days (0.25 in early May, 0.30 in June, 0.25 again by August); between its held days the app takes the largest fraction within three days and the latest of the neighbouring days (ruling R28), up to 5 min after the printed time (early and mid-May).", events = listOf("isha")),
        ),
    )

    private fun ruDumrfUnits(): Map<String, Map<String, Int>> = mapOf(
        "moscow" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 1, "sunrise" to 2),
    )

    private fun ruDumrt() = ProofStamp(
        entryId = "ru.dumrt",
        places = 19,
        placeDays = 6935,
        ramadanDays = 570,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 132, "fajr" to 126, "isha" to 62, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = ruDumrtUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrHanafi", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "On the nights either side of the white nights the dawn falls minutes from the sun's lowest point and moves by minutes with the town's point, which DUM RT does not publish, and the daily limit lets the end of sahur shown move at most 20 min later a night; it is kept on the safe side of the town's table, up to 3 min early.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 3, reason = "On the nights either side of the white nights the dawn falls minutes from the sun's lowest point and moves by minutes with the town's point, which DUM RT does not publish, and the daily limit lets the end of sahur shown move at most 20 min later a night; it is kept on the safe side of the town's table, up to 3 min early.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 3, reason = "Zainsk's table runs later than its app point gives (its point is not published); its Fajr keeps the later margin its own table needs on the night of 6 May, up to 3 min late on other days.", events = listOf("fajr")),
            ProofLateLimit(minutes = 12, reason = "On 8 August 2026 the sun sinks to 18.008° at Yelabuga, too close to call, so the end of sahur is the sun's lowest point, 8 min before the dawn DUM RT prints; the next night's dawn comes 24 min later and the daily limit lets the end of sahur shown move at most 20 min later a night, 12 min before the printed time.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 62, reason = "On a night whose sun sinks to within 0.01° of 18° at the town (Zelenodolsk 5 May and Yelabuga 8 August in 2026), whether DUM RT applies its white-night rule turns on its own sun, which the app cannot match that closely, so each time takes its safe side: Fajr at sunrise − 121, the end of sahur at the sun's lowest point and Isha at the later of Maghrib + 90 and the 15° dusk, up to this many minutes from the time printed.", events = listOf("isha")),
            ProofLateLimit(minutes = 126, reason = "On a night whose sun sinks to within 0.01° of 18° at the town (Zelenodolsk 5 May and Yelabuga 8 August in 2026), whether DUM RT applies its white-night rule turns on its own sun, which the app cannot match that closely, so each time takes its safe side: Fajr at sunrise − 121, the end of sahur at the sun's lowest point and Isha at the later of Maghrib + 90 and the 15° dusk, up to this many minutes from the time printed.", events = listOf("fajr")),
            ProofLateLimit(minutes = 132, reason = "On a night whose sun sinks to within 0.01° of 18° at the town (Zelenodolsk 5 May and Yelabuga 8 August in 2026), whether DUM RT applies its white-night rule turns on its own sun, which the app cannot match that closely, so each time takes its safe side: Fajr at sunrise − 121, the end of sahur at the sun's lowest point and Isha at the later of Maghrib + 90 and the 15° dusk, up to this many minutes from the time printed.", events = listOf("endOfEating")),
        ),
    )

    private fun ruDumrtUnits(): Map<String, Map<String, Int>> = mapOf(
        "agryz" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "almetyevsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "arsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "aznakayevo" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bavly" to mapOf("asrHanafi" to 1, "dhuhr" to 0, "endOfEating" to 3, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bugulma" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "buinsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "chistopol" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "kazan" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "kukmor" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "leninogorsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "mendeleyevsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "menzelinsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "naberezhnye-chelny" to mapOf("asrHanafi" to 1, "dhuhr" to 0, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "nizhnekamsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "nurlat" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "yelabuga" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 12, "fajr" to 126, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zainsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 3, "fajr" to 3, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "zelenodolsk" to mapOf("asrHanafi" to 2, "dhuhr" to 0, "endOfEating" to 132, "fajr" to 2, "isha" to 62, "maghrib" to 1, "sunrise" to 1),
    )

    private fun saUmmalqura() = ProofStamp(
        entryId = "sa.ummalqura",
        places = 267,
        placeDays = 210254,
        ramadanDays = 17027,
        provenThrough = "2030-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 5, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = saUmmalquraUnits1() + saUmmalquraUnits2() + saUmmalquraUnits3() + saUmmalquraUnits4() + saUmmalquraUnits5(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "isha", "maghrib")),
            ProofLateLimit(minutes = 2, reason = "21 km south-south-west of KACST's point for Taif, at the app's Ash Shafa, the user's own sun sets later than at Taif's point, so Asr, Maghrib and Isha come up to 2 min after Taif's table on 3 days of 2026-27", events = listOf("asrStandard", "isha", "maghrib")),
            ProofLateLimit(minutes = 2, reason = "on the few dates a year Umm al-Qura's calendar repeats the previous day's times, Taqwa shows the later start and the earlier sunrise and end of eating of the two days, so as never to be early if the calendar stops repeating them", events = listOf("endOfEating", "fajr", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "5 km south-west of KACST's point for Al Hofuf, at the app's own point for the city, the user's own sun sets later than at KACST's, so Maghrib and Isha come 2 min after Al Hofuf's table on 1 day of 2026-27", events = listOf("isha", "maghrib")),
            ProofLateLimit(minutes = 2, reason = "7 km south-west of KACST's point for Madinah, at the app's Sultanah, the user's own sun sets later than at Madinah's point, so Maghrib and Isha come 2 min after Madinah's table on 1 day of 2025-27", events = listOf("isha", "maghrib")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "isha", "maghrib")),
            ProofLateLimit(minutes = 3, reason = "the same table computed a degree north of Tayma: the sunrise and the end of eating shown in the town, the earlier of that table's and the town's own, come up to 3 min before the table's (class D's 3, not the lag dates' 2; city-points round, 9 Oct 2026)", events = listOf("endOfEating", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "KACST's list places Tayma a degree north of the town, so its Tayma table is computed 111 km north of it, and the Fajr shown in the town, the later of that table's and the town's own, comes up to 5 min after the table in spring and autumn (city-points round, 9 Oct 2026)", events = listOf("fajr")),
        ),
        provenThroughByUnit = saUmmalquraThrough(),
    )

    private fun saUmmalquraThrough(): Map<String, String> = mapOf(
        "abanat" to "2027-12-31",
        "abha" to "2027-12-31",
        "abqaiq" to "2027-12-31",
        "abu-arish" to "2027-12-31",
        "ad-dilam" to "2027-12-31",
        "ad-diriyah" to "2027-12-31",
        "addayer" to "2027-12-31",
        "adham" to "2027-12-31",
        "afif" to "2027-12-31",
        "ahad-al-masarihah" to "2027-12-31",
        "ahad-rafidah" to "2027-12-31",
        "al-aflaj" to "2027-12-31",
        "al-ahsa" to "2027-12-31",
        "al-amwah" to "2027-12-31",
        "al-aqiq" to "2027-12-31",
        "al-ardhiyat" to "2027-12-31",
        "al-aridhah" to "2027-12-31",
        "al-bada" to "2027-12-31",
        "al-badayea" to "2027-12-31",
        "al-bahah" to "2027-12-31",
        "al-bayda" to "2027-12-31",
        "al-birk" to "2027-12-31",
        "al-bukayriyah" to "2027-12-31",
        "al-busur" to "2027-12-31",
        "al-duwadimi" to "2027-12-31",
        "al-edabi" to "2027-12-31",
        "al-farshah" to "2027-12-31",
        "al-ghat" to "2027-12-31",
        "al-ghazalah" to "2027-12-31",
        "al-hait" to "2027-12-31",
        "al-hajrah" to "2027-12-31",
        "al-harajah" to "2027-12-31",
        "al-hariq" to "2027-12-31",
        "al-hayr" to "2027-12-31",
        "al-henakiyah" to "2027-12-31",
        "al-hofuf" to "2027-12-31",
        "al-is" to "2027-12-31",
        "al-jubail" to "2027-12-31",
        "al-jumum" to "2027-12-31",
        "al-kamil" to "2027-12-31",
        "al-kharj" to "2027-12-31",
        "al-kharkhir" to "2027-12-31",
        "al-khobar" to "2027-12-31",
        "al-khurma" to "2027-12-31",
        "al-lith" to "2027-12-31",
        "al-majmaah" to "2027-12-31",
        "al-makhwah" to "2027-12-31",
        "al-mithnab" to "2027-12-31",
        "al-mubarraz" to "2027-12-31",
        "al-muwayh" to "2027-12-31",
        "al-muzahimiyah" to "2027-12-31",
        "al-namas" to "2027-12-31",
        "al-qatif" to "2027-12-31",
        "al-qunfudhah" to "2027-12-31",
        "al-qurayyat" to "2027-12-31",
        "al-quwaiiyah" to "2027-12-31",
        "al-quwarah" to "2027-12-31",
        "al-reeth" to "2027-12-31",
        "al-udayd" to "2027-12-31",
        "al-ula" to "2027-12-31",
        "al-uwayqiliyah" to "2027-12-31",
        "al-uyaynah" to "2027-12-31",
        "al-wajh" to "2027-12-31",
        "alasyah" to "2027-12-31",
        "aldarb" to "2027-12-31",
        "alharth" to "2027-12-31",
        "almajaridah" to "2027-12-31",
        "almandaq" to "2027-12-31",
        "alqura" to "2027-12-31",
        "an-nabhaniyah" to "2027-12-31",
        "ar-rass" to "2027-12-31",
        "ar-rayn" to "2027-12-31",
        "arar" to "2027-12-31",
        "as-sulaymi" to "2027-12-31",
        "as-sulayyil" to "2027-12-31",
        "ash-shamli" to "2027-12-31",
        "ash-shimasiyah" to "2027-12-31",
        "ash-shinan" to "2027-12-31",
        "at-tuwal" to "2027-12-31",
        "az-zulfi" to "2027-12-31",
        "badr" to "2027-12-31",
        "badr-al-janoub" to "2027-12-31",
        "bahrah" to "2027-12-31",
        "baish" to "2027-12-31",
        "baljurashi" to "2027-12-31",
        "bani-hasan" to "2027-12-31",
        "baqaa" to "2027-12-31",
        "bariq" to "2027-12-31",
        "belqarn" to "2027-12-31",
        "bishah" to "2027-12-31",
        "buraydah" to "2027-12-31",
        "damad" to "2027-12-31",
        "dammam" to "2027-12-31",
        "dariyah" to "2027-12-31",
        "dhahban" to "2027-12-31",
        "dhahran" to "2027-12-31",
        "dhahran-al-janub" to "2027-12-31",
        "dhurma" to "2027-12-31",
        "duba" to "2027-12-31",
        "dumah-al-jandal" to "2027-12-31",
        "farasan-island" to "2027-12-31",
        "fayfa" to "2027-12-31",
        "ghamid-az-zinad" to "2027-12-31",
        "hafar-al-batin" to "2027-12-31",
        "hail" to "2027-12-31",
        "haql" to "2027-12-31",
        "harub" to "2027-12-31",
        "hawtat-sudayr" to "2027-12-31",
        "howtat-bani-tamim" to "2027-12-31",
        "hubuna" to "2027-12-31",
        "huraymila" to "2027-12-31",
        "jalajil" to "2027-12-31",
        "jazan" to "2027-12-31",
        "jeddah" to "2027-12-31",
        "khafji" to "2027-12-31",
        "khamis-mushait" to "2027-12-31",
        "khaybar" to "2027-12-31",
        "khbash" to "2027-12-31",
        "khulais" to "2027-12-31",
        "layla" to "2027-12-31",
        "madinah" to "2027-12-31",
        "mahd-al-thahab" to "2027-12-31",
        "malham" to "2027-12-31",
        "marat" to "2027-12-31",
        "mawqaq" to "2027-12-31",
        "maysan" to "2027-12-31",
        "muhayil" to "2027-12-31",
        "nairyah" to "2027-12-31",
        "najran" to "2027-12-31",
        "qaryat-al-ulya" to "2027-12-31",
        "qilwah" to "2027-12-31",
        "rabigh" to "2027-12-31",
        "rafha" to "2027-12-31",
        "ranyah" to "2027-12-31",
        "ras-tannurah" to "2027-12-31",
        "ras-tanura" to "2027-12-31",
        "rawdat-sudayr" to "2027-12-31",
        "riyadh-al-khabra" to "2027-12-31",
        "rojal" to "2027-12-31",
        "rumah" to "2027-12-31",
        "sabya" to "2027-12-31",
        "sakaka" to "2027-12-31",
        "samtah" to "2027-12-31",
        "sarat-abidah" to "2027-12-31",
        "shagra" to "2027-12-31",
        "sharorah" to "2027-12-31",
        "simira" to "2027-12-31",
        "sudus" to "2027-12-31",
        "suwayr" to "2027-12-31",
        "tabuk" to "2027-12-31",
        "taif" to "2027-12-31",
        "tanumah" to "2027-12-31",
        "tarib" to "2027-12-31",
        "tarut" to "2027-12-31",
        "tathleeth" to "2027-12-31",
        "tayma" to "2027-12-31",
        "thadig" to "2027-12-31",
        "thar" to "2027-12-31",
        "thuwal" to "2027-12-31",
        "tubarjal" to "2027-12-31",
        "tumair" to "2027-12-31",
        "turbah" to "2027-12-31",
        "umluj" to "2027-12-31",
        "unayzah" to "2027-12-31",
        "uqlat-as-suqur" to "2027-12-31",
        "uyun-al-jawa" to "2027-12-31",
        "wadi-ad-dawasir" to "2027-12-31",
        "wadi-al-fara" to "2027-12-31",
        "yadamah" to "2027-12-31",
        "yanbu" to "2027-12-31",
    )

    private fun saUmmalquraUnits1(): Map<String, Map<String, Int>> = mapOf(
        "abanat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "abha" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "abqaiq" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "abu-arish" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ad-dilam" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ad-diriyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "addayer" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "adham" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "afif" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ahad-al-masarihah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ahad-rafidah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-aflaj" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-ahsa" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-amwah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-aqiq" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-ardhiyat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-aridhah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-bada" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-badayea" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-bahah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-bayda" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-birk" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-bukayriyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-busur" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-duwadimi" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-edabi" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-farshah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-ghat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-ghazalah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-hait" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-hajrah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-harajah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-hariq" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-hayr" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-henakiyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-hofuf" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-is" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-jubail" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-jumum" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-kamil" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun saUmmalquraUnits2(): Map<String, Map<String, Int>> = mapOf(
        "al-kharj" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-kharkhir" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-khobar" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-khurma" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-lith" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-majmaah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-makhwah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-mithnab" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-mubarraz" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-muwayh" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-muzahimiyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-namas" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-qatif" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-qunfudhah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-qurayyat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-quwaiiyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-quwarah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-reeth" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-udayd" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "al-ula" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-uwayqiliyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-uyaynah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "al-wajh" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "alasyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "aldarb" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "alharth" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "almajaridah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "almandaq" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "alqura" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "an-nabhaniyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ar-rass" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ar-rayn" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "arar" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "as-sulaymi" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "as-sulayyil" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ash-shamli" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ash-shimasiyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ash-shinan" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "at-tuwal" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "az-zulfi" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun saUmmalquraUnits3(): Map<String, Map<String, Int>> = mapOf(
        "badr" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "badr-al-janoub" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bahrah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "baish" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "baljurashi" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bani-hasan" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "baqaa" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "bariq" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "belqarn" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "bishah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "buraydah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "damad" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dammam" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dariyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dhahban" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dhahran" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "dhahran-al-janub" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "dhurma" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "duba" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "dumah-al-jandal" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "farasan-island" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "fayfa" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "ghamid-az-zinad" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "hafar-al-batin" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "hail" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "haql" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "harub" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "hawtat-sudayr" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "howtat-bani-tamim" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "hubuna" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "huraymila" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "jalajil" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "jazan" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "jeddah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "khafji" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "khamis-mushait" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "khaybar" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "khbash" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "khulais" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "layla" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun saUmmalquraUnits4(): Map<String, Map<String, Int>> = mapOf(
        "madinah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        "mahd-al-thahab" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "makkah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "malham" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "marat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "mawqaq" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "maysan" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "muhayil" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "nairyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "najran" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "qaryat-al-ulya" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "qilwah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "rabigh" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "rafha" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ranyah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ras-tannurah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ras-tanura" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "rawdat-sudayr" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "riyadh" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "riyadh-al-khabra" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "rojal" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "rumah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "sabya" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sakaka" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "samtah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sarat-abidah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "shagra" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "sharorah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "simira" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sudus" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "suwayr" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "tabuk" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "taif" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        "tanumah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tarib" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tarut" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tathleeth" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tayma" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 5, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "thadig" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "thar" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun saUmmalquraUnits5(): Map<String, Map<String, Int>> = mapOf(
        "thuwal" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tubarjal" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "tumair" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "turaif" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "turbah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "umluj" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "unayzah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "uqlat-as-suqur" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "uyun-al-jawa" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "wadi-ad-dawasir" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "wadi-al-fara" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "yadamah" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "yanbu" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
    )

    private fun sdMinistry() = ProofStamp(
        entryId = "sd.ministry",
        places = 1,
        placeDays = 30,
        ramadanDays = 30,
        provenThrough = "2022-05-01",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 2, "isha" to 20, "maghrib" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "fajr", "maghrib")),
            ProofLateLimit(minutes = 7, reason = "News reports of Sudan's 2026 imsakiya, not the table itself, put its dawn near 19.4°, earlier than the 18.3° of the one table held (Khartoum, Ramadan 1443); until a current table is held the fast begins at the 19.5° dawn, up to 7 min before that table's Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 20, reason = "A 2026 Ramadan imsakiya circulating under the Fiqh Academy's name (not the verified 2022 table) puts Isha at Maghrib + 90; on the entry's Ramadan dates only, Isha never earlier than that, up to 20 min after the 2022 table's 18.0° angle.", events = listOf("isha")),
        ),
    )

    private fun seCautious() = ProofStamp(
        entryId = "se.cautious",
        places = 14,
        placeDays = 6034,
        ramadanDays = 447,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 6, "dhuhr" to 4, "endOfEating" to 154, "fajr" to 109, "isha" to 170, "maghrib" to 126, "sunrise" to 129),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 5, reason = "Zuhr is the later member's, Diyanet's (+ 5): up to 5 min after Rabita's at Helsinki.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 7, reason = "Asr is the later member's, in the later school (the school is not known): Diyanet's, up to 7 min after Rabita's at Helsinki.", events = listOf("asrStandard")),
            ProofLateLimit(minutes = 109, reason = "Fajr is the later member's, Diyanet's European takdir on its own city curves (Helsinki's and Copenhagen's since the monitor round): up to 109 min after Rabita's Helsinki calendar in summer; at Stockholm 6. Rabita's own Fajr follows its calendar.", events = listOf("fajr")),
            ProofLateLimit(minutes = 126, reason = "Maghrib is capped at Diyanet's (its sunset + 7) where the members spread: up to 2 min after it, and from late May to late July, where Diyanet holds its tables' day to nineteen hours around Dhuhr and prints its Maghrib before the sun sets, never before the real sunset: up to 4 min after its Turku table and 126 after its Luleå table.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 129, reason = "Sunrise is the earlier member's, Diyanet's (the sun's less 7): up to 6 min before Rabita's at Helsinki, and from late May to late July, where Diyanet holds its tables' day to nineteen hours around Dhuhr and prints its sunrise after the sun's, up to 11 min before its Helsinki table and 129 before its Luleå table (the app keeps the sun's, the safe side).", events = listOf("sunrise")),
            ProofLateLimit(minutes = 154, reason = "The end of eating is the earlier member's: Rabita's own earliest dawns, near the middle of the night from April to August (ruling R39), applied at Copenhagen and Stockholm, up to 137 and 136 min before Diyanet's imsak there, and up to 154 before its Malmö and Aarhus tables in August; at Helsinki up to 25 min before Rabita's own Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 170, reason = "Isha is the later member's, Rabita's own model: 17° while the sun reaches it, near midnight in late spring and late summer, up to 155 min after Rabita's calendar at Helsinki and 136 after Diyanet's Stockholm table, 170 after Diyanet's Luleå table in April and September (its takdir Isha comes earlier the further north).", events = listOf("isha")),
        ),
    )

    private fun sgMuis() = ProofStamp(
        entryId = "sg.muis",
        places = 1,
        placeDays = 1096,
        ramadanDays = 89,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "MUIS's own Standard Asr is not reproduced to the minute; a further minute of drift near the equinoxes past class B's own margin", events = listOf("asrStandard")),
        ),
    )

    private fun syAwqaf() = ProofStamp(
        entryId = "sy.awqaf",
        places = 2,
        placeDays = 2,
        ramadanDays = 1,
        provenThrough = "2026-10-01",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 0, "isha" to 0, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun tnInm() = ProofStamp(
        entryId = "tn.inm",
        places = 7,
        placeDays = 1546,
        ramadanDays = 124,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = tnInmUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        provenThroughByUnit = tnInmThrough(),
    )

    private fun tnInmThrough(): Map<String, String> = mapOf(
        "ben-guerdane" to "2026-12-30",
        "tabarka" to "2026-12-30",
        "tala" to "2026-12-30",
    )

    private fun tnInmUnits(): Map<String, Map<String, Int>> = mapOf(
        "ben-guerdane" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "sfax" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tabarka" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tala" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "tataouine" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "tunis" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun trDiyanet() = ProofStamp(
        entryId = "tr.diyanet",
        places = 22,
        placeDays = 8866,
        ramadanDays = 638,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = trDiyanetUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun trDiyanetUnits(): Map<String, Map<String, Int>> = mapOf(
        "20089" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9146" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9206" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9352" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9419" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9451" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9507" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9522" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9541" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9587" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9594" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9879" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "9930" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun trDiyanetEurope() = ProofStamp(
        entryId = "tr.diyanet.europe",
        places = 33,
        placeDays = 13174,
        ramadanDays = 957,
        provenThrough = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 70, "fajr" to 7, "isha" to 7, "maghrib" to 126, "sunrise" to 129),
        worstLateByUnit = trDiyanetEuropeUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 4 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 4, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 4 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 5, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 5, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 6, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after. Stockholm's Fajr also waits for Islamiska Förbundet's pages, a minute later than Diyanet's own table on some days: up to 6 min after Diyanet's in April and August.", events = listOf("fajr")),
            ProofLateLimit(minutes = 6, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 6, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 6, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 7, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 7, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 7 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 8, reason = "Diyanet prints Oslo's June and July sunrise up to 7 min after the sun's own (its rule is not published); the app keeps the sun's, the safe side.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 11, reason = "Diyanet prints Helsinki's June and early July sunrise up to 10 min after the sun's own (its rule is not published); the app keeps the sun's, the safe side: up to 11 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 13, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 13 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 14, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 14 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 17, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 17 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 28, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 28 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 32, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 32 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 35, reason = "In June Diyanet's imsak here, its Fajr under its nineteen-hour day, comes after the sun has risen; the end of eating stays before the Fajr shown, which stays before the sunrise (declared not followed), and around those days comes up to 35 min before Diyanet's imsak.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 41, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 41 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 50, reason = "From late May to late July Diyanet's Trondheim table holds the day to about nineteen hours around Dhuhr and prints its Maghrib up to 43 min before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 50 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 58, reason = "From late May to late July Diyanet's Trondheim table holds the day to about nineteen hours around Dhuhr and prints its sunrise up to 53 min after the sun's own; the app keeps the sun's, the safe side: up to 58 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 59, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 59 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 62, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 62 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 70, reason = "In June Diyanet's imsak here, its Fajr under its nineteen-hour day, comes after the sun has risen; the end of eating stays before the Fajr shown, which stays before the sunrise (declared not followed), and around those days comes up to 70 min before Diyanet's imsak.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 94, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 94 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 97, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 97 min before Diyanet's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 126, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its Maghrib before the sun sets; Maghrib never comes before the real sunset (the sun's plus Diyanet's 7 min): up to 126 min after Diyanet's.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 129, reason = "From late May to late July Diyanet holds this table's day to about nineteen hours around Dhuhr and prints its sunrise after the sun's own; the app keeps the sun's, the safe side: up to 129 min before Diyanet's.", events = listOf("sunrise")),
        ),
    )

    private fun trDiyanetEuropeUnits(): Map<String, Map<String, Int>> = mapOf(
        "tr.diyanet.europe.aalborg" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.aarhus" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.amsterdam" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.antwerpen" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.bergen" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 5, "maghrib" to 4, "sunrise" to 13),
        "tr.diyanet.europe.berlin" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.brussels" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.copenhagen" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.freiburg" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.gent" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.goteborg" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.helsinki" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 11),
        "tr.diyanet.europe.kristiansand" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.lille" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.london" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.lulea" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 70, "fajr" to 7, "isha" to 7, "maghrib" to 126, "sunrise" to 129),
        "tr.diyanet.europe.lyon" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.malmo" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.munich" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.oslo" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 8),
        "tr.diyanet.europe.oulu" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 35, "fajr" to 7, "isha" to 7, "maghrib" to 94, "sunrise" to 97),
        "tr.diyanet.europe.paris" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.sarajevo" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.stavanger" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.stockholm" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 6, "isha" to 4, "maghrib" to 1, "sunrise" to 2),
        "tr.diyanet.europe.sundsvall" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 5, "maghrib" to 32, "sunrise" to 41),
        "tr.diyanet.europe.tampere" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 17, "sunrise" to 28),
        "tr.diyanet.europe.trondheim" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 6, "fajr" to 6, "isha" to 5, "maghrib" to 50, "sunrise" to 58),
        "tr.diyanet.europe.turku" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 4, "sunrise" to 14),
        "tr.diyanet.europe.umea" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 6, "fajr" to 6, "isha" to 6, "maghrib" to 59, "sunrise" to 62),
        "tr.diyanet.europe.uppsala" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 7),
        "tr.diyanet.europe.wien" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "tr.diyanet.europe.zurich" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
    )

    private fun usChicago() = ProofStamp(
        entryId = "us.chicago",
        places = 7,
        placeDays = 1253,
        ramadanDays = 30,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 5, "dhuhr" to 6, "endOfEating" to 32, "fajr" to 31, "isha" to 5, "maghrib" to 6, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Sunrise up to 3 min before theirs.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 5, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Asr and Isha up to 5 min.", events = listOf("asrHanafi", "isha")),
            ProofLateLimit(minutes = 6, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Zuhr and Maghrib up to 6 min.", events = listOf("dhuhr", "maghrib")),
            ProofLateLimit(minutes = 31, reason = "Chicago's mosques differ on Fajr: the 15° mosques' Fajr, never before it, runs up to 31 min after the 18° block's (Villa Park, Makki, DarusSalam).", events = listOf("fajr")),
            ProofLateLimit(minutes = 32, reason = "Chicago's mosques differ on Fajr: the end of eating is the 18° block's dawn, never after it, up to 32 min before the 15° mosques' Fajr (MCC, MEC, Mosque Foundation, Orland Park).", events = listOf("endOfEating")),
        ),
    )

    private fun usIsna() = ProofStamp(
        entryId = "us.isna",
        places = 35,
        placeDays = 8617,
        ramadanDays = 628,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 5, "asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 6, "sunrise" to 4),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 4, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: ADAMS moves Fajr and sunrise 2 min earlier (its safe direction): Fajr up to 4 min after its, and sunrise and the end of eating, never after its, up to 4 min before the others'.", events = listOf("endOfEating", "fajr", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Asr and Isha, a minute or two after the angle at some tables (ADAMS's Isha 2 min later by its rule), up to 5 min after the others'.", events = listOf("asrHanafi", "asrStandard", "isha")),
            ProofLateLimit(minutes = 6, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Maghrib up to 6 min after the tables that add nothing.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 7, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Zuhr up to 7 min after the tables that add nothing.", events = listOf("dhuhr")),
        ),
    )

    private fun uzBoard() = ProofStamp(
        entryId = "uz.board",
        places = 1,
        placeDays = 19,
        ramadanDays = 0,
        provenThrough = "2026-09-26",
        worstLateMinutes = mapOf("asrHanafi" to 1, "dhuhr" to 6, "endOfEating" to 1, "fajr" to 1, "isha" to 4, "maghrib" to 1, "sunrise" to 5),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "endOfEating", "fajr", "maghrib")),
            ProofLateLimit(minutes = 4, reason = "the margin also covers both the 15° and 15.5° Isha eras together (task 7f)", events = listOf("isha")),
            ProofLateLimit(minutes = 5, reason = "the margin also covers the pre-Jul-2026 rule's earlier sunrise (task 7f); an early end is the safe side", events = listOf("sunrise")),
            ProofLateLimit(minutes = 6, reason = "the margin also covers the pre-Jul-2026 rule's Dhuhr + 5 min next to the new rule's own (task 7f)", events = listOf("dhuhr")),
        ),
    )

    private fun xkBik() = ProofStamp(
        entryId = "xk.bik",
        places = 2,
        placeDays = 730,
        ramadanDays = 60,
        provenThrough = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 6, "dhuhr" to 4, "endOfEating" to 3, "fajr" to 1, "isha" to 3, "maghrib" to 5, "sunrise" to 5),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("endOfEating", "fajr", "isha")),
            ProofLateLimit(minutes = 4, reason = "The Takvimi is an old perpetual table whose minutes step unevenly from day to day; the app follows each month of it on the safe side, with half a minute more for the towns' whole-minute offsets, and so runs up to this many minutes from its printed time on some days.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 5, reason = "The Takvimi is an old perpetual table whose minutes step unevenly from day to day; the app follows each month of it on the safe side, with half a minute more for the towns' whole-minute offsets, and so runs up to this many minutes from its printed time on some days.", events = listOf("maghrib", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "The Takvimi is an old perpetual table whose minutes step unevenly from day to day; the app follows each month of it on the safe side, with half a minute more for the towns' whole-minute offsets, and so runs up to this many minutes from its printed time on some days.", events = listOf("asrStandard")),
        ),
    )

    private fun zaCape() = ProofStamp(
        entryId = "za.cape",
        places = 1,
        placeDays = 61,
        ramadanDays = 0,
        provenThrough = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 4, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha")),
            ProofLateLimit(minutes = 2, reason = "Sunrise is the earliest of the three members', each a little before its own table's: up to 2 min before the MJC's printed sunrise.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "The three tables print Maghrib from sunset to sunset + 3; Maghrib is the latest member's, never before any, so on the days the MJC prints it 3 min before the others it runs up to 4 min after the MJC's.", events = listOf("maghrib")),
        ),
    )

    private fun zaJamiat() = ProofStamp(
        entryId = "za.jamiat",
        places = 9,
        placeDays = 14973,
        ramadanDays = 1198,
        provenThrough = "2029-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        worstLateByUnit = zaJamiatUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrHanafi", "asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        provenThroughByUnit = zaJamiatThrough(),
    )

    private fun zaJamiatThrough(): Map<String, String> = mapOf(
        "pretoria" to "2026-12-31",
    )

    private fun zaJamiatUnits(): Map<String, Map<String, Int>> = mapOf(
        "bloemfontein" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "cape-town" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 1),
        "durban" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "johannesburg" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "pietersburg/polokwane" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "port-elizabeth" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        "pretoria" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        "springbok-ncape" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        "upington" to mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun zaMjc() = ProofStamp(
        entryId = "za.mjc",
        places = 1,
        placeDays = 61,
        ramadanDays = 0,
        provenThrough = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = zaMjcUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun zaMjcUnits(): Map<String, Map<String, Int>> = mapOf(
        "za.mjc" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun zaVoc() = ProofStamp(
        entryId = "za.voc",
        places = 1,
        placeDays = 271,
        ramadanDays = 15,
        provenThrough = "2027-06-05",
        worstLateMinutes = mapOf("asrHanafi" to 2, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        worstLateByUnit = zaVocUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
    )

    private fun zaVocUnits(): Map<String, Map<String, Int>> = mapOf(
        "za.voc" to mapOf("asrHanafi" to 2, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
    )
}
