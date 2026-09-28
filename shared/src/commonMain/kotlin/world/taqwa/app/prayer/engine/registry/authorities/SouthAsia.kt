package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/** Pakistan, India and Bangladesh (research South Asia sections; spec §6.2). */
object SouthAsia {

    /**
     * A majority convention with the MWL Fiqh Council's minutes (spec §6.2 b, 1986): Dhuhr, Asr,
     * Maghrib and Isha + 2, sunrise − 2, the end of eating 2 min before the dawn; the Fajr start at
     * the convention's own angle. With the first-guess 30 s. Used elsewhere for authorities this
     * subtask does not own (the Americas' ISNA-convention entries); Pakistan and India (below) now
     * have their own task-7f-fitted methods instead of this first guess.
     */
    internal fun conventionWithMwlMinutes(id: String, fajr: Double, isha: Double, fajrMinutes: Int = 0) = TimetableMethod(
        id = id,
        fajrAngle = fajr,
        isha = IshaRule.Angle(isha),
        margins = margins(start = 120 + SAFE_START, sunrise = -120 + SAFE_SUNRISE, fajr = fajrMinutes * 60 + SAFE_START),
        endOfEatingMarginSeconds = -120,
    )

    /**
     * Pakistan: no binding state timetable; the Karachi method is near-universal (the Banuri Town
     * calculator equals 18°/18° with no offsets, nearest, Hanafi). Margins fitted (task 7f) on the
     * Deobandi Karachi Daimi (permanent) timetable's June, September and December pages, folded
     * together with Dawat-e-Islami's Karachi AJAX (a later, Barelvi member: `pk-karachi.tsv`,
     * `proof/7f-asia.md`), so the single Asr margin (+71 s) clears both the printed Standard and
     * Hanafi columns; the entry itself shows the Hanafi one (spec §3.7). Class D (no authority
     * publishes one binding table; the near-universal method is well fitted, but not to A/B's
     * every-day standard once Dawat-e-Islami's own +1 min advice is folded in).
     */
    val pakistanMethod = TimetableMethod(
        id = "pk.karachi",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        margins = EventOffsets(fajr = 4, sunrise = -28, dhuhr = 3, asr = 71, maghrib = 26, isha = 5),
        endOfEatingMarginSeconds = -26,
    )

    val pakistan: RegistryEntry = single(
        id = "pk.karachi", nameKey = "authority_karachi", entryClass = EntryClass.D_AUTHORITY,
        method = pakistanMethod, school = AsrSchool.HANAFI, countries = setOf("PK"),
    )

    /**
     * India: no authority found; the only verified table is Dawat-e-Islami Delhi (18°/18° Hanafi,
     * with elevation, 701 ft). Margins fitted (task 7f) on its September 2026 AJAX capture alone
     * (`in-karachi.tsv`): Delhi's elevation widens sunrise/Maghrib well beyond Karachi's own fit, so
     * India keeps its own margins rather than sharing Pakistan's. No printed sehri/imsak column was
     * captured for Delhi; the end of eating keeps the sunrise margin as a conservative stand-in (both
     * are "ends" at the same point), unproven on its own. Hanafi (spec §3.7; Kerala's Shafi'is,
     * unverified, get the later Asr). Class D: one month, one source, "sharing prohibited".
     */
    val indiaMethod = TimetableMethod(
        id = "in.karachi",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        margins = EventOffsets(fajr = 1, sunrise = -123, dhuhr = 4, asr = 75, maghrib = 121, isha = 4),
        endOfEatingMarginSeconds = -123,
    )

    val india: RegistryEntry = single(
        id = "in.karachi", nameKey = "authority_karachi", entryClass = EntryClass.D_AUTHORITY,
        method = indiaMethod, school = AsrSchool.HANAFI, countries = setOf("IN"),
    )

    /**
     * Bangladesh: the Islamic Foundation (spec §6.2 a). Its year-round Dhaka table adds 3 min to
     * true Fajr, Zuhr and Maghrib and ends sehri 3 min before true dawn; Isha ≈ 18° + 1..4. For
     * Ramadan 2026 it moved to 64 district tables: sehri at the district's east edge (floored),
     * adhan and iftar at its west edge (rounded up), at 18°. Margins fitted (task 7f) on the
     * permanent table across the year (`bd-ifb.tsv`, `proof/7f-asia.md`), held out on an
     * independently digitized October page (already public as BD-IFB) which agrees exactly; the
     * fitted core is then widened by a further district edge, a quarter degree (about a minute)
     * either side of Dhaka, since the entry has no per-district units (the Ramadan 2026 method's
     * own per-district edges are not separately proven here — a concern for a future unit split, as
     * district offsets of several minutes are documented for the far districts). Hanafi. Class D.
     */
    val bangladeshMethod = TimetableMethod(
        id = "bd.ifb",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(fajr = 3, dhuhr = 3, maghrib = 3),
        margins = EventOffsets(
            fajr = -53 + DISTRICT_EDGE, sunrise = -56 - DISTRICT_EDGE, dhuhr = 29 + DISTRICT_EDGE,
            asr = -34 + DISTRICT_EDGE, maghrib = 47 + DISTRICT_EDGE, isha = 165 + DISTRICT_EDGE,
        ),
        endOfEating = EndOfEating.DawnAngle(18.0),
        endOfEatingMarginSeconds = -358 - DISTRICT_EDGE,
    )

    val bangladesh: RegistryEntry = single(
        id = "bd.ifb", nameKey = "authority_ifb", entryClass = EntryClass.D_AUTHORITY, method = bangladeshMethod,
        school = AsrSchool.HANAFI, countries = setOf("BD"),
        lateLimits = listOf(
            LateLimit(4, "the true-dawn precaution swings across the year and the district edge stacks on it (task 7f)", setOf(TimedEvent.FAJR)),
            LateLimit(4, "IFB's Isha angle is only documented as about 18° + 1..4 min, and the district edge stacks on it (task 7f)", setOf(TimedEvent.ISHA)),
            LateLimit(4, "the district edge makes the fitted sehri end run up to a minute earlier still (task 7f); an early end is the safe side", setOf(TimedEvent.END_OF_EATING)),
        ),
    )

    val entries = listOf(pakistan, india, bangladesh)

    private const val DISTRICT_EDGE = 60
}
