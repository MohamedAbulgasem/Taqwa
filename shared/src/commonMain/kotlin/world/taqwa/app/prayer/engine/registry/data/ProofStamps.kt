// GENERATED FILE — do not edit by hand.
//
// Written by `./gradlew -p tools/timetables generateProofStamps` (GenerateProofStamps.kt) from
// every `tools/timetables/official/stamps/<entry>.json`. Re-run it after every stamp refresh (a
// `gate` run that changes a stamp) and commit the result: stamps are statistics only — place-days,
// dates, minute counts — never an official time, so this file is safe to publish (spec §5).
package world.taqwa.app.prayer.engine.registry.data

/**
 * One registry entry's proof, as the About-times screen shows it (spec §2.3, task 11): how many
 * place-days and places the gate has checked it on, the last date any row holds (`lastChecked`, the
 * stamp's `last`, null before any stamp exists: the end of the envelope, never a "checked through"
 * claim, since the days before it may have holes), the worst lateness seen per event over the whole entry
 * (`worstLateMinutes`, event keys as the gate writes them: "fajr", "sunrise", "dhuhr", "asr" or
 * "asrStandard"/"asrHanafi", "maghrib", "isha", "endOfEating", "imsak") and per authority unit
 * (`worstLateByUnit`, keyed by the unit's id; empty for an entry the gate checks without units),
 * and the late limits the gate held the entry to when the stamp was written ([ProofLateLimit],
 * ruling R41): the class's own (A 1, B 2, C 1 after the latest member, D 3), listed with a null
 * reason, and each recorded exception with its reason, each with the events it covered.
 * `checked` is the days any row compared, as the stamp's runs ("2026-09-25..2026-11-01", or one
 * date), and `checkedByUnit` a unit's own runs where they are not the entry's (ruling R115): About's
 * "checked through" date is the end of the run holding the day it is read on, so it never runs past a
 * day the place's own table was not checked on, as its city page's date never does.
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
    val lastChecked: String?,
    val worstLateMinutes: Map<String, Int>,
    val worstLateByUnit: Map<String, Map<String, Int>>,
    val lateLimits: List<ProofLateLimit>,
    val checked: List<String> = emptyList(),
    val checkedByUnit: Map<String, List<String>> = emptyMap(),
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
        lastChecked = "2026-09-25",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 3, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-25"),
    )

    private fun aeIacadDubai() = ProofStamp(
        entryId = "ae.iacad.dubai",
        places = 12,
        placeDays = 20797,
        ramadanDays = 1748,
        lastChecked = "2029-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = aeIacadDubaiUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "Dubai's one table serves the whole city, and Taqwa also keeps each place's own dawn, about a minute earlier in the city's north-east in June", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2022-01-01..2025-06-26", "2026-01-01..2029-12-31"),
        checkedByUnit = aeIacadDubaiChecked(),
    )

    private fun aeIacadDubaiChecked(): Map<String, List<String>> = mapOf(
        "dubai-rural" to listOf("2022-01-01..2025-06-26", "2026-01-01..2027-12-31"),
        "hatta" to listOf("2023-01-01..2025-06-26", "2026-01-01..2029-12-31"),
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
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = alKmshUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun alKmshUnits(): Map<String, Map<String, Int>> = mapOf(
        "tirana" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun atIggo() = ProofStamp(
        entryId = "at.iggo",
        places = 1,
        placeDays = 184,
        ramadanDays = 19,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = atIggoUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves the dawn by several minutes a day here, in steps; the end of eating takes the earliest of three neighbouring days so that the leap cycle cannot make it late, and so comes before the printed time by as much as a day's step on those days.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "In spring Diyanet's takdir moves Fajr by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Fajr early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("fajr")),
            ProofLateLimit(minutes = 4, reason = "In spring and in August Diyanet's takdir moves Isha by several minutes a day here, in steps; its curve takes the latest of three neighbouring days so that the leap cycle cannot make Isha early, and so runs after the printed time by as much as a day's step on those days. On its takdir days the curve is the latest moment Diyanet's own could be under its rounding to the minute, so that the next year's rounding cannot make it early, and shows the printed minute or the one after.", events = listOf("isha")),
        ),
        checked = listOf("2026-03-01..2026-03-31", "2026-05-01..2026-07-31", "2026-09-01..2026-09-30", "2026-12-01..2026-12-31"),
    )

    private fun atIggoUnits(): Map<String, Map<String, Int>> = mapOf(
        "at.iggo" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
    )

    private fun auCautious() = ProofStamp(
        entryId = "au.cautious",
        places = 3,
        placeDays = 729,
        ramadanDays = 56,
        lastChecked = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 18, "dhuhr" to 4, "endOfEating" to 2, "fajr" to 2, "isha" to 10, "maghrib" to 1, "sunrise" to 9),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("maghrib")),
            ProofLateLimit(minutes = 2, reason = "Fajr and the end of eating keep each member's own margins: up to 2 min from the tables'.", events = listOf("endOfEating", "fajr")),
            ProofLateLimit(minutes = 4, reason = "Zuhr is the later member's: the TURKEY preset's (+ 5) and LMA's (+ 2 to + 7 by month), up to 4 min after the earlier table's.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 9, reason = "Sunrise is the earlier member's, the TURKEY preset's (the sun's less 7 min): up to 8 min before LMA's at Lakemba, and up to 9 at the app's own Sydney, 12 km east of it, where the user's own sun rises earlier.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 10, reason = "Isha is the later member's, the TURKEY preset's 17° or LMA's Maghrib + 90: up to 10 min after the other's.", events = listOf("isha")),
            ProofLateLimit(minutes = 18, reason = "Asr is the later member's: LMA's, on its per-month offsets up to 15 min after its own table, and the TURKEY preset's with its allowance for Diyanet's daily model (subtask 7b): up to 18 min after the earlier table's.", events = listOf("asrStandard")),
        ),
        checked = listOf("2026-06-16..2026-12-01", "2026-12-03", "2026-12-05..2026-12-31", "2027-01-02..2027-01-30", "2027-02-01..2027-03-01", "2027-03-03..2027-04-29", "2027-05-01..2027-06-05"),
    )

    private fun auLma() = ProofStamp(
        entryId = "au.lma",
        places = 1,
        placeDays = 349,
        ramadanDays = 28,
        lastChecked = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 15, "dhuhr" to 3, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = auLmaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 15, reason = "LMA's Asr runs from 12 min before to 22 min after the Shafi'i Asr on a smooth seasonal curve no rule here reproduces; per-month offsets keep it never early, up to 15 min after it within its fastest-moving months (October, December and January).", events = listOf("asrStandard")),
        ),
        checked = listOf("2026-06-16..2026-12-01", "2026-12-03", "2026-12-05..2026-12-31", "2027-01-02..2027-01-30", "2027-02-01..2027-03-01", "2027-03-03..2027-04-29", "2027-05-01..2027-06-05"),
    )

    private fun auLmaUnits(): Map<String, Map<String, Int>> = mapOf(
        "au.lma" to mapOf("asrStandard" to 15, "dhuhr" to 3, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun baIz() = ProofStamp(
        entryId = "ba.iz",
        places = 3,
        placeDays = 2190,
        ramadanDays = 177,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        worstLateByUnit = baIzUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "IZ BiH prints this town as Sarajevo plus a whole-minute offset for each month, which drifts from the sun within the month; the app follows the month's offset on the safe side, up to 3 min from the printed time.", events = listOf("asrStandard", "endOfEating", "maghrib")),
            ProofLateLimit(minutes = 3, reason = "IZ BiH prints this town as Sarajevo plus a whole-minute offset for each month, which drifts from the sun within the month; the app follows the month's offset on the safe side, up to 3 min from the printed time.", events = listOf("asrStandard", "endOfEating", "maghrib")),
        ),
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun baIzUnits(): Map<String, Map<String, Int>> = mapOf(
        "banja-luka" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        "bihac" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        "sarajevo" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 2),
    )

    private fun bdIfb() = ProofStamp(
        entryId = "bd.ifb",
        places = 1,
        placeDays = 142,
        ramadanDays = 11,
        lastChecked = "2027-12-30",
        worstLateMinutes = mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 3, "sunrise" to 4),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "maghrib")),
            ProofLateLimit(minutes = 4, reason = "IFB's Isha angle is only documented as about 18° + 1..4 min, and the district edge stacks on it (task 7f)", events = listOf("isha")),
            ProofLateLimit(minutes = 4, reason = "the district edge puts sunrise up to a minute before Dhaka's (task 7f), and the permanent table against the leap cycle a minute more on a day of 2027; an early end is the safe side", events = listOf("sunrise")),
            ProofLateLimit(minutes = 5, reason = "the district edge makes the fitted sehri end run up to a minute earlier still (task 7f), and the permanent table against the leap cycle a minute more on a day of 2027; an early end is the safe side", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "the true-dawn precaution swings across the year and the district edge stacks on it (task 7f); the permanent table prints the same month-day times every year while the sun shifts through the leap cycle, a minute more on a day of 2027", events = listOf("fajr")),
        ),
        checked = listOf("2026-01-01", "2026-01-06", "2026-01-12", "2026-01-18", "2026-01-24", "2026-01-30", "2026-02-01", "2026-02-06", "2026-02-12", "2026-02-18", "2026-02-24", "2026-03-01", "2026-03-06", "2026-03-12", "2026-03-18", "2026-03-24", "2026-03-30", "2026-04-01", "2026-04-06", "2026-04-12", "2026-04-18", "2026-04-24", "2026-04-30..2026-05-01", "2026-05-06", "2026-05-12", "2026-05-18", "2026-05-24", "2026-05-30", "2026-06-01", "2026-06-06", "2026-06-12", "2026-06-18", "2026-06-24", "2026-06-30..2026-07-01", "2026-07-06", "2026-07-12", "2026-07-18", "2026-07-24", "2026-07-30", "2026-08-01", "2026-08-06", "2026-08-12", "2026-08-18", "2026-08-24", "2026-08-30", "2026-09-01", "2026-09-06", "2026-09-12", "2026-09-18", "2026-09-24", "2026-09-30..2026-10-01", "2026-10-06", "2026-10-12", "2026-10-18", "2026-10-24", "2026-10-30", "2026-11-01", "2026-11-06", "2026-11-12", "2026-11-18", "2026-11-24", "2026-11-30..2026-12-01", "2026-12-06", "2026-12-12", "2026-12-18", "2026-12-24", "2026-12-30", "2027-01-01", "2027-01-06", "2027-01-12", "2027-01-18", "2027-01-24", "2027-01-30", "2027-02-01", "2027-02-06", "2027-02-12", "2027-02-18", "2027-02-24", "2027-03-01", "2027-03-06", "2027-03-12", "2027-03-18", "2027-03-24", "2027-03-30", "2027-04-01", "2027-04-06", "2027-04-12", "2027-04-18", "2027-04-24", "2027-04-30..2027-05-01", "2027-05-06", "2027-05-12", "2027-05-18", "2027-05-24", "2027-05-30", "2027-06-01", "2027-06-06", "2027-06-12", "2027-06-18", "2027-06-24", "2027-06-30..2027-07-01", "2027-07-06", "2027-07-12", "2027-07-18", "2027-07-24", "2027-07-30", "2027-08-01", "2027-08-06", "2027-08-12", "2027-08-18", "2027-08-24", "2027-08-30", "2027-09-01", "2027-09-06", "2027-09-12", "2027-09-18", "2027-09-24", "2027-09-30..2027-10-01", "2027-10-06", "2027-10-12", "2027-10-18", "2027-10-24", "2027-10-30", "2027-11-01", "2027-11-06", "2027-11-12", "2027-11-18", "2027-11-24", "2027-11-30..2027-12-01", "2027-12-06", "2027-12-12", "2027-12-18", "2027-12-24", "2027-12-30"),
    )

    private fun beCautious() = ProofStamp(
        entryId = "be.cautious",
        places = 3,
        placeDays = 1252,
        ramadanDays = 87,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 65, "fajr" to 4, "isha" to 53, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "Fajr is the later member's; against Diyanet's Antwerpen and Gent tables (their own units since the monitor round) up to 4 min on the days its spring takdir moves Fajr in steps, and 2 on plain days where EMB's 18° dawn is the later.", events = listOf("fajr")),
            ProofLateLimit(minutes = 53, reason = "Isha is the later member's, EMB's (18°, in summer its clock cap and proportion from 45°): up to 53 min after Diyanet's Antwerpen and Gent tables in July, whose takdir Isha is earlier.", events = listOf("isha")),
            ProofLateLimit(minutes = 65, reason = "The end of eating is the earlier member's, EMB's own dawns (ruling R39): up to 65 min before Diyanet's Antwerpen and Gent imsak in late April and May, when Diyanet's takdir has moved its dawn later.", events = listOf("endOfEating")),
        ),
        checked = listOf("2026-09-25..2026-11-18", "2027-01-01..2027-12-31"),
    )

    private fun beEmb() = ProofStamp(
        entryId = "be.emb",
        places = 1,
        placeDays = 360,
        ramadanDays = 30,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 7, "isha" to 9, "maghrib" to 1, "sunrise" to 2),
        worstLateByUnit = beEmbUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 7, reason = "From mid-May to 1 August EMB holds Fajr at one clock time, and its fast begins then; the curves carry a clock time as the sun's depression, which the day-either-side envelope (ruling R28) moves by a day's change: Fajr up to 7 min after EMB's, the end of eating up to 7 min before it, in May.", events = listOf("endOfEating", "fajr")),
            ProofLateLimit(minutes = 9, reason = "From May to August EMB's Isha stops at a clock time (a few minutes later from July) or follows its own proportion from 45° (about 0.26 of the night in June); the curve takes the later of the clock time and the 45° proportion of 17.5°, never before EMB's, and runs up to 9 min after it in May.", events = listOf("isha")),
        ),
        checked = listOf("2026-01-01..2026-02-01", "2026-02-03..2026-05-07", "2026-05-09..2026-06-04", "2026-06-06..2026-06-23", "2026-06-25..2026-07-05", "2026-07-07..2026-12-31"),
    )

    private fun beEmbUnits(): Map<String, Map<String, Int>> = mapOf(
        "be.emb" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 7, "isha" to 9, "maghrib" to 1, "sunrise" to 2),
    )

    private fun bhCouncil() = ProofStamp(
        entryId = "bh.council",
        places = 9,
        placeDays = 3195,
        ramadanDays = 261,
        lastChecked = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-06-16..2027-06-05"),
    )

    private fun bnMora() = ProofStamp(
        entryId = "bn.mora",
        places = 1,
        placeDays = 355,
        ramadanDays = 29,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = bnMoraUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2026-01-21", "2026-01-23..2026-03-16", "2026-03-18..2026-03-23", "2026-03-25..2026-04-07", "2026-04-09..2026-04-22", "2026-04-24..2026-06-17", "2026-06-19..2026-07-15", "2026-07-17..2026-08-04", "2026-08-06..2026-08-10", "2026-08-12..2026-12-13", "2026-12-15..2026-12-31"),
    )

    private fun bnMoraUnits(): Map<String, Map<String, Int>> = mapOf(
        "brunei-muara" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun caCautious() = ProofStamp(
        entryId = "ca.cautious",
        places = 55,
        placeDays = 7607,
        ramadanDays = 538,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 5, "dhuhr" to 6, "endOfEating" to 39, "fajr" to 36, "isha" to 77, "maghrib" to 6, "sunrise" to 5),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 5, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Each keeps its own tables' minutes (Asr up to 3 min after the exact one): Asr up to 5 min after the others'.", events = listOf("asrStandard")),
            ProofLateLimit(minutes = 5, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Sunrise, the earliest family's, up to 4 min before the others' at their mosques; within a minute's reach of a family's table its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 5.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 6, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Each keeps its own tables' minutes (Al Farooq's Zuhr + 5, Al Omari's Maghrib + 6 from August): Zuhr and Maghrib up to 5 min after the others' at their mosques; within a minute's reach of a family's table its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 6.", events = listOf("dhuhr", "maghrib")),
            ProofLateLimit(minutes = 36, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Fajr, the 15° families', never before theirs, runs up to 36 min after the 18° family's.", events = listOf("fajr")),
            ProofLateLimit(minutes = 39, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. The end of eating, the 18° family's dawn, never after it, runs up to 38 min before the 15° families' Fajr at their mosques; within a minute's reach of a family's table its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 39.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 77, reason = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90); the app is never before any of them. Isha is the latest family's: Imam Malik's 16.5° (up to 28 % of the night) in spring and autumn, the 18° family's in winter, up to 77 min after Calgary Islamic Centre SW's Maghrib + 90 and the 18° family's spring Isha.", events = listOf("isha")),
        ),
        checked = listOf("2026-01-01..2026-12-31"),
    )

    private fun caIft() = ProofStamp(
        entryId = "ca.ift",
        places = 1,
        placeDays = 350,
        ramadanDays = 25,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = caIftUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2026-03-07", "2026-03-13..2026-03-27", "2026-03-29..2026-10-31", "2026-11-07..2026-11-27", "2026-12-01..2026-12-31"),
    )

    private fun caIftUnits(): Map<String, Map<String, Int>> = mapOf(
        "ca.ift" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
    )

    private fun caIit() = ProofStamp(
        entryId = "ca.iit",
        places = 1,
        placeDays = 359,
        ramadanDays = 25,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 4, "maghrib" to 4, "sunrise" to 4),
        worstLateByUnit = caIitUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr")),
            ProofLateLimit(minutes = 4, reason = "IIT's own table drifts over its year: its sunrise from 2 min before to 2 min after the sun's, its Maghrib from sunset to sunset + 2.5 (and Isha, 90 min after it, with it); the margins hold it never early all year, so in spring they run up to 4 min.", events = listOf("isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2026-03-07", "2026-03-13..2026-03-27", "2026-03-29..2026-12-31"),
    )

    private fun caIitUnits(): Map<String, Map<String, Int>> = mapOf(
        "ca.iit" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 4, "maghrib" to 4, "sunrise" to 4),
    )

    private fun caIsna() = ProofStamp(
        entryId = "ca.isna",
        places = 204,
        placeDays = 72922,
        ramadanDays = 5820,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 5, "asrStandard" to 6, "dhuhr" to 3, "endOfEating" to 6, "fajr" to 7, "isha" to 5, "maghrib" to 5, "sunrise" to 5),
        worstLateByUnit = caIsnaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("dhuhr")),
            ProofLateLimit(minutes = 4, reason = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any: Isha up to 4 min after the others'.", events = listOf("isha")),
            ProofLateLimit(minutes = 5, reason = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any: Fajr, Maghrib and sunrise up to 5 min from the others'.", events = listOf("fajr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "Within a minute's reach of this mosque its point rides beside the user's (rulings R30 and R44): east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk. Isha up to 5 min after its table there.", events = listOf("isha")),
            ProofLateLimit(minutes = 6, reason = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any: Asr up to 4 min after the others' at their mosques, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44), up to 5, and beside every other mosque's table within class D's reach of the user (the city-point round of 11 Oct 2026), up to 6.", events = listOf("asrHanafi", "asrStandard")),
            ProofLateLimit(minutes = 6, reason = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it (Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any: The end of eating up to 5 min from the others', and beside every other mosque's table within class D's reach of the user (the city-point round of 11 Oct 2026), up to 6.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "Within a minute's reach of this mosque its point rides beside the user's (rulings R30 and R44): east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk. Fajr up to 6 min after its table there.", events = listOf("fajr")),
            ProofLateLimit(minutes = 7, reason = "Within a minute's reach of this mosque its point rides beside the user's (rulings R30 and R44): east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk. Fajr up to 7 min after its table there.", events = listOf("fajr")),
        ),
        checked = listOf("2026-01-01..2026-12-31"),
        checkedByUnit = caIsnaChecked(),
    )

    private fun caIsnaChecked(): Map<String, List<String>> = mapOf(
        "abrar" to listOf("2026-01-01..2026-10-31", "2026-11-02..2026-12-31"),
        "al-rawdah" to listOf("2026-01-01..2026-01-12", "2026-01-14..2026-01-23", "2026-01-25..2026-10-31", "2026-11-02..2026-12-31"),
        "jami-omar" to listOf("2026-06-01..2026-10-31", "2026-11-02..2026-12-31"),
    )

    private fun caIsnaUnits(): Map<String, Map<String, Int>> = mapOf(
        "abrar" to mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 5, "isha" to 5, "maghrib" to 5, "sunrise" to 5),
        "aim" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "aisha" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "al-andalous" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 4, "sunrise" to 5),
        "al-madinah" to mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 4, "sunrise" to 4),
        "al-rawdah" to mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 5, "sunrise" to 5),
        "as-salam" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 4, "sunrise" to 4),
        "badr" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 5, "sunrise" to 5),
        "canadian-islamic-center" to mapOf("asrStandard" to 5, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "ccml" to mapOf("asrStandard" to 6, "dhuhr" to 3, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 5, "sunrise" to 5),
        "dar-al-arkam" to mapOf("asrStandard" to 6, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 3, "maghrib" to 4, "sunrise" to 4),
        "jami-omar" to mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 3, "maghrib" to 4, "sunrise" to 5),
        "kanata" to mapOf("asrStandard" to 5, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "laval" to mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "oma" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 4, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "ottawa-south" to mapOf("asrStandard" to 5, "dhuhr" to 2, "endOfEating" to 5, "fajr" to 4, "isha" to 4, "maghrib" to 4, "sunrise" to 5),
        "taiba" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 7, "isha" to 3, "maghrib" to 4, "sunrise" to 4),
        "west-island" to mapOf("asrHanafi" to 5, "dhuhr" to 3, "endOfEating" to 4, "fajr" to 6, "isha" to 3, "maghrib" to 4, "sunrise" to 5),
    )

    private fun caMac() = ProofStamp(
        entryId = "ca.mac",
        places = 1,
        placeDays = 359,
        ramadanDays = 25,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = caMacUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2026-03-07", "2026-03-13..2026-03-27", "2026-03-29..2026-12-31"),
    )

    private fun caMacUnits(): Map<String, Map<String, Int>> = mapOf(
        "ca.mac" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun caToronto() = ProofStamp(
        entryId = "ca.toronto",
        places = 6,
        placeDays = 1400,
        ramadanDays = 50,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 6, "asrStandard" to 6, "dhuhr" to 5, "endOfEating" to 4, "fajr" to 6, "isha" to 7, "maghrib" to 5, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = "Toronto's three tables round and offset their times irregularly against their own rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the latest printed time at the mosques. Sunrise is the earliest of the three, each a little before its own table's.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "None of the three tables prints an imsak, so each Fajr is where its fast begins; the end is the earliest member's own dawn less its margin, up to 4 min before the tables' Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "Toronto's three tables round and offset their times irregularly against their own rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the latest printed time at the mosques. West of the mosques the user's own sun is later (at Milton by 2.5 min), and Dhuhr, Asr and Maghrib shown follow it: up to 5, 6 and 5 min.", events = listOf("asrHanafi", "asrStandard", "dhuhr", "maghrib")),
            ProofLateLimit(minutes = 7, reason = "Toronto's three tables round and offset their times irregularly against their own rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the latest printed time at the mosques. West of the mosques the user's own sun is later than the tables' points (at Milton, the reach's western end, by 2.5 min), and Fajr and Isha shown follow it: up to 6 and 7 min.", events = listOf("fajr", "isha")),
        ),
        checked = listOf("2026-01-01..2026-03-07", "2026-03-13..2026-03-27", "2026-03-29..2026-10-31", "2026-11-07..2026-11-27", "2026-12-01..2026-12-31"),
    )

    private fun chFids() = ProofStamp(
        entryId = "ch.fids",
        places = 1,
        placeDays = 420,
        ramadanDays = 29,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = chFidsUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-25..2026-11-18", "2027-01-01..2027-12-31"),
    )

    private fun chFidsUnits(): Map<String, Map<String, Int>> = mapOf(
        "ch.fids" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
    )

    private fun deCautious() = ProofStamp(
        entryId = "de.cautious",
        places = 1,
        placeDays = 61,
        ramadanDays = 0,
        lastChecked = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 43, "isha" to 11, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib")),
            ProofLateLimit(minutes = 2, reason = "Diyanet's European method's own margins (subtask 7b): sunrise up to 2 min before Diyanet's Berlin table.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 5, reason = "The end of eating is the earliest member's, the 18° family's 18.4° dawn (Hamburg's 18.35°, ruling R87), up to 5 min before Diyanet's Berlin imsak.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "Isha is the later member's, VIKZ's: Diyanet's Isha + 10 min by VIKZ's own rule (no VIKZ table is held), up to 11 min after Diyanet's Berlin table.", events = listOf("isha")),
            ProofLateLimit(minutes = 44, reason = "Fajr is the latest member's: the late-dawn family's 13° from September to April (ruling R87), up to 44 min after Diyanet's Berlin table in early September, as its takdir ends, and about 30 in October; in summer VIKZ's, Diyanet's generic European method (VIKZ has no city curves of its own), no earlier than 19 % of the night, up to 12 min after the table.", events = listOf("fajr")),
        ),
        checked = listOf("2026-09-01..2026-10-31"),
    )

    private fun defaultSafe() = ProofStamp(
        entryId = "default.safe",
        places = 15,
        placeDays = 2054,
        ramadanDays = 149,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 4, "asrStandard" to 4, "dhuhr" to 6, "endOfEating" to 5, "fajr" to 12, "isha" to 8, "maghrib" to 6, "sunrise" to 7),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 5, reason = "this region's fast begins 2 min before the 18° dawn, and JAKIM prints Kuala Lumpur's Subuh for its zone's latest point, rounded up: up to 5 min before it (end-of-eating audit, 3 Oct 2026, safe-default.tsv)", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 15, reason = "this region's 18° Isha is deeper (later) than some real authorities' own shallower Isha angle (Dar al-Ifta Cairo's 17.5°, Task 7h, safe-default.tsv) — never early, only later by more than the class default", events = listOf("isha")),
            ProofLateLimit(minutes = 20, reason = "no local convention is known here; nearby authorities elsewhere run deeper safety minutes than this region's flat 1986 baseline (Task 7h, safe-default.tsv)", events = listOf("asrHanafi", "asrStandard", "dhuhr", "fajr", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun dzMarw() = ProofStamp(
        entryId = "dz.marw",
        places = 5,
        placeDays = 1783,
        ramadanDays = 145,
        lastChecked = "2027-06-05",
        worstLateMinutes = mapOf("asrStandard" to 6, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = dzMarwUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "The ministry prints Tamanrasset as Adrar's table plus one difference each half month, which does not follow Tamanrasset's own Asr through the half month; so that the Asr computed at its seat is never before that printed time, it is up to 6 min after it on some days.", events = listOf("asrStandard")),
        ),
        checked = listOf("2025-06-27", "2025-07-01", "2025-07-04", "2025-07-10", "2025-11-22", "2025-11-26", "2025-12-01", "2025-12-05", "2026-06-16..2027-06-05"),
        checkedByUnit = dzMarwChecked(),
    )

    private fun dzMarwChecked(): Map<String, List<String>> = mapOf(
        "1" to listOf("2026-06-16..2027-06-05"),
        "11" to listOf("2026-06-16..2027-06-05"),
        "17" to listOf("2026-06-16..2027-06-05"),
        "31" to listOf("2026-06-16..2027-06-05"),
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
        lastChecked = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = egEsaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "ESA computes Taba's table about half a degree west of Taba, and Taqwa keeps Taba's own earlier sunrise and dawn", events = listOf("endOfEating", "sunrise")),
        ),
        checked = listOf("2025-01-08", "2025-02-12", "2025-04-01..2025-07-31", "2025-08-18", "2025-09-01..2025-10-31", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30..2026-03-31", "2026-06-01..2026-06-30", "2026-08-01..2026-10-31"),
        checkedByUnit = egEsaChecked(),
    )

    private fun egEsaChecked(): Map<String, List<String>> = mapOf(
        "alexandria" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "aswan" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "bawiti" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "dahab" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "damietta" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "edfu" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "el-arish" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "el-dabaa" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "el-tor" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "farafra" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "halaib" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "hurghada" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "kharga" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "luxor" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "marsa-matruh" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "mut" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "nuweiba" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "port-said" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "quseir" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "rafah" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "ras-gharib" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "saint-catherine" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "sallum" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "shalatin" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "sharm-el-sheikh" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "sidi-barrani" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "siwa" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "suez" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
        "taba" to listOf("2025-01-08", "2025-02-12", "2025-04-29", "2025-07-05", "2025-08-18", "2025-09-12", "2025-09-29", "2025-11-12", "2025-11-14", "2025-11-17", "2025-11-30", "2025-12-23", "2026-02-11", "2026-03-20", "2026-06-12", "2026-09-01..2026-10-31"),
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
        placeDays = 1197,
        ramadanDays = 88,
        lastChecked = "2027-12-31",
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
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun frGmp() = ProofStamp(
        entryId = "fr.gmp",
        places = 1,
        placeDays = 374,
        ramadanDays = 30,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 16, "fajr" to 2, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = frGmpUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "fajr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "From October the Grande Mosquée's page still carries its earlier method, whose Isha moves from about 15.3° to 16° over October; Isha is taken at the later of its 2026 method and 16°, never before either, and so runs up to 5 min after the earlier method's in early October.", events = listOf("isha")),
            ProofLateLimit(minutes = 16, reason = "The GMP prints no imsak, so the fast begins at its own Fajr (ruling R80), within 4 min of it (the end holds for everyone within its table's reach, where its rule's dawn to the south comes up to 2 min earlier in June) but where its page changes method (the Moonsighting Committee's from January to September, its earlier method from October): each change is carried over the two days either side, so the end comes up to 16 min before its printed Fajr on 1 and 2 January and 8 before it on 29 and 30 September.", events = listOf("endOfEating")),
        ),
        checked = listOf("2025-10-01..2025-10-02", "2025-10-05", "2025-10-10", "2025-10-15", "2025-10-20", "2025-10-25..2025-10-26", "2025-10-31", "2026-01-01..2026-12-31"),
    )

    private fun frGmpUnits(): Map<String, Map<String, Int>> = mapOf(
        "fr.gmp" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 16, "fajr" to 2, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
    )

    private fun gbLondonLupt() = ProofStamp(
        entryId = "gb.london.lupt",
        places = 11,
        placeDays = 4015,
        ramadanDays = 330,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 3, "asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 5, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = gbLondonLuptUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("dhuhr")),
            ProofLateLimit(minutes = 3, reason = "The M25 follows one table computed at its point; at the M25's western edge the user's own sun is 1.6 min later, and Asr and Maghrib shown follow it.", events = listOf("asrHanafi", "asrStandard", "maghrib")),
            ProofLateLimit(minutes = 3, reason = "The M25 follows one table computed at its point; at the M25's eastern edge the user's own sunrise is 1.7 min earlier, and the sunrise shown follows it.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "The M25 follows one table computed at its point; at its eastern edge the user's own dawn is 1.7 min earlier, and at its northern edge up to 3 min earlier in summer, and the end of eating shown follows it.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 5, reason = "The M25 follows one table computed at its point, and Fajr and Isha shown are the later of the point's and the user's own: at the M25's southern and western edges the user's Fajr, and at its northern and north-western edges the user's Isha, at the table's depression run up to 3 min later than the point's in summer.", events = listOf("fajr", "isha")),
        ),
        checked = listOf("2026-01-01..2026-12-31"),
    )

    private fun gbLondonLuptUnits(): Map<String, Map<String, Int>> = mapOf(
        "gb.london.lupt" to mapOf("asrHanafi" to 3, "asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 5, "isha" to 5, "maghrib" to 3, "sunrise" to 3),
    )

    private fun gbWifaqul() = ProofStamp(
        entryId = "gb.wifaqul",
        places = 38,
        placeDays = 266,
        ramadanDays = 0,
        lastChecked = "2026-09-30",
        worstLateMinutes = mapOf("asrHanafi" to 1, "dhuhr" to 5, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 6, "sunrise" to 1),
        worstLateByUnit = gbWifaqulUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "endOfEating", "fajr", "isha", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "Wifaqul Ulama documents Zuhr at the meridian + 4 and Maghrib at sunset + 5; Manchester Central Mosque, the only table of theirs held, prints the meridian and sunset themselves, so against it Dhuhr runs up to 5 min and Maghrib up to 6 min late.", events = listOf("dhuhr", "maghrib")),
        ),
        checked = listOf("2026-09-01", "2026-09-05", "2026-09-10", "2026-09-15", "2026-09-20", "2026-09-25", "2026-09-30"),
    )

    private fun gbWifaqulUnits(): Map<String, Map<String, Int>> = mapOf(
        "manchester-central" to mapOf("asrHanafi" to 1, "dhuhr" to 5, "endOfEating" to 1, "fajr" to 2, "isha" to 1, "maghrib" to 6, "sunrise" to 1),
    )

    private fun idKemenag() = ProofStamp(
        entryId = "id.kemenag",
        places = 18,
        placeDays = 19728,
        ramadanDays = 1584,
        lastChecked = "2028-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = idKemenagUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2028-12-31"),
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
        "1433" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
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
        places = 2,
        placeDays = 730,
        ramadanDays = 60,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 5, "maghrib" to 3, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("dhuhr")),
            ProofLateLimit(minutes = 2, reason = "Each member keeps its own margins (IFI's times at its table's minute, the ICCI's rounding): starts up to 2 min after the later table's, sunrise up to 2 before the earlier's.", events = listOf("asrStandard", "fajr", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "The end of eating is the earlier member's: the ICCI's own rule read as an end (ruling R80), but for most of June, when IFI's own dawn is the earlier; the ICCI's, taken by month and day over the leap cycle, runs up to 3 min before the earlier of the two tables' Fajr in July and August.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 3, reason = "Each member keeps its own margins, and Maghrib is capped at IFI's where the two spread: up to 2 min after the later table's at the tables' point, and at the app's own Dublin, 2 km south-east of it, where the tables' point rides beside the user's (rulings R30, R44), up to 3.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 5, reason = "Isha is the later member's; the ICCI's rule on the curve's slots (ruling R28) runs up to 5 min after its table in May.", events = listOf("isha")),
        ),
        checked = listOf("2026-01-01..2026-12-31"),
    )

    private fun ieIcci() = ProofStamp(
        entryId = "ie.icci",
        places = 1,
        placeDays = 365,
        ramadanDays = 30,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = ieIcciUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "The ICCI's code keeps Fajr no earlier than 18/60 of the night before sunrise and Isha no later than 17/60 after sunset (adhan-js's TwilightAngle rule); on the curve's slots each is widened over the day before and after (ruling R28), most when the rule takes over, in late April and May: Isha up to 5 min after its table.", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "The ICCI's code keeps Fajr no earlier than 18/60 of the night before sunrise and Isha no later than 17/60 after sunset (adhan-js's TwilightAngle rule); on the curve's slots each is widened over the day before and after (ruling R28), most when the rule takes over, in late April and May: Fajr up to 7 min after its table.", events = listOf("fajr")),
        ),
        checked = listOf("2026-01-01..2026-12-31"),
    )

    private fun ieIcciUnits(): Map<String, Map<String, Int>> = mapOf(
        "ie.icci" to mapOf("asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 7, "isha" to 5, "maghrib" to 1, "sunrise" to 1),
    )

    private fun ieIfi() = ProofStamp(
        entryId = "ie.ifi",
        places = 1,
        placeDays = 1460,
        ramadanDays = 117,
        lastChecked = "2029-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = ieIfiUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-01-01..2028-02-28", "2028-03-01..2029-12-31"),
    )

    private fun ieIfiUnits(): Map<String, Map<String, Int>> = mapOf(
        "ie.ifi" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
    )

    private fun inKarachi() = ProofStamp(
        entryId = "in.karachi",
        places = 80,
        placeDays = 2400,
        ramadanDays = 0,
        lastChecked = "2026-09-30",
        worstLateMinutes = mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        worstLateByUnit = inKarachiUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "Dawat-e-Islami prints no sehri time: the end of eating keeps the sunrise margin, and within a minute's reach of Delhi's point the earlier of its dawn and the user's own is shown (rulings R30 and R44): up to 4 min before its Fajr; an early end is the safe side", events = listOf("endOfEating")),
        ),
        checked = listOf("2026-09-01..2026-09-30"),
    )

    private fun inKarachiUnits(): Map<String, Map<String, Int>> = mapOf(
        "delhi" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
    )

    private fun iqSunni() = ProofStamp(
        entryId = "iq.sunni",
        places = 4,
        placeDays = 4,
        ramadanDays = 0,
        lastChecked = "2025-12-04",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 0, "isha" to 1, "maghrib" to 1, "sunrise" to 0),
        worstLateByUnit = iqSunniUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2025-12-04"),
    )

    private fun iqSunniUnits(): Map<String, Map<String, Int>> = mapOf(
        "baghdad" to mapOf("asrStandard" to 1, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 0, "isha" to 1, "maghrib" to 1, "sunrise" to 0),
    )

    private fun joAwqaf() = ProofStamp(
        entryId = "jo.awqaf",
        places = 1,
        placeDays = 171,
        ramadanDays = 2,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = joAwqafUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2021-01-08", "2021-01-14", "2021-01-31", "2021-02-06", "2021-04-16", "2021-06-03", "2021-06-26", "2021-07-19", "2021-08-11", "2021-09-09", "2022-07-09", "2022-08-01", "2022-08-24", "2022-09-16", "2022-10-09", "2022-11-09", "2022-11-26", "2022-12-02", "2022-12-25", "2022-12-31", "2023-01-17", "2023-02-09", "2023-03-04", "2023-04-21", "2023-05-14", "2023-05-20", "2023-06-06", "2023-06-12", "2023-07-05", "2023-07-28", "2024-04-24..2024-05-03", "2024-12-08..2024-12-17", "2025-02-09..2025-02-18", "2025-07-12..2025-07-21", "2025-12-18..2025-12-27", "2026-10-02..2026-12-31"),
    )

    private fun joAwqafUnits(): Map<String, Map<String, Int>> = mapOf(
        "jo.awqaf.amman" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun kgDefault() = ProofStamp(
        entryId = "kg.default",
        places = 4,
        placeDays = 4,
        ramadanDays = 0,
        lastChecked = "2026-09-26",
        worstLateMinutes = mapOf("asrHanafi" to 0, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 0, "isha" to 0, "maghrib" to 0, "sunrise" to 0),
        worstLateByUnit = kgDefaultUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-26"),
    )

    private fun kgDefaultUnits(): Map<String, Map<String, Int>> = mapOf(
        "bishkek" to mapOf("asrHanafi" to 0, "dhuhr" to 0, "endOfEating" to 1, "fajr" to 0, "isha" to 0, "maghrib" to 0, "sunrise" to 0),
    )

    private fun kwAwqaf() = ProofStamp(
        entryId = "kw.awqaf",
        places = 1,
        placeDays = 25,
        ramadanDays = 4,
        lastChecked = "2026-09-25",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2019-02-27", "2019-11-11", "2019-11-23", "2019-12-11", "2020-05-16", "2021-03-02", "2021-04-18", "2021-11-01..2021-11-02", "2022-01-28", "2022-07-06", "2022-09-28", "2023-01-17..2023-01-18", "2023-08-13", "2025-12-29", "2026-02-03", "2026-02-07", "2026-03-04", "2026-03-13", "2026-03-23", "2026-04-09", "2026-06-01", "2026-07-23", "2026-09-25"),
    )

    private fun kzQmdb() = ProofStamp(
        entryId = "kz.qmdb",
        places = 1832,
        placeDays = 1337360,
        ramadanDays = 108088,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 10, "isha" to 8, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = kzQmdbUnits1() + kzQmdbUnits2() + kzQmdbUnits3(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 6, reason = "the same AngleBased residual in Isha from July to mid-August, up to 5 min from Astana to Pavlodar and 6 from 54.3N to QMDB's northernmost place, Isakovka (task 7f, fix rounds of 3 and 6 Oct 2026)", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 7, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 7 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "the end of eating keeps clear of QMDB's AngleBased residual, so on its other days it comes up to 8 min before the printed Fajr at and above 48N, as far north as Isakovka, and up to 4 from 46N to 48N (end-of-eating audit, 3 Oct 2026; north-west round, 6 Oct 2026)", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 8, reason = "QMDB's AngleBased Fajr runs ahead of the model from about 46N, around the solstice there and from late April to early June in the north, more the farther north: up to 4 min from 46N to 48N, 5 to 7 from 48N to 54.4N, 8 from 54.5N to Isakovka, QMDB's northernmost place (task 7f, fix rounds of 3 and 6 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Isha residual at the place's own table and the later of the place's dusk and the user's own add up, so Isha comes up to 8 min after the place's table", events = listOf("isha")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 9 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 9 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 9 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 9 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 9, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 9 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 10 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 10 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 10, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), QMDB's AngleBased Fajr residual at the place's own table and the later of the place's dawn and the user's own add up, so Fajr comes up to 10 min after the place's table", events = listOf("fajr")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 11, reason = "across this place's reach, toward its edge (its own tables replayed there, 9 Oct 2026), the end of eating kept clear of QMDB's AngleBased Fajr and the earlier of the place's dawn and the user's own add up, so it comes up to 11 min before the place's printed Fajr", events = listOf("endOfEating")),
        ),
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun kzQmdbUnits1(): Map<String, Map<String, Int>> = mapOf(
        "aiteke-bi" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        "akkol" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "aksay" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "aksu-pavlodar" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "aksu-turkistan" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 2),
        "aktau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 2),
        "aktobe" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "almaty" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 4, "isha" to 3, "maghrib" to 3, "sunrise" to 2),
        "altay" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "aral" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 5, "isha" to 5, "maghrib" to 3, "sunrise" to 3),
        "arkalyk" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "arys" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "astana" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "atbasar" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 3),
        "atyrau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 7, "fajr" to 5, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        "ayagoz" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "baikonur" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        "balkhash" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 7, "fajr" to 5, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        "boralday" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 1),
        "bugrovoe" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "ekibastuz" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "embi" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 7, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "isakovka" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "kalbatau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "kandyagash" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 3),
        "karaganda" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 10, "fajr" to 7, "isha" to 6, "maghrib" to 2, "sunrise" to 2),
        "karatau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "kentau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "khromtau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "kokshetau" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "konaev" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "kostanay" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "krasny-yar" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "kulomzino" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "kulsary" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 6, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        "kyzylorda" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "lenger" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "lisakovsk" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "makanshy" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 5, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        "makat" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 7, "fajr" to 5, "isha" to 6, "maghrib" to 2, "sunrise" to 2),
    )

    private fun kzQmdbUnits2(): Map<String, Map<String, Int>> = mapOf(
        "makinsk" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "mamyrsu" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "mangystau" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "merke" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "oral" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "oskemen" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "otegen-batyr" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "oteshqali" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        "pavlodar" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "petropavl" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 10, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "pulemetovka" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 9, "fajr" to 10, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "ridder" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "rudny" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 9, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "saran" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 9, "fajr" to 7, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "sarkand" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "saryagash" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "sarykemer" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "satpayev" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 5, "fajr" to 5, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
        "semey" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "shakhtinsk" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 7, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "shalqar" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 5, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        "shardara" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "shelek" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "shemonaikha" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "shiyeli" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "shu" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "shymkent" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 2),
        "spasovka" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 9, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 1),
        "stepnogorsk" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 11, "fajr" to 8, "isha" to 8, "maghrib" to 2, "sunrise" to 2),
        "taldykorgan" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 2, "sunrise" to 3),
        "talgar" to mapOf("asrHanafi" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 3, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        "taraz" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "tekeli" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "temirtau" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 10, "fajr" to 7, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "turar-ryskulov" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 3, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "turkistan" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "ushtobe" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "vagulino" to mapOf("asrHanafi" to 1, "dhuhr" to 1, "endOfEating" to 9, "fajr" to 8, "isha" to 7, "maghrib" to 2, "sunrise" to 2),
        "zaysan" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 6, "isha" to 6, "maghrib" to 2, "sunrise" to 2),
        "zhanakorgan" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
    )

    private fun kzQmdbUnits3(): Map<String, Map<String, Int>> = mapOf(
        "zhanaozen" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "zhanatas" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "zharkent" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "zhetysay" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
        "zhezkazgan" to mapOf("asrHanafi" to 2, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 6, "isha" to 5, "maghrib" to 2, "sunrise" to 2),
        "zhosaly" to mapOf("asrHanafi" to 3, "dhuhr" to 2, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 3, "sunrise" to 3),
    )

    private fun lbFatwa() = ProofStamp(
        entryId = "lb.fatwa",
        places = 5,
        placeDays = 2997,
        ramadanDays = 243,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 3, "endOfEating" to 9, "fajr" to 9, "isha" to 7, "maghrib" to 8, "sunrise" to 4),
        worstLateByUnit = lbFatwaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr")),
            ProofLateLimit(minutes = 4, reason = "the perpetual table's single never-early margin (as the entry's own), and within a minute's reach of Beirut's point, where it rides beside the user's (rulings R30, R44), each start the later of its sun and the user's own and each end the earlier: at the app's own towns near it (Jounieh, Aley) up to this many minutes from the table", events = listOf("sunrise")),
            ProofLateLimit(minutes = 7, reason = "the same margin runs to 7 min late on Maghrib and Isha on their worst held-out day", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "the perpetual table's single never-early margin (as the entry's own), and within a minute's reach of Beirut's point, where it rides beside the user's (rulings R30, R44), each start the later of its sun and the user's own and each end the earlier: at the app's own towns near it (Jounieh, Aley) up to this many minutes from the table", events = listOf("maghrib")),
            ProofLateLimit(minutes = 9, reason = "the perpetual table's single never-early margin (as the entry's own), and within a minute's reach of Beirut's point, where it rides beside the user's (rulings R30, R44), each start the later of its sun and the user's own and each end the earlier: at the app's own towns near it (Jounieh, Aley) up to this many minutes from the table", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 9, reason = "the perpetual table's single never-early margin, over 384 measured days across two sources and seven years, runs to 9 min late on Fajr on its worst day", events = listOf("fajr")),
        ),
        checked = listOf("2019-07-20", "2022-06-14", "2023-02-02", "2023-03-08", "2023-04-01", "2023-05-09", "2023-07-02", "2023-09-12", "2023-09-21", "2024-01-01..2024-02-28", "2024-03-01..2027-12-31"),
    )

    private fun lbFatwaUnits(): Map<String, Map<String, Int>> = mapOf(
        "beirut" to mapOf("asrStandard" to 3, "dhuhr" to 3, "endOfEating" to 9, "fajr" to 9, "isha" to 7, "maghrib" to 8, "sunrise" to 4),
    )

    private fun lyAwqaf() = ProofStamp(
        entryId = "ly.awqaf",
        places = 22,
        placeDays = 175,
        ramadanDays = 3,
        lastChecked = "2026-10-26",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 3, "endOfEating" to 1, "fajr" to 2, "imsak" to 2, "isha" to 3, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = lyAwqafUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "imsak", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2025-03-23", "2025-04-13", "2025-04-19", "2025-06-22", "2025-07-18", "2025-08-03", "2025-09-16", "2025-10-06", "2025-11-17", "2025-12-07", "2026-01-24", "2026-02-18", "2026-03-12", "2026-04-18", "2026-06-16", "2026-09-25..2026-10-26"),
        checkedByUnit = lyAwqafChecked(),
    )

    private fun lyAwqafChecked(): Map<String, List<String>> = mapOf(
        "ajdabiya" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "awjila" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "bani-walid" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "bayda" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "benghazi" to listOf("2026-09-25..2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "brega" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "derna" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "emsaed" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "ghadames" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "hun" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "jalu" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "khoms" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "kufra" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "marj" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "misrata" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "ras-lanuf" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "sabha" to listOf("2026-09-25..2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "sirte" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "tobruk" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "zawiya" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
        "zliten" to listOf("2026-09-25..2026-09-26", "2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"),
    )

    private fun lyAwqafUnits(): Map<String, Map<String, Int>> = mapOf(
        "ajdabiya" to mapOf("asrStandard" to 2, "dhuhr" to 2, "isha" to 2, "sunrise" to 2),
        "awjila" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 2, "sunrise" to 2),
        "bani-walid" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 2),
        "bayda" to mapOf("asrStandard" to 3, "dhuhr" to 3, "isha" to 2, "sunrise" to 0),
        "benghazi" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "brega" to mapOf("asrStandard" to 2, "dhuhr" to 2, "isha" to 2, "sunrise" to 2),
        "derna" to mapOf("asrStandard" to 2, "dhuhr" to 2, "isha" to 2, "sunrise" to 2),
        "emsaed" to mapOf("asrStandard" to 2, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "ghadames" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "hun" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 1, "sunrise" to 3),
        "jalu" to mapOf("asrStandard" to 1, "dhuhr" to 1, "isha" to 2, "sunrise" to 2),
        "khoms" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "kufra" to mapOf("asrStandard" to 2, "dhuhr" to 1, "isha" to 1, "sunrise" to 2),
        "marj" to mapOf("asrStandard" to 2, "dhuhr" to 2, "isha" to 2, "sunrise" to 2),
        "misrata" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ras-lanuf" to mapOf("asrStandard" to 2, "dhuhr" to 1, "isha" to 2, "sunrise" to 2),
        "sabha" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 0, "fajr" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 2),
        "sirte" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 2),
        "tobruk" to mapOf("asrStandard" to 1, "dhuhr" to 2, "isha" to 1, "sunrise" to 2),
        "tripoli" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 3, "maghrib" to 2, "sunrise" to 2),
        "zawiya" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 1),
        "zliten" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 2, "imsak" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun maHabous() = ProofStamp(
        entryId = "ma.habous",
        places = 40,
        placeDays = 3549,
        ramadanDays = 126,
        lastChecked = "2026-11-11",
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
        checked = listOf("2015-02-20..2015-03-21", "2017-05-27..2017-06-25", "2019-03-08..2019-05-06", "2019-06-05..2019-10-31", "2020-01-01..2020-01-27", "2020-02-01..2020-06-30", "2020-10-01..2020-10-17", "2021-02-13..2021-03-14", "2021-04-14..2021-05-12", "2021-10-08..2021-10-31", "2022-01-04..2022-02-02", "2022-06-01..2022-06-30", "2022-07-30..2022-08-28", "2022-09-10", "2023-06-01", "2023-07-15", "2023-11-01", "2024-02-11..2024-04-10", "2024-05-10..2024-06-07", "2025-01-02..2025-02-28", "2026-09-13..2026-11-11"),
        checkedByUnit = maHabousChecked(),
    )

    private fun maHabousChecked(): Map<String, List<String>> = mapOf(
        "casablanca" to listOf("2015-02-20..2015-03-21", "2017-05-27..2017-06-25", "2022-01-04..2022-02-02", "2022-06-01..2022-06-30", "2022-07-30..2022-08-28", "2025-01-02..2025-01-31", "2026-09-13..2026-11-11"),
        "dakhla" to listOf("2022-07-30..2022-08-28", "2026-09-13..2026-11-11"),
        "figuig" to listOf("2019-03-08..2019-04-06", "2019-07-04..2019-08-02", "2020-03-26..2020-04-24", "2026-09-13..2026-11-11"),
        "ifrane" to listOf("2026-09-13..2026-11-11"),
        "laayoune" to listOf("2019-08-03..2019-08-31", "2019-09-30..2019-10-29", "2020-10-01..2020-10-17", "2021-02-13..2021-03-14", "2022-07-30..2022-08-28", "2024-05-10..2024-06-07", "2025-01-31..2025-02-28", "2026-09-13..2026-11-11"),
        "lagouira" to listOf("2022-07-30..2022-08-28", "2026-09-13..2026-11-11"),
        "midelt" to listOf("2022-07-30..2022-08-28", "2026-09-13..2026-11-11"),
        "oujda" to listOf("2022-07-30..2022-08-28", "2026-09-13..2026-11-11"),
        "rabat" to listOf("2019-04-07..2019-05-06", "2019-06-05..2019-10-31", "2020-01-01..2020-01-27", "2020-02-01..2020-06-30", "2021-02-13..2021-03-14", "2021-04-14..2021-05-12", "2021-10-08..2021-10-31", "2022-01-04..2022-02-02", "2022-06-26", "2022-07-30..2022-08-28", "2022-09-10", "2023-06-01", "2023-07-15", "2023-11-01", "2024-02-11..2024-04-10", "2026-09-13..2026-11-11"),
        "tangier" to listOf("2022-07-30..2022-08-28", "2026-09-13..2026-11-11"),
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
        lastChecked = "2026-09-30",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-01..2026-09-30"),
    )

    private fun mrMinistry() = ProofStamp(
        entryId = "mr.ministry",
        places = 2,
        placeDays = 38,
        ramadanDays = 38,
        lastChecked = "2024-04-09",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "imsak" to 2, "isha" to 2, "maghrib" to 1, "sunrise" to 3),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "imsak", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2023-03-23", "2023-03-27", "2023-03-31", "2023-04-04", "2023-04-08", "2023-04-12", "2023-04-16", "2023-04-21", "2024-03-11..2024-04-09"),
    )

    private fun myJakim() = ProofStamp(
        entryId = "my.jakim",
        places = 13,
        placeDays = 9489,
        ramadanDays = 766,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        worstLateByUnit = myJakimUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "Kangar's printed sunrise spreads a further minute beyond a plain classic-noon model across the seasons", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "e-solat prints each zone's Subuh for its latest point, rounded up, and the fast here begins at the dawn of the place itself: up to 4 min before the printed Subuh (end-of-eating audit, 3 Oct 2026)", events = listOf("endOfEating")),
        ),
        checked = listOf("2025-01-01..2026-12-31"),
        checkedByUnit = myJakimChecked(),
    )

    private fun myJakimChecked(): Map<String, List<String>> = mapOf(
        "PLS01" to listOf("2025-01-01..2025-02-28", "2025-03-02..2026-12-31"),
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
        lastChecked = "2026-10-25",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 16, "isha" to 6, "maghrib" to 8, "sunrise" to 1),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "sunrise")),
            ProofLateLimit(minutes = 2, reason = "The members' own margins: the end of eating (Diyanet's earliest dawns) up to 2 min before the tables held.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 6, reason = "Isha is the latest member's, MWL's 17°, whose own table is not held here: up to 6 min after the Moroccan calendar's and Diyanet's.", events = listOf("isha")),
            ProofLateLimit(minutes = 8, reason = "Maghrib is capped at the Moroccan member's, which carries the Turkish and Arab calendars' Maghrib as a floor (ruling R87: 7 of 12 surveyed calendars print Diyanet's, sunset + 6 in spring to + 9 in autumn on the exact sun), so against the Moroccan calendar, which prints sunset + 1..4, it runs up to 8 min late.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 16, reason = "The Moroccan calendar's Fajr moves from about 16° in winter to 14° in May and August; its member takes 14°, the calendar's latest, never before it, and so runs up to 16 min after it in autumn (Task 7g checked it at Amsterdam from 25 September to 25 October).", events = listOf("fajr")),
        ),
        checked = listOf("2026-09-25..2026-10-25"),
    )

    private fun noCautious() = ProofStamp(
        entryId = "no.cautious",
        places = 5,
        placeDays = 2648,
        ramadanDays = 205,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 7, "asrStandard" to 6, "dhuhr" to 6, "endOfEating" to 57, "fajr" to 101, "isha" to 135, "maghrib" to 4, "sunrise" to 58),
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
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun noIrn() = ProofStamp(
        entryId = "no.irn",
        places = 3,
        placeDays = 1095,
        ramadanDays = 90,
        lastChecked = "2026-12-31",
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
        checked = listOf("2026-01-01..2026-12-31"),
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
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = nzFianzUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-03-01..2026-03-31", "2026-06-01..2026-06-30", "2026-09-01..2026-09-30", "2026-12-01..2026-12-31"),
    )

    private fun nzFianzUnits(): Map<String, Map<String, Int>> = mapOf(
        "auckland" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun omMara() = ProofStamp(
        entryId = "om.mara",
        places = 1,
        placeDays = 488,
        ramadanDays = 30,
        lastChecked = "2031-09-30",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = omMaraUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2021-01-01..2021-01-31", "2021-07-01..2021-07-31", "2026-01-01..2026-12-31", "2031-03-01..2031-03-31", "2031-09-01..2031-09-30"),
    )

    private fun omMaraUnits(): Map<String, Map<String, Int>> = mapOf(
        "muscat" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun otherTurkey() = ProofStamp(
        entryId = "other.turkey",
        places = 9,
        placeDays = 2613,
        ramadanDays = 174,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-25..2026-11-18", "2027-01-01..2027-12-31"),
    )

    private fun pkKarachi() = ProofStamp(
        entryId = "pk.karachi",
        places = 6,
        placeDays = 637,
        ramadanDays = 0,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 3, "asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = pkKarachiUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-06-01..2026-06-30", "2026-09-01..2026-09-30", "2026-12-01..2026-12-31", "2027-06-01..2027-06-30", "2027-09-01..2027-09-30", "2027-12-01..2027-12-31"),
    )

    private fun pkKarachiUnits(): Map<String, Map<String, Int>> = mapOf(
        "karachi" to mapOf("asrHanafi" to 3, "asrStandard" to 3, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun psGazaAwqaf() = ProofStamp(
        entryId = "ps.gaza.awqaf",
        places = 13,
        placeDays = 78,
        ramadanDays = 0,
        lastChecked = "2020-07-21",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = psGazaAwqafUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2020-06-22", "2020-06-26", "2020-07-03", "2020-07-10", "2020-07-17", "2020-07-21"),
    )

    private fun psGazaAwqafUnits(): Map<String, Map<String, Int>> = mapOf(
        "gaza" to mapOf("asrStandard" to 1, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun psIftaa() = ProofStamp(
        entryId = "ps.iftaa",
        places = 29,
        placeDays = 22634,
        ramadanDays = 1839,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 3, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = psIftaaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2012-01-01..2012-12-31", "2026-01-01..2027-12-31"),
        checkedByUnit = psIftaaChecked(),
    )

    private fun psIftaaChecked(): Map<String, List<String>> = mapOf(
        "ps.iftaa.acre" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.beersheba" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.beisan" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.beit-awwa" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.bethlehem" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.deir-al-balah" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.dura" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.haifa" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.idhna" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.jaffa" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.jenin" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.jericho" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.kafr-qasim" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.khan-yunis" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.lydd" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.nazareth" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.qalqilya" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.rafah" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.ramallah" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.ramla" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.safed" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.tayibe" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.tiberias" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.tulkarm" to listOf("2026-01-01..2027-12-31"),
        "ps.iftaa.umm-al-fahm" to listOf("2026-01-01..2027-12-31"),
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
        placeDays = 5883,
        ramadanDays = 475,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 3, "maghrib" to 3, "sunrise" to 1),
        worstLateByUnit = qaCalendarhouseUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "Al Khor follows Doha's calendar, and Taqwa also keeps Al Khor's own later summer sunset with the calendar's Maghrib + 3 (its Ramadan Maghrib, kept all year)", events = listOf("isha", "maghrib")),
        ),
        checked = listOf("2023-02-03", "2023-02-24", "2023-03-25", "2023-06-09..2023-06-10", "2023-09-23", "2023-12-10", "2024-02-21", "2024-05-21", "2024-05-27", "2024-06-15", "2024-06-17", "2024-07-04", "2024-08-02", "2024-08-06", "2024-08-08", "2024-09-15", "2024-11-05..2024-11-06", "2024-11-09", "2024-11-14", "2024-12-03", "2024-12-17", "2025-01-26", "2025-02-12", "2025-02-16", "2025-02-21", "2025-03-01", "2025-03-28", "2025-03-30", "2025-04-05", "2025-04-16", "2025-05-20", "2025-05-27", "2025-06-24", "2025-07-13", "2025-07-15", "2025-08-05", "2025-09-10", "2025-10-18", "2025-10-23", "2025-11-14", "2025-12-12", "2026-01-01..2027-12-31"),
        checkedByUnit = qaCalendarhouseChecked(),
    )

    private fun qaCalendarhouseChecked(): Map<String, List<String>> = mapOf(
        "al-khor" to listOf("2026-01-01..2027-12-31"),
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
        lastChecked = "2026-09-25",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 1, "sunrise" to 2),
        worstLateByUnit = ruDumrfUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 4, reason = "The end of suhur is DUM RF's own printed dawn, which from May to August moves by up to a quarter of a degree of the sun a day; the app takes the earliest of the neighbouring days (rulings R28, R39), up to 4 min before the printed time (May and late July to mid-August).", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 4, reason = "From May to August DUM RF's Fajr is sunrise − 0.30 of the night, when the sun stands up to a quarter of a degree higher or lower than the day before; the app takes the latest of the neighbouring days so that it holds in every year of the leap cycle (ruling R28), up to 4 min after the printed time (May, and late July to mid-August).", events = listOf("fajr")),
            ProofLateLimit(minutes = 5, reason = "From May to August DUM RF's Isha is sunset + a fraction of the night that it steps every few days (0.25 in early May, 0.30 in June, 0.25 again by August); between its held days the app takes the largest fraction within three days and the latest of the neighbouring days (ruling R28), up to 5 min after the printed time (early and mid-May).", events = listOf("isha")),
        ),
        checked = listOf("2025-05-01", "2025-06-01..2025-06-30", "2025-10-01", "2025-12-07", "2025-12-10", "2025-12-13", "2025-12-17", "2025-12-20", "2025-12-23", "2025-12-26", "2026-05-13", "2026-05-19", "2026-06-11", "2026-06-13", "2026-06-25", "2026-07-01..2026-08-31", "2026-09-25"),
    )

    private fun ruDumrfUnits(): Map<String, Map<String, Int>> = mapOf(
        "moscow" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 1, "sunrise" to 2),
    )

    private fun ruDumrt() = ProofStamp(
        entryId = "ru.dumrt",
        places = 19,
        placeDays = 6935,
        ramadanDays = 570,
        lastChecked = "2026-12-31",
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
        checked = listOf("2026-01-01..2026-12-31"),
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
        places = 3242,
        placeDays = 2493800,
        ramadanDays = 201850,
        lastChecked = "2030-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 5, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        worstLateByUnit = saUmmalquraUnits1() + saUmmalquraUnits2() + saUmmalquraUnits3() + saUmmalquraUnits4() + saUmmalquraUnits5(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = "within a unit's reach, a minute of longitude, the start shown is the later of the unit's table and the user's own sun, so toward the reach's edge it comes up to 2 min after the table (ruling R40; each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("asrStandard", "dhuhr", "isha", "maghrib")),
            ProofLateLimit(minutes = 2, reason = "on the few dates a year Umm al-Qura's calendar repeats the previous day's times, Taqwa shows the later Fajr of the two days, so as never to be early if the calendar stops repeating them; and across a unit's reach the later of the unit's table and the user's own sun (the reach rows, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "at this unit's reach's edge a lag date's later Fajr of the two days and the user's own later dawn meet, so Fajr comes up to 3 min after the unit's table there (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("fajr")),
            ProofLateLimit(minutes = 3, reason = "on the lag dates Taqwa shows the earlier sunrise and end of eating of the two days, and within a unit's reach the earlier of the unit's table and the user's own, so toward the reach's edge they come up to 3 min before the table (each unit's own tables replayed across its reach, 9 Oct 2026)", events = listOf("endOfEating", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "the same table computed a degree north of Tayma: the sunrise and the end of eating shown in the town, the earlier of that table's and the town's own, come up to 3 min before the table's, and Dhuhr, Asr, Maghrib and Isha up to 3 after it (class D's 3, not the entry's 2; city-points round, 9 Oct 2026)", events = listOf("asrStandard", "dhuhr", "endOfEating", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 5, reason = "KACST's list places Tayma a degree north of the town, so its Tayma table is computed 111 km north of it, and the Fajr shown in the town, the later of that table's and the town's own, comes up to 5 min after the table in spring and autumn (city-points round, 9 Oct 2026)", events = listOf("fajr")),
        ),
        checked = listOf("2024-01-01..2030-12-31"),
        checkedByUnit = saUmmalquraChecked1() + saUmmalquraChecked2() + saUmmalquraChecked3() + saUmmalquraChecked4() + saUmmalquraChecked5(),
    )

    private fun saUmmalquraChecked1(): Map<String, List<String>> = mapOf(
        "abanat" to listOf("2026-01-01..2027-12-31"),
        "abha" to listOf("2025-01-01..2027-12-31"),
        "abqaiq" to listOf("2026-01-01..2027-12-31"),
        "abu-arish" to listOf("2026-01-01..2027-12-31"),
        "ad-dilam" to listOf("2026-01-01..2027-12-31"),
        "ad-diriyah" to listOf("2026-01-01..2027-12-31"),
        "addayer" to listOf("2026-01-01..2027-12-31"),
        "adham" to listOf("2026-01-01..2027-12-31"),
        "afif" to listOf("2026-01-01..2027-12-31"),
        "ahad-al-masarihah" to listOf("2026-01-01..2027-12-31"),
        "ahad-rafidah" to listOf("2026-01-01..2027-12-31"),
        "al-aflaj" to listOf("2026-01-01..2027-12-31"),
        "al-ahsa" to listOf("2026-01-01..2027-12-31"),
        "al-amwah" to listOf("2026-01-01..2027-12-31"),
        "al-aqiq" to listOf("2026-01-01..2027-12-31"),
        "al-ardhiyat" to listOf("2026-01-01..2027-12-31"),
        "al-aridhah" to listOf("2026-01-01..2027-12-31"),
        "al-bada" to listOf("2026-01-01..2027-12-31"),
        "al-badayea" to listOf("2026-01-01..2027-12-31"),
        "al-bahah" to listOf("2026-01-01..2027-12-31"),
        "al-bayda" to listOf("2026-01-01..2027-12-31"),
        "al-birk" to listOf("2026-01-01..2027-12-31"),
        "al-bukayriyah" to listOf("2026-01-01..2027-12-31"),
        "al-busur" to listOf("2026-01-01..2027-12-31"),
        "al-duwadimi" to listOf("2026-01-01..2027-12-31"),
        "al-edabi" to listOf("2026-01-01..2027-12-31"),
        "al-farshah" to listOf("2026-01-01..2027-12-31"),
        "al-ghat" to listOf("2026-01-01..2027-12-31"),
        "al-ghazalah" to listOf("2026-01-01..2027-12-31"),
        "al-hait" to listOf("2026-01-01..2027-12-31"),
        "al-hajrah" to listOf("2026-01-01..2027-12-31"),
        "al-harajah" to listOf("2026-01-01..2027-12-31"),
        "al-hariq" to listOf("2026-01-01..2027-12-31"),
        "al-hayr" to listOf("2026-01-01..2027-12-31"),
        "al-henakiyah" to listOf("2026-01-01..2027-12-31"),
        "al-hofuf" to listOf("2026-01-01..2027-12-31"),
        "al-is" to listOf("2026-01-01..2027-12-31"),
        "al-jubail" to listOf("2026-01-01..2027-12-31"),
        "al-jumum" to listOf("2026-01-01..2027-12-31"),
        "al-kamil" to listOf("2026-01-01..2027-12-31"),
    )

    private fun saUmmalquraChecked2(): Map<String, List<String>> = mapOf(
        "al-kharj" to listOf("2026-01-01..2027-12-31"),
        "al-kharkhir" to listOf("2026-01-01..2027-12-31"),
        "al-khobar" to listOf("2026-01-01..2027-12-31"),
        "al-khurma" to listOf("2026-01-01..2027-12-31"),
        "al-lith" to listOf("2026-01-01..2027-12-31"),
        "al-majmaah" to listOf("2026-01-01..2027-12-31"),
        "al-makhwah" to listOf("2026-01-01..2027-12-31"),
        "al-mithnab" to listOf("2026-01-01..2027-12-31"),
        "al-mubarraz" to listOf("2026-01-01..2027-12-31"),
        "al-muwayh" to listOf("2026-01-01..2027-12-31"),
        "al-muzahimiyah" to listOf("2026-01-01..2027-12-31"),
        "al-namas" to listOf("2026-01-01..2027-12-31"),
        "al-qatif" to listOf("2026-01-01..2027-12-31"),
        "al-qunfudhah" to listOf("2026-01-01..2027-12-31"),
        "al-qurayyat" to listOf("2026-01-01..2027-12-31"),
        "al-quwaiiyah" to listOf("2026-01-01..2027-12-31"),
        "al-quwarah" to listOf("2026-01-01..2027-12-31"),
        "al-reeth" to listOf("2026-01-01..2027-12-31"),
        "al-udayd" to listOf("2026-01-01..2027-12-31"),
        "al-ula" to listOf("2026-01-01..2027-12-31"),
        "al-uwayqiliyah" to listOf("2026-01-01..2027-12-31"),
        "al-uyaynah" to listOf("2026-01-01..2027-12-31"),
        "al-wajh" to listOf("2026-01-01..2027-12-31"),
        "alasyah" to listOf("2026-01-01..2027-12-31"),
        "aldarb" to listOf("2026-01-01..2027-12-31"),
        "alharth" to listOf("2026-01-01..2027-12-31"),
        "almajaridah" to listOf("2026-01-01..2027-12-31"),
        "almandaq" to listOf("2026-01-01..2027-12-31"),
        "alqura" to listOf("2026-01-01..2027-12-31"),
        "an-nabhaniyah" to listOf("2026-01-01..2027-12-31"),
        "ar-rass" to listOf("2026-01-01..2027-12-31"),
        "ar-rayn" to listOf("2026-01-01..2027-12-31"),
        "arar" to listOf("2026-01-01..2027-12-31"),
        "as-sulaymi" to listOf("2026-01-01..2027-12-31"),
        "as-sulayyil" to listOf("2026-01-01..2027-12-31"),
        "ash-shamli" to listOf("2026-01-01..2027-12-31"),
        "ash-shimasiyah" to listOf("2026-01-01..2027-12-31"),
        "ash-shinan" to listOf("2026-01-01..2027-12-31"),
        "at-tuwal" to listOf("2026-01-01..2027-12-31"),
        "az-zulfi" to listOf("2026-01-01..2027-12-31"),
    )

    private fun saUmmalquraChecked3(): Map<String, List<String>> = mapOf(
        "badr" to listOf("2026-01-01..2027-12-31"),
        "badr-al-janoub" to listOf("2026-01-01..2027-12-31"),
        "bahrah" to listOf("2026-01-01..2027-12-31"),
        "baish" to listOf("2026-01-01..2027-12-31"),
        "baljurashi" to listOf("2026-01-01..2027-12-31"),
        "bani-hasan" to listOf("2026-01-01..2027-12-31"),
        "baqaa" to listOf("2026-01-01..2027-12-31"),
        "bariq" to listOf("2026-01-01..2027-12-31"),
        "belqarn" to listOf("2026-01-01..2027-12-31"),
        "bishah" to listOf("2026-01-01..2027-12-31"),
        "buraydah" to listOf("2026-01-01..2027-12-31"),
        "damad" to listOf("2026-01-01..2027-12-31"),
        "dammam" to listOf("2025-01-01..2027-12-31"),
        "dariyah" to listOf("2026-01-01..2027-12-31"),
        "dhahban" to listOf("2026-01-01..2027-12-31"),
        "dhahran" to listOf("2026-01-01..2027-12-31"),
        "dhahran-al-janub" to listOf("2026-01-01..2027-12-31"),
        "dhurma" to listOf("2026-01-01..2027-12-31"),
        "duba" to listOf("2026-01-01..2027-12-31"),
        "dumah-al-jandal" to listOf("2026-01-01..2027-12-31"),
        "farasan-island" to listOf("2026-01-01..2027-12-31"),
        "fayfa" to listOf("2026-01-01..2027-12-31"),
        "ghamid-az-zinad" to listOf("2026-01-01..2027-12-31"),
        "hafar-al-batin" to listOf("2026-01-01..2027-12-31"),
        "hail" to listOf("2026-01-01..2027-12-31"),
        "haql" to listOf("2026-01-01..2027-12-31"),
        "harub" to listOf("2026-01-01..2027-12-31"),
        "hawtat-sudayr" to listOf("2026-01-01..2027-12-31"),
        "howtat-bani-tamim" to listOf("2026-01-01..2027-12-31"),
        "hubuna" to listOf("2026-01-01..2027-12-31"),
        "huraymila" to listOf("2026-01-01..2027-12-31"),
        "jalajil" to listOf("2026-01-01..2027-12-31"),
        "jazan" to listOf("2026-01-01..2027-12-31"),
        "jeddah" to listOf("2025-01-01..2027-12-31"),
        "khafji" to listOf("2026-01-01..2027-12-31"),
        "khamis-mushait" to listOf("2026-01-01..2027-12-31"),
        "khaybar" to listOf("2026-01-01..2027-12-31"),
        "khbash" to listOf("2026-01-01..2027-12-31"),
        "khulais" to listOf("2026-01-01..2027-12-31"),
        "layla" to listOf("2026-01-01..2027-12-31"),
    )

    private fun saUmmalquraChecked4(): Map<String, List<String>> = mapOf(
        "madinah" to listOf("2025-01-01..2027-12-31"),
        "mahd-al-thahab" to listOf("2026-01-01..2027-12-31"),
        "malham" to listOf("2026-01-01..2027-12-31"),
        "marat" to listOf("2026-01-01..2027-12-31"),
        "mawqaq" to listOf("2026-01-01..2027-12-31"),
        "maysan" to listOf("2026-01-01..2027-12-31"),
        "muhayil" to listOf("2026-01-01..2027-12-31"),
        "nairyah" to listOf("2026-01-01..2027-12-31"),
        "najran" to listOf("2026-01-01..2027-12-31"),
        "qaryat-al-ulya" to listOf("2026-01-01..2027-12-31"),
        "qilwah" to listOf("2026-01-01..2027-12-31"),
        "rabigh" to listOf("2026-01-01..2027-12-31"),
        "rafha" to listOf("2026-01-01..2027-12-31"),
        "ranyah" to listOf("2026-01-01..2027-12-31"),
        "ras-tannurah" to listOf("2026-01-01..2027-12-31"),
        "ras-tanura" to listOf("2026-01-01..2027-12-31"),
        "rawdat-sudayr" to listOf("2026-01-01..2027-12-31"),
        "riyadh-al-khabra" to listOf("2026-01-01..2027-12-31"),
        "rojal" to listOf("2026-01-01..2027-12-31"),
        "rumah" to listOf("2026-01-01..2027-12-31"),
        "sabya" to listOf("2026-01-01..2027-12-31"),
        "sakaka" to listOf("2026-01-01..2027-12-31"),
        "samtah" to listOf("2026-01-01..2027-12-31"),
        "sarat-abidah" to listOf("2026-01-01..2027-12-31"),
        "shagra" to listOf("2026-01-01..2027-12-31"),
        "sharorah" to listOf("2026-01-01..2027-12-31"),
        "simira" to listOf("2026-01-01..2027-12-31"),
        "sudus" to listOf("2026-01-01..2027-12-31"),
        "suwayr" to listOf("2026-01-01..2027-12-31"),
        "tabuk" to listOf("2026-01-01..2027-12-31"),
        "taif" to listOf("2026-01-01..2027-12-31"),
        "tanumah" to listOf("2026-01-01..2027-12-31"),
        "tarib" to listOf("2026-01-01..2027-12-31"),
        "tarut" to listOf("2026-01-01..2027-12-31"),
        "tathleeth" to listOf("2026-01-01..2027-12-31"),
        "tayma" to listOf("2026-01-01..2027-12-31"),
        "thadig" to listOf("2026-01-01..2027-12-31"),
        "thar" to listOf("2026-01-01..2027-12-31"),
        "thuwal" to listOf("2026-01-01..2027-12-31"),
        "tubarjal" to listOf("2026-01-01..2027-12-31"),
    )

    private fun saUmmalquraChecked5(): Map<String, List<String>> = mapOf(
        "tumair" to listOf("2026-01-01..2027-12-31"),
        "turaif" to listOf("2024-01-01..2024-12-31", "2026-01-01..2030-12-31"),
        "turbah" to listOf("2026-01-01..2027-12-31"),
        "umluj" to listOf("2026-01-01..2027-12-31"),
        "unayzah" to listOf("2026-01-01..2027-12-31"),
        "uqlat-as-suqur" to listOf("2026-01-01..2027-12-31"),
        "uyun-al-jawa" to listOf("2026-01-01..2027-12-31"),
        "wadi-ad-dawasir" to listOf("2026-01-01..2027-12-31"),
        "wadi-al-fara" to listOf("2026-01-01..2027-12-31"),
        "yadamah" to listOf("2026-01-01..2027-12-31"),
        "yanbu" to listOf("2026-01-01..2027-12-31"),
    )

    private fun saUmmalquraUnits1(): Map<String, Map<String, Int>> = mapOf(
        "abanat" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "abha" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "abqaiq" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "abu-arish" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ad-dilam" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ad-diriyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "addayer" to mapOf("asrStandard" to 1, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "adham" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "afif" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "ahad-al-masarihah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ahad-rafidah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-aflaj" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-ahsa" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-amwah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-aqiq" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-ardhiyat" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-aridhah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-bada" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-badayea" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-bahah" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-bayda" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-birk" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-bukayriyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-busur" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-duwadimi" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-edabi" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-farshah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-ghat" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-ghazalah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-hait" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-hajrah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-harajah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-hariq" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-hayr" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-henakiyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-hofuf" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-is" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-jubail" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-jumum" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-kamil" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun saUmmalquraUnits2(): Map<String, Map<String, Int>> = mapOf(
        "al-kharj" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-kharkhir" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-khobar" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-khurma" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-lith" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-majmaah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-makhwah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-mithnab" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-mubarraz" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-muwayh" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-muzahimiyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-namas" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-qatif" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-qunfudhah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-qurayyat" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-quwaiiyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-quwarah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-reeth" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-udayd" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "al-ula" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-uwayqiliyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-uyaynah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "al-wajh" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "alasyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "aldarb" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "alharth" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "almajaridah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "almandaq" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "alqura" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "an-nabhaniyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "ar-rass" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "ar-rayn" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "arar" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "as-sulaymi" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "as-sulayyil" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ash-shamli" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ash-shimasiyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "ash-shinan" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "at-tuwal" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "az-zulfi" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun saUmmalquraUnits3(): Map<String, Map<String, Int>> = mapOf(
        "badr" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "badr-al-janoub" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "bahrah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "baish" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "baljurashi" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "bani-hasan" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "baqaa" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "bariq" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "belqarn" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "bishah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "buraydah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "damad" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "dammam" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "dariyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "dhahban" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "dhahran" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "dhahran-al-janub" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "dhurma" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "duba" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "dumah-al-jandal" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "farasan-island" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "fayfa" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "ghamid-az-zinad" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "hafar-al-batin" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "hail" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "haql" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "harub" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "hawtat-sudayr" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "howtat-bani-tamim" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "hubuna" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "huraymila" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "jalajil" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "jazan" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "jeddah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "khafji" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "khamis-mushait" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "khaybar" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "khbash" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "khulais" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "layla" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun saUmmalquraUnits4(): Map<String, Map<String, Int>> = mapOf(
        "madinah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "mahd-al-thahab" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "makkah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "malham" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "marat" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "mawqaq" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "maysan" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "muhayil" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "nairyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "najran" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "qaryat-al-ulya" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "qilwah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "rabigh" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "rafha" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "ranyah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ras-tannurah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "ras-tanura" to mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "rawdat-sudayr" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "riyadh" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "riyadh-al-khabra" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "rojal" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "rumah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "sabya" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "sakaka" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "samtah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "sarat-abidah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "shagra" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "sharorah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "simira" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "sudus" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "suwayr" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "tabuk" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "taif" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "tanumah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "tarib" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "tarut" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 3, "fajr" to 2, "isha" to 1, "maghrib" to 1, "sunrise" to 2),
        "tathleeth" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "tayma" to mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 5, "isha" to 3, "maghrib" to 3, "sunrise" to 3),
        "thadig" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "thar" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
    )

    private fun saUmmalquraUnits5(): Map<String, Map<String, Int>> = mapOf(
        "thuwal" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "tubarjal" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "tumair" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "turaif" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 3, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "turbah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "umluj" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "unayzah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "uqlat-as-suqur" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "uyun-al-jawa" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "wadi-ad-dawasir" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "wadi-al-fara" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
        "yadamah" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        "yanbu" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 3, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 3),
    )

    private fun sdMinistry() = ProofStamp(
        entryId = "sd.ministry",
        places = 1,
        placeDays = 30,
        ramadanDays = 30,
        lastChecked = "2022-05-01",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 1, "endOfEating" to 7, "fajr" to 2, "isha" to 20, "maghrib" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "fajr", "maghrib")),
            ProofLateLimit(minutes = 7, reason = "News reports of Sudan's 2026 imsakiya, not the table itself, put its dawn near 19.4°, earlier than the 18.3° of the one table held (Khartoum, Ramadan 1443); until a current table is held the fast begins at the 19.5° dawn, up to 7 min before that table's Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 20, reason = "A 2026 Ramadan imsakiya circulating under the Fiqh Academy's name (not the verified 2022 table) puts Isha at Maghrib + 90; on the entry's Ramadan dates only, Isha never earlier than that, up to 20 min after the 2022 table's 18.0° angle.", events = listOf("isha")),
        ),
        checked = listOf("2022-04-02..2022-05-01"),
    )

    private fun seCautious() = ProofStamp(
        entryId = "se.cautious",
        places = 23,
        placeDays = 9344,
        ramadanDays = 717,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 7, "dhuhr" to 5, "endOfEating" to 154, "fajr" to 111, "isha" to 170, "maghrib" to 126, "sunrise" to 129),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 5, reason = "Zuhr is the later member's, Diyanet's (+ 5): up to 5 min after Rabita's at Helsinki.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 7, reason = "Asr is the later member's, in the later school (the school is not known): Diyanet's, up to 7 min after Rabita's at Helsinki.", events = listOf("asrStandard")),
            ProofLateLimit(minutes = 111, reason = "Fajr is the later member's, Diyanet's European takdir on its own city curves (Helsinki's and Copenhagen's since the monitor round): up to 109 min after Rabita's Helsinki calendar in summer, and 111 at the towns of the Helsinki region its calendar is held at; at Stockholm 6. Rabita's own Fajr follows its calendar.", events = listOf("fajr")),
            ProofLateLimit(minutes = 126, reason = "Maghrib is capped at Diyanet's (its sunset + 7) where the members spread: up to 2 min after it, and from late May to late July, where Diyanet holds its tables' day to nineteen hours around Dhuhr and prints its Maghrib before the sun sets, never before the real sunset: up to 4 min after its Turku table and 126 after its Luleå table.", events = listOf("maghrib")),
            ProofLateLimit(minutes = 129, reason = "Sunrise is the earlier member's, Diyanet's (the sun's less 7): up to 6 min before Rabita's at Helsinki, and from late May to late July, where Diyanet holds its tables' day to nineteen hours around Dhuhr and prints its sunrise after the sun's, up to 11 min before its Helsinki table and 129 before its Luleå table (the app keeps the sun's, the safe side).", events = listOf("sunrise")),
            ProofLateLimit(minutes = 154, reason = "The end of eating is the earlier member's: Rabita's own earliest dawns, near the middle of the night from April to August (ruling R39), applied at Copenhagen and Stockholm, up to 137 and 136 min before Diyanet's imsak there, and up to 154 before its Malmö and Aarhus tables in August; at Helsinki up to 25 min before Rabita's own Fajr.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 170, reason = "Isha is the later member's, Rabita's own model: 17° while the sun reaches it, near midnight in late spring and late summer, up to 155 min after Rabita's calendar at Helsinki and 136 after Diyanet's Stockholm table, 170 after Diyanet's Luleå table in April and September (its takdir Isha comes earlier the further north).", events = listOf("isha")),
        ),
        checked = listOf("2025-12-01..2026-03-30", "2026-04-01..2027-12-31"),
    )

    private fun sgMuis() = ProofStamp(
        entryId = "sg.muis",
        places = 1,
        placeDays = 1096,
        ramadanDays = 89,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 3, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = "MUIS's own Standard Asr is not reproduced to the minute; a further minute of drift near the equinoxes past class B's own margin", events = listOf("asrStandard")),
        ),
        checked = listOf("2024-01-01..2026-12-31"),
    )

    private fun syAwqaf() = ProofStamp(
        entryId = "sy.awqaf",
        places = 16,
        placeDays = 30,
        ramadanDays = 15,
        lastChecked = "2026-10-01",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 1),
        worstLateByUnit = syAwqafUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-02-19", "2026-10-01"),
    )

    private fun syAwqafUnits(): Map<String, Map<String, Int>> = mapOf(
        "damascus" to mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 2, "sunrise" to 1),
        "umayyad-mosque" to mapOf("asrStandard" to 1, "dhuhr" to 2, "endOfEating" to 1, "fajr" to 0, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun tnInm() = ProofStamp(
        entryId = "tn.inm",
        places = 7,
        placeDays = 1573,
        ramadanDays = 124,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 1, "isha" to 2, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = tnInmUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2019-11-10", "2019-12-21", "2020-01-15", "2020-03-20", "2020-05-10", "2020-06-21", "2020-08-01", "2020-09-25", "2020-11-10", "2020-12-21", "2021-01-15", "2021-03-20", "2021-05-10", "2021-06-21", "2021-08-01", "2021-09-25", "2021-11-10", "2021-12-21", "2022-01-15", "2022-03-20", "2022-05-10", "2022-06-21", "2022-08-01", "2022-09-25", "2022-11-10", "2022-12-21", "2023-01-15", "2023-03-20", "2023-05-10", "2023-06-21", "2023-08-01", "2023-09-25", "2023-11-10", "2023-12-21", "2024-01-15", "2024-03-20", "2024-05-10", "2024-06-21", "2024-08-01", "2024-09-25", "2024-11-10", "2024-12-21", "2025-01-15", "2025-03-20", "2025-05-10", "2025-06-21", "2025-08-01", "2025-09-25", "2025-11-10", "2025-12-21", "2026-01-01..2026-12-31"),
        checkedByUnit = tnInmChecked(),
    )

    private fun tnInmChecked(): Map<String, List<String>> = mapOf(
        "ben-guerdane" to listOf("2026-01-01", "2026-01-04", "2026-01-07", "2026-01-10", "2026-01-13", "2026-01-16", "2026-01-19", "2026-01-22", "2026-01-25", "2026-01-28", "2026-01-31", "2026-02-03", "2026-02-06", "2026-02-09", "2026-02-12", "2026-02-15", "2026-02-18", "2026-02-21", "2026-02-24", "2026-02-27", "2026-03-02", "2026-03-05", "2026-03-08", "2026-03-11", "2026-03-14", "2026-03-17", "2026-03-20", "2026-03-23", "2026-03-26", "2026-03-29", "2026-04-01", "2026-04-04", "2026-04-07", "2026-04-10", "2026-04-13", "2026-04-16", "2026-04-19", "2026-04-22", "2026-04-25", "2026-04-28", "2026-05-01", "2026-05-04", "2026-05-07", "2026-05-10", "2026-05-13", "2026-05-16", "2026-05-19", "2026-05-22", "2026-05-25", "2026-05-28", "2026-05-31", "2026-06-03", "2026-06-06", "2026-06-09", "2026-06-12", "2026-06-15", "2026-06-18", "2026-06-21", "2026-06-24", "2026-06-27", "2026-06-30", "2026-07-03", "2026-07-06", "2026-07-09", "2026-07-12", "2026-07-15", "2026-07-18", "2026-07-21", "2026-07-24", "2026-07-27", "2026-07-30", "2026-08-02", "2026-08-05", "2026-08-08", "2026-08-11", "2026-08-14", "2026-08-17", "2026-08-20", "2026-08-23", "2026-08-26", "2026-08-29", "2026-09-01", "2026-09-04", "2026-09-07", "2026-09-10", "2026-09-13", "2026-09-16", "2026-09-19", "2026-09-22", "2026-09-25", "2026-09-28..2026-10-25", "2026-10-28", "2026-10-31", "2026-11-03", "2026-11-06", "2026-11-09", "2026-11-12", "2026-11-15", "2026-11-18", "2026-11-21", "2026-11-24", "2026-11-27", "2026-11-30", "2026-12-03", "2026-12-06", "2026-12-09", "2026-12-12", "2026-12-15", "2026-12-18", "2026-12-21", "2026-12-24", "2026-12-27", "2026-12-30"),
        "sfax" to listOf("2026-01-01..2026-12-31"),
        "tabarka" to listOf("2026-01-01", "2026-01-04", "2026-01-07", "2026-01-10", "2026-01-13", "2026-01-16", "2026-01-19", "2026-01-22", "2026-01-25", "2026-01-28", "2026-01-31", "2026-02-03", "2026-02-06", "2026-02-09", "2026-02-12", "2026-02-15", "2026-02-18", "2026-02-21", "2026-02-24", "2026-02-27", "2026-03-02", "2026-03-05", "2026-03-08", "2026-03-11", "2026-03-14", "2026-03-17", "2026-03-20", "2026-03-23", "2026-03-26", "2026-03-29", "2026-04-01", "2026-04-04", "2026-04-07", "2026-04-10", "2026-04-13", "2026-04-16", "2026-04-19", "2026-04-22", "2026-04-25", "2026-04-28", "2026-05-01", "2026-05-04", "2026-05-07", "2026-05-10", "2026-05-13", "2026-05-16", "2026-05-19", "2026-05-22", "2026-05-25", "2026-05-28", "2026-05-31", "2026-06-03", "2026-06-06", "2026-06-09", "2026-06-12", "2026-06-15", "2026-06-18", "2026-06-21", "2026-06-24", "2026-06-27", "2026-06-30", "2026-07-03", "2026-07-06", "2026-07-09", "2026-07-12", "2026-07-15", "2026-07-18", "2026-07-21", "2026-07-24", "2026-07-27", "2026-07-30", "2026-08-02", "2026-08-05", "2026-08-08", "2026-08-11", "2026-08-14", "2026-08-17", "2026-08-20", "2026-08-23", "2026-08-26", "2026-08-29", "2026-09-01", "2026-09-04", "2026-09-07", "2026-09-10", "2026-09-13", "2026-09-16", "2026-09-19", "2026-09-22", "2026-09-25", "2026-09-28..2026-10-25", "2026-10-28", "2026-10-31", "2026-11-03", "2026-11-06", "2026-11-09", "2026-11-12", "2026-11-15", "2026-11-18", "2026-11-21", "2026-11-24", "2026-11-27", "2026-11-30", "2026-12-03", "2026-12-06", "2026-12-09", "2026-12-12", "2026-12-15", "2026-12-18", "2026-12-21", "2026-12-24", "2026-12-27", "2026-12-30"),
        "tala" to listOf("2026-01-01", "2026-01-04", "2026-01-07", "2026-01-10", "2026-01-13", "2026-01-16", "2026-01-19", "2026-01-22", "2026-01-25", "2026-01-28", "2026-01-31", "2026-02-03", "2026-02-06", "2026-02-09", "2026-02-12", "2026-02-15", "2026-02-18", "2026-02-21", "2026-02-24", "2026-02-27", "2026-03-02", "2026-03-05", "2026-03-08", "2026-03-11", "2026-03-14", "2026-03-17", "2026-03-20", "2026-03-23", "2026-03-26", "2026-03-29", "2026-04-01", "2026-04-04", "2026-04-07", "2026-04-10", "2026-04-13", "2026-04-16", "2026-04-19", "2026-04-22", "2026-04-25", "2026-04-28", "2026-05-01", "2026-05-04", "2026-05-07", "2026-05-10", "2026-05-13", "2026-05-16", "2026-05-19", "2026-05-22", "2026-05-25", "2026-05-28", "2026-05-31", "2026-06-03", "2026-06-06", "2026-06-09", "2026-06-12", "2026-06-15", "2026-06-18", "2026-06-21", "2026-06-24", "2026-06-27", "2026-06-30", "2026-07-03", "2026-07-06", "2026-07-09", "2026-07-12", "2026-07-15", "2026-07-18", "2026-07-21", "2026-07-24", "2026-07-27", "2026-07-30", "2026-08-02", "2026-08-05", "2026-08-08", "2026-08-11", "2026-08-14", "2026-08-17", "2026-08-20", "2026-08-23", "2026-08-26", "2026-08-29", "2026-09-01", "2026-09-04", "2026-09-07", "2026-09-10", "2026-09-13", "2026-09-16", "2026-09-19", "2026-09-22", "2026-09-25", "2026-09-28..2026-10-25", "2026-10-28", "2026-10-31", "2026-11-03", "2026-11-06", "2026-11-09", "2026-11-12", "2026-11-15", "2026-11-18", "2026-11-21", "2026-11-24", "2026-11-27", "2026-11-30", "2026-12-03", "2026-12-06", "2026-12-09", "2026-12-12", "2026-12-15", "2026-12-18", "2026-12-21", "2026-12-24", "2026-12-27", "2026-12-30"),
        "tataouine" to listOf("2026-01-01..2026-12-31"),
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
        placeDays = 9240,
        ramadanDays = 638,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 2, "dhuhr" to 2, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 2, "sunrise" to 2),
        worstLateByUnit = trDiyanetUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-25..2026-11-18", "2027-01-01..2027-12-31"),
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
        places = 34,
        placeDays = 13913,
        ramadanDays = 986,
        lastChecked = "2027-12-31",
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
        checked = listOf("2026-09-25..2026-11-18", "2027-01-01..2027-12-31"),
        checkedByUnit = trDiyanetEuropeChecked(),
    )

    private fun trDiyanetEuropeChecked(): Map<String, List<String>> = mapOf(
        "tr.diyanet.europe.aalborg" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.aarhus" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.antwerpen" to listOf("2026-09-29..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.bergen" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.copenhagen" to listOf("2026-09-26..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.gent" to listOf("2026-09-29..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.goteborg" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.helsinki" to listOf("2026-09-29..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.kristiansand" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.lille" to listOf("2026-09-29..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.lulea" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.lyon" to listOf("2026-09-29..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.malmo" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.oulu" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.stavanger" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.sundsvall" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.tampere" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.trondheim" to listOf("2026-09-29..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.turku" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.umea" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.uppsala" to listOf("2026-09-29..2026-10-29", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.wien" to listOf("2026-09-26..2026-11-18", "2027-01-01..2027-12-31"),
        "tr.diyanet.europe.zurich" to listOf("2026-09-26..2026-11-18", "2027-01-01..2027-12-31"),
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
        "tr.diyanet.europe.lille" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 4, "fajr" to 4, "isha" to 4, "maghrib" to 1, "sunrise" to 1),
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
        places = 133,
        placeDays = 26209,
        ramadanDays = 600,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 7, "dhuhr" to 7, "endOfEating" to 34, "fajr" to 34, "isha" to 7, "maghrib" to 7, "sunrise" to 4),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 4, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Sunrise up to 3 min before theirs at the mosques; within a minute's reach of a mosque its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 4.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 7, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Asr up to 5 min at the mosques; within a minute's reach of a mosque its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 6, and beside every other mosque's table of the same member within class D's reach of the user (about 61 km), where a user may still follow it (the owner's rule: never before any of them), up to 7.", events = listOf("asrHanafi")),
            ProofLateLimit(minutes = 7, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Zuhr and Maghrib up to 6 min at the mosques; within a minute's reach of a mosque its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 7.", events = listOf("dhuhr", "maghrib")),
            ProofLateLimit(minutes = 7, reason = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less: Isha up to 5 min at the mosques; within a minute's reach of a mosque its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 7.", events = listOf("isha")),
            ProofLateLimit(minutes = 34, reason = "Chicago's mosques differ on Fajr: the end of eating is the 18° block's dawn, never after it, up to 32 min before the 15° mosques' Fajr (MCC, MEC, Mosque Foundation, Orland Park) at their mosques; within a minute's reach of a mosque its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 33, and beside every other mosque's table of the same member within class D's reach of the user (about 61 km), where a user may still follow it (the owner's rule: never before any of them), up to 34.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 34, reason = "Chicago's mosques differ on Fajr: the 15° mosques' Fajr, never before it, runs up to 31 min after the 18° block's (Villa Park, Makki, DarusSalam) at their mosques; within a minute's reach of a mosque its point rides beside the user's (rulings R30 and R44), and at the app's own cities near them, east, west, north or south of the mosque, up to 32, and beside every other mosque's table of the same member within class D's reach of the user (about 61 km), where a user may still follow it (the owner's rule: never before any of them), up to 34.", events = listOf("fajr")),
        ),
        checked = listOf("2025-10-01..2025-10-31", "2025-12-01..2026-12-31"),
    )

    private fun usIsna() = ProofStamp(
        entryId = "us.isna",
        places = 1110,
        placeDays = 251779,
        ramadanDays = 17541,
        lastChecked = "2026-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 7, "asrStandard" to 7, "dhuhr" to 8, "endOfEating" to 8, "fajr" to 7, "isha" to 8, "maghrib" to 9, "sunrise" to 6),
        worstLateByUnit = usIsnaUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 6, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: ADAMS moves sunrise 2 min earlier (its safe direction): sunrise, never after its, up to 4 min before the others' at their mosques, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to 5, and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 6.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 7, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Asr, a minute or two after the angle at some tables, up to 5 min after the others' at their mosques, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to 6, and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 7.", events = listOf("asrHanafi", "asrStandard")),
            ProofLateLimit(minutes = 7, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: ADAMS moves Fajr 2 min earlier (its safe direction): Fajr up to 4 min after its, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to 6, and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 7.", events = listOf("fajr")),
            ProofLateLimit(minutes = 8, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Zuhr up to 7 min after the tables that add nothing (8 at Dar Al Noor's and East Cobb's), and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 8.", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 8, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: ADAMS moves its dawn 2 min earlier (its safe direction): the end of eating, never after its Fajr, up to 4 min before the others' at their mosques, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to 5, and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 8.", events = listOf("endOfEating")),
            ProofLateLimit(minutes = 8, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Isha, a minute or two after the angle at some tables (ADAMS's Isha 2 min later by its rule), up to 5 min after the others' at their mosques, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to 7 (MEC's), and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 8.", events = listOf("isha")),
            ProofLateLimit(minutes = 9, reason = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app is never before any of them: Maghrib up to 6 min after the tables that add nothing at their mosques, and within a minute's reach of a mosque, where its point rides beside the user's (rulings R30 and R44: east of it the time shown is the mosque's, north or south of it the night's length moves the dawn and the dusk), up to 7, and beside every other mosque's table within class D's reach of the user (ruling R40's, about 52 to 72 km), where a user may still follow it (the owner's rule of 11 Oct 2026: never before any of them, nor any end after one), up to 9.", events = listOf("maghrib")),
        ),
        checked = listOf("2025-02-01..2025-03-31", "2025-05-01..2026-12-31"),
        checkedByUnit = usIsnaChecked(),
    )

    private fun usIsnaChecked(): Map<String, List<String>> = mapOf(
        "adams" to listOf("2026-01-01..2026-12-31"),
        "al-iman" to listOf("2026-01-01..2026-12-31"),
        "at-taqwa" to listOf("2026-06-01..2026-06-30", "2026-09-01..2026-10-31", "2026-12-01..2026-12-31"),
        "bay-ridge" to listOf("2026-01-01..2026-12-31"),
        "dar-al-hijrah" to listOf("2026-01-01..2026-12-31"),
        "dar-al-noor" to listOf("2026-01-01..2026-12-31"),
        "dar-us-salaam" to listOf("2026-06-01..2026-06-30", "2026-09-01..2026-09-30"),
        "dearborn" to listOf("2026-09-12..2026-10-11"),
        "ecic" to listOf("2026-01-01..2026-10-30", "2026-11-01..2026-12-31"),
        "epic" to listOf("2025-10-01..2026-09-30"),
        "frisco" to listOf("2026-01-01..2026-03-09", "2026-03-11..2026-04-02", "2026-04-23..2026-07-22", "2026-07-24..2026-10-31", "2026-11-02..2026-12-31"),
        "iagd" to listOf("2026-06-01..2026-06-30", "2026-09-01..2026-10-31", "2026-12-01..2026-12-31"),
        "iant" to listOf("2026-01-01..2026-12-31"),
        "ici" to listOf("2026-01-01..2026-12-31"),
        "icnv" to listOf("2026-01-01..2026-12-31"),
        "icoi" to listOf("2026-01-01..2026-04-05", "2026-04-07..2026-10-31", "2026-11-02..2026-12-31"),
        "icsgv" to listOf("2026-09-25"),
        "iioc" to listOf("2026-01-01..2026-12-31"),
        "isgh" to listOf("2025-02-01..2025-03-31", "2025-05-01..2026-01-31", "2026-04-01..2026-04-30"),
        "isoc" to listOf("2026-02-02..2026-12-31"),
        "jamaica" to listOf("2026-06-01..2026-06-30", "2026-09-01..2026-10-31", "2026-12-01..2026-12-31"),
        "king-fahad" to listOf("2026-09-01..2026-09-30"),
        "maryam" to listOf("2026-01-01..2026-12-31"),
        "masjid-manhattan" to listOf("2026-01-01..2026-12-31"),
        "mca" to listOf("2026-01-01..2026-08-18", "2026-08-20..2026-12-31"),
        "mcc-chicago" to listOf("2025-12-01..2026-01-02", "2026-06-01..2026-06-30", "2026-09-01..2026-10-31"),
        "mcc-silver-spring" to listOf("2026-09-24..2026-09-30"),
        "mec" to listOf("2025-12-01..2026-01-02", "2026-06-01..2026-06-30", "2026-09-01..2026-10-31"),
        "mishkah" to listOf("2026-01-01..2026-11-13", "2026-11-15..2026-11-20", "2026-11-22..2026-11-27", "2026-11-29..2026-12-04", "2026-12-06..2026-12-11", "2026-12-13..2026-12-18", "2026-12-20..2026-12-25", "2026-12-27..2026-12-31"),
        "mosque-foundation" to listOf("2026-06-01..2026-06-30", "2026-09-01..2026-10-31", "2026-12-01..2026-12-31"),
        "ocif" to listOf("2026-09-01..2026-09-30"),
        "orland-park" to listOf("2025-10-01..2025-10-31", "2025-12-01..2025-12-31", "2026-06-01..2026-06-30", "2026-09-01..2026-09-30"),
        "plano-masjid" to listOf("2026-01-01..2026-12-31"),
        "tawheed" to listOf("2026-01-01..2026-12-31"),
        "unity-center" to listOf("2026-09-22", "2026-09-25"),
    )

    private fun usIsnaUnits(): Map<String, Map<String, Int>> = mapOf(
        "adams" to mapOf("asrStandard" to 4, "dhuhr" to 6, "endOfEating" to 3, "fajr" to 6, "isha" to 3, "maghrib" to 5, "sunrise" to 3),
        "al-iman" to mapOf("asrStandard" to 6, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 3, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "at-taqwa" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 8, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 6),
        "bay-ridge" to mapOf("asrStandard" to 6, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "dar-al-hijrah" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 5, "fajr" to 4, "isha" to 6, "maghrib" to 7, "sunrise" to 5),
        "dar-al-noor" to mapOf("asrStandard" to 6, "dhuhr" to 8, "endOfEating" to 6, "fajr" to 4, "isha" to 7, "maghrib" to 5, "sunrise" to 5),
        "dar-us-salaam" to mapOf("asrStandard" to 5, "dhuhr" to 8, "endOfEating" to 4, "fajr" to 6, "isha" to 6, "maghrib" to 7, "sunrise" to 4),
        "dearborn" to mapOf("asrStandard" to 5, "dhuhr" to 8, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "ecic" to mapOf("asrHanafi" to 5, "dhuhr" to 8, "endOfEating" to 5, "fajr" to 4, "isha" to 6, "maghrib" to 7, "sunrise" to 5),
        "epic" to mapOf("asrStandard" to 6, "dhuhr" to 8, "endOfEating" to 5, "fajr" to 5, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "frisco" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 5, "fajr" to 4, "isha" to 6, "maghrib" to 7, "sunrise" to 4),
        "iagd" to mapOf("asrHanafi" to 7, "dhuhr" to 8, "endOfEating" to 5, "fajr" to 7, "isha" to 6, "maghrib" to 9, "sunrise" to 4),
        "iant" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "ici" to mapOf("asrStandard" to 4, "dhuhr" to 2, "endOfEating" to 6, "fajr" to 4, "isha" to 5, "maghrib" to 4, "sunrise" to 5),
        "icnv" to mapOf("asrHanafi" to 5, "dhuhr" to 7, "endOfEating" to 5, "fajr" to 3, "isha" to 5, "maghrib" to 7, "sunrise" to 5),
        "icoi" to mapOf("asrStandard" to 7, "dhuhr" to 8, "endOfEating" to 5, "fajr" to 5, "isha" to 8, "maghrib" to 9, "sunrise" to 5),
        "icsgv" to mapOf("asrStandard" to 6, "dhuhr" to 7, "endOfEating" to 3, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 3),
        "iioc" to mapOf("asrStandard" to 6, "dhuhr" to 8, "endOfEating" to 4, "fajr" to 5, "isha" to 6, "maghrib" to 8, "sunrise" to 4),
        "isgh" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 5, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "isoc" to mapOf("asrStandard" to 7, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 5, "isha" to 7, "maghrib" to 5, "sunrise" to 4),
        "jamaica" to mapOf("asrHanafi" to 5, "dhuhr" to 6, "endOfEating" to 5, "fajr" to 3, "isha" to 4, "maghrib" to 6, "sunrise" to 5),
        "king-fahad" to mapOf("asrStandard" to 3, "dhuhr" to 6, "endOfEating" to 6, "fajr" to 3, "isha" to 3, "maghrib" to 1, "sunrise" to 4),
        "maryam" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 3, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "masjid-manhattan" to mapOf("asrStandard" to 6, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 3, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "mca" to mapOf("asrStandard" to 5, "dhuhr" to 4, "endOfEating" to 5, "fajr" to 4, "isha" to 5, "maghrib" to 4, "sunrise" to 4),
        "mcc-chicago" to mapOf("asrHanafi" to 7, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 5, "isha" to 5, "maghrib" to 6, "sunrise" to 4),
        "mcc-silver-spring" to mapOf("asrHanafi" to 5, "dhuhr" to 3, "endOfEating" to 3, "fajr" to 4, "isha" to 4, "maghrib" to 5, "sunrise" to 4),
        "mec" to mapOf("asrHanafi" to 7, "dhuhr" to 7, "endOfEating" to 5, "fajr" to 5, "isha" to 7, "maghrib" to 6, "sunrise" to 4),
        "mishkah" to mapOf("asrStandard" to 5, "dhuhr" to 8, "endOfEating" to 4, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 4),
        "mosque-foundation" to mapOf("asrHanafi" to 4, "dhuhr" to 6, "endOfEating" to 6, "fajr" to 2, "isha" to 6, "maghrib" to 6, "sunrise" to 4),
        "ocif" to mapOf("asrStandard" to 4, "dhuhr" to 7, "endOfEating" to 4, "fajr" to 3, "isha" to 3, "maghrib" to 5, "sunrise" to 2),
        "orland-park" to mapOf("asrHanafi" to 5, "dhuhr" to 7, "endOfEating" to 6, "fajr" to 4, "isha" to 6, "maghrib" to 7, "sunrise" to 4),
        "plano-masjid" to mapOf("asrStandard" to 5, "dhuhr" to 7, "endOfEating" to 5, "fajr" to 4, "isha" to 5, "maghrib" to 5, "sunrise" to 4),
        "tawheed" to mapOf("asrStandard" to 7, "dhuhr" to 8, "endOfEating" to 5, "fajr" to 5, "isha" to 6, "maghrib" to 7, "sunrise" to 4),
        "unity-center" to mapOf("asrStandard" to 6, "dhuhr" to 6, "endOfEating" to 3, "fajr" to 4, "isha" to 5, "maghrib" to 7, "sunrise" to 3),
    )

    private fun uzBoard() = ProofStamp(
        entryId = "uz.board",
        places = 7,
        placeDays = 133,
        ramadanDays = 0,
        lastChecked = "2026-09-26",
        worstLateMinutes = mapOf("asrHanafi" to 1, "dhuhr" to 6, "endOfEating" to 2, "fajr" to 1, "isha" to 4, "maghrib" to 2, "sunrise" to 6),
        worstLateByUnit = uzBoardUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "endOfEating", "fajr", "maghrib")),
            ProofLateLimit(minutes = 4, reason = "the margin also covers both the 15° and 15.5° Isha eras together (task 7f)", events = listOf("isha")),
            ProofLateLimit(minutes = 6, reason = "the margin also covers the pre-Jul-2026 rule's Dhuhr + 5 min next to the new rule's own (task 7f)", events = listOf("dhuhr")),
            ProofLateLimit(minutes = 6, reason = "the margin also covers the pre-Jul-2026 rule's earlier sunrise (task 7f), and within a minute's reach of Tashkent's point the earlier of its sunrise and the user's own is shown (rulings R30 and R44): up to 6 min before its table's; an early end is the safe side", events = listOf("sunrise")),
        ),
        checked = listOf("2024-07-31", "2024-08-14", "2024-08-28", "2024-09-11", "2024-09-23", "2024-10-30", "2024-11-13", "2024-11-30", "2024-12-28", "2025-01-12", "2025-01-26", "2026-04-11", "2026-05-10", "2026-06-05", "2026-06-07", "2026-06-14", "2026-07-19", "2026-09-25..2026-09-26"),
    )

    private fun uzBoardUnits(): Map<String, Map<String, Int>> = mapOf(
        "tashkent" to mapOf("asrHanafi" to 1, "dhuhr" to 6, "endOfEating" to 2, "fajr" to 1, "isha" to 4, "maghrib" to 2, "sunrise" to 6),
    )

    private fun xkBik() = ProofStamp(
        entryId = "xk.bik",
        places = 44,
        placeDays = 25185,
        ramadanDays = 2045,
        lastChecked = "2027-12-31",
        worstLateMinutes = mapOf("asrStandard" to 8, "dhuhr" to 7, "endOfEating" to 9, "fajr" to 7, "isha" to 7, "maghrib" to 8, "sunrise" to 8),
        worstLateByUnit = xkBikUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 7, reason = "The base table's point rides across Kosovo beside the user's (rulings R30 and R44), with BIK's own town figures (the Takvimi's \"Diferenca e vendeve\": Sharri T + 2, Ferizaj, Gjilani, Prishtina, Podujeva and Vushtrria T - 1, Presheva T - 2): west of it, where BIK prints the Takvim itself, the user's own sun is later, in Sharri every start is at least T + 2, and north or south of it the night's length moves the dawn and the dusk, so against the base table itself the times run up to this many minutes from it, on top of its own uneven steps.", events = listOf("dhuhr", "fajr", "isha")),
            ProofLateLimit(minutes = 8, reason = "The base table's point rides across Kosovo beside the user's (rulings R30 and R44), with BIK's own town figures (the Takvimi's \"Diferenca e vendeve\": Sharri T + 2, Ferizaj, Gjilani, Prishtina, Podujeva and Vushtrria T - 1, Presheva T - 2): west of it, where BIK prints the Takvim itself, the user's own sun is later, in Sharri every start is at least T + 2, and north or south of it the night's length moves the dawn and the dusk, so against the base table itself the times run up to this many minutes from it, on top of its own uneven steps.", events = listOf("asrStandard", "maghrib", "sunrise")),
            ProofLateLimit(minutes = 9, reason = "The base table's point rides across Kosovo beside the user's (rulings R30 and R44), with BIK's own town figures (the Takvimi's \"Diferenca e vendeve\": Sharri T + 2, Ferizaj, Gjilani, Prishtina, Podujeva and Vushtrria T - 1, Presheva T - 2): west of it, where BIK prints the Takvim itself, the user's own sun is later, in Sharri every start is at least T + 2, and north or south of it the night's length moves the dawn and the dusk, so against the base table itself the times run up to this many minutes from it, on top of its own uneven steps.", events = listOf("endOfEating")),
        ),
        checked = listOf("2026-01-01..2027-12-31"),
    )

    private fun xkBikUnits(): Map<String, Map<String, Int>> = mapOf(
        "base" to mapOf("asrStandard" to 8, "dhuhr" to 7, "endOfEating" to 9, "fajr" to 7, "isha" to 7, "maghrib" to 8, "sunrise" to 8),
    )

    private fun zaCape() = ProofStamp(
        entryId = "za.cape",
        places = 1,
        placeDays = 61,
        ramadanDays = 0,
        lastChecked = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 4, "sunrise" to 2),
        worstLateByUnit = emptyMap(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 1, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha")),
            ProofLateLimit(minutes = 2, reason = "Sunrise is the earliest of the three members', each a little before its own table's: up to 2 min before the MJC's printed sunrise.", events = listOf("sunrise")),
            ProofLateLimit(minutes = 4, reason = "The three tables print Maghrib from sunset to sunset + 3; Maghrib is the latest member's, never before any, so on the days the MJC prints it 3 min before the others it runs up to 4 min after the MJC's.", events = listOf("maghrib")),
        ),
        checked = listOf("2026-09-01..2026-10-31"),
    )

    private fun zaJamiat() = ProofStamp(
        entryId = "za.jamiat",
        places = 9,
        placeDays = 15338,
        ramadanDays = 1227,
        lastChecked = "2029-12-31",
        worstLateMinutes = mapOf("asrHanafi" to 1, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 2, "maghrib" to 2, "sunrise" to 1),
        worstLateByUnit = zaJamiatUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 2, reason = null, events = listOf("asrHanafi", "asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2025-01-01..2029-12-31"),
        checkedByUnit = zaJamiatChecked(),
    )

    private fun zaJamiatChecked(): Map<String, List<String>> = mapOf(
        "pretoria" to listOf("2026-01-01..2027-12-31"),
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
        lastChecked = "2026-10-31",
        worstLateMinutes = mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
        worstLateByUnit = zaMjcUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-09-01..2026-10-31"),
    )

    private fun zaMjcUnits(): Map<String, Map<String, Int>> = mapOf(
        "za.mjc" to mapOf("asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 1, "fajr" to 1, "isha" to 1, "maghrib" to 1, "sunrise" to 1),
    )

    private fun zaVoc() = ProofStamp(
        entryId = "za.voc",
        places = 1,
        placeDays = 271,
        ramadanDays = 15,
        lastChecked = "2027-06-05",
        worstLateMinutes = mapOf("asrHanafi" to 2, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
        worstLateByUnit = zaVocUnits(),
        lateLimits = listOf(
            ProofLateLimit(minutes = 3, reason = null, events = listOf("asrHanafi", "asrStandard", "dhuhr", "endOfEating", "fajr", "isha", "maghrib", "sunrise")),
        ),
        checked = listOf("2026-06-01..2026-07-15", "2026-07-20", "2026-07-22", "2026-07-24..2026-07-27", "2026-07-29", "2026-07-31", "2026-08-02..2026-08-03", "2026-08-05", "2026-08-07..2026-08-10", "2026-08-12", "2026-08-19", "2026-08-21..2026-08-24", "2026-08-26", "2026-08-28..2026-10-31", "2026-11-02", "2026-11-04", "2026-11-06..2026-11-09", "2026-11-15..2026-11-16", "2026-11-18", "2026-11-20..2026-11-23", "2026-11-25", "2026-11-27..2026-12-31", "2027-01-02..2027-01-04", "2027-01-06", "2027-01-08", "2027-01-13", "2027-01-15..2027-01-18", "2027-01-20", "2027-01-22..2027-01-25", "2027-01-27", "2027-01-29..2027-01-31", "2027-02-03", "2027-02-05..2027-02-07", "2027-02-12", "2027-02-19..2027-02-22", "2027-02-24", "2027-02-26..2027-03-01", "2027-03-03", "2027-03-05..2027-03-08", "2027-03-14..2027-03-15", "2027-03-17", "2027-03-19..2027-03-22", "2027-03-24", "2027-03-26..2027-03-29", "2027-03-31", "2027-04-02..2027-04-05", "2027-04-07", "2027-04-14", "2027-04-16..2027-04-19", "2027-04-21", "2027-04-23..2027-04-26", "2027-04-28", "2027-04-30..2027-05-03", "2027-05-05", "2027-05-07", "2027-05-14..2027-05-17", "2027-05-19", "2027-05-21..2027-05-24", "2027-05-26", "2027-05-28..2027-05-31", "2027-06-02", "2027-06-04..2027-06-05"),
    )

    private fun zaVocUnits(): Map<String, Map<String, Int>> = mapOf(
        "za.voc" to mapOf("asrHanafi" to 2, "asrStandard" to 1, "dhuhr" to 1, "endOfEating" to 2, "fajr" to 2, "isha" to 2, "maghrib" to 3, "sunrise" to 2),
    )
}
