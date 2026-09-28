package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
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
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Egypt: the Egyptian General Authority for Survey (ESA), republished by Dar al-Ifta
 * (profiles-tested.md, data-gulf-egypt; authorities-arab-world-turkiye.md): Fajr 19.5° (fatwa 4021,
 * 2017), Isha 17.5°, no minutes, nearest rounding, about 100 cities at ESA's own points, no
 * elevation; the Asr shadow at the noon declination. Only data from 2025 on is used (a method change
 * between the Dec 2024 and Jan 2025 captures). The fast begins at the printed Fajr.
 *
 * Margins (Task 7a, eg-esa.tsv), national: fitted on eleven towns (the research's split B) with the
 * Conventions' formulas and 5 s and held out on eleven others, where one sunrise (Bawiti, 3 s) was
 * late and Dhuhr, Isha and the end of eating sat inside the 5 s; so they are the bound over all 22
 * towns with 5 s (proof 7a-gulf-egypt.md): Fajr +2, sunrise 0, Dhuhr −6, Asr −2, Maghrib +3, Isha
 * −4, end of eating +5. Held out on five more towns ESA prints (Dahab, Nuweiba, Saint Catherine,
 * Farafra, Shalatin): 0 early.
 *
 * Units: Cairo at ESA's own fitted point (30.08 N 31.27 E); the other towns at the app's points (the
 * five above at their town centres). Three towns' tables come from points well off the town, fitted
 * on their own 2025–2026 days (nothing held out there), and the engine keeps the later start and the
 * earlier end of the table and the method at the town (spec §3.5): Kharga's ESA point runs about
 * 70 s later and Taba's about 2 min later, so their starts are their own (Kharga +40..+46, Taba
 * +96..+99) and their sunrise and end of eating the national ones; Ras Gharib's runs about 65 s
 * earlier, so its starts are the national ones and its sunrise (−42) and end of eating (−49) its
 * own; Taba's own sunrise and dawn run up to 3 min before its table's (a recorded exception).
 *
 * Beyond every unit ESA's own point is not known and may be that far off: starts
 * [EDGE_START_SECONDS] s later (Taba's own margins exceed the national ones by up to 103 s, their
 * 5 s included), sunrise and the end of eating 60 s earlier (Ras Gharib's need 42 and 54). Checked by
 * computing all 30 towns at the edge instead of their units: 0 early, 0 late ends, at most 4 min. Class B
 * (reference points verified only at the tested towns).
 */
object Egypt {
    private val national = margins(start = 0, sunrise = 0, fajr = 2, dhuhr = -6, asr = -2, maghrib = 3, isha = -4)

    val method = TimetableMethod(
        id = "eg.esa",
        fajrAngle = 19.5,
        isha = IshaRule.Angle(17.5),
        asrModel = AsrModel.NOON_SHADOW,
        margins = national,
        endOfEatingMarginSeconds = 5,
    )

    private fun town(name: String, lat: Double, lon: Double) = AuthorityUnit(
        id = name.lowercase().replace(' ', '-'), name = name, point = GeoPoint(lat, lon), radiusKm = 20.0,
    )

    /** A town whose ESA table is computed at a point well off the town: its own margins. */
    private fun offPoint(name: String, lat: Double, lon: Double, margins: EventOffsets, endOfEating: Int): AuthorityUnit {
        val unit = town(name, lat, lon)
        return unit.copy(method = method.copy(id = "eg.esa.${unit.id}", margins = margins, endOfEatingMarginSeconds = endOfEating))
    }

    val units = UnitSet(
        "eg.esa",
        listOf(
            town("Cairo", 30.08, 31.27).copy(radiusKm = 30.0),
            town("Alexandria", 31.20176, 29.91582),
            town("Aswan", 24.09082, 32.89942),
            town("Marsa Matruh", 31.3529, 27.23725),
            town("El Arish", 31.13159, 33.79844),
            town("Sallum", 31.55371, 25.15793),
            town("Sidi Barrani", 31.61166, 25.92539),
            town("Siwa", 29.2032, 25.51965),
            town("Halaib", 22.22273, 36.64679),
            town("Rafah", 31.28204, 34.23869),
            town("Sharm El Sheikh", 27.91582, 34.32995),
            town("Hurghada", 27.25738, 33.81291),
            offPoint("Kharga", 25.45101, 30.54653, margins(start = 0, sunrise = 0, fajr = 45, dhuhr = 40, asr = 42, maghrib = 46, isha = 44), endOfEating = 5),
            town("Mut", 25.48742, 28.97918),
            town("Bawiti", 28.34827, 28.86936),
            town("Luxor", 25.69893, 32.6421),
            town("Quseir", 26.10611, 34.27716),
            town("Port Said", 31.26531, 32.3019),
            town("El Tor", 28.24168, 33.6222),
            town("Edfu", 24.97916, 32.87722),
            town("El Dabaa", 31.02819, 28.44498),
            town("Damietta", 31.41648, 31.81332),
            offPoint("Ras Gharib", 28.35831, 33.07829, national.copy(sunrise = -42), endOfEating = -49),
            town("Suez", 29.97371, 32.52627),
            town("Dahab", 28.5000, 34.5130),
            town("Nuweiba", 29.0333, 34.6667),
            town("Saint Catherine", 28.5550, 33.9760),
            town("Farafra", 27.0580, 27.9700),
            town("Shalatin", 23.1300, 35.5900),
            offPoint("Taba", 29.4925, 34.8969, margins(start = 0, sunrise = 0, fajr = 96, dhuhr = 97, asr = 98, maghrib = 99, isha = 96), endOfEating = 5)
                .copy(
                    lateLimits = listOf(
                        LateLimit(
                            3,
                            "ESA computes Taba's table about half a degree west of Taba, and Taqwa keeps Taba's own " +
                                "earlier sunrise and dawn",
                            setOf(TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
                        ),
                    ),
                ),
        ),
    ) { method.atEdge("eg.esa.edge", national.widened(EDGE_START_SECONDS, sunrise = -EDGE_END_SECONDS)) }

    val entry: RegistryEntry = single(
        id = "eg.esa", nameKey = "authority_esa", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("EG"),
    )

    /**
     * The old picker's Egyptian method anywhere: 19.5°/17.5°, Dhuhr +1 (adhan2's preset), ±30 s (ruling
     * R31), and Asr [ADHAN2_ASR_ALLOWANCE] later: adhan2 0.0.7 takes one declination for the whole
     * day, so its Asr runs up to about 34 s after this core's (docs/research/2026-09-prayer-times/
     * proof/7h-other-and-default.md). Against the old adhan2 engine at twelve places, every day of
     * 2026–2027 in both schools, this method's Standard Asr was a minute early on 19 of 8,760 days
     * (London 17, New York 2); 0 with the allowance.
     */
    val other: RegistryEntry = single(
        id = "other.egyptian", nameKey = "method_egyptian", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "other.egyptian",
            fajrAngle = 19.5,
            isha = IshaRule.Angle(17.5),
            authorityMinutes = EventOffsets(dhuhr = 1),
            margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE),
            endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    val entries = listOf(entry, other)

    /** Beyond every unit, starts: Taba's own margins exceed the national ones by up to 103 s. */
    private const val EDGE_START_SECONDS = 105

    /** Beyond every unit, sunrise and the end of eating: Ras Gharib's need 42 and 54 s; a minute. */
    private const val EDGE_END_SECONDS = 60
}
