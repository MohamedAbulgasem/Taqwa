package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.DayRule
import world.taqwa.app.prayer.engine.method.Harmonics
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
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
     * The one exception to class A's minute (rulings R37, R41): on a lag date the API prints the
     * previous day's times, and Taqwa shows the later of the two days' starts and the earlier of
     * their sunrises and ends of eating, which is a minute more than the class allows on a few days
     * (over 13,146 place-days: Fajr on 1, sunrise on 9, the end of eating on 21, every one a lag
     * date). Dhuhr, Asr, Maghrib and Isha never passed it.
     */
    val lagDateLimit = LateLimit(
        2,
        "on the few dates a year Umm al-Qura's calendar repeats the previous day's times, Taqwa shows the later start and " +
            "the earlier sunrise and end of eating of the two days, so as never to be early if the calendar stops repeating them",
        setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
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
        school = AsrSchool.STANDARD, countries = setOf("SA"), measured = true, lateLimits = listOf(lagDateLimit),
    )

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
